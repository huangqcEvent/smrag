package com.hqc.smrag.service.storage;

import java.io.InputStream;
import java.nio.file.Path;

public interface DocumentStorage {

    void put(String objectKey, InputStream stream, long size, String contentType);

    StoredObject get(String objectKey, Long offset, Long length);

    Path materialize(String objectKey, String suffix);

    void delete(String objectKey);
}
