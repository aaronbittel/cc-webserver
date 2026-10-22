package com.github.aaronbittel;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Objects;

public final class DeadlineInputStream extends InputStream {

    private final Socket socket;
    private final InputStream in;
    private final long deadline;

    public DeadlineInputStream(Socket socket, InputStream in, long deadline) {
        this.socket = Objects.requireNonNull(socket);
        this.in = in;
        this.deadline = deadline;
    }

    @Override
    public int read() throws IOException {
        long remaining = deadline - System.nanoTime();

        if (remaining <= 0) {
            throw new SocketTimeoutException("Request deadline exceeded");
        }

        long millis = remaining / 1_000_000L;
        if (remaining % 1_000_000L != 0) {
            millis++;
        }

        socket.setSoTimeout(Math.clamp(millis, 1, Integer.MAX_VALUE));

        int result = in.read();

        if (System.nanoTime() - deadline >= 0) {
            throw new SocketTimeoutException("Request deadline exceeded");
        }

        return result;
    }

    @Override
    public void close() throws IOException {
        in.close();
    }
}
