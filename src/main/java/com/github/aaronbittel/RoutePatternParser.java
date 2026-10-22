package com.github.aaronbittel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;
import java.util.stream.Collectors;

final class RoutePatternParser {

    private RoutePatternParser() {}

    public static RoutePattern parse(String path) {
        var sourcePath = path;

        if (path.equals("/")) {
            return new RoutePattern.SubtreeRoute("/");
        }

        if (path.equals("/{$}")) {
            return new RoutePattern.ExactRoute("/");
        }

        var hasTrailingSlash = false;
        if (path.endsWith("/")) {
            hasTrailingSlash = true;
            path = path.substring(0, path.length() - 1);
        }

        var segments = tokenize(path);

        IntPredicate isLastIndex = i -> i == segments.size() - 1;

        for (int i = 0; i < segments.size(); i++) {
            switch (segments.get(i)) {
                case RouteSegment.Literal(var segment) -> {
                    if (!isValidSegment(segment)) {
                        throw new IllegalArgumentException(
                                "Illegal route segment %s in route %s".formatted(segment, sourcePath));
                    }
                }
                case RouteSegment.TerminalWildcard _ -> {
                    if (!isLastIndex.test(i)) {
                        throw new IllegalArgumentException(
                                "Terminal Wildcard Pattern must be the last segment: " + sourcePath);
                    }
                    if (hasTrailingSlash) {
                        throw new IllegalArgumentException(
                                "Terminal Wildcard Pattern is not allowed in subtree routes: " + sourcePath);
                    }

                    return new RoutePattern.ExactRoute(pathWithoutTerminalWildcard(path));
                }
                case RouteSegment.Parameter(var name) -> {
                    if (!isValidPathParameter(name)) {
                        throw new IllegalArgumentException(
                                "Invalid Path Parameter %s in route %s".formatted(name, sourcePath));
                    }
                    if (hasTrailingSlash) {
                        throw new IllegalArgumentException(
                                "Path Parameter is not allowed in subtree routes: " + sourcePath);
                    }
                    if (!isLastIndex.test(i)) {
                        throw new IllegalArgumentException(
                                "Path Parameter must be the last segment: " + sourcePath);
                    }

                    return new RoutePattern.ParameterRoute(sourcePath, segments);
                }
            }
        }

        if (hasTrailingSlash) {
            return new RoutePattern.SubtreeRoute(sourcePath);
        }

        return new RoutePattern.ExactRoute(sourcePath);
    }

    private static String pathWithoutTerminalWildcard(String path) {
        if (path.equals("/{$}") || !path.endsWith("/{$}")) {
            throw new IllegalStateException("Illegal argument: " + path);
        }

        return path.substring(0, path.length() - 4);
    }

    private static List<RouteSegment> tokenize(String path) {
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException("Route pattern must start with '/': " + path);
        }

        var tokens = new ArrayList<RouteSegment>();

        for (var segment : path.substring(1).split("/", -1)) {
            if (segment.isEmpty()) {
                throw new IllegalArgumentException("Route segment must not be empty: " + path);
            }

            if (segment.equals("{$}")) {
                tokens.add(new RouteSegment.TerminalWildcard());
            } else if (segment.startsWith("{") && segment.endsWith("}")) {
                tokens.add(new RouteSegment.Parameter(segment.substring(1, segment.length() - 1)));
            } else {
                tokens.add(new RouteSegment.Literal(segment));
            }
        }

        return tokens;
    }

    private static boolean isValidSegment(String segment) {
        if (segment.equals(".") || segment.equals("..")) {
            return false;
        }

        return segment.matches("[a-zA-Z0-9._~-]+");
    }

    private static boolean isValidPathParameter(String name) {
        return !name.isBlank()
                && !name.contains("{")
                && !name.contains("}");
    }
}

sealed interface RoutePattern {

    String path();

    record ExactRoute(String path) implements RoutePattern {}

    record SubtreeRoute(String path) implements RoutePattern {}

    record ParameterRoute(String path, List<RouteSegment> segments) implements RoutePattern {

        public String canonical() {
            return "/" + segments.stream()
                    .map(segment -> switch (segment) {
                        case RouteSegment.Literal(var value) -> value;
                        case RouteSegment.Parameter(var _) -> "{}";
                        case RouteSegment.TerminalWildcard _ -> throw new IllegalStateException(
                                "Parameter Route must not contain a Terminal Wildcard");
                    })
                    .collect(Collectors.joining("/"));
        }
    }
}

sealed interface RouteSegment {

    record Literal(String value) implements RouteSegment {}

    record TerminalWildcard() implements RouteSegment {}

    record Parameter(String name) implements RouteSegment {}
}
