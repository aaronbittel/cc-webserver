package com.github.aaronbittel;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Main {

    private static final String DEFAULT_ADDRESS = "127.0.0.1";
    private static final int DEFAULT_PORT = 80;
    private static final Path DEFAULT_BASE_PATH = Path.of(".").toAbsolutePath().normalize();

    static void main() throws IOException {
        Path root;
        InetAddress address;
        int port;

        try {
            root = Path.of(readEnvVar("CC_DIRECTORY", DEFAULT_BASE_PATH.toString()));
            address = InetAddress.ofLiteral(readEnvVar("CC_ADDRESS", DEFAULT_ADDRESS));
            port = Integer.parseInt(readEnvVar("CC_PORT", String.valueOf(DEFAULT_PORT)));
        } catch (IllegalArgumentException e) {
            System.err.println("Configuration error: " + e.getMessage());
            return;
        }

        var staticFileHandler = new StripPrefixHandler(new StaticFileHandler(root), "/static");

        var usersRouter = new Router()
                .add(HttpMethod.GET, "/", (writer, request) -> {
                    var body = "Users home".getBytes(StandardCharsets.UTF_8);
                    writer.write(new HttpResponse(
                            request.version(),
                            HttpStatus.OK,
                            new HttpHeaders()
                                    .add("Content-Type", "text/plain")
                                    .add("Content-Length", String.valueOf(body.length)),
                            body
                    ));
                })
                .add(HttpMethod.GET, "/profile", (writer, request) -> {
                    var body = "User profile".getBytes(StandardCharsets.UTF_8);
                    writer.write(new HttpResponse(
                            request.version(),
                            HttpStatus.OK,
                            new HttpHeaders()
                                    .add("Content-Type", "text/plain")
                                    .add("Content-Length", String.valueOf(body.length)),
                            body
                    ));
                });

        var router = new Router()
                .add(HttpMethod.GET, "/", (writer, request) -> {
                    var body = "Hello, World!".getBytes(StandardCharsets.UTF_8);
                    writer.write(new HttpResponse(
                            request.version(),
                            HttpStatus.OK,
                            new HttpHeaders()
                                    .add("Content-Type", "text/plain")
                                    .add("Content-Length", String.valueOf(body.length)),
                            body
                    ));
                })
                .add(HttpMethod.GET, "/static/", staticFileHandler)
                .add(HttpMethod.GET, "/time", (writer, request) -> {
                    var now = LocalDateTime.now()
                            .format(DateTimeFormatter.ofPattern("HH:mm:ss"));

                    var body = ("{\"time\":\"" + now + "\"}").getBytes(StandardCharsets.UTF_8);

                    var headers = new HttpHeaders()
                            .add("Content-Type", "application/json")
                            .add("Content-Length", String.valueOf(body.length));

                    writer.write(new HttpResponse(request.version(), HttpStatus.OK, headers, body));
                })
                .mount("/users", usersRouter);

        try (var server = new HttpServer(router)) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Shutting down server...");
                try {
                    server.close();
                } catch (IOException e) {
                    System.err.println("Error shutting down server: " + e.getMessage());
                }
            }
            ));
            server.start(address, port);
        }
    }

    private static String readEnvVar(String name, String fallback) {
        var value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
