package com.hqc.smrag.common;

import com.hqc.smrag.exception.AiServiceException;
import com.hqc.smrag.exception.CodedApiError;
import com.hqc.smrag.exception.DomainNotFoundException;
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
import org.springframework.security.access.AccessDeniedException;

/**
 * \u5168\u5c40\u5f02\u5e38\u5904\u7406\u5668
 * <p>
 * \u7edf\u4e00\u5904\u7406 Controller \u5c42\u629b\u51fa\u7684\u5f02\u5e38\uff0c\u8f6c\u6362\u4e3a\u6807\u51c6\u7684 {@link Result} \u54cd\u5e94\u683c\u5f0f\u3002
 * <p>
 * \u5904\u7406\u7684\u5f02\u5e38\u7c7b\u578b\uff1a
 * <ul>
 *     <li>\u53c2\u6570\u6821\u9a8c\u5f02\u5e38\uff1a\u8fd4\u56de 400 Bad Request</li>
 *     <li>AI \u670d\u52a1\u5f02\u5e38\uff1a\u8fd4\u56de 502 Bad Gateway</li>
 *     <li>\u8bf7\u6c42\u65b9\u6cd5\u4e0d\u652f\u6301\uff1a\u8fd4\u56de 405 Method Not Allowed</li>
 *     <li>\u5176\u4ed6\u672a\u77e5\u5f02\u5e38\uff1a\u8fd4\u56de 500 Internal Server Error</li>
 * </ul>
 *
 * @see Result \u7edf\u4e00\u54cd\u5e94\u7ed3\u679c
 * @see AiServiceException AI \u670d\u52a1\u5f02\u5e38
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(DomainNotFoundException.class)
    public Result<Void> handleNotFound(DomainNotFoundException exception) {
        log.warn("Requested resource was not found: {}", exception.getMessage());
        return codedError(404, exception);
    }

    /**
     * \u5904\u7406\u53c2\u6570\u6821\u9a8c\u5f02\u5e38
     * <p>
     * \u5305\u62ec\uff1a
     * <ul>
     *     <li>{@link MethodArgumentNotValidException}\uff1a@Valid \u6821\u9a8c\u5931\u8d25</li>
     *     <li>{@link BindException}\uff1a\u8868\u5355\u7ed1\u5b9a\u5931\u8d25</li>
     *     <li>{@link ConstraintViolationException}\uff1a\u7ea6\u675f\u6821\u9a8c\u5931\u8d25</li>
     *     <li>{@link HttpMessageNotReadableException}\uff1a\u8bf7\u6c42\u4f53\u89e3\u6790\u5931\u8d25</li>
     *     <li>{@link MultipartException}\uff1a\u6587\u4ef6\u4e0a\u4f20\u5f02\u5e38</li>
     *     <li>{@link MissingServletRequestPartException}\uff1a\u7f3a\u5c11\u5fc5\u9700\u7684\u8bf7\u6c42\u90e8\u5206</li>
     *     <li>{@link IllegalArgumentException}\uff1a\u53c2\u6570\u975e\u6cd5</li>
     * </ul>
     *
     * @param exception \u5f02\u5e38
     * @return \u9519\u8bef\u54cd\u5e94
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        BindException.class,
        ConstraintViolationException.class,
        HttpMessageNotReadableException.class,
        MultipartException.class,
        MissingServletRequestPartException.class
    })
    public Result<Void> handleValidation(Exception exception) {
        log.warn("Request validation failed: {}", exception.getClass().getSimpleName());
        return Result.error(400, "\u8bf7\u6c42\u53c2\u6570\u4e0d\u5408\u6cd5");
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(IllegalArgumentException.class)
    public Result<Void> handleIllegalArgument(IllegalArgumentException exception) {
        log.warn("Request argument rejected: {}", exception.getMessage());
        if (exception instanceof CodedApiError coded) {
            return codedError(400, coded);
        }
        return Result.error(400, messageOrDefault(exception, "请求参数不合法"));
    }

    @ResponseStatus(HttpStatus.FORBIDDEN)
    @ExceptionHandler(AccessDeniedException.class)
    public Result<Void> handleAccessDenied(AccessDeniedException exception) {
        log.warn("Request access denied: {}", exception.getMessage());
        if (exception instanceof CodedApiError coded) {
            return codedError(403, coded);
        }
        return Result.error(403, messageOrDefault(exception, "没有权限执行该操作"));
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler(IllegalStateException.class)
    public Result<Void> handleConflict(IllegalStateException exception) {
        log.warn("Request conflicts with current state: {}", exception.getMessage());
        if (exception instanceof CodedApiError coded) {
            return codedError(409, coded);
        }
        return Result.error(409, messageOrDefault(exception, "资源状态已发生变化"));
    }

    /**
     * \u5904\u7406 AI \u670d\u52a1\u5f02\u5e38
     * <p>
     * \u5f53\u8c03\u7528\u5927\u8bed\u8a00\u6a21\u578b\u3001\u5411\u91cf\u6570\u636e\u5e93\u7b49 AI \u670d\u52a1\u5931\u8d25\u65f6\uff0c\u8fd4\u56de 502 \u72b6\u6001\u7801\u3002
     *
     * @param exception AI \u670d\u52a1\u5f02\u5e38
     * @return \u9519\u8bef\u54cd\u5e94
     */
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    @ExceptionHandler(AiServiceException.class)
    public Result<Void> handleAiService(AiServiceException exception) {
        log.error("AI service request failed", exception);
        return Result.error(502, "AI \u670d\u52a1\u6682\u65f6\u4e0d\u53ef\u7528\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5");
    }

    /**
     * \u5904\u7406\u8bf7\u6c42\u65b9\u6cd5\u4e0d\u652f\u6301\u5f02\u5e38
     *
     * @param exception \u5f02\u5e38
     * @return \u9519\u8bef\u54cd\u5e94
     */
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<Void> handleMethodNotAllowed(HttpRequestMethodNotSupportedException exception) {
        log.warn("Request method is not supported: {}", exception.getMethod());
        return Result.error(405, "\u8bf7\u6c42\u65b9\u6cd5\u4e0d\u652f\u6301");
    }

    /**
     * \u5904\u7406\u5176\u4ed6\u672a\u77e5\u5f02\u5e38
     * <p>
     * \u4f5c\u4e3a\u515c\u5e95\u5904\u7406\u5668\uff0c\u6355\u83b7\u6240\u6709\u672a\u88ab\u5176\u4ed6\u5904\u7406\u5668\u5904\u7406\u7684\u5f02\u5e38\u3002
     *
     * @param exception \u5f02\u5e38
     * @return \u9519\u8bef\u54cd\u5e94
     */
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Exception.class)
    public Result<Void> handleUnexpected(Exception exception) {
        log.error("Unexpected server error", exception);
        return Result.error(500, "\u7cfb\u7edf\u7e41\u5fd9\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5");
    }

    private String messageOrDefault(Exception exception, String fallback) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
            ? fallback
            : exception.getMessage();
    }

    private Result<Void> codedError(int status, CodedApiError error) {
        Exception exception = (Exception) error;
        return Result.error(
            status,
            error.errorCode(),
            messageOrDefault(exception, "请求处理失败"),
            error.details()
        );
    }
}
