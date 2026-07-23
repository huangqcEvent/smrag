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

    /**
     * 查询用户可访问的文档ID列表
     *
     * @param userId 用户ID
     * @return 文档ID列表
     */
    @Select("""
        SELECT DISTINCT dp.document_id FROM doc_permission dp
        INNER JOIN sys_user_role sur ON dp.role_id = sur.role_id
        WHERE sur.user_id = #{userId} AND dp.can_read = true
        """)
    List<Long> selectAccessibleDocIdsByUserId(@Param("userId") Long userId);

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
}
