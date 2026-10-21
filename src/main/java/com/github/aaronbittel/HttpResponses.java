package com.github.aaronbittel;

import java.util.Set;
import java.util.stream.Collectors;

public final class HttpResponses {

    private HttpResponses() {}

    public static void notFound(ResponseWriter writer) {
        writer.setStatus(HttpStatus.NOT_FOUND);
    }

    public static void methodNotAllowed(ResponseWriter writer, Set<HttpMethod> allowedMethods) {
        var allow = allowedMethods.stream()
                .map(HttpMethod::name)
                .sorted()
                .collect(Collectors.joining(", "));

        writer.setStatus(HttpStatus.METHOD_NOT_ALLOWED);
        writer.headers().set("Allow", allow);
    }

    public static void redirect(ResponseWriter writer, String location) {
        writer.setStatus(HttpStatus.TEMPORARY_REDIRECT);
        writer.headers().set("Location", location);
    }

    public static void badRequest(ResponseWriter writer) {
        writer.setStatus(HttpStatus.BAD_REQUEST);
    }
}
