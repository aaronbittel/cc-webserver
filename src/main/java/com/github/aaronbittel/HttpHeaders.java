package com.github.aaronbittel;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

public class HttpHeaders implements Iterable<Map.Entry<String, String>> {

    private final Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    public HttpHeaders set(String key, String value) {
        if (!HttpHeaderValidator.isValidFieldName(key)) {
            throw new IllegalArgumentException("Illegal field name");
        }

        if (!HttpHeaderValidator.isValidFieldValue(value)) {
            throw new IllegalArgumentException("Illegal field value");
        }

        headers.put(key, value);
        return this;
    }

    public Optional<String> get(String key) {
        return Optional.ofNullable(headers.get(key));
    }

    @Override
    public Iterator<Map.Entry<String, String>> iterator() {
        return headers.entrySet().iterator();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof HttpHeaders that)) return false;
        return Objects.equals(headers, that.headers);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(headers);
    }

    Map<String, String> headers() {
        return Collections.unmodifiableMap(new TreeMap<>(headers));
    }

    @Override
    public String toString() {
        return headers.toString();
    }
}
