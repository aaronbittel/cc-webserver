package com.github.aaronbittel;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

record RequestLine(HTTPMethod method, String requestTarget, HTTPVersion version) {}

enum HTTPMethod {
    GET,
    POST,
    PUT,
    DELETE,
}

enum HTTPVersion {
    HTTP_1_0,
    HTTP_1_1,
}

public class Main {
    private static final Logger log = Logger.getLogger(Main.class.getName());
    private static final int PORT = 80;
    private static final int BACKLOG = 5;
    private static final int MAXIMUM_HTTP_LINE_LENGTH = 8192;
    private static final Path basePath = Path.of("./www").toAbsolutePath().normalize();

    static void main() throws IOException {
        log.info("starting server on port: " + PORT);
        try (var server = new ServerSocket(PORT, BACKLOG, InetAddress.getLoopbackAddress())) {
            server.setReuseAddress(true);
            while (true) {
                try (var client = server.accept()) {
                    log.info("accepted client: " + client.getLocalSocketAddress());
                    var in = new BufferedInputStream(client.getInputStream());
                    var requestLine = parseRequestLine(readHTTPLine(in));
                    log.info("requestLine:" + requestLine);

                    var writer = new BufferedWriter(new OutputStreamWriter(client.getOutputStream()));

                    var resolvedTarget = resolveRequestPath(basePath, requestLine.requestTarget());
                    log.info("resolvedTarget: " + resolvedTarget);

                    if (!Files.exists(resolvedTarget) || !Files.isRegularFile(resolvedTarget)) {
                        writer.write("HTTP/1.1 404 NOT FOUND\r\n\r\n");
                        writer.flush();
                        continue;
                    }

                    var content = Files.readString(resolvedTarget);

                    writer.write("HTTP/1.1 200 OK\r\n\r\n" + content);
                    writer.flush();
                }
            }
        }
    }

    static String readHTTPLine(InputStream in) throws IOException {
        var sb = new StringBuilder();

        int c;
        while ((c = in.read()) != -1) {
            switch (c) {
                case '\r' -> {
                    if (in.read() == '\n') {
                        return sb.toString();
                    }
                    throw new IOException("Invalid HTTP line ending");
                }
                case '\n' -> throw new IOException("Invalid HTTP line ending");
                default -> {
                    if (sb.length() >= MAXIMUM_HTTP_LINE_LENGTH) {
                        throw new IOException("HTTP line exceeds maximum length");
                    }
                    sb.append((char) c);
                }
            }
        }

        throw new IOException("Unexpected end of stream before HTTP line terminator");
    }

    static RequestLine parseRequestLine(String line) {
        var parts = line.split(" ", 3);
        if (parts.length != 3) {
            throw new IllegalArgumentException("Illegal request line format: " + line);
        }

        var method = parseHTTPMethod(parts[0]);
        var requestTarget = parts[1];
        if (!requestTarget.startsWith("/")) {
            throw new IllegalArgumentException(
                    "Expected origin-form request target");
        }
        var version = parseHTTPVersion(parts[2]);

        return new RequestLine(method, requestTarget, version);
    }

    static HTTPVersion parseHTTPVersion(String version) {
        return switch (version) {
            case "HTTP/1.1" -> HTTPVersion.HTTP_1_1;
            case "HTTP/1.0" -> HTTPVersion.HTTP_1_0;
            default -> throw new IllegalArgumentException("Illegal http version: " + version);
        };
    }

    static HTTPMethod parseHTTPMethod(String method) {
        return switch (method) {
            case "GET" -> HTTPMethod.GET;
            case "POST" -> HTTPMethod.POST;
            case "PUT" -> HTTPMethod.PUT;
            case "DELETE" -> HTTPMethod.DELETE;
            default -> throw new IllegalStateException("Illegal http method: " + method);
        };
    }

    static Path resolveRequestPath(Path basePath, String requestTarget) {
        if (!requestTarget.startsWith("/")) {
            throw new IllegalArgumentException("Expected an origin-form request target");
        }

        var relativeTarget = requestTarget.substring(1);
        if (relativeTarget.isEmpty()) {
            relativeTarget = "index.html";
        }
        var resolvedTarget = basePath.resolve(relativeTarget).normalize();

        if (!resolvedTarget.startsWith(basePath)) {
            throw new IllegalArgumentException("Path traversal detected");
        }

        return resolvedTarget;
    }
}