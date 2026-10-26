package com.github.aaronbittel;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;

class MiddlewareChainTest {

    @Test
    void empty_middleware_chain_calls_handler() throws IOException {
        var chain = new MiddlewareChain();
        AtomicBoolean called = new AtomicBoolean(false);
        HttpHandler handler = (_, _) -> called.set(true);

        execute(chain.wrap(handler));
        assertThat(called).isTrue();
        assertThat(chain.wrap(handler)).isSameAs(handler);
    }

    @Test
    void middleware_executes_in_registration_order_and_unwinds_in_reverse() throws IOException {
        var events = new ArrayList<String>();

        var chain = new MiddlewareChain(
                recordingMiddleware("m1", events),
                recordingMiddleware("m2", events),
                recordingMiddleware("m3", events)
        );

        HttpHandler handler = (_, _) -> events.add("handler");
        execute(chain.wrap(handler));

        assertThat(events).containsExactly(
                "before m1",
                "before m2",
                "before m3",
                "handler",
                "after m3",
                "after m2",
                "after m1"
        );
    }

    @Test
    void middleware_can_prevent_handler_execution() throws IOException {
        var called = new AtomicBoolean(false);

        Middleware middleware = _ -> (_, _) -> {}; // Deliberately don't call next.

        var wrapped = new MiddlewareChain(middleware).wrap((_, _) -> called.set(true));
        execute(wrapped);

        assertThat(called).isFalse();
    }

    @Test
    void propagates_handler_exception() {
        HttpHandler handler = (_, _) -> {throw new IOException("test");};

        var wrapped = new MiddlewareChain(
                recordingMiddleware("m1", new ArrayList<>())
        ).wrap(handler);

        assertThatIOException().isThrownBy(() -> execute(wrapped)).withMessage("test");
    }

    private Middleware recordingMiddleware(String name, List<String> events) {
        return next -> (writer, request) -> {
            events.add("before " + name);
            next.handleRequest(writer, request);
            events.add("after " + name);
        };
    }

    private void execute(HttpHandler handler) throws IOException {
        handler.handleRequest(new TestUtils.NoOpResponseWriter(), TestUtils.request(HttpMethod.GET, "/"));
    }
}
