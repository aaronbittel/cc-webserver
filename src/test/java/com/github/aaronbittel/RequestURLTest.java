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

    @Test
    void parses_query_parameters() {
        var target = RequestURL.parse("/search?q=java&page=2");
        var params = target.parseQuery();

        assertThat(params.get("q")).containsExactly("java");
        assertThat(params.get("page")).containsExactly("2");
    }

    @Test
    void returns_empty_query_parameters_when_query_is_missing() {
        var target = RequestURL.parse("/search");
        assertThat(target.parseQuery().get("q")).isEmpty();
    }

    @Test
    void returns_empty_query_parameters_when_query_is_empty() {
        var target = RequestURL.parse("/search?");
        assertThat(target.parseQuery().get("q")).isEmpty();
    }

    @Test
    void parses_percent_encoded_query_values() {
        var target = RequestURL.parse("/search?q=hello%20world");
        assertThat(target.parseQuery().get("q")).containsExactly("hello world");
    }

    @Test
    void rejects_invalid_percent_encoding_in_query() {
        var target = RequestURL.parse("/search?q=%GG");
        assertThatIllegalArgumentException().isThrownBy(target::parseQuery);
    }
}
