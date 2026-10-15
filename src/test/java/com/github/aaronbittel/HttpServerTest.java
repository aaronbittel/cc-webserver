package com.github.aaronbittel;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.github.aaronbittel.TestUtilis.buildHeaders;
import static com.github.aaronbittel.TestUtilis.request;
import static org.assertj.core.api.Assertions.assertThat;

class HttpServerTest {

    @BeforeAll
    static void set_log_level_to_warning() {
        var log = Logger.getLogger(HttpServer.class.getName());
        log.setLevel(Level.WARNING);
    }

    @Test
    void query_string_does_not_affect_resource_resolution(@TempDir Path tempDir) throws IOException {
        var content = "index-file-content".getBytes(StandardCharsets.UTF_8);
        var path = tempDir.resolve("index.html");
        Files.write(path, content);

        try (var server = createTestServer(tempDir)) {
            var writer = new HttpResponseRecorder();
            var request = TestUtilis.request(HttpMethod.GET, "/index.html?lang=de");
            server.handleRequest(writer, request);

            var expected = new HttpResponse(
                    request.requestLine().version(),
                    HttpStatus.OK,
                    buildHeaders(
                            "Content-Type", "text/html",
                            "Content-Length", String.valueOf(content.length)
                    ),
                    content
            );
            assertThat(writer.response()).isEqualTo(expected);
        }
    }

    @Nested
    class HttpServerMethodTest {

        @TempDir
        Path root;
        String FILENAME = "hello-world.txt";
        byte[] CONTENT = "Hello, World".getBytes(StandardCharsets.UTF_8);
        String contentLength = String.valueOf(CONTENT.length);

        HttpServer server;
        HttpResponseRecorder recorder;

        @BeforeEach
        void setUp() throws IOException {
            var filepath = root.resolve(FILENAME);
            Files.write(filepath, CONTENT);

            server = createTestServer(root);
            recorder = new HttpResponseRecorder();
        }

        @Nested
        class Get {
            @Test
            void returns_file_contents_when_file_exists() throws IOException {
                server.handleRequest(recorder, request(HttpMethod.GET, "/" + FILENAME));

                var headers = buildHeaders("Content-Type", "text/plain", "Content-Length", contentLength);
                var expected = new HttpResponse(HttpVersion.HTTP_1_1, HttpStatus.OK, headers, CONTENT);

                assertThat(recorder.response()).isEqualTo(expected);
            }

            @Test
            void returns_not_found_when_file_does_not_exist() throws IOException {
                server.handleRequest(recorder, request(HttpMethod.GET, "/unknown-file"));

                var expected = new HttpResponse(HttpVersion.HTTP_1_1, HttpStatus.NOT_FOUND);
                assertThat(recorder.response()).isEqualTo(expected);
            }
        }

        @Nested
        class Head {
            @Test
            void returns_headers_without_body_when_file_exists() throws IOException {
                server.handleRequest(recorder, request(HttpMethod.HEAD, "/" + FILENAME));

                var headers = buildHeaders("Content-Type", "text/plain", "Content-Length", contentLength);
                var expected = new HttpResponse(HttpVersion.HTTP_1_1, HttpStatus.OK, headers);
                assertThat(recorder.response()).isEqualTo(expected);
            }

            @Test
            void returns_not_found_when_file_does_not_exist() throws IOException {
                server.handleRequest(recorder, request(HttpMethod.HEAD, "/unknown-file"));

                var expected = new HttpResponse(HttpVersion.HTTP_1_1, HttpStatus.NOT_FOUND);
                assertThat(recorder.response()).isEqualTo(expected);
            }
        }

        @ParameterizedTest
        @EnumSource(
                value = HttpMethod.class,
                mode = EnumSource.Mode.EXCLUDE,
                names = {"GET", "HEAD"}
        )
        void returns_method_not_allowed_for_existing_file(HttpMethod method) throws IOException {
            server.handleRequest(recorder, request(method, FILENAME));

            var expected = new HttpResponse(HttpVersion.HTTP_1_1, HttpStatus.METHOD_NOT_ALLOWED);
            assertThat(recorder.response()).isEqualTo(expected);
        }

        @ParameterizedTest
        @EnumSource(
                value = HttpMethod.class,
                mode = EnumSource.Mode.EXCLUDE,
                names = {"GET", "HEAD"}
        )
        void returns_method_not_allowed_for_not_existing_file(HttpMethod method) throws IOException {
            server.handleRequest(recorder, request(method, "unknown-file"));

            var expected = new HttpResponse(HttpVersion.HTTP_1_1, HttpStatus.METHOD_NOT_ALLOWED);
            assertThat(recorder.response()).isEqualTo(expected);
        }
    }

    private static HttpServer createTestServer(Path root) {
        return new HttpServer(
                new ServerConfig(
                        InetAddress.getLoopbackAddress(),
                        0,
                        root
                )
        );
    }
}
