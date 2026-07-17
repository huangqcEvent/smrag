package com.hqc.smrag.service.tool;

import com.hqc.smrag.entity.FundManager;
import com.hqc.smrag.entity.FundProduct;
import com.hqc.smrag.mapper.FundManagerMapper;
import com.hqc.smrag.mapper.FundProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;

@Component
@RequiredArgsConstructor
public class FundTools {

    private static final int MAX_COMPARE_PRODUCTS = 5;

    private final FundProductMapper productMapper;
    private final FundManagerMapper managerMapper;

    @Tool(description = "Find fund product details by product name or name keyword")
    public List<ProductView> queryProduct(
        @ToolParam(description = "Fund product name or name keyword") String productName
    ) {
        return productMapper.searchByName(requireText(productName, "productName"))
            .stream()
            .map(ProductView::from)
            .toList();
    }

    @Tool(description = "Compare up to five fund products by name")
    public List<ProductView> compareProducts(
        @ToolParam(description = "Fund product names to compare") List<String> productNames
    ) {
        if (productNames == null || productNames.isEmpty()) {
            throw new IllegalArgumentException("productNames must not be empty");
        }
        return productNames.stream()
            .filter(name -> name != null && !name.isBlank())
            .map(String::trim)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new))
            .stream()
            .limit(MAX_COMPARE_PRODUCTS)
            .flatMap(name -> productMapper.searchByName(name).stream())
            .map(ProductView::from)
            .toList();
    }

    @Tool(description = "Recommend fund products matching risk level and maximum investment amount")
    public List<ProductView> recommendProducts(
        @ToolParam(description = "Required risk level, for example R2 or R3") String riskLevel,
        @ToolParam(description = "Maximum available investment amount") BigDecimal maxAmount
    ) {
        if (maxAmount == null || maxAmount.signum() <= 0) {
            throw new IllegalArgumentException("maxAmount must be greater than zero");
        }
        return productMapper.findByRiskAndAmount(requireText(riskLevel, "riskLevel"), maxAmount)
            .stream()
            .map(ProductView::from)
            .toList();
    }

    @Tool(description = "Find fund manager details by manager name or name keyword")
    public List<ManagerView> queryManager(
        @ToolParam(description = "Fund manager name or name keyword") String managerName
    ) {
        return managerMapper.searchByName(requireText(managerName, "managerName"))
            .stream()
            .map(ManagerView::from)
            .toList();
    }

    private String requireText(String value, String parameterName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(parameterName + " must not be blank");
        }
        return value.trim();
    }

    public record ProductView(
        String productCode,
        String productName,
        String riskLevel,
        BigDecimal returnRate,
        BigDecimal minAmount,
        String investmentStrategy
    ) {
        static ProductView from(FundProduct product) {
            return new ProductView(
                product.getProductCode(),
                product.getProductName(),
                product.getRiskLevel(),
                product.getReturnRate(),
                product.getMinAmount(),
                product.getInvestmentStrategy()
            );
        }
    }

    public record ManagerView(
        String managerCode,
        String managerName,
        String companyName,
        BigDecimal assetsScale,
        String qualification
    ) {
        static ManagerView from(FundManager manager) {
            return new ManagerView(
                manager.getManagerCode(),
                manager.getManagerName(),
                manager.getCompanyName(),
                manager.getAssetsScale(),
                manager.getQualification()
            );
        }
    }
}
