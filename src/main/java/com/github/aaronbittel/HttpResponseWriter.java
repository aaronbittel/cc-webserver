package com.github.aaronbittel;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class HttpResponseWriter implements ResponseWriter {

    private final OutputStream out;

    public HttpResponseWriter(OutputStream out) {
        this.out = out;
    }

    @Override
    public void write(HttpResponse response) throws IOException {
        writeStatusLine(response.version(), response.status());

        for (var header : response.headers()) {
            writeHeaderLine(header.getKey(), header.getValue());
        }

        writeCrlf();
        out.write(response.body());

        out.flush();
    }

    private void writeStatusLine(HttpVersion version, HttpStatus status) throws IOException {
        writeAscii(version.protocolString());
        out.write(' ');
        writeAscii(String.valueOf(status.code()));
        out.write(' ');
        writeAscii(status.text());
        writeCrlf();
    }

    private void writeHeaderLine(String key, String value) throws IOException {
        writeAscii(key);
        out.write(':');
        out.write(' ');
        writeAscii(value);
        writeCrlf();
    }

    private void writeAscii(String value) throws IOException {
        out.write(value.getBytes(StandardCharsets.US_ASCII));
    }

    private void writeCrlf() throws IOException {
        out.write('\r');
        out.write('\n');
    }
}
