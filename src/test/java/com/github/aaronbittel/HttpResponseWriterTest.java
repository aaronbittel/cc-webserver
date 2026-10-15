package com.github.aaronbittel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static com.github.aaronbittel.TestUtilis.buildHeaders;
import static org.assertj.core.api.Assertions.assertThat;

class HttpResponseWriterTest {

    static final ByteArrayOutputStream out = new ByteArrayOutputStream();
    static final HttpResponseWriter writer = HttpResponseWriter.withBody(out);

    @BeforeEach
    void resetOutputStream() {
        out.reset();
    }

    @Test
    void empty_body_write() throws IOException {
        var expected = "HTTP/1.1 200 OK\r\n\r\n";
        writer.write(new HttpResponse(HttpVersion.HTTP_1_1, HttpStatus.OK));
        assertThat(out.toString()).isEqualTo(expected);
    }

    @Test
    void write_with_body() throws IOException {
        var content = "Hello World";
        var expected = "HTTP/1.1 200 OK\r\nContent-Length: %d\r\n\r\n%s".formatted(content.getBytes().length, content);
        var headers = buildHeaders("Content-Length", String.valueOf(content.length()));
        writer.write(new HttpResponse(HttpVersion.HTTP_1_1, HttpStatus.OK, headers, content.getBytes(StandardCharsets.US_ASCII)));
        assertThat(out.toString()).isEqualTo(expected);
    }

    @Test
    void does_not_write_body_when_configured_without_body() throws IOException {
        var writer = HttpResponseWriter.withoutBody(out);
        var content = "Hello, World".getBytes(StandardCharsets.US_ASCII);
        var headers = buildHeaders(
                "Content-Type", "text/plain",
                "Content-Length", String.valueOf(content.length)
        );
        var response = new HttpResponse(HttpVersion.HTTP_1_1, HttpStatus.OK, headers, content);

        writer.write(response);

        var expected = """
                HTTP/1.1 200 OK
                Content-Length: 12
                Content-Type: text/plain
                
                """.replace("\n", "\r\n");

        assertThat(out.toString(StandardCharsets.US_ASCII)).isEqualTo(expected);
    }
}
