package com.hqc.smrag.enums;

import lombok.Getter;

@Getter
public enum RouteType {
    PRODUCT_QUERY("产品查询"),
    PRODUCT_COMPARE("产品对比"),
    PRODUCT_RECOMMEND("产品推荐"),
    MANAGER_QUERY("管理人查询"),
    SALES_SCRIPT("销售话术"),
    KNOWLEDGE_QA("知识问答");

    private final String desc;

    RouteType(String desc) {
        this.desc = desc;
    }
}
