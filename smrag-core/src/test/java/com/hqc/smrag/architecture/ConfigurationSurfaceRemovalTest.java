package com.hqc.smrag.architecture;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigurationSurfaceRemovalTest {

    @Test
    void removesProductAndManagerConfigurationSurfaces() {
        assertMissing("com.hqc.smrag.controller.ProductController");
        assertMissing("com.hqc.smrag.controller.ManagerController");
        assertMissing("com.hqc.smrag.service.product.ProductService");
        assertMissing("com.hqc.smrag.service.manager.ManagerService");
    }

    @Test
    void keepsDomainAccessRequiredByChatTools() {
        assertPresent("com.hqc.smrag.entity.FundProduct");
        assertPresent("com.hqc.smrag.entity.FundManager");
        assertPresent("com.hqc.smrag.mapper.FundProductMapper");
        assertPresent("com.hqc.smrag.mapper.FundManagerMapper");
    }

    private void assertMissing(String className) {
        assertThatThrownBy(() -> Class.forName(className))
            .isInstanceOf(ClassNotFoundException.class);
    }

    private void assertPresent(String className) {
        assertThatCode(() -> Class.forName(className)).doesNotThrowAnyException();
    }
}
