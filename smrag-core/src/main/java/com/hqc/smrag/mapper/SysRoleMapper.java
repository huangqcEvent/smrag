package com.hqc.smrag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hqc.smrag.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统角色 Mapper 接口
 *
 * @see SysRole 角色实体
 */
@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {
}