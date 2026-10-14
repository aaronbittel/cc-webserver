package com.github.aaronbittel;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

sealed interface ResolvedResource {
    record FileResource(Path path) implements ResolvedResource {}

    record DirectoryListing(Path path) implements ResolvedResource {}

    record NotFound() implements ResolvedResource {}
}

public class HttpServer implements AutoCloseable {
    private static final Logger log = Logger.getLogger(HttpServer.class.getName());

    private static final String HTML_TEMPLATE = loadHtml();

    private static final String HTML_CONTENT_TYPE = "text/html";
    private static final String BINARY_CONTENT_TYPE = "application/octet-stream";

    private static final int BACKLOG = 5;

    private final ServerConfig config;

    private volatile ServerSocket server;

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

        server = new ServerSocket(config.port(), BACKLOG, config.address());
        server.setReuseAddress(true);

        try {
            while (!server.isClosed()) {
                var client = server.accept();
                Thread.ofVirtual().start(() -> {
                    try (client) {
                        handleConnection(client);
                    } catch (IOException e) {
                        log.log(Level.WARNING, "Client connection failed", e);
                    }
                });
            }
        } catch (SocketException e) {
            if (!server.isClosed()) {
                throw e;
            }
        }
    }

    private void handleConnection(Socket client) throws IOException {
        log.info("accepted client: " + client.getLocalSocketAddress());

        var parser = new HttpRequestParser(new BufferedInputStream(client.getInputStream()));
        var writer = new HttpResponseWriter(new BufferedOutputStream(client.getOutputStream()));

        HttpRequest request;
        HttpResponse response;

        try {
            request = parser.parseRequest();
        } catch (IllegalArgumentException e) {
            log.warning("Invalid HTTP request: " + e.getMessage());

            response = new HttpResponse(HttpVersion.HTTP_1_1, HttpStatus.BAD_REQUEST);
            writer.write(response);
            return;
        }

        log.info("request:" + request);
        handleRequest(writer, request);
    }

    void handleRequest(ResponseWriter writer, HttpRequest request) throws IOException {
        switch (request.requestLine().method()) {
            case GET -> handleGet(writer, request);
            case HEAD -> handleHead(writer, request);
            default -> handleUnsupportedMethod(writer, request);
        }
    }

    private ResolvedResource resolveResource(String requestTarget) throws IOException {
        var requestPath = extractPath(requestTarget);
        var resolvedTarget = resolveRequestPath(config.root(), requestPath);

        var indexPath = resolvedTarget.resolve("index.html");
        if (Files.isRegularFile(indexPath)) {
            return new ResolvedResource.FileResource(indexPath);
        }

        if (Files.isDirectory(resolvedTarget)) {
            return new ResolvedResource.DirectoryListing(resolvedTarget);
        }

        if (Files.isRegularFile(resolvedTarget)) {
            return new ResolvedResource.FileResource(resolvedTarget);
        }

        return new ResolvedResource.NotFound();
    }

    /**
     * Extracts the path component from the HTTP request-target.
     *
     * @param requestTarget the HTTP request-target, potentially including a query-string.
     * @return the path without the query string.
     */
    private String extractPath(String requestTarget) {
        var index = requestTarget.indexOf('?');
        if (index == -1) {
            return requestTarget;
        }
        return requestTarget.substring(0, index);
    }

    void handleGet(ResponseWriter writer, HttpRequest request) throws IOException {
        switch (resolveResource(request.requestLine().requestTarget())) {
            case ResolvedResource.DirectoryListing directoryListing -> {
                var content = renderDirectoryListing(directoryListing.path());
                writeString(writer, request, content);
            }
            case ResolvedResource.FileResource fileResource -> writeFile(writer, request, fileResource.path());
            case ResolvedResource.NotFound _ -> writeNotFound(writer, request);
        }
    }

    void handleHead(ResponseWriter writer, HttpRequest request) throws IOException {
        switch (resolveResource(request.requestLine().requestTarget())) {
            case ResolvedResource.DirectoryListing directoryListing -> {
                var content = renderDirectoryListing(directoryListing.path());
                writeStringHeaders(writer, request, content);
            }
            case ResolvedResource.FileResource fileResource -> writeFileHeaders(writer, request, fileResource.path());
            case ResolvedResource.NotFound _ -> writeNotFound(writer, request);
        }
    }

    void handleUnsupportedMethod(ResponseWriter writer, HttpRequest request) throws IOException {
        var response = new HttpResponse(request.requestLine().version(), HttpStatus.METHOD_NOT_ALLOWED);
        writer.write(response);
    }

    void writeFile(ResponseWriter writer, HttpRequest request, Path path) throws IOException {
        var content = Files.readAllBytes(path);
        var headers = createFileHeaders(path);
        writer.write(new HttpResponse(request.requestLine().version(), HttpStatus.OK, headers, content));
    }

    void writeString(ResponseWriter writer, HttpRequest request, String content) throws IOException {
        var data = content.getBytes(StandardCharsets.UTF_8);
        var contentLength = data.length;
        var headers = createFileHeaders(contentLength, HttpServer.HTML_CONTENT_TYPE);
        writer.write(new HttpResponse(request.requestLine().version(), HttpStatus.OK, headers, data));
    }

    void writeFileHeaders(ResponseWriter writer, HttpRequest request, Path path) throws IOException {
        var headers = createFileHeaders(path);
        var response = new HttpResponse(request.requestLine().version(), HttpStatus.OK, headers);
        writer.write(response);
    }

    void writeStringHeaders(ResponseWriter writer, HttpRequest request, String content) throws IOException {
        var contentLength = content.getBytes(StandardCharsets.UTF_8).length;
        var headers = createFileHeaders(contentLength, HTML_CONTENT_TYPE);
        var response = new HttpResponse(request.requestLine().version(), HttpStatus.OK, headers);
        writer.write(response);
    }

    void writeNotFound(ResponseWriter writer, HttpRequest request) throws IOException {
        var response = new HttpResponse(request.requestLine().version(), HttpStatus.NOT_FOUND);
        writer.write(response);
    }

    HttpHeaders createFileHeaders(long contentLength, String contentType) {
        var headers = new HttpHeaders();
        headers.add("Content-Length", String.valueOf(contentLength));
        headers.add("Content-Type", contentType);
        return headers;
    }

    private HttpHeaders createFileHeaders(Path path) throws IOException {
        String contentType = Files.probeContentType(path);

        if (contentType == null) {
            contentType = BINARY_CONTENT_TYPE;
        }

        return createFileHeaders(Files.size(path), contentType);
    }

    String renderDirectoryListing(Path directory) throws IOException {
        var entries = generateDirectoryListingHtml(directory);
        return HTML_TEMPLATE.replace("{{entries}}", entries);
    }

    String generateDirectoryListingHtml(Path directory) throws IOException {
        var entries = new StringBuilder();

        if (!directory.equals(config.root())) {
            appendDirectoryEntryHtml(entries, toRootRelativeUrl(config.root(), directory.getParent()), "..");
        }

        try (var paths = Files.list(directory)) {
            for (Path path : paths.sorted().toList()) {
                appendDirectoryEntryHtml(entries, toRootRelativeUrl(config.root(), path), path.getFileName().toString());
            }
        }

        return entries.toString();
    }

    private static String toRootRelativeUrl(Path root, Path path) {
        return "/" + root.relativize(path)
                .toString()
                .replace(path.getFileSystem().getSeparator(), "/");
    }

    private void appendDirectoryEntryHtml(StringBuilder html, String linkPath, String displayName) {
        html.append("<li><a href=\"")
                .append(linkPath)
                .append("\">")
                .append(displayName)
                .append("</a></li>\n");
    }

    static Path resolveRequestPath(Path rootPath, String requestTarget) throws IOException {
        if (!requestTarget.startsWith("/")) {
            throw new IllegalArgumentException("Expected an origin-form request target");
        }

        var relativeTarget = requestTarget.substring(1);
        var resolvedTarget = rootPath.resolve(relativeTarget).normalize();

        if (Files.isSymbolicLink(resolvedTarget)) {
            var symlinkTarget = Files.readSymbolicLink(resolvedTarget);
            resolvedTarget = resolvedTarget.getParent()
                    .resolve(symlinkTarget)
                    .normalize();
        }

        if (!resolvedTarget.startsWith(rootPath)) {
            throw new IllegalArgumentException("Path traversal detected");
        }

        return resolvedTarget;
    }

    private static String loadHtml() {
        try (var in = Main.class.getResourceAsStream("/index.html")) {
            if (in == null) {
                throw new IllegalStateException("index.html not found");
            }

            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load index.html", e);
        }
    }

    @Override
    public void close() throws IOException {
        if (server != null) {
            server.close();
        }
    }
}
