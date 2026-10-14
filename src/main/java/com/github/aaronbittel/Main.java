package com.github.aaronbittel;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Path;

public class Main {

    private static final String DEFAULT_ADDRESS = "127.0.0.1";
    private static final int DEFAULT_PORT = 80;
    private static final Path DEFAULT_BASE_PATH = Path.of(".").toAbsolutePath().normalize();

    static void main() throws IOException {
        Path root;
        InetAddress address;
        int port;

        try {
            root = Path.of(readEnvVar("CC_DIRECTORY", DEFAULT_BASE_PATH.toString()));
            address = InetAddress.ofLiteral(readEnvVar("CC_ADDRESS", DEFAULT_ADDRESS));
            port = Integer.parseInt(readEnvVar("CC_PORT", String.valueOf(DEFAULT_PORT)));
        } catch (IllegalArgumentException e) {
            System.err.println("Configuration error: " + e.getMessage());
            return;
        }

        var config = new ServerConfig(address, port, root);

        try (var server = new HttpServer(config)) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Shutting down server...");
                try {
                    server.close();
                } catch (IOException e) {
                    System.err.println("Error shutting down server: " + e.getMessage());
                }
            }
            ));
            server.serve();
        }
    }

    private static String readEnvVar(String name, String fallback) {
        var value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
