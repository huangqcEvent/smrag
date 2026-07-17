package com.hqc.smrag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 基金产品实体
 * <p>
 * 对应数据库表 fund_product，存储基金产品的基本信息。
 * 主要字段：
 * <ul>
 *     <li>id：主键</li>
 *     <li>productCode：产品代码</li>
 *     <li>productName：产品名称</li>
 *     <li>managerId：基金经理ID</li>
 *     <li>riskLevel：风险等级（R1-R5）</li>
 *     <li>returnRate：收益率</li>
 *     <li>investmentStrategy：投资策略</li>
 *     <li>minAmount：最低投资金额</li>
 *     <li>status：产品状态</li>
 * </ul>
 *
 * @see FundManager 基金经理实体
 * @see com.hqc.smrag.enums.RiskLevel 风险等级枚举
 */
@Data
@TableName("fund_product")
public class FundProduct {

    /**
     * 主键ID（自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 产品代码
     * <p>
     * 基金的唯一标识代码。
     */
    private String productCode;

    /**
     * 产品名称
     */
    private String productName;

    /**
     * 基金经理ID
     * <p>
     * 关联 fund_manager 表的 id 字段。
     */
    private Long managerId;

    /**
     * 风险等级
     * <p>
     * 可能的值：R1（低风险）、R2（中低风险）、R3（中风险）、R4（中高风险）、R5（高风险）。
     */
    private String riskLevel;

    /**
     * 收益率
     * <p>
     * 历史收益率（年化），用于展示和参考。
     */
    private BigDecimal returnRate;

    /**
     * 投资策略
     * <p>
     * 基金的投资策略描述。
     */
    private String investmentStrategy;

    /**
     * 最低投资金额
     */
    private BigDecimal minAmount;

    /**
     * 产品状态
     * <p>
     * 可能的值：ACTIVE（在售）、SUSPENDED（暂停）、CLOSED（清盘）。
     */
    private String status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}