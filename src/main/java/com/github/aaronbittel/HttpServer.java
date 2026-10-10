package com.github.aaronbittel;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

public class HttpServer {
    private static final Logger log = Logger.getLogger(HttpServer.class.getName());

    private static final int BACKLOG = 5;
    private final ServerConfig config;

    public HttpServer(ServerConfig config) {
        this.config = config;
    }

    public void serve() throws IOException {
        log.info(() -> String.format(
                "Serving HTTP on %s port %d (http://%s:%d/) from directory %s",
                config.address().getHostAddress(),
                config.port(),
                config.address().getHostAddress(),
                config.port(),
                config.root()));

        try (var server = new ServerSocket(config.port(), BACKLOG, config.address())) {
            server.setReuseAddress(true);
            while (true) {
                var client = server.accept();
                Thread.ofVirtual().start(() -> {
                    try (client) {
                        handleConnection(client, config.root());
                    } catch (IOException e) {
                        log.warning(e.getMessage());
                    }
                });
            }
        }
    }

    private static void handleConnection(Socket client, Path root) throws IOException {
        log.info("accepted client: " + client.getLocalSocketAddress());

        var in = new BufferedInputStream(client.getInputStream());

        var parser = new HttpRequestParser(in);
        var request = parser.parseRequest();
        log.info("request:" + request);

        var writer = new BufferedWriter(new OutputStreamWriter(client.getOutputStream()));

        var resolvedTarget = resolveRequestPath(root, request.requestLine().requestTarget());
        log.info("resolvedTarget: " + resolvedTarget);

        if (!Files.exists(resolvedTarget) || !Files.isRegularFile(resolvedTarget)) {
            writer.write("HTTP/1.1 404 Not Found\r\n\r\n");
            writer.flush();
            return;
        }

        var content = Files.readString(resolvedTarget);

        writer.write("HTTP/1.1 200 OK\r\n\r\n" + content);
        writer.flush();
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
