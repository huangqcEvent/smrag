package com.hqc.smrag.common;

import lombok.Data;

/**
 * 统一响应结果
 * <p>
 * 封装 API 响应的统一格式，包括：
 * <ul>
 *     <li>code：状态码（200=成功，400=参数错误，500=服务器错误等）</li>
 *     <li>message：响应消息</li>
 *     <li>data：响应数据（泛型）</li>
 * </ul>
 * <p>
 * 使用示例：
 * <pre>{@code
 * // 成功响应
 * return Result.success(data);
 *
 * // 成功响应（无数据）
 * return Result.success();
 *
 * // 错误响应
 * return Result.error(400, "参数不合法");
 * }</pre>
 *
 * @param <T> 响应数据类型
 */
@Data
public class Result<T> {

    /**
     * 状态码
     * <p>
     * 200=成功，400=参数错误，500=服务器错误，502=AI服务不可用等。
     */
    private Integer code;

    /**
     * 响应消息
     * <p>
     * 成功时为 "success"，失败时为错误描述。
     */
    private String message;

    /**
     * 响应数据
     * <p>
     * 泛型类型，可以是任意 Java 对象。
     */
    private T data;

    /**
     * 成功响应（带数据）
     *
     * @param data 响应数据
     * @return 统一响应结果
     */
    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMessage("success");
        result.setData(data);
        return result;
    }

    /**
     * 成功响应（无数据）
     *
     * @return 统一响应结果
     */
    public static <T> Result<T> success() {
        return success(null);
    }

    /**
     * 错误响应（默认 500 状态码）
     *
     * @param message 错误消息
     * @return 统一响应结果
     */
    public static <T> Result<T> error(String message) {
        Result<T> result = new Result<>();
        result.setCode(500);
        result.setMessage(message);
        return result;
    }

    /**
     * 错误响应（自定义状态码）
     *
     * @param code    状态码
     * @param message 错误消息
     * @return 统一响应结果
     */
    public static <T> Result<T> error(Integer code, String message) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }
}