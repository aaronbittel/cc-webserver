package com.github.aaronbittel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

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

        var tokens = tokenize(path);

        IntPredicate isLastIndex = i -> i == tokens.size() - 1;

        for (int i = 0; i < tokens.size(); i++) {
            switch (tokens.get(i)) {
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
                continue;
            }

            tokens.add(new RouteSegment.Literal(segment));
        }

        return tokens;
    }

    private static boolean isValidSegment(String segment) {
        return !segment.equals(".") && !segment.equals("..");
    }
}

sealed interface RoutePattern {

    String path();

    record ExactRoute(String path) implements RoutePattern {}

    record SubtreeRoute(String path) implements RoutePattern {}
}

sealed interface RouteSegment {

    record Literal(String value) implements RouteSegment {}

    record TerminalWildcard() implements RouteSegment {}
}
