package com.hqc.smrag.enums;

import lombok.Getter;

/**
 * 路由类型枚举
 * <p>
 * 定义用户问题的路由类型，决定问题由哪个分支处理。
 * <p>
 * 路由类型说明：
 * <ul>
 *     <li>PRODUCT_QUERY：产品查询，查询基金产品的基本信息</li>
 *     <li>PRODUCT_COMPARE：产品对比，对比多个产品的收益、风险等指标</li>
 *     <li>PRODUCT_RECOMMEND：产品推荐，根据用户需求推荐合适的基金产品</li>
 *     <li>MANAGER_QUERY：管理人查询，查询基金经理的基本信息和业绩</li>
 *     <li>SALES_SCRIPT：销售话术，生成合规的基金销售话术</li>
 *     <li>KNOWLEDGE_QA：知识问答，基于知识库进行 RAG 问答</li>
 * </ul>
 *
 * @see com.hqc.smrag.service.chat.RouteService 路由服务
 * @see com.hqc.smrag.dto.RouteDecision 路由决策 DTO
 */
@Getter
public enum RouteType {

    /**
     * 产品查询
     * <p>
     * 查询指定基金产品的基本信息，如产品名称、风险等级、收益率等。
     */
    PRODUCT_QUERY("产品查询"),

    /**
     * 产品对比
     * <p>
     * 对比多个基金产品的收益、风险、投资策略等指标。
     */
    PRODUCT_COMPARE("产品对比"),

    /**
     * 产品推荐
     * <p>
     * 根据用户的风险偏好、投资金额等需求推荐合适的基金产品。
     */
    PRODUCT_RECOMMEND("产品推荐"),

    /**
     * 管理人查询
     * <p>
     * 查询基金经理的基本信息、管理规模、从业资格等。
     */
    MANAGER_QUERY("管理人查询"),

    /**
     * 销售话术
     * <p>
     * 生成合规的基金销售话术，确保不承诺收益、披露风险。
     */
    SALES_SCRIPT("销售话术"),

    /**
     * 知识问答
     * <p>
     * 基于 RAG 架构，从知识库检索相关文档并生成答案。
     */
    KNOWLEDGE_QA("知识问答"),

    /**
     * 开放问答
     * <p>
     * 回答不依赖企业私有知识、业务数据库或外部工具的一般问题。
     */
    GENERAL_CHAT("开放问答");

    /**
     * 类型描述
     */
    private final String desc;

    RouteType(String desc) {
        this.desc = desc;
    }
}
