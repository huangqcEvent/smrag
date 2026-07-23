package com.hqc.smrag.mapper;

import com.hqc.smrag.entity.KnowledgeBaseRole;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface KnowledgeBaseRoleMapper {

    @Insert("""
        INSERT INTO knowledge_base_role(
            knowledge_base_id,
            role_id,
            created_by,
            created_at
        )
        VALUES(
            #{knowledgeBaseId},
            #{roleId},
            #{createdBy},
            #{createdAt}
        )
        """)
    int insert(KnowledgeBaseRole relation);

    @Delete("""
        DELETE FROM knowledge_base_role
        WHERE knowledge_base_id = #{knowledgeBaseId}
        """)
    int deleteByKnowledgeBaseId(
        @Param("knowledgeBaseId") Long knowledgeBaseId
    );

    @Select("""
        SELECT COUNT(*)
        FROM knowledge_base_role
        WHERE role_id = #{roleId}
        """)
    long countByRoleId(@Param("roleId") Long roleId);

    @Select("""
        SELECT role_id
        FROM knowledge_base_role
        WHERE knowledge_base_id = #{knowledgeBaseId}
        ORDER BY role_id
        """)
    List<Long> selectRoleIds(@Param("knowledgeBaseId") Long knowledgeBaseId);
}
