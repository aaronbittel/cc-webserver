package com.github.aaronbittel;

import java.util.Set;
import java.util.stream.Collectors;

public final class HttpResponses {

    private HttpResponses() {}

    public static HttpResponse notFound(HttpVersion version) {
        return new HttpResponse(version, HttpStatus.NOT_FOUND);
    }

    public static HttpResponse methodNotAllowed(HttpVersion version, Set<HttpMethod> allowedMethods) {
        var allow = allowedMethods.stream()
                .map(HttpMethod::name)
                .sorted()
                .collect(Collectors.joining(", "));

        var headers = new HttpHeaders()
                .add("Allow", allow);

        return new HttpResponse(version, HttpStatus.METHOD_NOT_ALLOWED, headers, new byte[0]);
    }

    public static HttpResponse redirect(HttpVersion version, String location) {
        var headers = new HttpHeaders()
                .add("Location", location);

        return new HttpResponse(version, HttpStatus.MOVED_PERMANENTLY, headers, new byte[0]);
    }

    public static HttpResponse badRequest(HttpVersion version) {
        return new HttpResponse(version, HttpStatus.BAD_REQUEST);
    }
}
