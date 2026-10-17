package com.github.aaronbittel;

import java.util.Arrays;
import java.util.Objects;

public class HttpRequest {

    private final HttpRequestLine requestLine;
    private final HttpHeaders headers;
    private final byte[] body;
    private final RequestURL requestUrl;

    public HttpRequest(HttpRequestLine requestLine, HttpHeaders headers, byte[] body) {
        this.requestLine = requestLine;
        this.headers = headers;
        this.body = body;
        this.requestUrl = RequestURL.parse(requestLine.requestTarget());
    }

    private HttpRequest(HttpRequestLine requestLine, HttpHeaders headers, byte[] body, RequestURL requestUrl) {
        this.requestLine = requestLine;
        this.headers = headers;
        this.body = body;
        this.requestUrl = requestUrl;
    }

    public HttpRequest withURL(RequestURL newUrl) {
        return new HttpRequest(requestLine, headers, body, newUrl);
    }

    public String path() {
        return requestUrl.path();
    }

    public HttpMethod method() {
        return requestLine.method();
    }

    public HttpVersion version() {
        return requestLine.version();
    }

    public HttpRequestLine requestLine() {
        return requestLine;
    }

    public RequestURL requestUrl() {
        return requestUrl;
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
