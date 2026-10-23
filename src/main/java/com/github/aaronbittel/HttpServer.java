package com.github.aaronbittel;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HttpServer implements AutoCloseable {
    private static final Logger log = Logger.getLogger(HttpServer.class.getName());

    private static final int BACKLOG = 5;
    private static final Duration DEFAULT_SOCKET_READ_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration DEFAULT_REQUEST_PARSING_TIMEOUT = Duration.ofSeconds(10);

    private volatile ServerSocket serverSocket;
    private final Router router;
    private final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();

    private Duration socketReadTimeout = DEFAULT_SOCKET_READ_TIMEOUT;
    private Duration requestParsingTimeout = DEFAULT_REQUEST_PARSING_TIMEOUT;

    public HttpServer(Router router) {
        this.router = router;
    }

    public void start(InetAddress address, int port) throws IOException {
        bind(address, port);
        serve();
    }

    void bind(InetAddress address, int port) throws IOException {
        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(address, port), BACKLOG);
    }

    void serve() throws IOException {
        log.info(() -> String.format("Serving HTTP on %s port %d (http://%s:%d/)",
                serverSocket.getInetAddress().getHostAddress(),
                serverSocket.getLocalPort(),
                serverSocket.getInetAddress().getHostAddress(),
                serverSocket.getLocalPort()));

        while (!serverSocket.isClosed()) {
            try {
                var client = serverSocket.accept();
                executorService.submit(() -> {
                    try (client) {
                        handleConnection(client);
                    } catch (IOException e) {
                        log.log(Level.WARNING, "Client connection failed", e);
                    }
                });
            } catch (SocketException e) {
                if (serverSocket.isClosed()) {
                    break; // server shutdown
                }
                throw e;
            }
        }
    }

    private void handleConnection(Socket client) throws IOException {
        client.setSoTimeout(Math.toIntExact(socketReadTimeout.toMillis()));

        log.info("accepted client: " + client.getLocalSocketAddress());

        var in = new BufferedInputStream(client.getInputStream());
        var parser = new HttpRequestParser(new DeadlineInputStream(client, in, System.nanoTime() + requestParsingTimeout.toNanos()));
        var out = new BufferedOutputStream(client.getOutputStream());

        HttpRequest request;

        try {
            request = parser.parseRequest();
        } catch (IllegalArgumentException e) {
            log.warning("Invalid HTTP request: " + e.getMessage());

            var writer = HttpResponseWriter.forMalformedRequest();
            HttpResponses.badRequestAndCloseConnection(writer);
            writer.finish(out, HttpVersion.HTTP_1_1);
            return;
        } catch (SocketTimeoutException e) {
            log.warning("Socket timeout: " + e.getMessage());

            var writer = HttpResponseWriter.forMalformedRequest();
            writer.headers().set("Connection", "close");
            writer.setStatus(HttpStatus.REQUEST_TIMEOUT);
            writer.finish(out, HttpVersion.HTTP_1_1);
            return;
        }

        log.info("request:" + request.requestLine());

        var writer = HttpResponseWriter.forRequest(request.method());
        router.handleRequest(writer, request);
        writer.finish(out, request.version());
    }

    public int port() {
        if (serverSocket == null) {
            throw new IllegalStateException("Server has not been started");
        }

        return serverSocket.getLocalPort();
    }

    @Override
    public void close() throws IOException {
        if (serverSocket != null) {
            serverSocket.close();
        }
    }

    public void setSocketReadTimeout(Duration socketReadTimeout) {
        this.socketReadTimeout = socketReadTimeout;
    }

    public void setRequestParsingTimeout(Duration requestParsingTimeout) {
        this.requestParsingTimeout = requestParsingTimeout;
    }
}
