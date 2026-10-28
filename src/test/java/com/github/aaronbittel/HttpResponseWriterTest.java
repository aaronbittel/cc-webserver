package com.github.aaronbittel;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class HttpResponseWriterTest {

    private static final int BUFFER_SIZE = 8192;
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final HttpResponseWriter writer = new HttpResponseWriter(out, true, HttpVersion.HTTP_1_1, ConnectionPolicy.NONE, BUFFER_SIZE);

    @ParameterizedTest(name = "{0}")
    @MethodSource("emptyBodyWriters")
    void writesEmptyBody(String description, BodyWriter writeBody) throws IOException {
        writeBody.write(writer);
        writer.finish();

        HttpResponseAssert.from(out)
                .hasHeader("Content-Length: 0")
                .hasNoHeader("Transfer-Encoding")
                .hasEmptyBody();
    }

    static Stream<Arguments> emptyBodyWriters() {
        return Stream.of(
                arguments("empty String", (BodyWriter) writer -> writer.write("")),
                arguments("empty byte array", (BodyWriter) writer -> writer.write(new byte[0])),
                arguments("empty InputStream", (BodyWriter) writer -> writer.write(new ByteArrayInputStream(new byte[0])))
        );
    }

    @Test
    void does_not_write_body_when_configured_without_body() throws IOException {
        var writer = HttpResponseWriter.forMalformedRequest(out);

        var content = "Hello, World";
        writer.headers().set("Content-Type", "text/plain");
        writer.write(content);
        writer.finish();

        HttpResponseAssert.from(out)
                .hasHeader("Content-Length: 12")
                .hasHeader("Content-Type: text/plain")
                .hasEmptyBody();
    }

    @Test
    void sets_content_length_when_not_provided() throws IOException {
        writer.write("Hello, World!");
        writer.finish();
        HttpResponseAssert.from(out).hasHeader("Content-Length: 13");
    }

    @Test
    void calculates_content_length_in_bytes() throws IOException {
        writer.write("Héllo");
        writer.finish();
        HttpResponseAssert.from(out).hasHeader("Content-Length: 6");
    }

    @Test
    void allows_status_to_be_overwritten() throws IOException {
        writer.setStatus(HttpStatus.NOT_FOUND);
        writer.setStatus(HttpStatus.METHOD_NOT_ALLOWED);
        writer.finish();

        HttpResponseAssert.from(out).hasStatusLine("HTTP/1.1 405 Method Not Allowed");
    }

    @Test
    void finish_does_not_write_response_twice() throws IOException {
        writer.finish();

        var response = out.toString(StandardCharsets.UTF_8);
        assertThat(response).isNotEmpty();

        writer.finish();
        assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo(response);
    }

    @Test
    void preserves_content_length_when_it_matches_body_size() throws IOException {
        writer.headers().set("Content-Length", "13");
        writer.write("Hello");
        writer.write(", ");
        writer.write("World!");

        writer.finish();

        HttpResponseAssert.from(out)
                .hasStatusLine("HTTP/1.1 200 OK")
                .hasHeader("Content-Length: 13")
                .hasBody("Hello, World!");
    }

    @Test
    void throws_if_content_length_exceeds_actual_body_size() throws IOException {
        writer.headers().set("Content-Length", "10");
        writer.write("Hello");

        assertThatIOException()
                .isThrownBy(writer::finish)
                .withMessage("Content-Length mismatch: declared 10 bytes, but body contains 5 bytes");

        assertThat(out.size()).isZero();
    }

    @Test
    void throws_if_content_length_is_less_than_actual_body_size() throws IOException {
        writer.headers().set("Content-Length", "3");
        writer.write("Hello");

        assertThatIOException()
                .isThrownBy(writer::finish)
                .withMessage("Content-Length mismatch: declared 3 bytes, but body contains 5 bytes");

        assertThat(out.size()).isZero();
    }

    @Test
    void accepts_zero_content_length_for_empty_body() throws IOException {
        writer.headers().set("Content-Length", "0");
        writer.finish();

        HttpResponseAssert.from(out)
                .hasHeader("Content-Length: 0")
                .hasEmptyBody();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("write_methods")
    void freezes_headers_and_status_after_first_write(String description, BodyWriter writeBody) throws IOException {
        writer.setStatus(HttpStatus.TEMPORARY_REDIRECT);
        var headers = writer.headers();
        headers.set("Key", "Value");

        writeBody.write(writer);

        headers.set("AfterWrite", "Value");
        writer.setStatus(HttpStatus.NOT_FOUND);
        writer.finish();

        HttpResponseAssert.from(out)
                .hasStatusLine("HTTP/1.1 307 Temporary Redirect")
                .hasHeader("Content-Length: 2")
                .hasHeader("Key: Value")
                .hasNoHeader("AfterWrite")
                .hasBody("hi");
    }

    static Stream<Arguments> write_methods() {
        return Stream.of(
                Arguments.of("write(String)", (BodyWriter) writer -> writer.write("hi")),
                Arguments.of("write(byte[])", (BodyWriter) writer -> writer.write("hi".getBytes(StandardCharsets.UTF_8))),
                Arguments.of("write(InputStream)", (BodyWriter) writer -> writer.write(
                        new ByteArrayInputStream("hi".getBytes(StandardCharsets.UTF_8))))
        );
    }

    @Nested
    class ResponseWriterChunked {

        private static final int BUFFER_SIZE = 20;
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();
        private final HttpResponseWriter writer = new HttpResponseWriter(out, true, HttpVersion.HTTP_1_1, ConnectionPolicy.NONE, BUFFER_SIZE);

        @ParameterizedTest(name = "{0}")
        @MethodSource("fitsInBuffer")
        void uses_content_length_when_response_fits_in_buffer(String description, String expected, BodyWriter writeBody)
                throws IOException {
            writeBody.write(writer);
            writer.finish();

            HttpResponseAssert.from(out)
                    .hasStatusLine("HTTP/1.1 200 OK")
                    .hasHeader("Content-Length: " + expected.length())
                    .hasNoHeader("Transfer-Encoding: chunked")
                    .hasBody(expected);
        }

        static Stream<Arguments> fitsInBuffer() {
            var content = "AAAAABBBBBCCCCCDDD"; // 19 bytes when BUFFER_SIZE = 20
            assertThat(content.getBytes(StandardCharsets.UTF_8).length).isLessThan(BUFFER_SIZE);

            return Stream.of(
                    arguments("single String write", content, (BodyWriter) writer -> writer.write(content)),
                    arguments("single byte[] write", content, (BodyWriter) writer ->
                            writer.write(content.getBytes(StandardCharsets.UTF_8))),
                    arguments("single InputStream write", content, (BodyWriter) writer ->
                            writer.write(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)))),
                    arguments("multiple String writes", content, (BodyWriter) writer -> {
                        writer.write(content.substring(0, 10));
                        writer.write(content.substring(10));
                    }),
                    arguments("mixed write methods", content, (BodyWriter) writer -> {
                        writer.write(content.substring(0, 6));
                        writer.write(content.substring(6, 12).getBytes(StandardCharsets.UTF_8));
                        writer.write(new ByteArrayInputStream(content.substring(12).getBytes(StandardCharsets.UTF_8)));
                    })
            );
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("exactlyFillsBuffer")
        void uses_content_length_when_response_exactly_fills_buffer(String description, String expected, BodyWriter writeBody)
                throws IOException {

            writeBody.write(writer);
            writer.finish();

            HttpResponseAssert.from(out)
                    .hasStatusLine("HTTP/1.1 200 OK")
                    .hasHeader("Content-Length: " + expected.length())
                    .hasNoHeader("Transfer-Encoding")
                    .hasBody(expected);
        }

        static Stream<Arguments> exactlyFillsBuffer() {
            var content = "AAAAABBBBBCCCCCDDDDD"; // 20 bytes when BUFFER_SIZE = 20
            assertThat(content.getBytes(StandardCharsets.UTF_8).length).isEqualTo(BUFFER_SIZE);

            return Stream.of(
                    arguments("single String write", content, (BodyWriter) writer -> writer.write(content)),
                    arguments("single byte[] write", content, (BodyWriter) writer ->
                            writer.write(content.getBytes(StandardCharsets.UTF_8))),
                    arguments("single InputStream write", content, (BodyWriter) writer ->
                            writer.write(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)))),
                    arguments("multiple String writes", content, (BodyWriter) writer -> {
                        writer.write(content.substring(0, BUFFER_SIZE / 2));
                        writer.write(content.substring(BUFFER_SIZE / 2));
                    }),
                    arguments("mixed write methods", content, (BodyWriter) writer -> {
                        writer.write(content.substring(0, BUFFER_SIZE / 3));
                        writer.write(content.substring(
                                BUFFER_SIZE / 3, 2 * (BUFFER_SIZE / 3)).getBytes(StandardCharsets.UTF_8));
                        writer.write(new ByteArrayInputStream(
                                content.substring(2 * (BUFFER_SIZE / 3)).getBytes(StandardCharsets.UTF_8)));
                    })
            );
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("exceedsBuffer")
        void uses_chunked_encoding_when_response_exceeds_buffer(String description, String expected, BodyWriter writeBody)
                throws IOException {

            writeBody.write(writer);
            writer.finish();

            HttpResponseAssert.from(out)
                    .hasStatusLine("HTTP/1.1 200 OK")
                    .hasHeader("Transfer-Encoding: chunked")
                    .hasNoHeader("Content-Length")
                    .hasChunksExactly(expected.substring(0, BUFFER_SIZE), expected.substring(BUFFER_SIZE))
                    .hasBody(expected);
        }

        static Stream<Arguments> exceedsBuffer() {
            var content = "AAAAABBBBBCCCCCDDDDDX"; // 21 bytes when BUFFER_SIZE = 20
            assertThat(content.getBytes(StandardCharsets.UTF_8).length).isGreaterThan(BUFFER_SIZE);

            return Stream.of(
                    arguments("single String write", content, (BodyWriter) writer -> writer.write(content)),
                    arguments("single byte[] write", content, (BodyWriter) writer ->
                            writer.write(content.getBytes(StandardCharsets.UTF_8))),
                    arguments("single InputStream write", content, (BodyWriter) writer ->
                            writer.write(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)))),
                    arguments("multiple String writes", content, (BodyWriter) writer -> {
                        writer.write(content.substring(0, BUFFER_SIZE / 2));
                        writer.write(content.substring(BUFFER_SIZE / 2));
                    }),
                    arguments("mixed write methods", content, (BodyWriter) writer -> {
                        writer.write(content.substring(0, BUFFER_SIZE / 3));
                        writer.write(content.substring(
                                BUFFER_SIZE / 3, 2 * (BUFFER_SIZE / 3)).getBytes(StandardCharsets.UTF_8));
                        writer.write(new ByteArrayInputStream(
                                content.substring(2 * (BUFFER_SIZE / 3)).getBytes(StandardCharsets.UTF_8)));
                    })
            );
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("multipleChunks")
        void uses_multiple_chunks_when_response_exceeds_buffer_multiple_times(
                String description, String expected, BodyWriter writeBody) throws IOException {

            writeBody.write(writer);
            writer.finish();

            HttpResponseAssert.from(out)
                    .hasStatusLine("HTTP/1.1 200 OK")
                    .hasHeader("Transfer-Encoding: chunked")
                    .hasNoHeader("Content-Length")
                    .hasChunksExactly(
                            expected.substring(0, BUFFER_SIZE),
                            expected.substring(BUFFER_SIZE, 2 * BUFFER_SIZE),
                            expected.substring(2 * BUFFER_SIZE))
                    .hasBody(expected);
        }

        static Stream<Arguments> multipleChunks() {
            var content = "AAAAABBBBBCCCCCDDDDDEEEEEFFFFFGGGGGHHHHHX"; // 41 bytes when BUFFER_SIZE = 20
            assertThat(content.getBytes(StandardCharsets.UTF_8).length)
                    .isGreaterThan(2 * BUFFER_SIZE);

            return Stream.of(
                    arguments("single String write", content, (BodyWriter) writer -> writer.write(content)),
                    arguments("single byte[] write", content, (BodyWriter) writer ->
                            writer.write(content.getBytes(StandardCharsets.UTF_8))),
                    arguments("single InputStream write", content, (BodyWriter) writer ->
                            writer.write(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)))),
                    arguments("multiple String writes", content, (BodyWriter) writer -> {
                        writer.write(content.substring(0, 10));
                        writer.write(content.substring(10, 30));
                        writer.write(content.substring(30));
                    }),
                    arguments("mixed write methods", content, (BodyWriter) writer -> {
                        writer.write(content.substring(0, 7));
                        writer.write(content.substring(7, 25).getBytes(StandardCharsets.UTF_8));
                        writer.write(new ByteArrayInputStream(content.substring(25).getBytes(StandardCharsets.UTF_8)));
                    })
            );
        }
    }

    static final class HttpResponseAssert {

        private final String statusLine;
        private final List<String> headers;
        private final String body;
        private final List<String> chunks;
        private final boolean chunked;

        private HttpResponseAssert(String statusLine, List<String> headers, String body, List<String> chunks, boolean chunked) {
            this.statusLine = statusLine;
            this.headers = List.copyOf(headers);
            this.body = body;
            this.chunks = List.copyOf(chunks);
            this.chunked = chunked;
        }

        static HttpResponseAssert from(ByteArrayOutputStream out) {
            return from(out.toByteArray());
        }

        static HttpResponseAssert from(byte[] response) {
            return from(new String(response, StandardCharsets.ISO_8859_1));
        }

        static HttpResponseAssert from(String response) {
            int separator = response.indexOf("\r\n\r\n");
            assertThat(separator)
                    .as("HTTP response header/body separator")
                    .isGreaterThanOrEqualTo(0);

            String[] lines = response.substring(0, separator).split("\r\n");
            String statusLine = lines[0];
            List<String> headers = Arrays.stream(lines)
                    .skip(1)
                    .toList();
            String body = response.substring(separator + 4);

            return create(statusLine, headers, body);
        }

        static HttpResponseAssert readFrom(InputStream in) throws IOException {
            String statusLine = HttpRequestParser.readHttpLine(in);
            List<String> headers = new ArrayList<>();

            String line;
            while (!(line = HttpRequestParser.readHttpLine(in)).isEmpty()) {
                headers.add(line);
            }

            if (hasChunkedTransferEncoding(headers)) {
                List<String> chunks = readChunks(in);
                return create(statusLine, headers, chunks);
            }

            int contentLength = contentLength(headers);
            byte[] bodyBytes = in.readNBytes(contentLength);

            if (bodyBytes.length != contentLength) {
                throw new EOFException("Incomplete HTTP response body");
            }

            return new HttpResponseAssert(statusLine, headers, new String(bodyBytes, StandardCharsets.UTF_8), List.of(), false);
        }

        private static HttpResponseAssert create(String statusLine, List<String> headers, String body) {
            if (hasChunkedTransferEncoding(headers)) {
                return create(statusLine, headers, parseChunks(body));
            }

            return new HttpResponseAssert(statusLine, headers, body, List.of(), false);
        }

        private static HttpResponseAssert create(String statusLine, List<String> headers, List<String> chunks) {
            return new HttpResponseAssert(statusLine, headers, String.join("", chunks), chunks, true);
        }

        HttpResponseAssert hasStatusLine(String expected) {
            assertThat(statusLine).isEqualTo(expected);
            return this;
        }

        HttpResponseAssert hasHeader(String expected) {
            assertThat(headers).contains(expected);
            return this;
        }

        HttpResponseAssert hasNoHeader(String expected) {
            assertThat(headers).noneMatch(header -> header.regionMatches(true, 0, expected, 0, expected.length()));
            return this;
        }

        HttpResponseAssert hasBody(String expected) {
            assertThat(body).isEqualTo(expected);
            return this;
        }

        HttpResponseAssert hasEmptyBody() {
            return hasBody("");
        }

        HttpResponseAssert hasChunksExactly(String... expected) {
            assertThat(chunked)
                    .as("Response uses chunked transfer encoding")
                    .isTrue();
            assertThat(chunks).containsExactly(expected);
            return this;
        }

        @SuppressWarnings({"unused", "JavaPrintToLogpoint"})
        HttpResponseAssert debug() {
            System.out.println("statusLine = " + statusLine);
            headers.forEach(header -> System.out.println("header = " + header));
            System.out.println("body = " + body);
            if (chunked) {
                System.out.println("chunks = " + chunks);
            }
            return this;
        }

        private static boolean hasChunkedTransferEncoding(List<String> headers) {
            return headers.stream().anyMatch(header -> {
                int colon = header.indexOf(':');
                return colon >= 0
                        && header.substring(0, colon).trim()
                        .equalsIgnoreCase("Transfer-Encoding")
                        && Arrays.stream(header.substring(colon + 1).split(","))
                        .anyMatch(value -> value.trim().equalsIgnoreCase("chunked"));
            });
        }

        private static int contentLength(List<String> headers) {
            List<String> values = headers.stream()
                    .filter(header -> {
                        int colon = header.indexOf(':');
                        return colon >= 0
                                && header.substring(0, colon).trim()
                                .equalsIgnoreCase("Content-Length");
                    })
                    .map(header -> header.substring(header.indexOf(':') + 1).trim())
                    .toList();

            if (values.isEmpty()) {
                return 0;
            }

            int length = Integer.parseInt(values.getFirst());
            if (length < 0 || values.stream().anyMatch(value ->
                    !value.equals(values.getFirst()))) {
                throw new AssertionError("Invalid or conflicting Content-Length headers");
            }
            return length;
        }

        private static List<String> readChunks(InputStream in) throws IOException {
            List<String> chunks = new ArrayList<>();

            while (true) {
                String sizeLine = HttpRequestParser.readHttpLine(in);
                int size = parseChunkSize(sizeLine);

                if (size == 0) {
                    String end = HttpRequestParser.readHttpLine(in);
                    if (!end.isEmpty()) {
                        throw new IOException("Chunk trailers are not supported: " + end);
                    }
                    return chunks;
                }

                byte[] data = in.readNBytes(size);
                if (data.length != size) {
                    throw new EOFException("Incomplete chunk data");
                }

                if (in.read() != '\r' || in.read() != '\n') {
                    throw new IOException("Missing CRLF after chunk data");
                }

                chunks.add(new String(data, StandardCharsets.UTF_8));
            }
        }

        private static List<String> parseChunks(String wireBody) {
            List<String> chunks = new ArrayList<>();
            int offset = 0;

            while (true) {
                int lineEnd = wireBody.indexOf("\r\n", offset);
                if (lineEnd < 0) {
                    throw new AssertionError("Missing CRLF after chunk size");
                }

                int size = parseChunkSize(wireBody.substring(offset, lineEnd));
                offset = lineEnd + 2;

                if (size == 0) {
                    assertThat(wireBody.substring(offset))
                            .as("No trailers or data after final chunk")
                            .isEqualTo("\r\n");
                    return chunks;
                }

                int chunkEnd = offset + size;
                if (chunkEnd + 2 > wireBody.length()
                        || !wireBody.startsWith("\r\n", chunkEnd)) {
                    throw new AssertionError("Invalid chunk data or missing CRLF");
                }

                chunks.add(wireBody.substring(offset, chunkEnd));
                offset = chunkEnd + 2;
            }
        }

        private static int parseChunkSize(String sizeLine) {
            try {
                int size = Integer.parseInt(sizeLine.trim(), 16);
                if (size < 0) {
                    throw new AssertionError("Negative chunk size");
                }
                return size;
            } catch (NumberFormatException e) {
                throw new AssertionError("Invalid chunk size: " + sizeLine, e);
            }
        }
    }

    @FunctionalInterface
    interface BodyWriter {
        void write(HttpResponseWriter writer) throws IOException;
    }
}
