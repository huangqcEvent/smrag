package com.hqc.smrag.vo;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class ProductVO {
    private Long id;
    private String productCode;
    private String productName;
    private String managerName;
    private String riskLevel;
    private BigDecimal returnRate;
    private String investmentStrategy;
    private BigDecimal minAmount;
}