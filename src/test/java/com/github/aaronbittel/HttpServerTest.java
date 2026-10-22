package com.github.aaronbittel;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

public class HttpServerTest {

    TestHttpServer server;

    @BeforeAll
    static void set_log_level_to_warning() {
        var log = Logger.getLogger(HttpServer.class.getName());
        log.setLevel(Level.SEVERE);
    }

    @BeforeEach
    void setUp() throws IOException {
        server = new TestHttpServer();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.close();
    }

    @Test
    void returns_400_bad_request_when_message_is_malformed() throws IOException {
        try (var client = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
            client.getOutputStream().write("MALFORMED MESSAGE!\r\n".getBytes(StandardCharsets.UTF_8));
            client.getOutputStream().flush();

            var reader = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.US_ASCII));
            assertThat(reader.readLine()).isEqualTo("HTTP/1.1 400 Bad Request");
        }
    }

    @Test
    void returns_408_request_timeout_when_client_request_times_out() throws IOException {
        try (var client = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
            client.getOutputStream().write("TIMEOUT!".getBytes(StandardCharsets.UTF_8));
            client.getOutputStream().flush();

            var reader = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.US_ASCII));
            assertThat(reader.readLine()).isEqualTo("HTTP/1.1 408 Request Timeout");
        }
    }
}
