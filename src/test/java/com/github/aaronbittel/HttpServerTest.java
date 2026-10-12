package com.github.aaronbittel;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.opentest4j.TestAbortedException;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.github.aaronbittel.TestUtilis.buildHeaders;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class HttpServerTest {

    private static final Path BASE_PATH = Path.of("base").toAbsolutePath().normalize();

    @BeforeAll
    static void set_log_level_to_warning() {
        var log = Logger.getLogger(HttpServer.class.getName());
        log.setLevel(Level.WARNING);
    }

    @Test
    void root_request_resolves_to_index_html() throws IOException {
        var expected = BASE_PATH.resolve("index.html");
        assertThat(HttpServer.resolveRequestPath(BASE_PATH, "/")).isEqualTo(expected);
    }

    @Test
    void reject_path_not_starting_with_slash() {
        assertThatIllegalArgumentException().isThrownBy(() -> HttpServer.resolveRequestPath(BASE_PATH, "some-path"));
    }

    @Test
    void reject_path_traversal() {
        assertThatIllegalArgumentException().isThrownBy(() -> HttpServer.resolveRequestPath(BASE_PATH, "/../somepath"))
                .withMessage("Path traversal detected");
    }

    @Test
    void normalize_dot_segments_in_request_path() throws IOException {
        var expected = BASE_PATH.resolve("baz");
        assertThat(HttpServer.resolveRequestPath(BASE_PATH, "/foo/bar/../../baz")).isEqualTo(expected);
    }

    @Test
    void reject_symlink_pointing_outside_of_root(@TempDir Path tempDir) throws IOException {
        var fileOutsideRoot = tempDir.resolve("file-outside-root");
        Files.writeString(fileOutsideRoot, "Hello, World!");

        var root = tempDir.resolve("root");
        Files.createDirectory(root);

        var symPath = root.resolve("symlink");

        try {
            Files.createSymbolicLink(symPath, fileOutsideRoot);
        } catch (FileSystemException e) {
            throw new TestAbortedException("Symbolic links are unavailable in this environment: " + e);
        }

        assertThatIllegalArgumentException()
                .isThrownBy(() -> HttpServer.resolveRequestPath(root, "/" + symPath))
                .withMessage("Path traversal detected");
    }

    @Nested
    class HttpServerMethodTest {

        @TempDir
        Path root;
        String FILENAME = "hello-world";
        String CONTENT = "Hello, World";
        String contentLength = String.valueOf(CONTENT.getBytes(StandardCharsets.UTF_8).length);

        HttpServer server;
        HttpResponseRecorder recorder;

        @BeforeEach
        void setUp() throws IOException {
            var filepath = root.resolve(FILENAME);
            Files.writeString(filepath, CONTENT);

            server = new HttpServer(new ServerConfig(InetAddress.getLoopbackAddress(), 80, root));
            recorder = new HttpResponseRecorder();
        }

        HttpRequest request(HttpMethod method, String targetPath) {
            return new HttpRequest(
                    new HttpRequestLine(method, "/" + targetPath, HttpVersion.HTTP_1_1),
                    new HttpHeaders(), HttpResponse.EMPTY_BODY
            );
        }

        @Nested
        class Get {
            @Test
            void returns_file_contents_when_file_exists() throws IOException {
                server.handleRequest(recorder, request(HttpMethod.GET, FILENAME));

                var headers = buildHeaders("Content-Type", "text/html", "Content-Length", contentLength);
                var expected = new HttpResponse(HttpVersion.HTTP_1_1, HttpStatus.OK, headers, CONTENT.getBytes(StandardCharsets.UTF_8));

                assertThat(recorder.response()).isEqualTo(expected);
            }

            @Test
            void returns_not_found_when_file_does_not_exist() throws IOException {
                server.handleRequest(recorder, request(HttpMethod.GET, "unknown-file"));

                var expected = new HttpResponse(HttpVersion.HTTP_1_1, HttpStatus.NOT_FOUND);
                assertThat(recorder.response()).isEqualTo(expected);
            }
        }

        @Nested
        class Head {
            @Test
            void returns_headers_without_body_when_file_exists() throws IOException {
                server.handleRequest(recorder, request(HttpMethod.HEAD, FILENAME));

                var headers = buildHeaders("Content-Type", "text/html", "Content-Length", contentLength);
                var expected = new HttpResponse(HttpVersion.HTTP_1_1, HttpStatus.OK, headers, HttpResponse.EMPTY_BODY);
                assertThat(recorder.response()).isEqualTo(expected);
            }

            @Test
            void returns_not_found_when_file_does_not_exist() throws IOException {
                server.handleRequest(recorder, request(HttpMethod.HEAD, "unknown-file"));

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
}