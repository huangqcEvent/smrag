package com.hqc.smrag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hqc.smrag.entity.KnowledgeBase;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface KnowledgeBaseMapper extends BaseMapper<KnowledgeBase> {

    @Select("SELECT * FROM knowledge_base WHERE id = #{id} FOR UPDATE")
    KnowledgeBase selectByIdForUpdate(@Param("id") Long id);

    @Select("""
        SELECT kb.*
        FROM knowledge_base kb
        WHERE kb.status = 'ACTIVE'
          AND EXISTS (
              SELECT 1
              FROM knowledge_base_role kbr
              WHERE kbr.knowledge_base_id = kb.id
          )
        ORDER BY kb.created_at, kb.id
        """)
    List<KnowledgeBase> selectActiveWithRoles();

    @Select("""
        SELECT DISTINCT kb.*
        FROM knowledge_base kb
        INNER JOIN knowledge_base_role kbr ON kbr.knowledge_base_id = kb.id
        INNER JOIN sys_user_role sur ON sur.role_id = kbr.role_id
        WHERE sur.user_id = #{userId} AND kb.status = 'ACTIVE'
        ORDER BY kb.created_at, kb.id
        """)
    List<KnowledgeBase> selectAccessibleByUserId(@Param("userId") Long userId);
}
