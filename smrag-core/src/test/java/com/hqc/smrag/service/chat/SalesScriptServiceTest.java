package com.hqc.smrag.service.chat;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SalesScriptServiceTest {

    @Test
    void usesAComplianceFocusedSystemPrompt() {
        assertThat(SalesScriptService.systemPrompt())
            .contains("\u4e0d\u627f\u8bfa\u6536\u76ca")
            .contains("\u62ab\u9732\u98ce\u9669")
            .contains("\u5efa\u8bae\u9605\u8bfb\u6b63\u5f0f\u4ea7\u54c1\u6750\u6599");
    }
}
