# DashScope Embedding 批量上限修复设计

## 问题

文档切分超过 25 个 chunk 时，Spring AI 默认的 `TokenCountBatchingStrategy`
可能把超过 25 条文本合并到同一次 embedding 请求。DashScope embedding 接口限制
单次最多 25 条文本，因此请求在写入 PGVector 前以
`IllegalArgumentException: The input texts limit 25` 失败。

`spring.ai.vectorstore.pgvector.max-document-batch-size` 只控制向量写入数据库时的
批量大小，不控制 embedding 请求条数，不能解决该问题。

## 设计

提供一个应用级 `BatchingStrategy` Bean，替代 PGVector 自动配置提供的默认策略：

1. 先使用 Spring AI 的 `TokenCountBatchingStrategy` 按 token 上限拆分文档；
2. 再把每个 token 批次按最多 25 个文档继续拆分；
3. 保持文档原始顺序，不丢失、不重复文档。

PGVector 自动配置使用 `@ConditionalOnMissingBean` 创建默认策略，因此应用级 Bean
会被所有 `VectorStore.add(...)` 入口统一使用，包括文档上传、向量重建和批量向量服务。
业务服务不再分别维护分批逻辑。

## 数据流

`chunks -> token 分批 -> 每批最多 25 条 -> DashScope embedding -> PGVector`

权限元数据作为 `Document` 的 metadata 随 chunk 原样传递，分批过程不修改 metadata。

## 错误处理

任意批次调用失败时继续向上抛出原异常。现有文档摄取和重建流程仍负责按全部稳定
vector ID 执行补偿删除并记录失败状态，不在 batching strategy 内重试或吞掉异常。

## 配置

DashScope 的 25 条限制作为明确常量保存在策略类中。本次不增加可调配置，避免配置值
超过供应商硬限制。将来切换 embedding 供应商时，可替换该 Bean 或再引入供应商级配置。

## 测试

- 26 个短文档必须拆为 `25 + 1`；
- token 策略已经拆分的批次仍需分别应用 25 条上限；
- 输出顺序、数量和对象引用与输入一致；
- 运行文档摄取相关测试及 `smrag-core` 全量测试。
