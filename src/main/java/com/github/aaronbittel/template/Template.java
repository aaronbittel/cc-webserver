package com.github.aaronbittel.template;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
            segments.add(new Segment.ExpressionSegment(expression));
            source = source.substring(end + 2);
        }

        return new Template(segments);
    }

    public String render(Map<String, Object> data) {
        if (data == null) {
            data = Map.of();
        }

        var sb = new StringBuilder();

        for (var segment : segments) {
            var sbAppend = switch (segment) {
                case Segment.ExpressionSegment(var expression) -> resolveProperty(data, expression);
                case Segment.TextSegment(var text) -> text;
            };
            sb.append(sbAppend);
        }

        return sb.toString();
    }

    private static String resolveProperty(Map<String, Object> data, String expression) {
        var value = data.get(expression);
        return switch (value) {
            case null -> "";
            case String s -> s;
            case Character c -> String.valueOf(c);
            case Number n -> String.valueOf(n);
            case Boolean b -> String.valueOf(b);
            default -> throw new IllegalArgumentException("Illegal type: " + value.getClass().getName());
        };
    }
}
