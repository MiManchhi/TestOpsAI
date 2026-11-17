# 正式开发代码与格式规范

更新：2026-11-17。状态：VERIFIED（规范入口与配套门禁已验收）。适用于人和 AI 的新增、修改代码；规则优先保持清晰、可测试与适度复杂度，不追求形式化层数。

## 1. 通用代码约定

- 按业务域归属，域内先平铺；目录规则以 [目录指南](DIRECTORY_LAYOUT.md) 为准。没有实际复用不建通用层，没有实现不建空目录。
- 名称体现业务含义，避免 data1、temp2、万能 Manager/Utils；类/组件用 PascalCase，TS/Java 函数和变量用 camelCase，Python 函数/模块用 snake_case，常量用有语义的全大写名称。
- 函数只承担一个清楚的职责；优先早返回，避免深层嵌套。重复逻辑先确认业务是否相同，不为两行相似代码引入复杂抽象。
- 注释说明原因、约束和取舍，不逐行复述代码。公共接口、复杂状态机或非直观算法补充契约；TODO 必须写清原因与下一步，不能用 TODO 冒充完成。
- 输入在边界校验；错误不静默吞掉，不返回伪造成功或空结果掩盖失败。对外隐藏内部异常栈，对内使用可追踪、已脱敏的日志。
- 外部 HTTP/模型/文件操作明确超时、大小与重试边界；重试有上限，写操作先考虑幂等。不得将密钥、密码、JWT、完整私有文档写入日志。
- 新依赖先说明用途与兼容性，再锁定版本；保留 lock，禁止为通过门禁而批量关闭 lint、类型检查或测试。

## 2. Java / Spring Boot

- Java 21；包名全小写。启动类保留扫描根，跨域技术归 platform，业务随真实需求归 modules/业务域。测试包对应源码包。
- 生产代码使用构造器注入，明确依赖；不使用字段注入，不将 Spring 容器当全局服务定位器。测试夹具可使用 Spring Test 的字段注入，不把此便利扩展到生产代码。暂不引入 Lombok 等非必要依赖。
- Controller 负责 HTTP 映射和边界校验；业务规则与事务在应用服务；数据库访问封装于所属域内。按需要拆分，不提前创建所有层。
- API 的请求/响应 DTO 与数据库实体分离，不直接暴露密码散列、内部状态或 ORM 对象；简单不可变 DTO 可用 record。
- 认证失败 401、授权失败 403，资源权限在服务端校验；状态改变与数据库事务要有明确边界，不让 AI/前端自行声称的身份替代授权。
- 持久化启用后，使用版本化迁移，不修改已经执行的迁移，不用运行时自动改表代替正式迁移。SQL 参数化，分页/排序字段使用白名单。
- 优先类型明确的返回值，避免原始集合、万能 Map 和 Optional.get() 无检查。时间按 UTC 存储，业务时区在边界转换。
- 格式只由 Spotless / Google Java Format 负责：2 空格缩进，导入/换行以锁定的格式器为准，不再手写另一套 Checkstyle 排版规则。

## 3. Vue 3 / TypeScript

- 使用 Composition API 与 script setup lang="ts"；启动组装归 app，业务页面、请求、组件和测试归 features/业务域。
- 保持 TypeScript strict、noUnused、noUncheckedIndexedAccess。外部 JSON 先当 unknown 校验，避免显式 any 和无依据的类型断言；确有必要的兼容例外须写明原因。
- 组件负责展示与交互状态；复杂可复用逻辑再提为 composable。props/事件显式定义，单向数据流，不直接修改 props。
- HTTP 请求归所属 feature 的 API 模块，异步行为处理 loading、失败与取消/超时；浏览器只访问 Java 业务入口，不持有服务端密钥。
- 组件使用 PascalCase.vue，composable 使用 useXxx.ts，测试使用 *.test.ts；测试可就近放置，不统一搬到根 tests。
- 路由、Pinia 和共享组件在实际业务需要时引入，不为预想功能预建目录。交互保留语义标签、表单标签、键盘操作和必要的状态提示。
- 排版交给根 package.json 的 Prettier 配置；ESLint 管语义和错误，保留零警告。两条与 Prettier 重叠的 Vue 排版规则已明确关闭，不据此关闭其他检查。

## 4. Python / FastAPI

- Python 3.12；保留 src 包布局。main.py 为稳定 ASGI 入口，app.py 组装应用，core 只承载跨域运行基础，业务归 features/业务域。
- 新增函数与公共边界写类型注解，mypy strict；结构化输入使用 Pydantic 校验，不让未经验证的 dict 穿过整个业务链。
- async 路径不直接执行长时间阻塞 IO/CPU 工作；外部客户端与资源有明确生命周期、超时及关闭逻辑。
- LLM/Embedding/工具客户端与业务编排分开，按实际需要提取适配器。Python 不直接操作 Java 业务表，也不绕过 Java 授权。
- Ruff 负责静态规则、导入与格式：4 空格、100 字符行宽。依赖安装使用 uv sync --locked，构建生成物放应用 dist，不提交 .venv、缓存或产物。

## 5. 文件格式、PowerShell 与文档

| 内容                              | 规范/所有者                                     | 检查或修复                                                                                          |
| --------------------------------- | ----------------------------------------------- | --------------------------------------------------------------------------------------------------- |
| Java                              | Spotless / Google Java Format，2 空格           | scripts/maven.ps1 spotless:check / spotless:apply                                                   |
| Vue、TS、JS、JSON、YAML、Markdown | Prettier：2 空格、单引号、分号、100 列          | npm run format:check / npm run format                                                               |
| Python 与 smoke-local.py          | 应用 pyproject 的 Ruff 配置：4 空格、100 列     | uv run --project apps/ai-service ruff check/format --config apps/ai-service/pyproject.toml 指定路径 |
| PowerShell                        | 4 空格，ASCII 脚本标识，显式 UTF-8 读写中文资料 | 解析器语法检查、相关工程脚本回归；当前未引入 PSScriptAnalyzer                                       |
| 文本与 Git                        | UTF-8、末尾换行、默认 LF；cmd/bat 为 CRLF       | .editorconfig 与 .gitattributes；当前不另引入排版工具                                               |

PowerShell 使用参数和 LiteralPath，跨目录操作使用可核对的绝对路径；删除/迁移前确认目标范围。不通过拼接 Shell 字符串处理用户输入，自编脚本不重新定义 HOME 等系统变量；上游 Maven Wrapper 的兼容逻辑保留，不作为自编脚本模板。向 Python 管道发送中文前显式使用 UTF-8。格式化不得重写原始立项方案和历史归档。

原始 PROJECT_BLUEPRINT.md 的 Markdown 双空格硬换行按原文保留：Prettier 已排除此基线，Git 属性只为该文件关闭 blank-at-eol 检查，其他空白与源码检查不受影响，不通过修改原文来消除预期格式差异。

VS Code 可使用仓库推荐的 Prettier、Vue、ESLint、Java、Python、Ruff 扩展；配置只改本项目，不改用户全局设置。保存格式化只是便利功能，命令行与 CI 的结果才是交付依据。

项目设置已为前端/文档选择 Prettier、Python 选择 Ruff 并启用保存格式化，须安装相应推荐扩展才生效。Java 不用编辑器的另一套默认排版覆盖 Google Java Format，保存自动排版关闭，交付前运行 Spotless；PowerShell 仍按明确风格与解析器/回归检查执行。未安装扩展不影响命令行门禁。

## 6. 测试与完成标准

常用命令（仓库根目录，新终端先激活环境）：

```powershell
. ./scripts/env.ps1
npm run format
./scripts/maven.ps1 spotless:apply
uv run --project apps/ai-service ruff format --config apps/ai-service/pyproject.toml apps/ai-service scripts/smoke-local.py
./scripts/check.ps1
```

- 每个实质变更检查正常、异常、权限及适用的边界路径。缺陷修复尽量加入能重现原缺陷的测试；不写只验证常量、复制实现或永远通过的占位测试。
- 单应用测试归应用内，跨应用测试归根 tests。外部模型/服务用可控替身隔离，集成测试使用独立数据，不能依赖公司内网或真实设备。
- 小改先运行相关检查；正式交付执行 scripts/check.ps1。涉及启动入口、代理或配置时额外执行 scripts/smoke-local.ps1；容器变更单独做容器验收，不用本机 smoke 代替。
- 自动门禁检查目录、记忆、格式、lint、类型、测试和构建；命名质量、职责划分、事务/授权语义、注释是否有效仍需人和 AI 审查，不能宣称已全自动保证。
- 未执行、失败、环境限制和遗留问题如实记录到 session/CURRENT/EVIDENCE。提交遵守 [Conventional Commits 约定](CONTRIBUTING.md)，未经用户授权不提交或推送。

正式开发前读 [AGENTS](../../AGENTS.md)、[目录规范](DIRECTORY_LAYOUT.md)、本文件和当前记忆；接口/架构变化同步契约、ADR 和测试。

数据库与 API 命名：表、列使用 snake_case，SQL 迁移采用 Flyway 的 V1__description.sql 递增版本约定，已执行版本不修改；JSON 字段用 camelCase，REST 路径使用既有 API 版本与复数资源约定。主键策略、具体字段、分页和错误码在 M1 对照契约冻结，不能仅凭本规范虚构已存在的 schema。
