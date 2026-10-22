package com.github.aaronbittel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class HttpResponseWriter implements ResponseWriter {

    private HttpStatus status = HttpStatus.OK;
    private final HttpHeaders headers = new HttpHeaders();
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    private final boolean writeBody;
    private boolean calledFinish = false;

    private HttpResponseWriter(boolean writeBody) {
        this.writeBody = writeBody;
    }

    public static HttpResponseWriter forRequest(HttpMethod method) {
        return new HttpResponseWriter(method != HttpMethod.HEAD);
    }

    public static HttpResponseWriter forMalformedRequest() {
        return new HttpResponseWriter(false);
    }

    private void serialize(OutputStream out, HttpVersion version, byte[] body) throws IOException {
        writeStatusLine(out, version, status);

        for (var header : headers()) {
            writeHeaderLine(out, header.getKey(), header.getValue());
        }

        writeCrlf(out);

        if (writeBody) {
            out.write(body);
        }

        out.flush();
    }

    public void finish(OutputStream out, HttpVersion version) throws IOException {
        if (calledFinish) {
            return;
        }

        calledFinish = true;

        var body = buffer.toByteArray();

        var headers = headers();
        headers.set("Date", ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.RFC_1123_DATE_TIME));
        if (body.length > 0) {
            headers.set("Content-Length", String.valueOf(body.length));
        }

        serialize(out, version, body);
    }

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

    private void writeStatusLine(OutputStream out, HttpVersion version, HttpStatus status) throws IOException {
        writeAscii(out, version.protocolString());
        out.write(' ');
        writeAscii(out, String.valueOf(status.code()));
        out.write(' ');
        writeAscii(out, status.text());
        writeCrlf(out);
    }

    private void writeHeaderLine(OutputStream out, String key, String value) throws IOException {
        writeAscii(out, key);
        out.write(':');
        out.write(' ');
        writeAscii(out, value);
        writeCrlf(out);
    }

    private void writeAscii(OutputStream out, String value) throws IOException {
        out.write(value.getBytes(StandardCharsets.US_ASCII));
    }

    private void writeCrlf(OutputStream out) throws IOException {
        out.write('\r');
        out.write('\n');
    }
}
