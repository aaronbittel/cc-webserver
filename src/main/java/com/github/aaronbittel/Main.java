package com.github.aaronbittel;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Main {

    private static final String DEFAULT_ADDRESS = "127.0.0.1";
    private static final int DEFAULT_PORT = 8080;
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
                .add(HttpMethod.GET, "/", Main::usersHomeHandler)
                .add(HttpMethod.GET, "/profile", Main::userProfileHandler);

        var router = new Router()
                .add(HttpMethod.GET, "/", Main::homeHandler)
                .add(HttpMethod.GET, "/static/", staticFileHandler)
                .add(HttpMethod.GET, "/time", Main::timeHandler)
                .mount("/users", usersRouter);

        var chain = new MiddlewareChain(Main::loggingMiddleware, Main::timingMiddleware);

        try (var server = new HttpServer.Builder(chain.wrap(router)).build()) {
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

    private static void homeHandler(ResponseWriter writer, HttpRequest ignored) throws IOException {
        writer.headers().set("Content-Type", "text/plain");
        writer.write("Hello, World!");
    }

    private static void usersHomeHandler(ResponseWriter writer, HttpRequest ignored) throws IOException {
        writer.headers().set("Content-Type", "text/plain");
        writer.write("Users home");
    }

    private static void userProfileHandler(ResponseWriter writer, HttpRequest ignored) throws IOException {
        writer.headers().set("Content-Type", "text/plain");
        writer.write("User profile");
    }

    private static void timeHandler(ResponseWriter writer, HttpRequest request) throws IOException {
        var defaultPattern = "HH:mm:ss";
        var query = request.requestUrl().parseQuery();
        var formats = query.get("format");
        var pattern = formats.isEmpty() ? defaultPattern : formats.getFirst();

        DateTimeFormatter formatter;
        try {
            formatter = DateTimeFormatter.ofPattern(pattern);
        } catch (IllegalArgumentException _) {
            writer.setStatus(HttpStatus.BAD_REQUEST);
            writer.headers().set("Content-Type", "application/json");
            writer.write("{\"error\":\"Invalid date-time format\"}");
            return;
        }

        var now = LocalDateTime.now().format(formatter);
        var body = "{\"time\":\"" + now + "\"}";

        writer.headers().set("Content-Type", "application/json");
        writer.write(body);
    }

    private static HttpHandler loggingMiddleware(HttpHandler next) {
        return (writer, request) -> {
            var start = System.nanoTime();
            var method = request.method();
            var path = request.requestUrl().path();

            System.out.printf("-> %s %s%n", method, path);

            try {
                next.handleRequest(writer, request);
            } finally {
                var elapsed = System.nanoTime() - start;
                System.out.printf("<- %s %s (%.2f ms)%n", method, path, elapsed / 1_000_000.0);
            }
        };
    }

    private static HttpHandler timingMiddleware(HttpHandler next) {
        return (writer, request) -> {
            var start = System.nanoTime();

            next.handleRequest(writer, request);

            var elapsed = System.nanoTime() - start;
            writer.headers().set("X-Response-Time", elapsed / 1_000_000.0 + " ms");
        };
    }

    private static String readEnvVar(String name, String fallback) {
        var value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
