package com.github.aaronbittel;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@Timeout(value = 3, unit = TimeUnit.SECONDS)
public class HttpServerE2ETest {

    private static final HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(1))
            .build();

    private HttpServer server;
    private ExecutorService executor;
    private Future<?> serveFuture;

    @TempDir
    private Path root;

    @BeforeAll
    static void set_log_level_to_warning() {
        var log = Logger.getLogger(HttpServer.class.getName());
        log.setLevel(Level.WARNING);
    }

    @BeforeEach
    void setupServer() throws IOException {
        var config = new ServerConfig(
                InetAddress.getLoopbackAddress(),
                0,
                root
        );

        server = new HttpServer(config);
        server.bind();

        executor = Executors.newSingleThreadExecutor();

        serveFuture = executor.submit(() -> {
            server.serve();
            return null;
        });
    }

    @AfterEach
    void tearDownServer() throws IOException {
        if (server != null) {
            server.close();
        }

        if (executor != null) {
            executor.close();
        }
    }

    @Test
    void server_shutdown_is_handled_gracefully() throws IOException, InterruptedException {
        get("/"); // ensure executor started server
        server.close();

        assertThatCode(() -> serveFuture.get(3, TimeUnit.SECONDS)).doesNotThrowAnyException();
    }

    @Test
    void get_root_returns_index_html_content() throws IOException, InterruptedException {
        var fileContent = "index-file-content";
        Files.writeString(root.resolve("index.html"), fileContent);

        var response = get("/");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(fileContent);
    }

    @Test
    void get_returns_not_found_for_nonexistent_file() throws IOException, InterruptedException {
        assertThat(get("/fielpath").statusCode()).isEqualTo(404);
    }

    @Test
    void head_returns_headers_without_body() throws IOException, InterruptedException {
        var fileContent = "index-file-content".getBytes(StandardCharsets.UTF_8);
        Files.write(root.resolve("index.html"), fileContent);

        var response = head("/index.html");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type")).hasValue("text/html");
        assertThat(response.headers().firstValue("Content-Length")).hasValue(String.valueOf(fileContent.length));
        assertThat(response.body()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(
            value = HttpMethod.class,
            mode = EnumSource.Mode.EXCLUDE,
            names = {"GET", "HEAD"}
    )
    void unsupported_method_returns_method_not_allowed(HttpMethod method) throws IOException, InterruptedException {
        assertThat(requestNoBody(method, "/index.html").statusCode())
                .isEqualTo(HttpStatus.METHOD_NOT_ALLOWED.code());
    }

    @Test
    void get_to_root_returns_directory_listing() throws IOException, InterruptedException {
        Files.createDirectory(root.resolve("docs"));
        Files.createFile(root.resolve("root.bin"));

        var response = get("/");

        assertThat(response.statusCode()).isEqualTo(200);
        assertDirectoryEntry(response.body(), "/docs", "docs");
        assertDirectoryEntry(response.body(), "/root.bin", "root.bin");
    }

    @Test
    void get_to_subdirectory_returns_directory_listing() throws IOException, InterruptedException {
        var directory = Files.createDirectory(root.resolve("docs"));
        Files.createFile(directory.resolve("first.txt"));
        Files.createFile(directory.resolve("second.txt"));

        var response = get("/docs");

        assertThat(response.statusCode()).isEqualTo(200);
        assertDirectoryEntry(response.body(), "/", "..");
        assertDirectoryEntry(response.body(), "/docs/first.txt", "first.txt");
        assertDirectoryEntry(response.body(), "/docs/second.txt", "second.txt");
    }

    private HttpResponse<String> get(String resource) throws IOException, InterruptedException {
        var request = requestBuilder(resource)
                .GET()
                .build();

        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> head(String resource) throws IOException, InterruptedException {
        var request = requestBuilder(resource)
                .HEAD()
                .build();

        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> requestNoBody(HttpMethod method, String resource) throws IOException, InterruptedException {
        var request = requestBuilder(resource)
                .method(method.name(), HttpRequest.BodyPublishers.noBody())
                .build();

        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest.Builder requestBuilder(String resource) {
        return HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + server.port() + resource))
                .timeout(Duration.ofSeconds(1));
    }

    private void assertDirectoryEntry(String html, String linkPath, String displayName) {
        assertThat(html)
                .contains("href=\"" + linkPath + "\"")
                .contains(">" + displayName + "</a>");
    }
}
