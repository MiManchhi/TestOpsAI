const { chromium } = require(process.env.TESTOPS_PLAYWRIGHT_MODULE || 'playwright');
const { pathToFileURL } = require('node:url');
const path = require('node:path');
const assert = require('node:assert/strict');
(async () => {
  const browser = await chromium.launch({
    ...(process.env.TESTOPS_BROWSER_PATH
      ? { executablePath: process.env.TESTOPS_BROWSER_PATH }
      : {}),
    headless: true,
  });
  try {
    const page = await browser.newPage({
      viewport: { width: 1440, height: 960 },
      deviceScaleFactor: 1,
    });
    page.setDefaultTimeout(5000);
    const errors = [];
    page.on('pageerror', (e) => errors.push(e.message));
    const base = pathToFileURL(path.resolve('docs/design/index.html')).href;
    const all = [
      'accounts',
      'accountEdit',
      'password',
      'handoverRequest',
      'transfer',
      'login',
      'account',
      'empty',
      'chat',
      'answer',
      'robot',
      'library',
      'submit',
      'document',
      'review',
      'access',
      'basis',
      'runs',
      'register',
      'run',
      'settings',
      'members',
      'member',
      'platform',
      'create',
      'platformDetail',
      'jobs',
    ];
    const snaps = ['library', 'review', 'members', 'settings', 'platform', 'jobs', 'run', 'basis'];
    const act = (k) => page.locator('[data-action="' + k + '"]').first();
    const open = async (k) => {
      await page.goto('about:blank');
      await page.goto(base + '#' + k);
      await page.locator('#surface h1, #surface .reply').first().waitFor({ timeout: 4000 });
    };
    for (const width of [1440, 390]) {
      await page.setViewportSize({ width, height: width === 1440 ? 960 : 844 });
      for (const k of all) {
        await open(k);
        assert.ok(
          await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1),
          'overflow ' + width + ' ' + k,
        );
        if (process.env.TESTOPS_REFRESH_PREVIEWS === '1' && width === 1440 && snaps.includes(k))
          await page.screenshot({ path: 'docs/design/previews/' + k + '.png' });
      }
      console.log(width, all.length, 'screens');
    }
    await page.setViewportSize({ width: 1440, height: 960 });
    await open('members');
    await act('newMember').click();
    await page.locator('#newUser').selectOption('周澄');
    await act('saveMember').click();
    await page.getByText('周澄', { exact: true }).waitFor();
    await page.getByRole('row').filter({ hasText: '周澄' }).getByRole('button').click();
    await act('removeMember').click();
    await act('confirmRemove').click();
    assert.equal(await page.getByText('周澄', { exact: true }).count(), 0);
    await open('chat');
    await page.locator('#role').selectOption('test');
    await page.locator('#ask').fill('帮我核对响应时间');
    await page.locator('#chatfile').setInputFiles({
      name: 'output.xml',
      mimeType: 'application/xml',
      buffer: Buffer.from('<robot/>'),
    });
    await act('send').click();
    await act('carryRun').click();
    await page.locator('#runName').fill('样稿复测');
    await act('saveRun').click();
    await page.getByRole('button', { name: '回到原对话' }).click();
    assert.equal(await page.locator('.reply h2').count(), 1);
    await page.locator('nav').getByRole('button', { name: '项目资料' }).click();
    await act('newdoc').click();
    await page.locator('#docName').fill('测试用规范');
    await page.locator('#upload').setInputFiles({
      name: 'test.md',
      mimeType: 'text/markdown',
      buffer: Buffer.from('requirement'),
    });
    await act('submitDoc').click();
    assert.equal(await page.getByRole('button', { name: '测试用规范', exact: true }).count(), 1);
    await page.locator('#role').selectOption('dev');
    await page.locator('nav').getByRole('button', { name: '项目资料' }).click();
    assert.equal(await page.getByRole('button', { name: '测试用规范', exact: true }).count(), 0);
    assert.equal(await page.getByRole('button', { name: '验收标书附件', exact: true }).count(), 0);
    await page.locator('#role').selectOption('owner');
    await page.locator('nav').getByRole('button', { name: '项目资料' }).click();
    await page.getByRole('button', { name: '测试用规范', exact: true }).click();
    await act('reviewdoc').click();
    await act('publish').click();
    await page.locator('#role').selectOption('dev');
    await page.locator('nav').getByRole('button', { name: '项目资料' }).click();
    assert.equal(await page.getByRole('button', { name: '测试用规范', exact: true }).count(), 1);
    await open('answer');
    await page.getByRole('button', { name: 'V2 条款 ↗' }).click();
    await page.getByRole('dialog').getByText('设备应在 2 秒内响应。').waitFor();
    await act('source').click();
    assert.equal(await page.locator('h1').innerText(), '响应时间技术规范');
    await open('create');
    await page.locator('#newProjName').fill('独立验收项目');
    await act('createProject').click();
    assert.equal(await page.locator('h1').innerText(), '独立验收项目');
    assert.equal(await page.locator('nav').getByRole('button', { name: '项目资料' }).count(), 0);
    await open('accounts');
    await act('addAccount').click();
    await page.locator('#loginName').fill('sample.qa');
    await page.locator('#displayName').fill('示例成员');
    await page.locator('#initialPass').fill('Sample-pass-2026!');
    await act('writeAccount').click();
    await page.getByText('示例成员', { exact: true }).waitFor();
    await open('handoverRequest');
    await page.locator('#successor').selectOption('王禾');
    await act('requestHandover').click();
    assert.match(await page.locator('main').innerText(), /等待平台管理员确认/);
    await page.evaluate(() => (location.hash = 'transfer'));
    await page.getByText('待管理员确认', { exact: true }).waitFor();
    await act('approveHandover').click();
    await page.getByText(/管理员确认：张岚 → 王禾/).waitFor();
    await page.locator('summary').filter({ hasText: '管理员代办' }).click();
    await act('proxyHandover').click();
    assert.equal(await page.locator('#proxyReason').getAttribute('aria-invalid'), 'true');
    await page.locator('#proxyReason').fill('示例离岗交接');
    await act('proxyHandover').click();
    await page.getByText(/管理员离岗代办（示例离岗交接）/).waitFor();
    await open('review');
    await act('reject').click();
    assert.equal(await page.locator('#reason').evaluate((e) => e === document.activeElement), true);
    await page.locator('#reason').fill('版本不符，请提供修订版');
    await act('reject').click();
    assert.match(
      await page.getByRole('row').filter({ hasText: 'Robot 关键字说明' }).innerText(),
      /已退回[\s\S]*未启动/,
    );
    await open('basis');
    await page.locator('#packageLabel').fill('验收包 V3');
    assert.equal(await page.locator('.bundlefile:checked').count(), 2);
    await act('changeBasis').click();
    await page.getByRole('heading', { name: '当前生效 · 验收包 V3' }).waitFor();
    const historical = page
      .locator('details')
      .filter({ has: page.getByText('验收包 V2', { exact: true }) });
    await historical.locator('summary').click();
    assert.match(await historical.innerText(), /响应时间技术规范[\s\S]*验收标书附件/);
    await open('answer');
    const evidence = page.getByRole('button', { name: 'V2 条款 ↗' });
    const size = await evidence.boundingBox();
    assert.ok(size.width >= 44 && size.height >= 44, 'evidence target');
    await evidence.focus();
    await page.keyboard.press('Enter');
    assert.equal(await page.locator('#surface').evaluate((e) => e.inert), true);
    await page.keyboard.press('Shift+Tab');
    assert.equal(await act('source').evaluate((e) => e === document.activeElement), true);
    await page.keyboard.press('Tab');
    assert.equal(
      await page
        .locator('.drawer [data-action="close"]')
        .evaluate((e) => e === document.activeElement),
      true,
    );
    await page.keyboard.press('Escape');
    assert.equal(await evidence.evaluate((e) => e === document.activeElement), true);
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await evidence.click();
    assert.equal(
      await page.locator('.drawer').evaluate((e) => getComputedStyle(e).animationName),
      'none',
    );
    assert.deepEqual(errors, []);
    console.log(
      'Passed review, transfer, bundle, membership, private files, mobile layout, target, focus and reduced motion.',
    );
  } finally {
    await browser.close();
  }
})().catch((e) => {
  console.error(e);
  process.exitCode = 1;
});
