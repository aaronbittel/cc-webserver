package com.github.aaronbittel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class RouterTest {

    private static final HttpResponse OK_RESPONSE = new HttpResponse(HttpVersion.HTTP_1_1, HttpStatus.OK);
    private static final HttpResponse NOT_FOUND_RESPONSE = new HttpResponse(HttpVersion.HTTP_1_1, HttpStatus.NOT_FOUND);

    private static final HttpHandler NO_OP_HANDLER = (_, _) -> {};
    private static final HttpHandler OK_RESPONSE_WRITER = (writer, _) -> writer.write(OK_RESPONSE);
    private static final HttpHandler NOT_FOUND_RESPONSE_WRITER = (writer, _) -> writer.write(NOT_FOUND_RESPONSE);
    private HttpResponseRecorder recorder;

    @BeforeEach
    void setUp() {
        recorder = new HttpResponseRecorder();
    }

    @Test
    void dispatches_to_handler_when_method_and_path_match() throws IOException {
        var path = "/hello-world";
        var response = new HttpResponse(
                HttpVersion.HTTP_1_1,
                HttpStatus.OK,
                TestUtils.buildHeaders("Key", "Value"),
                "content".getBytes(StandardCharsets.UTF_8)
        );
        var router = new Router()
                .add(HttpMethod.GET, path, (writer, _) -> writer.write(response));

        router.handleRequest(recorder, TestUtils.request(HttpMethod.GET, path));

        assertThat(recorder.response()).isEqualTo(response);
    }

    @Test
    void returns_not_found_when_path_is_not_registered() throws IOException {
        var router = new Router();
        var request = TestUtils.request(HttpMethod.GET, "/path-not-registered");
        router.handleRequest(recorder, request);

        var expected = new HttpResponse(HttpVersion.HTTP_1_1, HttpStatus.NOT_FOUND);
        assertThat(recorder.response()).isEqualTo(expected);
    }

    @Test
    void returns_method_not_allowed_when_method_is_not_registered_for_path() throws IOException {
        var path = "/path";

        var router = new Router()
                .add(HttpMethod.GET, path, NO_OP_HANDLER);

        var request = TestUtils.request(HttpMethod.POST, path);
        router.handleRequest(recorder, request);

        var expected = new HttpResponse(HttpVersion.HTTP_1_1, HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(recorder.response()).isEqualTo(expected);
    }

    @Test
    void routes_requests_to_the_handler_matching_the_method() throws IOException {
        var getResponse = OK_RESPONSE;
        var postResponse = new HttpResponse(HttpVersion.HTTP_1_1, HttpStatus.CREATED);

        var path = "/path";
        var router = new Router()
                .add(HttpMethod.GET, path, (writer, _) -> writer.write(getResponse))
                .add(HttpMethod.POST, path, (writer, _) -> writer.write(postResponse));

        router.handleRequest(recorder, TestUtils.request(HttpMethod.GET, path));
        assertThat(recorder.response()).isEqualTo(getResponse);

        router.handleRequest(recorder, TestUtils.request(HttpMethod.POST, path));
        assertThat(recorder.response()).isEqualTo(postResponse);
    }

    @Test
    void routes_requests_to_the_handler_matching_the_path() throws IOException {
        var firstResponse = OK_RESPONSE;
        var secondResponse = new HttpResponse(HttpVersion.HTTP_1_1, HttpStatus.CREATED);

        var router = new Router()
                .add(HttpMethod.GET, "/first", (writer, _) -> writer.write(firstResponse))
                .add(HttpMethod.GET, "/second", (writer, _) -> writer.write(secondResponse));

        router.handleRequest(recorder, TestUtils.request(HttpMethod.GET, "/first"));
        assertThat(recorder.response()).isEqualTo(firstResponse);

        router.handleRequest(recorder, TestUtils.request(HttpMethod.GET, "/second"));
        assertThat(recorder.response()).isEqualTo(secondResponse);
    }

    @Test
    void rejects_route_path_without_leading_slash() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new Router().add(HttpMethod.GET, "route-with-slash", NO_OP_HANDLER)
        );
    }

    @Test
    void path_with_query_string_is_dispatched_to_the_handler() throws IOException {
        var path = "/route";
        var router = new Router()
                .add(HttpMethod.GET, path, OK_RESPONSE_WRITER);

        router.handleRequest(recorder, TestUtils.request(HttpMethod.GET, path + "?query=value"));
        assertThat(recorder.response()).isEqualTo(OK_RESPONSE);
    }

    @Test
    void rejects_duplicate_route_registration() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Router()
                .add(HttpMethod.GET, "/hello-world", NO_OP_HANDLER)
                .add(HttpMethod.GET, "/hello-world", NO_OP_HANDLER));
    }

    @Test
    void route_without_trailing_slash_only_matches_exactly() throws IOException {
        var router = new Router()
                .add(HttpMethod.GET, "/static", OK_RESPONSE_WRITER);

        router.handleRequest(recorder, TestUtils.request(HttpMethod.GET, "/static"));
        assertThat(recorder.response()).isEqualTo(OK_RESPONSE);
    }

    @Test
    void route_does_not_exact_match_with_trailing_slash() throws IOException {
        var router = new Router()
                .add(HttpMethod.GET, "/static", OK_RESPONSE_WRITER);

        router.handleRequest(recorder, TestUtils.request(HttpMethod.GET, "/static/"));
        assertThat(recorder.response()).isEqualTo(NOT_FOUND_RESPONSE);
    }

    @Test
    void prefix_route_does_not_match_path_without_trailing_slash() throws IOException {
        var router = new Router()
                .add(HttpMethod.GET, "/static/", OK_RESPONSE_WRITER);

        router.handleRequest(recorder, TestUtils.request(HttpMethod.GET, "/static"));
        assertThat(recorder.response()).isEqualTo(NOT_FOUND_RESPONSE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/static/docs", "/static/docs/directory"})
    void route_with_trailing_slash_matches_prefix(String path) throws IOException {
        var router = new Router()
                .add(HttpMethod.GET, "/static/", OK_RESPONSE_WRITER);

        router.handleRequest(recorder, TestUtils.request(HttpMethod.GET, path));
        assertThat(recorder.response()).isEqualTo(OK_RESPONSE);
    }

    @Test
    void exact_route_does_not_match_nested_path() throws IOException {
        var router = new Router()
                .add(HttpMethod.GET, "/static", OK_RESPONSE_WRITER);

        router.handleRequest(recorder, TestUtils.request(HttpMethod.GET, "/static/docs"));
        assertThat(recorder.response()).isEqualTo(NOT_FOUND_RESPONSE);
    }

    @Test
    void falls_back_to_get_route_when_head_route_is_not_defined() throws IOException {
        var router = new Router()
                .add(HttpMethod.GET, "/static", OK_RESPONSE_WRITER);

        router.handleRequest(recorder, TestUtils.request(HttpMethod.HEAD, "/static"));
        assertThat(recorder.response()).isEqualTo(OK_RESPONSE);
    }

    @Test
    void prefers_explicit_head_route_over_get_route() throws IOException {
        var router = new Router()
                .add(HttpMethod.GET, "/static", OK_RESPONSE_WRITER)
                .add(HttpMethod.HEAD, "/static", NOT_FOUND_RESPONSE_WRITER);

        router.handleRequest(recorder, TestUtils.request(HttpMethod.HEAD, "/static"));
        assertThat(recorder.response()).isEqualTo(NOT_FOUND_RESPONSE);
    }
}
