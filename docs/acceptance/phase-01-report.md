# Phase 01 验收报告：工程骨架与统一契约

验收日期：2026-08-27

验收分支：`phase/01-foundation-contracts`

结论：`PASS`

## 1. 阶段范围结论

本阶段完成 Vue 3 + TypeScript + Vite + Pinia + Vue Router H5 骨架、Spring Boot
`/api/v1/xiaoming` API 骨架、固定产品上下文、统一响应与错误、traceId/requestId、
幂等占位服务、OpenAPI 和前后端共享契约。产品固定为 ID `36743`。

本阶段没有实现知识检索、业务规则、真实保险问答、职业查询、历史会话、资料清单、
健康告知或投保外链；chat 端点只返回明确的“模型能力未开放”响应，不调用 DeepSeek。
POC 保持免登录，正式上线的邀请码准入只保留为后续生产化约束。

## 2. 自动化验收证据

| 检查项 | 结果 | 证据 |
| --- | --- | --- |
| Java 21 编译 | PASS | Maven compile 成功 |
| 后端全量回归 | PASS | 259/259 tests，0 failure，0 error，0 skipped |
| 统一契约与 OpenAPI | PASS | 枚举、schema、错误映射和共享 JSON 契约测试通过 |
| 请求标识与幂等 | PASS | 合法/非法 ID、缺键、重复、异载荷、并发和 TTL 测试通过 |
| 防护过滤器 | PASS | 超长输入、限流、新旧 API 错误形状回归通过 |
| 前端共享契约 | PASS | 机械生成检查无漂移 |
| TypeScript | PASS | `vue-tsc --noEmit` 通过 |
| 前端单元/组件/可访问性 | PASS | Vitest 7/7 通过 |
| 前端生产构建 | PASS | Vite build 成功；JS 97.97 kB（gzip 38.97 kB） |
| 依赖安全 | PASS | npm audit：0 个已知漏洞 |
| 阶段总门禁 | PASS | `scripts/phase-gate.sh` 通过 |

默认 Maven 测试已排除带 `e2e` 标签的真实模型测试；只有显式执行
`./mvnw -Plive-e2e test` 才允许进入付费 E2E，避免常规回归误调用模型。

## 3. 本地运行与接口冒烟

使用独立端口启动本阶段后端与前端进行验证，未影响原有 8080 进程：

- `GET /api/v1/xiaoming/product-context`：HTTP 200，产品 ID、九项未开放能力、
  `POC_NO_LOGIN` 与追踪字段完整。
- `GET /api/v1/xiaoming/health/ready`：HTTP 200，API 与数据库状态为 UP。
- H5 首页：HTTP 200，可经 Vite 代理访问上述接口。
- chat 占位响应为结构化未就绪错误，不包含业务结论，也不触发真实模型。

临时验证进程已停止。机器上原有的 8080 Java 进程仍是旧版本，未被本阶段验收擅自终止；
在 VS Code 中查看新界面前需要停止并重新运行 `Backend: Local`。

## 4. 浏览器与可访问性验收

使用真实 Chromium 检查，所有宽度均无横向溢出、遮挡或布局断裂：

| 视口 | 结果 | 关键检查 |
| --- | --- | --- |
| 375 × 812 | PASS | 单列移动布局、触控目标、禁用输入状态完整 |
| 390 px | PASS | 无溢出，九项能力完整展示 |
| 430 px | PASS | 无溢出，固定产品与准入说明可见 |
| 768 px | PASS | 内容最大宽 720 px，居中显示 |
| 1440 px | PASS | 内容最大宽 720 px，桌面居中，无拉伸断裂 |

语义快照确认标题层级、按钮和文本域可识别；页面没有密码输入框；在线/离线状态不只依赖颜色；
触控目标不小于 44 px；axe 结构性可访问性测试通过。

- [移动端 375 px 截图](phase-01-mobile-375.png)
- [桌面端 1440 px 截图](phase-01-desktop-1440.png)

## 5. 安全、密钥与仓库检查

- `.env.local` 已被 Git 忽略，未跟踪、未暂存、未提交。
- 验收过程未读取、输出或复制用户的 DeepSeek Key。
- 跟踪文件与待提交差异执行了密钥特征扫描，未发现真实 Key。
- 日志、异常和新 API 响应不记录消息正文或客户敏感资料。
- `git diff --check` 通过。
- 工作区中原有的其他未提交修改保持原样，未覆盖、未暂存、未纳入本阶段提交。

一次早期全量回归在安全隔离规则补齐前进入了实时 E2E，并在中止前开始了 1 次
DeepSeek 请求；发现后立即停止。此后已将实时 E2E 改为显式 Maven profile，最终的
259 项后端回归和阶段总门禁均未调用真实模型。

## 6. 非阻塞提醒

- Spring Boot/H2 与 Mockito 输出了未来版本兼容性提示，但不影响本阶段编译、运行或测试。
- 前端依赖树包含一个传递性的 `glob@10.5.0` 弃用提示；npm audit 为 0 漏洞，后续升级上游
  测试工具时一并消除。
- 当前旧 8080 进程需要在 VS Code 中重启，才能加载本阶段代码。

## 7. 阶段退出判断

Phase 01 所有强制门禁均已通过，允许保存本阶段提交、创建 `phase-01-passed` 标签，
并从该提交创建 `phase/02-knowledge-rules-evidence` 分支。Phase 02 不得在本报告的
验收提交之前开始。
