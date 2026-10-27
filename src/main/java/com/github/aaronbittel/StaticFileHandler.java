package com.github.aaronbittel;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public record StaticFileHandler(Path root) implements HttpHandler {

    private sealed interface ResolvedResource {
        record FileResource(Path path) implements ResolvedResource {}

        record DirectoryListing(Path path) implements ResolvedResource {}

        record NotFound() implements ResolvedResource {}
    }

    public StaticFileHandler {
        root = root.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Root directory does not exist: " + root);
        }
    }

    private static final String HTML_TEMPLATE = loadHtml();

    @Override
    public void handleRequest(ResponseWriter writer, HttpRequest request) throws IOException {
        switch (resolveResource(request)) {
            case ResolvedResource.DirectoryListing(var path) -> {
                writer.headers().set("Content-Type", "text/html");
                writer.write(renderDirectoryListing(path));
            }
            case ResolvedResource.FileResource(var path) -> {
                var contentType = Objects.requireNonNullElse(Files.probeContentType(path), "application/octet-stream");
                writer.headers().set("Content-Type", contentType);
                writer.headers().set("Content-Length", String.valueOf(Files.size(path)));
                try (var in = Files.newInputStream(path)) {
                    writer.write(in);
                }
            }
            case ResolvedResource.NotFound _ -> HttpResponses.notFound(writer);
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

    String renderDirectoryListing(Path directory) throws IOException {
        var entries = generateDirectoryListingHtml(directory);
        return HTML_TEMPLATE.replace("{{entries}}", entries);
    }

    String generateDirectoryListingHtml(Path directory) throws IOException {
        var entries = new StringBuilder();

        if (!directory.equals(root)) {
            appendDirectoryEntryHtml(entries, "../");
        }

        try (var paths = Files.list(directory)) {
            for (Path path : paths.sorted().toList()) {
                appendDirectoryEntryHtml(entries, displayName(path));
            }
        }

        return entries.toString();
    }

    private static String displayName(Path path) {
        String name = path.getFileName().toString();
        return Files.isDirectory(path) ? name + "/" : name;
    }

    private void appendDirectoryEntryHtml(StringBuilder html, String path) {
        html.append("<li><a href=\"")
                .append(path)
                .append("\">")
                .append(path)
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
