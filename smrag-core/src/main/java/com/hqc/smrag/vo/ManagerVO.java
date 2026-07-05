package com.hqc.smrag.vo;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class ManagerVO {
    private Long id;
    private String managerCode;
    private String managerName;
    private String companyName;
    private BigDecimal assetsScale;
    private String qualification;
}