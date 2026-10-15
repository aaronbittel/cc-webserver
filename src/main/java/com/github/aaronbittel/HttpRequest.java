package com.github.aaronbittel;

import java.util.Arrays;
import java.util.Objects;

public class HttpRequest {

    private final HttpRequestLine requestLine;
    private final HttpHeaders headers;
    private final byte[] body;
    private final RequestTarget requestTarget;

    public HttpRequest(HttpRequestLine requestLine, HttpHeaders headers, byte[] body) {
        this.requestLine = requestLine;
        this.headers = headers;
        this.body = body;
        this.requestTarget = RequestTarget.parse(requestLine.requestTarget());
    }

    public String path() {
        return requestTarget.path();
    }

    public HttpMethod method() {
        return requestLine.method();
    }

    public HttpVersion version() {
        return requestLine.version();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof HttpRequest that)) return false;
        return Objects.equals(requestLine, that.requestLine) && Objects.equals(headers, that.headers) && Arrays.equals(body, that.body);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requestLine, headers, Arrays.hashCode(body));
    }
}
