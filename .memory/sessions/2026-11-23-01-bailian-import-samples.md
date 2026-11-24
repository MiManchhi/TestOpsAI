# 百炼接入排查与资料导入样例

更新：2026-11-23（Asia/Shanghai）。状态：IMPLEMENTED。

<!-- handoff:v1 -->

Task: BAILIAN-SAMPLES-001
Role: integration
Baseline: 本机 main 4c21d1a，含已部署的工作台变更。
Contract: 工作台资料提交、审批及原文定位沿用当前契约。
Scope: 诊断聊天失败、生成可导入的自造项目资料、说明标准模型 API 的配置方式。仅本机工作区交付。

## 现场

<!-- checks -->

本机六服务健康，最近一轮问答的应用错误码为 ANSWER_MODEL_FAILED。容器内配置显示聊天和向量模型分别为 qwen-plus 与 qwen3.7-text-embedding，维数 1024。两个端点仍是 llm-xxx 示例业务空间域名，凭据类型属于 Token Plan。Token Plan 套餐不适用于本应用后端，见[官方使用规范](https://help.aliyun.com/zh/model-studio/token-plan-permissions-and-usage)。即使替换为 Token Plan 专属地址，也不应把该 Key 放入此自建后端。

## 交付

在 samples/meter-acceptance 生成四份虚构 UTF-8 Markdown。资料覆盖标书 ZB-01/ZB-02/ZB-03、技术规范 V1/V2、Robot 关键字说明和验收依据版本切换。AI knowledge-v2 解析器逐份读取成功，每份生成 1 个索引块和 8–11 个原文定位块。导入顺序与三个核对问题写入 [samples README](../../samples/README.md)。

## 交接

<!-- next -->

BLOCKED：正式问答和索引需用户改用百炼标准 API Key（通常 sk- 开头，不是 sk-sp-）并提供真实聊天、向量 API 地址。填入根目录 .env 后执行 scripts/local.ps1 start -SkipBuild，再按样例 README 导入和核对。
