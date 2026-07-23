package com.hqc.smrag.exception;

import org.springframework.security.access.AccessDeniedException;

import java.util.Map;

public class DomainForbiddenException extends AccessDeniedException
    implements CodedApiError {

    private final String errorCode;
    private final Map<String, Object> details;

    public DomainForbiddenException(String errorCode, String message) {
        this(errorCode, message, Map.of());
    }

    public DomainForbiddenException(
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
