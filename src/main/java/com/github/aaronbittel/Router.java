package com.github.aaronbittel;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An HTTP router that dispatches requests to handlers registered for exact paths or subtree paths.
 *
 * <p>Paths registered without a trailing slash are treated as exact routes. Paths registered with a trailing slash
 * are treated as subtree routes, matching the registered base path and all descendant paths.
 *
 * <p>Requests are resolved in the following order:
 * <ol>
 *     <li>An exact route matching the request path is considered first.</li>
 *     <li>If no exact route matches and appending a trailing slash produces a registered subtree base path, the request
 *     is redirected to that canonical path.</li>
 *     <li>Otherwise, the most specific matching subtree route is selected.</li>
 *     <li>If no route matches, a {@code 404 Not Found} response is returned.</li>
 *     <li>If a route matches but does not support the request method, a {@code 405 Method Not Allowed} response
 *     is returned with an {@code Allow} header.</li>
 *     <li>Otherwise, the selected handler processes the request.</li>
 * </ol>
 *
 * <p>When a route supports {@code GET} but does not explicitly register {@code HEAD}, the {@code GET} handler is
 * used to handle {@code HEAD} requests. The {@code Allow} header includes {@code HEAD} whenever {@code GET} is
 * supported.
 *
 * <p>Trailing-slash redirects are based on the registered subtree base path and are independent of the request
 * method. Redirect responses use {@code 301 Moved Permanently}. The redirect location currently contains the
 * canonical path; query-string preservation is not implemented here unless handled elsewhere.
 */
public class Router implements HttpHandler {

    private final Map<String, Map<HttpMethod, HttpHandler>> exactRoutes = new HashMap<>();
    private final Map<String, Map<HttpMethod, HttpHandler>> subtreeRoutes = new HashMap<>();

    /**
     * Handles an HTTP request by resolving its route and dispatching it to the selected handler or writing the
     * appropriate error or redirect response.
     *
     * @param writer  the writer used to produce the HTTP response
     * @param request the incoming HTTP request
     * @throws IOException if writing the response or invoking the selected handler fails
     */
    @Override
    public void handleRequest(ResponseWriter writer, HttpRequest request) throws IOException {
        switch (resolveRequest(request)) {
            case RoutingResult.Dispatch(var handler) -> handler.handleRequest(writer, request);
            case RoutingResult.MethodNotAllowed(var allowedMethods) ->
                    writeMethodNotAllowed(writer, request, allowedMethods);
            case RoutingResult.NotFound() -> writeNotFound(writer, request);
            case RoutingResult.Redirect(var location) -> writeRedirect(writer, request, location);
        }
    }

    /**
     * Registers a handler for an HTTP method and path.
     *
     * <p>A path ending with {@code /} registers a subtree route. It matches the registered base path and all
     * descendant paths. A path without a trailing slash registers an exact route that matches only that path.
     *
     * <p>An exact route and a subtree route may be registered for the same base path. Each HTTP method may be
     * registered at most once for a given route.
     *
     * @param method  the HTTP method to register
     * @param path    the route path; must start with {@code /}. A trailing slash enables subtree matching
     * @param handler the handler invoked when the route matches and supports the request method
     * @return this router instance, allowing method chaining
     * @throws IllegalArgumentException if the path does not start with {@code /}, or the method and path are already
     *                                  registered
     */
    public Router add(HttpMethod method, String path, HttpHandler handler) {
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException("Route path must start with '/': " + path);
        }

        var routes = path.endsWith("/") ? subtreeRoutes : exactRoutes;

        var methods = routes.computeIfAbsent(path, _ -> new HashMap<>());

        if (methods.putIfAbsent(method, handler) != null) {
            throw new IllegalArgumentException("Route already registered: " + method + " " + path);
        }

        return this;
    }

    /**
     * Resolves a request to a handler, redirect, or HTTP error result.
     *
     * <p>Exact routes are considered before trailing-slash redirects. Redirects are considered before subtree
     * dispatch so that a registered subtree base path without its trailing slash is redirected even when a broader
     * subtree route also matches the request.
     *
     * @param request the request to resolve
     * @return the routing result for the request
     */
    private RoutingResult resolveRequest(HttpRequest request) {
        var result = lookupExactRoute(request);
        if (result.isPresent()) {
            return result.orElseThrow();
        }

        result = lookupTrailingSlashRedirect(request);
        if (result.isPresent()) {
            return result.orElseThrow();
        }

        result = lookupSubtreeRoute(request);
        if (result.isPresent()) {
            return result.orElseThrow();
        }

        return new RoutingResult.NotFound();
    }

    /**
     * Looks up an exact route for the request path and resolves its HTTP method.
     *
     * <p>Requests whose paths end with {@code /} are not considered for exact-route matching.
     *
     * @param request the request to resolve
     * @return a routing result if an exact route exists, or empty otherwise
     */
    private Optional<RoutingResult> lookupExactRoute(HttpRequest request) {
        if (request.path().endsWith("/")) {
            return Optional.empty();
        }

        return findExactRoute(request.path()).map(route -> resolveMatchedRoute(route, request));
    }

    /**
     * Looks up the most specific subtree route matching the request path and resolves its HTTP method.
     *
     * @param request the request to resolve
     * @return a routing result if a subtree route matches, or empty otherwise
     */
    private Optional<RoutingResult> lookupSubtreeRoute(HttpRequest request) {
        return findLongestMatchingSubtreeRoute(request.path()).map(route -> resolveMatchedRoute(route, request));
    }

    /**
     * Resolves the HTTP method for a route that has already matched by path.
     *
     * <p>If the route supports the request method, the result dispatches to its handler. Otherwise, the result
     * indicates that the method is not allowed and includes the methods supported by the route.
     *
     * @param route   the method-to-handler mapping for the matched route
     * @param request the request being resolved
     * @return a dispatch result or a method-not-allowed result
     */
    private RoutingResult resolveMatchedRoute(Map<HttpMethod, HttpHandler> route, HttpRequest request) {
        var handler = findHandler(route, request.method());

        if (handler.isEmpty()) {
            return new RoutingResult.MethodNotAllowed(getAllowedMethods(route));
        }

        return new RoutingResult.Dispatch(handler.orElseThrow());
    }

    /**
     * Checks whether appending a trailing slash to the request path produces a registered subtree base path.
     *
     * <p>The lookup requires an exact subtree registration for the slash-terminated path. A match against a broader
     * subtree prefix is not sufficient to trigger a redirect.
     *
     * @param request the request to check
     * @return a redirect result if the canonical subtree base is registered, or empty otherwise
     */
    private Optional<RoutingResult> lookupTrailingSlashRedirect(HttpRequest request) {
        var path = request.path();

        if (path.endsWith("/")) {
            return Optional.empty();
        }

        var canonicalPath = path + "/";

        if (!subtreeRoutes.containsKey(canonicalPath)) {
            return Optional.empty();
        }

        return Optional.of(new RoutingResult.Redirect(canonicalPath));
    }

    /**
     * Finds a handler for the specified HTTP method.
     *
     * <p>If the requested method is {@code HEAD} and no explicit {@code HEAD} handler exists, the {@code GET} handler
     * is returned as a fallback. The response-writing layer must ensure that a {@code HEAD} response contains no body.
     *
     * @param routes the method-to-handler mapping for a route
     * @param method the requested HTTP method
     * @return the matching handler, or empty if the route does not support the method
     */
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
     * Finds the exact route registered for the specified path.
     *
     * @param path the path to look up
     * @return the route's method-to-handler mapping, or empty if no exact route is registered
     */
    private Optional<Map<HttpMethod, HttpHandler>> findExactRoute(String path) {
        return Optional.ofNullable(exactRoutes.get(path));
    }

    /**
     * Finds the most specific registered subtree route matching the specified path.
     *
     * <p>A subtree route matches when the request path starts with its registered prefix. Because registered subtree
     * prefixes end with {@code /}, matching respects path-segment boundaries. If multiple subtree routes match, the
     * longest registered prefix is selected.
     *
     * <p>This method matches by path only and does not consider HTTP methods.
     *
     * @param path the request path to match
     * @return the method-to-handler mapping for the longest matching subtree route, or empty if no route matches
     */
    private Optional<Map<HttpMethod, HttpHandler>> findLongestMatchingSubtreeRoute(String path) {
        String longestMatchingRoute = null;

        for (var entry : subtreeRoutes.entrySet()) {
            var prefix = entry.getKey();
            if (!path.startsWith(prefix)) {
                continue;
            }
            if (longestMatchingRoute == null || prefix.length() > longestMatchingRoute.length()) {
                longestMatchingRoute = prefix;
            }
        }

        return longestMatchingRoute == null ? Optional.empty() : Optional.of(subtreeRoutes.get(longestMatchingRoute));
    }

    /**
     * Writes a {@code 404 Not Found} response.
     *
     * @param writer  the writer used to produce the response
     * @param request the request whose HTTP version is used for the response
     * @throws IOException if writing the response fails
     */
    private void writeNotFound(ResponseWriter writer, HttpRequest request) throws IOException {
        writer.write(new HttpResponse(request.version(), HttpStatus.NOT_FOUND));
    }

    /**
     * Writes a {@code 405 Method Not Allowed} response with an {@code Allow} header listing the supported methods.
     *
     * @param writer         the writer used to produce the response
     * @param request        the request whose HTTP version is used for the response
     * @param allowedMethods the methods supported by the matched route
     * @throws IOException if writing the response fails
     */
    private void writeMethodNotAllowed(ResponseWriter writer, HttpRequest request, Set<HttpMethod> allowedMethods) throws IOException {
        var allowHeaderValue = allowedMethods.stream().map(HttpMethod::name).sorted().collect(Collectors.joining(", "));
        var headers = new HttpHeaders().add("Allow", allowHeaderValue);
        writer.write(new HttpResponse(request.version(), HttpStatus.METHOD_NOT_ALLOWED, headers, new byte[0]));
    }

    /**
     * Writes a {@code 301 Moved Permanently} redirect response.
     *
     * @param writer   the writer used to produce the response
     * @param request  the request whose HTTP version is used for the response
     * @param location the target path supplied in the {@code Location} header
     * @throws IOException if writing the response fails
     */
    private void writeRedirect(ResponseWriter writer, HttpRequest request, String location) throws IOException {
        writer.write(new HttpResponse(request.version(), HttpStatus.MOVED_PERMANENTLY, new HttpHeaders().add("Location", location), new byte[0]));
    }

    /**
     * Returns the methods allowed by a matched route.
     *
     * <p>The returned set includes all explicitly registered methods and adds {@code HEAD} whenever {@code GET} is
     * registered, because this router supports {@code HEAD} through the {@code GET} handler.
     *
     * @param routes the method-to-handler mapping for a route
     * @return the methods allowed for the route
     */
    private Set<HttpMethod> getAllowedMethods(Map<HttpMethod, HttpHandler> routes) {
        var allowedMethods = new HashSet<>(routes.keySet());

        if (allowedMethods.contains(HttpMethod.GET)) {
            allowedMethods.add(HttpMethod.HEAD);
        }

        return allowedMethods;
    }

    /** Represents the outcome of resolving an HTTP request. */
    private sealed interface RoutingResult {

        /**
         * Indicates that a handler has been selected for the request.
         *
         * @param handler the selected request handler
         */
        record Dispatch(HttpHandler handler) implements RoutingResult {}

        /**
         * Indicates that the request should be redirected to a canonical path.
         *
         * @param location the redirect target path
         */
        record Redirect(String location) implements RoutingResult {}

        /**
         * Indicates that no registered route matches the request path.
         */
        record NotFound() implements RoutingResult {}

        /**
         * Indicates that a route matches the request path but does not support its HTTP method.
         *
         * @param allowedMethods the methods supported by the matched route, including {@code HEAD} when {@code GET} is
         *                       supported
         */
        record MethodNotAllowed(Set<HttpMethod> allowedMethods) implements RoutingResult {
            public MethodNotAllowed {
                allowedMethods = Set.copyOf(allowedMethods);
            }
        }
    }
}
