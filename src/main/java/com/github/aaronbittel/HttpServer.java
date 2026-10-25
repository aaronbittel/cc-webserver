package com.github.aaronbittel;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HttpServer implements AutoCloseable {
    private static final Logger log = Logger.getLogger(HttpServer.class.getName());

    private static final int BACKLOG = 5;

    private volatile ServerSocket serverSocket;
    private final Router router;
    private final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();

    private final Duration requestParsingTimeout;

    private static final Duration DEFAULT_REQUEST_PARSING_TIMEOUT = Duration.ofSeconds(15);

    private HttpServer(Builder builder) {
        Objects.requireNonNull(builder);
        this.router = builder.router;
        this.requestParsingTimeout = builder.requestParsingTimeout;
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
                executorService.submit(new ConnectionHandler(client, router, requestParsingTimeout));
            } catch (SocketException e) {
                if (serverSocket.isClosed()) {
                    break; // server shutdown
                }
                throw e;
            }
        }
    }

    private void awaitGracefulShutdown() {
        try {
            if (executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                log.info("Server shut down gracefully");
            } else {
                log.warning("Graceful shutdown timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.log(Level.WARNING, "Interrupted while waiting for server shutdown", e);
        }
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

        executorService.shutdown();
        awaitGracefulShutdown();
    }

    public static class Builder {

        private final Router router;
        private Duration requestParsingTimeout = DEFAULT_REQUEST_PARSING_TIMEOUT;

        public Builder(Router router) {
            this.router = Objects.requireNonNull(router);
        }

        public Builder requestParsingTimeout(Duration requestParsingTimeout) {
            this.requestParsingTimeout = Objects.requireNonNull(requestParsingTimeout);
            return this;
        }

        public HttpServer build() {
            return new HttpServer(this);
        }
    }
}
