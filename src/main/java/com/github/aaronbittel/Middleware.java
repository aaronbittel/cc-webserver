package com.github.aaronbittel;

@FunctionalInterface
public interface Middleware {
    HttpHandler wrap(HttpHandler next);
}
