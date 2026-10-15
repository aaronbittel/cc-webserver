package com.github.aaronbittel;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public record StaticFileHandler(Path root) implements HttpHandler {

    private sealed interface ResolvedResource {
        record FileResource(Path path) implements ResolvedResource {}

        record DirectoryListing(Path path) implements ResolvedResource {}

        record NotFound() implements ResolvedResource {}
    }

    public StaticFileHandler {
        root = root.toAbsolutePath().normalize();
    }

    private static final String HTML_TEMPLATE = loadHtml();

    private static final String HTML_CONTENT_TYPE = "text/html";
    private static final String BINARY_CONTENT_TYPE = "application/octet-stream";

    @Override
    public void handleRequest(ResponseWriter writer, HttpRequest request) throws IOException {
        switch (request.requestLine().method()) {
            case GET -> handleGet(writer, request);
            case HEAD -> handleHead(writer, request);
            default -> throw new IllegalStateException("must be GET or HEAD");
        }
    }

    private void handleGet(ResponseWriter writer, HttpRequest request) throws IOException {
        switch (resolveResource(request)) {
            case ResolvedResource.DirectoryListing directoryListing -> {
                var content = renderDirectoryListing(directoryListing.path());
                writeString(writer, request, content);
            }
            case ResolvedResource.FileResource fileResource -> writeFile(writer, request, fileResource.path());
            case ResolvedResource.NotFound _ -> writeNotFound(writer, request);
        }
    }

    private void handleHead(ResponseWriter writer, HttpRequest request) throws IOException {
        switch (resolveResource(request)) {
            case ResolvedResource.DirectoryListing directoryListing -> {
                var content = renderDirectoryListing(directoryListing.path());
                writeStringHeaders(writer, request, content);
            }
            case ResolvedResource.FileResource fileResource -> writeFileHeaders(writer, request, fileResource.path());
            case ResolvedResource.NotFound _ -> writeNotFound(writer, request);
        }
    }

    private ResolvedResource resolveResource(HttpRequest request) throws IOException {
        var requestPath = request.path();
        var resolvedTarget = resolveRequestPath(root, requestPath);

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

    void writeFile(ResponseWriter writer, HttpRequest request, Path path) throws IOException {
        var content = Files.readAllBytes(path);
        var headers = createFileHeaders(path);
        writer.write(new HttpResponse(request.requestLine().version(), HttpStatus.OK, headers, content));
    }

    void writeFileHeaders(ResponseWriter writer, HttpRequest request, Path path) throws IOException {
        var headers = createFileHeaders(path);
        var response = new HttpResponse(request.requestLine().version(), HttpStatus.OK, headers);
        writer.write(response);
    }

    void writeString(ResponseWriter writer, HttpRequest request, String content) throws IOException {
        var data = content.getBytes(StandardCharsets.UTF_8);
        var contentLength = data.length;
        var headers = createFileHeaders(contentLength, HTML_CONTENT_TYPE);
        writer.write(new HttpResponse(request.requestLine().version(), HttpStatus.OK, headers, data));
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

        if (!directory.equals(root)) {
            appendDirectoryEntryHtml(entries, toRootRelativeUrl(root, directory.getParent()), "..");
        }

        try (var paths = Files.list(directory)) {
            for (Path path : paths.sorted().toList()) {
                appendDirectoryEntryHtml(entries, toRootRelativeUrl(root, path), path.getFileName().toString());
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
}
