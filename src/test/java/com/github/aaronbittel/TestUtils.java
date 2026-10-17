package com.github.aaronbittel;

import static org.assertj.core.api.Assertions.assertThat;

public class TestUtils {

    static final ResponseWriter NO_OP_RESPONSE_WRITER = response -> {};

    public static HttpHeaders buildHeaders(String... pairs) {
        if (pairs.length % 2 != 0) {
            throw new IllegalArgumentException("Expected key-value pairs");
        }

        var headers = new HttpHeaders();

        for (int i = 0; i < pairs.length; i += 2) {
            headers.add(pairs[i], pairs[i + 1]);
        }

        return headers;
    }

    public static HttpRequest request(HttpMethod method, String requestString) {
        return new HttpRequest(
                new HttpRequestLine(method, requestString, HttpVersion.HTTP_1_1),
                new HttpHeaders(),
                new byte[0]
        );
    }

    public static void assertNotFound(HttpResponseRecorder recorder) {
        assertThat(recorder.response()).isEqualTo(new HttpResponse(HttpVersion.HTTP_1_1, HttpStatus.NOT_FOUND));
    }
}
