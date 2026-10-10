package com.github.aaronbittel;

import java.io.IOException;
import java.io.InputStream;

public class HttpRequestParser {
    private final InputStream in;
    private static final int MAXIMUM_HTTP_LINE_LENGTH = 8192;

    public HttpRequestParser(InputStream in) {
        this.in = in;
    }

    HttpRequest parseRequest() throws IOException {
        var line = readHttpLine(in);
        var requestLine = parseRequestLine(line);
        return new HttpRequest(requestLine);
    }

    private static HttpRequestLine parseRequestLine(String line) {
        var parts = line.split(" ", 3);
        if (parts.length != 3) {
            throw new IllegalArgumentException("Illegal request line format: " + line);
        }

        var method = HttpMethod.parse(parts[0]);
        var requestTarget = parts[1];
        if (!requestTarget.startsWith("/")) {
            throw new IllegalArgumentException(
                    "Expected origin-form request target");
        }
        var version = HttpVersion.parse(parts[2]);

        return new HttpRequestLine(method, requestTarget, version);
    }

    private static String readHttpLine(InputStream in) throws IOException {
        var sb = new StringBuilder();

        int c;
        while ((c = in.read()) != -1) {
            switch (c) {
                case '\r' -> {
                    if (in.read() == '\n') {
                        return sb.toString();
                    }
                    throw new IOException("Invalid HTTP line ending");
                }
                case '\n' -> throw new IOException("Invalid HTTP line ending");
                default -> {
                    if (sb.length() >= MAXIMUM_HTTP_LINE_LENGTH) {
                        throw new IOException("HTTP line exceeds maximum length");
                    }
                    sb.append((char) c);
                }
            }
        }

        throw new IOException("Unexpected end of stream before HTTP line terminator");
    }
}
