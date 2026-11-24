# TestOps AI

面向企业测试与开发工程师的项目工作台。工程师可以贴入 Robot 用例，结合项目标书评审、补写草稿，也可以核对已登记的复测结果。项目负责人管理资料审核、成员和验收依据。平台管理员管理账号、项目、交接与处理状态。

## 本机启动

在仓库根目录用 PowerShell 执行以下命令。需要已启动的 Docker Desktop（Linux 容器）。

```powershell
.\scripts\local.ps1 start
```

打开 [TestOps AI](http://127.0.0.1:8088)。首次启动生成本机 `.env`、数据库及服务间签名密钥。管理员从平台项目进入，项目成员登录后进入 AI 工作台。新建的本机数据库包含初始化管理员 `admin`，初始密码为 `admin123`。已修改的账号沿用当前密码。

## 配置自己的模型

使用与 Chat Completions 和 Embeddings 接口兼容的服务。在根目录 `.env` 填写以下配置。聊天填 API 基地址，向量填完整端点。维数采用服务实际返回值，同一索引使用相同模型和维数。

```dotenv
TESTOPS_AI_CHAT_BASE_URL=
TESTOPS_AI_CHAT_MODEL=
TESTOPS_AI_CHAT_API_KEY=
TESTOPS_AI_EMBEDDING_URL=
TESTOPS_AI_EMBEDDING_MODEL=
TESTOPS_AI_EMBEDDING_DIMENSIONS=0
TESTOPS_AI_EMBEDDING_API_KEY=
```

例如服务基地址为 `https://your-provider.example/v1` 时，向量 URL 通常为 `https://your-provider.example/v1/embeddings`。地址从 AI 容器访问。凭据填在本机文件。保存后执行 `.\scripts\local.ps1 start -SkipBuild` 重新加载。无模型配置时可以登录、建项目、管理成员和登记运行。需要调用尚未配置服务的操作会提示配置状态；资料页支持配置完成后重试索引。

### 阿里云百炼

本应用由 Python 后端调用模型，并在资料审核后后台索引文档。使用百炼标准模型 API 的业务空间 Key，调用费用按该服务计费。Token Plan 套餐的使用范围限于编程工具或智能体工具中的交互场景，排除了自定义应用后端。[Token Plan 使用范围](https://help.aliyun.com/zh/model-studio/token-plan-permissions-and-usage) · [创建标准 API Key](https://help.aliyun.com/zh/model-studio/get-api-key)

在华北 2（北京）验证时，聊天地址可填 `https://dashscope.aliyuncs.com/compatible-mode/v1`，向量地址可填 `https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings`。也可以使用控制台复制的本业务空间专属 API Host 替换域名。两个地址应与 Key 属于同一地域。若 Key 允许访问这两个模型，可选聊天 `qwen-plus` 和向量 `qwen3.7-text-embedding`，后者默认 1024 维。[地域与接入地址](https://help.aliyun.com/zh/model-studio/base-url) · [Embedding 接口](https://help.aliyun.com/zh/model-studio/embedding-interfaces-compatible-with-openai)

标准 API 的最小配置示例：

```dotenv
TESTOPS_AI_CHAT_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
TESTOPS_AI_CHAT_MODEL=qwen-plus
TESTOPS_AI_CHAT_API_KEY=sk-你的标准百炼APIKey
TESTOPS_AI_EMBEDDING_URL=https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings
TESTOPS_AI_EMBEDDING_MODEL=text-embedding-v4
TESTOPS_AI_EMBEDDING_DIMENSIONS=1024
TESTOPS_AI_EMBEDDING_API_KEY=sk-你的标准百炼APIKey
```

业务空间专属域名按控制台显示的 API Host 替换 `dashscope.aliyuncs.com`。Token Plan 的 `sk-sp-` Key 和 Token Plan 专属域名只留给受支持的编程或智能体工具使用。

可使用[项目资料导入样例](samples/README.md)验证从资料审批、索引到聊天引用原文的流程。

```powershell
.\scripts\local.ps1 status
.\scripts\local.ps1 logs
.\scripts\local.ps1 stop
```

## 开发与验收

- [产品蓝图](docs/PRODUCT_DESIGN.md)和[界面说明](docs/WEB_UI_DESIGN.md)定义用户任务、角色与页面。
- [开发计划](docs/ROADMAP.md)和[联合验收](docs/development/PARALLEL_DEVELOPMENT.md)列出三端交付范围。
- [跨端契约](docs/contracts/README.md)、[架构](docs/ARCHITECTURE.md)和[数据模型](docs/DOMAIN_MODEL.md)定义调用及数据边界。
- [容器指南](docs/development/CONTAINERS.md)说明健康检查、重启和数据卷。
- 本机开发先执行 `. ./scripts/env.ps1` 和 `./scripts/setup.ps1`。完整门禁为 `./scripts/check.ps1`。实测结果与剩余限制见[工程交接](.memory/CURRENT.md)。
