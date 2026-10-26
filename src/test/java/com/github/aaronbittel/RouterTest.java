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
            assertThat(recorder.result().headers().get("Allow")).hasValue("GET, HEAD, OPTIONS");
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

        @Test
        void returns_allowed_methods_for_options_request() throws IOException {
            router.handleRequest(recorder, TestUtils.request(HttpMethod.OPTIONS, PATH));
            assertThat(recorder.result().status()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(recorder.result().headers().get("Allow")).hasValue("GET, HEAD, OPTIONS");
            assertThat(recorder.result().body()).isEmpty();
        }

        @Test
        void prefers_explicit_options_handler_over_default() throws IOException {
            var optionsHandler = new TestUtils.RecordingHandler();
            router.add(HttpMethod.OPTIONS, PATH, optionsHandler);

            router.handleRequest(recorder, TestUtils.request(HttpMethod.OPTIONS, PATH));
            TestUtils.assertWasCalled(optionsHandler);
        }

        @Test
        void returns_all_supported_methods_for_options_request() throws IOException {
            router.add(HttpMethod.POST, PATH, TestUtils.NO_OP_HANDLER);
            router.add(HttpMethod.HEAD, PATH, TestUtils.NO_OP_HANDLER);

            router.handleRequest(recorder, TestUtils.request(HttpMethod.OPTIONS, PATH));

            assertThat(recorder.result().status()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(recorder.result().headers().get("Allow")).hasValue("GET, HEAD, OPTIONS, POST");
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
            assertThat(recorder.result().headers().get("Location")).hasValue(SUBTREE_PATH);
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
            assertThat(recorder.result().headers().get("Location")).hasValue(SUBTREE_PATH);
        }

        @Test
        void preserves_query_string_when_matching_nested_path() throws IOException {
            router.handleRequest(recorder, TestUtils.request(HttpMethod.GET, SUBTREE_PATH + "docs?query=value"));

            TestUtils.assertWasCalled(getHandler);
            assertThat(getHandler.received().requestUrl().query()).isEqualTo("query=value");
        }

        @Test
        void returns_allowed_methods_for_options_request() throws IOException {
            router.handleRequest(recorder, TestUtils.request(HttpMethod.OPTIONS, SUBTREE_PATH));
            assertThat(recorder.result().status()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(recorder.result().headers().get("Allow")).hasValue("GET, HEAD, OPTIONS");
            assertThat(recorder.result().body()).isEmpty();
        }

        @Test
        void prefers_explicit_options_handler_over_default() throws IOException {
            var optionsHandler = new TestUtils.RecordingHandler();
            router.add(HttpMethod.OPTIONS, SUBTREE_PATH, optionsHandler);

            router.handleRequest(recorder, TestUtils.request(HttpMethod.OPTIONS, SUBTREE_PATH));
            TestUtils.assertWasCalled(optionsHandler);
        }

        @Test
        void preserves_query_string_when_redirecting_base_path() throws IOException {
            router.handleRequest(recorder, TestUtils.request(HttpMethod.GET, EXACT_PATH + "?key=value"));

            TestUtils.assertStatus(recorder, HttpStatus.TEMPORARY_REDIRECT);
            assertThat(recorder.result().headers().get("Location")).hasValue(SUBTREE_PATH + "?key=value");
            TestUtils.assertWasNotCalled(getHandler);
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
            assertThat(recorder.result().headers().get("Location")).hasValue(MOUNT_ROOT_PATH);
        }

        @Test
        void preserves_query_string_when_stripping_prefix() throws IOException {
            router.handleRequest(recorder, TestUtils.request(HttpMethod.GET, MOUNT_PREFIX + "/docs?query=value"));
            TestUtils.assertWasCalled(getHandler);
            assertThat(getHandler.received().requestUrl().path()).isEqualTo("/docs");
            assertThat(getHandler.received().requestUrl().query()).isEqualTo("query=value");
        }

        @Test
        void preserves_query_string_when_redirecting_mount_prefix() throws IOException {
            router.handleRequest(recorder, TestUtils.request(HttpMethod.GET, MOUNT_PREFIX + "?key=value"));

            TestUtils.assertStatus(recorder, HttpStatus.TEMPORARY_REDIRECT);
            assertThat(recorder.result().headers().get("Location")).hasValue(MOUNT_ROOT_PATH + "?key=value");
            TestUtils.assertWasNotCalled(getHandler);
        }
    }

    @Test
    void redirects_to_more_specific_subtree() throws IOException {
        var router = new Router()
                .add(HttpMethod.GET, "/", TestUtils.NO_OP_HANDLER)
                .add(HttpMethod.GET, "/users/", TestUtils.NO_OP_HANDLER);

        router.handleRequest(recorder, TestUtils.request(HttpMethod.GET, "/users"));
        TestUtils.assertStatus(recorder, HttpStatus.TEMPORARY_REDIRECT);
        assertThat(recorder.result().headers().get("Location"))
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
    class TerminalWildcardRouteTests {

        private Router router;
        private TestUtils.RecordingHandler rootHandler;
        private TestUtils.RecordingHandler usersHandler;
        private static final String ROOT_PATH = "/";
        private static final String USERS_PATH = "/users";

        @BeforeEach
        void setUp() {
            rootHandler = new TestUtils.RecordingHandler();
            usersHandler = new TestUtils.RecordingHandler();
            router = new Router()
                    .add(HttpMethod.GET, "/{$}", rootHandler)
                    .add(HttpMethod.GET, USERS_PATH + "/{$}", usersHandler);
        }

        @Test
        void matches_root_path() throws IOException {
            router.handleRequest(recorder, TestUtils.request(HttpMethod.GET, ROOT_PATH));
            TestUtils.assertWasCalled(rootHandler);
        }

        @Test
        void matches_base_path_without_trailing_slash() throws IOException {
            router.handleRequest(recorder, TestUtils.request(HttpMethod.GET, USERS_PATH));
            TestUtils.assertWasCalled(usersHandler);
        }

        @Test
        void does_not_match_trailing_slash() throws IOException {
            router.handleRequest(recorder, TestUtils.request(HttpMethod.GET, USERS_PATH + "/"));

            TestUtils.assertStatus(recorder, HttpStatus.NOT_FOUND);
            TestUtils.assertWasNotCalled(usersHandler);
        }

        @Test
        void does_not_match_nested_path() throws IOException {
            router.handleRequest(recorder, TestUtils.request(HttpMethod.GET, USERS_PATH + "/docs"));

            TestUtils.assertStatus(recorder, HttpStatus.NOT_FOUND);
            TestUtils.assertWasNotCalled(usersHandler);
        }

        @Test
        void prefers_exact_root_route_over_root_subtree_route() throws IOException {
            var subtreeHandler = new TestUtils.RecordingHandler();

            router.add(HttpMethod.GET, "/", subtreeHandler);

            router.handleRequest(recorder, TestUtils.request(HttpMethod.GET, ROOT_PATH));

            TestUtils.assertWasCalled(rootHandler);
            TestUtils.assertWasNotCalled(subtreeHandler);
        }
    }

    @Nested
    class ParameterRouteTests {

        private Router router;
        private TestUtils.RecordingHandler parameterHandler;
        private static final String PARAMETER_NAME = "{id}";
        private static final String BASE = "/users";

        @BeforeEach
        void setUp() {
            parameterHandler = new TestUtils.RecordingHandler();
            router = new Router()
                    .add(HttpMethod.GET, BASE + "/" + PARAMETER_NAME, parameterHandler);
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "123",
                "..",
                ".",
                "user-123",
        })
        void matches_single_path_segment(String parameter) throws IOException {
            router.handleRequest(recorder, TestUtils.request(HttpMethod.GET, BASE + "/" + parameter));
            TestUtils.assertWasCalled(parameterHandler);
            assertThat(parameterHandler.received().pathValue("id")).isEqualTo(parameter);
        }

        @Test
        void prefers_exact_route_over_parameter_route() throws IOException {
            var exactHandler = new TestUtils.RecordingHandler();
            router.add(HttpMethod.GET, BASE + "/profile", exactHandler);

            router.handleRequest(recorder, TestUtils.request(HttpMethod.GET, BASE + "/profile"));
            TestUtils.assertWasCalled(exactHandler);
            TestUtils.assertWasNotCalled(parameterHandler);
        }

        @Test
        void matches_parameter_route_when_no_exact_route_matches() throws IOException {
            var exactHandler = new TestUtils.RecordingHandler();
            router.add(HttpMethod.GET, BASE + "/profile", exactHandler);

            router.handleRequest(recorder, TestUtils.request(HttpMethod.GET, BASE + "/123"));
            TestUtils.assertWasCalled(parameterHandler);
            TestUtils.assertWasNotCalled(exactHandler);
            assertThat(parameterHandler.received().pathValue("id")).isEqualTo("123");
        }

        @Test
        void prefers_parameter_route_over_subtree_route() throws IOException {
            var subtreeHandler = new TestUtils.RecordingHandler();
            router.add(HttpMethod.GET, BASE + "/", subtreeHandler);

            router.handleRequest(recorder, TestUtils.request(HttpMethod.GET, BASE + "/1"));
            TestUtils.assertWasCalled(parameterHandler);
            TestUtils.assertWasNotCalled(subtreeHandler);
            assertThat(parameterHandler.received().pathValue("id")).isEqualTo("1");
        }

        @Test
        void prefers_subtree_route_without_parameter() throws IOException {
            var subtreeHandler = new TestUtils.RecordingHandler();
            router.add(HttpMethod.GET, BASE + "/", subtreeHandler);

            router.handleRequest(recorder, TestUtils.request(HttpMethod.GET, BASE + "/"));
            TestUtils.assertWasCalled(subtreeHandler);
            TestUtils.assertWasNotCalled(parameterHandler);
        }

        @Test
        void returns_allowed_methods_for_options_request() throws IOException {
            router.handleRequest(recorder, TestUtils.request(HttpMethod.OPTIONS, BASE + "/1"));
            assertThat(recorder.result().status()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(recorder.result().headers().get("Allow")).hasValue("GET, HEAD, OPTIONS");
            assertThat(recorder.result().body()).isEmpty();
        }

        @Test
        void prefers_explicit_options_handler_over_default() throws IOException {
            var optionsHandler = new TestUtils.RecordingHandler();
            router.add(HttpMethod.OPTIONS, BASE + "/1", optionsHandler);

            router.handleRequest(recorder, TestUtils.request(HttpMethod.OPTIONS, BASE + "/1"));
            TestUtils.assertWasCalled(optionsHandler);
        }
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
            var router = new Router()
                    .add(HttpMethod.GET, "/hello-world", TestUtils.NO_OP_HANDLER);
            assertThatIllegalArgumentException().isThrownBy(() -> router
                    .add(HttpMethod.GET, "/hello-world", TestUtils.NO_OP_HANDLER));
        }

        @Test
        void rejects_mount_prefix_without_leading_slash() {
            assertThatIllegalArgumentException().isThrownBy(() -> new Router().mount("path", new Router()));
        }

        @Test
        void rejects_duplicate_mount_prefix_after_normalization() {
            var router = new Router()
                    .mount("/path", new Router());
            assertThatIllegalArgumentException().isThrownBy(() -> router
                    .mount("/path/", new Router()));
        }

        @Test
        void rejects_conflicting_mount_and_subtree_prefixes() {
            var router1 = new Router()
                    .add(HttpMethod.GET, "/path/", TestUtils.NO_OP_HANDLER);
            assertThatIllegalArgumentException().isThrownBy(() -> router1
                    .mount("/path/", new Router()));

            var router2 = new Router()
                    .mount("/path/", new Router());
            assertThatIllegalArgumentException().isThrownBy(() -> router2
                    .add(HttpMethod.GET, "/path/", TestUtils.NO_OP_HANDLER));
        }

        @Test
        void allows_different_methods_for_same_path() {
            var router = new Router()
                    .add(HttpMethod.GET, "/hello-world", TestUtils.NO_OP_HANDLER);
            assertThatNoException().isThrownBy(() -> router
                    .add(HttpMethod.POST, "/hello-world", TestUtils.NO_OP_HANDLER)
            );
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "/path/{$}/path",
                "/path/{$}/path/{$}",
        })
        void rejects_terminal_wildcard_pattern_not_at_the_end(String path) {
            assertThatIllegalArgumentException().isThrownBy(() -> new Router()
                    .add(HttpMethod.GET, path, TestUtils.NO_OP_HANDLER));
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "/{$}",
                "/path/{$}",
        })
        void allows_terminal_wildcard_pattern(String path) {
            assertThatNoException().isThrownBy(() -> new Router()
                    .add(HttpMethod.GET, path, TestUtils.NO_OP_HANDLER));
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "/{$}",
                "/path/{$}",
                "/path/{$}/path",
                "/path/{$}/path/{$}",
        })
        void rejects_terminal_wildcard_pattern_in_mount_paths(String path) {
            assertThatIllegalArgumentException().isThrownBy(() -> new Router()
                    .mount(path, new Router()));
        }

        @Test
        void allows_exact_root_and_root_subtree_routes_to_coexist() {
            var router = new Router()
                    .add(HttpMethod.GET, "/", TestUtils.NO_OP_HANDLER);
            assertThatNoException().isThrownBy(() -> router
                    .add(HttpMethod.GET, "/{$}", TestUtils.NO_OP_HANDLER));
        }

        @Test
        void rejects_duplicate_parameter_routes() {
            var router = new Router()
                    .add(HttpMethod.GET, "/users/{id}", TestUtils.NO_OP_HANDLER);
            assertThatIllegalArgumentException().isThrownBy(() -> router
                    .add(HttpMethod.GET, "/users/{name}", TestUtils.NO_OP_HANDLER));
        }

        @Test
        void allows_duplicate_parameter_routes_with_different_methods() {
            var router1 = new Router()
                    .add(HttpMethod.GET, "/users/{id}", TestUtils.NO_OP_HANDLER);
            assertThatNoException().isThrownBy(() -> router1
                    .add(HttpMethod.POST, "/users/{name}", TestUtils.NO_OP_HANDLER));

            var router2 = new Router()
                    .add(HttpMethod.GET, "/users/{id}", TestUtils.NO_OP_HANDLER);
            assertThatNoException().isThrownBy(() -> router2
                    .add(HttpMethod.POST, "/users/{id}", TestUtils.NO_OP_HANDLER));
        }
    }
}
