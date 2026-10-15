package com.github.aaronbittel;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

public record HttpRequest(HttpRequestLine requestLine, HttpHeaders headers, byte[] body) {

    public HttpRequest(HttpRequestLine requestLine, HttpHeaders headers) {
        this(requestLine, headers, new byte[0]);
    }

    public String path() {
        int index = requestLine.requestTarget().indexOf('?');
        if (index == -1) {
            return requestLine.requestTarget();
        }
        return requestLine.requestTarget().substring(0, index);
    }

    public Optional<String> query() {
        int index = requestLine.requestTarget().indexOf('?');
        if (index == -1) {
            return Optional.empty();
        }

        return Optional.of(requestLine.requestTarget().substring(index + 1));
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof HttpRequest(HttpRequestLine line, HttpHeaders otherHeaders, byte[] otherBody))) return false;
        return Objects.equals(requestLine, line)
                && Objects.equals(headers, otherHeaders)
                && Arrays.equals(body, otherBody);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requestLine, headers, Arrays.hashCode(body));
    }
}
