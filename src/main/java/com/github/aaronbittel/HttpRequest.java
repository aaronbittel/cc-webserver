package com.github.aaronbittel;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class HttpRequest {

    private final HttpRequestLine requestLine;
    private final HttpHeaders headers;
    private final byte[] body;
    private final RequestURL requestUrl;
    private final Map<String, String> pathParameters = new HashMap<>();

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

    public void setPathValue(String key, String value) {
        pathParameters.put(key, value);
    }

    public String pathValue(String key) {
        String value = pathParameters.get(key);

        if (value == null) {
            throw new IllegalArgumentException("Path parameter not found: " + key);
        }

        return value;
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

    public HttpHeaders headers() {
        return headers;
    }

    public InputStream body() {
        return new ByteArrayInputStream(body);
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
