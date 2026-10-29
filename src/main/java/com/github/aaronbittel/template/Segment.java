package com.github.aaronbittel.template;

public sealed interface Segment {
    record TextSegment(String text) implements Segment {}

    record ExpressionSegment(String property) implements Segment {}
}
