package com.hqc.smrag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hqc.smrag.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/**
 * 系统角色 Mapper 接口
 *
 * @see SysRole 角色实体
 */
@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {

    @Select("SELECT * FROM sys_role WHERE id = #{id} FOR UPDATE")
    SysRole selectByIdForUpdate(@Param("id") Long id);

    @Select("""
        <script>
        SELECT *
        FROM sys_role
        WHERE role_code IN
        <foreach collection="roleCodes" item="roleCode"
                 open="(" separator="," close=")">
            #{roleCode}
        </foreach>
        ORDER BY id
        FOR KEY SHARE
        </script>
        """)
    List<SysRole> selectByCodesForKeyShare(
        @Param("roleCodes") Collection<String> roleCodes
    );
}
