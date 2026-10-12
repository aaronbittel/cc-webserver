package com.github.aaronbittel;

import java.io.IOException;

public interface ResponseWriter {

    void write(HttpResponse response) throws IOException;
}
