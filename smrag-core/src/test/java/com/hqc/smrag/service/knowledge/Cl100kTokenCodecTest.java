package com.hqc.smrag.service.knowledge;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Cl100kTokenCodecTest {

    private final Cl100kTokenCodec codec = new Cl100kTokenCodec();

    @Test
    void splitsWithoutExceedingTokenLimitAndPreservesText() {
        String text = "第一段 mixed English content。第二段继续补充信息。";

        List<String> pieces = codec.splitByTokens(text, 5);

        assertThat(pieces).isNotEmpty();
        assertThat(pieces).allMatch(piece -> codec.count(piece) <= 5);
        assertThat(String.join("", pieces)).isEqualTo(text);
    }

    @Test
    void extractsAtMostRequestedTailTokens() {
        String text = "第一段 mixed English content。第二段继续补充信息。";

        String suffix = codec.lastTokens(text, 4);

        assertThat(suffix).isNotEmpty();
        assertThat(codec.count(suffix)).isLessThanOrEqualTo(4);
        assertThat(text).endsWith(suffix);
        assertThat(codec.lastTokens(text, 0)).isEmpty();
    }

    @Test
    void rejectsNonPositiveSplitLimit() {
        assertThatThrownBy(() -> codec.splitByTokens("content", 0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.lastTokens("content", -1))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
