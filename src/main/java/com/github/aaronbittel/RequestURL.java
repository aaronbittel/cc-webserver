package com.github.aaronbittel;

public class RequestURL {

    private final String path;
    private final String query;
    private final boolean hasQuery;

    private RequestURL(String path, String query, boolean hasQuery) {
        this.path = path;
        this.query = query;
        this.hasQuery = hasQuery;
    }

    public RequestURL withPath(String newPath) {
        return new RequestURL(newPath, query, hasQuery);
    }

    public static RequestURL parse(String rawTarget) {
        if (!rawTarget.startsWith("/")) {
            throw new IllegalArgumentException("Request target must start with '/'");
        }

        int queryIndex = rawTarget.indexOf('?');

        if (queryIndex == -1) {
            return new RequestURL(rawTarget, "", false);
        }

        return new RequestURL(
                rawTarget.substring(0, queryIndex),
                rawTarget.substring(queryIndex + 1),
                true
        );
    }

    /**
     * Parses this URL's query string into query parameters.
     *
     * @return the parsed query parameters
     */
    public QueryParams parseQuery() {
        return QueryParams.parse(query);
    }

    public String path() {
        return path;
    }

    public String query() {
        return query;
    }

    public boolean hasQuery() {
        return hasQuery;
    }
}
