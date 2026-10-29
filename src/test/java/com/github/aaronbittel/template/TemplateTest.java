package com.github.aaronbittel.template;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class TemplateTest {

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
            "{{.name",
            "{{.name}",
            "{{name}}",
            "{{.}}",
            "{{.name and}}",
    })
    void throws_when_template_syntax_is_invalid(String source) {
        assertThatIllegalArgumentException().isThrownBy(() -> Template.parse(source));
    }

    @Test
    void throws_if_data_is_a_list() {
        var template = Template.parse("{{.value}}");
        assertThatIllegalArgumentException().isThrownBy(() -> template.render(Map.of("value", List.of())))
                .withMessageContaining("Illegal type:");
    }

    @Nested
    class MapAccess {
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
                    Arguments.of("Empty '{{.name}}' Name", null, "Empty '' Name"),
                    Arguments.of("Empty '{{.name}}' Name", Map.of("age", 42), "Empty '' Name")
            );
        }

        @ParameterizedTest
        @MethodSource("primitives")
        void renders_primitives(Object data, String expected) {
            var map = new HashMap<String, Object>();
            map.put("value", data);
            assertThat(Template.parse("{{.value}}").render(map)).isEqualTo(expected);
        }

        static Stream<Arguments> primitives() {
            return Stream.of(
                    Arguments.of(null, ""),
                    Arguments.of((byte) 32, "32"),
                    Arguments.of((short) 32, "32"),
                    Arguments.of(32, "32"),
                    Arguments.of((long) 32, "32"),
                    Arguments.of((float) 32.2, "32.2"),
                    Arguments.of(32.2, "32.2"),
                    Arguments.of('A', "A"),
                    Arguments.of(true, "true"),
                    Arguments.of(false, "false"),
                    Arguments.of("hello", "hello")
            );
        }

        @Test
        void accesses_nested_map_values() {
            var template = Template.parse("{{.names.value.count}}");
            var data = Map.of("names", Map.of("value", Map.of("count", 40)));
            assertThat(template.render(data)).isEqualTo("40");
        }

        @ParameterizedTest
        @MethodSource("nestedAccessWithAbsentOrNullValues")
        void renders_empty_string_when_nested_value_is_missing(Map<String, Object> data) {
            var template = Template.parse("{{.names.value.count}}");
            assertThat(template.render(data)).isEqualTo("");
        }

        static Stream<Arguments> nestedAccessWithAbsentOrNullValues() {
            var dataWithNullNames = new HashMap<String, Object>();
            dataWithNullNames.put("names", null);
            return Stream.of(
                    Arguments.of(Map.of()),
                    Arguments.of(Map.of("names", Map.of())),
                    Arguments.of(Map.of("names", Map.of("value", Map.of()))),
                    Arguments.of(Map.of("names", Map.of("value", Map.of("different", 40)))),
                    Arguments.of(dataWithNullNames)
            );
        }
    }

    @Nested
    class RecordAccess {

        record Person(String name, int age) {}

        record PersonWithBestFriend(String name, Person bestFriend) {}

        @Test
        void accesses_record_components() {
            var template = Template.parse("Hello, {{.name}}. Your age: {{.age}}");
            assertThat(template.render(new Person("Alice", 42))).isEqualTo("Hello, Alice. Your age: 42");
        }

        @Test
        void renders_missing_component_as_empty_string() {
            var template = Template.parse("Do {{.something}}");
            assertThat(template.render(new Person("Alice", 42))).isEqualTo("Do ");
        }

        @Test
        void accesses_nested_record_components() {
            var template = Template.parse("{{.bestFriend.name}}");
            var data = new PersonWithBestFriend("Alice", new Person("Bob", 30));
            assertThat(template.render(data)).isEqualTo("Bob");
        }

        @Test
        void renders_empty_string_when_nested_record_is_null() {
            var template = Template.parse("{{.bestFriend.name}}");
            var data = new PersonWithBestFriend("Alice", null);
            assertThat(template.render(data)).isEmpty();
        }
    }

    @Nested
    class HtmlEscaping {

        @ParameterizedTest
        @MethodSource("htmlSpecialCharacters")
        void escapes_html_special_characters(String input, String expected) {
            assertThat(Template.parse("{{.value}}")
                    .render(Map.of("value", input)))
                    .isEqualTo(expected);
        }

        static Stream<Arguments> htmlSpecialCharacters() {
            return Stream.of(
                    Arguments.of("&", "&amp;"),
                    Arguments.of("<", "&lt;"),
                    Arguments.of(">", "&gt;"),
                    Arguments.of("\"", "&quot;"),
                    Arguments.of("'", "&#39;")
            );
        }

        @Test
        void escapes_all_html_special_characters() {
            assertThat(Template.parse("{{.value}}")
                    .render(Map.of("value", "&<>\"'")))
                    .isEqualTo("&amp;&lt;&gt;&quot;&#39;");
        }

        @Test
        void escapes_expression_values_but_preserves_literal_html() {
            assertThat(Template.parse("<p>{{.name}}</p>")
                    .render(Map.of("name", "<script>alert('xss')</script>")))
                    .isEqualTo("<p>&lt;script&gt;alert(&#39;xss&#39;)&lt;/script&gt;</p>");
        }

        @Test
        void escapes_ampersands_in_existing_html_entities() {
            assertThat(Template.parse("{{.value}}")
                    .render(Map.of("value", "&amp;")))
                    .isEqualTo("&amp;amp;");
        }

        @Test
        void escapes_each_expression_independently() {
            assertThat(Template.parse("{{.first}} & {{.second}}")
                    .render(Map.of("first", "<Bob>", "second", "Tom & Jerry")))
                    .isEqualTo("&lt;Bob&gt; & Tom &amp; Jerry");
        }

        @Test
        void preserves_unicode_when_escaping() {
            assertThat(Template.parse("{{.value}}")
                    .render(Map.of("value", "café 😀")))
                    .isEqualTo("café 😀");
        }
    }

    @Nested
    class FileTemplates {

        @TempDir
        Path tempDir;

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
}