# Phase 07 验收报告：正式投保外链与反馈

结论：**PASS**

日期：2026-08-28

分支：`phase/07-application-feedback`

> 真实 UAT/生产投保地址尚未由用户提供。本次只用 `example.test` 保留域名完成成功路径夹具验收；最终 Local 配置已恢复为安全不可跳转状态，未把测试夹具描述为真实 UAT 验收。

## 1. 交付范围

- 九类限定域能力全部开放；快速入口和手工输入“正式投保”都会进入投保前检查，不直接返回或拼接 URL。
- 投保前检查汇总当前客户的规则问答、职业确认、健康确认和资料清单，覆盖 `INCOMPLETE`、`READY`、`STALE`。
- 未完成项提供返回对应步骤的操作；检查不完整时仍要求顾问在外链确认弹层明确确认。
- 后端只根据当前环境配置生成 HTTPS 地址，执行精确域名、端口、凭证、片段、query 键和值校验。
- production 配置拒绝 UAT/test/staging/dev/localhost/保留域名，缺失或冲突时返回 `APPLICATION_URL_NOT_CONFIGURED`，无 UAT 回退。
- 新窗口失败时保留当前会话并支持重试或复制同一条未过期安全地址；过期地址必须重新请求。
- 顾问反馈默认只提交原因和引用 ID；问题与回答摘要必须主动勾选，健康意图始终禁止附带正文。
- 反馈执行字段白名单、枚举、长度、敏感内容、幂等冲突和匿名会话标识保护；响应不回显提交正文。
- 客户切换立即清空内存安全链接和待重试反馈；IndexedDB 不保存正式投保 URL。
- 外链与反馈弹层具备焦点锁定、Escape、焦点归还及 `inert`/`aria-hidden` 背景隔离。

## 2. 自动化门禁

最终执行：

```text
./scripts/phase-gate.sh
```

结果：

- 分支、空白和密钥忽略检查：通过。
- Java 编译：211 个生产源文件通过。
- 后端全量：390 项，0 failure，0 error，2 项按既有条件跳过。
- 前端契约生成一致性：通过。
- TypeScript 类型检查：通过。
- 前端全量：12 个测试文件、47 项测试全部通过。
- Vite 生产构建：61 个模块构建成功。

阶段专项覆盖：

- `ApplicationLinkServiceTest`：HTTPS、精确域名、允许参数、未知/健康参数丢弃、重复参数、非 HTTPS、production/UAT 隔离和相似子域拒绝。
- `FeedbackServiceTest`：默认无正文、健康正文拒绝、手机号/身份证/邮箱/银行卡/URL query/健康词/姓名和地址拦截、匿名会话保存。
- `ApplicationFeedbackContractIntegrationTest`：成功地址、OpenAPI、九类能力、手工输入投保入口、反馈幂等重放、同键异体冲突、敏感字段和未知字段拒绝。
- 前端 store/组件测试：三态检查、过期链接、弹窗拦截、客户切换清理、反馈默认隐私、同标识重试、焦点归还、背景隔离和 axe 结构检查。

## 3. 真实 API 验收

- Local 产品上下文：HTTP 200，知识版本 `knowledge-36743-2026-08-27`，9/9 能力启用。
- Local 未配置地址：HTTP 503，错误码 `APPLICATION_URL_NOT_CONFIGURED`，无候选 URL。
- 手工输入“我想开始正式投保”：HTTP 200，`NEED_INPUT`、`FORMAL_APPLICATION`、`APPLICATION_CHECK`、`RUN_APPLICATION_CHECK`，响应无 URL。
- 保留域名成功夹具：只返回 `productId` 和允许的 `channelType`；未知参数与 `healthFacts` 被丢弃。
- 反馈真实提交：HTTP 200，响应数据仅有 `feedbackId`、`status`、`acceptedAt`。
- 同一反馈重放：第一次 `Idempotency-Replayed: false`，第二次为 `true`，返回相同 `feedbackId`。

## 4. 真实浏览器验收

使用隔离 Chromium、真实 Vite 页面和真实 Spring Boot Local(H2)服务完成：

- 375、390、430、768、1440 px：无横向溢出，9 个能力入口可用，可见主操作无低于 44 px 的触控目标。
- `INCOMPLETE`：显示四项未完成及四个“返回完成此项”，DOM 中无投保 URL。
- `READY`：真实完成规则问答、职业确认、9/9 资料勾选、健康授权/3 项事实确认/三态匹配后显示“检查已完成”。
- `STALE`：临时把测试清单知识版本改旧后显示“资料版本已变化”；随即恢复原版本并复验回到 `READY`。
- 确认弹层：显示批准域名、过期时间、检查状态和未完成项；默认禁用进入按钮，不显示完整 URL。
- 取消：弹层关闭，焦点准确回到“获取正式投保安全地址”。
- 背景隔离：弹层在 `.page-shell` 外渲染，页面设置 `inert` 和 `aria-hidden=true`；关闭后恢复。
- 弹窗拦截：保留当前会话，显示重试/复制入口；复制值仅为后端校验后的地址，页面正文不展示完整 URL。
- 反馈：默认不勾选上下文；成功提交不回显问题；一次可控 503 后使用相同 requestId 与 Idempotency-Key 重试成功。
- 客户 A/B：新建客户后投保面板、弹层和消息均关闭/清空；重新打开不会复用旧链接。
- IndexedDB：`checklists`、`conversations`、`healthFacts`、`messages`、`preferences` 均不含 `apply.example.test`。

## 5. 失败、修复与复跑

验收过程中发现并修复：

1. 手工输入正式投保曾被旧意图门禁判为未开放，已开放第九类意图并增加独立投保检查 handoff。
2. 全量测试首次发现投保检查依赖知识快照、空快照返回 503；已将不需要检索的检查入口与知识检索解耦，失败用例和全量门禁均复跑通过。
3. 真实浏览器发现取消确认后焦点落到页面根节点；已在请求发起时保存触发元素，并避免 `BLOCKED` 状态覆盖返回焦点。
4. Phase 07 弹层背景未完全隔离；已通过 Teleport、`inert` 和 `aria-hidden` 修复并增加测试。
5. 客户切换时内存链接和反馈重试操作可短暂保留；已增加 store 级即时清理和回归测试。

所有失败均在修复后重新执行专项测试、全量门禁和对应真实浏览器路径；最终结果全部通过。

## 6. 安全与范围审计

- `.env.local`、`.local-data/`、`frontend/dist/`、`target/` 均被 Git 忽略，真实 Key 未读取、输出或提交。
- 代码与 Git 文件扫描仅命中环境变量占位符、测试占位值和本地 Ollama 默认值，无真实 Key。
- 应用 URL、反馈摘要和健康事实未写日志；指标只使用低基数意图、原因和丢弃计数。
- 未实现代填、支付、投保状态回传、承保判断、真实 UAT/生产跳转或 Phase 08 标准题集。
- `upstream` 未推送；本阶段只建立本地提交、标签和分支。

## 7. 未决的用户参与项

- 进入真实 UAT 验收前，用户仍需提供批准的 UAT 基础地址及精确允许域名。
- 生产配置和上线前仍需业务、合规审批；本阶段没有启用生产地址或邀请码准入。

## 8. 结论

Phase 07 的实现、异常、安全、隐私、契约、回归、响应式、可访问性、真实 API 和真实浏览器门禁全部通过。允许提交本阶段成果、创建 `phase-07-passed` 标签，并从该提交创建 `phase/08-poc-hardening` 分支。
