package com.github.aaronbittel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;

class HttpResponseWriterTest {

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final HttpResponseWriter writer = HttpResponseWriter.forRequest(HttpMethod.GET);

    @BeforeEach
    void setUp() {
        out.reset();
    }

    @Test
    void empty_body_write() throws IOException {
        writer.finish(out, HttpVersion.HTTP_1_1);
        HttpResponseAssert.from(out).hasEmptyBody();
    }

    @Test
    void write_with_body() throws IOException {
        var content = "Hello World";
        writer.write(content);
        writer.finish(out, HttpVersion.HTTP_1_1);

        HttpResponseAssert.from(out).hasBody(content);
    }

    @Test
    void does_not_write_body_when_configured_without_body() throws IOException {
        var writer = HttpResponseWriter.forMalformedRequest();

        var content = "Hello, World";
        writer.headers().set("Content-Type", "text/plain");
        writer.write(content);
        writer.finish(out, HttpVersion.HTTP_1_1);

        HttpResponseAssert.from(out).hasEmptyBody();
    }

    @Test
    void sets_content_length_when_not_provided() throws IOException {
        writer.write("Hello, World!");
        writer.finish(out, HttpVersion.HTTP_1_1);
        HttpResponseAssert.from(out).hasHeader("Content-Length: 13");
    }

    @Test
    void calculates_content_length_in_bytes() throws IOException {
        writer.write("Héllo");
        writer.finish(out, HttpVersion.HTTP_1_1);
        HttpResponseAssert.from(out).hasHeader("Content-Length: 6");
    }

    @Test
    void allows_status_to_be_overwritten() throws IOException {
        writer.setStatus(HttpStatus.NOT_FOUND);
        writer.setStatus(HttpStatus.METHOD_NOT_ALLOWED);
        writer.finish(out, HttpVersion.HTTP_1_1);

        HttpResponseAssert.from(out).hasStatusLine("HTTP/1.1 405 Method Not Allowed");
    }

    @Test
    void finish_does_not_write_response_twice() throws IOException {
        writer.finish(out, HttpVersion.HTTP_1_1);

        var response = out.toString(StandardCharsets.UTF_8);
        assertThat(response).isNotEmpty();

        writer.finish(out, HttpVersion.HTTP_1_1);
        assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo(response);
    }

    @Test
    void preserves_content_length_when_it_matches_body_size() throws IOException {
        writer.headers().set("Content-Length", "13");
        writer.write("Hello");
        writer.write(", ");
        writer.write("World!");

        writer.finish(out, HttpVersion.HTTP_1_1);

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
                .isThrownBy(() -> writer.finish(out, HttpVersion.HTTP_1_1))
                .withMessage("Content-Length mismatch: declared 10 bytes, but body contains 5 bytes");

        assertThat(out.size()).isZero();
    }

    @Test
    void throws_if_content_length_is_less_than_actual_body_size() throws IOException {
        writer.headers().set("Content-Length", "3");
        writer.write("Hello");

        assertThatIOException()
                .isThrownBy(() -> writer.finish(out, HttpVersion.HTTP_1_1))
                .withMessage("Content-Length mismatch: declared 3 bytes, but body contains 5 bytes");

        assertThat(out.size()).isZero();
    }

    @Test
    void accepts_zero_content_length_for_empty_body() throws IOException {
        writer.headers().set("Content-Length", "0");

        writer.finish(out, HttpVersion.HTTP_1_1);

        HttpResponseAssert.from(out)
                .hasHeader("Content-Length: 0")
                .hasEmptyBody();
    }

    @Test
    void writes_body_from_input_stream() throws IOException {
        var content = "Hello, World!";
        writer.write(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));

        writer.finish(out, HttpVersion.HTTP_1_1);

        HttpResponseAssert.from(out)
                .hasHeader("Content-Length: 13")
                .hasBody(content);
    }

    @Test
    void writes_empty_input_stream() throws IOException {
        writer.write(new ByteArrayInputStream(new byte[0]));

        writer.finish(out, HttpVersion.HTTP_1_1);

        HttpResponseAssert.from(out)
                .hasHeader("Content-Length: 0")
                .hasEmptyBody();
    }

    static class HttpResponseAssert {

        private final String statusLine;
        private final List<String> headers;
        private final String body;

        HttpResponseAssert(String response) {
            var separator = response.indexOf("\r\n\r\n");
            assertThat(separator)
                    .as("HTTP response header/body separator")
                    .isGreaterThanOrEqualTo(0);

            var header = response.substring(0, separator);
            var statusLineAndHeaders = header.split("\r\n");

            statusLine = statusLineAndHeaders[0];
            headers = Arrays.stream(statusLineAndHeaders)
                    .skip(1)
                    .toList();
            body = response.substring(separator + 4);
        }

        private HttpResponseAssert(String statusLine, List<String> headers, String body) {
            this.statusLine = statusLine;
            this.headers = headers;
            this.body = body;
        }

        static HttpResponseAssert readFrom(InputStream in) throws IOException {
            var statusLine = HttpRequestParser.readHttpLine(in);
            var headers = new ArrayList<String>();
            String line;
            int contentLength = 0;
            while (!(line = HttpRequestParser.readHttpLine(in)).isEmpty()) {
                headers.add(line);
                if (line.regionMatches(true, 0, "Content-Length: ", 0, 16)) {
                    contentLength = Integer.parseInt(line.substring(16).trim());
                }
            }
            var body = in.readNBytes(contentLength);
            if (body.length != contentLength) {
                throw new EOFException("Incomplete HTTP response body");
            }

            return new HttpResponseAssert(statusLine, headers, new String(body, StandardCharsets.UTF_8));
        }

        static HttpResponseAssert from(ByteArrayOutputStream out) {
            return new HttpResponseAssert(out.toString(StandardCharsets.UTF_8));
        }

        HttpResponseAssert hasStatusLine(String expected) {
            assertThat(statusLine).isEqualTo(expected);
            return this;
        }

        HttpResponseAssert hasHeader(String expected) {
            assertThat(headers).contains(expected);
            return this;
        }

        HttpResponseAssert hasBody(String expected) {
            assertThat(body).isEqualTo(expected);
            return this;
        }

        HttpResponseAssert hasEmptyBody() {
            assertThat(body).isEmpty();
            return this;
        }
    }
}
