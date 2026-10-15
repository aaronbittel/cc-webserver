package com.github.aaronbittel;

public class HttpResponseRecorder implements ResponseWriter {

    private HttpResponse response;

    @Override
    public void write(HttpResponse response) {
        this.response = response;
    }

    public HttpResponse response() {
        return response;
    }
}
