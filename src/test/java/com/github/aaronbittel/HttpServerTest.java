package com.github.aaronbittel;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
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
    void returns_400_bad_request_when_request_line_is_malformed() throws IOException {
        try (var server = new TestHttpServer(new HttpServer.Builder(new Router()))) {
            try (var client = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
                var out = client.getOutputStream();
                out.write("MALFORMED MESSAGE!\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                out.flush();

                var reader = new BufferedReader(
                        new InputStreamReader(client.getInputStream(), StandardCharsets.US_ASCII));

                assertThat(reader.readLine()).isEqualTo("HTTP/1.1 400 Bad Request");
            }
        }
    }

    @Test
    @Tag("slow")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void returns_408_request_timeout_when_request_is_incomplete() throws IOException {
        var server = new TestHttpServer(new HttpServer.Builder(new Router())
                .requestParsingTimeout(Duration.ofSeconds(1)));

        try (server) {
            try (var client = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
                var out = client.getOutputStream();
                out.write("GET / HTTP/1.1\r\nHost:".getBytes(StandardCharsets.US_ASCII));
                out.flush();

                var reader = new BufferedReader(
                        new InputStreamReader(client.getInputStream(), StandardCharsets.US_ASCII));

                assertThat(reader.readLine()).isEqualTo("HTTP/1.1 408 Request Timeout");
            }
        }
    }

    @Test
    @Tag("slow")
    void returns_408_request_timeout_when_clients_takes_too_long_sending_request() throws IOException,
            InterruptedException {
        var server = new TestHttpServer(new HttpServer.Builder(new Router())
                .requestParsingTimeout(Duration.ofSeconds(1)));

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

                    var reader = new BufferedReader(new InputStreamReader(client.getInputStream(),
                            StandardCharsets.US_ASCII));
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
                assertThatExceptionOfType(TimeoutException.class).isThrownBy(() -> shutdownFuture.get(200,
                        TimeUnit.MILLISECONDS));
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
    void keeps_http_1_1_connection_open_by_default() throws IOException {
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

    @Test
    void closes_http_1_0_connection_by_default() throws IOException {
        var request = "GET / HTTP/1.0\r\n\r\n".getBytes(StandardCharsets.UTF_8);

        try (var server = new TestHttpServer(new HttpServer.Builder(new Router()))) {
            try (var client = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
                var in = new BufferedInputStream(client.getInputStream());
                var out = client.getOutputStream();

                out.write(request);
                out.flush();

                var response = HttpResponseWriterTest.HttpResponseAssert.readFrom(in);
                response.hasStatusLine("HTTP/1.0 404 Not Found");

                assertConnectionClosed(client, Duration.ofMillis(200));
            }
        }
    }

    @Test
    void closes_http_1_1_connection_when_close_is_requested() throws IOException {
        try (var server = new TestHttpServer(new HttpServer.Builder(new Router()))) {
            try (var client = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
                var out = client.getOutputStream();
                var request = """
                        GET / HTTP/1.1
                        Connection: close
                        
                        """.replace("\n", "\r\n").getBytes(StandardCharsets.UTF_8);
                out.write(request);
                out.flush();

                var in = new BufferedInputStream(client.getInputStream());
                HttpResponseWriterTest.HttpResponseAssert.readFrom(in)
                        .hasStatusLine("HTTP/1.1 404 Not Found")
                        .hasHeader("Connection: close");

                assertConnectionClosed(client, Duration.ofMillis(200));
            }
        }
    }

    @Test
    void keeps_http_1_0_connection_open_when_keep_alive_is_requested() throws IOException {
        try (var server = new TestHttpServer(new HttpServer.Builder(new Router()))) {
            try (var client = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
                var out = client.getOutputStream();
                var request = """
                        GET / HTTP/1.0
                        Connection: keep-alive
                        
                        """.replace("\n", "\r\n").getBytes(StandardCharsets.UTF_8);
                out.write(request);
                out.flush();

                var in = new BufferedInputStream(client.getInputStream());
                HttpResponseWriterTest.HttpResponseAssert.readFrom(in)
                        .hasStatusLine("HTTP/1.0 404 Not Found")
                        .hasHeader("Connection: keep-alive");

                request = "GET / HTTP/1.0\r\n\r\n".getBytes(StandardCharsets.UTF_8);
                out.write(request);
                out.flush();

                HttpResponseWriterTest.HttpResponseAssert.readFrom(in)
                        .hasStatusLine("HTTP/1.0 404 Not Found");
            }
        }
    }

    @Test
    @Tag("slow")
    void closes_connection_when_idle_timeout_expires_after_successful_request() throws IOException {
        var idleTimeout = Duration.ofSeconds(2);

        try (var server = new TestHttpServer(new HttpServer.Builder(new Router())
                .idleTimeout(idleTimeout))) {
            try (var client = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
                var out = client.getOutputStream();
                var in = new BufferedInputStream(client.getInputStream());
                out.write("GET / HTTP/1.1\r\n\r\n".getBytes(StandardCharsets.UTF_8));
                out.flush();

                HttpResponseWriterTest.HttpResponseAssert.readFrom(in)
                        .hasStatusLine("HTTP/1.1 404 Not Found");

                assertConnectionClosed(client, idleTimeout.plusMillis(50));
            }
        }
    }

    private static void assertConnectionClosed(Socket socket, Duration timeout) throws IOException {
        socket.setSoTimeout(Math.toIntExact(timeout.toMillis()));

        try {
            assertThat(socket.getInputStream().read())
                    .as("Server should close the connection within %s", timeout)
                    .isEqualTo(-1);
        } catch (SocketTimeoutException e) {
            Assertions.fail("Server did not close the connection within %s".formatted(timeout), e);
        }
    }
}
