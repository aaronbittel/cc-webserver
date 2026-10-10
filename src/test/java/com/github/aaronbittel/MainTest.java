package com.github.aaronbittel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

class MainTest {

    private static final Path BASE_PATH = Path.of("base").toAbsolutePath().normalize();

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

    @Test
    void root_request_resolves_to_index_html() {
        var expected = BASE_PATH.resolve("index.html");
        assertThat(Main.resolveRequestPath(BASE_PATH, "/")).isEqualTo(expected);
    }

    @Test
    void reject_path_not_starting_with_slash() {
        assertThatIllegalArgumentException().isThrownBy(() -> Main.resolveRequestPath(BASE_PATH, "somepath"));
    }

    @Test
    void reject_path_traversal() {
        assertThatIllegalArgumentException().isThrownBy(() -> Main.resolveRequestPath(BASE_PATH, "/../somepath"))
                .withMessage("Path traversal detected");
    }

    @Test
    void normalize_dot_segments_in_request_path() {
        var expected = BASE_PATH.resolve("baz");
        assertThat(Main.resolveRequestPath(BASE_PATH, "/foo/bar/../../baz")).isEqualTo(expected);
    }
}