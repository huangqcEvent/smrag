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

/**
 * 基金工具类
 * <p>
 * 提供 Spring AI Function Calling 的工具方法，用于处理与基金产品、基金经理相关的业务查询。
 * <p>
 * 支持的工具：
 * <ul>
 *     <li>queryProduct：查询基金产品详情</li>
 *     <li>compareProducts：对比多个基金产品</li>
 *     <li>recommendProducts：推荐基金产品</li>
 *     <li>queryManager：查询基金经理信息</li>
 * </ul>
 * <p>
 * 这些工具会被 {@link com.hqc.smrag.service.chat.BusinessToolChatService} 调用，
 * 大语言模型会根据用户问题自动决定是否需要调用这些工具。
 *
 * @see com.hqc.smrag.service.chat.BusinessToolChatService 业务工具聊天服务
 */
@Component
@RequiredArgsConstructor
public class FundTools {

    /**
     * 最大对比产品数量
     */
    private static final int MAX_COMPARE_PRODUCTS = 5;

    private final FundProductMapper productMapper;
    private final FundManagerMapper managerMapper;

    /**
     * 查询基金产品详情
     * <p>
     * 根据产品名称或关键词搜索基金产品，返回匹配的产品列表。
     *
     * @param productName 产品名称或关键词
     * @return 产品视图列表
     */
    @Tool(description = "Find fund product details by product name or name keyword")
    public List<ProductView> queryProduct(
        @ToolParam(description = "Fund product name or name keyword") String productName
    ) {
        return productMapper.searchByName(requireText(productName, "productName"))
            .stream()
            .map(ProductView::from)
            .toList();
    }

    /**
     * 对比多个基金产品
     * <p>
     * 最多支持对比 5 个产品，返回各产品的详细信息。
     *
     * @param productNames 产品名称列表
     * @return 产品视图列表
     */
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

    /**
     * 推荐基金产品
     * <p>
     * 根据风险等级和最大投资金额，推荐符合条件的基金产品。
     *
     * @param riskLevel 风险等级（如 R2、R3）
     * @param maxAmount 最大投资金额
     * @return 产品视图列表
     */
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

    /**
     * 查询基金经理信息
     * <p>
     * 根据经理姓名或关键词搜索基金经理，返回匹配的经理列表。
     *
     * @param managerName 经理姓名或关键词
     * @return 经理视图列表
     */
    @Tool(description = "Find fund manager details by manager name or name keyword")
    public List<ManagerView> queryManager(
        @ToolParam(description = "Fund manager name or name keyword") String managerName
    ) {
        return managerMapper.searchByName(requireText(managerName, "managerName"))
            .stream()
            .map(ManagerView::from)
            .toList();
    }

    /**
     * 校验文本参数非空
     */
    private String requireText(String value, String parameterName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(parameterName + " must not be blank");
        }
        return value.trim();
    }

    /**
     * 产品视图
     * <p>
     * 用于返回给大语言模型的基金产品信息。
     */
    public record ProductView(
        String productCode,
        String productName,
        String riskLevel,
        BigDecimal returnRate,
        BigDecimal minAmount,
        String investmentStrategy
    ) {
        /**
         * 从实体转换为视图
         */
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

    /**
     * 经理视图
     * <p>
     * 用于返回给大语言模型的基金经理信息。
     */
    public record ManagerView(
        String managerCode,
        String managerName,
        String companyName,
        BigDecimal assetsScale,
        String qualification
    ) {
        /**
         * 从实体转换为视图
         */
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
