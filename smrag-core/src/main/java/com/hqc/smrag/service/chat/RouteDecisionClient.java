package com.hqc.smrag.service.chat;

import com.hqc.smrag.dto.RouteDecision;

/**
 * 路由决策客户端接口
 * <p>
 * 定义问题路由决策的能力，根据用户问题判断应该由哪个分支处理。
 *
 * @see SpringAiRouteDecisionClient Spring AI 实现类
 * @see RouteDecision 路由决策结果
 */
public interface RouteDecisionClient {

    /**
     * 对问题进行路由决策
     *
     * @param question 用户问题
     * @return 路由决策结果，包含路由类型和决策原因
     */
    RouteDecision decide(String question);
}
