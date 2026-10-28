package com.github.aaronbittel;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class HttpResponseWriter implements ResponseWriter {

    private HttpStatus status = HttpStatus.OK;
    private final HttpHeaders headers = new HttpHeaders();
    private final static int BUFFER_SIZE = 8192;
    private final ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);

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

    private void serialize(OutputStream out, HttpVersion version) throws IOException {
        writeStatusLine(out, version, status);

        for (var header : headers()) {
            writeHeaderLine(out, header.getKey(), header.getValue());
        }

        writeCrlf(out);

        if (writeBody) {
            out.write(buffer.array(), 0, buffer.position());
        }

        out.flush();
    }

    public void finish(OutputStream out, HttpVersion version) throws IOException {
        if (calledFinish) {
            return;
        }

        calledFinish = true;

        var headers = headers();
        headers.set("Date", ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.RFC_1123_DATE_TIME));
        // TODO: handle 204 and other special cases

        var contentLengthHeader = headers.get("Content-Length");

        int contentLength;
        if (contentLengthHeader.isPresent()) {
            contentLength = Integer.parseInt(contentLengthHeader.get());
        } else {
            contentLength = buffer.position();
            headers.set("Content-Length", String.valueOf(contentLength));
        }

        if (contentLength != buffer.position()) {
            throw new IOException("Content-Length mismatch: declared %d bytes, but body contains %d bytes"
                    .formatted(contentLength, buffer.position())
            );
        }

        serialize(out, version);
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
        buffer.put(text.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void write(byte[] data) throws IOException {
        buffer.put(data);
    }

    @Override
    public void write(InputStream in) throws IOException {
        while (buffer.hasRemaining()) {
            int n = in.read(buffer.array(), buffer.position(), buffer.remaining());
            if (n == -1) {
                break;
            }
            buffer.position(buffer.position() + n);
        }
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
