package com.hqc.smrag.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChunkingStrategyTest {

    @Test
    void defaultsMissingRequestValueToToken() {
        assertThat(ChunkingStrategy.fromRequest(null))
            .isEqualTo(ChunkingStrategy.TOKEN);
        assertThat(ChunkingStrategy.fromRequest("  "))
            .isEqualTo(ChunkingStrategy.TOKEN);
    }

    @Test
    void parsesRequestValueCaseInsensitively() {
        assertThat(ChunkingStrategy.fromRequest(" recursive "))
            .isEqualTo(ChunkingStrategy.RECURSIVE);
    }

    @Test
    void rejectsUnsupportedRequestValue() {
        assertThatThrownBy(() -> ChunkingStrategy.fromRequest("characters"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("TOKEN")
            .hasMessageContaining("RECURSIVE");
    }

    @Test
    void persistedValueMustBePresentAndSupported() {
        assertThat(ChunkingStrategy.fromPersisted("TOKEN"))
            .isEqualTo(ChunkingStrategy.TOKEN);
        assertThatThrownBy(() -> ChunkingStrategy.fromPersisted(null))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ChunkingStrategy.fromPersisted("UNKNOWN"))
            .isInstanceOf(IllegalStateException.class);
    }
}
