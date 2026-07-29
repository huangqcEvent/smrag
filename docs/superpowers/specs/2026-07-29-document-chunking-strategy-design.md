# 文档上传可选切块策略设计

**日期：** 2026-07-29
**状态：** 已确认，待实现

## 1. 背景

当前文档入库统一使用 Spring AI `TokenTextSplitter`。该实现能够控制
Token 上限，但不支持相邻块重叠，也只会在有限的英文标点处尝试回退，
中文段落和句子可能在边界处被截断。

系统需要允许管理员在每次上传文档时选择：

- `TOKEN`：保留现有 Token 切块行为；
- `RECURSIVE`：使用面向中英文文档的增强递归切块行为。

上传者只选择策略，不在界面配置块大小、重叠量或分隔符。具体参数由
后端统一配置，避免不合理参数导致碎片过多、向量成本失控或内容被截断。

## 2. 目标

1. 上传界面允许选择 Token 切块或递归切块。
2. 未选择或旧客户端未传参时继续使用 Token 切块。
3. 切块策略随文档持久化，异步消费和重试使用同一策略。
4. 递归切块优先保留段落、句子和标点。
5. 递归切块合并相邻短片段，避免产生大量过小 Chunk。
6. 递归切块支持可配置的 Token 重叠，并保证每块不超过 Token 上限。
7. 两种策略共用现有 metadata、ACL、稳定向量 ID 和补偿清理流程。

## 3. 非目标

- 不允许上传者自定义块大小、重叠量或分隔符。
- 不自动重新切分已经完成入库的历史文档。
- 不跨 Spring AI 原始 `Document` 合并内容；PDF 不跨页切块或重叠。
- 不引入 Alibaba `RecursiveCharacterTextSplitter` 作为实际算法实现。
- 不在本次变更中增加 Markdown、代码或语义模型切块策略。

## 4. 核心决策

### 4.1 策略类型

新增 `ChunkingStrategy` 枚举：

```java
public enum ChunkingStrategy {
    TOKEN,
    RECURSIVE
}
```

接口输入大小写不敏感，服务端统一转为大写枚举值。空值使用 `TOKEN`，
未知值作为请求参数错误返回。

### 4.2 组件边界

新增统一策略接口：

```java
public interface DocumentSplittingStrategy {

    ChunkingStrategy type();

    List<Document> split(List<Document> documents);
}
```

对应两个实现：

- `TokenDocumentSplittingStrategy`：封装现有 `TokenTextSplitter`；
- `RecursiveDocumentSplittingStrategy`：实现增强递归切块算法。

`DocumentChunker` 不再持有某个具体 splitter，而是：

1. 为源文档补充业务 metadata；
2. 根据文档保存的 `ChunkingStrategy` 选择策略；
3. 调用策略完成文本切分；
4. 按返回顺序写入 `chunkIndex`；
5. 继续使用 `VectorDocumentIdGenerator` 生成稳定 UUID。

策略实现只负责切分并完整复制源 `Document` 的 metadata，不负责业务
metadata、ACL 或向量 ID。

Spring 注入所有 `DocumentSplittingStrategy` Bean 后建立按枚举索引的
不可变映射。启动时若策略缺失或同一枚举存在重复实现，应立即失败。

## 5. 数据流

```text
管理员选择切块策略
  → POST /api/knowledge/upload
  → 校验并保存 knowledge_document.chunking_strategy
  → 在同一上传事务中写入权限和 Outbox
  → Outbox 发布 Kafka 消息
  → 消费端按 documentId 读取 KnowledgeDocument
  → DocumentChunker 根据持久化策略选择 splitter
  → 统一补充 metadata、chunkIndex 和稳定 UUID
  → 分批写入向量库
  → 保存 knowledge_chunk 并完成文档状态
```

`DocumentParseTaskDTO` 不重复保存策略。`knowledge_document` 是策略的
唯一事实来源，避免数据库与消息中出现两个可能不一致的值。

## 6. API 契约

现有上传接口继续使用 `multipart/form-data`，新增可选字段：

```text
chunkingStrategy=TOKEN | RECURSIVE
```

兼容规则：

- 未提供、空字符串：按 `TOKEN` 处理；
- `token`、`Token` 等大小写形式：规范化为 `TOKEN`；
- 非法值：返回 HTTP 400，并说明允许值为 `TOKEN`、`RECURSIVE`。

前端请求方法和 Pinia store 将该字段原样沿上传链路传递。

文档列表响应新增：

```json
{
  "chunkingStrategy": "RECURSIVE"
}
```

不返回递归切块的内部参数，避免把部署配置暴露为文档级配置。

## 7. 数据持久化

新增 Flyway 迁移，为 `knowledge_document` 增加：

```sql
chunking_strategy VARCHAR(16) NOT NULL DEFAULT 'TOKEN'
```

同时添加检查约束，只允许：

```text
TOKEN
RECURSIVE
```

数据库默认值会把现有文档迁移为 `TOKEN`，不会触发重新解析或向量写入。

`KnowledgeDocument` 和 `DocumentVO` 增加 `chunkingStrategy` 字段。创建
占位文档时即写入策略，确保文件上传完成和 Outbox 创建之前策略已经
持久化。

## 8. Token 切块策略

`TokenDocumentSplittingStrategy` 保持当前配置与行为：

- `chunkSize`
- `minChunkSizeChars`
- `minChunkLengthToEmbed`
- `maxNumChunks`
- `keepSeparator`

本次变更不调整已有 Token 策略参数、默认值或结果顺序。现有测试作为
回归基线。

## 9. 增强递归切块策略

### 9.1 配置

新增后端配置：

```yaml
smrag:
  ai:
    rag:
      recursive-chunk-size: 800
      recursive-chunk-overlap: 120
      recursive-separators:
        - "\n\n"
        - "\n"
        - "。"
        - "！"
        - "？"
        - "；"
        - "，"
        - " "
```

其中：

- `recursiveChunkSize` 是每个输出 Chunk 的最大 Token 数；
- `recursiveChunkOverlap` 是相邻 Chunk 的目标重叠 Token 数；
- `recursiveSeparators` 按从粗到细的优先级排列。

默认重叠为 `120 / 800 = 15%`。

使用与当前 `TokenTextSplitter` 一致的 `CL100K_BASE` 编码进行计数和
硬切。虽然它不是 DashScope 的原生 tokenizer，但可以保持项目两种
策略的 Token 估算口径一致。

配置启动校验：

- `recursiveChunkSize > 0`；
- `0 <= recursiveChunkOverlap < recursiveChunkSize`；
- 分隔符列表非空；
- 分隔符不能为 `null` 或空字符串，空白分隔符 `" "` 合法；
- 分隔符不得重复。

分隔符耗尽后的 Token 硬切是明确的回退步骤，不使用空字符串分隔符。

### 9.2 单个源文档的处理

每个 Spring AI 源 `Document` 独立处理：

1. 空白文本不产生 Chunk。
2. 按 `recursiveSeparators` 的顺序递归拆分。
3. 分隔符按原位置保留在前一个片段末尾。
4. 切分使用字面量匹配，不使用 `String.split(regex)`。
5. 超过 Token 上限的片段继续使用下一级分隔符。
6. 所有分隔符耗尽后，按 Token 边界硬切为不超过上限的原子片段。
7. 按原始顺序将相邻原子片段贪心合并，尽量接近 Token 上限。
8. 生成下一块时，以前一块尾部约 `recursiveChunkOverlap` 个 Token
   作为上下文前缀。
9. 每个新 Chunk 必须包含尚未输出的新内容，禁止生成纯重叠 Chunk。
10. 最终重新计算 Token 数，任何 Chunk 都不得超过
    `recursiveChunkSize`。

### 9.3 重叠规则

优先使用完整尾部原子片段组成重叠区域：

- 从上一 Chunk 尾部向前收集片段；
- 收集后不超过目标 overlap 时继续；
- 如果一个尾部片段本身超过 overlap，取该片段最后 overlap 个 Token；
- overlap 仅作为下一 Chunk 的前缀，不改变新内容顺序；
- 最后一个 Chunk 后不产生仅含 overlap 的额外块。

重叠区域和新内容共同计入 `recursiveChunkSize`。如果加入完整新片段会
超限，应在更细粒度边界切分，最终才使用 Token 硬切。

### 9.4 小块与数量限制

输出继续遵守现有 `minChunkLengthToEmbed` 和 `maxNumChunks`：

- 短于最小嵌入长度的尾部内容优先尝试并入相邻 Chunk；
- 无法在 Token 上限内合并时，沿用现有最小嵌入长度过滤语义；
- 非空输入最终没有可嵌入 Chunk 时，入库失败；
- 输出数量超过 `maxNumChunks` 时抛出明确异常并让文档进入 `FAILED`，
  不静默截断剩余内容。

## 10. Metadata 与稳定 ID

两种策略都必须保留源文档 metadata。`DocumentChunker` 继续统一写入：

- `knowledgeBaseId`
- `documentId`
- `documentName`
- `documentType`
- `documentVersion`
- `documentStatus`
- `checksum`
- `documentAclVersion`
- `pageNumber`（存在时）
- `chunkIndex`

向量 ID 继续由知识库 ID、文档 ID、文档版本和 `chunkIndex` 生成。只要
源文本、持久化策略及部署配置不变，重试结果和 UUID 顺序保持稳定。

递归策略不会跨源 `Document` 合并。PDF 每页的 `pageNumber` 不会因
重叠而混入其他页面。

## 11. 前端交互

知识库上传区域新增切块方式下拉框：

- `Token 切块`：说明“按 Token 控制块大小，兼容现有处理方式”；
- `递归切块`：说明“优先保留段落和句子，并包含少量上下文重叠”。

交互规则：

- 页面初次加载默认 `TOKEN`；
- 用户在当前页面连续上传时保留上一次选择；
- 页面刷新后恢复 `TOKEN`；
- 知识库停用时与角色、文件控件一起禁用；
- 上传按钮原有校验不变；
- 上传成功后清空文件，但不重置当前策略。

文档列表增加策略标签：

- `TOKEN` 显示“Token 切块”；
- `RECURSIVE` 显示“递归切块”。

未知值显示安全的通用标签，不阻断整个文档列表渲染。

## 12. 异常、重试与补偿

- 非法上传参数在创建占位文档前返回 HTTP 400。
- 不合法的递归全局配置导致应用启动失败，并指出具体字段。
- 数据库存在未知策略值时，消费端拒绝静默回退，文档进入 `FAILED`。
- 切块超过最大数量或非空输入没有输出时，文档进入 `FAILED`。
- 切块或向量写入失败继续使用现有补偿流程：
  - 删除本次已经写入的向量；
  - 删除本次解析文本对象；
  - 在新事务中保存失败原因；
  - 保留原始异常，补偿异常作为 suppressed exception。
- Kafka 重试重新读取文档持久化策略，不使用前端状态或进程内临时值。

## 13. 兼容与发布

- 旧客户端不传参数时行为不变。
- 历史文档通过数据库默认值标记为 `TOKEN`。
- 历史文档不自动重新切块，不产生向量变动。
- 需要递归切块的历史内容通过重新上传生成新文档。
- 新增字段必须先通过 Flyway 部署，再由新版本应用读写。
- 不依赖 Alibaba 递归 splitter 的内部实现，避免其分隔符丢失、短片段
  不合并和无 overlap 等行为影响系统。

## 14. 测试设计

### 14.1 递归算法单元测试

- 按段落、换行和中英文标点逐级降级；
- 分隔符保留且内容顺序不变；
- 分隔符按字面量处理，正则特殊字符不改变含义；
- 相邻短片段合并并接近目标大小；
- 相邻 Chunk 存在配置范围内的上下文重叠；
- Chunk 不超过 Token 上限；
- 无分隔符长文本走 Token 硬切；
- 中文、英文、Emoji 和混合文本；
- 空文本、短文本、单个超长句子；
- overlap 为 0 和接近 chunkSize 的合法边界；
- overlap 不小于 chunkSize 时配置校验失败；
- 超过 `maxNumChunks` 时失败且不截断；
- 非空输入无法产生可嵌入 Chunk 时失败。

### 14.2 策略路由测试

- `TOKEN` 调用 Token 策略；
- `RECURSIVE` 调用递归策略；
- 缺少或重复策略 Bean 时初始化失败；
- 两种策略均保留业务 metadata、ACL 和页码；
- 相同输入、版本、策略与配置生成相同 chunkIndex 和 UUID。

### 14.3 上传与异步链路测试

- 未传策略默认持久化为 `TOKEN`；
- 显式 `RECURSIVE` 正确持久化；
- 非法策略在创建占位记录前失败；
- Outbox payload 不重复保存策略；
- Kafka 消费和重试读取文档当前持久化策略；
- 递归失败沿用既有向量和对象存储补偿；
- 文档列表返回策略字段；
- Flyway 迁移为历史数据回填 `TOKEN` 并建立检查约束。

### 14.4 前端测试

- 上传表单默认选择 `TOKEN`；
- 切换为 `RECURSIVE` 后请求包含正确字段；
- 上传成功只清空文件，不重置策略；
- 两种文档策略标签正确显示；
- 未知策略不导致列表渲染失败；
- 知识库停用时策略选择控件被禁用。

### 14.5 回归验证

- 现有 `DocumentChunkerTest` 继续验证 Token 切块；
- 文档上传、Outbox、Kafka 消费、向量批写、ACL、删除和文档预览测试通过；
- 前端构建和现有单元测试通过；
- Maven `smrag-core` 测试通过。

## 15. 验收标准

1. 管理员上传文档时可选择 Token 或递归切块。
2. 不选择时结果与当前 Token 切块一致。
3. 文档记录和列表能够显示实际使用的策略。
4. 异步消费、失败重试和同版本确定性处理使用持久化策略。
5. 递归结果保留分隔符，短片段会合并，相邻块具有配置的 overlap。
6. 递归结果中没有超过配置 Token 上限的 Chunk。
7. PDF 不跨页合并或重叠，页码 metadata 正确。
8. 超过最大 Chunk 数时失败，不丢弃剩余正文。
9. 历史数据、旧客户端和现有 Token 行为保持兼容。
10. 后端、前端和迁移测试全部通过。
