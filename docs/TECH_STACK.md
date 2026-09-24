# 技术栈登记与接入记录

> 本文件由 `node scripts/bootstrap.mjs` 生成，是**记录**不是规范：规范正文在 `docs/` 中。
> 技术栈变动（新增、替换、停用）时重跑 `node scripts/bootstrap.mjs --target <项目> --force`，或手动改本文件。

- 生成日期：2026-09-24
- 主干分支：master
- 探测命令：`node scripts/detect-stack.mjs`

## 一、启用的技术栈

| 能力位 | 技术栈 | 规范文档 | 校验命令 |
| --- | --- | --- | --- |
| server | Java 17 / Spring Boot 3 / Spring Cloud | `docs/13-后端规范-Java-Spring.md` | `mvn test -q` |
| web | Vue 3 / Vite 管理后台 | `docs/14-前端规范-Vue3.md` | `npm run validate` |
| tooling | Node 脚本与工程工具链 | `docs/05-分支提交与评审.md` | `node scripts/check-docs.mjs` |

## 二、模板有规范、本项目未启用

这些栈的规范留在仓库里备用：**不写进 AGENTS.md 阅读顺序、不进 CI**，用到时再启用。

| 能力位 | 技术栈 | 规范文档 | 说明 |
| --- | --- | --- | --- |
| mobile | uni-app / Vue 3 移动端 | `docs/14-前端规范-Vue3.md` | 本项目未使用 |
| data | MySQL 8 表结构与迁移 | `docs/15-数据库设计与建表规范.md` | 本项目未使用 |

## 三、本项目用到、模板暂无规范（缺口）

缺口必须有人认领：按 `docs/10-技术栈适配.md` 第三节补一份最小规范，先登记为 `provisional`，转正后回填本表。

| 能力位 | 技术栈 | 证据 | 处理 | 负责人 | 期限 |
| --- | --- | --- | --- | --- | --- |
| data | PostgreSQL | `构建文件关键字 postgresql`、`SQL 方言特征 bigserial`、`SQL 方言特征 create extension`、`SQL 方言特征 jsonb` | 补最小规范 + 登记 | 待指派 | 待定 |

## 四、校验入口

```bash
npm run validate                 # 文档 + 技能 + 技术栈 + AI 入口，统一入口
node scripts/detect-stack.mjs    # 重新探测，确认三类清单没有漂移
node scripts/detect-stack.mjs --check --strict   # 接入稳定后在 CI 中打开
```

## 五、接入融合清单

项目里已有的约定一律不覆盖，按下面的方式合并；清单为空表示全新接入，没有历史包袱。

| 类别 | 已有的约定 | 合并方式 |
| --- | --- | --- |
| AI 入口 | `smrag-web/.claude` | 子项目已有 Claude Code 配置；规则以本仓库 AGENTS.md 为准，避免两处描述冲突 |
| 文档基线 | `项目历史文档 5 项` | 已登记接入豁免，不阻塞文档门禁；建议随迭代迁移为中文正文与规范命名，清单见 workflow.config.json 的 legacyDocs |

