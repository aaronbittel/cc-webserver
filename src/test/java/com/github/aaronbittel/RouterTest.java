package com.github.aaronbittel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class RouterTest {

    private TestUtils.RecordingHandler recordingHandler;
    private HttpResponseRecorder recorder;

    @BeforeEach
    void setUp() {
        recordingHandler = new TestUtils.RecordingHandler();
        recorder = new HttpResponseRecorder();
    }

    @Test
    void dispatches_to_handler_when_method_and_path_match() throws IOException {
        var path = "/hello-world";
        var router = new Router()
                .add(HttpMethod.GET, path, recordingHandler);

        router.handleRequest(recorder, TestUtils.request(HttpMethod.GET, path));
        assertThat(recordingHandler.wasCalled()).isTrue();
    }

    @Test
    void returns_not_found_when_no_route_matches_path() throws IOException {
        var router = new Router();
        var request = TestUtils.request(HttpMethod.GET, "/path-not-registered");

        router.handleRequest(recorder, request);
        TestUtils.assertStatus(recorder, HttpStatus.NOT_FOUND);
    }

    @Test
    void returns_method_not_allowed_when_path_matches_but_method_does_not() throws IOException {
        var path = "/path";

        var router = new Router()
                .add(HttpMethod.GET, path, TestUtils.NO_OP_HANDLER);

        var request = TestUtils.request(HttpMethod.POST, path);

        router.handleRequest(recorder, request);
        TestUtils.assertStatus(recorder, HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(recorder.response().headers().get("Allow")).hasValue("GET, HEAD");
    }

    @Test
    void dispatches_to_handler_matching_request_method() throws IOException {
        var getHandler = new TestUtils.RecordingHandler();
        var postHandler = new TestUtils.RecordingHandler();

        var path = "/path";
        var router = new Router()
                .add(HttpMethod.GET, path, getHandler)
                .add(HttpMethod.POST, path, postHandler);

        router.handleRequest(recorder, TestUtils.request(HttpMethod.GET, path));

        assertThat(getHandler.wasCalled()).isTrue();
        assertThat(postHandler.wasCalled()).isFalse();

        getHandler.reset();
        postHandler.reset();

        router.handleRequest(recorder, TestUtils.request(HttpMethod.POST, path));

        assertThat(getHandler.wasCalled()).isFalse();
        assertThat(postHandler.wasCalled()).isTrue();
    }

    @Test
    void dispatches_to_handler_matching_request_path() throws IOException {
        var firstHandler = new TestUtils.RecordingHandler();
        var secondHandler = new TestUtils.RecordingHandler();

        var router = new Router()
                .add(HttpMethod.GET, "/first", firstHandler)
                .add(HttpMethod.GET, "/second", secondHandler);

        router.handleRequest(recorder, TestUtils.request(HttpMethod.GET, "/first"));
        TestUtils.assertWasCalled(firstHandler);
        TestUtils.assertWasNotCalled(secondHandler);

        firstHandler.reset();
        secondHandler.reset();

        router.handleRequest(recorder, TestUtils.request(HttpMethod.GET, "/second"));
        TestUtils.assertWasNotCalled(firstHandler);
        TestUtils.assertWasCalled(secondHandler);
    }

    @Test
    void rejects_route_registration_without_leading_slash() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new Router().add(HttpMethod.GET, "route-without-slash", TestUtils.NO_OP_HANDLER)
        );
    }

    @Test
    void matches_route_when_request_path_contains_query_string() throws IOException {
        var path = "/route";
        var router = new Router()
                .add(HttpMethod.GET, path, recordingHandler);

        router.handleRequest(recorder, TestUtils.request(HttpMethod.GET, path + "?query=value"));
        TestUtils.assertWasCalled(recordingHandler);
    }

    @Test
    void rejects_duplicate_route_registration() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Router()
                .add(HttpMethod.GET, "/hello-world", TestUtils.NO_OP_HANDLER)
                .add(HttpMethod.GET, "/hello-world", TestUtils.NO_OP_HANDLER));
    }

    @Test
    void matches_non_subtree_route_only_exactly() throws IOException {
        var router = new Router()
                .add(HttpMethod.GET, "/static", recordingHandler);

        router.handleRequest(recorder, TestUtils.request(HttpMethod.GET, "/static"));
        TestUtils.assertWasCalled(recordingHandler);
    }

    @Test
    void does_not_match_non_subtree_route_with_trailing_slash() throws IOException {
        var router = new Router()
                .add(HttpMethod.GET, "/static", TestUtils.NO_OP_HANDLER);

        router.handleRequest(recorder, TestUtils.request(HttpMethod.GET, "/static/"));
        TestUtils.assertStatus(recorder, HttpStatus.NOT_FOUND);
    }

    @Test
    void redirects_to_trailing_slash_when_matching_subtree_route_exists() throws IOException {
        var router = new Router()
                .add(HttpMethod.GET, "/static/", TestUtils.NO_OP_HANDLER);

        router.handleRequest(recorder, TestUtils.request(HttpMethod.GET, "/static"));
        TestUtils.assertStatus(recorder, HttpStatus.MOVED_PERMANENTLY);
        assertThat(recorder.response().headers().get("Location")).hasValue("/static/");
    }

    @Test
    void matches_subtree_route_at_base_path_with_trailing_slash() throws IOException {
        var router = new Router()
                .add(HttpMethod.GET, "/static/", recordingHandler);

        router.handleRequest(recorder, TestUtils.request(HttpMethod.GET, "/static/"));
        TestUtils.assertWasCalled(recordingHandler);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/static/docs", "/static/docs/directory"})
    void matches_nested_paths_for_subtree_route(String path) throws IOException {
        var router = new Router()
                .add(HttpMethod.GET, "/static/", recordingHandler);

        router.handleRequest(recorder, TestUtils.request(HttpMethod.GET, path));
        TestUtils.assertWasCalled(recordingHandler);
    }

    @Test
    void does_not_match_nested_path_for_non_subtree_route() throws IOException {
        var router = new Router()
                .add(HttpMethod.GET, "/static", recordingHandler);

        router.handleRequest(recorder, TestUtils.request(HttpMethod.GET, "/static/docs"));
        TestUtils.assertWasNotCalled(recordingHandler);
    }

    @Test
    void falls_back_to_get_route_when_head_route_is_not_defined() throws IOException {
        var router = new Router()
                .add(HttpMethod.GET, "/static", recordingHandler);

        router.handleRequest(recorder, TestUtils.request(HttpMethod.HEAD, "/static"));
        TestUtils.assertWasCalled(recordingHandler);
    }

    @Test
    void prefers_explicit_route_over_subtree_route() throws IOException {
        var exactHandler = new TestUtils.RecordingHandler();
        var subtreeHandler = new TestUtils.RecordingHandler();

        var router = new Router()
                .add(HttpMethod.GET, "/static", exactHandler)
                .add(HttpMethod.GET, "/static/", subtreeHandler);

        router.handleRequest(recorder, TestUtils.request(HttpMethod.GET, "/static"));
        TestUtils.assertWasCalled(exactHandler);
        TestUtils.assertWasNotCalled(subtreeHandler);
    }

    @Test
    void prefers_subtree_route_when_request_path_has_trailing_slash() throws IOException {
        var exactHandler = new TestUtils.RecordingHandler();
        var subtreeHandler = new TestUtils.RecordingHandler();

        var router = new Router()
                .add(HttpMethod.GET, "/static", exactHandler)
                .add(HttpMethod.GET, "/static/", subtreeHandler);

        router.handleRequest(recorder, TestUtils.request(HttpMethod.HEAD, "/static/"));
        TestUtils.assertWasCalled(subtreeHandler);
        TestUtils.assertWasNotCalled(exactHandler);
    }

    @Test
    void prefers_explicit_head_route_over_get_route() throws IOException {
        var getHandler = new TestUtils.RecordingHandler();
        var headHandler = new TestUtils.RecordingHandler();

        var router = new Router()
                .add(HttpMethod.GET, "/static", getHandler)
                .add(HttpMethod.HEAD, "/static", headHandler);

        router.handleRequest(recorder, TestUtils.request(HttpMethod.HEAD, "/static"));
        TestUtils.assertWasCalled(headHandler);
        TestUtils.assertWasNotCalled(getHandler);
    }

    @Test
    void does_not_match_paths_sharing_only_subtree_prefix()
            throws IOException {
        var router = new Router()
                .add(HttpMethod.GET, "/static/", recordingHandler);

        router.handleRequest(recorder, TestUtils.request(HttpMethod.GET, "/staticity"));

        TestUtils.assertWasNotCalled(recordingHandler);
        TestUtils.assertStatus(recorder, HttpStatus.NOT_FOUND);
    }

    @Test
    void redirects_to_subtree_path_even_if_method_is_not_allowed() throws IOException {
        var router = new Router()
                .add(HttpMethod.GET, "/static/", TestUtils.NO_OP_HANDLER);

        router.handleRequest(recorder, TestUtils.request(HttpMethod.POST, "/static"));
        TestUtils.assertStatus(recorder, HttpStatus.MOVED_PERMANENTLY);
    }

    @Test
    void redirects_to_subtree_base_even_when_parent_subtree_matches() throws IOException {
        var router = new Router()
                .add(HttpMethod.GET, "/", TestUtils.NO_OP_HANDLER)
                .add(HttpMethod.GET, "/static/", TestUtils.NO_OP_HANDLER);

        router.handleRequest(recorder, TestUtils.request(HttpMethod.GET, "/static"));
        TestUtils.assertStatus(recorder, HttpStatus.MOVED_PERMANENTLY);
        assertThat(recorder.response().headers().get("Location"))
                .hasValue("/static/");
    }
}
