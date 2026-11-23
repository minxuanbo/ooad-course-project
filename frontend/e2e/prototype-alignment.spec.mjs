import { test, expect } from '@playwright/test'
import { createRequire } from 'node:module'
import { mkdir } from 'node:fs/promises'
const require = createRequire(import.meta.url)
const { createDemoModel, DEMO_PASSWORD } = require('../../prototype/model.js')
const directory = new URL(
  '../../.local/ui-prototype-alignment-20261124/',
  import.meta.url,
).pathname.replace(/^\/([A-Za-z]:)/, '$1')

/** 仅供页面渲染的168人虚构夹具，不连接部署环境或冒充真实业务回归。 */
async function preview(page, allowed = null, eventOverride = null) {
  const model = createDemoModel()
  model.login('admin', DEMO_PASSWORD)
  const requests = []
  const types = { 公司: 'HEADQUARTERS', 分公司: 'COMPANY', 部门: 'DEPARTMENT', 项目: 'PROJECT' }
  const inverse = Object.fromEntries(Object.entries(types).map(([name, code]) => [code, name]))
  const role = (item) => ({ ...item, managementLevel: item.authorityLevel })
  const permissions = () => {
    const nodes = model.resources({ systemId: 1 }).permissions
    const append = (parentId) =>
      nodes
        .filter((node) => node.parentId === parentId)
        .map((node) => ({ ...node, children: append(node.id) }))
    return append(null)
  }
  await page.route('**/api/**', async (route) => {
    const request = route.request(),
      url = new URL(request.url()),
      path = url.pathname.slice(4),
      method = request.method()
    requests.push({ path, method, query: url.searchParams })
    let data = null
    try {
      const body = request.postDataJSON()
      if (path === '/me')
        data = {
          user: {
            id: 1,
            username: 'admin',
            name: '系统管理员',
            departmentId: 1,
            departmentName: '总公司',
            globalScope: true,
            routes: model
              .apis({ size: 100 })
              .items.filter((item) => !allowed || allowed.includes(item.method + ' ' + item.path))
              .map((item) => ({ method: item.method, path: item.path })),
          },
        }
      else if (path === '/overview') {
        const value = model.overview()
        data = {
          departmentName: value.scope.name,
          totalUsers: value.total,
          enabledUsers: value.enabled,
          disabledUsers: value.disabled,
          totalDepartments: value.departments,
          distribution: value.branches.map((item) => ({
            ...item,
            totalUsers: item.total,
            directUsers: model.list({ departmentId: item.id, departmentScope: 'DIRECT' }).total,
          })),
        }
      } else if (path === '/departments' && method === 'GET')
        data = model.departments().map((item) => ({
          ...item,
          type: types[item.type],
          canEdit: item.canRename,
          directUsers: item.direct,
          totalUsers: item.total,
        }))
      else if (path === '/departments' && method === 'POST')
        data = model.createDepartment({ ...body, type: inverse[body.type] })
      else if (/^\/departments\/\d+$/.test(path))
        data =
          method === 'DELETE'
            ? model.deleteDepartment(Number(path.split('/')[2]))
            : model.renameDepartment(Number(path.split('/')[2]), body.name)
      else if (path === '/users')
        data = model.list({
          departmentId: url.searchParams.get('departmentId') || undefined,
          departmentScope: url.searchParams.get('departmentScope') || 'SUBTREE',
          roleId: url.searchParams.has('roleId')
            ? Number(url.searchParams.get('roleId'))
            : undefined,
          q: url.searchParams.get('q') || '',
          enabled: url.searchParams.get('enabled') || '',
          page: Number(url.searchParams.get('page') || 0),
          size: Number(url.searchParams.get('size') || 20),
        })
      else if (path === '/systems')
        data = [{ id: 1, code: 'rbac', name: 'RBAC权限管理系统', enabled: true }]
      else if (path === '/permissions/tree') data = permissions()
      else if (/^\/permissions\/\d+\/apis$/.test(path)) {
        const id = Number(path.split('/')[2])
        const result =
          method === 'PUT' ? model.savePermissionApis(id, body.ids) : model.permissionApis(id)
        const apis = result.apis || []
        data = { ids: apis.map((item) => item.id), items: apis }
      } else if (/^\/permissions\/\d+$/.test(path))
        data = model.patchPermission(Number(path.split('/')[2]), body)
      else if (path === '/api-resources')
        data = model.apis({
          q: url.searchParams.get('q') || '',
          page: Number(url.searchParams.get('page') || 0),
          size: Number(url.searchParams.get('size') || 20),
        })
      else if (path === '/roles' && method === 'GET') {
        const result = model.roles({
          q: url.searchParams.get('q') || '',
          enabled: url.searchParams.get('enabled') || '',
          page: Number(url.searchParams.get('page') || 0),
          size: 20,
        })
        data = { ...result, items: result.items.map(role) }
      } else if (path === '/roles' && method === 'POST') data = role(model.createRole(body))
      else if (/^\/roles\/\d+\/permissions$/.test(path)) {
        const id = Number(path.split('/')[2])
        const result =
          method === 'PUT' ? model.saveRolePermissions(id, body.ids) : model.rolePermissions(id)
        data = { ids: result.permissions.map((item) => item.id), items: result.permissions }
      } else if (/^\/roles\/\d+$/.test(path))
        data = role(model.patchRole(Number(path.split('/')[2]), body))
      else if (path === '/authorization-logs') {
        const limit = Number(url.searchParams.get('limit') || 12)
        const result = model.logs({
          limit: limit === 5 ? 6 : limit,
          cursor: url.searchParams.get('cursor') || null,
        })
        data = {
          ...result,
          items: result.items
            .slice(0, limit)
            .map((item, index) => ({
              ...item,
              appCode: 'rbac',
              ...(index === 0 ? eventOverride : {}),
            })),
          systems: [{ id: 1, code: 'rbac', name: 'RBAC权限管理系统', enabled: true }],
        }
      } else throw Error('未定义的界面夹具：' + method + ' ' + path)
      await route.fulfill({
        status: method === 'POST' ? 201 : 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 'OK', data }),
      })
    } catch (error) {
      await route.fulfill({
        status: error.status || 500,
        contentType: 'application/json',
        body: JSON.stringify({
          code: error.code || 'UI_FIXTURE_ERROR',
          message: error.message,
          data: null,
        }),
      })
    }
  })
  await page.addInitScript(() => sessionStorage.setItem('rbacToken', 'ui-render-fixture'))
  return { model, requests }
}

test('R17 原型五页与人员页在桌面和窄屏可用，刷新统一放在标题右侧', async ({ page }) => {
  const errors = []
  page.on('pageerror', (error) => errors.push(error.message))
  await preview(page)
  await mkdir(directory, { recursive: true })
  for (const width of [1440, 768, 390]) {
    await page.setViewportSize({ width, height: width === 1440 ? 1000 : 844 })
    for (const [path, title, filename] of [
      ['/', '总览', 'overview'],
      ['/departments', '部门管理', 'departments'],
      ['/roles', '角色与权限', 'roles'],
      ['/resources', '资源与API', 'resources'],
      ['/logs', '鉴权日志', 'logs'],
      ['/users', '人员管理', 'users'],
    ]) {
      await page.goto(path)
      await expect(page.getByRole('heading', { name: title, exact: true, level: 1 })).toBeVisible()
      await expect(
        page.locator('#page-actions').getByRole('button', { name: '刷新', exact: true }),
      ).toBeEnabled()
      expect(
        await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1),
        title + ' overflow at ' + width,
      ).toBe(true)
      await expect(
        page.getByRole('navigation', { name: '管理工作区' }).locator('a.active'),
      ).toHaveCount(1)
      if (width === 1440)
        await page.screenshot({ path: directory + '/' + filename + '.png', fullPage: true })
    }
  }
  expect(errors).toEqual([])
})

test('R24 部门选择、直属员工与含下级、键盘创建及取消删除', async ({ page }) => {
  const { requests } = await preview(page)
  await page.goto('/departments')
  await page
    .locator('.department-tree .department-select')
    .filter({ hasText: '工程管理部' })
    .first()
    .click()
  await expect(page.locator('#current-department-title')).toHaveText('工程管理部')
  await page.getByRole('button', { name: '查看员工', exact: true }).first().click()
  const dialog = page.getByRole('dialog')
  await expect(dialog.getByLabel('查看范围')).toHaveValue('DIRECT')
  await dialog.getByLabel('查看范围').selectOption('SUBTREE')
  await expect(dialog).toContainText('滨江项目部')
  await page.keyboard.press('Escape')
  expect(
    requests.some(
      (item) => item.path === '/users' && item.query.get('departmentScope') === 'SUBTREE',
    ),
  ).toBe(true)
  await page.getByRole('button', { name: '新增下级', exact: true }).click()
  await dialog.getByLabel('部门名称').fill('界面验收空项目')
  await dialog.getByLabel('部门名称').press('Enter')
  await expect(dialog).toBeHidden()
  const row = page.getByRole('row').filter({ hasText: '界面验收空项目' })
  await row.getByRole('button', { name: '删除', exact: true }).click()
  await page.keyboard.press('Escape')
  await expect(row).toBeVisible()
})

test('R14 权限详情显示单操作真实映射并使用抽屉配置', async ({ page }) => {
  await preview(page)
  await page.goto('/resources')
  const tree = page.getByRole('group', { name: '权限目录节点' })
  await tree.getByRole('button', { name: '查询人员', exact: true }).click()
  await expect(page.locator('.permission-fields')).toContainText('rbac.users.read')
  await expect(page.locator('.mapped-api')).toHaveCount(1)
  await expect(page.locator('.mapped-api')).toContainText('/api/users')
  await tree.getByRole('button', { name: '查看人员角色', exact: true }).click()
  await expect(page.locator('.mapped-api')).toContainText('/api/users/{id}/roles')
  await page.getByRole('button', { name: '配置映射', exact: true }).click()
  await expect(page.getByRole('dialog')).toHaveClass(/drawer/)
  await page.keyboard.press('Escape')
  await page.getByRole('button', { name: 'API资源', exact: true }).click()
  await expect(page.locator('tbody tr')).toHaveCount(20)
})

test('R13 角色启停有独立确认，日志详情展示真实字段及来源IP', async ({ page }) => {
  await preview(page)
  await page.goto('/roles')
  const row = page.getByRole('row').filter({ hasText: 'DEMO_01' })
  await row.getByRole('button', { name: '停用', exact: true }).click()
  await page.getByRole('button', { name: '确认停用', exact: true }).click()
  await expect(row).toContainText('已停用')
  await page.goto('/logs')
  await page.getByRole('button', { name: '查看详情', exact: true }).first().click()
  const dialog = page.getByRole('dialog')
  await expect(dialog).toHaveClass(/drawer/)
  await expect(dialog).toContainText('请求来源 IP')
  await expect(dialog).toContainText('192.0.2.24')
  await expect(dialog).toContainText('时间（北京时间）')
})

test('R17 部分按钮权限只展示获准操作，缺少映射读取权限不请求详情', async ({ page }) => {
  const { requests } = await preview(page, [
    'GET /api/roles',
    'GET /api/systems',
    'GET /api/permissions/tree',
  ])
  await page.goto('/roles')
  await expect(page.getByRole('button', { name: '查询', exact: true })).toBeEnabled()
  await expect(page.getByRole('button', { name: '新建角色', exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '编辑', exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '配置权限', exact: true })).toHaveCount(0)
  await page.goto('/resources')
  await expect(page.getByText('当前没有读取映射的权限。')).toBeVisible()
  expect(requests.some((item) => /^\/permissions\/\d+\/apis$/.test(item.path))).toBe(false)
})

test('R16 总览及日志不把HTTP200无权限查询误标为请求拒绝', async ({ page }) => {
  await preview(page, null, { eventType: 'PERMISSION_CHECK', allowed: false, httpStatus: 200 })
  await page.goto('/')
  await expect(page.locator('.overview-activity')).toContainText('无权限')
  await page.goto('/logs')
  await expect(page.locator('tbody tr').first()).toContainText('无权限')
})

test('R17 仅授API目录或权限树读取时有可用资源入口，无隐含系统列表请求', async ({ page }) => {
  for (const capability of ['GET /api/api-resources', 'GET /api/permissions/tree']) {
    const context = await page.context().browser().newContext()
    const isolated = await context.newPage()
    const { requests } = await preview(isolated, [capability])
    await isolated.goto('/')
    await isolated
      .getByRole('navigation', { name: '管理工作区' })
      .getByRole('link', { name: '资源与API' })
      .click()
    await expect(isolated.getByRole('heading', { level: 1, name: '资源与API' })).toBeVisible()
    await expect(isolated.getByLabel('所属系统')).toBeDisabled()
    if (capability.includes('api-resources'))
      await expect(isolated.locator('tbody tr')).toHaveCount(20)
    else await expect(isolated.locator('.permission-tree button')).toHaveCount(30)
    await expect(isolated.getByRole('alert')).toHaveCount(0)
    expect(requests.some((item) => item.path === '/systems')).toBe(false)
    await context.close()
  }
})
