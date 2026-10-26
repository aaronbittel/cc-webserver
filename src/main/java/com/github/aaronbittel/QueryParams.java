package com.github.aaronbittel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QueryParams {

    private final Map<String, List<String>> values = new HashMap<>();

    /**
     * Parses a raw query string into query parameters. Repeated keys are mapped to multiple values in encounter order.
     * Missing values and empty values are represented as empty strings. Percent-encoding is preserved without
     * decoding.
     *
     * @param query the raw query string, without a leading '?'
     * @return the parsed query parameters
     */
    public static QueryParams parse(String query) {
        if (query.isEmpty()) {
            return new QueryParams();
        }

        var params = new QueryParams();
        var pairs = query.split("&");

        for (var pair : pairs) {
            var parts = pair.split("=", 2);
            var key = PercentDecoder.decode(parts[0]);
            var list = params.values.computeIfAbsent(key, _ -> new ArrayList<>());
            list.add(parts.length == 2 ? PercentDecoder.decode(parts[1]) : "");
        }

        return params;
    }

    public List<String> get(String key) {
        return values.getOrDefault(key, List.of());
    }
}
