package com.hqc.smrag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hqc.smrag.entity.FundProduct;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface FundProductMapper extends BaseMapper<FundProduct> {

    @Select("SELECT * FROM fund_product WHERE product_name LIKE CONCAT('%', #{productName}, '%')")
    List<FundProduct> searchByName(@Param("productName") String productName);

    @Select("SELECT * FROM fund_product WHERE risk_level = #{riskLevel} AND min_amount <= #{minAmount}")
    List<FundProduct> findByRiskAndAmount(
        @Param("riskLevel") String riskLevel,
        @Param("minAmount") java.math.BigDecimal minAmount
    );
}
