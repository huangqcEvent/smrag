package com.hqc.smrag.service.tool;

import com.hqc.smrag.entity.FundManager;
import com.hqc.smrag.mapper.FundManagerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.function.Function;

@Slf4j
@Component
@RequiredArgsConstructor
public class ManagerQueryTool implements Function<String, String> {

    private final FundManagerMapper managerMapper;

    @Override
    public String apply(String managerName) {
        List<FundManager> managers = managerMapper.searchByName(managerName);
        if (managers.isEmpty()) {
            return "未找到管理人：" + managerName;
        }
        FundManager manager = managers.get(0);
        return String.format("管理人：%s，公司：%s，资产规模：%s元",
            manager.getManagerName(),
            manager.getCompanyName(),
            manager.getAssetsScale());
    }
}