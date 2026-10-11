package com.github.aaronbittel;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
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

        var parser = new HttpRequestParser(new BufferedInputStream(client.getInputStream()));
        var responseWriter = new HttpResponseWriter(new BufferedOutputStream(client.getOutputStream()));

        HttpRequest request;
        HttpResponse response;

        try {
            request = parser.parseRequest();
        } catch (IllegalArgumentException e) {
            log.warning("Invalid HTTP request: " + e.getMessage());

            response = new HttpResponse(HttpVersion.HTTP_1_1, HttpStatus.BAD_REQUEST, HttpResponse.EMPTY_BODY);
            responseWriter.write(response);
            return;
        }

        log.info("request:" + request);

        var resolvedTarget = resolveRequestPath(root, request.requestLine().requestTarget());
        log.info("resolvedTarget: " + resolvedTarget);

        if (!Files.exists(resolvedTarget) || !Files.isRegularFile(resolvedTarget)) {
            response = new HttpResponse(request.requestLine().version(), HttpStatus.NOT_FOUND, HttpResponse.EMPTY_BODY);
        } else {
            var content = Files.readAllBytes(resolvedTarget);
            response = new HttpResponse(request.requestLine().version(), HttpStatus.OK, content);
        }

        responseWriter.write(response);
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
