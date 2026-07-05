package com.hqc.smrag.service.tool;

import com.hqc.smrag.entity.FundProduct;
import com.hqc.smrag.mapper.FundProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.function.Function;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductQueryTool implements Function<String, String> {

    private final FundProductMapper productMapper;

    @Override
    public String apply(String productName) {
        List<FundProduct> products = productMapper.searchByName(productName);
        if (products.isEmpty()) {
            return "未找到产品：" + productName;
        }
        FundProduct product = products.get(0);
        return String.format("产品名称：%s，风险等级：%s，收益率：%s%%，最低投资额：%s元",
            product.getProductName(),
            product.getRiskLevel(),
            product.getReturnRate(),
            product.getMinAmount());
    }
}