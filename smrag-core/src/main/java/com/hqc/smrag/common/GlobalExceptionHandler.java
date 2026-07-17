package com.hqc.smrag.common;

import com.hqc.smrag.exception.AiServiceException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        BindException.class,
        ConstraintViolationException.class,
        HttpMessageNotReadableException.class,
        MultipartException.class,
        MissingServletRequestPartException.class,
        IllegalArgumentException.class
    })
    public Result<Void> handleValidation(Exception exception) {
        log.warn("Request validation failed: {}", exception.getClass().getSimpleName());
        return Result.error(400, "\u8bf7\u6c42\u53c2\u6570\u4e0d\u5408\u6cd5");
    }

    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    @ExceptionHandler(AiServiceException.class)
    public Result<Void> handleAiService(AiServiceException exception) {
        log.error("AI service request failed", exception);
        return Result.error(502, "AI \u670d\u52a1\u6682\u65f6\u4e0d\u53ef\u7528\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5");
    }

    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<Void> handleMethodNotAllowed(HttpRequestMethodNotSupportedException exception) {
        log.warn("Request method is not supported: {}", exception.getMethod());
        return Result.error(405, "\u8bf7\u6c42\u65b9\u6cd5\u4e0d\u652f\u6301");
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Exception.class)
    public Result<Void> handleUnexpected(Exception exception) {
        log.error("Unexpected server error", exception);
        return Result.error(500, "\u7cfb\u7edf\u7e41\u5fd9\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5");
    }
}
