package com.github.aaronbittel;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ConnectionHandler implements Runnable {

    private static final Logger log = Logger.getLogger(ConnectionHandler.class.getName());

    private final Socket socket;
    private final Router router;
    private final Duration socketReadTimeout;
    private final Duration requestParsingTimeout;

    public ConnectionHandler(Socket socket, Router router, Duration socketReadTimeout, Duration requestParsingTimeout) {
        this.socket = socket;
        this.router = router;
        this.socketReadTimeout = socketReadTimeout;
        this.requestParsingTimeout = requestParsingTimeout;
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
        socket.setSoTimeout(Math.toIntExact(socketReadTimeout.toMillis()));

        log.fine(() -> "accepted client: " + socket.getRemoteSocketAddress());

        var in = new BufferedInputStream(socket.getInputStream());
        var parser = new HttpRequestParser(new DeadlineInputStream(socket, in, System.nanoTime() + requestParsingTimeout.toNanos()));
        var out = new BufferedOutputStream(socket.getOutputStream());

        HttpRequest request;

        try {
            request = parser.parseRequest();
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

        log.fine(() -> "request:" + request.requestLine());

        var writer = HttpResponseWriter.forRequest(request.method());
        router.handleRequest(writer, request);
        writer.finish(out, request.version());
    }
}
