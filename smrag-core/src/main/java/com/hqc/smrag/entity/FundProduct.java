package com.hqc.smrag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("fund_product")
public class FundProduct {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String productCode;
    private String productName;
    private Long managerId;
    private String riskLevel;
    private BigDecimal returnRate;
    private String investmentStrategy;
    private BigDecimal minAmount;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}