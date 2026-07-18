package com.hqc.smrag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hqc.smrag.entity.SysUserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

/**
 * 用户角色关联 Mapper 接口
 *
 * @see SysUserRole 用户角色关联实体
 */
@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {

    /**
     * 根据用户ID查询角色编码列表
     *
     * @param userId 用户ID
     * @return 角色编码列表
     */
    @Select("""
        SELECT r.role_code FROM sys_role r
        INNER JOIN sys_user_role ur ON r.id = ur.role_id
        WHERE ur.user_id = #{userId}
        """)
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);
}