package com.hqc.smrag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("fund_manager")
public class FundManager {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String managerCode;
    private String managerName;
    private String companyName;
    private LocalDateTime registerTime;
    private BigDecimal assetsScale;
    private String qualification;
    private LocalDateTime createTime;
}