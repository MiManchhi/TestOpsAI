# Web 白屏与启动脚本调试（只调试，不改功能）

更新：2026-11-18。状态：VERIFIED（根因定位 + 两处缺陷修复；一项契约不一致未修，待决策）。

## 现象

- 浏览器打开 http://127.0.0.1:8088/ 白屏；容器六服务 healthy、index.html/JS/CSS 均 200。
- `scripts/local.ps1 start -SkipBuild` 在 smoke 阶段抛 ConvertFrom-Json 参数异常。

## 根因与修复

### 1) 白屏：CSP 阻断 Ajv 运行期编译（已修）

- `apps/web/src/app/contracts.ts` 用 `new Ajv(...)` 在浏览器内编译 JSON Schema；Ajv 6 通过 `new Function` 生成校验器。
- nginx `script-src 'self'` 不允许 eval，抛 `EvalError`，bundle 初始化中断，Vue 从未 `mount('#app')` → 白屏。
- 证据：CDP 抓取到 `EvalError ... 'unsafe-eval' is not an allowed source of script`，`#app` innerHTML 长度为 0。
- Vite dev server 无 CSP，所以此前的开发期页面验证发现不了该缺陷；仅容器环境暴露。
- 修复：`infra/nginx/default.conf` 的 script-src 增加 `'unsafe-eval'`，其余指令不变；重新构建 web 镜像。
- 修复后 CDP 复核：无异常，`#app` 渲染 3352 字符，路径 `/login`，2 输入框 2 按钮。
- 遗留：`'unsafe-eval'` 放宽了 CSP。后续应改用构建期预编译校验器（当前 ajv 6.15.0 无 standalone，需评估升级或替换），已列为后续任务。

### 2) 启动脚本：smoke.ps1 JSON 解析（已修）

- `docker compose ps --all --format json` 的输出在 PowerShell 5.1 下按 ANSI 解码，中文/省略号被破坏；且 Compose 可能输出数组或逐行对象。
- 修复：显式临时切换 `[Console]::OutputEncoding` 为 UTF-8，并按首字符是否为 `[` 兼容两种输出形态。
- 验证：`scripts/smoke.ps1` 退出 0；`powershell -NoProfile -File scripts/local.ps1 start -SkipBuild` 退出 0 并打印就绪提示。

## 未修复的联调阻断（需决策）

- `GET /api/v2/projects` 返回裸数组，契约 `ProjectPage` 要求 `{items,total,page,size}`；Web 的 `readContract('ProjectPage')` 校验失败，概览页显示「无法读取项目」。
- 证据：带 token 请求返回 `[{...},{...},{...}]`；页面文案「服务响应或请求格式不符合约定」。
- 影响：登录后可进入工作台外壳，但项目上下文不可用，Robot 用例等依赖项目的页面无法加载。
- 处置选项：后端改为分页响应（与契约一致）或契约/前端调整；属契约级变更，应显式决策后实施。

## 复现命令（已实测）

| 命令                                                             | 结果                               |
| ---------------------------------------------------------------- | ---------------------------------- |
| `docker compose ps`                                              | 六服务 healthy                     |
| `curl -s -o NUL -w "%{http_code}" http://127.0.0.1:8088/`        | 200                                |
| `./scripts/smoke.ps1`                                            | 退出 0，两项 smoke 通过            |
| `powershell -NoProfile -File scripts/local.ps1 start -SkipBuild` | 退出 0，打印 ready                 |
| 无头 Edge + CDP 打开首页                                         | `/login`，无异常，表单渲染         |
| 无头 Edge 提交 admin/admin123                                    | `/overview`，sessionStorage 有令牌 |

## 状态

- 白屏与启动脚本缺陷：VERIFIED 修复（本机容器环境，Windows 10 + PowerShell 5.1 + Docker Desktop Linux 引擎）。
- 未提交：本轮只调试，未创建提交；工作区含 nginx CSP 与 smoke.ps1 两处修改。
- 后续：v2 项目分页契约对齐；CSP 收窄（预编译校验器）；真实 v2 provider 联调。

## 追加：v2 项目列表契约对齐（2026-11-18，用户要求改后端）

### 变更

- `ProjectRepository`：新增 `countAuthorizedProjects`，`findAuthorizedProjects(userId, isAdmin, limit, offset)` 以 `ORDER BY id` 保证分页稳定。
- `ProjectService.listAuthorized(user, page, size)`：返回 `PageResponse<ProjectResponse>`，先用 `PageResponse.validatePagination` 校验（page≥0，1≤size≤100），越界 400 `VALIDATION_ERROR`。
- `ProjectController`：`GET /api/v2/projects` 增加 `page`（默认 0）、`size`（默认 20）查询参数。
- `ProjectResponse.from`：`description` 为 null 时输出空字符串，满足契约 `description: string`（数据库列可为 NULL）。
- `ProjectApiTest`：改为断言分页结构，新增分页正确性、非法分页 400、无描述返回空串三项。

### 验证（真实 MySQL + 容器）

| 命令/检查                                              | 结果                                                                           |
| ------------------------------------------------------ | ------------------------------------------------------------------------------ |
| `mvnw -Dtest=ProjectApiTest test`（带 MYSQL_PASSWORD） | 11 tests, 0 failures                                                           |
| `GET /api/v2/projects` 各分页组合                      | 200，且用与服务端相同的 ajv+schemas.json 校验全部通过（12 条 item，0 invalid） |
| `?size=101` / `?page=-1`                               | 400 `VALIDATION_ERROR`                                                         |
| 浏览器（CDP）登录后 `/overview`                        | 无异常、无 4xx，显示当前项目 Demo Project、角色「项目所有者」                  |
| `scripts/check.ps1`                                    | 退出 0（Java 48 项中 43 项 DB/Redis 跳过；Web 59；Python 64）                  |

### 仍存在的联调缺口（下一片开发，不是返回形状问题）

- `GET /api/v2/projects/{id}/test-suites`、`/test-cases`、`/overview` 均返回 404 NOT_FOUND：Java 只实现了 projects 与 members，Robot 套件/用例/运行域尚未实现。
- 因此「Robot 用例」页会显示读取失败；需要按契约实现 suites/test-cases 查询（依赖 Robot 导入与 V 迁移）。

### 调试副产物

- 无头 Edge 的临时 profile 曾因 `os.tmpdir()` 为空被写入仓库根目录 `undefined/temp/…`，导致 layout 检查失败；已删除该目录并复跑 layout 检查通过。后续脚本应对临时目录做存在性校验，不能依赖未定义的临时路径。
