package com.github.aaronbittel;

public record HttpResponse(HttpVersion version, HttpStatus status, byte[] body) {

    public static final byte[] EMPTY_BODY = new byte[0];
}
