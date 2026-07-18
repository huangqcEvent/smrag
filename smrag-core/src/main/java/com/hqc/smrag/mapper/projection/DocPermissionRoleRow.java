package com.hqc.smrag.mapper.projection;

import lombok.Data;

@Data
public class DocPermissionRoleRow {
    private Long documentId;
    private Long roleId;
    private String roleCode;
    private String roleName;
    private String description;
}
