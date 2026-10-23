package com.github.aaronbittel;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

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
}
