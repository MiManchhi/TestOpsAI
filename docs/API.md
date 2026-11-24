# 业务与服务接口

Web 使用同源 Java 网关。登录和当前身份沿用 [认证接口](contracts/public.openapi.json)。项目、成员、文件版本、验收依据、运行、私有会话、引用和平台操作在[工作台契约](contracts/workspace.openapi.json)中定义。

Java 根据登录用户、项目成员关系、文档读者名单及当前版本状态授权。资料提交、审核、角色交接等写操作遵循工作台契约规定的版本与幂等键。聊天流支持恢复和显式取消，读取历史、来源原文和日志时重新授权。

Python 通过[内部解析与索引接口](contracts/ai.openapi.json)接收任务，通过[对象读取契约](contracts/tools.openapi.json)取得获准的文件。回答与索引块的签名回调见[内部服务契约说明](contracts/README.md)。公开接口与模型的密钥分别管理。
