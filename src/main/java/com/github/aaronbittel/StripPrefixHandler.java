package com.github.aaronbittel;

import java.io.IOException;

public record StripPrefixHandler(HttpHandler next, String prefix) implements HttpHandler {

    @Override
    public void handleRequest(ResponseWriter writer, HttpRequest request) throws IOException {
        if (!request.path().startsWith(prefix)) {
            writer.write(new HttpResponse(request.version(), HttpStatus.NOT_FOUND));
            return;
        }

        var strippedRequestTarget = request.requestLine().requestTarget().substring(prefix.length());
        var strippedRequest = request.withRequestTarget(strippedRequestTarget);
        next.handleRequest(writer, strippedRequest);
    }
}
