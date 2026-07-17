package com.hqc.smrag.enums;

import lombok.Getter;

@Getter
public enum DocumentStatus {
    PENDING("待处理"),
    PROCESSING("处理中"),
    DELETING("删除中"),
    COMPLETED("已完成"),
    FAILED("失败");

    private final String desc;

    DocumentStatus(String desc) {
        this.desc = desc;
    }
}
