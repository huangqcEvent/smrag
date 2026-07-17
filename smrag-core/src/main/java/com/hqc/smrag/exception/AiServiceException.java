package com.hqc.smrag.exception;

/**
 * AI 服务异常
 * <p>
 * 当调用 AI 服务（如大语言模型、向量数据库等）失败时抛出此异常。
 * <p>
 * 常见场景：
 * <ul>
 *     <li>大语言模型 API 调用超时或失败</li>
 *     <li>向量数据库连接失败</li>
 *     <li>模型返回空响应</li>
 * </ul>
 * <p>
 * 该异常会被 {@link com.hqc.smrag.common.GlobalExceptionHandler} 捕获，
 * 返回 502 Bad Gateway 状态码。
 *
 * @see com.hqc.smrag.common.GlobalExceptionHandler#handleAiService(AiServiceException) 异常处理器
 */
public class AiServiceException extends RuntimeException {

    /**
     * 构造函数
     *
     * @param message 错误消息
     * @param cause   原始异常
     */
    public AiServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
