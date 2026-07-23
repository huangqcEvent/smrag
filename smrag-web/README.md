# 基金智库 Web

基金智库的 Vue 3 前端，提供企业知识库管理和可取消的流式 RAG 问答。

## 技术栈

- Vue 3、Vite、Pinia、Vue Router
- Element Plus
- Fetch `ReadableStream`、Axios
- Marked、DOMPurify

## 主要功能

- 上传、查看和删除知识库文档
- RAG 知识问答、产品查询/对比/推荐和合规销售话术
- SSE 增量回答、引用依据和会话历史
- 生成中止、错误恢复和移动端适配

## 聊天接口

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/api/chat/stream` | POST | 默认聊天入口，返回 `text/event-stream` |
| `/api/chat/ask` | POST | 兼容的非流式 JSON 问答 |
| `/api/chat/history/{sessionId}` | GET | 查询已成功持久化的会话历史 |

`/api/chat/stream` 接收与 `/api/chat/ask` 相同的 JSON 请求。事件顺序为：

1. `status`：分析或路由阶段。
2. `metadata`：会话 ID 和路由类型。
3. `delta`：零个或多个回答片段。
4. `citations`：RAG 引用依据，其他路由省略。
5. `done`：完整回答已持久化。

失败时返回脱敏的 `error` 事件且不会发送 `done`。前端通过 `AbortController` 停止生成；未完成的回答不会写入应用会话历史。

## 知识库接口

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/api/knowledge/upload` | POST | 上传文档 |
| `/api/knowledge/list` | GET | 查询文档列表 |
| `/api/knowledge/{id}` | DELETE | 删除文档 |

## 本地运行

```bash
npm install
npm run dev
```

开发服务器默认监听 `http://localhost:3000`，并将 `/api` 代理到 `http://localhost:8080`。

## 验证

```bash
npm run test
npm run build
```
