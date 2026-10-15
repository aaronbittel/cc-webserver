package com.github.aaronbittel;

import java.io.IOException;

@FunctionalInterface
public interface HttpHandler {

    void handleRequest(ResponseWriter writer, HttpRequest request) throws IOException;
}
