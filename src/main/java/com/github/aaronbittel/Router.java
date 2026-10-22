package com.github.aaronbittel;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * An HTTP router that dispatches requests to handlers registered for exact paths, subtree paths, parameterized paths,
 * or mounted routers.
 *
 * <p>Routes registered through {@link #add(HttpMethod, String, HttpHandler)} are classified by their pattern:
 * paths without a trailing slash are exact routes, paths with a trailing slash are subtree routes, and paths
 * containing a path parameter such as {@code /users/{id}} are parameter routes. A subtree route matches its
 * registered base path and all descendant paths. A parameter route matches requests with the same path structure,
 * capturing the corresponding path segment as a parameter.
 *
 * <p>Parameter names do not affect route identity. For example, {@code /users/{id}} and {@code /users/{name}}
 * represent the same parameter route and cannot be registered separately for the same HTTP method. Different HTTP
 * methods may be registered for the same parameter route. The parameter names from the first registration are
 * retained for request matching and parameter extraction.
 *
 * <p>Routers can also be mounted through {@link #mount(String, Router)}. A mount delegates matching requests to
 * another router after stripping the mount prefix from the request path.
 *
 * <p>Requests are resolved in the following order:
 * <ol>
 *     <li>An exact route matching the request path is considered first.</li>
 *     <li>If appending a trailing slash produces a registered subtree base path or mount prefix, the request is
 *     redirected to that canonical path.</li>
 *     <li>Otherwise, the longest matching prefix among subtree routes and mounts is selected.</li>
 *     <li>For a selected subtree route, the HTTP method is resolved. If the method is unsupported, a
 *     {@code 405 Method Not Allowed} response is returned. Mounted routers handle their own routing.</li>
 *     <li>If no route matches, a {@code 404 Not Found} response is returned.</li>
 * </ol>
 *
 * <p>If a route supports {@code GET} but does not explicitly register {@code HEAD}, its {@code GET} handler is used
 * for {@code HEAD} requests. The {@code Allow} header includes {@code HEAD} whenever {@code GET} is supported.
 *
 * <p>Trailing-slash redirects are independent of the request method and use {@code 301 Moved Permanently}. The
 * redirect location contains the canonical path.
 */
public class Router implements HttpHandler {

    private final Map<String, Map<HttpMethod, HttpHandler>> exactRoutes = new HashMap<>();
    private final Map<String, Map<HttpMethod, HttpHandler>> subtreeRoutes = new HashMap<>();
    private final Map<String, HttpHandler> mountRoutes = new HashMap<>();
    private final Map<String, RegisteredParameterRoute> parameterRoutes = new HashMap<>();

    private static final String TERMINAL_WILDCARD_PATTERN = "/{$}";

    /**
     * Registers a handler for an HTTP method and path.
     *
     * <p>A path without a trailing slash registers an exact route, matching only that path. A path ending in
     * {@code /} registers a subtree route, matching its base path and all descendant paths. The path {@code /}
     * registers a subtree route matching all paths.
     *
     * <p>A path containing a parameter segment in braces, such as {@code /users/{id}}, registers a parameter route.
     * A parameter matches one non-empty path segment. Parameter routes are identified by their path structure rather
     * than their parameter names. For example, {@code /users/{id}} and {@code /users/{name}} represent the same route.
     * Registering the same parameter route for the same HTTP method more than once is rejected, while registering
     * different HTTP methods for that route is allowed. The parameter names from the first registration are retained.
     *
     * <p>The terminal wildcard pattern {@code {$}} registers an exact route. The wildcard is removed before
     * registration, so {@code /users/{$}} registers an exact route for {@code /users}, and {@code /{$}} registers an
     * exact route for {@code /}.
     *
     * <p>The terminal wildcard must be the final path segment and cannot be followed by a trailing slash. Path
     * parameters must also be the final segment and cannot be followed by a trailing slash.
     *
     * <p>An exact route and a subtree route may coexist for the same logical base path. For example, {@code /users}
     * and {@code /users/} are distinct registrations. An exact route takes precedence for its exact path.
     *
     * <p>Each HTTP method may be registered at most once for a given route. A subtree route cannot be registered at
     * the same prefix as a mount.
     *
     * @param method  the HTTP method to register
     * @param path    the route pattern; must start with {@code /} and contain no empty, {@code .}, or {@code ..} literal
     *                path segments
     * @param handler the handler invoked when the route matches and supports the request method
     * @return this router instance, allowing method chaining
     * @throws IllegalArgumentException if the route pattern is invalid, the method and route are already registered, or
     *                                  the subtree route conflicts with a mount
     */
    public Router add(HttpMethod method, String path, HttpHandler handler) {
        switch (RoutePatternParser.parse(path)) {
            case RoutePattern.ExactRoute(var route) -> registerRoute(exactRoutes, route, handler, method);
            case RoutePattern.SubtreeRoute(var route) -> {
                if (mountRoutes.containsKey(route)) {
                    throw new IllegalArgumentException("Duplicated path: " + route);
                }
                registerRoute(subtreeRoutes, route, handler, method);
            }
            case RoutePattern.ParameterRoute parameterRoute -> {
                var key = parameterRoute.canonical();

                var route = parameterRoutes.computeIfAbsent(key, _ -> new RegisteredParameterRoute(parameterRoute, new HashMap<>()));
                if (route.handlers().putIfAbsent(method, handler) != null) {
                    throw new IllegalArgumentException("Duplicate route: %s conflicts with %s"
                            .formatted(path, parameterRoutes.get(key).route().path()));
                }
            }
        }

        return this;
    }

    /**
     * Mounts another router under the specified prefix.
     *
     * <p>The prefix must start with {@code /}. A trailing slash is added if necessary, so {@code /users} and
     * {@code /users/} identify the same mount prefix.
     *
     * <p>Requests matching the mount prefix are delegated to the mounted router through a handler that strips the
     * prefix before forwarding the request. For example, a request to {@code /users/profile} mounted at {@code /users/}
     * is forwarded with the path {@code /profile}.
     *
     * <p>A request to the mount base without its trailing slash, such as {@code /users}, is redirected to
     * {@code /users/}. A mount cannot share its normalized prefix with another mount or a subtree route. Exact routes
     * without a trailing slash may coexist with mounts.
     *
     * @param prefix the mount prefix; must start with {@code /}
     * @param router the router to mount
     * @return this router instance, allowing method chaining
     * @throws IllegalArgumentException if the prefix does not start with {@code /}, a mount already exists at the
     *                                  normalized prefix, or a subtree route is registered at that prefix
     */
    public Router mount(String prefix, Router router) {
        validateMountPath(prefix);

        if (!isSubtreePattern(prefix)) {
            prefix += "/";
        }

        if (mountRoutes.containsKey(prefix)) {
            throw new IllegalArgumentException("Mount already registered: " + prefix);
        }

        if (subtreeRoutes.containsKey(prefix)) {
            throw new IllegalArgumentException("Duplicated path: " + prefix);
        }

        mountRoutes.put(prefix, new StripPrefixHandler(router, prefix.substring(0, prefix.length() - 1)));

        return this;
    }

    /**
     * Resolves an incoming request and dispatches it to the selected handler, or writes the appropriate redirect or
     * error response.
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
                    HttpResponses.methodNotAllowed(writer, allowedMethods);
            case RoutingResult.NotFound() -> HttpResponses.notFound(writer);
            case RoutingResult.Redirect(var location) -> HttpResponses.redirect(writer, location);
        }
    }

    /**
     * Resolves a request using exact-route precedence, trailing-slash redirects, and longest-prefix matching.
     *
     * <p>Method resolution for exact routes happens as part of the exact-route lookup. For subtree routes, path
     * matching takes place before method resolution, ensuring that an unsupported method on a more-specific route does
     * not fall back to a broader route.
     *
     * @param request the request to resolve
     * @return the routing result for the request
     */
    private RoutingResult resolveRequest(HttpRequest request) {
        var result = lookupExactRoute(request);
        if (result.isPresent()) {
            return result.orElseThrow();
        }

        result = lookupParameterRoute(request);
        if (result.isPresent()) {
            return result.orElseThrow();
        }

        result = lookupTrailingSlashRedirect(request);
        if (result.isPresent()) {
            return result.orElseThrow();
        }

        result = lookupLongestPrefixRoute(request);
        if (result.isPresent()) {
            return result.orElseThrow();
        }

        return new RoutingResult.NotFound();
    }

    private Optional<RoutingResult> lookupParameterRoute(HttpRequest request) {
        if (request.path().endsWith("/")) { // subtree route cant match
            return Optional.empty();
        }

        var requestSegments = request.path().substring(1).split("/", -1);

        for (var parameterRoute : parameterRoutes.values()) {
            var route = parameterRoute.route();
            if (route.segments().size() != requestSegments.length) {
                continue;
            }

            if (machtesParameterRoute(route, requestSegments)) {
                request.setPathValue(route.parameterName(), requestSegments[requestSegments.length - 1]);
                return Optional.of(resolveMatchedRoute(parameterRoute.handlers(), request));
            }
        }

        return Optional.empty();
    }

    private static boolean machtesParameterRoute(RoutePattern.ParameterRoute route, String[] requestSegments) {
        for (int i = 0; i < route.segments().size(); i++) {
            var routeSegment = route.segments().get(i);
            var requestSegment = requestSegments[i];

            switch (routeSegment) {
                case RouteSegment.Literal(var value) -> {
                    if (!requestSegment.equals(value)) {
                        return false;
                    }
                }
                case RouteSegment.Parameter _ -> {}
                case RouteSegment.TerminalWildcard _ -> throw new IllegalStateException(
                        "Paramter Routes must not conatain a Terminal Wildcard");
            }
        }
        return true;
    }

    /**
     * Looks up an exact route for the request path and resolves its HTTP method.
     *
     * @param request the request to resolve
     * @return a routing result if an exact route exists, or empty otherwise
     */
    private Optional<RoutingResult> lookupExactRoute(HttpRequest request) {
        return findExactRoute(request.path())
                .map(route -> resolveMatchedRoute(route, request));
    }

    /**
     * Checks whether appending a trailing slash to the request path produces a registered subtree base path or mount
     * prefix.
     *
     * <p>The lookup requires an exact registration for the canonical path. A match against a broader subtree or mount
     * prefix is not sufficient to trigger a redirect.
     *
     * @param request the request to check
     * @return a redirect result if the canonical path is registered, or empty otherwise
     */
    private Optional<RoutingResult> lookupTrailingSlashRedirect(HttpRequest request) {
        var path = request.path();

        if (isSubtreePattern(path)) {
            return Optional.empty();
        }

        var canonicalPath = path + "/";

        if (subtreeRoutes.containsKey(canonicalPath) || mountRoutes.containsKey(canonicalPath)) {
            return Optional.of(new RoutingResult.Redirect(canonicalPath));
        }

        return Optional.empty();
    }

    /**
     * Selects the longest matching prefix across subtree routes and mounts.
     *
     * <p>Subtree routes and mounts compete on prefix length. The longer prefix wins, regardless of route type.
     * A selected subtree route is then resolved against the request method. A selected mount delegates the request to
     * its mounted router.
     *
     * @param request the request to resolve
     * @return a routing result if a subtree route or mount matches, or empty otherwise
     */
    private Optional<RoutingResult> lookupLongestPrefixRoute(HttpRequest request) {
        var subtreeMatch = findLongestMatchingSubtreeRoute(request.path());
        var mountMatch = findLongestMatchingMountRoute(request.path());

        if (subtreeMatch.isPresent() && mountMatch.isPresent()) {
            var sm = subtreeMatch.orElseThrow();
            var mm = mountMatch.orElseThrow();

            if (sm.prefix().length() > mm.prefix().length()) {
                var strippedPath = request.path().substring(sm.prefix().length());
                return Optional.of(resolveMatchedRoute(sm.methods(), request.withURL(request.requestUrl().withPath(strippedPath))));
            }

            return Optional.of(new RoutingResult.Dispatch(mm.handler()));
        }

        if (subtreeMatch.isPresent()) {
            var sm = subtreeMatch.orElseThrow();
            var strippedPath = request.path().substring(sm.prefix().length());
            return Optional.of(resolveMatchedRoute(sm.methods(), request.withURL(request.requestUrl().withPath(strippedPath))));
        }

        if (mountMatch.isPresent()) {
            var mm = mountMatch.orElseThrow();
            return Optional.of(new RoutingResult.Dispatch(mm.handler()));
        }

        return Optional.empty();
    }

    /**
     * Finds the longest registered prefix that matches the supplied path.
     *
     * <p>Matching uses {@link String#startsWith(String)}. Callers supply slash-terminated subtree and mount prefixes,
     * which preserves path-segment boundaries for normal registered prefixes.
     *
     * @param path     the request path
     * @param prefixes the registered prefixes to search
     * @return the longest matching prefix, or empty if none match
     */
    private Optional<String> findLongestMatchingPrefix(String path, Set<String> prefixes) {
        String longestMatch = null;

        for (var prefix : prefixes) {
            if (!path.startsWith(prefix)) {
                continue;
            }

            if (longestMatch == null || prefix.length() > longestMatch.length()) {
                longestMatch = prefix;
            }
        }

        return Optional.ofNullable(longestMatch);
    }

    /**
     * Finds the most-specific matching subtree route for a request path.
     *
     * @param path the request path
     * @return the matching subtree prefix and its method-to-handler mapping, or empty if none match
     */
    private Optional<SubtreeMatch> findLongestMatchingSubtreeRoute(String path) {
        return findLongestMatchingPrefix(path, subtreeRoutes.keySet())
                .map(prefix -> new SubtreeMatch(prefix, subtreeRoutes.get(prefix)));
    }

    /**
     * Finds the most-specific matching mount for a request path.
     *
     * @param path the request path
     * @return the matching mount prefix and its handler, or empty if none match
     */
    private Optional<MountMatch> findLongestMatchingMountRoute(String path) {
        return findLongestMatchingPrefix(path, mountRoutes.keySet())
                .map(prefix -> new MountMatch(prefix, mountRoutes.get(prefix)));
    }

    /**
     * Finds the exact route registered for the specified path.
     *
     * @param path the request path
     * @return the route's method-to-handler mapping, or empty if no exact route is registered
     */
    private Optional<Map<HttpMethod, HttpHandler>> findExactRoute(String path) {
        return Optional.ofNullable(exactRoutes.get(path));
    }

    /**
     * Resolves the HTTP method for a route that has already matched by path.
     *
     * <p>If the route supports the requested method, the result dispatches to its handler. Otherwise, the result
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
     * Finds a handler for the specified HTTP method.
     *
     * <p>If the requested method is {@code HEAD} and no explicit {@code HEAD} handler exists, the {@code GET} handler
     * is returned as a fallback. The response-writing layer must ensure that a {@code HEAD} response contains no body.
     *
     * @param routes the method-to-handler mapping for a matched route
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
     * Returns the methods allowed by a matched route.
     *
     * <p>The returned set includes all explicitly registered methods and adds {@code HEAD} whenever {@code GET} is
     * registered, because this router supports {@code HEAD} through the {@code GET} handler.
     *
     * @param routes the method-to-handler mapping for a matched route
     * @return the methods allowed for the route
     */
    private Set<HttpMethod> getAllowedMethods(Map<HttpMethod, HttpHandler> routes) {
        var allowedMethods = new HashSet<>(routes.keySet());

        if (allowedMethods.contains(HttpMethod.GET)) {
            allowedMethods.add(HttpMethod.HEAD);
        }

        return allowedMethods;
    }

    /**
     * Determines whether the given path represents a subtree pattern.
     *
     * @param path the route path to check
     * @return {@code true} if the path ends with a slash, {@code false} otherwise
     */
    private static boolean isSubtreePattern(String path) {
        return path.endsWith("/");
    }

    /**
     * Registers a handler for the specified HTTP method and path.
     *
     * @param routes  the route map in which to register the handler
     * @param path    the route path
     * @param handler the handler to register
     * @param method  the HTTP method associated with the handler
     * @throws IllegalArgumentException if a handler is already registered for the specified path and HTTP method
     */
    private void registerRoute(
            Map<String, Map<HttpMethod, HttpHandler>> routes,
            String path,
            HttpHandler handler,
            HttpMethod method
    ) {
        var methods = routes.computeIfAbsent(path, _ -> new HashMap<>());

        if (methods.putIfAbsent(method, handler) != null) {
            throw new IllegalArgumentException("Route already registered: " + method + " " + path);
        }
    }

    /**
     * Validates that a path is non-null and starts with {@code /}.
     *
     * @param path the path to validate
     * @throws IllegalArgumentException if the path is {@code null} or does not start with {@code /}
     */
    private static void validateAbsolutePath(String path) {
        if (path == null || !path.startsWith("/")) {
            throw new IllegalArgumentException("Path must start with '/': " + path);
        }
    }

    /**
     * Validates a mount prefix before mounting another router.
     *
     * <p>The prefix must be absolute and must not contain the terminal wildcard pattern.
     *
     * @param path the mount prefix to validate
     * @throws IllegalArgumentException if the prefix is invalid or contains the terminal wildcard pattern
     */
    private static void validateMountPath(String path) {
        validateAbsolutePath(path);

        if (path.contains(TERMINAL_WILDCARD_PATTERN)) {
            throw new IllegalArgumentException("Mount prefix must not contain wildcard patterns: " + path);
        }
    }

    /** A matched subtree route, including its registered prefix and method mappings. */
    private record SubtreeMatch(String prefix, Map<HttpMethod, HttpHandler> methods) {}

    /** A matched mount, including its registered prefix and forwarding handler. */
    private record MountMatch(String prefix, HttpHandler handler) {}

    /** Represents the outcome of resolving an HTTP request. */
    private sealed interface RoutingResult {

        /** Indicates that a handler has been selected for the request. */
        record Dispatch(HttpHandler handler) implements RoutingResult {}

        /** Indicates that the request should be redirected to a canonical path. */
        record Redirect(String location) implements RoutingResult {}

        /** Indicates that no registered route matches the request path. */
        record NotFound() implements RoutingResult {}

        /**
         * Indicates that a route matches the request path but does not support the request method.
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

    private record RegisteredParameterRoute(RoutePattern.ParameterRoute route, Map<HttpMethod, HttpHandler> handlers) {}
}
