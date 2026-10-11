package com.github.aaronbittel;

public record HttpResponse(HttpVersion version, HttpStatus status, HttpHeaders headers, byte[] body) {

    public HttpResponse(HttpVersion version, HttpStatus status) {
        this(version, status, new HttpHeaders(), EMPTY_BODY);
    }

    public static final byte[] EMPTY_BODY = new byte[0];
}
