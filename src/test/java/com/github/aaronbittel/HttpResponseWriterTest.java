package com.github.aaronbittel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class HttpResponseWriterTest {

    static final ByteArrayOutputStream out = new ByteArrayOutputStream();
    static final HttpResponseWriter writer = new HttpResponseWriter(out);

    @BeforeEach
    void resetOutputStream() {
        out.reset();
    }

    @Test
    void empty_body_write() throws IOException {
        var expected = "HTTP/1.1 200 OK\r\n\r\n";
        writer.write(new HttpResponse(HttpVersion.HTTP_1_1, HttpStatus.OK, HttpResponse.EMPTY_BODY));
        assertThat(out.toString()).isEqualTo(expected);
    }

    @Test
    void write_with_body() throws IOException {
        var expected = "HTTP/1.1 200 OK\r\n\r\nHello World";
        writer.write(new HttpResponse(HttpVersion.HTTP_1_1, HttpStatus.OK, "Hello World".getBytes(StandardCharsets.US_ASCII)));
        assertThat(out.toString()).isEqualTo(expected);
    }
}