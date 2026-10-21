package com.github.aaronbittel;

import java.io.IOException;

public interface ResponseWriter {

    void setStatus(HttpStatus status);

    HttpHeaders headers();

    void write(String text) throws IOException;

    void write(byte[] data) throws IOException;
}
