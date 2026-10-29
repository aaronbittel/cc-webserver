package com.github.aaronbittel.template;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class Template {

    private final List<Segment> segments;

    private Template(List<Segment> segments) {
        this.segments = segments;
    }

    public static Template parse(Path path) throws IOException {
        return parse(Files.readString(path));
    }

    public static Template parse(String source) {
        List<Segment> segments = new ArrayList<>();

        while (!source.isEmpty()) {
            var start = source.indexOf("{{");
            if (start == -1) {
                segments.add(new Segment.TextSegment(source));
                break;
            }

            var text = source.substring(0, start);
            if (!text.isEmpty()) {
                segments.add(new Segment.TextSegment(text));
            }
            source = source.substring(start + 2);
            var end = source.indexOf("}}");
            if (end == -1) {
                throw new IllegalArgumentException("No matching ending '}}' found");
            }
            var expression = source.substring(0, end).trim();
            if (!expression.startsWith(".")) {
                throw new IllegalArgumentException("Expression must start with a '.'");
            }
            expression = expression.substring(1);
            if (expression.chars().anyMatch(Character::isWhitespace)) {
                throw new IllegalArgumentException("Expression must not contain whitespace");
            }
            if (expression.isEmpty()) {
                throw new IllegalArgumentException("Expression must not be empty");
            }
            segments.add(new Segment.ExpressionSegment(Arrays.stream(expression.split("\\.")).toList()));
            source = source.substring(end + 2);
        }

        return new Template(segments);
    }

    public String render(Object data) {
        var sb = new StringBuilder();

        for (var segment : segments) {
            var sbAppend = switch (segment) {
                case Segment.ExpressionSegment(var path) -> escapeHtml(valueToString(resolvePath(data, path)));
                case Segment.TextSegment(var text) -> text;
            };
            sb.append(sbAppend);
        }

        return sb.toString();
    }

    private static Object resolvePath(Object data, List<String> path) {
        var current = data;
        for (var property : path) {
            current = resolveProperty(current, property);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private static Object resolveProperty(Object data, String property) {
        if (data == null) {
            return null;
        }

        if (data instanceof Map<?, ?> map) {
            return map.get(property);
        }

        Method method = getMethod(data, property);

        try {
            return method.invoke(data);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot access property '%s' on type %s"
                    .formatted(property, data.getClass().getName()), e);
        } catch (InvocationTargetException e) {
            var cause = e.getCause();

            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }

            throw new IllegalStateException("Property method failed", cause);
        }
    }

    private static Method getMethod(Object data, String name) {
        Method method;
        try {
            method = data.getClass().getMethod(name);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("No method '%s()' on type %s"
                    .formatted(name, data.getClass().getName()), e);
        }

        if (method.getReturnType() == void.class) {
            throw new IllegalArgumentException("method '%s' must not return void".formatted(method.getName()));
        }

        return method;
    }

    private static String valueToString(Object value) {
        return switch (value) {
            case null -> "";
            case String s -> s;
            case Character c -> String.valueOf(c);
            case Number n -> String.valueOf(n);
            case Boolean b -> String.valueOf(b);
            default -> throw new IllegalArgumentException("Illegal type: " + value.getClass().getName());
        };
    }

    private static String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
