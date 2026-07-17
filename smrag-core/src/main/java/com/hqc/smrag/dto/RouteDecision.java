package com.hqc.smrag.dto;

import com.hqc.smrag.enums.RouteType;

/**
 * 路由决策 DTO
 * <p>
 * 封装路由决策的结果，包括：
 * <ul>
 *     <li>routeType：路由类型，决定问题由哪个分支处理</li>
 *     <li>reason：决策原因，解释为什么选择该路由</li>
 * </ul>
 * <p>
 * 由大语言模型根据用户问题的意图进行判断，以结构化格式返回。
 *
 * @see RouteType 路由类型枚举
 * @see RouteDecisionClient 路由决策客户端
 */
public record RouteDecision(RouteType routeType, String reason) {

    /**
     * 紧凑构造函数，提供默认值
     *
     * @param routeType 路由类型，如果为 null 则默认为 KNOWLEDGE_QA
     * @param reason    决策原因，如果为 null 则默认为空字符串
     */
    public RouteDecision {
        routeType = routeType == null ? RouteType.KNOWLEDGE_QA : routeType;
        reason = reason == null ? "" : reason;
    }
}
