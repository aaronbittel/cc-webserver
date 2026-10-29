package com.github.aaronbittel.template;

import java.util.List;

public sealed interface Segment {
    record TextSegment(String text) implements Segment {}

    record ExpressionSegment(List<String> path) implements Segment {}
}
