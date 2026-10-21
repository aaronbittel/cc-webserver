package com.github.aaronbittel;

import static org.assertj.core.api.Assertions.assertThat;

public class TestUtils {

    static final HttpHandler NO_OP_HANDLER = (_, _) -> {};

    static class RecordingHandler implements HttpHandler {
        private HttpRequest received;

        @Override
        public void handleRequest(ResponseWriter writer, HttpRequest request) {
            received = request;
        }

        public HttpRequest received() {
            return received;
        }

        public boolean wasCalled() {
            return received != null;
        }

        public void reset() {
            received = null;
        }
    }

    static void assertStatus(HttpResponseRecorder recorder, HttpStatus expected) {
        assertThat(recorder.result().status()).isEqualTo(expected);
    }

    static void assertWasCalled(RecordingHandler recordingHandler) {
        assertThat(recordingHandler.wasCalled()).isTrue();
    }

    static void assertWasNotCalled(RecordingHandler recordingHandler) {
        assertThat(recordingHandler.wasCalled()).isFalse();
    }

    static HttpHeaders buildHeaders(String... pairs) {
        if (pairs.length % 2 != 0) {
            throw new IllegalArgumentException("Expected key-value pairs");
        }

        var headers = new HttpHeaders();

        for (int i = 0; i < pairs.length; i += 2) {
            headers.set(pairs[i], pairs[i + 1]);
        }

        return headers;
    }

    static HttpRequest request(HttpMethod method, String requestString) {
        return new HttpRequest(
                new HttpRequestLine(method, requestString, HttpVersion.HTTP_1_1),
                new HttpHeaders(),
                new byte[0]
        );
    }

    static class NoOpResponseWriter implements ResponseWriter {

        @Override
        public void setStatus(HttpStatus status) {}

        @Override
        public HttpHeaders headers() {
            return null;
        }

        @Override
        public void write(String text) {}

        @Override
        public void write(byte[] data) {}
    }
}
