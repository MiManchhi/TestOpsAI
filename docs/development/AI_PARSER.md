# Robot 文件处理

登记复测时 Web 上传 Robot `output.xml` 和可选日志，提交给 Java 的项目运行接口。Java 将原件按项目和提交者落盘，建立处理任务，通过已签名的 `POST /internal/v1/parsers/robot-output` 调用 Python。Python 校验对象委托、内容哈希和大小，静态解析 XML；Java 保存执行结果和证据位置，并公布处理状态。

聊天里的 .robot、.resource 和粘贴的文本是本人的对话材料。分析源文件、输出草稿均不执行 Robot 关键字、Shell 或设备操作。

[内部接口](../contracts/ai.openapi.json) · [运行记录与引用](../contracts/workspace.openapi.json) · [联合验收](PARALLEL_DEVELOPMENT.md)
