# RAG 角色 ID 权限过滤设计

## 目标

让写入向量分块元数据的文档权限真正参与 RAG 检索，同时避免把用户可访问的全部文档 ID 展开成超长过滤条件。角色名称或编码修改后不应要求重建向量索引。

## 设计选择

关系数据库仍然是权限事实来源，向量分块保存用于检索前置过滤的权限快照。

- 每个可读角色使用稳定角色 ID 写入布尔元数据，例如 `aclRoleId_12=true`。
- `aclVersion` 表示文档权限快照版本。
- `aclDenied` 表示该分块是否必须拒绝检索。
- 不再使用 `aclRole_ADMIN` 之类依赖角色编码的键。
- RAG 检索使用用户当前角色 ID，而不是用户可访问的全部文档 ID。

采用角色 ID 的原因是角色名称和角色编码属于可修改的业务属性，而角色 ID 是权限关联表使用的稳定标识。新增、删除角色不会改变其他角色对应的元数据键。

## 权限数据流

### 文档上传与重新索引

1. 文档权限先写入 `doc_permission`。
2. 解析或重建索引时，从 `doc_permission` 读取该文档当前所有可读角色 ID。
3. 每个 chunk 写入相同的 `aclVersion`、`aclDenied=false` 和 `aclRoleId_<id>=true`。
4. 文档没有任何可读角色时终止索引，不能生成默认放行的 chunk。

### 文档权限修改

1. 锁定 `knowledge_document`，在同一事务内替换 `doc_permission`。
2. 递增文档 `aclVersion`。
3. 清除该文档现有向量元数据中的全部 `aclRoleId_*` 和旧 `aclRole_*` 键。
4. 写入新的 `aclVersion`、`aclDenied` 和 `aclRoleId_*` 权限快照。

### RAG 检索

1. 每次问答从 `sys_user_role` 查询用户当前角色 ID。
2. 基础过滤条件保持 `knowledgeBaseId`、`documentStatus=COMPLETED` 和 `aclDenied=false`。
3. 有角色时追加：

   ```text
   aclRoleId_1=true OR aclRoleId_12=true
   ```

4. 没有角色时追加不可能命中的条件，禁止退化为知识库全量检索。
5. 指定 `documentId` 时仍追加文档 ID 条件，但不再展开用户可访问文档 ID 列表。

过滤条件长度由“用户拥有的角色数量”决定，而不是由“用户可访问的文档数量”决定。

## 角色变更语义

- 修改角色名称：角色 ID 不变，不更新 chunk。
- 修改角色编码：角色 ID 不变，不更新 chunk。
- 给用户增加或移除角色：下次检索读取当前角色 ID，不更新 chunk。
- 给文档增加或移除角色：更新该文档全部 chunk 的 ACL 快照。
- 删除角色：沿用现有约束；仍被用户或文档引用时拒绝删除。

## 二次校验

向量元数据只负责减少无权限内容进入候选集，不能替代最终授权。

- 引用返回和持久化前，继续使用 `doc_permission` 与用户当前角色关系重新校验文档 ID。
- 原文件预览和下载继续通过关系数据库校验。
- 即使向量 ACL 快照短暂不一致，引用和原文件也不能越权返回。

## 数据迁移

新增 Flyway 迁移：

- 删除所有历史 `aclRole_*` 和 `aclRoleId_*` 键。
- 根据 `doc_permission.role_id` 为现有 chunk 回填 `aclRoleId_<id>=true`。
- 同步 `aclVersion`。
- 没有可读角色的文档写入 `aclDenied=true`。

迁移应可处理历史元数据为空或已有旧角色编码键的情况。

## 测试范围

- 检索过滤使用角色 ID，不包含可访问文档 ID 展开条件。
- 多角色生成 OR 条件，空角色拒绝检索。
- 指定文档时同时要求文档 ID 和角色权限。
- 用户角色 ID 从数据库按请求读取。
- 同步、切块、重新索引均写入 `aclRoleId_*`，不写 `aclRole_*`。
- 权限修改清理旧的角色 ID 和角色编码元数据键。
- Flyway 迁移将既有向量元数据正确转换为角色 ID ACL。
- 引用和原文件数据库二次校验保持有效。
