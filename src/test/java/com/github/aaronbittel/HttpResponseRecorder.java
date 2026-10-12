package com.github.aaronbittel;

import java.io.IOException;

public class HttpResponseRecorder implements ResponseWriter {

    private HttpResponse response;

    @Override
    public void write(HttpResponse response) throws IOException {
        this.response = response;
    }

    public HttpResponse response() {
        return response;
    }
}
