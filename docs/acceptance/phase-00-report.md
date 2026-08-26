# Phase 00 验收报告：开发基线

- 日期：2026-08-27（Asia/Shanghai）
- 阶段分支：`phase/00-development-baseline`
- 实施提交：`5173e275658d2231b86edf874208bd5d2eefafc2`
- 验收提交：本报告所在提交，以标签 `phase-00-passed` 固定
- 基线提交：`9b52371`
- 旧 POC 归档：分支 `archive/vehicle-poc-passwordless-wip`，提交 `72e5512`

## 1. 范围与明确不做

本阶段只建立开发基线：冻结 V2.0 文档和需求优先级，配置 Java 21、VS Code、`local` profile、H2、环境变量、分支/标签流程、阶段门禁和验收规则，并调查既有车险 POC 的可构建性。

本阶段没有实现达尔文 12 号重疾险业务功能；没有调用真实 DeepSeek 对话；没有执行付费、生产、真实投保、真实核保或真实理赔流程；没有实现登录。POC 暂不登录、生产使用邀请码属于后续阶段约束。

## 2. 主要变更

- 将 4 份 V2.0 Word 开发文档复制到 `docs/specs/` 并记录 SHA-256。
- 新增 `AGENTS.md`、开发路线和阶段验收规范。
- 新增 VS Code Java 21、调试、扩展和任务配置。
- 新增 `.env.local.example`，确认真实 `.env.local` 只在本机且不受 Git 跟踪。
- 新增 H2 + BM25 本地配置；忽略 `.local-data/`。
- 新增 `scripts/phase-gate.sh`。
- 将上游仓库改为只允许 fetch，push URL 设为 `DISABLED`，避免误推到原作者仓库。
- 锁定 `io.micrometer:context-propagation:1.1.4`，解决 Embabel 2.0.0-SNAPSHOT 与 Spring Boot 4.0.1 的二进制不兼容。
- 将 Spring profile 从命令行参数改为环境变量，避免 Spring Shell 把参数误解析成命令。

## 3. 验证记录

| 类别 | 命令或检查 | 结果 | 关键证据 |
|---|---|---:|---|
| Java | `/usr/libexec/java_home -V` | PASS | Temurin 21.0.12.1，arm64，用户级安装 |
| 仓库 | 分支、提交、remote 和工作树检查 | PASS | 旧改动已归档；当前为阶段分支；上游 push 已禁用 |
| 文档 | `shasum -a 256 docs/specs/*.docx` | PASS | 4 份文件校验值与 `docs/specs/README.md` 一致 |
| 配置 | `jq empty .vscode/*.json` | PASS | 4 个 JSON 文件均合法 |
| 脚本 | `sh -n scripts/phase-gate.sh` | PASS | Shell 语法合法 |
| 格式 | `git diff --check` | PASS | 无行尾空格或冲突标记 |
| 秘密 | Git 跟踪、忽略规则和密钥模式扫描 | PASS | `.env.local` 被忽略且未跟踪；未发现真实 Key 或私钥 |
| 编译 | `./mvnw -DskipTests compile` | PASS | Maven `BUILD SUCCESS` |
| 定向回归 | 4 个原失败集成测试类 | PASS | 61 项，0 失败，0 错误 |
| 全量门禁 | `./scripts/phase-gate.sh` | PASS | 236 项，0 失败，0 错误；总耗时 01:21 |
| 本地启动 | `SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run` | PASS | Java 21、local profile、H2 文件库、BM25；`BUILD SUCCESS` 后优雅停止 |
| HTTP 冒烟 | `/`、业务 health、Actuator、OpenAPI、Swagger | PASS | 5 个端点全部 HTTP 200 |
| 本地数据 | `.local-data/xiaoming.mv.db` | PASS | 文件创建成功且被 `.gitignore` 忽略 |

## 4. 失败、根因与完整回归

1. 首次全量测试为 179 项，其中 175 项通过、4 项 Spring 上下文加载错误。根因是 Embabel 调用 `ContextRegistry.removeThreadLocalAccessor(Object)`，而解析到的 Micrometer 1.2.0 没有该二进制方法。锁定同时提供 `String` 与 `Object` 重载的 1.1.4 后，先执行 61 项定向回归，再执行完整门禁；最终 236 项全部通过。
2. 首次 `local` 启动完成后退出。根因是 Spring Shell 把 `--spring.profiles.active=local` 当作 Shell 命令。改为 `SPRING_PROFILES_ACTIVE=local` 环境变量并同步修改 `.vscode/launch.json` 后，应用持续运行，5 个端点全部通过，再优雅停止。
3. 提交前检查发现路线文档 3 处行尾空格。清理后重新执行格式和秘密检查，全部通过。

以上失败均已解释、修复并完成全量回归；没有通过跳过测试或放宽断言处理。

## 5. 阶段适用性说明

- 真实 DeepSeek E2E：本阶段不适用。带 `e2e` 标签的真实模型测试由普通门禁排除，避免无业务价值的付费调用；在实现受影响业务的阶段单独执行。
- V2.0 前端浏览器、响应式、契约、故障注入、性能和可访问性：本阶段尚无 V2.0 功能，均不是 Phase 00 必测项；已纳入对应后续阶段退出条件。
- 业务、合规和生产验收：不属于开发基线，由 Phase 08 及生产化流程完成。

## 6. 未解决事项与风险

- 当前没有用户自有的 GitHub/GitLab `origin`。本地分支、提交和标签完整可用，且已禁止向原作者上游误推；但电脑真正休眠后使用 Codex Cloud 继续开发前，仍需用户提供或创建自己的私有远程仓库。这是云端开发的外部前置条件，不影响本地 Phase 00 验收。
- `local` 启动会记录可选 Qdrant、Embedding 和观测导出器不可用的告警；当前明确使用 H2 + BM25，相关组件均自动降级，健康检查为 UP。后续若启用向量检索或观测，必须新增对应阶段测试。

## 7. 最终结论

Phase 00 所有必测项均已执行并通过，没有无法解释的失败或高风险未解决问题。

**结论：PASS**
