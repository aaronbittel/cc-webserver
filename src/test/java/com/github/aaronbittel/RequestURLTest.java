package com.github.aaronbittel;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class RequestURLTest {

    @Test
    void parses_path_without_query() {
        var target = RequestURL.parse("/hello-world");

        assertThat(target.path()).isEqualTo("/hello-world");
        assertThat(target.query()).isEmpty();
        assertThat(target.hasQuery()).isFalse();
    }

    @Test
    void parses_path_with_query() {
        var target = RequestURL.parse("/search?q=java");

        assertThat(target.path()).isEqualTo("/search");
        assertThat(target.query()).isEqualTo("q=java");
        assertThat(target.hasQuery()).isTrue();
    }

    @Test
    void distinguishes_empty_query_from_missing_query() {
        var target = RequestURL.parse("/search?");

        assertThat(target.path()).isEqualTo("/search");
        assertThat(target.query()).isEmpty();
        assertThat(target.hasQuery()).isTrue();
    }

    @Test
    void rejects_request_target_that_does_not_start_with_slash() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> RequestURL.parse("hello"))
                .withMessage("Request target must start with '/'");
    }
}
