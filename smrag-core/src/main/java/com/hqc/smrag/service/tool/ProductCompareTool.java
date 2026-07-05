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
public class ProductCompareTool implements Function<String, String> {

    private final FundProductMapper productMapper;

    @Override
    public String apply(String productNames) {
        String[] names = productNames.split(",");
        StringBuilder result = new StringBuilder("产品对比结果：\n");

        for (String name : names) {
            List<FundProduct> products = productMapper.searchByName(name.trim());
            if (!products.isEmpty()) {
                FundProduct p = products.get(0);
                result.append(String.format("- %s：风险%s，收益率%s%%，最低投资%s元\n",
                    p.getProductName(), p.getRiskLevel(), p.getReturnRate(), p.getMinAmount()));
            }
        }
        return result.toString();
    }
}