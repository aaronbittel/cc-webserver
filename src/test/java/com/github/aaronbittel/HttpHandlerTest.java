package com.github.aaronbittel;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

public class HttpHandlerTest {

    @Test
    void handler_can_read_query_parameters() throws IOException {
        HttpHandler queryHandler = (writer, request) -> {
            var params = request.requestUrl().parseQuery();
            var value = params.get("q").getFirst();
            writer.write(value);
        };

        var recorder = new HttpResponseRecorder();
        queryHandler.handleRequest(recorder, TestUtils.request(HttpMethod.GET, "/search?q=hello%20world"));

        assertThat(new String(recorder.result().body())).isEqualTo("hello world");
    }
}
