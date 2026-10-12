package com.github.aaronbittel;

public class TestUtilis {
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
}
