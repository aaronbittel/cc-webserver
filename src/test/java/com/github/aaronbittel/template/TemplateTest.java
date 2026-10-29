package com.github.aaronbittel.template;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class TemplateTest {

    @TempDir
    Path tempDir;

    @ParameterizedTest
    @ValueSource(strings = {
            "Hello, World!",
            "<h1>Title</h1>",
            "{.name}",
    })
    void renders_plain_text_unchanged(String source) {
        assertThat(Template.parse(source).render(null)).isEqualTo(source);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{{.name}}",
            "{{ .name }}",
            "{{  .name      }}",
    })
    void renders_property_expression(String source) {
        assertThat(Template.parse(source).render(Map.of("name", "Bob"))).isEqualTo("Bob");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{{.name",
            "{{.name}",
            "{{name}}",
            "{{.}}",
            "{{.name and}}",
    })
    void throws_when_template_syntax_is_invalid(String source) {
        assertThatIllegalArgumentException().isThrownBy(() -> Template.parse(source));
    }

    @ParameterizedTest
    @MethodSource("templates")
    void renders_template(String source, Map<String, Object> input, String expected) {
        assertThat(Template.parse(source).render(input)).isEqualTo(expected);
    }

    static Stream<Arguments> templates() {
        return Stream.of(
                Arguments.of("Hello, {{.name}}!", Map.of("name", "Bob"), "Hello, Bob!"),
                Arguments.of("<h1>{{.name}}</h1>", Map.of("name", "Bob"), "<h1>Bob</h1>"),
                Arguments.of("{{.name}} is here", Map.of("name", "Bob"), "Bob is here"),
                Arguments.of("Hello, {{.name}}", Map.of("name", "Bob"), "Hello, Bob"),
                Arguments.of("{{.age}}", Map.of("age", 30), "30"),
                Arguments.of("{{.active}}", Map.of("active", true), "true"),
                Arguments.of("{{.initial}}", Map.of("initial", 'A'), "A"),
                Arguments.of("{{.price}}", Map.of("price", 3.14f), "3.14"),
                Arguments.of("Empty '{{.name}}' Name", null, "Empty '' Name"),
                Arguments.of("Empty '{{.name}}' Name", Map.of("age", 42), "Empty '' Name")
        );
    }

    @Test
    void parses_template_from_path() throws IOException {
        Path file = tempDir.resolve("template.html");
        Files.writeString(file, "Hello, {{.name}}!");

        assertThat(Template.parse(file).render(Map.of("name", "Bob"))).isEqualTo("Hello, Bob!");
    }

    @Test
    void throws_when_path_does_not_exist() {
        Path file = tempDir.resolve("missing.html");
        assertThatIOException().isThrownBy(() -> Template.parse(file));
    }
}