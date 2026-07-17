package com.hqc.smrag.service.tool;

import com.hqc.smrag.entity.FundManager;
import com.hqc.smrag.entity.FundProduct;
import com.hqc.smrag.mapper.FundManagerMapper;
import com.hqc.smrag.mapper.FundProductMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FundToolsTest {

    private final FundProductMapper productMapper = mock(FundProductMapper.class);
    private final FundManagerMapper managerMapper = mock(FundManagerMapper.class);
    private final FundTools tools = new FundTools(productMapper, managerMapper);

    @Test
    void exposesOnlyProductFieldsNeededByTheModel() {
        when(productMapper.searchByName("Growth"))
            .thenReturn(List.of(product("P-001", "Growth Fund")));

        assertThat(tools.queryProduct(" Growth "))
            .containsExactly(new FundTools.ProductView(
                "P-001",
                "Growth Fund",
                "R3",
                new BigDecimal("3.20"),
                new BigDecimal("1000"),
                "Long-term growth"
            ));
        verify(productMapper).searchByName("Growth");
    }

    @Test
    void comparesAtMostFiveDistinctProductNames() {
        for (String name : List.of("A", "B", "C", "D", "E")) {
            when(productMapper.searchByName(name)).thenReturn(List.of(product(name, "Fund " + name)));
        }

        List<FundTools.ProductView> result = tools.compareProducts(
            List.of(" A ", "B", "A", "C", "D", "E", "F", " ")
        );

        assertThat(result).hasSize(5);
        verify(productMapper).searchByName("A");
        verify(productMapper).searchByName("E");
    }

    @Test
    void validatesRecommendationAmount() {
        assertThatThrownBy(() -> tools.recommendProducts("R2", BigDecimal.ZERO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("maxAmount");
    }

    @Test
    void mapsManagerQueryToARestrictedView() {
        FundManager manager = new FundManager();
        manager.setManagerCode("M-001");
        manager.setManagerName("Alex Chen");
        manager.setCompanyName("Example Asset");
        manager.setAssetsScale(new BigDecimal("800000000"));
        manager.setQualification("CFA");
        when(managerMapper.searchByName("Alex")).thenReturn(List.of(manager));

        assertThat(tools.queryManager("Alex"))
            .containsExactly(new FundTools.ManagerView(
                "M-001",
                "Alex Chen",
                "Example Asset",
                new BigDecimal("800000000"),
                "CFA"
            ));
    }

    private FundProduct product(String code, String name) {
        FundProduct product = new FundProduct();
        product.setProductCode(code);
        product.setProductName(name);
        product.setRiskLevel("R3");
        product.setReturnRate(new BigDecimal("3.20"));
        product.setMinAmount(new BigDecimal("1000"));
        product.setInvestmentStrategy("Long-term growth");
        return product;
    }
}
