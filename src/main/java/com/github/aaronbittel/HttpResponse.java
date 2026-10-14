package com.github.aaronbittel;

import java.util.Arrays;
import java.util.Objects;

public record HttpResponse(HttpVersion version, HttpStatus status, HttpHeaders headers, byte[] body) {

    public HttpResponse(HttpVersion version, HttpStatus status) {
        this(version, status, new HttpHeaders(), new byte[0]);
    }

    public HttpResponse(HttpVersion version, HttpStatus status, HttpHeaders headers) {
        this(version, status, headers, new byte[0]);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof HttpResponse(HttpVersion version1, HttpStatus status1, HttpHeaders headers1, byte[] body1)))
            return false;
        return Arrays.equals(body, body1) && status == status1 && version == version1 && Objects.equals(headers, headers1);
    }

    @Override
    public int hashCode() {
        return Objects.hash(version, status, headers, Arrays.hashCode(body));
    }

    @Override
    public String toString() {
        return "HttpResponse[" +
                "version=" + version +
                ", status=" + status +
                ", headers=" + headers +
                ", bodyLength=" + body.length +
                ", bodyPreview=" +
                Arrays.toString(Arrays.copyOf(body, Math.min(body.length, 32))) +
                (body.length > 32 ? "..." : "") +
                "]";
    }
}
