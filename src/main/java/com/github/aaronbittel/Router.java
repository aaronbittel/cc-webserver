package com.github.aaronbittel;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class Router implements HttpHandler {

    private final Map<String, Map<HttpMethod, HttpHandler>> routes = new HashMap<>();

    @Override
    public void handleRequest(ResponseWriter writer, HttpRequest request) throws IOException {
        var route = findRoute(request.path());
        if (route.isEmpty()) {
            writeNotFound(writer, request);
            return;
        }

        var routes = route.orElseThrow();

        if (!routes.containsKey(request.method())) {
            writeMethodNotAllowed(writer, request);
            return;
        }

        routes.get(request.method()).handleRequest(writer, request);
    }

    /**
     * Registers a handler for the specified HTTP method and path.
     * A path ending with {@code /} performs a prefix match, matching the
     * path itself and any nested paths. A path without a trailing slash
     * performs an exact match.
     *
     * @param method  the HTTP method to match
     * @param path    the path to match; a trailing slash enables prefix matching
     * @param handler the handler to invoke when the request matches
     * @return this router instance, for method chaining
     */
    public Router add(HttpMethod method, String path, HttpHandler handler) {
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException("Route path must start with '/': " + path);
        }

        var methods = routes.computeIfAbsent(path, _ -> new HashMap<>());

        if (methods.putIfAbsent(method, handler) != null) {
            throw new IllegalArgumentException("Route already registered: " + method + " " + path);
        }

        return this;
    }

    private Optional<Map<HttpMethod, HttpHandler>> findRoute(String path) {
        if (routes.containsKey(path)) {
            return Optional.of(routes.get(path));
        }

        var currentPath = path;
        while (!(currentPath = chopTillRightSlash(currentPath)).isEmpty()) {
            if (routes.containsKey(currentPath)) {
                return Optional.of(routes.get(currentPath));
            }
        }

        return Optional.empty();
    }

    private String chopTillRightSlash(String path) {
        if (path.charAt(path.length() - 1) == '/') {
            path = path.substring(0, path.length() - 1);
        }

        var index = path.lastIndexOf('/');
        if (index == -1) {
            return "";
        }
        return path.substring(0, index + 1);
    }

    private void writeNotFound(ResponseWriter writer, HttpRequest request) throws IOException {
        writer.write(new HttpResponse(
                request.version(),
                HttpStatus.NOT_FOUND
        ));
    }

    private void writeMethodNotAllowed(ResponseWriter writer, HttpRequest request) throws IOException {
        writer.write(new HttpResponse(
                request.version(),
                HttpStatus.METHOD_NOT_ALLOWED
        ));
    }
}
