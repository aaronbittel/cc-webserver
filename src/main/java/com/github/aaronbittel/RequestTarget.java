package com.github.aaronbittel;

public class RequestTarget {

    private final String rawTarget;
    private final String path;
    private final String query;
    private final boolean hasQuery;

    private RequestTarget(String rawTarget, String path, String query, boolean hasQuery) {
        this.rawTarget = rawTarget;
        this.path = path;
        this.query = query;
        this.hasQuery = hasQuery;
    }

    public static RequestTarget parse(String rawTarget) {
        if (!rawTarget.startsWith("/")) {
            throw new IllegalArgumentException("Request target must start with '/'");
        }

        int queryIndex = rawTarget.indexOf('?');

        if (queryIndex == -1) {
            return new RequestTarget(rawTarget, rawTarget, "", false);
        }

        return new RequestTarget(
                rawTarget,
                rawTarget.substring(0, queryIndex),
                rawTarget.substring(queryIndex + 1),
                true
        );
    }

    public String path() {
        return path;
    }

    public String rawTarget() {
        return rawTarget;
    }

    public String query() {
        return query;
    }

    public boolean hasQuery() {
        return hasQuery;
    }
}
