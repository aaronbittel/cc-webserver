package com.github.aaronbittel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;

class MainTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "GET / HTTP/1.1\r",
            "GET / HTTP/1.1\n",
            "GET / HTTP/1.1\rX",
            "GET / HTTP/1.1\n\r",
    })
    void reject_invalid_line_endings(String line) {
        var in = new ByteArrayInputStream(
                line.getBytes(StandardCharsets.UTF_8)
        );

        assertThatIOException().isThrownBy(() -> Main.readHTTPLine(in));
    }

    @Test
    void accept_valid_line_endings() throws IOException {
        var in = new ByteArrayInputStream("anything\r\n".getBytes(StandardCharsets.UTF_8));
        assertThat(Main.readHTTPLine(in)).isEqualTo("anything");
    }
}