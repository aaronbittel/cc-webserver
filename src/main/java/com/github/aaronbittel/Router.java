package com.github.aaronbittel;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class Router implements HttpHandler {

    private final Map<String, Map<HttpMethod, HttpHandler>> routes = new HashMap<>();

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

    @Override
    public void handleRequest(ResponseWriter writer, HttpRequest request) throws IOException {
        var target = request.path();

        if (!routes.containsKey(target)) {
            writeNotFound(writer, request);
            return;
        }

        var method = request.requestLine().method();
        if (!routes.get(target).containsKey(method)) {
            writeMethodNotAllowed(writer, request);
            return;
        }

        routes.get(target).get(method).handleRequest(writer, request);
    }

    private void writeNotFound(ResponseWriter writer, HttpRequest request) throws IOException {
        writer.write(new HttpResponse(
                request.requestLine().version(),
                HttpStatus.NOT_FOUND
        ));
    }

    private void writeMethodNotAllowed(ResponseWriter writer, HttpRequest request) throws IOException {
        writer.write(new HttpResponse(
                request.requestLine().version(),
                HttpStatus.METHOD_NOT_ALLOWED
        ));
    }
}
