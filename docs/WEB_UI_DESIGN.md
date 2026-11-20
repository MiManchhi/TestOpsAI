# 页面、角色与操作对照

2026-11-19 · 最终版界面设计。点击[全界面样稿](design/index.html)，用顶部“全部界面”定位任意页面，或选角色按真实菜单走。样稿使用内存中的自造数据，刷新会复位。以下链接打开同一份设计的不同状态。API 名对应[工作台 OpenAPI](contracts/workspace.openapi.json)，复用的登录操作见[接口说明](contracts/README.md)。

## 登录与工作台

| 页面                                                                  | 用户实际看到和能做的事                                                                                           | 正式路由 · operationId                                                   |
| --------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------ |
| [登录](design/index.html#login)                                       | 账号、密码、登录，显示 401 或 429 和重试时间；保留已授权的返回地址                                               | /login · login、currentUser、myProjects                                  |
| [无项目](design/index.html#empty) / [账号](design/index.html#account) | 无项目显示联系负责人；账号页列显示名、身份、可访问项目及退出。双重身份可在平台与成员工作区间切换                 | /projects/empty、/account · currentUser、myProjects                      |
| [直接提问](design/index.html#chat)                                    | 左侧新对话、历史与搜索，右侧组合输入框支持文字、粘贴、附件。发送后输入框移至底部；切项目清除旧附件和临时引用     | /p/:p/chat · myHistory、uploadPrivateFile、startTurn                     |
| [判定和证据](design/index.html#answer)                                | 两版结论、实测、日志、条款就近显示；点击来源展开侧栏，显示版本和定位，可进详情。展开逐项、复制回答和出处、继续问 | /p/:p/chat/:c · myMessages、watchTurn、getMyTurn、stopTurn、readCitation |
| [用例草稿](design/index.html#robot)                                   | 默认 Robot 代码块，预期、出处、待确认分开。可复制、要求改写；输出留在本人会话                                    | 同会话路由 · startTurn、watchTurn、readCitation                          |

项目切换器从 myProjects 取成员项目，搜索历史也限制为本人当前项目。保护被撤权的历史摘要、标题、附件和已显示的证据。窄屏历史收成“个人对话”按钮。生成中显示进度、停止和重连；需要澄清的情况直接在聊天里问清是哪一份运行或哪条要求。回车发送、Shift+Enter 换行、中文输入法确认词不触发发送。

## 项目资料与管理资料

| 页面                                     | 布局、操作、完成后去哪                                                                                                  | 正式路由建议 · operationId                                                    |
| ---------------------------------------- | ----------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------- |
| [资料列表](design/index.html#library)    | 名称搜索、状态筛选；表内显示版本、发布/解析两列、读者范围与提供人。本人能看自己的待审与退回。负责人可从待确认结果进审核 | /p/:p/documents · findDocuments                                               |
| [提交或更新](design/index.html#submit)   | 名称、类型、版本、文件与变更说明；提交新版时继承已有资料。提交成功显示待确认，可回列表或原对话                          | /p/:p/documents/new · uploadPrivateFile、submitVersion                        |
| [正文与版本](design/index.html#document) | 左侧原文，右侧元数据、版本与读者；选择历史版本读取指定对象；有权限的人可提交新版、审版本或管理读者                      | /p/:p/documents/:d/versions/:v · getVersion、listVersions、readVersion        |
| [审核](design/index.html#review)         | 负责人先查看或下载原件，批准后才显示排队解析；退回须填写原因，继续显示未启动。退回后上传新版本可再次提交                | /p/:p/versions/:v/review · downloadOriginalVersion、reviewVersion、retryIndex |
| [读者范围](design/index.html#access)     | 负责人选择“项目内共享”或“限定读者”；名单从项目成员中选，保存后立即影响列表、AI 和旧引用                                 | /p/:p/documents/:d/readers · projectMembers、getReaders、setReaders           |
| [验收依据](design/index.html#basis)      | 项目管理显示当前包、内含文件及各自版本、历史区间。勾选一份或多份已批准就绪版本，命名并整包发布                          | /p/:p/settings/bases · listBases、findDocuments、setBasis                     |

## 运行、成员与平台

| 页面                                                                                  | 布局、操作、完成后去哪                                                                                                       | 正式路由建议 · operationId                                                                   |
| ------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------- |
| [运行记录](design/index.html#runs)                                                    | 名称、执行时间、提供人、解析状态；列表进详情或点登记。待解析也能刷新查看                                                     | /p/:p/runs · listProjectRuns                                                                 |
| [登记复测](design/index.html#register)                                                | 名称、执行时间、output.xml、选填日志。可从对话带入本人当前项目的附件；不合格式时保留名称、提示重新选文件。成功回列表或原会话 | /p/:p/runs/new · uploadPrivateFile、registerRun                                              |
| [运行详情](design/index.html#run)                                                     | 原始状态、用例结果、日志定位、问 AI 核对本次运行。任务失败显示原因和重新登记入口；保留提供人及已提交文件                     | /p/:p/runs/:r · runDetails、getResults、getLogLines、startTurn                               |
| [项目设置](design/index.html#settings)                                                | 项目负责人编辑项目名称、说明；页内切换基本信息/成员/依据。保存时校验 revision                                                | /p/:p/settings · readProject、editProject                                                    |
| [项目成员](design/index.html#members)                                                 | 名单与项目角色；按行管理，当前负责人标识；删除成员前展示失去的访问范围                                                       | /p/:p/settings/members · projectMembers、removeMember                                        |
| [添加或调整成员](design/index.html#member)                                            | 选已有账号，设置测试/开发角色。新增、编辑、移出在同一工作流；拒绝移除负责人                                                  | /p/:p/settings/members/edit · memberCandidates、saveMember                                   |
| [平台项目](design/index.html#platform) / [创建](design/index.html#create)             | 平台管理员看项目与负责人、创建项目并选择首位负责人。创建和指派一次成功或一起回滚                                             | /platform/projects、/platform/projects/new · platformProjects、platformUsers、platformCreate |
| [平台项目详情](design/index.html#platformDetail) / [处理状态](design/index.html#jobs) | 基本信息、负责人、项目任务状态；显示任务编号、审核/解析阶段和安全错误码。无正文与业务日志入口                                | /platform/projects/:p、/platform/jobs · platformDetail、platformJobs                         |

## 账号来源与负责人交接

| 页面                                                                                     | 管理动作                                                                                                                                   | 正式路由建议 · operationId                                                                                                                                                                       |
| ---------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| [平台账号](design/index.html#accounts) / [账号管理](design/index.html#accountEdit)       | 平台管理员建普通账号、设临时密码、停用或启用、重设密码。项目角色由负责人设置，平台账号页不授予项目业务权限                                 | /platform/accounts、/platform/accounts/edit · listAccounts、createAccount、setAccountStatus、resetPassword                                                                                       |
| [修改密码](design/index.html#password)                                                   | 临时密码账号首次登录先改密并重新登录。普通用户也可从“我的账号”进入                                                                         | /account/password · myAccount、changePassword                                                                                                                                                    |
| [发起交接](design/index.html#handoverRequest) / [审核或代办](design/index.html#transfer) | 负责人在项目管理中选定接任成员并提交申请，管理员在平台页确认或退回。原负责人已离岗可由管理员填原因代办。生效前后提示并审计受限文档读者变化 | /p/:p/settings/transfer、/platform/projects/:p/owner · projectMembers、requestOwnerTransfer、ownerTransferHistory、eligibleOwners、adminTransferHistory、decideOwnerTransfer、proxyOwnerTransfer |

## 状态与交互验收

| 条件                 | 用户看到什么                                   | 可继续做什么                           |
| -------------------- | ---------------------------------------------- | -------------------------------------- |
| 第一次使用、列表为空 | 可做的单一主操作或“联系项目负责人”             | 新对话、提交资料、登记运行，按角色显示 |
| 上传或解析失败       | 格式/大小或脱敏原因；已输入字段保留            | 换文件，负责人重试可恢复的版本索引     |
| 并发修改             | 提示版本已经变化，保留未提交输入               | 获取新版本、核对差异后再保存           |
| 证据不足或冲突       | 缺了哪项日志或依据、可核对的原文，结论分别标注 | 补附件或继续追问；无验收依据时请人确认 |
| 链接失效或撤权       | 通用不可访问提示，旧页面清除相关内容           | 关闭侧栏或回可见列表                   |
| 会话断线、切出业务页 | 正在重连；草稿消息和返回位置可恢复             | 用原 turnId 续订，成功登记后回原对话   |

样稿优先呈现主要页面与管理动作。正式实现还需覆盖真实数据分页、下载、服务故障、SSE、键盘焦点与撤权清屏；验收以三端执行计划为准。

逐组件交互、键盘与触屏、错误恢复及动画参数见 [Web 执行计划](development/WORKSPACE_WEB.md#交互动效与点击热区)。
