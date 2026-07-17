package com.hqc.smrag.dto;

import com.hqc.smrag.enums.RouteType;

public record RouteDecision(RouteType routeType, String reason) {

    public RouteDecision {
        routeType = routeType == null ? RouteType.KNOWLEDGE_QA : routeType;
        reason = reason == null ? "" : reason;
    }
}
