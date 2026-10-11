package com.github.aaronbittel;

public enum HttpVersion {
    HTTP_1_0("HTTP/1.0"),
    HTTP_1_1("HTTP/1.1");

    private final String protocolString;

    HttpVersion(String protcolString) {
        this.protocolString = protcolString;
    }

    public String protocolString() {
        return this.protocolString;
    }

    static HttpVersion parse(String version) {
        return switch (version) {
            case "HTTP/1.1" -> HttpVersion.HTTP_1_1;
            case "HTTP/1.0" -> HttpVersion.HTTP_1_0;
            default -> throw new IllegalArgumentException("Illegal http version: " + version);
        };
    }
}
