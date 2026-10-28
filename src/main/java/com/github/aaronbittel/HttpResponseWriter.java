package com.github.aaronbittel;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Writes an HTTP response.
 *
 * <p>The response status and headers are frozen on the first call to a {@code write} method. Changes to the status or
 * headers after that call do not affect the response.
 */
public class HttpResponseWriter implements ResponseWriter {

    private HttpStatus status = HttpStatus.OK;
    private final HttpHeaders headers = new HttpHeaders();
    private HttpHeaders responseHeaders;

    private static final int BUFFER_SIZE = 8192;
    private final ByteBuffer buffer;
    private final OutputStream out;

    private ResponseState state = ResponseState.UNDECIDED;

    private final boolean writeBody;
    private final HttpVersion version;
    private final ConnectionPolicy connectionPolicy;
    private boolean calledFinish = false;
    private boolean headersFrozen = false;

    HttpResponseWriter(OutputStream out, boolean writeBody, HttpVersion version, ConnectionPolicy connectionPolicy, int bufferSize) {
        if (bufferSize <= 0) {
            throw new IllegalArgumentException("bufferSize must be positive");
        }

        this.out = out;
        this.writeBody = writeBody;
        this.version = version;
        this.connectionPolicy = connectionPolicy;
        this.buffer = ByteBuffer.allocate(bufferSize);
    }

    private HttpResponseWriter(OutputStream out, boolean writeBody, HttpVersion version, ConnectionPolicy connectionPolicy) {
        this(out, writeBody, version, connectionPolicy, BUFFER_SIZE);
    }

    public static HttpResponseWriter forRequest(OutputStream out, HttpRequest request, ConnectionPolicy connectionPolicy) {
        return new HttpResponseWriter(out, request.method() != HttpMethod.HEAD, request.version(), connectionPolicy);
    }

    public static HttpResponseWriter forMalformedRequest(OutputStream out) {
        return new HttpResponseWriter(out, false, HttpVersion.HTTP_1_1, ConnectionPolicy.CLOSE);
    }

    /**
     * Completes the response by writing any remaining headers and body.
     *
     * <p>This method must be called to produce a complete, valid HTTP response.
     *
     * <p>For chunked responses, writes any remaining buffered data and the terminating chunk. For buffered responses,
     * sets or validates {@code Content-Length}.
     *
     * <p>Calling this method multiple times has no effect after the first call.
     *
     * @throws IOException if writing the response fails or the declared content length does not match the body
     */
    public void finish() throws IOException {
        if (calledFinish) {
            return;
        }

        calledFinish = true;
        freezeHeaders();

        if (state == ResponseState.CHUNKED) {
            if (buffer.position() > 0) {
                writeChunk();
            }
            writeTerminalChunk();
            return;
        }

        finishHeaders();

        // TODO: handle 204 and other special cases

        var contentLengthHeader = responseHeaders.get("Content-Length");

        int contentLength;
        if (contentLengthHeader.isPresent()) {
            contentLength = Integer.parseInt(contentLengthHeader.get());
        } else {
            contentLength = buffer.position();
            responseHeaders.set("Content-Length", String.valueOf(contentLength));
        }

        if (contentLength != buffer.position()) {
            throw new IOException("Content-Length mismatch: declared %d bytes, but body contains %d bytes"
                    .formatted(contentLength, buffer.position())
            );
        }

        writeResponse();
    }

    /**
     * Sets the response status.
     *
     * <p>Has no effect after the first call to a {@code write} method.
     *
     * @param status the HTTP response status
     */
    @Override
    public void setStatus(HttpStatus status) {
        if (!headersFrozen) {
            this.status = status;
        }
    }

    /**
     * Returns the mutable response headers.
     *
     * <p>Header changes made after the first call to a {@code write} method do not affect the response.
     *
     * @return the response headers
     */
    @Override
    public HttpHeaders headers() {
        return headers;
    }

    /**
     * Writes a string to the response body using UTF-8.
     *
     * <p>Freezes the response status and headers on the first call.
     *
     * @param text the body text
     * @throws IOException if writing the response body fails
     */
    @Override
    public void write(String text) throws IOException {
        freezeHeaders();
        write(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Writes bytes to the response body.
     *
     * <p>Freezes the response status and headers on the first call. If the body exceeds the buffer capacity, the
     * response is sent using chunked transfer encoding.
     *
     * @param data the body bytes
     * @throws IOException if writing the response body fails
     */
    @Override
    public void write(byte[] data) throws IOException {
        freezeHeaders();
        if (data.length <= buffer.remaining()) {
            buffer.put(data);
            return;
        }

        var source = ByteBuffer.wrap(data);

        while (source.remaining() > 0) {
            prepareChunkTransfer();

            int count = Math.min(source.remaining(), buffer.remaining());

            int oldLimit = source.limit();
            source.limit(source.position() + count);
            buffer.put(source);
            source.limit(oldLimit);

            if (buffer.remaining() == 0) {
                writeChunk();
            }
        }
    }

    /**
     * Streams data from an input stream to the response body.
     *
     * <p>Freezes the response status and headers on the first call. If the body exceeds the buffer capacity, the
     * response is sent using chunked transfer encoding.
     *
     * @param in the input stream to read from
     * @throws IOException if reading the input stream or writing the response body fails
     */
    @Override
    public void write(InputStream in) throws IOException {
        freezeHeaders();

        while (true) {
            int n = in.read(buffer.array(), buffer.position(), buffer.remaining());
            if (n == -1) {
                break;
            }

            if (buffer.remaining() == 0) {
                prepareChunkTransfer();
                writeChunk();
            }

            buffer.position(buffer.position() + n);
        }
    }

    private void writeResponse() throws IOException {
        writeStatusLine();
        writeHeaders();

        if (writeBody) {
            out.write(buffer.array(), 0, buffer.position());
        }

        out.flush();
    }

    private void freezeHeaders() {
        headersFrozen = true;
        if (responseHeaders == null) {
            responseHeaders = new HttpHeaders(headers);
        }
    }

    private void prepareChunkTransfer() throws IOException {
        if (state == ResponseState.UNDECIDED) {
            state = ResponseState.CHUNKED;
            finishHeaders();
            responseHeaders.set("Transfer-Encoding", "chunked");
            writeStatusLine();
            writeHeaders();
        }
    }

    private void finishHeaders() {
        responseHeaders.set("Date", ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.RFC_1123_DATE_TIME));
        switch (connectionPolicy) {
            case CLOSE -> responseHeaders.set("Connection", "close");
            case KEEP_ALIVE -> responseHeaders.set("Connection", "keep-alive");
            case NONE -> {}
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

    private void writeChunk() throws IOException {
        out.write(Integer.toHexString(buffer.position()).getBytes(StandardCharsets.US_ASCII));
        writeCrlf();
        out.write(buffer.array(), 0, buffer.position());
        writeCrlf();

        buffer.clear();
    }

    private void writeTerminalChunk() throws IOException {
        out.write('0');
        writeCrlf();
        writeCrlf();
    }

    private void writeHeaders() throws IOException {
        for (var header : responseHeaders) {
            writeHeaderLine(header.getKey(), header.getValue());
        }
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

    private enum ResponseState {
        UNDECIDED,
        CHUNKED,
    }
}
