package com.github.aaronbittel;

public enum HttpMethod {
    GET,
    HEAD,
    POST,
    PUT,
    PATCH,
    OPTIONS,
    DELETE;

    static HttpMethod parse(String method) {
        return switch (method) {
            case "GET" -> HttpMethod.GET;
            case "HEAD" -> HttpMethod.HEAD;
            case "POST" -> HttpMethod.POST;
            case "PUT" -> HttpMethod.PUT;
            case "PATCH" -> HttpMethod.PATCH;
            case "OPTIONS" -> HttpMethod.OPTIONS;
            case "DELETE" -> HttpMethod.DELETE;
            default -> throw new IllegalArgumentException("Illegal http method: " + method);
        };
    }
}
