package com.hqc.smrag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hqc.smrag.entity.FundManager;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface FundManagerMapper extends BaseMapper<FundManager> {

    @Select("SELECT * FROM fund_manager WHERE manager_name LIKE CONCAT('%', #{managerName}, '%')")
    List<FundManager> searchByName(@Param("managerName") String managerName);
}
