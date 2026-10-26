package com.github.aaronbittel;

import java.util.List;

public class MiddlewareChain implements Middleware {

    private final List<Middleware> middlewares;

    public MiddlewareChain(Middleware... middlewares) {
        this.middlewares = List.of(middlewares);
    }

    @Override
    public HttpHandler wrap(HttpHandler next) {
        var result = next;
        for (var middleware : middlewares.reversed()) {
            result = middleware.wrap(result);
        }
        return result;
    }
}
