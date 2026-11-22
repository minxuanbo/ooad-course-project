import { test, expect } from '@playwright/test'

test.skip(process.env.UI_ACCEPTANCE !== '1', '仅在独立的虚构验收库显式执行')
const password = process.env.UI_FIXTURE_PASSWORD
const run = Date.now().toString(36)

/** 通过登录页建立真实会话，口令由验收环境提供。 */
async function login(page, account = 'ui-admin') {
  if (!password) throw Error('请设置 UI_FIXTURE_PASSWORD')
  await page.goto('/login')
  await page.getByLabel('账号', { exact: true }).fill(account)
  await page.getByLabel('密码', { exact: true }).fill(password)
  await page.getByLabel('密码', { exact: true }).press('Enter')
  await expect(page.getByRole('navigation')).toBeVisible()
}

/** 回读真实后端；令牌只在内存使用，不写入测试输出。 */
async function api(page, path, method = 'GET', body) {
  return page.evaluate(
    async ({ path, method, body }) => {
      const response = await fetch('/api' + path, {
        method,
        headers: {
          'Content-Type': 'application/json',
          Authorization: 'Bearer ' + sessionStorage.getItem('rbacToken'),
        },
        body: body === undefined ? undefined : JSON.stringify(body),
      })
      const result = await response.json()
      if (!response.ok) throw Error(result.code + ': ' + result.message)
      return result.data
    },
    { path, method, body },
  )
}

/** 使用实际导航入口，等待页面查询完成。 */
async function nav(page, name) {
  await page
    .getByRole('navigation')
    .getByRole('link', { name, exact: true })
    .click()
  await expect(page.getByRole('heading', { name, level: 1 })).toBeVisible()
  if (name === '部门管理')
    await expect(page.getByRole('button', { name: '刷新目录' })).toBeEnabled()
  else
    await expect(
      page.getByRole('button', { name: '查询', exact: true }),
    ).toBeEnabled()
}

/** 页内筛选通过回车提交，等待后端结果进入表格。 */
async function search(page, label, value) {
  await expect(
    page.getByRole('button', { name: '查询', exact: true }),
  ).toBeEnabled()
  await page.getByLabel(label, { exact: true }).fill(value)
  const response = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      response.url().includes('q=' + encodeURIComponent(value)),
  )
  await page.getByLabel(label, { exact: true }).press('Enter')
  await response
  await expect(page.locator('tbody tr')).toHaveCount(1)
  await expect(page.locator('tbody')).toContainText(value)
}

/** 共享集合保存必须经过两次明确确认。 */
async function saveCollection(page, empty = false) {
  const dialog = page.getByRole('dialog')
  await dialog
    .getByRole('button', { name: '保存完整集合', exact: true })
    .click()
  await expect(dialog.getByRole('alert')).toContainText(
    empty ? '撤销全部关联' : '请再次确认',
  )
  await dialog
    .getByRole('button', {
      name: empty ? '确认撤销全部关联' : '确认保存完整集合',
      exact: true,
    })
    .click()
  await expect(dialog).toBeHidden()
}

test('R17/R20 窄屏所有管理页无文档横向溢出，日志快照总数可见', async ({
  page,
}, testInfo) => {
  await login(page)
  for (const width of [390, 540, 768, 1280]) {
    await page.setViewportSize({ width, height: 844 })
    for (const name of [
      '工作总览',
      '部门管理',
      '人员管理',
      '角色与权限',
      '资源与API',
      '鉴权日志',
    ]) {
      await page
        .getByRole('navigation')
        .getByRole('link', { name, exact: true })
        .click()
      await expect(page.getByRole('heading', { name, level: 1 })).toBeVisible()
      const layout = await page.evaluate(() => ({
        viewport: document.documentElement.clientWidth,
        width: document.documentElement.scrollWidth,
      }))
      expect(layout.width, `${width}px ${name}`).toBeLessThanOrEqual(
        layout.viewport,
      )
      if (name === '鉴权日志')
        await expect(
          page.getByText(/匹配 \d+ 条 · 已加载 \d+ 条/),
        ).toBeVisible()
      if (width === 390 || width === 1280)
        await page.screenshot({
          path: testInfo.outputPath(`${width}-${name}.png`),
          fullPage: true,
        })
    }
  }
})

test('R13/R17 角色键盘流程、焦点约束及共享修改取消/持久化', async ({
  page,
}) => {
  await login(page)
  await nav(page, '角色与权限')
  const opener = page.getByRole('button', { name: '新建角色', exact: true })
  await opener.focus()
  await page.keyboard.press('Enter')
  const dialog = page.getByRole('dialog')
  await expect(
    dialog.getByRole('button', { name: '关闭', exact: true }),
  ).toBeFocused()
  await page.keyboard.press('Tab')
  await expect(dialog.getByLabel('角色编码')).toBeFocused()
  await page.keyboard.type('ui-e2e-' + run)
  await page.keyboard.press('Tab')
  await expect(dialog.getByLabel('角色名称')).toBeFocused()
  await page.keyboard.insertText('验收角色' + run)
  await page.keyboard.press('Tab')
  await expect(
    dialog.getByRole('button', { name: '取消', exact: true }),
  ).toBeFocused()
  await page.keyboard.press('Tab')
  await expect(
    dialog.getByRole('button', { name: '保存', exact: true }),
  ).toBeFocused()
  await page.keyboard.press('Tab')
  await expect(
    dialog.getByRole('button', { name: '关闭', exact: true }),
  ).toBeFocused()
  await page.keyboard.press('Shift+Tab')
  await page.keyboard.press('Enter')
  await expect(dialog).toBeHidden()
  await expect(opener).toBeFocused()
  await search(page, '搜索角色', 'ui-e2e-' + run)
  await page.getByRole('button', { name: '编辑定义' }).click()
  await dialog.getByLabel('角色名称').fill('取消的名称')
  await dialog.getByRole('button', { name: '保存', exact: true }).click()
  await expect(dialog.getByRole('alert')).toContainText('共享配置')
  await page.keyboard.press('Escape')
  await expect(page.locator('tbody')).toContainText('验收角色' + run)
  await page.getByRole('button', { name: '编辑定义' }).click()
  await dialog.getByLabel('角色名称').fill('已更新' + run)
  await dialog.getByLabel('启用角色').uncheck()
  await dialog.getByRole('button', { name: '保存', exact: true }).click()
  await dialog.getByRole('button', { name: '确认保存共享配置' }).click()
  await expect(dialog).toBeHidden()
  await page.reload()
  await search(page, '搜索角色', 'ui-e2e-' + run)
  await expect(page.locator('tbody')).toContainText('已更新' + run)
  await expect(page.locator('tbody')).toContainText('停用')
})

test('R23 人员跨页角色、空集合取消/确认、调动和停用持久化', async ({
  page,
}) => {
  await login(page)
  await api(page, '/users/5/roles', 'PUT', { ids: [4] })
  await api(page, '/users/5/department', 'PUT', { departmentId: 2 })
  await nav(page, '人员管理')
  await search(page, '搜索人员', 'ui-user-05')
  await page.getByRole('button', { name: '调整角色' }).click()
  const dialog = page.getByRole('dialog')
  await expect(dialog.getByText('已选 1 项')).toBeVisible()
  await dialog.getByRole('checkbox', { name: /验收空角色05/ }).check()
  await dialog.getByRole('button', { name: '下一页' }).click()
  await dialog.getByRole('checkbox', { name: /验收空角色35/ }).check()
  await expect(dialog.getByText('已选 3 项')).toBeVisible()
  await saveCollection(page)
  expect(
    (await api(page, '/users/5/roles')).roles
      .map((item) => item.id)
      .sort((a, b) => a - b),
  ).toEqual([4, 5, 35])
  await page.reload()
  await search(page, '搜索人员', 'ui-user-05')
  await page.getByRole('button', { name: '调整角色' }).click()
  await expect(dialog.getByText('已选 3 项')).toBeVisible()
  for (const id of [4, 5, 35])
    await dialog
      .getByRole('button', { name: '移除ID ' + id, exact: true })
      .click()
  await dialog
    .getByRole('button', { name: '保存完整集合', exact: true })
    .click()
  await expect(dialog.getByRole('alert')).toContainText('将撤销全部关联')
  await page.keyboard.press('Escape')
  expect((await api(page, '/users/5/roles')).roles).toHaveLength(3)
  await page.getByRole('button', { name: '调整角色' }).click()
  for (const id of [4, 5, 35])
    await dialog
      .getByRole('button', { name: '移除ID ' + id, exact: true })
      .click()
  await saveCollection(page, true)
  expect((await api(page, '/users/5/roles')).roles).toHaveLength(0)
  await page.getByRole('button', { name: '调动归属' }).click()
  await dialog.getByLabel('目标部门').selectOption('4')
  await dialog.getByRole('button', { name: '取消', exact: true }).click()
  await expect(page.locator('tbody')).toContainText('工程部')
  await page.getByRole('button', { name: '调动归属' }).click()
  await dialog.getByLabel('目标部门').selectOption('4')
  await dialog.getByRole('button', { name: '确认调动' }).click()
  await expect(dialog).toBeHidden()
  await expect(page.locator('tbody')).toContainText('财务部')
  await page.getByRole('button', { name: '停用', exact: true }).click()
  await page.keyboard.press('Escape')
  await expect(
    page.getByRole('button', { name: '停用', exact: true }),
  ).toBeVisible()
  await page.getByRole('button', { name: '停用', exact: true }).click()
  await dialog.getByRole('button', { name: '确认停用', exact: true }).click()
  await expect(dialog).toBeHidden()
  await page.reload()
  await search(page, '搜索人员', 'ui-user-05')
  await expect(page.locator('tbody')).toContainText('财务部')
  await expect(page.locator('tbody')).toContainText('停用')
  await expect(page.locator('tbody')).toContainText('虚构验收岗位')
})

test('R23 编辑中范围变化返回403并禁用再次保存，同级只读', async ({
  page,
  context,
}) => {
  const warnings = []
  page.on('console', (message) => {
    if (message.type() === 'warning' && message.text().includes('[Vue warn]'))
      warnings.push(message.text())
  })
  const admin = await context.newPage()
  await login(admin)
  await api(admin, '/users/6/department', 'PUT', { departmentId: 2 })
  await api(admin, '/users/6/roles', 'PUT', { ids: [4] })
  await login(page, 'ui-manager')
  await expect(page.getByRole('link', { name: '角色与权限' })).toHaveCount(0)
  await nav(page, '人员管理')
  await search(page, '搜索人员', 'ui-peer')
  await expect(page.getByRole('button', { name: '调整角色' })).toHaveCount(0)
  await page.getByRole('button', { name: '查看角色' }).click()
  await expect(
    page.getByRole('dialog').getByRole('button', { name: '保存完整集合' }),
  ).toHaveCount(0)
  await page.keyboard.press('Escape')
  await search(page, '搜索人员', 'ui-user-06')
  await page.getByRole('button', { name: '调整角色' }).click()
  const dialog = page.getByRole('dialog')
  await expect(dialog.getByText('已选 1 项')).toBeVisible()
  await dialog.getByRole('button', { name: '移除ID 4', exact: true }).click()
  await api(admin, '/users/6/department', 'PUT', { departmentId: 4 })
  await dialog
    .getByRole('button', { name: '保存完整集合', exact: true })
    .click()
  const denied = page.waitForResponse(
    (response) =>
      response.url().endsWith('/users/6/roles') &&
      response.request().method() === 'PUT',
  )
  await dialog.getByRole('button', { name: '确认撤销全部关联' }).click()
  expect((await denied).status()).toBe(403)
  await expect(dialog.getByText(/已禁用本次保存/)).toBeVisible()
  await expect(
    dialog.getByRole('button', { name: '确认撤销全部关联' }),
  ).toBeDisabled()
  expect(
    (await api(admin, '/users/6/roles')).roles.map((item) => item.id),
  ).toEqual([4])
  await page.keyboard.press('Escape')
  await expect(page.locator('tbody')).toContainText('没有符合条件')
  await api(admin, '/users/6/department', 'PUT', { departmentId: 2 })
  await admin.close()
  expect(warnings).toEqual([])
})

test('R08/R09/R13 角色权限非空/空集合、启停与授权即时失效', async ({
  page,
  context,
}) => {
  await login(page)
  await api(page, '/roles/5', 'PATCH', { name: '验收空角色05', enabled: true })
  await api(page, '/roles/5/permissions', 'PUT', { ids: [] })
  await api(page, '/users/7/roles', 'PUT', { ids: [4, 5] })
  await nav(page, '角色与权限')
  await search(page, '搜索角色', 'ui-extra-05')
  await page.getByRole('button', { name: '配置权限' }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByRole('checkbox', { name: /rbac.overview.read/ }).check()
  await saveCollection(page)
  expect((await api(page, '/roles/5/permissions')).ids).toEqual([1])
  const observer = await context.newPage()
  await login(observer, 'ui-user-07')
  await page.getByRole('button', { name: '编辑定义' }).click()
  await dialog.getByLabel('启用角色').uncheck()
  await dialog.getByRole('button', { name: '保存', exact: true }).click()
  await dialog.getByRole('button', { name: '确认保存共享配置' }).click()
  await expect(dialog).toBeHidden()
  await observer.reload()
  await expect(
    observer.getByText('当前账号没有范围总览权限。', { exact: false }),
  ).toBeVisible()
  expect(
    await observer.evaluate(
      async () =>
        (
          await fetch('/api/overview', {
            headers: {
              Authorization: 'Bearer ' + sessionStorage.getItem('rbacToken'),
            },
          })
        ).status,
    ),
  ).toBe(403)
  expect(
    (await api(page, '/users/7/roles')).roles.map((item) => item.id),
  ).toEqual([4, 5])
  await page.getByRole('button', { name: '编辑定义' }).click()
  await dialog.getByLabel('启用角色').check()
  await dialog.getByRole('button', { name: '保存', exact: true }).click()
  await dialog.getByRole('button', { name: '确认保存共享配置' }).click()
  await expect(dialog).toBeHidden()
  await login(observer, 'ui-user-07')
  await page.getByRole('button', { name: '配置权限' }).click()
  await dialog.getByRole('button', { name: '移除ID 1', exact: true }).click()
  await saveCollection(page, true)
  expect((await api(page, '/roles/5/permissions')).ids).toEqual([])
  await observer.reload()
  await expect(
    observer.getByText('当前账号没有范围总览权限。', { exact: false }),
  ).toBeVisible()
  expect(
    await observer.evaluate(
      async () =>
        (
          await fetch('/api/overview', {
            headers: {
              Authorization: 'Bearer ' + sessionStorage.getItem('rbacToken'),
            },
          })
        ).status,
    ),
  ).toBe(403)
  await observer.close()
})

test('R14 资源属性、API启停和映射的取消/保存/重载', async ({ page }) => {
  await login(page)
  await api(page, '/permissions/100', 'PATCH', {
    type: 'BUTTON',
    parentId: null,
    enabled: true,
  })
  await api(page, '/permissions/100/apis', 'PUT', { ids: [] })
  await api(page, '/api-resources/100', 'PATCH', { enabled: true })
  await nav(page, '资源与API')
  await page.getByLabel('搜索资源').fill('ui.fixture.permission')
  await page.getByRole('button', { name: '编辑属性' }).click()
  const dialog = page.getByRole('dialog')
  await dialog
    .getByRole('combobox', { name: '类型', exact: true })
    .selectOption('MENU')
  await dialog.getByRole('button', { name: '保存', exact: true }).click()
  await page.keyboard.press('Escape')
  await expect(page.locator('tbody')).toContainText('按钮')
  await page.getByRole('button', { name: '编辑属性' }).click()
  await dialog
    .getByRole('combobox', { name: '类型', exact: true })
    .selectOption('MENU')
  await dialog.getByLabel('启用资源').uncheck()
  await dialog.getByRole('button', { name: '保存', exact: true }).click()
  await dialog.getByRole('button', { name: '确认保存共享配置' }).click()
  await expect(dialog).toBeHidden()
  await page.reload()
  await expect(
    page.getByRole('button', { name: '查询', exact: true }),
  ).toBeEnabled()
  await page.getByLabel('搜索资源').fill('ui.fixture.permission')
  await expect(page.locator('tbody')).toContainText('菜单')
  await expect(page.locator('tbody')).toContainText('停用')
  await page.getByRole('button', { name: 'API映射' }).click()
  await dialog.getByRole('checkbox', { name: /GET \/api\/ui-fixture/ }).check()
  await saveCollection(page)
  expect((await api(page, '/permissions/100/apis')).ids).toEqual([100])
  await page.getByRole('button', { name: 'API映射' }).click()
  await dialog.getByRole('button', { name: '移除ID 100', exact: true }).click()
  await saveCollection(page, true)
  expect((await api(page, '/permissions/100/apis')).ids).toEqual([])
  await page.getByRole('button', { name: 'API资源', exact: true }).click()
  await search(page, '搜索资源', '/api/ui-fixture')
  await page.getByRole('button', { name: '启停设置' }).click()
  await dialog.getByLabel('启用资源').uncheck()
  await dialog.getByRole('button', { name: '保存', exact: true }).click()
  await dialog.getByRole('button', { name: '确认保存共享配置' }).click()
  await expect(dialog).toBeHidden()
  await expect(page.locator('tbody')).toContainText('停用')
  await page.getByRole('button', { name: '启停设置' }).click()
  await dialog.getByLabel('启用资源').check()
  await dialog.getByRole('button', { name: '保存', exact: true }).click()
  await dialog.getByRole('button', { name: '确认保存共享配置' }).click()
  await expect(dialog).toBeHidden()
  expect(
    (await api(page, '/api-resources?systemId=1&q=ui-fixture&page=0&size=20'))
      .items[0].enabled,
  ).toBe(true)
})

test('R16/R20 日志筛选快照、续批、详情键盘和时间校验', async ({ page }) => {
  await login(page)
  await nav(page, '鉴权日志')
  await page.getByLabel('每批条数').selectOption('10')
  await expect(page.getByText('筛选条件已改变', { exact: false })).toBeVisible()
  await expect(page.getByRole('button', { name: '加载下一批' })).toBeDisabled()
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(page.getByText(/已加载 10 条/)).toBeVisible()
  const before = await page.getByText(/匹配 \d+ 条 · 已加载/).innerText()
  await page.getByRole('button', { name: '加载下一批' }).click()
  await expect(page.getByText(/已加载 20 条/)).toBeVisible()
  expect(
    (await page.getByText(/匹配 \d+ 条 · 已加载/).innerText()).split(' · ')[0],
  ).toBe(before.split(' · ')[0])
  const opener = page.getByRole('button', { name: '详情', exact: true }).first()
  await opener.focus()
  await page.keyboard.press('Enter')
  await expect(page.getByRole('dialog')).toContainText('httpStatus')
  await page.keyboard.press('Escape')
  await expect(opener).toBeFocused()
  await page.getByLabel('账号（精确匹配）').fill('ui-does-not-exist')
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(page.getByText('没有符合条件的日志')).toBeVisible()
  await expect(page.getByText('匹配 0 条 · 已加载 0 条')).toBeVisible()
  await page.getByLabel('开始（北京时间，含）').fill('2026-11-19T10:00')
  await page.getByLabel('结束（北京时间，不含）').fill('2026-11-18T10:00')
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText(
    '开始时间必须早于结束时间',
  )
})

test('R07/R17 窄屏登录失败重试、键盘提交及退出', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/login')
  await page.getByLabel('账号', { exact: true }).focus()
  await page.keyboard.type('ui-admin')
  await page.keyboard.press('Tab')
  await expect(page.getByLabel('密码', { exact: true })).toBeFocused()
  await page.keyboard.type('incorrect-fixture-password')
  await page.keyboard.press('Enter')
  await expect(page.getByRole('alert')).not.toBeEmpty()
  await expect(page.getByLabel('账号', { exact: true })).toHaveValue('ui-admin')
  await page.getByLabel('密码', { exact: true }).fill(password)
  await page.keyboard.press('Tab')
  await expect(
    page.getByRole('button', { name: '显示', exact: true }),
  ).toBeFocused()
  await page.keyboard.press('Tab')
  await page.keyboard.press('Enter')
  await expect(page.getByRole('navigation')).toBeVisible()
  await page.getByRole('button', { name: '退出', exact: true }).focus()
  await page.keyboard.press('Enter')
  await expect(page).toHaveURL(/\/login$/)
})

test('R24/R17 窄屏部门键盘新增、重名重试、改名、取消与删除', async ({
  page,
}) => {
  await login(page)
  await page.setViewportSize({ width: 390, height: 844 })
  await nav(page, '部门管理')
  await page
    .getByRole('row')
    .filter({ hasText: '工程部' })
    .getByRole('button', { name: '新增下级' })
    .focus()
  await page.keyboard.press('Enter')
  const dialog = page.getByRole('dialog')
  await page.keyboard.press('Tab')
  await expect(
    dialog.getByRole('combobox', { name: '上级部门', exact: true }),
  ).toBeFocused()
  await page.keyboard.press('Tab')
  await expect(
    dialog.getByRole('combobox', { name: '组织类型', exact: true }),
  ).toBeFocused()
  await page.keyboard.press('Tab')
  await expect(dialog.getByLabel('部门名称')).toBeFocused()
  await page.keyboard.insertText('道路项目')
  await page.keyboard.press('Enter')
  await expect(dialog.getByRole('alert')).toContainText(
    'DEPARTMENT_NAME_CONFLICT',
  )
  await dialog.getByLabel('部门名称').fill('验收项目' + run)
  await page.keyboard.press('Enter')
  await expect(dialog).toBeHidden()
  const row = page.getByRole('row').filter({ hasText: '验收项目' + run })
  await row.getByRole('button', { name: '修改名称' }).click()
  await dialog.getByLabel('部门名称').fill('验收改名' + run)
  await dialog.getByLabel('部门名称').press('Enter')
  await expect(dialog).toBeHidden()
  const renamed = page.getByRole('row').filter({ hasText: '验收改名' + run })
  await renamed.getByRole('button', { name: '删除', exact: true }).click()
  await page.keyboard.press('Escape')
  await expect(renamed).toBeVisible()
  await renamed.getByRole('button', { name: '删除', exact: true }).click()
  await dialog.getByRole('button', { name: '确认删除' }).focus()
  await page.keyboard.press('Enter')
  await expect(dialog).toBeHidden()
  await page.reload()
  await expect(page.getByRole('button', { name: '刷新目录' })).toBeEnabled()
  await expect(renamed).toHaveCount(0)
  expect(
    (await api(page, '/departments')).some(
      (item) => item.name === '验收改名' + run,
    ),
  ).toBe(false)
})

test('R11/R17 完整集合读取和写入失败可重试，候选失败不丢已选项', async ({
  page,
}) => {
  await login(page)
  await api(page, '/users/8/roles', 'PUT', { ids: [4] })
  await nav(page, '人员管理')
  await search(page, '搜索人员', 'ui-user-08')
  const failure = {
    status: 503,
    contentType: 'application/json',
    body: JSON.stringify({
      code: 'UI_TEST_UNAVAILABLE',
      message: '验收模拟服务暂不可用',
      requestId: 'ui-test-retry',
    }),
  }
  let failRead = true,
    failWrite = true,
    failCandidates = true
  await page.route('**/api/users/8/roles', async (route) => {
    if (route.request().method() === 'GET' && failRead) {
      failRead = false
      await route.fulfill(failure)
    } else if (route.request().method() === 'PUT' && failWrite) {
      failWrite = false
      await route.fulfill(failure)
    } else await route.continue()
  })
  await page.route('**/api/users/8/assignable-roles?*', async (route) => {
    if (failCandidates) {
      failCandidates = false
      await route.fulfill(failure)
    } else await route.continue()
  })
  await page.getByRole('button', { name: '调整角色' }).click()
  const dialog = page.getByRole('dialog')
  await expect(
    dialog.getByRole('button', { name: '保存完整集合', exact: true }),
  ).toBeDisabled()
  await dialog.getByRole('button', { name: '完整重试' }).click()
  await expect(dialog.getByText('已选 1 项')).toBeVisible()
  await dialog.getByRole('button', { name: '重试候选' }).click()
  await dialog.getByRole('checkbox', { name: /验收空角色08/ }).check()
  await dialog
    .getByRole('button', { name: '保存完整集合', exact: true })
    .click()
  await dialog.getByRole('button', { name: '确认保存完整集合' }).click()
  await expect(dialog.locator('.error').first()).toContainText('ui-test-retry')
  await expect(dialog.getByText('已选 2 项')).toBeVisible()
  expect(
    (await api(page, '/users/8/roles')).roles.map((item) => item.id),
  ).toEqual([4])
  await dialog.getByRole('button', { name: '确认保存完整集合' }).click()
  await expect(dialog).toBeHidden()
  expect(
    (await api(page, '/users/8/roles')).roles.map((item) => item.id),
  ).toEqual([4, 8])
})

test('R15 最后维护者拒绝后保留草稿、请求号和原始授权', async ({ page }) => {
  await login(page)
  const before = (await api(page, '/roles/1/permissions')).ids
  await nav(page, '角色与权限')
  await search(page, '搜索角色', 'system-admin')
  await page.getByRole('button', { name: '配置权限' }).click()
  const dialog = page.getByRole('dialog')
  for (const id of before)
    await dialog
      .getByRole('button', { name: '移除ID ' + id, exact: true })
      .click()
  await dialog
    .getByRole('button', { name: '保存完整集合', exact: true })
    .click()
  await dialog.getByRole('button', { name: '确认撤销全部关联' }).click()
  await expect(dialog.locator('.error')).toContainText('LAST_ADMIN_REQUIRED')
  await expect(dialog.locator('.error')).toContainText('请求号')
  await expect(dialog.getByText('已选 0 项')).toBeVisible()
  expect((await api(page, '/roles/1/permissions')).ids).toEqual(before)
  await page.keyboard.press('Escape')
})

test('R14/R17 窄屏资源键盘导航、下拉、复选和二次确认', async ({
  page,
}, testInfo) => {
  await login(page)
  await api(page, '/permissions/100', 'PATCH', {
    type: 'MENU',
    parentId: null,
    enabled: false,
  })
  await page.setViewportSize({ width: 390, height: 844 })
  await page.getByRole('link', { name: '资源与API', exact: true }).focus()
  await page.keyboard.press('Enter')
  await expect(
    page.getByRole('button', { name: '查询', exact: true }),
  ).toBeEnabled()
  await page.getByLabel('搜索资源').focus()
  await page.keyboard.type('ui.fixture.permission')
  await page.getByRole('button', { name: '编辑属性' }).focus()
  await page.keyboard.press('Enter')
  const dialog = page.getByRole('dialog')
  await page.keyboard.press('Tab')
  await expect(
    dialog.getByRole('combobox', { name: '父权限', exact: true }),
  ).toBeFocused()
  await page.keyboard.press('Tab')
  await expect(
    dialog.getByRole('combobox', { name: '类型', exact: true }),
  ).toBeFocused()
  await page.keyboard.press('ArrowDown')
  await page.keyboard.press('Tab')
  await expect(dialog.getByRole('checkbox')).toBeFocused()
  await page.keyboard.press('Space')
  await page.keyboard.press('Tab')
  await page.keyboard.press('Tab')
  await expect(
    dialog.getByRole('button', { name: '保存', exact: true }),
  ).toBeFocused()
  await page.keyboard.press('Enter')
  await expect(dialog.getByRole('alert')).toBeVisible()
  await page.screenshot({ path: testInfo.outputPath('390-资源键盘确认.png') })
  expect(
    await dialog.evaluate(
      (element) => element.scrollWidth <= element.clientWidth,
    ),
  ).toBe(true)
  await page.keyboard.press('Enter')
  await expect(dialog).toBeHidden()
  const resource = (await api(page, '/permissions/tree?systemId=1')).find(
    (item) => item.id === 100,
  )
  expect(resource.type).toBe('BUTTON')
  expect(resource.enabled).toBe(true)
})

test('R14 权限父级保存与移回根级，树查询保留祖先', async ({ page }) => {
  await login(page)
  await nav(page, '资源与API')
  await page.getByLabel('搜索资源').fill('ui.fixture.permission')
  const target = page
    .getByRole('row')
    .filter({ hasText: 'ui.fixture.permission' })
  await target.getByRole('button', { name: '编辑属性' }).click()
  const dialog = page.getByRole('dialog')
  await dialog
    .getByRole('combobox', { name: '父权限', exact: true })
    .selectOption('101')
  await dialog.getByRole('button', { name: '保存', exact: true }).click()
  await dialog.getByRole('button', { name: '确认保存共享配置' }).click()
  await expect(dialog).toBeHidden()
  await expect(page.locator('tbody')).toContainText('验收父菜单')
  expect(
    (await api(page, '/permissions/tree?systemId=1'))
      .find((item) => item.id === 101)
      .children.map((item) => item.id),
  ).toContain(100)
  await target.getByRole('button', { name: '编辑属性' }).click()
  await dialog
    .getByRole('combobox', { name: '父权限', exact: true })
    .selectOption('')
  await dialog.getByRole('button', { name: '保存', exact: true }).click()
  await dialog.getByRole('button', { name: '确认保存共享配置' }).click()
  await expect(dialog).toBeHidden()
  expect(
    (await api(page, '/permissions/tree?systemId=1')).find(
      (item) => item.id === 100,
    ).parentId,
  ).toBeNull()
})
