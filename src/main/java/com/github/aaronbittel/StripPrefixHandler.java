package com.github.aaronbittel;

import java.io.IOException;

public record StripPrefixHandler(HttpHandler next, String prefix) implements HttpHandler {

    public StripPrefixHandler {
        if (prefix.endsWith("/")) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
    }

    @Override
    public void handleRequest(ResponseWriter writer, HttpRequest request) throws IOException {
        if (!matchesPrefix(request.path())) {
            writer.write(new HttpResponse(request.version(), HttpStatus.NOT_FOUND));
            return;
        }

        var strippedPath = request.path().substring(prefix.length());
        if (strippedPath.isEmpty()) {
            strippedPath = "/";
        }
        next.handleRequest(writer, request.withURL(request.requestUrl().withPath(strippedPath)));
    }

    private boolean matchesPrefix(String path) {
        if (!path.startsWith(prefix)) {
            return false;
        }

        return path.length() == prefix.length() || path.charAt(prefix.length()) == '/';
    }
}
