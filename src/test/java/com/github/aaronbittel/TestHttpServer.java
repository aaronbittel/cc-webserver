package com.github.aaronbittel;

import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class TestHttpServer implements AutoCloseable {

    private final HttpServer server;
    private final ExecutorService executor;

    public TestHttpServer(Router router) throws IOException {
        server = new HttpServer(router);
        server.setRequestTimeoutMillis(1_000);
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
}
