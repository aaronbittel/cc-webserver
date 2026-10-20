package com.github.aaronbittel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static com.github.aaronbittel.TestUtils.buildHeaders;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class HttpRequestParserTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "GET / HTTP/1.1\r",
            "GET / HTTP/1.1\n",
            "GET / HTTP/1.1\rX",
            "GET / HTTP/1.1\n\r",
    })
    void reject_invalid_line_endings(String line) {
        var in = new ByteArrayInputStream(line.getBytes(StandardCharsets.UTF_8));
        var parser = new HttpRequestParser(in);
        assertThatIOException().isThrownBy(parser::parseRequest);
    }

    @Test
    void accept_valid_line_endings() throws IOException {
        var in = new ByteArrayInputStream("GET /hello-world HTTP/1.1\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        var parser = new HttpRequestParser(in);

        var expected = new HttpRequest(
                new HttpRequestLine(HttpMethod.GET, "/hello-world", HttpVersion.HTTP_1_1),
                new HttpHeaders(),
                new byte[0]
        );
        assertThat(parser.parseRequest()).isEqualTo(expected);
    }

    @Test
    void parse_valid_header_line() throws IOException {
        var in = new ByteArrayInputStream("Content-Type: text/html\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
        var expected = buildHeaders("Content-Type", "text/html");
        assertThat(HttpRequestParser.parseHeaders(in).headers()).isEqualTo(expected.headers());
    }

    @Test
    void empty_headers() throws IOException {
        var in = new ByteArrayInputStream("\r\n".getBytes(StandardCharsets.US_ASCII));
        var expected = new HttpHeaders();
        assertThat(HttpRequestParser.parseHeaders(in).headers()).isEqualTo(expected.headers());
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "'Empty field name', ': field-value\r\n\r\n'",
            "'Space before colon separator', 'Content-Type : html/text\r\n\r\n'",
    })
    void rejects_illegal_header_line(String description, String headerLine) {
        var in = new ByteArrayInputStream(headerLine.getBytes(StandardCharsets.US_ASCII));
        assertThatIllegalArgumentException().isThrownBy(() -> HttpRequestParser.parseHeaders(in));
    }

    @Test
    void parse_request_with_body() throws IOException {
        var body = "Hello, world!";
        var requestText = """
                POST /hello HTTP/1.1\r
                Content-Length: 13\r
                \r
                %s""".formatted(body);

        var in = new ByteArrayInputStream(requestText.getBytes(StandardCharsets.US_ASCII));
        var parser = new HttpRequestParser(in);

        var expected = new HttpRequest(
                new HttpRequestLine(HttpMethod.POST, "/hello", HttpVersion.HTTP_1_1),
                buildHeaders("Content-Length", "13"),
                body.getBytes(StandardCharsets.US_ASCII)
        );

        assertThat(parser.parseRequest()).isEqualTo(expected);
    }

    @Test
    void body_stream_contains_body_bytes() throws IOException {
        var expectedBody = "Hello, world!".getBytes(StandardCharsets.UTF_8);
        var request = new HttpRequest(
                new HttpRequestLine(HttpMethod.POST, "/hello", HttpVersion.HTTP_1_1),
                new HttpHeaders(),
                expectedBody
        );

        try (var body = request.body()) {
            assertThat(body.readAllBytes()).isEqualTo(expectedBody);
        }
    }
}
