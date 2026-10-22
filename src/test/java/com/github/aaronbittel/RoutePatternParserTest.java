package com.github.aaronbittel;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class RoutePatternParserTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "hello",

            "/hello/./",
            "/hello/.",
            "/hello/..",
            "/hello/../",

            "//",
            "/hello//",
            "/hello//world",

            "/hello/{$}/",
            "/hello/{$}/{$}",
            "/hello/{$}/world",
    })
    void reject_invalid_route_patterns(String path) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> RoutePatternParser.parse(path));
    }

    @ParameterizedTest
    @CsvSource({
            "'/{$}', '/'",
            "'/hello', '/hello'",
            "'/hello/world', '/hello/world'",
            "'/hello/{$}', '/hello'",
    })
    void parses_valid_exact_route_patterns(String input, String expectedPath) {
        var result = RoutePatternParser.parse(input);

        assertThat(result).isInstanceOf(RoutePattern.ExactRoute.class);
        assertThat(result.path()).isEqualTo(expectedPath);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/",
            "/hello/",
            "/hello/world/",
    })
    void parses_valid_subtree_route_patterns(String input) {
        var result = RoutePatternParser.parse(input);

        assertThat(result).isInstanceOf(RoutePattern.SubtreeRoute.class);
        assertThat(result.path()).isEqualTo(input);
    }
}
