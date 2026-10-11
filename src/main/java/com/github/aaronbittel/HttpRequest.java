package com.github.aaronbittel;

import java.util.Arrays;
import java.util.Objects;

public record HttpRequest(HttpRequestLine requestLine, HttpHeaders headers, byte[] body) {

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
