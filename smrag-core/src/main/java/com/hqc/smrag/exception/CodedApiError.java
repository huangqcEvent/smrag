package com.hqc.smrag.exception;

import java.util.Map;

public interface CodedApiError {

    String errorCode();

    default Map<String, Object> details() {
        return Map.of();
    }
}
