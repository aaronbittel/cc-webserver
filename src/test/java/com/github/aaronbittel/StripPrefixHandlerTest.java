package com.github.aaronbittel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class StripPrefixHandlerTest {

    HttpResponseRecorder recorder;
    TestUtils.RecordingHandler recordingHandler;

    static Stream<String> prefixes() {
        return Stream.of("/prefix", "/prefix/");
    }

    @BeforeEach
    void setUp() {
        recorder = new HttpResponseRecorder();
        recordingHandler = new TestUtils.RecordingHandler();
    }

    @ParameterizedTest
    @MethodSource("prefixes")
    void test_strip_prefix_returns_404_if_not_matching(String prefix) throws IOException {
        HttpHandler next = (writer, _) -> writer.write(new HttpResponse(HttpVersion.HTTP_1_1, HttpStatus.OK));
        var handler = new StripPrefixHandler(next, prefix);

        handler.handleRequest(recorder, TestUtils.request(HttpMethod.GET, "/"));
        TestUtils.assertStatus(recorder, HttpStatus.NOT_FOUND);
    }

    @ParameterizedTest
    @MethodSource("prefixes")
    void matching_prefix_is_stripped_from_url(String prefix) throws IOException {
        var req = TestUtils.request(HttpMethod.GET, "/prefix/path");

        var handler = new StripPrefixHandler(recordingHandler, prefix);

        handler.handleRequest(TestUtils.NO_OP_RESPONSE_WRITER, req);
        assertThat(recordingHandler.wasCalled()).isTrue();
        assertThat(recordingHandler.received().path()).isEqualTo("/path");
    }

    @Test
    void only_matches_complete_path_segments() throws IOException {
        HttpHandler next = (writer, _) -> writer.write(new HttpResponse(HttpVersion.HTTP_1_1, HttpStatus.OK));
        var handler = new StripPrefixHandler(next, "/prefix");

        handler.handleRequest(recorder, TestUtils.request(HttpMethod.GET, "/prefix-not"));
        TestUtils.assertStatus(recorder, HttpStatus.NOT_FOUND);
    }

    @Test
    void nested_prefix_handlers_strip_the_current_url() throws IOException {
        var handler = new StripPrefixHandler(new StripPrefixHandler(recordingHandler, "/v1"), "/api");
        handler.handleRequest(TestUtils.NO_OP_RESPONSE_WRITER, TestUtils.request(HttpMethod.GET, "/api/v1/users"));

        assertThat(recordingHandler.wasCalled()).isTrue();
        assertThat(recordingHandler.received().path()).isEqualTo("/users");
    }

    @ParameterizedTest
    @MethodSource("prefixes")
    void path_matching_prefix_exactly_should_result_in_slash(String prefix) throws IOException {
        var handler = new StripPrefixHandler(recordingHandler, prefix);
        handler.handleRequest(TestUtils.NO_OP_RESPONSE_WRITER, TestUtils.request(HttpMethod.GET, prefix));

        assertThat(recordingHandler.wasCalled()).isTrue();
        assertThat(recordingHandler.received().path()).isEqualTo("/");
    }

    @ParameterizedTest
    @MethodSource("prefixes")
    void preserves_query_when_stripping_prefix(String prefix) throws IOException {
        var request = TestUtils.request(HttpMethod.GET, "/prefix/users?active=true");

        var handler = new StripPrefixHandler(recordingHandler, prefix);

        handler.handleRequest(TestUtils.NO_OP_RESPONSE_WRITER, request);

        assertThat(recordingHandler.wasCalled()).isTrue();
        assertThat(recordingHandler.received().path()).isEqualTo("/users");
        assertThat(recordingHandler.received().requestUrl().query()).isEqualTo("active=true");
        assertThat(recordingHandler.received().requestUrl().hasQuery()).isTrue();
    }

    @Test
    void preserves_empty_query_when_stripping_prefix() throws IOException {
        var request = TestUtils.request(HttpMethod.GET, "/prefix/users?");
        var handler = new StripPrefixHandler(recordingHandler, "/prefix");

        handler.handleRequest(TestUtils.NO_OP_RESPONSE_WRITER, request);

        assertThat(recordingHandler.wasCalled()).isTrue();
        assertThat(recordingHandler.received().path()).isEqualTo("/users");
        assertThat(recordingHandler.received().requestUrl().query()).isEmpty();
        assertThat(recordingHandler.received().requestUrl().hasQuery()).isTrue();
    }
}