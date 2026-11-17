# 工程化目录实施

更新：2026-11-16。状态：VERIFIED。

用户确认：三端内部一起整理，按业务域组织；工具保留 .tools；修复基础阻塞并完成三端检查、构建、本机健康验收。不得开发业务或提交/推送。

## 实施计划

- [x] 核对工作区、读取规则和记忆；保存迁移前文件与本机工具清单。
- [x] 收敛根配置，整理 .tools/toolchains 和 .tools/cache，验证后清理遗留。
- [x] 迁移 Java platform/security、Vue app/features/health、Python app/core/features/health，保持接口与启动入口。
- [x] 完善结构检查与回归测试，修复 Wrapper、Vue 格式冲突、Python 依赖锁与工具入口。
- [x] 执行三端全量门禁、构建、本机健康及 Compose 配置检查。
- [x] 更新 ADR、目录指南、模块/当前记忆和真实证据，完成逐项审计。

备份与迁移清单仅本机保存：.runtime/migrations/2026-11-16-engineering-layout。原立项方案原文保留。无初始 Git 提交，不能仅依靠 git diff 回滚。

## 真实过程

- 开始时有 14 根文件；Java/Node/uv/标准 CPython 均已有本机二进制，但 FastAPI 尚未安装。
- 结构整理不能只改说明；将同步入口、导入、测试、镜像、CI 和开发脚本。

## 实际变更与迁移清单

- 根 Prettier 设置并入 package.json，删独立配置；根源码文件 14 → 13。npm 固定 11.6.4，Node 版本由 .nvmrc 提供，Java/TS/Python 其余直接依赖沿用原选择，新增 Python 全量 uv.lock。
- Java：config/SecurityConfiguration.java → platform/security；BoundaryTest → 对应测试包。启动类位置不变。Wrapper 模板版本占位符归实，Windows 从脚本位置找 .mvn，尾点解决含空格路径的参数引号问题。
- Vue：main.ts/App.vue/style.css → src/app；api/health 请求与测试 → features/health/api.ts 与 api.test.ts，提取真实 HealthStatus 组件；index.html 与导入同步。只关闭两条重复排版规则，保留零警告。
- Python：工厂提取 app.py，config.py → core/settings.py，健康路由归 features/health/router.py；main.py 保留兼容 ASGI 和 create_app 导出。测试导入更新，接口不变。
- .tools 在用工具迁入 toolchains/java、node、npm、uv、python，缓存归 cache/npm、uv、ruff、mypy；env 增加稳定 npm shim 与明确 Java/Python 路径。根生成缓存迁走，不扩大白名单。
- 新增 doctor、maven、smoke-local 入口，check 串联结构回归与 Python 构建；CI 同步版本/检查，尚未推送运行。
- 新增 tests/engineering 的 9 项正反例；增强现有结构检查的必需入口和应用归属检查。源文件归属不是无限白名单，深层业务语义仍需人工审查。
- 更新 ADR-0002、目录指南、README、规范、模块记忆、CURRENT、EVIDENCE。用户补充“不要过度复杂”已写入 AGENTS/开发指南；未创建空业务目录。

## 真实验证（2026-11-16 / Asia/Shanghai）

执行目录为仓库根；无 Git commit SHA，适用本轮未提交工作区。

| 命令/检查                              | 结果                                                                                                                                                             |
| -------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| scripts/doctor.ps1                     | Node 22.23.5、npm 11.6.4、JDK 21、标准 CPython 3.12.16、uv 可用                                                                                                  |
| scripts/setup.ps1                      | 退出 0；npm ci、uv sync --locked、Java test-compile 成功；本机 .env 随机值未输出                                                                                 |
| scripts/check.ps1                      | 退出 0；目录 9 项回归、格式、Vue 类型/lint/3 测试/构建、Java 2 测试/打包、Python Ruff/mypy/1 接口测试/构建全部通过；smoke Python 脚本的 lint/格式/严格类型亦通过 |
| scripts/maven.ps1 -B -ntp clean verify | 退出 0；从零编译与 2 项测试、格式、打包通过，避免旧 class 掩盖迁移问题                                                                                           |
| scripts/smoke-local.ps1                | 退出 0；动态空闲端口启动三端，Java status/业务 401、AI live/ready、网页入口、Vite 到 Java 代理通过；自己创建的子进程均停止                                       |
| docker compose config --quiet          | 退出 0；本机随机凭据不为空，不输出真实值；未构建或部署容器                                                                                                       |
| scripts/memory.ps1 -Mode Check         | 14 份活动记录通过，归档不作为当前事实                                                                                                                            |
| 文档相对链接/UTF-8/原方案 SHA-256 检查 | 通过；原始方案字节未变；中文页面文本正常                                                                                                                         |
| npm audit --json                       | 1 条 minimatch 中等级公告关联 7 个开发依赖包，0 high/critical；未执行强制跨版本降级建议                                                                          |

## 问题与处理

- Wrapper 初次固定脚本路径时尾反斜杠影响 Java 参数，引入末尾点后复验成功。
- PowerShell 默认管道编码影响 Python 中的中文替换，改 UTF-8 并恢复中文页面；内容检查通过。
- smoke 首次用默认端口，8080 已由其他用户进程占用；改动态端口及进程内代理配置，未停止其他程序。
- smoke 最初对 Java health 完整对象过度约束，实际可含非敏感 groups；改验证 status=UP 且不含 components，保持端点语义。
- smoke 新脚本的 Ruff 行宽与返回类型问题已修复，统一显式使用应用 Ruff 配置。
- 自动审批拒绝递归删除（仅 blocked by policy），改成可回退的 Move-Item，60 项旧遗留已移出 .tools。归档仍占磁盘，未声称已释放；无审批绕过。

## 限制与交接

本轮目标完成：适度工程化目录、工具归档、引用与脚本修复、基础构建/本机链路验收。没有提交/推送，容器部署与远端 CI 未执行。npm 开发依赖公告、pytest TestClient 和 Mockito agent 的升级提示保留为独立维护事项；没有新增 JWT/RAG 等业务。

所有迁移前文件清单、备份和旧工具归档仅在忽略的 .runtime/migrations/2026-11-16-engineering-layout。下一步从 CURRENT 恢复；新增功能按域先平铺，真实职责增加后再拆目录。
