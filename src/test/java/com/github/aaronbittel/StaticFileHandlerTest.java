package com.github.aaronbittel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.github.aaronbittel.TestUtils.request;
import static org.assertj.core.api.Assertions.assertThat;

class StaticFileHandlerTest {

    private static final String FILENAME = "hello-world.txt";
    private static final String CONTENT = "Hello, World";

    @TempDir
    Path root;

    StaticFileHandler staticFileHandler;
    HttpResponseRecorder recorder;

    @BeforeEach
    void setUp() throws IOException {
        Files.writeString(root.resolve(FILENAME), CONTENT);
        staticFileHandler = new StaticFileHandler(root);
        recorder = new HttpResponseRecorder();
    }

    @Nested
    class Get {

        @Test
        void returns_file_contents_when_file_exists() throws IOException {
            staticFileHandler.handleRequest(recorder, request(HttpMethod.GET, "/" + FILENAME));

            var response = recorder.result();

            assertThat(response.status()).isEqualTo(HttpStatus.OK);
            assertThat(response.headers().get("Content-Type")).hasValue("text/plain");
            assertThat(new String(response.body(), StandardCharsets.UTF_8)).isEqualTo(CONTENT);
        }

        @Test
        void returns_not_found_when_file_does_not_exist() throws IOException {
            staticFileHandler.handleRequest(recorder, request(HttpMethod.GET, "/unknown-file"));
            assertThat(recorder.result().status()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void query_string_does_not_affect_resource_resolution() throws IOException {
            staticFileHandler.handleRequest(recorder, request(HttpMethod.GET, "/" + FILENAME + "?lang=de"));

            var response = recorder.result();

            assertThat(response.status()).isEqualTo(HttpStatus.OK);
            assertThat(response.headers().get("Content-Type")).hasValue("text/plain");
            assertThat(new String(response.body(), StandardCharsets.UTF_8)).isEqualTo(CONTENT);
        }
    }
}
