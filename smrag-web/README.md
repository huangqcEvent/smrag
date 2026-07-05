# 私募AI专家前端项目

基于 Vue 3 + Element Plus + Pinia + Vue Router 构建的私募AI专家前端应用。

## 技术栈

- **Vue 3** - 渐进式JavaScript框架
- **Vite** - 下一代前端构建工具
- **Element Plus** - Vue 3组件库
- **Pinia** - Vue状态管理库
- **Vue Router** - Vue.js官方路由
- **Axios** - HTTP客户端

## 项目结构

```
smrag-web/
├── src/
│   ├── api/              # API接口定义
│   │   ├── chat.js       # 聊天相关接口
│   │   ├── knowledge.js  # 知识库管理接口
│   │   ├── product.js    # 产品管理接口
│   │   └── manager.js    # 管理人管理接口
│   ├── stores/           # Pinia状态管理
│   │   ├── chat.js
│   │   ├── knowledge.js
│   │   ├── product.js
│   │   └── manager.js
│   ├── views/            # 页面组件
│   │   ├── Chat.vue      # 智能问答页面
│   │   ├── Knowledge.vue # 知识库管理页面
│   │   ├── Product.vue   # 产品管理页面
│   │   └── Manager.vue   # 管理人管理页面
│   ├── utils/            # 工具函数
│   │   └── request.js    # Axios封装
│   ├── router/           # 路由配置
│   │   └── index.js
│   ├── App.vue           # 根组件
│   └── main.js           # 入口文件
└── vite.config.js        # Vite配置
```

## 后端接口使用清单

### 1. 聊天接口 (ChatController)

| 接口 | 方法 | 路径 | 使用位置 |
|------|------|------|----------|
| 智能问答 | POST | /api/chat/ask | chat.js → chatAsk() |
| 聊天历史 | GET | /api/chat/history/{sessionId} | chat.js → getChatHistory() |

### 2. 知识库接口 (KnowledgeController)

| 接口 | 方法 | 路径 | 使用位置 |
|------|------|------|----------|
| 上传文档 | POST | /api/knowledge/upload | knowledge.js → uploadDocument() |
| 文档列表 | GET | /api/knowledge/list | knowledge.js → getDocumentList() |
| 删除文档 | DELETE | /api/knowledge/{id} | knowledge.js → deleteDocument() |

### 3. 产品接口 (ProductController)

| 接口 | 方法 | 路径 | 使用位置 |
|------|------|------|----------|
| 产品详情 | GET | /api/product/{id} | product.js → getProduct() |
| 搜索产品 | GET | /api/product/search | product.js → searchProducts() |
| 产品列表 | GET | /api/product/list | product.js → getProductList() |

### 4. 管理人接口 (ManagerController)

| 接口 | 方法 | 路径 | 使用位置 |
|------|------|------|----------|
| 管理人详情 | GET | /api/manager/{id} | manager.js → getManager() |
| 搜索管理人 | GET | /api/manager/search | manager.js → searchManagers() |
| 管理人列表 | GET | /api/manager/list | manager.js → getManagerList() |

## 功能特性

### 智能问答 (Chat.vue)
- 支持问题改写、路由分类
- 支持知识问答、产品查询、产品对比、产品推荐、销售话术等多种问答类型
- 显示引用参考资料
- 会话历史记录

### 知识库管理 (Knowledge.vue)
- 文档上传（支持PDF、DOC、DOCX、TXT）
- 文档列表展示
- 文档删除

### 产品管理 (Product.vue)
- 产品列表展示
- 产品名称搜索
- 产品详情查看
- 风险等级展示

### 管理人管理 (Manager.vue)
- 管理人列表展示
- 管理人名称搜索
- 管理人详情查看
- 资质展示

## 安装与运行

```bash
# 安装依赖
npm install

# 开发模式运行
npm run dev

# 生产环境构建
npm run build
```

## 开发环境配置

项目默认配置代理到 `http://localhost:8080`，可在 `vite.config.js` 中修改后端地址。

## 注意事项

- 确保后端服务已启动并运行在8080端口
- 所有接口均已对接完成，确保每个接口都正确使用