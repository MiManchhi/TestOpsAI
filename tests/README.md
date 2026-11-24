# 验证入口

在仓库根目录执行 `scripts/check.ps1` 检查格式、类型、契约及三端测试。`tests/contracts/workspace-live.cjs` 用独立项目、四种身份检验权限、资料审批、复测、连续提问、引用和交接，运行条件见[联合验收](../docs/development/PARALLEL_DEVELOPMENT.md)。

`tests/engineering/container.tests.ps1` 检查服务健康、重新启动和数据卷。业务接口测试使用独立数据库。真实模型输出质量以兼容 API 上的生成、评审和对照原始条款的结果验收。
