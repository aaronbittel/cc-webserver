package com.github.aaronbittel;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.PushbackInputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ConnectionHandler implements Runnable {

    private static final Logger log = Logger.getLogger(ConnectionHandler.class.getName());

    private final Socket socket;
    private final HttpHandler handler;
    private final Duration requestParsingTimeout;
    private final Duration idleTimeout;

    public ConnectionHandler(Socket socket, HttpHandler handler, Duration requestParsingTimeout, Duration idleTimeout) {
        this.socket = socket;
        this.handler = handler;
        this.requestParsingTimeout = requestParsingTimeout;
        this.idleTimeout = idleTimeout;
    }

    @Override
    public void run() {
        try (socket) {
            handleConnection(socket);
        } catch (IOException e) {
            log.log(Level.FINE, "Connection I/O failed", e);
        }
    }

    private void handleConnection(Socket socket) throws IOException {
        var in = new PushbackInputStream(new BufferedInputStream(socket.getInputStream()));
        var out = new BufferedOutputStream(socket.getOutputStream());

        var closeConnection = false;
        while (!closeConnection) {
            log.fine(() -> "accepted client: " + socket.getRemoteSocketAddress());

            var parser = new HttpRequestParser(new DeadlineInputStream(socket, in,
                    System.nanoTime() + requestParsingTimeout.toNanos()));

            HttpRequest request;

            try {
                request = parser.parseRequest();
                log.fine(() -> "request:" + request.requestLine());
            } catch (IllegalArgumentException e) {
                log.fine(() -> "Invalid HTTP request: " + e.getMessage());

                var writer = HttpResponseWriter.forMalformedRequest();
                HttpResponses.badRequestAndCloseConnection(writer);
                writer.finish(out, HttpVersion.HTTP_1_1);
                return;
            } catch (SocketTimeoutException e) {
                log.fine(() -> "Socket timeout: " + e.getMessage());

                var writer = HttpResponseWriter.forMalformedRequest();
                writer.headers().set("Connection", "close");
                writer.setStatus(HttpStatus.REQUEST_TIMEOUT);
                writer.finish(out, HttpVersion.HTTP_1_1);
                return;
            }

            if (request.version() == HttpVersion.HTTP_1_0) {
                closeConnection = true;
            }

            var writer = HttpResponseWriter.forRequest(request.method());
            handler.handleRequest(writer, request);

            if (clientRequestsConnectionClose(request.headers())) {
                closeConnection = true;
                writer.headers().set("Connection", "close");
            } else if (clientRequestsConnectionKeepAlive(request.headers())) {
                closeConnection = false;
                writer.headers().set("Connection", "keep-alive");
            }

            writer.finish(out, request.version());

            if (closeConnection || !awaitNextRequest(socket, in)) {
                break;
            }
        }
    }

    /**
     * Waits for the client to begin sending the next request, up to the configured idle timeout.
     *
     * <p> Returns {@code false} if the client closes the connection or the idle timeout expires. Returns {@code true}
     * if at least one byte is received, restoring the byte to the input stream so that it can be consumed by the
     * request parser.
     *
     * @param socket the client socket
     * @param in     the pushback input stream used to read client requests
     * @return {@code true} if client data is available, {@code false} if the client disconnects or the idle timeout
     *         expires
     * @throws IOException if an I/O error occurs while reading from the input stream
     */
    private boolean awaitNextRequest(Socket socket, PushbackInputStream in) throws IOException {
        socket.setSoTimeout(Math.toIntExact(idleTimeout.toMillis()));
        try {
            var b = in.read();
            if (b == -1) { // client closed connection
                return false;
            }
            in.unread(b); // put read byte back into stream
        } catch (SocketTimeoutException e) {
            log.log(Level.FINE, "Idle timeout expired; closing connection");
            return false;
        }
        return true;
    }

    private static boolean clientRequestsConnectionClose(HttpHeaders headers) {
        return headers.get("Connection")
                .stream()
                .flatMap(value -> Arrays.stream(value.split(",")))
                .map(String::trim)
                .anyMatch("close"::equalsIgnoreCase);
    }

    private static boolean clientRequestsConnectionKeepAlive(HttpHeaders headers) {
        return headers.get("Connection")
                .stream()
                .flatMap(value -> Arrays.stream(value.split(",")))
                .map(String::trim)
                .anyMatch("keep-alive"::equalsIgnoreCase);
    }
}
