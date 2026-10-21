package com.github.aaronbittel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class HttpResponseRecorder implements ResponseWriter {

    private HttpStatus status = HttpStatus.OK;
    private final HttpHeaders headers = new HttpHeaders();
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    private HttpResponse response;

    @Override
    public void setStatus(HttpStatus status) {
        this.status = status;
    }

    @Override
    public HttpHeaders headers() {
        return headers;
    }

    @Override
    public void write(String text) throws IOException {
        buffer.write(text.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void write(byte[] data) throws IOException {
        buffer.write(data);
    }

    public HttpResponse result() {
        if (response != null) {
            return response;
        }

        return new HttpResponse(HttpVersion.HTTP_1_1, status, headers, buffer.toByteArray());
    }
}
