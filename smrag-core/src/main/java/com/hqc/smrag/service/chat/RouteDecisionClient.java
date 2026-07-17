package com.hqc.smrag.service.chat;

import com.hqc.smrag.dto.RouteDecision;

public interface RouteDecisionClient {
    RouteDecision decide(String question);
}
