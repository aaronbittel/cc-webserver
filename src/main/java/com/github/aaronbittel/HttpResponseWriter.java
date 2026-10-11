package com.github.aaronbittel;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class HttpResponseWriter {

    private final OutputStream out;

    public HttpResponseWriter(OutputStream out) {
        this.out = out;
    }

    public void write(HttpResponse response) throws IOException {
        var responseLine = "%s %d %s\r\n\r\n".formatted(
                response.version().protocolString(),
                response.status().code(),
                response.status().text());

        out.write(responseLine.getBytes(StandardCharsets.US_ASCII));
        out.write(response.body());
        out.flush();
    }
}
