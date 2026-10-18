package com.github.aaronbittel;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class Router implements HttpHandler {

    private final Map<String, Map<HttpMethod, HttpHandler>> routes = new HashMap<>();

    /**
     * Handles an HTTP request by resolving its path and dispatching it to the appropriate route handler.
     *
     * <p>Request processing follows these rules:
     * <ol>
     *     <li>An exact route matching the request path takes precedence.</li>
     *     <li>If no exact route matches and the request path is the base path of a registered subtree route, the
     *     request is redirected to the corresponding path with a trailing slash.</li>
     *     <li>Otherwise, the most specific matching subtree route is selected.</li>
     *     <li>If no route matches, a {@code 404 Not Found} response is written.</li>
     *     <li>If a route matches but does not support the request method, a {@code 405 Method Not Allowed} response
     *     is written.</li>
     *     <li>Otherwise, the selected handler processes the request.</li>
     * </ol>
     *
     * <p>Redirects preserve the query string. Redirect behavior must also account for the request method to avoid
     * unintentionally changing its semantics.
     *
     * @param writer  the writer used to produce the HTTP response
     * @param request the incoming HTTP request
     * @throws IOException if an I/O error occurs while processing the request
     */
    @Override
    public void handleRequest(ResponseWriter writer, HttpRequest request) throws IOException {
        switch (resolveRequest(request)) {
            case RequestResult.Handler(var handler) -> handler.handleRequest(writer, request);
            case RequestResult.MethodNotAllowed() -> writeMethodNotAllowed(writer, request);
            case RequestResult.NotFound() -> writeNotFound(writer, request);
            case RequestResult.Redirect(var location) -> writeRedirect(writer, request, location);
        }
    }

    /**
     * Registers a handler for the specified HTTP method and path. A path ending with {@code /} performs a subtree
     * match, matching the path itself and any nested paths. A path without a trailing slash performs an exact match.
     *
     * <p>Exact and subtree routes may be registered for the same base path. The exact route matches only the path
     * without a trailing slash, while the subtree route matches the slash-terminated path and its descendants.
     *
     * @param method  the HTTP method to match
     * @param path    the path to match; a trailing slash enables subtree matching
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

    private sealed interface RequestResult {
        record Handler(HttpHandler handler) implements RequestResult {}

        record Redirect(String location) implements RequestResult {}

        record NotFound() implements RequestResult {}

        record MethodNotAllowed() implements RequestResult {}
    }

    private RequestResult resolveRequest(HttpRequest request) {
        var route = findRoute(request.path());

        if (route.isPresent()) {
            var handler = findHandler(route.orElseThrow(), request.method());

            if (handler.isEmpty()) {
                return new RequestResult.MethodNotAllowed();
            }

            return new RequestResult.Handler(handler.orElseThrow());
        }

        if (!request.path().endsWith("/")) {
            var redirectRoute = findRoute(request.path() + "/");
            if (redirectRoute.isEmpty()) {
                return new RequestResult.NotFound();
            }
            return new RequestResult.Redirect(request.path() + "/");
        }
        return new RequestResult.NotFound();
    }

    private Optional<HttpHandler> findHandler(Map<HttpMethod, HttpHandler> routes, HttpMethod method) {
        if (routes.containsKey(method)) {
            return Optional.of(routes.get(method));
        }

        if (method != HttpMethod.HEAD) {
            return Optional.empty();
        }

        return Optional.ofNullable(routes.get(HttpMethod.GET));
    }

    /**
     * Finds the most specific route matching the given path.
     *
     * <p>Exact routes take precedence over subtree routes. An exact route matches only its registered path, while a
     * subtree route matches its slash-terminated base path and all descendant paths.
     *
     * <p>This method performs path matching only. It does not select a handler based on the HTTP method, perform
     * redirects, or write an HTTP response.
     *
     * @param path the request path to match
     * @return an {@code Optional} containing the HTTP-method-to-handler mapping for the most specific matching route,
     *         or empty if no route matches
     */
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

    private void writeNotFound(ResponseWriter writer, HttpRequest request) throws IOException {
        writer.write(new HttpResponse(request.version(), HttpStatus.NOT_FOUND));
    }

    private void writeMethodNotAllowed(ResponseWriter writer, HttpRequest request) throws IOException {
        writer.write(new HttpResponse(request.version(), HttpStatus.METHOD_NOT_ALLOWED));
    }

    private void writeRedirect(ResponseWriter writer, HttpRequest request, String location) throws IOException {
        writer.write(new HttpResponse(
                request.version(),
                HttpStatus.MOVED_PERMANENTLY,
                new HttpHeaders()
                        .add("Location", location),
                new byte[0]
        ));
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
}
