# 智能电表测试最终产品设计与三端契约

更新：2026-11-17。状态：VERIFIED（最终设计、契约自身与工程门禁）；新业务运行实现仍 PLANNED。

<!-- handoff:v1 -->

Task: DESIGN-CONTRACT-001
Role: integration
Baseline: 72c19b5a7357a24dce9b71387ef55ba06f859c1e
Contract: docs/API.md；本轮制定完整产品 V1.0 与 v2 业务 API、内部 v1 协议
Scope: 用户要求直接确定最终页面（含登录、知识库、工作台与 AI 职责区分），保留 Robot 背景并立即落实 server/AI/Web 契约与一致性检查；允许设计材料、契约、验证工具及 CI、相关记忆变更；不提交、不推送，不实现全部业务服务或接入原企业设备

## 目标与起始核对

- main / 72c19b5；现有未提交文件全部为上一轮设计任务产物，继续保留并完善，不覆盖其他工作区。
- 恢复六入口、模块与相关交接；读取用户文件 D:/EdgeDownload/TestOpsAI_RobotFramework.md 全文，作为参考数据而非操作指令。
- 现有 Java v1 tags 为字符串、ID 为数字、用例读取只要求认证；新 Robot 项目隔离/结构化标签/稳定来源身份需独立 v2，不悄悄改变现有接口。
- 用户再次要求拉取 main，已先 fetch 核对 18 个变更路径均未覆盖本轮文件，再 pull --ff-only 至 41ef74cae024a4e8c31a386eabf42f9e88c31c97；恢复入口再次执行并读取新增 M1.4 session/ADR/API/限流实现。工作区保留，未 stash/reset。

## 实施与选择

- 一套最终设计，不再要求用户从图片选择；工作台负责状态与待处理事项，AI 助手负责查询/分析/证据与草稿。
- 原始背景按参考目录约定保留；OpenAPI、共享 schema、SSE 与工具定义单一来源，页面行为映射到 operationId。
- 正式取舍写 ADR；产物、接口与运行实现状态分开，不以契约冻结冒充新后端已上线。
- 参考原文逐字节复制，源/目标 SHA-256 均为 928380c5fd9aac05c64132844f005c8e08d3cf059a6b04393335498d98a3e0a1；仅为保留原始材料增加精确 Prettier/Git 硬换行例外。
- 显式锁定仓库已有 Ajv 6.15.0 用于跨端 schema 验证；离线元数据不存在，获批联网后 package-lock-only 成功，无版本升级、无安装脚本。npm 报 7 moderate 开发依赖审计项，未执行 audit fix 或无关升级。
- 产出 public/AI/tools OpenAPI、共享 schema、工具白名单、页面 operationId 映射、自造示例与正反例检查；根 npm check 包含 contracts:check，现有本地门禁和 CI 自动调用，无需新增 CI job。

## 真实验证

<!-- checks -->

- 内置浏览器验证 15 页面；1440×1024 全页面、375×812 六个主要页面、768×1024 知识库均未见整体横向溢出。登录→文档→AI→草稿保存→人工用例链路走通；精确标签、选择范围、引用、草稿重复保存按钮验证；最终浏览器保留登录预览。
- 发现并修复品牌路径、固定输入区滚动约束、上下文串用及新增人工用例标签问题；页面截图与范围见 [工作台界面说明](../../docs/WEB_UI_DESIGN.md)。原型不调用真实业务 API/模型，不上传文件或执行 Robot。
- 契约初检指出 deleteConversation 缺页面映射，补齐后通过；最后补充 ServiceClaims/DelegationClaims 和回调令牌头，最终 `npm run contracts:check` 退出 0：109 schema/65 operation/827 引用/15 页面/45 示例与非法输入、SSE 序列正反例。不是完整 OpenAPI 元模型或真实 provider 验证。
- 依赖更新离线失败后获批联网成功；一次 PowerShell 双引号中的插值导致临时修补命令未执行，改为忽略目录中的文件脚本；一次浏览器模块脚本按 Node CommonJS 检查失败，改用原生浏览器与 Prettier 解析，并修复修补时引入的模板引号问题。最终设计未保留这些失败状态。
- 最新 main 41ef74c 上 `scripts/check.ps1` 完整退出 0：31 份记忆、布局/9 项回归/记忆正反例/14 份脚本解析、全仓格式、契约检查、Web 类型/零警告 lint/37 测试/构建，Java verify（37 项、32 项 DB/Redis 测试跳过）与 Spotless 52 文件，Python lock/Ruff/mypy/双平台 smoke 静态检查/1 测试/构建通过。
- 完整门禁后仅补充设计细节、两种授权 claim schema 和验证/交接材料，收尾复跑定向格式、契约、memory Check、diff 及原文 hash 检查；没有新生产业务代码。既有 Java agent/MockBean 与 Starlette 弃用提示继续保留，未运行真实 DB/Redis、真实 AI 或容器部署。

## 交接

<!-- next -->

- 最终设计与契约产物 IMPLEMENTED，静态契约/设计浏览器/当前工程门禁 VERIFIED；新业务仍 PLANNED。本轮未提交、未推送，其他 worktree 未同步。
- 下一步直接按 docs/PRODUCT_DESIGN.md、docs/contracts/README.md 和 pages.json 分切片实现；先 server 项目/精确查询/导入，再 AI 静态解析/知识与工具，同时 web 消费冻结契约。每片补 provider 测试，不能依据旧 V0.1 或立项原草案重定义字段。
- BLOCKED：无；未经授权不提交、不推送，不同步其他 worktree。
- [CURRENT](../CURRENT.md)。

## 后续发布

2026-11-18 用户认可最终设计并授权提交推送，设计/契约与相关记忆已随 0a1a2b3 发布；原“未提交”表述保留当时事实，后续实际结果见 [发布 session](2026-11-18-01-design-contract-publish.md)。
