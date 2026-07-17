package com.hqc.smrag.service.knowledge;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VectorDocumentIdGeneratorTest {

    @Test
    void generatesStableUuidPerDocumentVersionAndChunk() {
        VectorDocumentIdGenerator generator = new VectorDocumentIdGenerator();

        assertThat(generator.generate("default", 42L, 2, 3))
            .isEqualTo(generator.generate("default", 42L, 2, 3));
        assertThat(generator.generate("default", 42L, 2, 3))
            .isNotEqualTo(generator.generate("default", 42L, 2, 4));
        assertThat(generator.generate("default", 42L, 2, 3))
            .isNotEqualTo(generator.generate("research", 42L, 2, 3));
    }
}
