package com.hqc.smrag.exception;

import java.util.Map;

public class DomainConflictException extends IllegalStateException
    implements CodedApiError {

    private final String errorCode;
    private final Map<String, Object> details;

    public DomainConflictException(String errorCode, String message) {
        this(errorCode, message, Map.of());
    }

    public DomainConflictException(
        String errorCode,
        String message,
        Map<String, Object> details
    ) {
        super(message);
        this.errorCode = errorCode;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    @Override
    public String errorCode() {
        return errorCode;
    }

    @Override
    public Map<String, Object> details() {
        return details;
    }
}
