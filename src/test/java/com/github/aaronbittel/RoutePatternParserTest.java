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

            "/{}",
            "/hello/{}",

            "/{id",
            "/id}",
            "/{id}}",
            "/{{id}",
            "/hello/{id",

            "/{id}/hello",
            "/hello/{id}/world",

            "/{id}/{name}",
            "/hello/{id}/{name}",

            "/{id}/",
            "/hello/{id}/",
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

    @ParameterizedTest
    @CsvSource({
            "'/{id}', '/{id}', id",
            "'/hello/{id}', '/hello/{id}', id",
            "'/hello/{user-id}', '/hello/{user-id}', user-id",
            "'/hello/{user_id}', '/hello/{user_id}', user_id",
            "'/hello/{user.id}', '/hello/{user.id}', user.id",
            "'/hello/{123}', '/hello/{123}', 123",
            "'/hello/{anything-you-want}', '/hello/{anything-you-want}', anything-you-want",
    })
    void parse_valid_path_parameters(String pattern, String expectedPath, String expectedParameterName) {
        var result = RoutePatternParser.parse(pattern);

        assertThat(result)
                .isInstanceOfSatisfying(
                        RoutePattern.ParameterRoute.class,
                        route -> {
                            assertThat(route.path()).isEqualTo(expectedPath);
                            assertThat(route.segments()).last().isEqualTo(
                                    new RouteSegment.Parameter(expectedParameterName)
                            );
                        }
                );
    }
}
