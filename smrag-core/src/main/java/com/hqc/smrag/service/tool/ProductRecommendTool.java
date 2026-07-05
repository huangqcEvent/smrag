package com.hqc.smrag.service.tool;

import com.hqc.smrag.entity.FundProduct;
import com.hqc.smrag.mapper.FundProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductRecommendTool implements Function<String, String> {

    private final FundProductMapper productMapper;

    @Override
    public String apply(String params) {
        String[] parts = params.split(",");
        if (parts.length < 2) {
            return "参数格式错误，请提供：风险等级,金额";
        }
        String riskLevel = parts[0].trim();
        BigDecimal maxAmount = new BigDecimal(parts[1].trim());

        List<FundProduct> products = productMapper.findByRiskAndAmount(riskLevel, maxAmount);

        if (products.isEmpty()) {
            return "未找到符合条件的产品";
        }

        StringBuilder result = new StringBuilder("推荐产品：\n");
        for (FundProduct p : products) {
            result.append(String.format("- %s：风险%s，收益率%s%%，最低投资%s元\n",
                p.getProductName(), p.getRiskLevel(), p.getReturnRate(), p.getMinAmount()));
        }
        return result.toString();
    }
}