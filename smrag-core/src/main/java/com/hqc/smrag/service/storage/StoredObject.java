package com.hqc.smrag.service.storage;

import java.io.IOException;
import java.io.InputStream;

public record StoredObject(
    InputStream stream,
    long totalSize,
    long contentLength,
    String contentType
) implements AutoCloseable {

    @Override
    public void close() throws IOException {
        stream.close();
    }
}
