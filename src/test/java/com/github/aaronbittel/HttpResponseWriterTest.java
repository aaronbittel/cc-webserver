package com.github.aaronbittel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

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
        var expected = "HTTP/1.1 200 OK\r\n\r\n";
        assertThat(out.toString()).isEqualTo(expected);
    }

    @Test
    void write_with_body() throws IOException {
        var content = "Hello World";

        writer.write(content);
        writer.finish(out, HttpVersion.HTTP_1_1);

        var expected = "HTTP/1.1 200 OK\r\nContent-Length: %d\r\n\r\n%s".formatted(content.getBytes().length, content);
        assertThat(out.toString()).isEqualTo(expected);
    }

    @Test
    void does_not_write_body_when_configured_without_body() throws IOException {
        var writer = HttpResponseWriter.forMalformedRequest();

        var content = "Hello, World";
        writer.headers().set("Content-Type", "text/plain");
        writer.write(content);
        writer.finish(out, HttpVersion.HTTP_1_1);

        var expected = """
                HTTP/1.1 200 OK
                Content-Length: 12
                Content-Type: text/plain
                
                """.replace("\n", "\r\n");

        assertThat(out.toString(StandardCharsets.US_ASCII)).isEqualTo(expected);
    }

    @Test
    void sets_content_length_automatically() throws IOException {
        writer.write("Hello, World!");
        writer.finish(out, HttpVersion.HTTP_1_1);

        var expected = """
                HTTP/1.1 200 OK
                Content-Length: 13
                
                Hello, World!""".replace("\n", "\r\n");

        assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo(expected);
    }

    @Test
    void calculates_content_length_in_bytes() throws IOException {
        writer.write("Héllo");
        writer.finish(out, HttpVersion.HTTP_1_1);

        assertThat(out.toString(StandardCharsets.UTF_8)).contains("Content-Length: 6\r\n");
    }

    @Test
    void allows_status_to_be_overwritten() throws IOException {
        writer.setStatus(HttpStatus.NOT_FOUND);
        writer.setStatus(HttpStatus.METHOD_NOT_ALLOWED);
        writer.finish(out, HttpVersion.HTTP_1_1);

        var expected = "HTTP/1.1 405 Method Not Allowed\r\n\r\n";
        assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo(expected);
    }

    @Test
    void finish_does_not_write_response_twice() throws IOException {
        writer.setStatus(HttpStatus.METHOD_NOT_ALLOWED);
        writer.finish(out, HttpVersion.HTTP_1_1);

        var expected = "HTTP/1.1 405 Method Not Allowed\r\n\r\n";
        assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo(expected);

        writer.finish(out, HttpVersion.HTTP_1_1); // does not append / write a second time
        assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo(expected);
    }
}
