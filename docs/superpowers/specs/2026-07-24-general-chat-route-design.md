# 开放问答路由设计

## 状态

用户已确认：开放性问题由大模型直接回答，不调用搜索、数据库或其他外部工具。本规格用于最终审查，审查通过后再编写实现计划。

## 目标

在现有基金业务路由和 RAG 问答之外增加独立的开放问答能力，使常识解释、内容创作、日常交流等不依赖企业知识库的问题能够得到直接回答，同时保持内部知识问答的有据可查和现有业务工具的最小授权边界。

## 非目标

- 不接入联网搜索、网页浏览或第三方实时数据源。
- 不构建 ReAct 的 Thought/Action/Observation 循环。
- 不向开放问答分支开放 `FundTools` 或知识库检索器。
- 不改变现有同步与 SSE 接口、请求 DTO 或前端协议。
- 不让 RAG 在无检索结果时自动退化成通识回答。
- 不提供模型知识截止时间之后的实时事实保证。

## 方案选择

采用“结构化路由 + 独立 `GENERAL_CHAT` 分支”：

1. `RouteType` 新增 `GENERAL_CHAT`。
2. 路由模型将明确依赖内部文档、制度、上传资料的问题分到 `KNOWLEDGE_QA`。
3. 常识解释、写作、总结用户已提供的文字、头脑风暴、非实时的一般咨询和闲聊分到 `GENERAL_CHAT`。
4. `GeneralChatService` 直接调用现有 Spring AI `ChatClient`，不注册任何工具或 RAG advisor。
5. `AiChatService` 像处理现有业务分支一样统一负责同步/流式编排、空答案校验、持久化和安全错误响应。

当前需求没有可执行的 Action，使用 ReAct 只会引入额外模型轮次、成本和失败状态。独立服务边界允许未来在真正接入搜索工具时，将 `GeneralChatService` 内部替换为受限 Agent，而不改变 API 和路由枚举。

## 路由边界

更新 `structured_route_prompt.txt`，使两个容易混淆的路由具有互斥定义：

- `KNOWLEDGE_QA`：用户要求依据所选知识库、内部文档、公司制度、上传资料、合规材料或其他私有内容回答。
- `GENERAL_CHAT`：无需访问企业私有资料或业务数据库即可回答的开放性问题。

典型分类：

| 用户问题 | 路由 |
| --- | --- |
| “根据知识库说明赎回流程” | `KNOWLEDGE_QA` |
| “这份内部制度对销售有什么要求” | `KNOWLEDGE_QA` |
| “解释一下什么是复利” | `GENERAL_CHAT` |
| “帮我写一段会议开场白” | `GENERAL_CHAT` |
| “你好，你能做什么” | `GENERAL_CHAT` |
| “查询产品 A 的历史收益” | `PRODUCT_QUERY` |

问题改写继续发生在路由之前，用于解析多轮对话中的指代。结构化路由异常、空决策或非法输出仍回退 `KNOWLEDGE_QA`，避免路由故障导致内部问题被当作无依据的通识问答。

## `GeneralChatService`

新增单一职责服务，提供：

```java
String answer(String question, String conversationKey)
Flux<String> stream(String question, String conversationKey)
```

服务使用克隆的 `ChatClient` 和当前 `ChatMemory` 中的用户隔离会话历史。系统提示词要求：

- 使用用户当前语言作答。
- 直接、清晰地回答一般问题。
- 不声称访问了知识库、业务数据库或互联网。
- 对不确定、可能变化或需要实时数据的事实明确说明限制。
- 不虚构来源、引用、产品或基金经理信息。
- 涉及投资时提供一般信息，不作收益承诺或个性化投资保证。

服务不注册 `toolCallbacks`、`RetrievalAugmentationAdvisor` 或其他外部能力。模型调用失败统一包装为 `AiServiceException`；流式和同步入口保持相同语义。

## 编排与数据流

同步流程：

1. 按现有规则验证和规范化请求。
2. 使用用户隔离的 `conversationKey` 改写问题。
3. 结构化路由返回 `GENERAL_CHAT`。
4. `AiChatService.answer(...)` 调用 `GeneralChatService.answer(...)`。
5. 返回不带引用的 `BranchAnswer`。
6. 复用现有空答案校验，将问答写入 `chat_session` 和 `ChatMemory`。
7. 返回 `routeType=GENERAL_CHAT`、空 `citations`、空 `references`。

流式流程保持现有事件顺序：

1. `status: ANALYZING`
2. `metadata: { routeType: "GENERAL_CHAT" }`
3. `status: GENERATING`
4. 零到多个 `delta`
5. `done`

`GENERAL_CHAT` 不发送 `citations` 事件，与现有非 RAG 分支一致。只有聚合答案非空且生成正常结束后才持久化；超时、取消和异常继续走当前统一处理逻辑。

## 兼容性

- `/api/chat/ask` 与 `/api/chat/stream` 的 URL、请求体和响应结构不变。
- 当前请求仍要求选择 1–5 个知识库；`GENERAL_CHAT` 分支忽略该检索范围。本次不扩大到前端交互和 DTO 变更。
- 数据库无需迁移，`chat_session.route_type` 已存字符串，可直接保存 `GENERAL_CHAT`。
- 现有历史记录、引用展示和前端未知路由显示逻辑应保持可用。
- 产品查询、产品对比、产品推荐、经理查询和销售话术的工具白名单不变。

## 错误与安全

- 路由模型不可用时回退到 `KNOWLEDGE_QA`，不直接猜测。
- 开放问答模型不可用、超时或返回空文本时使用现有安全错误，不暴露供应商异常或提示词。
- 开放问答不得生成伪造的内部引用，响应中的引用集合始终为空。
- 模型不得把历史消息中的文字视为系统指令，也不得声称已调用未提供的工具。
- 涉及实时价格、最新政策、当前人物等时，回答必须说明没有实时数据能力。

## 测试策略

按照测试驱动方式覆盖：

1. `RouteDecision` 能保留 `GENERAL_CHAT`，路由失败仍回退 `KNOWLEDGE_QA`。
2. 路由提示词同时包含 `GENERAL_CHAT` 和收窄后的 `KNOWLEDGE_QA` 定义。
3. `GeneralChatService` 同步调用包含系统提示、隔离会话历史和用户问题，且没有工具回调。
4. `GeneralChatService` 流式返回模型文本片段，并把模型异常包装为 `AiServiceException`。
5. `AiChatService.ask()` 对 `GENERAL_CHAT` 调用新服务，不调用知识、业务或销售分支，返回空引用并持久化正确路由。
6. `AiChatService.stream()` 产生 `GENERATING`、`delta` 和 `done`，不产生 `citations`，最终答案只持久化一次。
7. 现有 RAG、业务工具、销售话术、控制器和安全测试继续通过。

## 验收标准

- “解释一下什么是复利”等开放问题被分类为 `GENERAL_CHAT` 并获得直接回答。
- 明确引用内部资料的问题仍走 `KNOWLEDGE_QA`，无证据时不使用通识知识补答。
- 开放问答的同步和 SSE 流式接口均可工作并保留多轮上下文。
- 开放问答不执行向量检索、不调用 `FundTools`、不返回引用。
- 路由、模型或流式处理异常不会暴露内部错误信息。
- `smrag-core` 相关单元测试和完整 Maven 测试通过。
