# Phase 08 VS Code 运行手册

## 日常开发

1. 用 VS Code 打开仓库根目录，不要只打开 `frontend` 子目录。
2. 左下角 Git 分支应为 `phase/08-poc-hardening`。
3. `Terminal → Run Task` 运行 `Maven: Tests without E2E` 或 `Frontend: Test and Build`。
4. 阶段总检使用 `Phase Gate: Local`；任一失败必须修复并完整重跑。

## 本地页面

- 后端使用 Local(H2) 和正式本地知识快照；产品资料导入需显式开启。
- 前端运行 `Frontend: Dev Server`，地址为 `http://127.0.0.1:5173/xiaoming`。
- `.env.local` 只能保存在仓库根目录且必须保持 Git ignored；不要截图、输出或提交 key。
- 当前不配置正式投保地址时，页面显示安全停止是正确结果。

## 阶段 8 门禁

```bash
./scripts/phase-gate.sh
./mvnw -Dtest=Phase08StandardQuestionAcceptanceTest -Dsurefire.excludedGroups= test
```

标准题命令会读取仓库中的 21 份固定正式资料，验证 40 题并执行普通问答性能采样。

## 真实 DeepSeek E2E

只有在用户明确同意把以下最小内容发送到 DeepSeek 后，才运行 VS Code 任务 `Maven: DeepSeek E2E`：

- 固定问题“发生保险事故后如何报案？”；
- `CLAIMS_STEPS=REPORT,SUBMIT,REVIEW,PAY`；
- `07-投保须知书.pdf` 第 5 页“索赔程序”一条片段。

任务只运行 `XiaomingDeepSeekLiveE2ETest`，不会再触发仓库里的旧车险付费 E2E。不得在终端打印 `.env.local`。

## 失败处理

- 知识导入失败：确认资料目录完整、文件哈希未变化；不得跳过校验或回退旧版本。
- 模型失败：保留输入并使用同一请求标识重试；不得切换为无证据猜测。
- OCR/文件超时：系统在 30 秒停止，改用手工文字；检查临时目录已清理。
- IndexedDB 失败：页面显示本机历史错误；不要新建/切换客户直到保存成功。
- 投保地址缺失：保持功能关闭，等待用户提供批准环境、HTTPS 地址和精确允许域名。
- 测试失败：保留当前阶段分支，不打标签、不建下一阶段分支。
