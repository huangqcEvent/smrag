package com.hqc.smrag.service.chat;

import com.hqc.smrag.enums.RouteType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 路由决策服务
 * <p>
 * 根据用户问题的内容，智能判断应该由哪个分支处理。
 * 路由类型包括：
 * <ul>
 *     <li>KNOWLEDGE_QA：知识库问答</li>
 *     <li>PRODUCT_QUERY：产品查询</li>
 *     <li>PRODUCT_COMPARE：产品对比</li>
 *     <li>PRODUCT_RECOMMEND：产品推荐</li>
 *     <li>MANAGER_QUERY：基金经理查询</li>
 *     <li>SALES_SCRIPT：销售话术生成</li>
 * </ul>
 * <p>
 * 如果路由决策失败，默认路由到知识库问答分支。
 *
 * @see RouteDecisionClient 路由决策客户端
 * @see RouteType 路由类型枚举
 */
@Slf4j
@Service
public class RouteService {

    private final RouteDecisionClient decisionClient;

    public RouteService(RouteDecisionClient decisionClient) {
        this.decisionClient = decisionClient;
    }

    /**
     * 对问题进行路由决策
     * <p>
     * 调用大语言模型判断问题的意图，返回对应的路由类型。
     * 如果决策失败或返回空值，默认返回 {@link RouteType#KNOWLEDGE_QA}。
     *
     * @param question 用户问题
     * @return 路由类型
     */
    public RouteType route(String question) {
        try {
            var decision = decisionClient.decide(question);
            if (decision == null || decision.routeType() == null) {
                return RouteType.KNOWLEDGE_QA;
            }
            log.info("Routed question to {}: {}", decision.routeType(), decision.reason());
            return decision.routeType();
        } catch (RuntimeException failure) {
            log.warn("Unable to route question; falling back to knowledge QA", failure);
            return RouteType.KNOWLEDGE_QA;
        }
    }
}
