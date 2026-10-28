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
    private static final int BUFFER_SIZE = 8192;
    private final ByteBuffer buffer;
    private final OutputStream out;

    private final boolean writeBody;
    private final HttpVersion version;
    private boolean calledFinish = false;

    HttpResponseWriter(OutputStream out, boolean writeBody, HttpVersion version, int bufferSize) {
        if (bufferSize <= 0) {
            throw new IllegalArgumentException("bufferSize must be positive");
        }

        this.out = out;
        this.writeBody = writeBody;
        this.version = version;
        this.buffer = ByteBuffer.allocate(bufferSize);
    }

    private HttpResponseWriter(OutputStream out, boolean writeBody, HttpVersion version) {
        this(out, writeBody, version, BUFFER_SIZE);
    }

    public static HttpResponseWriter forRequest(OutputStream out, HttpRequest request) {
        return new HttpResponseWriter(out, request.method() != HttpMethod.HEAD, request.version());
    }

    public static HttpResponseWriter forMalformedRequest(OutputStream out) {
        return new HttpResponseWriter(out, false, HttpVersion.HTTP_1_1);
    }

    private void serialize() throws IOException {
        writeStatusLine();

        for (var header : headers()) {
            writeHeaderLine(header.getKey(), header.getValue());
        }

        writeCrlf();

        if (writeBody) {
            out.write(buffer.array(), 0, buffer.position());
        }

        out.flush();
    }

    public void finish() throws IOException {
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

        serialize();
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
        write(text.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void write(byte[] data) throws IOException {
        if (data.length <= buffer.remaining()) {
            buffer.put(data);
            return;
        }
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

    private void writeStatusLine() throws IOException {
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
