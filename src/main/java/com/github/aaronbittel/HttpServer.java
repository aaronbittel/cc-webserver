package com.github.aaronbittel;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HttpServer implements AutoCloseable {
    private static final Logger log = Logger.getLogger(HttpServer.class.getName());

    private static final int BACKLOG = 5;

    private final ServerConfig config;
    private volatile ServerSocket serverSocket;

    private final Router router;

    public HttpServer(ServerConfig config, Router router) {
        this.config = config;
        this.router = router;
    }

    public void bind() throws IOException {
        this.serverSocket = new ServerSocket();
        this.serverSocket.setReuseAddress(true);
        this.serverSocket.bind(
                new InetSocketAddress(config.address(), config.port()),
                BACKLOG
        );
    }

    public void serve() throws IOException {
        log.info(() -> String.format(
                "Serving HTTP on %s port %d (http://%s:%d/) from directory %s",
                config.address().getHostAddress(),
                config.port(),
                config.address().getHostAddress(),
                config.port(),
                config.root()));

        try {
            while (!serverSocket.isClosed()) {
                var client = serverSocket.accept();
                Thread.ofVirtual().start(() -> {
                    try (client) {
                        handleConnection(client);
                    } catch (IOException e) {
                        log.log(Level.WARNING, "Client connection failed", e);
                    }
                });
            }
        } catch (SocketException e) {
            if (!serverSocket.isClosed()) {
                throw e;
            }
        }
    }

    private void handleConnection(Socket client) throws IOException {
        log.info("accepted client: " + client.getLocalSocketAddress());

        var parser = new HttpRequestParser(new BufferedInputStream(client.getInputStream()));
        var out = new BufferedOutputStream(client.getOutputStream());

        HttpRequest request;

        try {
            request = parser.parseRequest();
        } catch (IllegalArgumentException e) {
            log.warning("Invalid HTTP request: " + e.getMessage());

            var writer = HttpResponseWriter.withBody(out);
            writer.write(HttpResponses.badRequest(HttpVersion.HTTP_1_1));
            return;
        }

        ResponseWriter writer;
        if (request.method() == HttpMethod.HEAD) {
            writer = HttpResponseWriter.withoutBody(out);
        } else {
            writer = HttpResponseWriter.withBody(out);
        }

        log.info("request:" + request.requestLine());

        router.handleRequest(writer, request);
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
}
