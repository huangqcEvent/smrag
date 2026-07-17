package com.hqc.smrag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 基金经理实体
 * <p>
 * 对应数据库表 fund_manager，存储基金经理的基本信息。
 * 主要字段：
 * <ul>
 *     <li>id：主键</li>
 *     <li>managerCode：经理代码</li>
 *     <li>managerName：经理姓名</li>
 *     <li>companyName：所属公司名称</li>
 *     <li>registerTime：注册时间</li>
 *     <li>assetsScale：管理资产规模</li>
 *     <li>qualification：从业资格</li>
 * </ul>
 *
 * @see FundProduct 基金产品实体
 */
@Data
@TableName("fund_manager")
public class FundManager {

    /**
     * 主键ID（自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 经理代码
     * <p>
     * 基金经理的唯一标识代码。
     */
    private String managerCode;

    /**
     * 经理姓名
     */
    private String managerName;

    /**
     * 所属公司名称
     * <p>
     * 基金管理人（私募/公募基金公司）名称。
     */
    private String companyName;

    /**
     * 注册时间
     * <p>
     * 基金经理在该平台注册的时间。
     */
    private LocalDateTime registerTime;

    /**
     * 管理资产规模
     * <p>
     * 基金经理管理的总资产规模（单位：元）。
     */
    private BigDecimal assetsScale;

    /**
     * 从业资格
     * <p>
     * 基金经理的从业资格证书信息。
     */
    private String qualification;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}