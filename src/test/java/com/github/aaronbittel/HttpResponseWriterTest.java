package com.github.aaronbittel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HttpResponseWriterTest {

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final HttpResponseWriter writer = HttpResponseWriter.forRequest(HttpMethod.GET);

    @BeforeEach
    void resetOutputStream() {
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
    void sets_content_length_automatically() throws IOException {
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
