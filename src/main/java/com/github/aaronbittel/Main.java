package com.github.aaronbittel;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.logging.Logger;

record RequestLine(HTTPMethod method, String path, HTTPVersion version) {}

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

    static void main() throws IOException {
        log.info("starting server on port: " + PORT);
        try (var server = new ServerSocket(PORT, BACKLOG, InetAddress.getLoopbackAddress())) {
            server.setReuseAddress(true);
            while (true) {
                try (var client = server.accept()) {
                    log.info("accepted client: " + client.getLocalSocketAddress());
                    var reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
                    var requestLine = parseRequestLine(reader.readLine());
                    log.info("client send: " + requestLine);

                    var writer = new BufferedWriter(new OutputStreamWriter(client.getOutputStream()));
                    writer.write("HTTP/1.1 200 OK\r\n\r\nRequested path: " + requestLine.path() + "\r\n");
                    writer.flush();
                }
            }
        }
    }

    static RequestLine parseRequestLine(String line) {
        var parts = line.split(" ", 3);
        if (parts.length != 3) {
            throw new IllegalArgumentException("Illegal request line format: " + line);
        }

        var method = parseHTTPMethod(parts[0]);
        var path = parts[1];
        var version = parseHTTPVersion(parts[2]);

        return new RequestLine(method, path, version);
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
}