package com.github.aaronbittel;

import java.io.IOException;
import java.net.InetAddress;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class TestHttpServer implements AutoCloseable {

    private final HttpServer server;
    private final ExecutorService executor;

    public TestHttpServer(Router router) throws IOException {
        server = new HttpServer(router);
        server.setSocketReadTimeout(Duration.ofSeconds(1));
        server.bind(InetAddress.getLoopbackAddress(), 0);

        executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            server.serve();
            return null;
        });
    }

    public TestHttpServer() throws IOException {
        this(new Router());
    }

    int port() {
        return server.port();
    }

    @Override
    public void close() throws IOException {
        server.close();
        executor.close();
    }

    public void setSocketReadTimeout(Duration socketReadTimeout) {
        server.setSocketReadTimeout(socketReadTimeout);
    }

    public void setRequestParsingTimeout(Duration requestParsingTimeout) {
        server.setRequestParsingTimeout(requestParsingTimeout);
    }
}
