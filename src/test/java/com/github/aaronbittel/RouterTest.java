package com.github.aaronbittel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;

public class RouterTest {

    private HttpResponseRecorder recorder;

    @BeforeEach
    void setUp() {
        recorder = new HttpResponseRecorder();
    }

    @Nested
    class ExactRouteTests {

        private Router router;
        private TestUtils.RecordingHandler getHandler;
        private static final String PATH = "/users";

        @BeforeEach
        void setUp() {
            getHandler = new TestUtils.RecordingHandler();
            router = new Router()
                    .add(HttpMethod.GET, PATH, getHandler);
        }

        @Test
        void dispatches_when_method_and_path_match() throws IOException {
            router.handleRequest(recorder, TestUtils.request(HttpMethod.GET, PATH));
            TestUtils.assertWasCalled(getHandler);
        }

        @Test
        void returns_not_found_for_unmatched_path() throws IOException {
            router.handleRequest(recorder, TestUtils.request(HttpMethod.GET, "/path-not-registered"));
            TestUtils.assertStatus(recorder, HttpStatus.NOT_FOUND);
            TestUtils.assertWasNotCalled(getHandler);
        }

        @Test
        void returns_method_not_allowed_when_method_does_not_match() throws IOException {
            router.handleRequest(recorder, TestUtils.request(HttpMethod.POST, PATH));
            TestUtils.assertStatus(recorder, HttpStatus.METHOD_NOT_ALLOWED);
            assertThat(recorder.response().headers().get("Allow")).hasValue("GET, HEAD");
        }

        @Test
        void dispatches_to_handler_for_matching_method() throws IOException {
            var postHandler = new TestUtils.RecordingHandler();
            router.add(HttpMethod.POST, PATH, postHandler);

            router.handleRequest(recorder, TestUtils.request(HttpMethod.GET, PATH));
            TestUtils.assertWasCalled(getHandler);
            TestUtils.assertWasNotCalled(postHandler);

            getHandler.reset();
            postHandler.reset();

            router.handleRequest(recorder, TestUtils.request(HttpMethod.POST, PATH));
            TestUtils.assertWasNotCalled(getHandler);
            TestUtils.assertWasCalled(postHandler);
        }

        @Test
        void matches_path_with_query_string() throws IOException {
            router.handleRequest(recorder, TestUtils.request(HttpMethod.GET, PATH + "?query=value"));
            TestUtils.assertWasCalled(getHandler);
            assertThat(getHandler.received().requestUrl().query()).isEqualTo("query=value");
            assertThat(getHandler.received().requestUrl().hasQuery()).isTrue();
        }

        @Test
        void does_not_match_path_with_trailing_slash() throws IOException {
            router.handleRequest(recorder, TestUtils.request(HttpMethod.GET, PATH + "/"));
            TestUtils.assertStatus(recorder, HttpStatus.NOT_FOUND);
            TestUtils.assertWasNotCalled(getHandler);
        }

        @Test
        void does_not_match_nested_path() throws IOException {
            router.handleRequest(recorder, TestUtils.request(HttpMethod.GET, PATH + "/docs"));
            TestUtils.assertStatus(recorder, HttpStatus.NOT_FOUND);
            TestUtils.assertWasNotCalled(getHandler);
        }

        @Test
        void head_falls_back_to_get() throws IOException {
            router.handleRequest(recorder, TestUtils.request(HttpMethod.HEAD, PATH));
            TestUtils.assertWasCalled(getHandler);
        }

        @Test
        void prefers_explicit_head_handler_over_get() throws IOException {
            var headHandler = new TestUtils.RecordingHandler();

            router.add(HttpMethod.HEAD, PATH, headHandler);

            router.handleRequest(recorder, TestUtils.request(HttpMethod.HEAD, PATH));
            TestUtils.assertWasCalled(headHandler);
            TestUtils.assertWasNotCalled(getHandler);
        }
    }

    @Nested
    class SubtreeRouteTests {

        private Router router;
        private TestUtils.RecordingHandler getHandler;
        private static final String EXACT_PATH = "/users";
        private static final String SUBTREE_PATH = "/users/";

        @BeforeEach
        void setUp() {
            getHandler = new TestUtils.RecordingHandler();
            router = new Router()
                    .add(HttpMethod.GET, SUBTREE_PATH, getHandler);
        }

        @Test
        void redirects_base_path_to_trailing_slash() throws IOException {
            router.handleRequest(recorder, TestUtils.request(HttpMethod.GET, EXACT_PATH));
            TestUtils.assertStatus(recorder, HttpStatus.TEMPORARY_REDIRECT);
            assertThat(recorder.response().headers().get("Location")).hasValue(SUBTREE_PATH);
        }

        @Test
        void matches_base_path_with_trailing_slash() throws IOException {
            router.handleRequest(recorder, TestUtils.request(HttpMethod.GET, SUBTREE_PATH));
            TestUtils.assertWasCalled(getHandler);
        }

        @ParameterizedTest
        @ValueSource(strings = {"docs", "docs/directory"})
        void matches_nested_paths(String suffix) throws IOException {
            var path = SUBTREE_PATH + suffix;
            router.handleRequest(recorder, TestUtils.request(HttpMethod.GET, path));
            TestUtils.assertWasCalled(getHandler);
            assertThat(getHandler.received().requestUrl().path()).isEqualTo(path);
        }

        @Test
        void does_not_match_similar_prefix() throws IOException {
            router.handleRequest(recorder, TestUtils.request(HttpMethod.GET, "/users-extra"));
            TestUtils.assertWasNotCalled(getHandler);
            TestUtils.assertStatus(recorder, HttpStatus.NOT_FOUND);
        }

        @Test
        void redirects_base_path_even_when_method_is_not_allowed() throws IOException {
            router.handleRequest(recorder, TestUtils.request(HttpMethod.POST, EXACT_PATH));
            TestUtils.assertStatus(recorder, HttpStatus.TEMPORARY_REDIRECT);
            assertThat(recorder.response().headers().get("Location")).hasValue(SUBTREE_PATH);
        }

        @Test
        void preserves_query_string_when_matching_nested_path() throws IOException {
            router.handleRequest(recorder, TestUtils.request(HttpMethod.GET, SUBTREE_PATH + "docs?query=value"));

            TestUtils.assertWasCalled(getHandler);
            assertThat(getHandler.received().requestUrl().query())
                    .isEqualTo("query=value");
        }
    }

    @Nested
    class ExactAndSubtreeRouteTests {

        private static final String EXACT_PATH = "/users";
        private static final String SUBTREE_PATH = "/users/";

        private Router router;
        private TestUtils.RecordingHandler exactHandler;
        private TestUtils.RecordingHandler subtreeHandler;

        @BeforeEach
        void setUp() {
            exactHandler = new TestUtils.RecordingHandler();
            subtreeHandler = new TestUtils.RecordingHandler();

            router = new Router()
                    .add(HttpMethod.GET, EXACT_PATH, exactHandler)
                    .add(HttpMethod.GET, SUBTREE_PATH, subtreeHandler);
        }

        @Test
        void prefers_exact_route() throws IOException {
            router.handleRequest(recorder, TestUtils.request(HttpMethod.GET, EXACT_PATH));
            TestUtils.assertWasCalled(exactHandler);
            TestUtils.assertWasNotCalled(subtreeHandler);
        }

        @Test
        void matches_subtree_with_trailing_slash() throws IOException {
            router.handleRequest(recorder, TestUtils.request(HttpMethod.GET, SUBTREE_PATH));
            TestUtils.assertWasCalled(subtreeHandler);
            TestUtils.assertWasNotCalled(exactHandler);
        }

        @Test
        void matches_nested_path_with_exact_route_registered() throws IOException {
            router.handleRequest(recorder, TestUtils.request(HttpMethod.GET, SUBTREE_PATH + "docs"));

            TestUtils.assertWasCalled(subtreeHandler);
            TestUtils.assertWasNotCalled(exactHandler);
        }
    }

    @Nested
    class MountRouteTests {

        private static final String MOUNT_PREFIX = "/users";
        private static final String MOUNT_ROOT_PATH = "/users/";

        private Router router;
        private TestUtils.RecordingHandler getHandler;

        @BeforeEach
        void setUp() {
            getHandler = new TestUtils.RecordingHandler();

            router = new Router()
                    .mount(MOUNT_PREFIX, new Router().add(HttpMethod.GET, "/", getHandler));
        }

        @Test
        void strips_mount_prefix_before_dispatch() throws IOException {
            var request = TestUtils.request(HttpMethod.GET, MOUNT_ROOT_PATH);

            router.handleRequest(recorder, request);
            TestUtils.assertWasCalled(getHandler);
            assertThat(getHandler.received().path()).isEqualTo("/");
        }

        @Test
        void prefers_longest_mount_prefix() throws IOException {
            var nonMatchingHandler = new TestUtils.RecordingHandler();
            var matchingHandler = new TestUtils.RecordingHandler();

            var router = new Router()
                    .mount("/users", new Router().add(HttpMethod.GET, "/", nonMatchingHandler))
                    .mount("/users/docs", new Router().add(HttpMethod.GET, "/guide/", matchingHandler));

            router.handleRequest(recorder, TestUtils.request(HttpMethod.GET, "/users/docs/guide/"));

            TestUtils.assertWasCalled(matchingHandler);
            TestUtils.assertWasNotCalled(nonMatchingHandler);
        }

        @Test
        void redirects_mount_prefix_to_trailing_slash() throws IOException {
            router.handleRequest(recorder, TestUtils.request(HttpMethod.GET, MOUNT_PREFIX));
            TestUtils.assertStatus(recorder, HttpStatus.TEMPORARY_REDIRECT);
            assertThat(recorder.response().headers().get("Location")).hasValue(MOUNT_ROOT_PATH);
        }

        @Test
        void preserves_query_string_when_stripping_prefix() throws IOException {
            router.handleRequest(recorder, TestUtils.request(HttpMethod.GET, MOUNT_PREFIX + "/docs?query=value"));
            TestUtils.assertWasCalled(getHandler);
            assertThat(getHandler.received().requestUrl().path()).isEqualTo("/docs");
            assertThat(getHandler.received().requestUrl().query()).isEqualTo("query=value");
        }
    }

    @Test
    void redirects_to_more_specific_subtree() throws IOException {
        var router = new Router()
                .add(HttpMethod.GET, "/", TestUtils.NO_OP_HANDLER)
                .add(HttpMethod.GET, "/users/", TestUtils.NO_OP_HANDLER);

        router.handleRequest(recorder, TestUtils.request(HttpMethod.GET, "/users"));
        TestUtils.assertStatus(recorder, HttpStatus.TEMPORARY_REDIRECT);
        assertThat(recorder.response().headers().get("Location"))
                .hasValue("/users/");
    }

    @Test
    void longest_subtree_prefix_takes_precedence_over_mount() throws IOException {
        var nonMatchingHandler = new TestUtils.RecordingHandler();
        var matchingHandler = new TestUtils.RecordingHandler();

        var router = new Router()
                .mount("/users", new Router().add(HttpMethod.GET, "/", nonMatchingHandler))
                .add(HttpMethod.GET, "/users/docs/", matchingHandler);

        router.handleRequest(recorder, TestUtils.request(HttpMethod.GET, "/users/docs/"));
        TestUtils.assertWasCalled(matchingHandler);
        TestUtils.assertWasNotCalled(nonMatchingHandler);
    }

    @Test
    void longest_mount_prefix_takes_precedence_over_subtree() throws IOException {
        var nonMatchingHandler = new TestUtils.RecordingHandler();
        var matchingHandler = new TestUtils.RecordingHandler();

        var router = new Router()
                .mount("/users/docs", new Router().add(HttpMethod.GET, "/", matchingHandler))
                .add(HttpMethod.GET, "/users/", nonMatchingHandler);

        router.handleRequest(recorder, TestUtils.request(HttpMethod.GET, "/users/docs/"));
        TestUtils.assertWasCalled(matchingHandler);
        TestUtils.assertWasNotCalled(nonMatchingHandler);
    }

    @Nested
    class RouteRegistrationTests {
        @Test
        void rejects_path_without_leading_slash() {
            assertThatIllegalArgumentException().isThrownBy(() ->
                    new Router().add(HttpMethod.GET, "route-without-slash", TestUtils.NO_OP_HANDLER)
            );
        }

        @Test
        void rejects_duplicate_route_registration() {
            assertThatIllegalArgumentException().isThrownBy(() -> new Router()
                    .add(HttpMethod.GET, "/hello-world", TestUtils.NO_OP_HANDLER)
                    .add(HttpMethod.GET, "/hello-world", TestUtils.NO_OP_HANDLER));
        }

        @Test
        void rejects_mount_prefix_without_leading_slash() {
            assertThatIllegalArgumentException().isThrownBy(() -> new Router().mount("path", new Router()));
        }

        @Test
        void rejects_duplicate_mount_prefix_after_normalization() {
            assertThatIllegalArgumentException().isThrownBy(() ->
                    new Router()
                            .mount("/path", new Router())
                            .mount("/path/", new Router()));
        }

        @Test
        void rejects_conflicting_mount_and_subtree_prefixes() {
            assertThatIllegalArgumentException().isThrownBy(() ->
                    new Router()
                            .add(HttpMethod.GET, "/path/", TestUtils.NO_OP_HANDLER)
                            .mount("/path/", new Router()));

            assertThatIllegalArgumentException().isThrownBy(() ->
                    new Router()
                            .mount("/path/", new Router())
                            .add(HttpMethod.GET, "/path/", TestUtils.NO_OP_HANDLER));
        }

        @Test
        void allows_different_methods_for_same_path() {
            assertThatNoException().isThrownBy(() -> new Router()
                    .add(HttpMethod.GET, "/hello-world", TestUtils.NO_OP_HANDLER)
                    .add(HttpMethod.POST, "/hello-world", TestUtils.NO_OP_HANDLER)
            );
        }
    }
}
