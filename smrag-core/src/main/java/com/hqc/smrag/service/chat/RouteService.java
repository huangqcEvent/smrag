package com.hqc.smrag.service.chat;

import com.hqc.smrag.enums.RouteType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class RouteService {

    private final RouteDecisionClient decisionClient;

    public RouteService(RouteDecisionClient decisionClient) {
        this.decisionClient = decisionClient;
    }

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
