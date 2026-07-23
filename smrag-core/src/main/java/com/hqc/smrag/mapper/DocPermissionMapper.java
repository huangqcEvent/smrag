package com.hqc.smrag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hqc.smrag.entity.DocPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;
import com.hqc.smrag.mapper.projection.DocPermissionRoleRow;

/**
 * 文档权限 Mapper 接口
 *
 * @see DocPermission 文档权限实体
 */
@Mapper
public interface DocPermissionMapper extends BaseMapper<DocPermission> {

    @Select("""
        SELECT d.id
        FROM knowledge_document d
        INNER JOIN knowledge_base kb ON kb.id = d.knowledge_base_id
        WHERE kb.status = 'ACTIVE'
          AND EXISTS (
              SELECT 1
              FROM knowledge_base_role kbr
              INNER JOIN sys_user_role sur ON sur.role_id = kbr.role_id
              WHERE kbr.knowledge_base_id = kb.id
                AND sur.user_id = #{userId}
          )
          AND EXISTS (
              SELECT 1
              FROM doc_permission dp
              INNER JOIN sys_user_role sur ON sur.role_id = dp.role_id
              WHERE dp.document_id = d.id
                AND dp.can_read = true
                AND sur.user_id = #{userId}
          )
        ORDER BY d.id
        """)
    List<Long> selectAuthorizedDocIdsByUserId(@Param("userId") Long userId);

    @Select("""
        SELECT d.id
        FROM knowledge_document d
        WHERE d.knowledge_base_id = #{knowledgeBaseId}
          AND EXISTS (
              SELECT 1
              FROM knowledge_base_role kbr
              INNER JOIN sys_user_role sur ON sur.role_id = kbr.role_id
              WHERE kbr.knowledge_base_id = d.knowledge_base_id
                AND sur.user_id = #{userId}
          )
          AND EXISTS (
              SELECT 1
              FROM doc_permission dp
              INNER JOIN sys_user_role sur ON sur.role_id = dp.role_id
              WHERE dp.document_id = d.id
                AND dp.can_read = true
                AND sur.user_id = #{userId}
          )
        ORDER BY d.id
        """)
    List<Long> selectAuthorizedDocIdsByUserIdAndKnowledgeBaseId(
        @Param("userId") Long userId,
        @Param("knowledgeBaseId") Long knowledgeBaseId
    );

    @Select("""
        SELECT d.id
        FROM knowledge_document d
        WHERE d.knowledge_base_id = #{knowledgeBaseId}
          AND EXISTS (
              SELECT 1
              FROM knowledge_base_role kbr
              WHERE kbr.knowledge_base_id = d.knowledge_base_id
          )
          AND EXISTS (
              SELECT 1
              FROM doc_permission dp
              WHERE dp.document_id = d.id AND dp.can_read = true
          )
        ORDER BY d.id
        """)
    List<Long> selectAllDocumentIdsByKnowledgeBaseId(
        @Param("knowledgeBaseId") Long knowledgeBaseId
    );

    @Select("""
        <script>
        SELECT d.id
        FROM knowledge_document d
        INNER JOIN knowledge_base kb ON kb.id = d.knowledge_base_id
        WHERE d.id IN
        <foreach collection="documentIds" item="id" open="(" separator="," close=")">
            #{id}
        </foreach>
          AND kb.status = 'ACTIVE'
          AND EXISTS (
              SELECT 1
              FROM knowledge_base_role kbr
              WHERE kbr.knowledge_base_id = kb.id
          )
          AND EXISTS (
              SELECT 1
              FROM doc_permission dp
              WHERE dp.document_id = d.id AND dp.can_read = true
          )
          AND (
              #{globalAdmin} = true
              OR (
                  EXISTS (
                      SELECT 1
                      FROM knowledge_base_role kbr
                      INNER JOIN sys_user_role sur ON sur.role_id = kbr.role_id
                      WHERE kbr.knowledge_base_id = kb.id
                        AND sur.user_id = #{userId}
                  )
                  AND EXISTS (
                      SELECT 1
                      FROM doc_permission dp
                      INNER JOIN sys_user_role sur ON sur.role_id = dp.role_id
                      WHERE dp.document_id = d.id
                        AND dp.can_read = true
                        AND sur.user_id = #{userId}
                  )
              )
          )
        ORDER BY d.id
        </script>
        """)
    List<Long> selectCurrentlyReadableDocumentIds(
        @Param("userId") Long userId,
        @Param("globalAdmin") boolean globalAdmin,
        @Param("documentIds") List<Long> documentIds
    );

    @Select("""
        <script>
        SELECT dp.document_id AS documentId, r.id AS roleId, r.role_code AS roleCode,
               r.role_name AS roleName, r.description
        FROM doc_permission dp
        INNER JOIN sys_role r ON r.id = dp.role_id
        WHERE dp.can_read = true AND dp.document_id IN
        <foreach collection="documentIds" item="id" open="(" separator="," close=")">
            #{id}
        </foreach>
        ORDER BY r.role_code
        </script>
        """)
    List<DocPermissionRoleRow> selectReadableRolesByDocumentIds(
        @Param("documentIds") List<Long> documentIds
    );

    @Select("""
        SELECT dp.role_id
        FROM doc_permission dp
        WHERE dp.document_id = #{documentId} AND dp.can_read = true
        ORDER BY dp.role_id
        """)
    List<Long> selectReadableRoleIdsByDocumentId(
        @Param("documentId") Long documentId
    );

    @Select("""
        SELECT role_id
        FROM sys_user_role
        WHERE user_id = #{userId}
        ORDER BY role_id
        """)
    List<Long> selectRoleIdsByUserId(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM doc_permission WHERE role_id = #{roleId}")
    long countByRoleId(@Param("roleId") Long roleId);

    @Select("""
        SELECT DISTINCT dp.role_id
        FROM doc_permission dp
        INNER JOIN knowledge_document d ON d.id = dp.document_id
        WHERE d.knowledge_base_id = #{knowledgeBaseId}
          AND dp.can_read = true
        ORDER BY dp.role_id
        """)
    List<Long> selectReadableRoleIdsByKnowledgeBaseId(
        @Param("knowledgeBaseId") Long knowledgeBaseId
    );
}
