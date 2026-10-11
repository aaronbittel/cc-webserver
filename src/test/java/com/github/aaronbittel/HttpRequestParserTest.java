package com.github.aaronbittel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

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
                HttpResponse.EMPTY_BODY
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

    public static HttpHeaders buildHeaders(String... pairs) {
        if (pairs.length % 2 != 0) {
            throw new IllegalArgumentException("Expected key-value pairs");
        }

        var headers = new HttpHeaders();

        for (int i = 0; i < pairs.length; i += 2) {
            headers.add(pairs[i], pairs[i + 1]);
        }

        return headers;
    }
}