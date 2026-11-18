const app = document.querySelector('#app');
const dialog = document.querySelector('#dialog');
const fixtures = await fetch('../contracts/examples.json').then((r) => r.json());
const mappings = await fetch('../contracts/pages.json').then((r) => r.json());
const all = (name) => fixtures.instances.filter((x) => x.schema === name).map((x) => x.data);
const first = (name) => all(name)[0];
const state = {
  selected: new Set(['1', '2']),
  query: '',
  module: '',
  suite: '',
  tag: '',
  result: '',
  kbQuery: '',
  kbCategory: '',
  source: innerWidth >= 1280,
  tab: 'documents',
  chat: '',
  extraQuestions: [],
  saved: false,
  menu: false,
  failedLogin: false,
};
state.context = structuredClone(first('MessageCreate').context.testContext);
state.analysisMode = 'RUN';
const esc = (x) =>
  String(x ?? '').replace(
    /[&<>"']/g,
    (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c],
  );
const tags = (items) => items.map((x) => `<span class="tag">${esc(x)}</span>`).join('');
const badge = (text, color = '') => `<span class="status ${color}">${esc(text)}</span>`;
const status = (v) =>
  ({
    READY: badge('可问答', 'green'),
    FAILED: badge('处理失败', 'red'),
    PROCESSING: badge('处理中', 'amber'),
    QUEUED: badge('排队中', 'amber'),
    PASS: badge('通过', 'green'),
    FAIL: badge('失败', 'red'),
    SKIP: badge('跳过', 'amber'),
    ACTIVE: badge('启用', 'green'),
    ARCHIVED: badge('归档'),
    SUCCEEDED: badge('导入成功', 'green'),
  })[v] || badge(v);
const link = (route, label, css = '') => `<a href="#/${route}" class="${css}">${label}</a>`;
const button = (action, label, css = '', operation = '') =>
  `<button type="button" data-action="${action}" ${operation ? `data-operation="${operation}"` : ''} class="${css}">${label}</button>`;
const heading = (title, description, actions = '') =>
  `<div class="heading"><div><div class="eyebrow">METER TESTING / 测试协作</div><h1>${title}</h1><p>${description}</p></div><div class="actions">${actions}</div></div>`;
const table = (headers, rows) =>
  `<div class="table-scroll"><table><thead><tr>${headers.map((x) => `<th>${x}</th>`).join('')}</tr></thead><tbody>${rows.join('') || `<tr><td colspan="${headers.length}"><div class="empty">没有符合条件的记录，请调整筛选。</div></td></tr>`}</tbody></table></div>`;
const row = (...cells) => `<tr>${cells.map((x) => `<td>${x}</td>`).join('')}</tr>`;
const pager = (count) =>
  `<div class="pagination"><span>共 ${count} 条 · 第 1 页</span><span>每页 20 条</span></div>`;
const projectContext = () =>
  `<div class="context-bar"><strong>当前测试对象</strong><span>${esc(state.context.deviceType === 'THREE_PHASE' ? '三相智能电表' : '单相智能电表')} / ${esc(state.context.productModel)}</span><span>固件 ${esc(state.context.firmwareVersion)}</span><span>模拟规约 ${esc(state.context.protocolVersion)}</span>${button('context', '查看与调整', 'text-button')}</div>`;
const page = () => location.hash.replace(/^#\//, '').split('?')[0] || 'overview';
const navs = [
  ['overview', '工作台', ''],
  ['assistant', 'AI 助手', ''],
  ['knowledge', '知识库', ''],
  ['test-cases', 'Robot 用例', ''],
  ['runs', '运行记录', ''],
  ['imports', '数据导入', ''],
];
function shell(content, active) {
  return `<div class="shell ${state.menu ? 'menu-open' : ''}"><aside class="sidebar" aria-label="主导航"><div class="brand"><img src="brand.svg" alt=""/><span>TestOps <b>AI</b></span></div><small>智能电表测试协作</small><nav>${navs.map(([id, title]) => link(id, title, active === id ? 'active' : '')).join('')}</nav><nav class="nav-group">${link('project-settings', '项目与成员', active === 'project-settings' ? 'active' : '')}${link('audit', '审计记录', active === 'audit' ? 'active' : '')}</nav><div class="sidebar-bottom"><div class="muted" style="font-size:11px">独立 Robot 模拟测试域</div><div class="profile"><div class="avatar">测</div><div>${link('account', '演示测试工程师')}<div class="muted" style="font-size:11px">项目所有者</div></div></div></div></aside><main class="main"><header class="topbar"><div class="actions">${button('menu', '导航', 'mobile-menu')}<select aria-label="当前项目"><option>智能电表功能回归</option></select></div><span class="preview-note">最终设计 V1.0<span class="wide-only"> · </span>自造数据演示</span></header>${content}</main></div>`;
}
function login() {
  return `<main class="login" data-page="login"><section class="login-story"><div class="brand"><img src="brand.svg" alt=""/><span>TestOps <b>AI</b></span></div><h1>让每一次测试，<br/>都有据可查。</h1><p>连接智能电表测试规范、Robot 用例与运行结果。用事实定位问题，用知识支持判断。</p><div class="flow-list"><div><strong>测试事实</strong><span>套件 · 用例 · 运行结果</span></div><div><strong>知识依据</strong><span>规约 · 测试方法 · 故障手册</span></div><div><strong>AI 协作</strong><span>精确查询 · 有源分析 · 建议草稿</span></div></div><footer>独立模拟测试域，不连接企业测试台体。<br/>TestOps AI · 智能电表测试协作</footer></section><section class="login-form-wrap"><form class="login-form" id="login-form" data-operation="login"><span class="eyebrow">WELCOME BACK</span><h1>登录测试协作空间</h1><p class="muted">从项目、用例和知识开始今天的工作。</p><div class="field"><label for="username">账号</label><input id="username" name="username" autocomplete="username" required value="demo.engineer"/></div><div class="field"><label for="password">密码</label><input id="password" name="password" type="password" autocomplete="current-password" required placeholder="输入密码，体验登录流程"/></div><div class="actions" style="justify-content:space-between;margin-top:10px"><label style="font-size:12px"><input type="checkbox" id="show-password"/> 显示密码</label><span class="muted" style="font-size:11px">会话仅保留在当前标签页</span></div><p class="login-error" role="alert">${state.failedLogin ? '账号或密码不正确，请检查后重试。' : ''}</p><button class="primary" type="submit">登录工作台</button><p class="fineprint">设计演示：填写任意非空密码即可进入；此页面不调用真实认证或保存密码。</p><div class="actions" style="margin-top:20px">${button('login-error', '预览登录错误', 'text-button')}${button('rate-limit', '预览限流反馈', 'text-button')}</div></form></section></main>`;
}
function overview() {
  const v = first('Overview');
  return `<div class="page" data-page="overview">${heading('测试工作台', '掌握当前回归结果与资料状态，继续需要处理的工作。', link('imports', '导入测试数据', 'primary button-link'))}${projectContext()}<div class="stats"><div class="stat"><p>已索引用例</p><div class="number">${v.catalogCaseCount}</div>${link('test-cases', '查看套件与用例 →')}</div><div class="stat"><p>最近一次运行</p><div class="number">4 <span style="font-size:14px">/ 6 通过</span></div><small>1 失败 · 1 跳过</small></div><div class="stat"><p>可问答文档</p><div class="number">3</div>${link('knowledge', '查看知识库 →')}</div><div class="stat"><p>资料待处理</p><div class="number" style="color:var(--amber)">1</div><small>1 份文档解析失败</small></div></div><div class="two-col"><section class="surface"><div class="section-head"><h2>最近运行</h2>${link('runs', '全部记录 →')}</div><div class="section-body"><div class="pill-line"><div><h3>${link('run-detail', 'DEMO-M1 功能回归 · 11/17')}</h3><p class="muted" style="font-size:12px">导入的 Robot 运行结果 · 2026-11-17 14:00</p></div>${badge('有失败项', 'red')}</div><div class="meta-line"><span>PASS 4</span><span>FAIL 1</span><span>SKIP 1</span><span>耗时 2分14秒</span></div><div class="insight"><strong>费率切换后状态核对</strong><p>运行记录显示模拟状态断言不一致，需要结合日志与适用规范核查。</p><div class="actions">${link('run-detail', '查看失败详情 →')}${button('ask-run', '让 AI 分析这次运行', '', 'createConversation')}</div></div></div></section><section class="surface"><div class="section-head"><h2>需要处理</h2>${badge('1 项', 'amber')}</div><div class="section-body"><div class="list-row"><div><strong>时钟恢复场景补充</strong><p>解析失败 · 示例文件内容为空</p></div>${link('knowledge-detail', '处理 →')}</div><div class="list-row"><div><strong>检查测试上下文</strong><p>核对表型、固件与规约适用范围</p></div>${button('context', '查看', 'text-button')}</div></div></section></div><section class="surface" style="margin-top:24px"><div class="section-head"><h2>继续上次的 AI 分析</h2>${link('assistant', '进入 AI 助手 →')}</div><div class="section-body"><div class="pill-line"><div><h3>费率切换失败分析</h3><p class="muted">关联本次运行、费率切换测试方法和 1 份用例建议草稿</p></div>${link('assistant', '继续分析 →')}</div></div></section><p class="subtle-footer">工作台聚合业务状态；完整提问、引用核对和建议处理在 AI 助手中完成。</p></div>`;
}
function knowledge() {
  const categories = {
    TARIFF_CLOCK: '费率与时钟',
    PROTOCOL: '通信规约',
    ROBOT_METHOD: 'Robot 方法',
    METERING: '电能计量',
    PREPAYMENT: '费控',
    EVENTS: '事件记录',
  };
  const items = all('KnowledgeBase').filter(
    (k) =>
      (!state.kbQuery || k.name.includes(state.kbQuery)) &&
      (!state.kbCategory || k.category === state.kbCategory),
  );
  return `<div class="page" data-page="knowledge">${heading('知识库', '按测试领域组织规范与方法，为 AI 回答提供可核对的依据。', button('new-kb', '创建知识库', 'primary', 'createKnowledgeBase'))}<div class="toolbar"><div class="filters"><input class="search" aria-label="搜索知识库" data-filter="kbQuery" value="${esc(state.kbQuery)}" placeholder="搜索知识库名称"/><select aria-label="知识领域" data-filter="kbCategory"><option value="">全部领域</option>${Object.entries(
    categories,
  )
    .map(
      ([v, t]) => `<option value="${v}" ${state.kbCategory === v ? 'selected' : ''}>${t}</option>`,
    )
    .join(
      '',
    )}</select></div><span class="muted">${items.length} 个知识库</span></div><div class="kb-grid">${items.map((k) => `<article class="kb-card"><div class="section-body"><div class="kb-label">${categories[k.category]}</div><h2>${esc(k.name)}</h2><p>${esc(k.description)}</p><div class="actions">${button(`open-kb:${k.id}`, '进入知识库', '', 'getKnowledgeBase')}${button(`ask-kb:${k.id}`, '问这个知识库', 'text-button', 'createConversation')}</div></div><footer><span>${k.readyCount} 份可问答 / ${k.documentCount} 份文档</span>${badge(k.visibility === 'PROJECT' ? '项目内可读' : '限制访问')}</footer></article>`).join('') || '<div class="empty">没有匹配的知识库，请调整搜索或创建知识库。</div>'}</div><div class="inline-notice">只会检索你有权访问且已完成处理的资料。规范版本和适用范围会随问题一起保留。</div></div>`;
}
let currentKb = '1';
function knowledgeDetail() {
  const kb = all('KnowledgeBase').find((x) => x.id === currentKb) || first('KnowledgeBase');
  const documents = all('Document').filter(
    (d) => d.knowledgeBaseId === kb.id && d.status !== 'DELETED',
  );
  let content;
  if (state.tab === 'documents')
    content = `<div class="surface">${table(
      ['文档与版本', '处理状态', '加入时间', '操作'],
      documents.map((d) =>
        row(
          `<strong>${esc(d.title)}</strong><p class="muted">v${esc(d.versionLabel)}</p>`,
          status(d.status),
          '2026-11-17',
          d.status === 'READY'
            ? `${button(`document:${d.id}`, '阅读', 'text-button', 'getDocumentContent')} · ${button(`ask-doc:${d.id}`, '问文档', 'text-button')}`
            : `${button(`retry:${d.id}`, '重试处理', 'text-button', 'retryDocument')} · ${button(`delete-doc:${d.id}`, '删除', 'text-button danger', 'deleteDocument')}`,
        ),
      ),
    )}${pager(documents.length)}</div>`;
  if (state.tab === 'settings')
    content = `<form id="kb-settings" class="surface section-body"><div class="field-grid"><div class="field"><label>名称<input name="name" value="${esc(kb.name)}" required/></label></div><div class="field"><label>可见范围<select name="visibility"><option value="PROJECT">项目成员可读</option><option value="RESTRICTED" ${kb.visibility === 'RESTRICTED' ? 'selected' : ''}>指定成员可读</option></select></label></div><div class="field full"><label>用途与适用范围<textarea name="description">${esc(kb.description)}</textarea></label></div></div><button class="primary" style="margin-top:20px" data-operation="updateKnowledgeBase">保存设置</button></form>`;
  if (state.tab === 'grants')
    content = `<div class="surface section-body"><h2>指定成员授权</h2><p class="muted">限制访问知识库需显式授权；项目所有者保留管理权限。</p>${table(['成员', '知识库权限', '操作'], [row('演示研发人员', '读取', button('grant', '调整权限', 'text-button', 'setKnowledgeGrant'))])}${button('grant', '添加授权成员', '', 'setKnowledgeGrant')}</div>`;
  return `<div class="page" data-page="knowledge-detail">${link('knowledge', '← 返回知识库', 'back')}${heading(esc(kb.name), `${esc(kb.description)} · ${kb.visibility === 'PROJECT' ? '项目内可读' : '限制访问'}`, `${button(`ask-kb:${kb.id}`, '问这个知识库', '', 'createConversation')}${button('upload-doc', '上传文档', 'primary', 'uploadDocument')}`)}<div class="tabs">${[
    ['documents', '文档'],
    ['settings', '知识库设置'],
    ['grants', '访问权限'],
  ]
    .map(([v, t]) => button(`kb-tab:${v}`, t, state.tab === v ? 'active' : ''))
    .join(
      '',
    )}</div>${content}<p class="subtle-footer">每份文档保留版本和内容标识，只有“可问答”资料进入 AI 检索范围。</p></div>`;
}
function documentPage() {
  const d = first('DocumentContent');
  return `<div class="page" data-page="document">${link('knowledge-detail', '← 返回知识库', 'back')}${heading('费率切换测试方法', 'v0.1 · 自造示例 · Markdown · 2026-11-17', button('ask-document', '问这篇文档', 'primary', 'createConversation'))}<div class="document-layout"><aside><h3>文档目录</h3>${d.blocks.map((b) => `<p style="margin-top:16px"><a href="#${b.id}" data-block="${b.id}">${esc(b.heading)}</a></p>`).join('')}<p class="muted" style="margin-top:30px;font-size:11px">适用对象：DEMO-M1<br/>来源：独立自造资料</p></aside><article class="document-content"><div class="eyebrow">TEST METHOD / 测试方法</div><h1 style="margin-top:12px">费率切换测试方法</h1>${d.blocks.map((b) => `<section id="${b.id}" class="${b.id === 'b2' ? 'highlight' : ''}"><h2>${esc(b.heading)}</h2><p>${esc(b.text)}</p><p class="muted" style="font-size:11px;margin-top:10px">第 ${b.startLine} 行起</p></section>`).join('')}</article></div></div>`;
}
function casesPage() {
  const cases = all('TestCase').filter(
    (c) =>
      (!state.query || `${c.title}${c.documentation}`.includes(state.query)) &&
      (!state.module || c.module === state.module) &&
      (!state.suite || c.suiteId === state.suite) &&
      (!state.tag || c.tags.includes(state.tag)),
  );
  return `<div class="page" data-page="test-cases">${heading('Robot 用例', '按套件与标签精确检索，选择集合由同一规则计算。', `${button('new-case', '新建人工用例', '', 'createTestCase')}${link('imports', '导入 Robot 定义 →')}`)}${projectContext()}<div class="detail-grid"><aside class="suite-tree"><h3>测试套件</h3>${button('suite:', `全部用例 · ${all('TestCase').length}`, state.suite ? '' : 'active')}${all(
    'Suite',
  )
    .map((s) =>
      button(`suite:${s.id}`, `${s.name} · ${s.caseCount}`, state.suite === s.id ? 'active' : ''),
    )
    .join(
      '',
    )}</aside><section><div class="toolbar"><div class="filters"><input aria-label="搜索用例" class="search" data-filter="query" value="${esc(state.query)}" placeholder="搜索名称或说明"/><select aria-label="精确标签" data-filter="tag"><option value="">全部标签</option>${['smoke', 'regression', 'tariff'].map((t) => `<option ${t === state.tag ? 'selected' : ''}>${t}</option>`).join('')}</select></div></div><div class="selection"><span>已选择 <strong>${state.selected.size}</strong> 条用例 <small>（保留跨页选择）</small></span><div class="actions">${button('clear-selection', '清空', 'text-button')}${button('ask-selection', '问所选用例', '', 'createCaseSelection')}</div></div><div class="surface">${table(
    ['选择', '用例名称', '标签', '优先级', '来源'],
    cases.map((c) =>
      row(
        `<input type="checkbox" aria-label="选择${esc(c.title)}" data-case="${c.id}" ${state.selected.has(c.id) ? 'checked' : ''}/>`,
        `${button(`case:${c.id}`, esc(c.title), 'text-button', 'getTestCase')}<p class="muted" style="font-size:11px">${esc(c.module)}</p>`,
        tags(c.tags),
        badge(c.priority),
        c.sourceType === 'ROBOT' ? 'Robot 定义' : '人工用例',
      ),
    ),
  )}${pager(cases.length)}</div></section></div></div>`;
}
let currentCase = '2';
function caseDetail() {
  const c = all('TestCase').find((x) => x.id === currentCase) || first('TestCase');
  return `<div class="page" data-page="case-detail">${link('test-cases', '← 返回用例', 'back')}${heading(esc(c.title), `稳定来源身份：${esc(c.stableKey)} · 定义版本 ${c.version}`, button('ask-case', '问这个用例', 'primary'))}<div class="two-col"><section class="surface"><div class="section-head"><h2>用例定义</h2>${badge(c.sourceType === 'ROBOT' ? 'Robot 来源 · 只读' : '人工用例')}</div><div class="section-body"><div class="meta-line"><span>模块：${esc(c.module)}</span><span>优先级：${c.priority}</span>${status(c.lifecycleStatus)}</div>${tags(c.tags)}<h3 style="margin-top:25px">说明</h3><p style="margin-top:9px">${esc(c.documentation)}</p><h3 style="margin-top:25px">关键字步骤</h3><pre class="code">${c.steps.map((s) => `${s.line}  ${s.keyword}    ${s.arguments.join('    ')}`).join('\n')}</pre><h3>预期结果</h3><p style="margin-top:9px">${esc(c.expectedResult)}</p></div></section><section class="surface"><div class="section-head"><h2>来源与运行关联</h2></div><div class="section-body"><p class="muted">${esc(c.sourceRef?.path || '人工创建')}</p><p>来源行：${c.sourceRef?.line || '无'}</p><p style="margin:20px 0">用例是否启用与某次运行结果独立保存。</p>${link('run-detail', '查看最近导入的运行 →')}<div class="inline-notice">Robot 来源需修改定义文件后重新导入，AI 建议会保存为独立人工用例。</div></div></section></div></div>`;
}
function runsPage() {
  return `<div class="page" data-page="runs">${heading('运行记录', '查看导入的 Robot 结果，以结构化结果作为测试事实。', button('import-run', '导入 output.xml', 'primary', 'importRobotRun'))}${projectContext()}<div class="surface">${table(
    ['运行', '来源', '通过 / 失败 / 跳过', '耗时', '状态'],
    all('Run').map((r) =>
      row(
        button('run', esc(r.name), 'text-button', 'getRun'),
        'Robot 产物导入',
        `${badge('4 通过', 'green')} ${badge('1 失败', 'red')} ${badge('1 跳过', 'amber')}`,
        '2分14秒',
        badge('结果完整', 'green'),
      ),
    ),
  )}${pager(1)}</div></div>`;
}
function runDetail() {
  const results = all('Result').filter((r) => !state.result || r.status === state.result);
  return `<div class="page" data-page="run-detail">${link('runs', '← 返回运行记录', 'back')}${heading('DEMO-M1 功能回归 · 11/17', '来自自造 demo-output.xml · 目录版本 aaaaaaaa · 2026-11-17 14:00', button('ask-run', '让 AI 分析本次运行', 'primary'))}${projectContext()}<div class="stats">${[
    ['总用例', '6'],
    ['通过', '4'],
    ['失败', '1'],
    ['跳过', '1'],
  ]
    .map(([t, n]) => `<div class="stat"><p>${t}</p><div class="number">${n}</div></div>`)
    .join(
      '',
    )}</div><div class="toolbar"><h2>用例结果</h2><select aria-label="结果状态" data-filter="result"><option value="">全部结果</option>${['PASS', 'FAIL', 'SKIP'].map((x) => `<option ${state.result === x ? 'selected' : ''}>${x}</option>`).join('')}</select></div><div class="surface">${table(
    ['用例', '结果', '错误 / 说明', '耗时', '证据'],
    results.map((r) =>
      row(
        esc(r.caseName),
        status(r.status),
        esc(r.message),
        `${r.elapsedMs}ms`,
        button('logs', '查看日志片段', 'text-button', 'getArtifactLines'),
      ),
    ),
  )}${pager(results.length)}</div><div class="inline-notice">统计来自叶子结果：6 = 4 PASS + 1 FAIL + 1 SKIP。这里查看既有导入产物，不触发设备测试。</div></div>`;
}
function evidence() {
  const c = first('Citation');
  return `<aside class="evidence-panel" aria-label="引用原文"><div class="pill-line"><h3>引用原文</h3>${button('source', '关闭', 'text-button')}</div><p><strong>${esc(c.title)}</strong><br/>v${esc(c.version)}</p><p class="muted">${esc(c.locator.section)} · 第 ${c.locator.line} 行</p><blockquote>${esc(c.excerpt)}</blockquote>${link('document', '打开完整文档 →')}<p class="muted">引用按当前权限读取，保留回答使用的资料版本。</p></aside>`;
}
function analysisBody() {
  if (state.analysisMode === 'KNOWLEDGE')
    return `<div class="question">请解释所选资料中的费率切换场景要求。</div><article class="answer"><h2>先明确适用范围，再核对场景覆盖</h2><div class="observed"><strong>文档依据</strong><p>${esc(first('Citation').excerpt)}</p></div><p>这份自造资料提出场景要求，没有给出真实电表合格阈值，不能据此判断设备合格与否。</p>${button('source', '[1] 查看所选文档原文', 'text-button source-link')}<div class="result-strip">${link('document', '打开完整资料 →')}</div></article>`;
  if (state.analysisMode === 'CASES')
    return `<div class="question">请核对所选 ${state.selected.size} 条用例的场景覆盖。</div><article class="answer"><h2>已按选择快照读取 ${state.selected.size} 条用例</h2>${table(
      ['用例名称', '标签', '来源'],
      all('TestCase')
        .filter((c) => state.selected.has(c.id))
        .map((c) => row(esc(c.title), tags(c.tags), c.sourceType)),
    )}<div class="hypothesis"><strong>范围说明</strong><p>当前仅核对选中定义，不能将未选中的场景直接当作整个项目的缺失项。也不能从启用状态推断运行是否通过。</p></div>${link('test-cases', '返回用例核对 →')}</article>`;
  return `<div class="question">这次费率切换回归为什么失败？请结合规范给出排查建议。</div><article class="answer"><span class="muted" style="font-size:11px">已查询运行结果 · 已检索 1 份资料</span><h2>1 条失败，集中在切换后的状态核对</h2><div class="observed"><strong>观测事实</strong><p>“费率切换后状态核对”返回 FAIL，失败关键字为 Check Demo State，消息为“模拟状态断言不一致”。</p></div><div class="hypothesis"><strong>待验证推测</strong><p>需要核对切换时点与状态读取先后；现有证据不能确认设备故障。</p></div><p>建议先查看该结果的日志片段，再根据测试方法核对相关场景的覆盖。</p>${button('source', '[1] 费率切换测试方法 · 第 2 节', 'text-button source-link', 'getCitation')}<div class="result-strip"><div><strong>相关失败结果</strong><p class="muted">费率切换后状态核对 · FAIL · 2000ms</p></div>${link('run-detail', '打开运行详情 →')}</div><div class="result-strip"><div><strong>用例建议草稿</strong><p class="muted">1 项建议 · ${state.saved ? '已保存' : '尚未写入用例库'}</p></div>${link('draft', '检查并编辑草稿 →')}</div><p class="muted" style="font-size:11px">证据部分充分 · 条件与适用规范仍需测试人员核对</p></article>`;
}
function assistant() {
  const title =
    state.analysisMode === 'KNOWLEDGE'
      ? '费率切换规范问答'
      : state.analysisMode === 'CASES'
        ? '所选用例覆盖核对'
        : '费率切换失败分析';
  return `<div class="assistant-shell" data-page="assistant"><aside class="history">${button('new-chat', '新建分析会话', '', 'createConversation')}<input aria-label="搜索会话" placeholder="搜索会话" style="width:100%" data-history-search/><p>最近会话</p><div id="history-list">${button('chat-existing', '费率切换失败分析', 'active')}<p>2026-11-17 · 智能电表功能回归</p></div>${button('delete-chat', '删除当前会话', 'text-button', 'deleteConversation')}</aside><section class="chat"><header class="chat-top"><div><h2>${state.chat === 'new' ? '新的测试分析' : title}</h2><small class="muted">问题、业务事实、知识依据与建议分开展示</small></div>${button('context', '上下文范围', '', 'sendMessage')}</header><div class="chat-with-source"><div class="chat-stack"><div class="chat-main"><div class="context-chips"><span class="chip">${esc(state.context.productModel)} · ${esc(state.context.deviceType)}</span><span class="chip">规约：模拟 ${esc(state.context.protocolVersion)}</span>${state.analysisMode === 'CASES' ? `<span class="chip">已选 ${state.selected.size} 条用例 · 无运行上下文</span>` : state.analysisMode === 'KNOWLEDGE' ? '<span class="chip">仅所选知识资料 · 无运行上下文</span>' : '<span class="chip">知识库：费率与时钟</span><span class="chip">运行：11/17 功能回归</span>'}</div>${state.chat === 'new' ? `<div class="empty"><h2>从一个具体测试问题开始</h2><p>可查询用例、解释规范，或分析所选运行记录。</p><div class="actions" style="justify-content:center;margin-top:20px">${button('prompt-cases', '查 smoke 用例')}${button('prompt-knowledge', '解释费率切换规范')}${button('prompt-run', '分析本次失败')}</div></div>` : analysisBody()}${state.extraQuestions.map((q) => `<div class="question" style="margin-top:25px">${esc(q)}</div><article class="answer"><p>按当前可见上下文继续核对；可打开精确用例结果或来源阅读。本设计原型不调用模型。</p>${link('test-cases', '查看用例 →')}</article>`).join('')}</div><form id="chat-form" class="composer" data-operation="sendMessage"><div class="composer-box"><textarea name="question" aria-label="向 AI 提问" placeholder="问测试用例、规范依据或本次运行问题…" required></textarea><div class="composer-footer"><div class="actions">${button('context', '添加或调整上下文', 'text-button')}${button('source', '引用阅读', 'text-button')}</div><button class="primary" type="submit">发送</button></div></div><small>Enter 发送 · Shift+Enter 换行 · 自造设计数据，不调用模型</small></form></div>${state.source ? evidence() : ''}</div></section></div>`;
}
function importsPage() {
  return `<div class="page" data-page="imports">${heading('数据导入', '把独立测试系统的定义与结果带入协作空间，保留来源与版本。')}<div class="import-options"><section class="surface section-body"><span class="eyebrow">CATALOG</span><h2 style="margin-top:12px">导入 Robot 用例定义</h2><p>.robot / .resource 完整快照。静态提取套件、用例、标签和步骤，不执行关键字。</p>${button('import-catalog', '选择定义文件', 'primary', 'importRobotCatalog')}</section><section class="surface section-body"><span class="eyebrow">RESULT</span><h2 style="margin-top:12px">导入运行结果</h2><p>选择 output.xml，关联目录版本与测试对象。统计由结果解析，不从 HTML 报告猜测。</p>${button('import-run', '选择 output.xml', 'primary', 'importRobotRun')}</section></div><section class="surface"><div class="section-head"><h2>导入历史</h2></div>${table(
    ['导入任务', '类型', '状态', '结果'],
    all('ImportJob').map((j) =>
      row(
        'demo-output.xml · 2026-11-17',
        j.kind === 'ROBOT_RESULT' ? '运行结果' : 'Robot 定义',
        status(j.status),
        j.runId
          ? link('run-detail', '查看运行 →')
          : button('import-detail', '查看索引摘要', 'text-button'),
      ),
    ),
  )}${pager(all('ImportJob').length)}</section><p class="subtle-footer">来源为独立自造或有权使用的材料；本页不需要配置企业系统地址和设备凭据。</p></div>`;
}
function draftPage() {
  const d = first('Draft');
  return `<div class="page" data-page="draft">${link('assistant', '← 返回 AI 分析', 'back')}${heading('检查用例建议草稿', '确认适用范围和字段后，保存为独立人工用例。', badge(state.saved ? '已保存' : 'AI 建议 · 尚未保存', state.saved ? 'green' : 'amber'))}${projectContext()}<form id="draft-form" data-operation="commitDraft"><section class="draft-item"><div class="pill-line"><label><input type="checkbox" checked name="selected"/> 保存此建议</label><span class="muted">草稿版本 ${d.revision} · 有效期 24 小时</span></div><div class="field-grid" style="margin-top:20px"><div class="field full"><label for="draft-title">用例名称</label><input id="draft-title" name="title" value="${esc(d.items[0].values.title)}" required/></div><div class="field"><label for="draft-module">所属模块</label><input id="draft-module" name="module" value="费率与时钟" required/></div><div class="field"><label for="draft-priority">优先级</label><select id="draft-priority" name="priority"><option>P0</option><option selected>P1</option><option>P2</option><option>P3</option></select></div><div class="field full"><label for="draft-expected">预期结果</label><textarea id="draft-expected" name="expectedResult" required>${esc(d.items[0].values.expectedResult)}</textarea></div></div>${button('source-modal', '[1] 查看建议依据', 'text-button source-link')}<div class="warning">这是一份人工用例建议，不会修改原 Robot 文件，也不会触发测试执行。</div></section><div class="actions"><button type="submit" class="primary" ${state.saved ? 'disabled' : ''}>${state.saved ? '已保存，避免重复创建' : '确认保存选中 1 条用例'}</button>${button('discard', '放弃草稿')}${state.saved ? link('test-cases', '打开用例库 →') : ''}</div></form></div>`;
}
function settingsPage() {
  return `<div class="page" data-page="project-settings">${heading('项目与成员', '维护项目身份与授权；项目是测试数据的隔离边界。')}<section class="surface section-body"><form id="project-form" data-operation="updateProject"><div class="field-grid"><div class="field"><label>项目名称<input name="name" value="${esc(first('Project').name)}" required/></label></div><div class="field"><label>项目标识<input value="METER_DEMO" readonly/></label></div><div class="field full"><label>说明<textarea name="description">独立自造 Robot 智能电表测试域</textarea></label></div></div><button class="primary" style="margin-top:20px">保存项目设置</button></form></section><section class="surface" style="margin-top:25px"><div class="section-head"><h2>项目成员</h2>${button('add-member', '添加成员', '', 'setProjectMember')}</div>${table(
    ['成员', '项目角色', '权限'],
    all('Member').map((m) =>
      row(
        esc(m.username),
        m.role,
        m.role === 'OWNER' ? '项目管理与审计' : button('member-role', '调整角色', 'text-button'),
      ),
    ),
  )}</section></div>`;
}
function auditPage() {
  return `<div class="page" data-page="audit">${heading('审计记录', '追踪导入、授权和业务写入，记录操作事实与结果。')}<div class="surface">${table(
    ['时间', '操作人', '动作', '对象', '结果'],
    all('AuditEntry').map((x) =>
      row(
        '2026-11-17 14:30',
        esc(x.actorName),
        esc(x.action),
        `${x.resourceType} / ${x.resourceId}`,
        badge('成功', 'green'),
      ),
    ),
  )}${pager(all('AuditEntry').length)}</div></div>`;
}
function accountPage() {
  return `<div class="page" data-page="account">${heading('账户', '当前标签页的身份与会话信息。')}<div class="surface section-body"><h2>演示测试工程师</h2><div class="meta-line"><span>平台角色 ADMIN</span><span>当前项目 OWNER</span></div><p class="muted">退出后清理当前标签页会话，返回登录页面。</p><div style="margin-top:25px">${button('logout', '退出登录')}</div></div></div>`;
}
function render() {
  const p = page();
  if (p === 'login') {
    document.title = '登录 · TestOps AI 最终设计';
    app.innerHTML = login();
    return;
  }
  const views = {
    overview,
    assistant,
    knowledge,
    'knowledge-detail': knowledgeDetail,
    document: documentPage,
    'test-cases': casesPage,
    'case-detail': caseDetail,
    runs: runsPage,
    'run-detail': runDetail,
    imports: importsPage,
    draft: draftPage,
    'project-settings': settingsPage,
    audit: auditPage,
    account: accountPage,
  };
  const active = ['knowledge-detail', 'document'].includes(p)
    ? 'knowledge'
    : p === 'case-detail'
      ? 'test-cases'
      : p === 'run-detail'
        ? 'runs'
        : p === 'draft'
          ? 'assistant'
          : p;
  app.innerHTML = shell((views[p] || overview)(), active);
  document.title = `${mappings.pages.find((x) => x.id === p)?.title || '工作台'} · TestOps AI 最终设计`;
}
function toast(text) {
  const el = document.querySelector('#toast');
  el.textContent = text;
  el.style.display = 'block';
  clearTimeout(state.toast);
  state.toast = setTimeout(() => {
    el.style.display = 'none';
  }, 3500);
}
function modal(title, body, footer = button('close', '关闭')) {
  dialog.innerHTML = `<header><h2 id="dialog-title">${title}</h2>${button('close', '关闭', 'text-button')}</header><div class="dialog-body">${body}</div><footer>${footer}</footer>`;
  if (!dialog.open) dialog.showModal();
}
function go(path) {
  if (page() === path) render();
  else location.hash = `/${path}`;
}
function ask() {
  state.chat = 'existing';
  state.menu = false;
  go('assistant');
}
function importModal(kind) {
  modal(
    kind === 'catalog' ? '导入 Robot 用例定义' : '导入运行结果',
    `<form id="import-form" data-kind="${kind}"><div class="field"><label for="import-file">选择${kind === 'catalog' ? '.robot / .resource 文件' : 'output.xml'}</label><input id="import-file" type="file" ${kind === 'catalog' ? 'multiple accept=".robot,.resource"' : 'accept=".xml"'}/></div><div class="field" style="margin-top:20px"><label>关联目录版本<input value="aaaaaaaa · 当前自造目录" readonly/></label></div><p class="muted" style="font-size:12px;margin-top:20px">设计演示提供完整文件选择与确认流程；使用下方“载入自造样例”验证导入结果，真实解析按冻结接口实现。</p></form>`,
    `${button('sample-import', '载入自造样例', 'primary')}${button('review-files', '核对所选文件')}`,
  );
}
document.addEventListener('click', (event) => {
  const block = event.target.closest('[data-block]');
  if (block) {
    event.preventDefault();
    document.getElementById(block.dataset.block)?.scrollIntoView({ block: 'center' });
    return;
  }
  const target = event.target.closest('[data-action]');
  if (!target) return;
  const [action, id] = target.dataset.action.split(':');
  if (action === 'close') return dialog.close();
  if (action === 'menu') {
    state.menu = !state.menu;
    return render();
  }
  if (action === 'logout') {
    state.failedLogin = false;
    return go('login');
  }
  if (action === 'login-error') {
    state.failedLogin = true;
    return render();
  }
  if (action === 'rate-limit')
    return modal(
      '请求过于频繁',
      '<p>登录尝试次数过多，请在 60 秒后重试。</p><p class="muted">429 · RATE_LIMITED · Retry-After: 60</p>',
    );
  if (action === 'open-kb') {
    currentKb = id;
    state.tab = 'documents';
    return go('knowledge-detail');
  }
  if (action === 'kb-tab') {
    state.tab = id;
    return render();
  }
  if (action === 'case') {
    currentCase = id;
    return go('case-detail');
  }
  if (action === 'document') return go('document');
  if (action === 'run') return go('run-detail');
  if (action === 'suite') {
    state.suite = id;
    return render();
  }
  if (action === 'clear-selection') {
    state.selected.clear();
    return render();
  }
  if (action === 'source') {
    state.source = !state.source;
    return render();
  }
  if (action === 'source-modal')
    return modal(
      '建议引用依据',
      `<h3>${esc(first('Citation').title)}</h3><p style="margin-top:18px">${esc(first('Citation').excerpt)}</p>${link('document', '打开完整文档 →')}`,
    );
  if (action.startsWith('ask')) {
    state.analysisMode =
      action.includes('kb') || action.includes('doc')
        ? 'KNOWLEDGE'
        : action.includes('case') || action.includes('selection')
          ? 'CASES'
          : 'RUN';
    if (action === 'ask-selection' && state.selected.size === 0)
      return toast('先选择至少一条用例。');
    return ask();
  }
  if (action === 'delete-chat')
    return modal(
      '删除当前会话？',
      '<p>删除对话不删除已保存的人工用例。</p>',
      button('new-chat', '删除会话'),
    );
  if (action === 'new-chat') {
    if (dialog.open) dialog.close();
    state.chat = 'new';
    state.extraQuestions = [];
    return render();
  }
  if (action === 'chat-existing') {
    state.analysisMode = 'RUN';
    state.chat = 'existing';
    return render();
  }
  if (action.startsWith('prompt')) {
    const input = document.querySelector('[name="question"]');
    input.value = {
      'prompt-cases': '查找 smoke 标签的费率切换用例',
      'prompt-knowledge': '解释所选费率切换测试方法的场景要求',
      'prompt-run': '分析这次回归的失败结果',
    }[action];
    return input.focus();
  }
  if (action === 'logs')
    return modal(
      '失败结果 · 日志证据',
      `<p class="muted">demo-output.xml · 第 41 行起 · 自造模拟结果</p><pre class="code">${esc(first('ArtifactLines').lines.join('\n'))}</pre><p>请将日志事实与可能原因分开判断。</p>`,
    );
  if (action === 'context')
    return modal(
      '本次测试上下文',
      `<form id="context-form"><div class="field-grid">${[
        ['deviceType', '表型', state.context.deviceType],
        ['productModel', '产品型号', state.context.productModel],
        ['firmwareVersion', '固件版本', state.context.firmwareVersion],
        ['protocolVersion', '规约版本', state.context.protocolVersion],
      ]
        .map(
          ([name, label, value]) =>
            `<div class="field"><label>${label}${
              name === 'deviceType'
                ? `<select name="deviceType">${[
                    ['SINGLE_PHASE', '单相智能电表'],
                    ['THREE_PHASE', '三相智能电表'],
                    ['UNSPECIFIED', '未指定'],
                  ]
                    .map(
                      ([v, t]) =>
                        `<option value="${v}" ${value === v ? 'selected' : ''}>${t}</option>`,
                    )
                    .join('')}</select>`
                : `<input name="${name}" value="${esc(value)}"/>`
            }</label></div>`,
        )
        .join(
          '',
        )}<div class="field full"><label>知识范围<select name="knowledgeMode"><option value="SELECTED">仅所选知识库与文档</option><option value="NONE">不使用知识资料</option></select></label></div></div><p class="muted" style="margin-top:18px">当前运行：11/17 功能回归；所选用例：${state.selected.size} 条。调整仅影响后续提问，不修改历史回答范围。</p><button type="submit" class="primary" style="margin-top:20px">应用到后续提问</button></form>`,
    );
  if (action === 'retry') {
    const d = all('Document').find((x) => x.id === id);
    d.status = 'READY';
    d.error = null;
    d.stage = 'FINISHED';
    render();
    return toast('自造演示文档处理流程已重试完成。');
  }
  if (action === 'delete-doc')
    return modal(
      '确认删除文档',
      '<p>删除后停止检索此文档，文件与向量清理完成后才从列表移除。</p>',
      `${button(`confirm-delete:${id}`, '确认删除', 'primary')}${button('close', '保留文档')}`,
    );
  if (action === 'confirm-delete') {
    all('Document').find((x) => x.id === id).status = 'DELETED';
    dialog.close();
    return render();
  }
  if (action === 'upload-doc')
    return modal(
      '上传知识文档',
      `<form id="upload-form"><div class="field-grid"><div class="field full"><label>文档文件<input type="file" name="file" accept=".md,.pdf" required/></label></div><div class="field"><label>文档名称<input name="title" required/></label></div><div class="field"><label>版本标识<input name="versionLabel" value="0.1" required/></label></div></div><p class="muted" style="font-size:12px;margin-top:18px">Markdown ≤2 MiB；PDF ≤20 MiB。新内容生成独立版本，处理完成后才可问答。</p><button class="primary" type="submit" style="margin-top:20px">确认上传</button></form>`,
    );
  if (action === 'new-kb')
    return modal(
      '创建知识库',
      `<form id="new-kb-form"><div class="field"><label>知识库名称<input name="name" required maxlength="128"/></label></div><div class="field" style="margin-top:16px"><label>测试领域<select name="category"><option value="METERING">电能计量</option><option value="PROTOCOL">通信规约</option><option value="TARIFF_CLOCK">费率与时钟</option><option value="EVENTS">事件记录</option></select></label></div><div class="field" style="margin-top:16px"><label>适用范围<textarea name="description" required></textarea></label></div><button class="primary" type="submit" style="margin-top:18px">创建知识库</button></form>`,
    );
  if (action === 'import-catalog') return importModal('catalog');
  if (action === 'import-run') return importModal('run');
  if (action === 'review-files') {
    const files = [...(document.querySelector('#import-file')?.files || [])];
    if (!files.length) return toast('请先选择文件，或载入自造样例。');
    const catalog = document.querySelector('#import-form').dataset.kind === 'catalog';
    if (
      files.some(
        (f) =>
          !(catalog ? /\.(robot|resource)$/i : /\.xml$/i).test(f.name) ||
          f.size > (catalog ? 2 : 50) * 1024 * 1024,
      )
    )
      return toast('文件类型或大小不符合当前导入规则。');
    return modal(
      '文件核对',
      `<p>${files.map((f) => esc(f.name)).join('<br/>')}</p><div class="warning">文件选择校验通过。当前是设计原型，不上传、执行或解析这些文件；后续实现使用冻结的导入接口。</div>`,
    );
  }
  if (action === 'sample-import') {
    dialog.close();
    toast('自造样例已载入，可核对既有导入结果。');
    return go('imports');
  }
  if (action === 'import-detail')
    return modal('目录导入摘要', '<p>自造目录：2 个套件、6 条用例；未归档人工用例。</p>');
  if (action === 'discard')
    return modal(
      '放弃这份草稿？',
      '<p>放弃草稿不修改原 Robot 用例，也不删除已保存的业务数据。</p>',
      `${button('confirm-discard', '放弃草稿')}${button('close', '继续检查', 'primary')}`,
    );
  if (action === 'confirm-discard') {
    dialog.close();
    return go('assistant');
  }
  if (['grant', 'member-role', 'add-member'].includes(action))
    return modal(
      '成员授权',
      `<form id="member-form"><div class="field"><label>项目成员<select name="username"><option>演示研发人员</option><option>演示测试人员</option></select></label></div><div class="field" style="margin-top:20px"><label>权限<select name="role"><option value="VIEWER">只读 / READ</option><option value="EDITOR">编辑 / WRITE</option></select></label></div><button class="primary" style="margin-top:20px">保存授权</button></form>`,
    );
  if (action === 'new-case')
    return modal(
      '新建人工用例',
      '<form id="manual-case-form"><div class="field"><label>用例名称<input name="title" required/></label></div><div class="field" style="margin-top:15px"><label>所属模块<input name="module" value="费率与时钟" required/></label></div><div class="field" style="margin-top:15px"><label>预期结果<textarea name="expectedResult" required></textarea></label></div><button class="primary" style="margin-top:20px">保存人工用例</button></form>',
    );
});
document.addEventListener('change', (event) => {
  const t = event.target;
  if (t.id === 'show-password')
    document.querySelector('#password').type = t.checked ? 'text' : 'password';
  if (t.dataset.case) {
    if (t.checked) state.selected.add(t.dataset.case);
    else state.selected.delete(t.dataset.case);
    render();
  }
  if (t.dataset.filter) {
    state[t.dataset.filter] = t.value;
    render();
  }
});
document.addEventListener('input', (event) => {
  if (event.target.hasAttribute('data-history-search'))
    document.querySelector('#history-list').hidden = !'费率切换失败分析'.includes(
      event.target.value,
    );
});
document.addEventListener('keydown', (event) => {
  if (
    event.target.name === 'question' &&
    event.key === 'Enter' &&
    !event.shiftKey &&
    !event.isComposing
  ) {
    event.preventDefault();
    document.querySelector('#chat-form').requestSubmit();
  }
});
document.addEventListener('submit', (event) => {
  event.preventDefault();
  const form = event.target;
  const data = Object.fromEntries(new FormData(form));
  if (form.id === 'login-form') {
    state.failedLogin = false;
    return go('overview');
  }
  if (form.id === 'chat-form') {
    state.chat = 'existing';
    state.extraQuestions.push(data.question);
    render();
    return;
  }
  if (form.id === 'draft-form') {
    if (state.saved) return toast('这份草稿已经保存，不会重复创建。');
    if (!form.querySelector('[name="selected"]').checked) return toast('至少选择一项建议。');
    const base = structuredClone(first('TestCase'));
    Object.assign(base, {
      id: '101',
      stableKey: 'manual:101',
      title: data.title,
      module: data.module,
      expectedResult: data.expectedResult,
      priority: data.priority,
      tags: first('Draft').items[0].values.tags,
      sourceType: 'MANUAL',
      suiteId: null,
      sourceRef: null,
      steps: [],
    });
    fixtures.instances.push({ schema: 'TestCase', data: base });
    state.saved = true;
    render();
    return toast('已保存 1 条人工用例；原 Robot 定义保持原样。');
  }
  if (form.id === 'new-kb-form') {
    const kb = {
      ...structuredClone(first('KnowledgeBase')),
      id: String(all('KnowledgeBase').length + 1),
      ...data,
      documentCount: 0,
      readyCount: 0,
    };
    fixtures.instances.push({ schema: 'KnowledgeBase', data: kb });
    dialog.close();
    render();
    return toast('设计会话中已创建知识库。');
  }
  if (form.id === 'kb-settings') {
    Object.assign(
      all('KnowledgeBase').find((k) => k.id === currentKb),
      data,
    );
    render();
    return toast('知识库设置已更新。');
  }
  if (form.id === 'upload-form') {
    const f = form.querySelector('[name="file"]').files[0];
    const md = /\.md$/i.test(f.name),
      pdf = /\.pdf$/i.test(f.name);
    if ((!md && !pdf) || f.size > (md ? 2 : 20) * 1024 * 1024)
      return toast('只支持符合大小限制的 Markdown 或 PDF。');
    const d = {
      ...structuredClone(first('Document')),
      id: String(all('Document').length + 1),
      knowledgeBaseId: currentKb,
      title: data.title,
      versionLabel: data.versionLabel,
      mimeType: md ? 'text/markdown' : 'application/pdf',
      status: 'QUEUED',
      stage: 'UPLOADING',
    };
    fixtures.instances.push({ schema: 'Document', data: d });
    dialog.close();
    render();
    return toast('文件已加入本地设计队列；无真实上传。');
  }
  if (form.id === 'manual-case-form') {
    const c = {
      ...structuredClone(first('TestCase')),
      id: String(100 + all('TestCase').length),
      title: data.title,
      module: data.module,
      expectedResult: data.expectedResult,
      sourceType: 'MANUAL',
      suiteId: null,
      sourceRef: null,
      steps: [],
    };
    c.stableKey = `manual:${c.id}`;
    fixtures.instances.push({ schema: 'TestCase', data: c });
    dialog.close();
    render();
    return toast('已创建人工用例。');
  }
  if (form.id === 'project-form') {
    Object.assign(first('Project'), data);
    return toast('项目设置已保存。');
  }
  if (form.id === 'member-form') {
    const member = all('Member').find((m) => m.username === data.username);
    if (member) member.role = data.role;
    else
      fixtures.instances.push({
        schema: 'Member',
        data: {
          userId: String(all('Member').length + 1),
          username: data.username,
          role: data.role,
        },
      });
    dialog.close();
    render();
    return toast('设计会话中已应用授权变更。');
  }
  if (form.id === 'context-form') {
    if (!['SINGLE_PHASE', 'THREE_PHASE', 'UNSPECIFIED'].includes(data.deviceType))
      return toast('表型请使用已列出的枚举值。');
    const { knowledgeMode, ...contextValues } = data;
    Object.assign(state.context, contextValues);
    state.knowledgeMode = knowledgeMode;
    dialog.close();
    render();
    return toast('后续问题将使用调整后的范围；历史消息保持原上下文。');
  }
});
window.addEventListener('hashchange', () => {
  if (dialog.open) dialog.close();
  state.menu = false;
  render();
  window.scrollTo(0, 0);
});
render();
