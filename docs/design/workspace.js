'use strict';

const pages = {
  accounts: '平台账号',
  accountEdit: '账号管理',
  password: '修改密码',
  transfer: '审核负责人交接',
  handoverRequest: '发起负责人交接',
  login: '登录',
  account: '我的账号',
  empty: '尚未加入项目',
  chat: 'AI 工作台',
  answer: '复测判定',
  robot: 'Robot 用例草稿',
  library: '项目资料',
  submit: '提交资料',
  document: '资料详情',
  review: '确认资料版本',
  access: '文档读者',
  basis: '验收依据',
  runs: '运行记录',
  register: '登记复测',
  run: '运行详情',
  settings: '项目设置',
  members: '项目成员',
  member: '添加或调整成员',
  platform: '平台项目',
  create: '创建项目',
  platformDetail: '平台项目详情',
  jobs: '处理状态',
};
const names = { owner: '张岚', test: '李宁', dev: '王禾', admin: '林乔' };
const roleNames = {
  owner: '项目负责人',
  test: '测试工程师',
  dev: '开发工程师',
  admin: '平台管理员',
};
const ownerPages = [
  'review',
  'access',
  'basis',
  'settings',
  'members',
  'member',
  'handoverRequest',
];
const adminPages = [
  'platform',
  'create',
  'platformDetail',
  'jobs',
  'accounts',
  'accountEdit',
  'transfer',
];
let role = 'owner',
  page = 'library',
  project = '电表 A 型验收',
  selectedPlatform = project,
  docId = 'spec',
  docVersion = 'V2',
  selectedUser = '',
  selectedRun = 'R-026',
  returnChat = '',
  query = '',
  filter = '全部状态',
  question = '',
  chatFile = '',
  navFile = '',
  attachment = null;
let queryDocs = [
  {
    id: 'spec',
    name: '响应时间技术规范',
    version: 'V2',
    type: '技术规范',
    review: '已发布',
    index: '就绪',
    author: '李宁',
    access: '项目内共享',
    readers: ['张岚', '李宁', '王禾'],
    excerpt: '设备应在 2 秒内响应。',
  },
  {
    id: 'keyword',
    name: 'Robot 关键字说明',
    version: 'V3',
    type: '关键字说明',
    review: '待确认',
    index: '未启动',
    author: '王禾',
    access: '项目内共享',
    readers: ['张岚', '李宁', '王禾'],
    excerpt: '读取拒绝记录关键字返回时间、结果及拒绝原因。',
  },
  {
    id: 'tender',
    name: '验收标书附件',
    version: 'V1',
    type: '标书',
    review: '已发布',
    index: '就绪',
    author: '张岚',
    access: '限定读者',
    readers: ['张岚', '李宁'],
    explicitReaders: ['李宁'],
    excerpt: '拒绝操作时，应记录拒绝原因。',
  },
];
const declaredFile = (id, version) => ({
  id,
  name: queryDocs.find((d) => d.id === id).name,
  version,
});
const historyBasis = [
  { label: '验收包 V2', files: [declaredFile('spec', 'V2'), declaredFile('tender', 'V1')] },
  { label: '验收包 V1', files: [declaredFile('spec', 'V1'), declaredFile('tender', 'V1')] },
];
const people = [
  { name: '张岚', role: '项目负责人' },
  { name: '李宁', role: '测试工程师' },
  { name: '王禾', role: '开发工程师' },
];
const users = ['张岚', '李宁', '王禾', '周澄', '罗辰'];
const projects = [{ name: project, owner: '张岚', desc: 'A 型智能电表验收与复测' }];
const runRows = [
  {
    id: 'R-026',
    name: '周一响应时间复测',
    file: 'output.xml',
    who: '王禾',
    at: '2026-11-16 10:30',
    status: '已登记',
  },
  {
    id: 'R-025',
    name: '拒绝原因回归',
    file: 'output.xml',
    who: '李宁',
    at: '2026-11-15 09:10',
    status: '已登记',
  },
];
let desc = projects[0].desc;
const $ = (s) => document.querySelector(s);
const esc = (v) =>
  String(v ?? '').replace(
    /[&<>"']/g,
    (ch) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[ch],
  );
const action = (key, label, cls = '', data = '') =>
  '<button type="button" data-action="' +
  esc(key) +
  '" data-value="' +
  esc(data) +
  '" class="' +
  cls +
  '">' +
  label +
  '</button>';
const link = (key, label) => action('go', label, 'text', key);
const pill = (v) =>
  '<span class="pill ' +
  (/待|解析中/.test(v) ? 'amber' : /失败|退回/.test(v) ? 'red' : '') +
  '">' +
  esc(v) +
  '</span>';
const panel = (s) => '<section class="panel">' + s + '</section>';
const table = (heads, rows) =>
  '<div class="scroll"><table><thead><tr>' +
  heads.map((x) => '<th>' + esc(x) + '</th>').join('') +
  '</tr></thead><tbody>' +
  rows.map((r) => '<tr>' + r.map((x) => '<td>' + x + '</td>').join('') + '</tr>').join('') +
  '</tbody></table>' +
  (rows.length ? '' : '<p class="emptymsg">没有匹配的记录</p>') +
  '</div>';
const title = (h, sub = '', cta = '') =>
  '<div class="title"><div><h1>' +
  esc(h) +
  '</h1><span class="muted">' +
  esc(sub) +
  '</span></div>' +
  cta +
  '</div>';
const field = (label, el, help = '') =>
  '<label class="field">' + label + el + (help ? '<small>' + help + '</small>' : '') + '</label>';
const input = (id, val = '', type = 'text') =>
  '<input id="' + id + '" type="' + type + '" value="' + esc(val) + '">';
const select = (id, opts, current = '') =>
  '<select id="' +
  id +
  '">' +
  opts
    .map((x) => '<option ' + (x === current ? 'selected' : '') + '>' + esc(x) + '</option>')
    .join('') +
  '</select>';
const save = (key, label) => action(key, label, 'primary');
const docs = () =>
  queryDocs.filter(
    (d) =>
      role !== 'admin' &&
      (d.access !== '限定读者' || d.readers.includes(names[role])) &&
      (d.review === '已发布' || role === 'owner' || d.author === names[role]),
  );
const current = () => docs().find((d) => d.id === docId);
const head = () => {
  let list =
    role === 'admin'
      ? [
          ['platform', '平台项目'],
          ['accounts', '平台账号'],
          ['jobs', '处理状态'],
        ]
      : [
          ['chat', 'AI 工作台'],
          ['library', '项目资料'],
          ['runs', '运行记录'],
          ...(role === 'owner' ? [['settings', '项目管理']] : []),
        ];
  const active = adminPages.includes(page)
    ? page === 'jobs'
      ? 'jobs'
      : ['accounts', 'accountEdit'].includes(page)
        ? 'accounts'
        : 'platform'
    : ['chat', 'answer', 'robot'].includes(page)
      ? 'chat'
      : ['runs', 'run', 'register'].includes(page)
        ? 'runs'
        : ownerPages.slice(2).includes(page)
          ? 'settings'
          : 'library';
  return (
    '<header><span class="brand"><b>T</b>TestOps AI</span>' +
    action('switch-project', role === 'admin' ? '平台管理' : esc(project) + ' ⌄', 'project') +
    '<nav>' +
    list.map(([k, n]) => action('go', n, k === active ? 'active' : '', k)).join('') +
    '</nav>' +
    action('go', esc(names[role]) + ' · ' + roleNames[role], 'who', 'account') +
    '</header>'
  );
};
const settingsNav = () =>
  '<div class="actions settings-tabs" role="navigation" aria-label="项目设置">' +
  link('settings', '基本信息') +
  link('members', '项目成员') +
  link('basis', '验收依据') +
  link('handoverRequest', '交接负责人') +
  '</div>';
function draw() {
  $('#role').value = role;
  const view = views[page] || views.library;
  $('#surface').innerHTML =
    (page === 'login' ? '' : head()) +
    (['chat', 'answer', 'robot'].includes(page)
      ? view()
      : '<main>' +
        (['login', 'empty'].includes(page)
          ? ''
          : '<div class="crumb">' +
            esc(adminPages.includes(page) ? '平台管理' : project) +
            '　/　' +
            pages[page] +
            '</div>') +
        view() +
        '</main>');
  $('#surface nav .active')?.setAttribute('aria-current', 'page');
  const settingsTab = document.querySelector(
    '.settings-tabs [data-value="' + (page === 'member' ? 'members' : page) + '"]',
  );
  settingsTab?.setAttribute('aria-current', 'page');
  document.title = pages[page] + ' · TestOps AI 设计评审';
}
function go(key) {
  if (!pages[key]) return;
  if (
    (ownerPages.includes(key) && role !== 'owner') ||
    (adminPages.includes(key) && role !== 'admin') ||
    (role === 'admin' &&
      !adminPages.includes(key) &&
      !['login', 'account', 'empty', 'password'].includes(key))
  ) {
    notify('此角色没有此入口');
    return;
  }
  page = key;
  closeDrawer(false);
  location.hash = key;
  draw();
  const heading = $('#surface h1, #surface .reply h2');
  if (heading) {
    heading.tabIndex = -1;
    heading.focus({ preventScroll: true });
  }
  window.scrollTo(0, 0);
}
function fieldError(id, message) {
  const control = document.getElementById(id);
  if (!control) return notify(message);
  const errorId = id + '-error';
  let error = document.getElementById(errorId);
  if (!error) {
    error = document.createElement('small');
    error.id = errorId;
    error.className = 'field-error';
    error.setAttribute('role', 'alert');
    control.insertAdjacentElement('afterend', error);
  }
  error.textContent = message;
  control.setAttribute('aria-invalid', 'true');
  control.setAttribute('aria-describedby', errorId);
  control.focus();
}
function clearFieldError(control) {
  if (control.getAttribute('aria-invalid') !== 'true') return;
  document.getElementById(control.id + '-error')?.remove();
  control.removeAttribute('aria-invalid');
  control.removeAttribute('aria-describedby');
}
function notify(msg) {
  $('#toast').textContent = msg;
  clearTimeout(notify.timer);
  notify.timer = setTimeout(() => ($('#toast').textContent = ''), 2800);
}
let opener = null;
function closeDrawer(restore = true) {
  if (!$('#overlay').firstChild) return;
  $('#overlay').replaceChildren();
  $('#surface').inert = false;
  $('.review').inert = false;
  if (restore && opener?.isConnected) opener.focus({ preventScroll: true });
  opener = null;
}
function drawer(body, wide = false) {
  if (!$('#overlay').firstChild) opener = document.activeElement;
  $('#surface').inert = true;
  $('.review').inert = true;
  $('#overlay').innerHTML =
    '<div class="veil" data-action="close"></div><aside class="drawer ' +
    (wide ? 'map' : '') +
    '" role="dialog" aria-modal="true" aria-label="详情">' +
    action('close', '关闭 ×') +
    body +
    '</aside>';
  $('#overlay button')?.focus();
}
const demoSwitch = (k) => {
  if (adminPages.includes(k)) role = 'admin';
  else if (ownerPages.includes(k)) role = 'owner';
  else if (role === 'admin' && !['account', 'login', 'empty', 'password'].includes(k))
    role = 'test';
  go(k);
};
const groups = [
  ['账号与入口', ['login', 'empty', 'account', 'password']],
  ['AI 工作台', ['chat', 'answer', 'robot']],
  ['项目资料', ['library', 'submit', 'document', 'review', 'access']],
  ['运行记录', ['runs', 'register', 'run']],
  ['项目管理', ['settings', 'members', 'member', 'basis', 'handoverRequest']],
  [
    '平台管理',
    ['platform', 'create', 'platformDetail', 'transfer', 'jobs', 'accounts', 'accountEdit'],
  ],
];
function map() {
  drawer(
    '<h2>全部界面</h2><p class="muted">选择页面会切换到可体验该页面的角色。</p>' +
      groups
        .map(
          ([name, keys]) =>
            '<h3>' +
            name +
            '</h3><div class="row">' +
            keys.map((k) => action('demo', pages[k], '', k)).join('') +
            '</div>',
        )
        .join(''),
    true,
  );
}
const v = {};
v.login = () =>
  '<section class="sheet center">' +
  title('登录 TestOps AI') +
  field('账号', input('username', 'demo.user')) +
  field('密码', '<input type="password" value="sample" autocomplete="off">') +
  save('sign-in', '登录') +
  '</section>';
v.empty = () =>
  panel(
    title('尚未加入项目', '请联系项目负责人将你的平台账号加入项目。') +
      link('account', '查看我的账号'),
  );
v.account = () =>
  panel(
    title(names[role], roleNames[role]) +
      field('当前身份', '<span>' + roleNames[role] + '</span>') +
      field('可进入的工作区', '<span>' + esc(role === 'admin' ? '平台管理' : project) + '</span>') +
      link('password', '修改密码') +
      action('go', '退出登录', '', 'login'),
  );
function composer() {
  return (
    '<div class="composer"><textarea id="ask" aria-label="消息" placeholder="提问、粘贴 Robot 用例，或附上运行日志…"></textarea><div class="row"><label>附件 <input id="chatfile" type="file" style="max-width:180px"></label>' +
    save('send', '发送') +
    '</div></div>'
  );
}
let answerBasis = historyBasis[0];
function comparison() {
  const spec = answerBasis.files.find((x) => x.id === 'spec');
  const currentKnown = spec?.version === 'V2';
  const current = currentKnown ? '未达标' : '缺少可核对的响应时间条款';
  const summary = 'Robot 执行通过。按当时验收包 V1 达标，按' + answerBasis.label + current + '。';
  return { summary, currentKnown };
}
const robotText =
  '*** Test Cases ***\n拒绝操作时保留原因\n    [Documentation]    核对拒绝操作后记录中的原因\n    # 待确认：准备会被拒绝的请求及项目关键字\n    # 待确认：项目内读取拒绝记录的关键字与字段';
function chat(kind) {
  if (kind === 'answer' && !docs().some((d) => d.id === 'spec'))
    return (
      '<div class="thread">' +
      panel(
        '<h2>相关资料权限已变化</h2><p>原回答包含当前不可见的资料，重新提问可基于现有权限核对。</p>',
      ) +
      composer() +
      '</div>'
    );
  return (
    '<div class="chat"><aside class="history">' +
    save('newchat', '＋ 新对话') +
    '<input id="historySearch" aria-label="搜索个人对话" placeholder="搜索对话"><small>我的对话</small>' +
    action('go', '周一复测核对', kind === 'answer' ? 'now' : '', 'answer') +
    action('go', '拒绝原因用例', kind === 'robot' ? 'now' : '', 'robot') +
    '</aside><section class="thread ' +
    (kind === 'chat' ? 'empty' : '') +
    '">' +
    action('history', '个人对话', 'mobile-only') +
    (kind === 'chat'
      ? '<h1>今天想核对什么？</h1>'
      : '<div class="bubble">' +
        esc(
          question || (kind === 'robot' ? '根据这条要求起草 Robot 用例' : '周一这次复测达标吗？'),
        ) +
        '</div><article class="reply">' +
        (kind === 'robot'
          ? '<h2>拒绝原因记录 · Robot 草稿</h2><pre>' +
            esc(robotText) +
            '</pre><p>预期结果：记录包含拒绝原因。待确认：如何触发拒绝、读取哪个字段。</p>' +
            action('evidence', '查看条款', 'text', 'draft')
          : '<h2>' +
            esc(comparison().summary) +
            '</h2><div class="answerline">实测 2.5 秒　' +
            action('evidence', '运行日志 ↗', 'text', 'log') +
            '</div><div class="answerline">当时 V1 ≤ 3 秒　' +
            action('evidence', 'V1 条款 ↗', 'text', 'v1') +
            '</div><div class="answerline">' +
            esc(answerBasis.label) +
            (comparison().currentKnown
              ? ' ≤ 2 秒　' + action('evidence', 'V2 条款 ↗', 'text', 'v2')
              : '：请补充响应时间条款') +
            '</div><details><summary>展开逐条核对</summary>响应时间：2.5 ≤ 3，按当时依据达标。' +
            (comparison().currentKnown ? '按当前依据，2.5 > 2。' : '当前依据缺少对应条款。') +
            '其他要求缺少运行证据。</details>') +
        '<div class="actions">' +
        action('copy', kind === 'robot' ? '复制草稿及出处' : '复制回答及出处') +
        action('carryRun', '去登记复测', 'text') +
        action('carryDoc', '去提交资料', 'text') +
        '</div></article>') +
    composer() +
    '</section></div>'
  );
}
v.chat = () => chat('chat');
v.answer = () => chat('answer');
v.robot = () => chat('robot');
v.library = () =>
  title('项目资料', '查阅标书、技术规范与关键字说明。', save('newdoc', '提交资料')) +
  panel(
    '<div class="tools"><input id="findDoc" placeholder="搜索资料名称" aria-label="搜索资料" value="' +
      esc(query) +
      '">' +
      select('docFilter', ['全部状态', '已发布', '待确认', '已退回'], filter) +
      '</div><div id="docrows">' +
      docRows() +
      '</div>',
  );
function docRows() {
  return table(
    ['资料名称', '版本', '发布状态', '解析', '读者', '提供人'],
    docs()
      .filter((d) => d.name.includes(query) && (filter === '全部状态' || d.review === filter))
      .map((d) => [
        action('openDoc', esc(d.name), 'text', d.id),
        esc(d.version),
        pill(d.review),
        pill(d.index),
        esc(d.access),
        esc(d.author),
      ]),
  );
}
v.submit = () =>
  title('提交资料', '提交后由项目负责人确认。已发布且解析就绪的版本可供 AI 引用。') +
  panel(
    field('资料名称', input('docName')) +
      field('资料类型', select('docType', ['技术规范', '标书', '关键字说明'])) +
      field('资料版本', input('version', 'V1')) +
      field(
        '文件',
        '<input id="upload" type="file" accept=".pdf,.md,.docx">',
        esc(navFile || '支持 PDF、Markdown、DOCX。'),
      ) +
      field('变更说明', '<textarea id="summary" rows="2"></textarea>') +
      '<div class="actions">' +
      save('submitDoc', '提交确认') +
      link('library', '取消') +
      '</div>',
  );
function docDetail() {
  let d = current();
  if (!d) return panel('此资料当前不可见。');
  const ver = docVersion || d.version;
  return (
    title(
      d.name,
      '版本 ' + ver + '　·　' + d.type,
      role === 'owner' ? link('access', '管理读者') : '',
    ) +
    '<div class="two">' +
    panel(
      '<h2>正文预览</h2><p class="muted">' +
        esc(d.name) +
        ' · ' +
        esc(ver) +
        '</p><blockquote class="mark">' +
        esc(
          d.index !== '就绪'
            ? '请打开原件核对；当前没有解析正文。'
            : ver === 'V1' && d.id === 'spec'
              ? '设备应在 3 秒内响应。'
              : d.excerpt,
        ) +
        '</blockquote><div class="actions">' +
        action('original', '下载原件') +
        action('newVersion', '提交新版') +
        (d.reason ? '<p>退回原因：' + esc(d.reason) + '</p>' : '') +
        (d.review === '待确认' && role === 'owner' ? save('reviewdoc', '审核此版本') : '') +
        '</div>',
    ) +
    panel(
      '<h3>版本与访问</h3><p>' +
        pill(d.review) +
        '　' +
        pill(d.index) +
        '</p><p>' +
        esc(d.access) +
        ' · 提交人 ' +
        esc(d.author) +
        '</p><h3>版本记录</h3>' +
        action('version', esc(d.version), 'text', d.version) +
        (d.id === 'spec' ? action('version', 'V1', 'text', 'V1') : '') +
        (role === 'owner' && d.review === '已发布'
          ? '<p>' + link('basis', '查看验收依据') + '</p>'
          : ''),
    ) +
    '</div>'
  );
}
v.document = docDetail;
v.review = () => {
  const d = current();
  return (
    title('审核原始文件', '批准后才开始解析和建立索引。') +
    panel(
      '<h2>' +
        esc(d?.name) +
        ' · ' +
        esc(d?.version) +
        '</h2><p>提供人 ' +
        esc(d?.author) +
        '　' +
        pill(d?.index || '') +
        '</p><blockquote class="mark">' +
        '请打开上传时的原始文件核对版本与内容。' +
        '</blockquote>' +
        field(
          '审核意见',
          '<textarea id="reason" rows="2" placeholder="退回时填写原因"></textarea>',
        ) +
        '<div class="actions">' +
        action('original', '打开原始文件') +
        save('publish', '批准并开始解析') +
        action('reject', '退回修改', 'warn') +
        '</div>',
    )
  );
};
v.access = () => {
  let d = current();
  return (
    title('文档读者', d?.name || '') +
    panel(
      field('可见范围', select('visibility', ['项目内共享', '限定读者'], d?.access)) +
        '<h3>限定读者名单</h3>' +
        people
          .map(
            (p) =>
              '<label class="inline"><input class="reader" type="checkbox" value="' +
              esc(p.name) +
              '" ' +
              (d?.readers.includes(p.name) ? 'checked' : '') +
              (p.role === '项目负责人' ? ' disabled' : '') +
              '>' +
              p.name +
              '</label> ',
          )
          .join('') +
        '<p class="muted">限定读者包含审核本资料的项目负责人。</p>' +
        save('saveAccess', '保存读者范围'),
    )
  );
};
const currentBundleFile = (id) =>
  docs().find((d) => d.id === id && d.review === '已发布' && d.index === '就绪');
v.basis = () => {
  const active = historyBasis[0],
    allowed = docs().filter((d) => d.review === '已发布' && d.index === '就绪');
  return (
    title('项目验收依据', '全项目共用一套依据，发布后整套同时生效。') +
    settingsNav() +
    panel(
      '<h2>当前生效 · ' +
        esc(active.label) +
        '</h2><ul>' +
        active.files.map((d) => '<li>' + esc(d.name) + ' · ' + esc(d.version) + '</li>').join('') +
        '</ul><hr><h3>发布下一套依据</h3>' +
        field('新包版本名', input('packageLabel')) +
        '<fieldset><legend>纳入的文件版本（可多选）</legend>' +
        allowed
          .map(
            (d) =>
              '<label class="checkrow"><input type="checkbox" class="bundlefile" value="' +
              esc(d.id) +
              '" ' +
              (active.files.some((x) => x.id === d.id && x.version === d.version)
                ? 'checked'
                : '') +
              '>' +
              esc(d.name) +
              ' · ' +
              esc(d.version) +
              '</label>',
          )
          .join('') +
        '</fieldset>' +
        save('changeBasis', '发布整套依据') +
        '<h3>生效历史</h3>' +
        historyBasis
          .map(
            (x) =>
              '<details><summary>' +
              esc(x.label) +
              '</summary>' +
              x.files.map((y) => esc(y.name) + ' · ' + esc(y.version)).join('、') +
              '</details>',
          )
          .join(''),
    )
  );
};
v.runs = () =>
  title('运行记录', '查看由项目成员登记的 Robot 运行。', save('newrun', '登记复测')) +
  panel(
    table(
      ['运行名称', '编号', '执行时间', '提供人', '状态'],
      runRows.map((r) => [
        action('openRun', esc(r.name), 'text', r.id),
        r.id,
        r.at,
        esc(r.who),
        pill(r.status),
      ]),
    ),
  );
v.register = () =>
  title('登记复测', '登记后，项目成员可以查到这次运行及其提供人。') +
  panel(
    field('运行名称', input('runName')) +
      field('执行时间', input('runTime', '2026-11-16T10:30', 'datetime-local')) +
      field(
        'Robot 结果',
        '<input id="runFile" type="file" accept=".xml">',
        esc(navFile || '选择 output.xml'),
      ) +
      field('补充日志（可选）', '<input type="file" accept=".txt,.log">') +
      save('saveRun', '登记运行') +
      ' ' +
      link('runs', '取消'),
  );
v.run = () => {
  let r = runRows.find((x) => x.id === selectedRun) || runRows[0];
  let known = r.id === 'R-026';
  return (
    title(r.name, r.id + ' · 提供人 ' + r.who + ' · ' + r.at) +
    panel(
      '<h2>执行结果</h2>' +
        (known
          ? table(
              ['用例', 'Robot 状态', '实测', '出处'],
              [
                [
                  '响应时间复核',
                  pill('PASS'),
                  '2.5 秒',
                  action('evidence', '打开日志', 'text', 'log'),
                ],
              ],
            )
          : '<p>' +
            esc(r.status === '处理中' ? '正在解析，完成后显示用例结果。' : 'Robot 结果已登记。') +
            '</p>') +
        '<div class="actions">' +
        (known ? save('askRun', '问 AI 核对这次运行') + action('raw', '查看原始结果') : '') +
        '</div>',
    )
  );
};
v.settings = () =>
  title('项目管理', project) +
  settingsNav() +
  panel(
    field('项目名称', input('projName', project)) +
      field('项目说明', '<textarea id="projDesc">' + esc(desc) + '</textarea>') +
      field('项目负责人', '<span>' + esc(projects[0].owner) + '</span>') +
      save('saveProject', '保存基本信息'),
  );
v.members = () =>
  title('项目成员', '成员加入后即可查看项目内共享资料。', save('newMember', '添加成员')) +
  settingsNav() +
  panel(
    table(
      ['成员', '项目角色', '操作'],
      people.map((p) => [
        esc(p.name),
        esc(p.role),
        p.role === '项目负责人' ? '当前负责人' : action('editMember', '管理', 'text', p.name),
      ]),
    ),
  );
v.member = () =>
  title(selectedUser ? '管理成员' : '添加项目成员', project) +
  panel(
    field(
      '已有平台账号',
      selectedUser
        ? '<span>' + esc(selectedUser) + '</span>'
        : select(
            'newUser',
            users.filter((u) => !disabledUsers.has(u) && !people.some((p) => p.name === u)),
          ),
    ) +
      field(
        '本项目角色',
        select(
          'memberRole',
          ['测试工程师', '开发工程师'],
          people.find((p) => p.name === selectedUser)?.role,
        ),
      ) +
      '<div class="actions">' +
      save('saveMember', '保存成员') +
      (selectedUser ? action('removeMember', '移出项目', 'warn') : '') +
      link('members', '返回') +
      '</div>',
  );
v.platform = () =>
  title('平台项目', '管理项目与负责人。', save('newProject', '创建项目')) +
  panel(
    table(
      ['项目', '项目负责人', '管理'],
      projects.map((p) => [
        esc(p.name),
        esc(p.owner),
        action('adminDetail', '查看项目', 'text', p.name),
      ]),
    ),
  );
v.create = () =>
  title('创建项目', '选择已有平台账号担任首位项目负责人。') +
  panel(
    field('项目名称', input('newProjName')) +
      field(
        '负责人',
        select(
          'newOwner',
          users.filter((u) => !disabledUsers.has(u)),
        ),
      ) +
      field('说明', '<textarea id="newDesc"></textarea>') +
      save('createProject', '创建并指派'),
  );
v.platformDetail = () => {
  let p = projects.find((x) => x.name === selectedPlatform) || projects[0];
  return (
    title(p.name, '平台管理信息') +
    panel(
      '<h2>项目基本信息</h2><p>' +
        esc(p.desc) +
        '</p>' +
        field('项目负责人', '<span>' + esc(p.owner) + '</span>') +
        action('projectJobs', '查看处理状态', 'text') +
        link('transfer', '交接负责人'),
    )
  );
};
let jobProject = null;
v.jobs = () =>
  title(
    '处理状态',
    jobProject ? jobProject + ' · 项目处理任务' : '平台可查看项目、处理环节与错误码。',
    jobProject ? action('allJobs', '全部项目') : '',
  ) +
  panel(
    table(
      ['项目', '任务编号', '环节', '处理状态'],
      [
        [esc(project), 'JOB-104', '资料审核', pill('待确认')],
        [esc(project), 'JOB-103', '文档解析', pill('解析失败') + ' · PARSE_FORMAT'],
        [esc(project), 'JOB-102', '运行解析', pill('就绪')],
      ].filter((row) => !jobProject || row[0] === esc(jobProject)),
    ),
  );
let editingAccount = '';
const disabledUsers = new Set();
v.accounts = () =>
  title('平台账号', '账号可用后，负责人可以将其加入项目。', save('addAccount', '创建普通账号')) +
  panel(
    table(
      ['账号', '状态', '操作'],
      users.map((name) => [
        esc(name),
        pill(disabledUsers.has(name) ? '已停用' : '可用'),
        action('editAccount', '管理', 'text', name),
      ]),
    ),
  );
v.accountEdit = () =>
  title(editingAccount ? '管理普通账号' : '创建普通账号', '平台身份与项目角色分别管理。') +
  panel(
    field(
      '账号标识',
      editingAccount ? '<span>' + esc(editingAccount) + '</span>' : input('loginName'),
    ) +
      (editingAccount ? '' : field('显示名称', input('displayName'))) +
      field(
        '临时密码',
        input('initialPass', '', 'password'),
        '设置后，用户下次登录须先修改密码。',
      ) +
      '<div class="actions">' +
      save('writeAccount', editingAccount ? '重设临时密码' : '创建账号') +
      (editingAccount
        ? action('toggleAccount', disabledUsers.has(editingAccount) ? '启用账号' : '停用账号')
        : '') +
      '</div>',
  );
v.password = () =>
  title('修改密码', '首次使用临时密码时，完成修改后进入工作区。') +
  panel(
    field('当前密码', input('oldPass', '', 'password')) +
      field('新密码', input('newPass', '', 'password')) +
      field('确认新密码', input('confirmPass', '', 'password')) +
      save('changePass', '保存并重新登录'),
  );
v.transfer = () => {
  const p = projects.find((x) => x.name === selectedPlatform) || projects[0];
  const pending = p.pending;
  return (
    title('审核负责人交接', p.name) +
    panel(
      pending
        ? '<h2>待管理员确认</h2><p>' +
            esc(p.owner) +
            ' → ' +
            esc(pending.to) +
            '；原负责人转为' +
            esc(pending.former) +
            '</p><p>确认后接任人加入受限文档审核读者名单，授权变更将留痕。</p>' +
            field('退回原因（退回时必填）', input('returnReason')) +
            save('approveHandover', '确认申请') +
            ' ' +
            action('returnHandover', '退回申请')
        : '<p>暂无待确认的申请。</p><details><summary>原负责人已离岗 · 管理员代办</summary>' +
            handoverForm(p) +
            field('离岗代办原因', input('proxyReason')) +
            save('proxyHandover', '确认离岗代办') +
            '</details>',
    ) +
    handoverTrail(p)
  );
};
function handoverForm(p) {
  return (
    field(
      '接任成员',
      select(
        'successor',
        (p === projects[0] ? people : [])
          .filter((x) => x.name !== p.owner && !disabledUsers.has(x.name))
          .map((x) => x.name),
      ),
    ) +
    field('原负责人交接后的角色', select('formerRole', ['开发工程师', '测试工程师'])) +
    '<p>生效时会更新受限资料的审核读者并记录变更。</p>'
  );
}
function handoverTrail(p) {
  return (p.trail || []).length
    ? panel('<h3>交接记录</h3>' + p.trail.map((t) => '<p>' + esc(t) + '</p>').join(''))
    : '';
}
v.handoverRequest = () => {
  const p = projects[0];
  return (
    title('发起负责人交接', p.name) +
    settingsNav() +
    panel(
      p.pending
        ? '<p>已申请交接给 ' +
            esc(p.pending.to) +
            '，等待平台管理员确认。当前负责人仍为 ' +
            esc(p.owner) +
            '。</p>'
        : handoverForm(p) + save('requestHandover', '提交交接申请'),
    ) +
    handoverTrail(p)
  );
};
const views = v;
let versionSource = null;
const elVal = (id) => document.getElementById(id)?.value.trim() || '';
function openDoc(id) {
  docId = id;
  docVersion = current()?.version || '';
  go('document');
}
function showEvidence(key) {
  if (role === 'admin') {
    notify('需要项目访问权限');
    return;
  }
  const data = {
    log: [
      '运行日志',
      '周一响应时间复测 · output.xml',
      '结果日志，第 18 行',
      'response_time = 2.5 s',
    ],
    v1: ['验收依据', '响应时间技术规范 · V1', '响应时间要求 · 第 4 页', '设备应在 3 秒内响应。'],
    v2: ['验收依据', '响应时间技术规范 · V2', '响应时间要求 · 第 4 页', '设备应在 2 秒内响应。'],
    draft: [
      '条款原文',
      '本次提问附带的条款',
      '本人对话 · 提问摘录',
      '拒绝操作时，应记录拒绝原因。',
    ],
  };
  let [label, name, loc, quote] = data[key] || data.v2;
  if (['v1', 'v2'].includes(key) && !docs().some((d) => d.id === 'spec')) {
    notify('该引用当前不可见');
    return;
  }
  drawer(
    '<h2>' +
      label +
      '</h2><h3>' +
      name +
      '</h3><p class="muted">' +
      loc +
      '</p><blockquote class="mark">' +
      quote +
      '</blockquote>' +
      (key === 'draft' ? '' : action('source', '打开完整来源', 'text', key)),
  );
}
function goBack(next) {
  const origin = returnChat;
  returnChat = '';
  navFile = '';
  attachment = null;
  go(next);
  if (origin)
    drawer(
      '<h2>操作已提交</h2>' +
        action('go', '回到原对话', 'primary', origin) +
        ' ' +
        action('close', '留在本页'),
    );
}
function chooseRole(r) {
  role = r;
  question = '';
  chatFile = '';
  returnChat = '';
  navFile = '';
  attachment = null;
  selectedUser = '';
  query = '';
  filter = '全部状态';
  docId = 'spec';
  docVersion = 'V2';
  go(r === 'admin' ? 'platform' : 'chat');
}
function applyHandover(proxy) {
  const p = projects.find((x) => x.name === selectedPlatform) || projects[0];
  if (role !== 'admin' || (proxy ? !!p.pending : !p.pending)) return;
  const reason = elVal('proxyReason');
  if (proxy && !reason) return fieldError('proxyReason', '请填写原负责人离岗的代办原因');
  const change = proxy ? { to: elVal('successor'), former: elVal('formerRole') } : p.pending;
  const target = people.find((x) => x.name === change.to);
  if (p !== projects[0] || !target || disabledUsers.has(target.name) || target.name === p.owner)
    return notify('成员状态已变化，请重新核对');
  const old = p.owner;
  people.find((x) => x.name === old).role = change.former;
  target.role = '项目负责人';
  p.owner = target.name;
  names.owner = p.owner;
  for (const d of queryDocs)
    if (d.access === '限定读者') {
      d.explicitReaders ??= d.readers.filter((n) => n !== old);
      d.readers = [...new Set([...d.explicitReaders, p.owner])];
    }
  for (const [k, roleTitle] of [
    ['dev', '开发工程师'],
    ['test', '测试工程师'],
  ]) {
    names[k] = people.find((x) => x.role === roleTitle)?.name || '';
    const opt = $('#role option[value="' + k + '"]');
    opt.disabled = !names[k];
    opt.textContent = roleTitle + ' · ' + (names[k] || '暂无成员');
  }
  $('#role option[value="owner"]').textContent = '项目负责人 · ' + p.owner;
  (p.trail ??= []).unshift(
    (proxy ? '管理员离岗代办（' + reason + '）' : '管理员确认') +
      '：' +
      old +
      ' → ' +
      p.owner +
      '；已记录审核读者变更',
  );
  p.pending = null;
  draw();
  notify('交接已生效');
}
const acts = {
  projectJobs: () => {
    jobProject = selectedPlatform;
    go('jobs');
  },
  allJobs: () => {
    jobProject = null;
    go('jobs');
  },
  addAccount: () => {
    editingAccount = '';
    go('accountEdit');
  },
  editAccount: (n) => {
    editingAccount = n;
    go('accountEdit');
  },
  writeAccount: () => {
    if (
      !elVal('initialPass') ||
      (!editingAccount && (!elVal('loginName') || !elVal('displayName')))
    ) {
      notify('请填写账号和临时密码');
      return;
    }
    if (!editingAccount) {
      let n = elVal('displayName');
      if (users.includes(n)) {
        notify('样例账号已存在');
        return;
      }
      users.push(n);
    }
    go('accounts');
    notify('账号已保存，首次登录需修改密码');
  },
  toggleAccount: () => {
    if (projects.some((p) => p.owner === editingAccount)) {
      notify('请先交接该账号负责的项目');
      return;
    }
    if (disabledUsers.has(editingAccount)) disabledUsers.delete(editingAccount);
    else disabledUsers.add(editingAccount);
    go('accounts');
  },
  changePass: () => {
    if (!elVal('oldPass') || !elVal('newPass') || elVal('newPass') !== elVal('confirmPass')) {
      notify('请核对当前密码和两次新密码');
      return;
    }
    go('login');
    notify('样稿已返回登录页');
  },
  requestHandover: () => {
    const p = projects[0],
      to = elVal('successor');
    if (role !== 'owner' || p.pending || !to) return notify('请选择可接任的成员');
    p.pending = { to, former: elVal('formerRole') };
    (p.trail ??= []).unshift(p.owner + ' 发起交接给 ' + to);
    draw();
    notify('已提交，原负责人继续履职');
  },
  approveHandover: () => applyHandover(false),
  proxyHandover: () => applyHandover(true),
  returnHandover: () => {
    const p = projects.find((x) => x.name === selectedPlatform);
    if (role !== 'admin' || !p?.pending || !elVal('returnReason'))
      return fieldError('returnReason', '请填写退回原因');
    (p.trail ??= []).unshift('管理员退回：' + elVal('returnReason'));
    p.pending = null;
    draw();
  },
  go: go,
  map,
  close: () => closeDrawer(),
  demo: (k) => {
    if (k === 'review') {
      docId = 'keyword';
      docVersion = 'V3';
    } else if (['document', 'access'].includes(k)) {
      docId = 'spec';
      docVersion = 'V2';
    }
    if (k === 'member') selectedUser = '';
    demoSwitch(k);
  },
  'switch-project': () =>
    drawer(
      '<h2>' +
        (role === 'admin' ? '平台工作区' : '切换项目') +
        '</h2>' +
        (role === 'admin' ? link('platform', '平台项目') : action('go', esc(project), '', 'chat')),
    ),
  'sign-in': () => go(role === 'admin' ? 'platform' : 'chat'),
  newchat: () => {
    question = '';
    chatFile = '';
    go('chat');
  },
  history: () =>
    drawer('<h2>个人对话</h2>' + link('answer', '周一复测核对') + link('robot', '拒绝原因用例')),
  send: () => {
    let message = elVal('ask');
    if (!message && !$('#chatfile')?.files.length) {
      return fieldError('ask', '请先输入问题或选择附件');
    }
    answerBasis = historyBasis[0];
    question = message || '请评审附件';
    chatFile = $('#chatfile')?.files[0]?.name || '';
    attachment = $('#chatfile')?.files[0] || null;
    go(/用例|robot|草稿|写一/i.test(message) || /\.robot$/i.test(chatFile) ? 'robot' : 'answer');
    notify('样稿展示预设回答');
  },
  evidence: showEvidence,
  source: (k) => {
    if (k === 'log') {
      selectedRun = 'R-026';
      go('run');
    } else {
      docId = 'spec';
      docVersion = k === 'v1' ? 'V1' : 'V2';
      go('document');
    }
  },
  copy: async () => {
    const txt =
      page === 'robot'
        ? robotText + '\n依据：本次提问附带的条款。'
        : comparison().summary +
          '\n实测 2.5 秒（周一复测日志，第 18 行）。V1 第 4 页 ≤3 秒。' +
          (comparison().currentKnown ? 'V2 第 4 页 ≤2 秒。' : '');
    try {
      await navigator.clipboard.writeText(txt);
      notify('已复制');
    } catch {
      drawer('<h2>复制内容</h2><textarea rows="10" readonly>' + esc(txt) + '</textarea>');
    }
  },
  carryRun: () => {
    returnChat = page;
    navFile = chatFile;
    go('register');
  },
  carryDoc: () => {
    returnChat = page;
    navFile = chatFile;
    versionSource = null;
    go('submit');
  },
  newdoc: () => {
    returnChat = '';
    navFile = '';
    versionSource = null;
    go('submit');
  },
  newVersion: () => {
    versionSource = current();
    navFile = '';
    returnChat = '';
    go('submit');
    $('#docName').value = versionSource.name;
    $('#docType').value = versionSource.type;
    $('#version').value = '';
  },
  submitDoc: () => {
    const name = elVal('docName'),
      version = elVal('version'),
      file = $('#upload')?.files[0]?.name || navFile;
    if (!name || !version || !file) {
      notify('请填写名称、版本并选择资料文件');
      return;
    }
    if (!/\.(pdf|md|docx)$/i.test(file)) {
      notify('请选择 PDF、MD 或 DOCX 资料');
      return;
    }
    if (queryDocs.some((d) => d.name === name && d.version === version)) {
      notify('此版本已存在');
      return;
    }
    queryDocs.unshift({
      id: 'doc' + Date.now(),
      family: versionSource?.family || versionSource?.id,
      original: $('#upload')?.files[0] || attachment,
      name,
      version,
      type: elVal('docType'),
      review: '待确认',
      index: '未启动',
      author: names[role],
      access: versionSource?.access || '项目内共享',
      readers: versionSource?.readers.slice() || people.map((p) => p.name),
      explicitReaders: versionSource?.explicitReaders?.slice(),
      excerpt: '此处显示新文件的正文。样稿仅模拟资料提交。',
    });
    versionSource = null;
    goBack('library');
    notify('资料已进入待确认列表');
  },
  original: () => {
    const d = current();
    if (!d) return;
    const selected = page === 'document' ? docVersion : d.version;
    const excerpt = d.id === 'spec' && selected === 'V1' ? '设备应在 3 秒内响应。' : d.excerpt;
    const blob = d.original || new Blob([excerpt], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob),
      a = document.createElement('a');
    a.href = url;
    a.download = blob.name || d.name + '-' + selected + '.md';
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  },
  openDoc,
  version: (n) => {
    docVersion = n;
    draw();
  },
  reviewdoc: () => go('review'),
  publish: () => {
    let d = current();
    if (!d || d.review !== '待确认') {
      notify('该版本已处理');
      return;
    }
    d.review = '已发布';
    d.index = '排队解析';
    setTimeout(() => {
      d.index = '就绪';
      if (['library', 'document', 'basis'].includes(page)) draw();
    }, 900);
    go('library');
    notify('已批准，开始解析；当前验收包继续生效');
  },
  reject: () => {
    if (!elVal('reason')) {
      return fieldError('reason', '请填写退回原因');
    }
    let d = current();
    if (!d || d.review !== '待确认') return;
    d.review = '已退回';
    d.reason = elVal('reason');
    go('library');
    notify('资料已退回');
  },
  saveAccess: () => {
    let d = current();
    if (!d) return;
    d.access = elVal('visibility');
    const keepOwnerExplicit = d.explicitReaders?.includes(names.owner);
    d.explicitReaders = Array.from(document.querySelectorAll('.reader:checked:not(:disabled)')).map(
      (n) => n.value,
    );
    if (keepOwnerExplicit) d.explicitReaders.push(names.owner);
    d.readers = [...new Set([names.owner, ...d.explicitReaders])];
    for (const sibling of queryDocs.filter((x) => (x.family || x.id) === (d.family || d.id))) {
      sibling.access = d.access;
      sibling.readers = [...d.readers];
      sibling.explicitReaders = [...d.explicitReaders];
    }
    go('document');
    notify('读者范围已保存');
  },
  changeBasis: () => {
    const label = elVal('packageLabel'),
      selected = [...document.querySelectorAll('.bundlefile:checked')].map((n) =>
        currentBundleFile(n.value),
      );
    if (!label) return fieldError('packageLabel', '请填写新的验收包版本名');
    if (!selected.length || selected.some((d) => !d)) return notify('请至少选择一份就绪文件');
    if (
      historyBasis.some((x) => x.label === label) ||
      new Set(selected.map((d) => d.family || d.id)).size !== selected.length
    )
      return notify('包名需唯一，每份资料只可纳入一个版本');
    historyBasis.unshift({ label, files: selected.map((d) => declaredFile(d.id, d.version)) });
    draw();
    notify('整套依据已生效，历史包已保留');
  },
  newrun: () => {
    returnChat = '';
    navFile = '';
    go('register');
  },
  saveRun: () => {
    const name = elVal('runName'),
      file = $('#runFile')?.files[0]?.name || navFile,
      time = elVal('runTime');
    if (!name || !time || !file || !file.toLowerCase().endsWith('.xml')) {
      notify('请填写名称、时间并选择 XML 结果');
      return;
    }
    runRows.unshift({
      id: 'R-' + (27 + runRows.length - 2),
      name,
      file,
      who: names[role],
      at: time.replace('T', ' '),
      status: '处理中',
    });
    goBack('runs');
    notify('运行已登记，等待解析');
  },
  raw: () =>
    drawer(
      '<h2>原始运行结果</h2><p>output.xml · 周一响应时间复测</p><pre>&lt;test name="响应时间"&gt;\n  &lt;status status="PASS" elapsed="2.5"/&gt;\n&lt;/test&gt;</pre>'.replaceAll(
        '\n',
        '<br>',
      ),
    ),
  openRun: (id) => {
    selectedRun = id;
    go('run');
  },
  askRun: () => {
    question = '帮我核对这次复测是否达标';
    go('answer');
  },
  saveProject: () => {
    const n = elVal('projName');
    if (!n) {
      notify('请输入项目名称');
      return;
    }
    if (selectedPlatform === project) selectedPlatform = n;
    project = n;
    desc = elVal('projDesc');
    projects[0].name = n;
    projects[0].desc = desc;
    draw();
    notify('项目信息已保存');
  },
  newMember: () => {
    selectedUser = '';
    go('member');
  },
  editMember: (name) => {
    selectedUser = name;
    go('member');
  },
  saveMember: () => {
    let name = selectedUser || elVal('newUser'),
      r = elVal('memberRole');
    if (!name) {
      notify('请选择可用账号');
      return;
    }
    let p = people.find((x) => x.name === name);
    if (p) p.role = r;
    else people.push({ name, role: r });
    go('members');
    notify('成员已保存');
  },
  removeMember: () =>
    drawer(
      '<h2>移出 ' +
        esc(selectedUser) +
        '？</h2><p>该账号将失去本项目的访问权限。</p>' +
        action('confirmRemove', '确认移出', 'warn') +
        ' ' +
        action('close', '取消'),
    ),
  confirmRemove: () => {
    const i = people.findIndex((p) => p.name === selectedUser && p.role !== '项目负责人');
    if (i >= 0) people.splice(i, 1);
    go('members');
    notify('成员已移出');
  },
  newProject: () => go('create'),
  createProject: () => {
    let n = elVal('newProjName');
    if (!n || projects.some((p) => p.name === n)) {
      notify('请输入未使用的项目名称');
      return;
    }
    projects.push({ name: n, owner: elVal('newOwner'), desc: elVal('newDesc') });
    selectedPlatform = n;
    go('platformDetail');
    notify('项目与首位负责人已创建');
  },
  adminDetail: (n) => {
    selectedPlatform = n;
    go('platformDetail');
  },
};
document.addEventListener('click', (e) => {
  const b = e.target.closest('button[data-action],.veil');
  if (b) acts[b.dataset.action]?.(b.dataset.value);
});
document.addEventListener('input', (e) => {
  clearFieldError(e.target);
  if (e.target.id === 'findDoc') {
    query = e.target.value;
    $('#docrows').innerHTML = docRows();
  }
  if (e.target.id === 'historySearch') {
    document
      .querySelectorAll('.history [data-action="go"]')
      .forEach((b) => (b.hidden = !b.textContent.includes(e.target.value)));
  }
});
document.addEventListener('change', (e) => {
  if (e.target.id === 'role') chooseRole(e.target.value);
  if (e.target.id === 'docFilter') {
    filter = e.target.value;
    $('#docrows').innerHTML = docRows();
  }
});
document.addEventListener('keydown', (e) => {
  if (e.key === 'Escape') {
    closeDrawer();
    return;
  }
  if (e.key === 'Tab' && $('#overlay .drawer')) {
    const nodes = [
      ...$('#overlay .drawer').querySelectorAll(
        'button:not([disabled]),a[href],input:not([disabled]),select:not([disabled]),textarea:not([disabled]),summary',
      ),
    ].filter((n) => n.getClientRects().length);
    const first = nodes[0],
      last = nodes.at(-1);
    if (e.shiftKey && document.activeElement === first) {
      e.preventDefault();
      last?.focus();
    } else if (!e.shiftKey && document.activeElement === last) {
      e.preventDefault();
      first?.focus();
    }
  }
  if (e.target.id === 'ask' && e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
    e.preventDefault();
    acts.send();
  }
});
window.addEventListener('hashchange', () => {
  const k = location.hash.slice(1);
  if (k !== page && pages[k]) demoSwitch(k);
});
const initial = location.hash.slice(1);
if (pages[initial]) acts.demo(initial);
else draw();
