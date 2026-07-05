package com.hqc.smrag.enums;

import lombok.Getter;

@Getter
public enum RiskLevel {
    R1("低风险"),
    R2("中低风险"),
    R3("中风险"),
    R4("中高风险"),
    R5("高风险");

    private final String desc;

    RiskLevel(String desc) {
        this.desc = desc;
    }
}