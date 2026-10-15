package com.github.aaronbittel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.github.aaronbittel.TestUtilis.buildHeaders;
import static com.github.aaronbittel.TestUtilis.request;
import static org.assertj.core.api.Assertions.assertThat;

class StaticFileHandlerTest {

    private static final String FILENAME = "hello-world.txt";
    private static final byte[] CONTENT = "Hello, World".getBytes(StandardCharsets.UTF_8);
    private static final String CONTENT_LENGTH = String.valueOf(CONTENT.length);

    @TempDir
    Path root;

    StaticFileHandler staticFileHandler;
    HttpResponseRecorder recorder;

    @BeforeEach
    void setUp() throws IOException {
        Files.write(root.resolve(FILENAME), CONTENT);
        staticFileHandler = new StaticFileHandler(root);
        recorder = new HttpResponseRecorder();
    }

    @Nested
    class Get {

        @Test
        void returns_file_contents_when_file_exists() throws IOException {
            staticFileHandler.handleRequest(recorder, request(HttpMethod.GET, "/" + FILENAME));

            var headers = buildHeaders(
                    "Content-Type", "text/plain",
                    "Content-Length", CONTENT_LENGTH
            );

            var expected = new HttpResponse(HttpVersion.HTTP_1_1, HttpStatus.OK, headers, CONTENT);
            assertThat(recorder.response()).isEqualTo(expected);
        }

        @Test
        void returns_not_found_when_file_does_not_exist() throws IOException {
            staticFileHandler.handleRequest(recorder, request(HttpMethod.GET, "/unknown-file"));

            var expected = new HttpResponse(HttpVersion.HTTP_1_1, HttpStatus.NOT_FOUND);
            assertThat(recorder.response()).isEqualTo(expected);
        }

        @Test
        void query_string_does_not_affect_resource_resolution() throws IOException {
            staticFileHandler.handleRequest(recorder, request(HttpMethod.GET, "/" + FILENAME + "?lang=de"));

            var headers = buildHeaders(
                    "Content-Type", "text/plain",
                    "Content-Length", CONTENT_LENGTH
            );

            var expected = new HttpResponse(HttpVersion.HTTP_1_1, HttpStatus.OK, headers, CONTENT);
            assertThat(recorder.response()).isEqualTo(expected);
        }
    }

    @Nested
    class Head {

        @Test
        void returns_headers_without_body_when_file_exists() throws IOException {
            staticFileHandler.handleRequest(recorder, request(HttpMethod.HEAD, "/" + FILENAME));

            var headers = buildHeaders(
                    "Content-Type", "text/plain",
                    "Content-Length", CONTENT_LENGTH
            );

            var expected = new HttpResponse(HttpVersion.HTTP_1_1, HttpStatus.OK, headers);
            assertThat(recorder.response()).isEqualTo(expected);
        }

        @Test
        void returns_not_found_when_file_does_not_exist() throws IOException {
            staticFileHandler.handleRequest(recorder, request(HttpMethod.HEAD, "/unknown-file"));

            var expected = new HttpResponse(HttpVersion.HTTP_1_1, HttpStatus.NOT_FOUND);
            assertThat(recorder.response()).isEqualTo(expected);
        }
    }
}
