package com.github.aaronbittel;

import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class TestHttpServer implements AutoCloseable {

    private final HttpServer server;
    private final ExecutorService executor;

    public TestHttpServer(HttpServer.Builder builder) throws IOException {
        server = builder.build();

        server.bind(InetAddress.getLoopbackAddress(), 0);

        executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            server.serve();
            return null;
        });
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
