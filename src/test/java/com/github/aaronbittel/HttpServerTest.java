package com.github.aaronbittel;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class HttpServerTest {

    private static final Path BASE_PATH = Path.of("base").toAbsolutePath().normalize();

    @Test
    void root_request_resolves_to_index_html() {
        var expected = BASE_PATH.resolve("index.html");
        assertThat(HttpServer.resolveRequestPath(BASE_PATH, "/")).isEqualTo(expected);
    }

    @Test
    void reject_path_not_starting_with_slash() {
        assertThatIllegalArgumentException().isThrownBy(() -> HttpServer.resolveRequestPath(BASE_PATH, "somepath"));
    }

    @Test
    void reject_path_traversal() {
        assertThatIllegalArgumentException().isThrownBy(() -> HttpServer.resolveRequestPath(BASE_PATH, "/../somepath"))
                .withMessage("Path traversal detected");
    }

    @Test
    void normalize_dot_segments_in_request_path() {
        var expected = BASE_PATH.resolve("baz");
        assertThat(HttpServer.resolveRequestPath(BASE_PATH, "/foo/bar/../../baz")).isEqualTo(expected);
    }
}