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
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.github.aaronbittel.TestUtilis.buildHeaders;
import static org.assertj.core.api.Assertions.*;

class HttpServerTest {

    private static final Path BASE_PATH = Path.of("base").toAbsolutePath().normalize();

    @BeforeAll
    static void set_log_level_to_warning() {
        var log = Logger.getLogger(HttpServer.class.getName());
        log.setLevel(Level.WARNING);
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

        createSymlinkOrAbort(symPath, fileOutsideRoot);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> HttpServer.resolveRequestPath(root, "/" + symPath))
                .withMessage("Path traversal detected");
    }

    @Test
    void resolves_relative_symlink_inside_root(@TempDir Path tempDir) throws IOException {
        Path root = Files.createDirectory(tempDir.resolve("www"));
        Path documents = Files.createDirectory(root.resolve("documents"));

        Path targetFile = documents.resolve("report.txt");
        Files.writeString(targetFile, "Hello from the target");

        Path symlink = root.resolve("latest.txt");
        createSymlinkOrAbort(symlink, root.relativize(targetFile));

        Path resolved = HttpServer.resolveRequestPath(root, "/latest.txt");

        assertThat(targetFile.toRealPath()).isEqualTo(resolved.toRealPath());
    }

    @Test
    void query_string_does_not_affect_resource_resolution(@TempDir Path tempDir) throws IOException {
        var content = "index-file-content".getBytes(StandardCharsets.UTF_8);
        var path = tempDir.resolve("index.html");
        Files.write(path, content);

        try (var server = createTestServer(tempDir)) {
            var writer = new HttpResponseRecorder();
            var request = request(HttpMethod.GET, "/index.html?lang=de");
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

    @Test
    void server_shutdown_is_handled_gracefully() throws Exception {
        var config = new ServerConfig(
                InetAddress.getLoopbackAddress(),
                0,
                Path.of(".")
        );

        var server = new HttpServer(config);

        var executor = Executors.newSingleThreadExecutor();

        try {
            var future = executor.submit(() -> {
                server.serve();
                return null;
            });

            Thread.sleep(200);
            server.close();

            assertThatCode(() -> future.get(3, TimeUnit.SECONDS))
                    .doesNotThrowAnyException();
        } finally {
            server.close();
            executor.shutdownNow();
        }
    }

    private static HttpServer createTestServer(Path root) {
        return new HttpServer(
                new ServerConfig(
                        InetAddress.getLoopbackAddress(),
                        80,
                        root
                )
        );
    }

    private static void createSymlinkOrAbort(Path link, Path target) throws IOException {
        try {
            Files.createSymbolicLink(link, target);
        } catch (FileSystemException e) {
            throw new TestAbortedException("Cannot create symbolic link; privileges may be missing", e);
        }
    }

    private static HttpRequest request(HttpMethod method, String requestString) {
        return new HttpRequest(
                new HttpRequestLine(method, requestString, HttpVersion.HTTP_1_1),
                new HttpHeaders(),
                new byte[0]
        );
    }
}
