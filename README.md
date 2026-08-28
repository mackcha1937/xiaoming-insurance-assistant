# 小明智能投保助手

面向保险顾问的单产品限定域智能投保助手。首期产品为复星联合达尔文 12 号重大疾病保险（互联网），产品 ID `36743`。

当前状态：**Phase 07 — 正式投保外链与反馈（开发中）**。Phase 06 已通过并封存；
当前只在安全配置、投保前确认、脱敏反馈和受限埋点范围内继续开发。

## 开发依据

- `AGENTS.md`：阶段门禁、测试和安全规则；
- `docs/development-roadmap.md`：阶段范围和退出条件；
- `docs/specs/`：V2.0 产品、前端、后端和立项原始文档；
- `docs/acceptance/`：每阶段验收报告。

## 本地准备

1. 使用 VS Code 打开仓库根目录。
2. 安装工作区推荐扩展。
3. 将 `.env.local.example` 复制为 `.env.local`。
4. 由用户在 `.env.local` 填写 `DEEPSEEK_API_KEY`；禁止提交该文件。
5. 运行 VS Code 任务 `Phase Gate: Local`，或使用终端：

```bash
./scripts/phase-gate.sh
```

本机 Java 版本固定为 Temurin 21，VS Code 已通过 `.vscode/settings.json` 指向用户级 JDK。

产品资料自动导入默认关闭。只在本机确认资料存在时显式设置：

```bash
XIAOMING_KNOWLEDGE_IMPORT_ENABLED=true
XIAOMING_KNOWLEDGE_SOURCE_DIRECTORY=docs/source-materials/product-36743/raw
XIAOMING_SPREADSHEET_SCAN_MODE=STRUCTURE_ONLY
```

`FULL_CELL_SCAN` 仅用于阶段验收或离线维护；超大现金价值表会采用流式读取。原始资料、分享参数
和实际资料清单均被 Git 忽略。

## POC 边界

POC 阶段免登录，浏览器本地保存会话并严格隔离不同客户。正式上线时增加邀请码准入。

产品只覆盖九类白名单能力，不提供真实报价、支付、保单查询、核保结论、医学诊断或真实理赔结论。关键回答必须带正式资料依据；证据不足或冲突时停止给出确定结论。

## 分支规则

- 当前阶段：`phase/07-application-feedback`
- 旧车险免登录归档：`archive/vehicle-poc-passwordless-wip`
- 第三方 `upstream` 只读，禁止推送；
- 阶段只有在验收报告为 PASS 后才能打 `phase-NN-passed` 标签并创建下一阶段分支。
