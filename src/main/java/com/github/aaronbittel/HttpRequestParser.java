package com.github.aaronbittel;

import java.io.IOException;
import java.io.InputStream;

public class HttpRequestParser {

    private final InputStream in;

    private static final String CRLF = "\r\n";
    private static final int MAXIMUM_HTTP_LINE_LENGTH = 8192;

    public HttpRequestParser(InputStream in) {
        this.in = in;
    }

    HttpRequest parseRequest() throws IOException {
        var line = readHttpLine(in);
        var requestLine = parseRequestLine(line);
        var headers = parseHeaders(in);
        var bodyLength = Integer.parseInt(headers.get("Content-Length").orElse("0"));

        var body = in.readNBytes(bodyLength);
        if (body.length < bodyLength) { // stream was closed?
            throw new IllegalArgumentException("Stream was closed");
        }

        return new HttpRequest(requestLine, headers, body);
    }

    private static HttpRequestLine parseRequestLine(String line) {
        var parts = line.split(" ", 3);
        if (parts.length != 3) {
            throw new IllegalArgumentException("Illegal request line format: " + line);
        }

        var method = HttpMethod.parse(parts[0]);
        var requestTarget = parts[1];
        if (!requestTarget.startsWith("/")) {
            throw new IllegalArgumentException("Expected origin-form request target");
        }
        var version = HttpVersion.parse(parts[2]);

        return new HttpRequestLine(method, requestTarget, version);
    }

    static HttpHeaders parseHeaders(InputStream in) throws IOException {
        var headers = new HttpHeaders();

        String fieldLine;
        while (true) {
            fieldLine = readHttpLine(in);
            if (fieldLine.isEmpty()) {
                break;
            }

            var parts = fieldLine.split(":", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Missing colon separator");
            }
            var fieldName = parts[0];
            if (!isValidFieldName(fieldName)) {
                throw new IllegalArgumentException("Illegal field name");
            }
            var fieldValue = parseFieldValue(parts[1]);

            headers.set(fieldName, fieldValue);
        }

        return headers;
    }

    private static String parseFieldValue(String value) {
        if (value.endsWith(CRLF)) {
            value = value.substring(0, value.length() - 2);
        }

        var trimmed = value.trim();
        if (!HttpHeaderValidator.isValidFieldValue(value)) {
            throw new IllegalArgumentException("Contains illegal characters");
        }
        return trimmed;
    }

    private static boolean isValidFieldName(String name) {
        var trimmed = name.trim();
        if (!trimmed.equals(name)) {
            return false;
        }

        return HttpHeaderValidator.isValidFieldName(trimmed);
    }

    /**
     * Reads bytes from the input stream until the next CRLF (`\r\n`) is encountered and returns the decoded line
     * without the trailing CRLF.
     *
     * @param in the input stream to read from
     * @return the line without the trailing CRLF
     * @throws IOException if an I/O error occurs while reading the stream
     */
    private static String readHttpLine(InputStream in) throws IOException {
        var sb = new StringBuilder();

        int c;
        while ((c = in.read()) != -1) {
            switch (c) {
                case '\r' -> {
                    if (in.read() == '\n') {
                        return sb.toString();
                    }
                    throw new IOException("Invalid HTTP line ending");
                }
                case '\n' -> throw new IOException("Invalid HTTP line ending");
                default -> {
                    if (sb.length() >= MAXIMUM_HTTP_LINE_LENGTH) {
                        throw new IOException("HTTP line exceeds maximum length");
                    }
                    sb.append((char) c);
                }
            }
        }

        throw new IOException("Unexpected end of stream before HTTP line terminator");
    }
}
