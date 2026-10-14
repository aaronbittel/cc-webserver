package com.github.aaronbittel;

import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;

public record ServerConfig(InetAddress address, int port, Path root) {
    public ServerConfig {
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 0 and 65535");
        }

        root = root.toAbsolutePath().normalize();

        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Document root is not a directory: " + root);
        }
    }
}
