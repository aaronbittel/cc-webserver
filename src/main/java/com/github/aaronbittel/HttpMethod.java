package com.github.aaronbittel;

public enum HttpMethod {
    GET,
    POST,
    PUT,
    DELETE;

    static HttpMethod parse(String method) {
        return switch (method) {
            case "GET" -> HttpMethod.GET;
            case "POST" -> HttpMethod.POST;
            case "PUT" -> HttpMethod.PUT;
            case "DELETE" -> HttpMethod.DELETE;
            default -> throw new IllegalStateException("Illegal http method: " + method);
        };
    }
}
