package com.github.aaronbittel;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIOException;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatNoException;

public class HttpServerTest {

    @BeforeAll
    static void set_log_level_to_warning() {
        var log = Logger.getLogger(HttpServer.class.getName());
        log.setLevel(Level.WARNING);
    }

    @Test
    void returns_400_bad_request_when_message_is_malformed() throws IOException {
        try (var server = new TestHttpServer(new HttpServer.Builder(new Router()))) {
            try (var client = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
                client.getOutputStream().write("MALFORMED MESSAGE!\r\n".getBytes(StandardCharsets.UTF_8));
                client.getOutputStream().flush();

                var reader = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.US_ASCII));
                assertThat(reader.readLine()).isEqualTo("HTTP/1.1 400 Bad Request");
            }
        }
    }

    @Test
    @Tag("slow")
    void returns_408_request_timeout_when_client_request_times_out() throws IOException {
        var server = new TestHttpServer(new HttpServer.Builder(new Router())
                .socketReadTimeout(Duration.ofSeconds(5))
                .requestParsingTimeout(Duration.ofSeconds(1)));

        try (server) {
            try (var client = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
                client.getOutputStream().write("TIMEOUT!".getBytes(StandardCharsets.UTF_8));
                client.getOutputStream().flush();

                var reader = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.US_ASCII));
                assertThat(reader.readLine()).isEqualTo("HTTP/1.1 408 Request Timeout");
            }
        }
    }

    @Test
    @Tag("slow")
    void returns_408_request_timeout_when_clients_takes_too_long_sending_request() throws IOException, InterruptedException {
        var server = new TestHttpServer(new HttpServer.Builder(new Router())
                .socketReadTimeout(Duration.ofMillis(500))
                .requestParsingTimeout(Duration.ofSeconds(2)));

        try (server) {
            try (var client = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
                client.setSoTimeout(3_000);

                var sender = Thread.ofPlatform().start(() -> {
                    try {
                        while (!Thread.currentThread().isInterrupted()) {
                            client.getOutputStream().write('a');
                            client.getOutputStream().flush();
                            Thread.sleep(100);
                        }
                    } catch (IOException | InterruptedException _) {}
                });

                try {
                    var reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
                    assertThat(reader.readLine()).isEqualTo("HTTP/1.1 408 Request Timeout");
                } finally {
                    sender.interrupt();
                    sender.join(1_000);
                }
            }
        }
    }

    @Test
    @Tag("slow")
    void after_shutdown_server_does_not_accept_new_connections() throws IOException {
        var server = new TestHttpServer(new HttpServer.Builder(new Router()));
        var port = server.port();
        server.close();

        try (var client = new Socket()) {
            assertThatIOException().isThrownBy(() ->
                    client.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port)));
        }
    }

    @Test
    @Tag("slow")
    void started_tasks_are_completed_after_server_shutdown() throws Exception {
        var handlerStarted = new CountDownLatch(1);
        var allowHandlerToFinish = new CountDownLatch(1);

        var router = new Router()
                .add(HttpMethod.GET, "/", (writer, _) -> {
                    handlerStarted.countDown();

                    try {
                        if (!allowHandlerToFinish.await(3, TimeUnit.SECONDS)) {
                            throw new RuntimeException("Handler was not released");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }

                    HttpResponses.notFound(writer);
                });

        var server = new TestHttpServer(new HttpServer.Builder(router));

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var clientFuture = executor.submit(() -> {
                try (var client = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
                    client.setSoTimeout(3_000);
                    client.getOutputStream().write("GET / HTTP/1.1\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                    client.getOutputStream().flush();

                    var reader = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.US_ASCII));
                    return reader.readLine();
                }
            });

            assertThat(handlerStarted.await(3, TimeUnit.SECONDS)).isTrue();

            var shutdownFuture = executor.submit(() -> {
                server.close();
                return null;
            });

            try {
                // Not ideal due to timing uncertainty, but sufficient to check that shutdown waits for the handler.
                assertThatExceptionOfType(TimeoutException.class).isThrownBy(() -> shutdownFuture.get(200, TimeUnit.MILLISECONDS));
            } finally {
                allowHandlerToFinish.countDown();
            }

            shutdownFuture.get(5, TimeUnit.SECONDS);
            assertThat(clientFuture.get(5, TimeUnit.SECONDS))
                    .isEqualTo("HTTP/1.1 404 Not Found");
        } finally {
            server.close();
        }
    }

    @Test
    void handles_multiple_requests_on_the_same_connection() throws IOException {
        var request = "GET / HTTP/1.1\r\n\r\n".getBytes(StandardCharsets.UTF_8);

        try (var server = new TestHttpServer(new HttpServer.Builder(new Router()))) {
            try (var client = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
                var in = new BufferedInputStream(client.getInputStream());
                var out = client.getOutputStream();

                out.write(request);
                out.flush();

                var response = HttpResponseWriterTest.HttpResponseAssert.readFrom(in);
                response.hasStatusLine("HTTP/1.1 404 Not Found");

                out.write(request);
                out.flush();

                assertThatNoException().isThrownBy(() -> {
                    var response2 = HttpResponseWriterTest.HttpResponseAssert.readFrom(in);
                    response2.hasStatusLine("HTTP/1.1 404 Not Found");
                });
            }
        }
    }
}
