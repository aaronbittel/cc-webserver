package com.github.aaronbittel;

public record HttpRequestLine(HttpMethod method, String requestTarget, HttpVersion version) {}
