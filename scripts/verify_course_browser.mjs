/** 使用两个生产前端与真实后端复验课程菜单/按钮，不开启网络mock或录制凭据。 */
import { createRequire } from 'node:module'
import fs from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const require = createRequire(path.join(root, 'frontend/package.json'))
const { chromium, expect } = require('@playwright/test')
const output = path.join(root, '.local/course-integration/full/browser')
await fs.mkdir(output, { recursive: true })
const password = process.env.UI_FIXTURE_PASSWORD
if (!password) throw Error('请通过环境提供虚构夹具口令')
const browser = await chromium.launch({ channel: 'chrome', headless: true })
const adminContext = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
const courseContext = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
const adminPage = await adminContext.newPage()
const coursePage = await courseContext.newPage()
const evidence = []
let originalIds, adminToken

/** 恢复夹具和定位权限使用真实RBAC接口；演示撤权写入使用RBAC页面。 */
async function rbac(apiPath, method = 'GET', body) {
  const response = await fetch('http://127.0.0.1:8081/api' + apiPath, {
    method, headers: { Authorization: 'Bearer ' + adminToken, 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  if (!response.ok) throw Error(`RBAC夹具操作失败：HTTP${response.status}`)
  return (await response.json()).data
}

/** 从当前完整角色集合中切换指定课程权限，再显式确认整体保存。 */
async function revokeInUi(code) {
  await adminPage.getByRole('row').filter({ hasText: 'course-admin' }).getByRole('button', { name: '配置权限' }).click()
  const dialog = adminPage.getByRole('dialog')
  await dialog.getByLabel('所属系统').selectOption('2')
  const label = dialog.locator('.candidate-list label').filter({ hasText: new RegExp(`${code} ·`) })
  await label.getByRole('checkbox').uncheck()
  await dialog.getByRole('button', { name: '保存完整集合', exact: true }).click()
  await dialog.getByRole('button', { name: '确认保存完整集合', exact: true }).click()
  await expect(dialog).not.toBeVisible()
}

try {
  await adminPage.goto('http://127.0.0.1:5173/login')
  await adminPage.getByLabel('账号', { exact: true }).fill('ui-admin')
  await adminPage.getByLabel('密码', { exact: true }).fill(password)
  await adminPage.getByRole('button', { name: '登录 →' }).click()
  await adminPage.waitForURL('http://127.0.0.1:5173/')
  adminToken = await adminPage.evaluate(() => sessionStorage.getItem('rbacToken'))
  originalIds = (await rbac('/roles/51/permissions')).ids
  await adminPage.goto('http://127.0.0.1:5173/roles')
  await adminPage.getByLabel('搜索角色').fill('course-admin')
  await adminPage.getByRole('button', { name: '查询', exact: true }).click()
  await expect(adminPage.getByRole('row').filter({ hasText: 'course-admin' })).toBeVisible()
  evidence.push('RBAC生产前端真实登录并读取课程角色')

  await coursePage.goto('http://127.0.0.1:5174/')
  await coursePage.getByRole('tab', { name: '教师登录' }).click()
  await coursePage.getByPlaceholder('请输入工号').fill('ui-user-08')
  await coursePage.locator('input[name="password"]').fill(password)
  await coursePage.getByRole('button', { name: /登录/ }).last().click()
  await coursePage.waitForURL('**/teacher/courses')
  await expect(coursePage.getByRole('link', { name: '日志', exact: true })).toBeVisible()
  const courseToken = await coursePage.evaluate(() => localStorage.getItem('cpm.accessToken'))
  evidence.push('老师原始课程前端真实教师登录并显示数据库课程')
  await coursePage.goto('http://127.0.0.1:5174/admin/terms')
  await expect(coursePage.getByText('联调基准学期', { exact: true })).toBeVisible()
  await expect(coursePage.getByRole('button', { name: '新建学期', exact: true })).toBeEnabled()
  await coursePage.screenshot({ path: path.join(output, '01-authorized.png'), fullPage: true })
  evidence.push('MTR菜单与MTRC按钮授权有效')

  await revokeInUi('MTRC')
  await coursePage.reload()
  await expect(coursePage.getByText('联调基准学期', { exact: true })).toBeVisible()
  await expect(coursePage.getByRole('button', { name: '取消当前', exact: true })).toBeEnabled()
  await expect(coursePage.getByRole('button', { name: '新建学期', exact: true })).toBeDisabled()
  expect((await coursePage.evaluate(() => localStorage.getItem('cpm.accessToken'))) === courseToken).toBe(true)
  await coursePage.screenshot({ path: path.join(output, '02-button-revoked.png'), fullPage: true })
  evidence.push('RBAC页面撤回MTRC，课程重读页面禁用按钮，令牌未替换')
  await rbac('/roles/51/permissions', 'PUT', { ids: originalIds })
  await adminPage.reload()
  await adminPage.getByLabel('搜索角色').fill('course-admin')
  await adminPage.getByRole('button', { name: '查询', exact: true }).click()
  await expect(adminPage.getByRole('row').filter({ hasText: 'course-admin' })).toBeVisible()
  await revokeInUi('MTR')
  await coursePage.goto('http://127.0.0.1:5174/admin/terms')
  await expect(coursePage.getByRole('heading', { name: '无法访问' })).toBeVisible()
  await coursePage.screenshot({ path: path.join(output, '03-menu-revoked.png'), fullPage: true })
  evidence.push('RBAC页面撤回MTR，课程直达菜单被实时守卫拒绝')
  await rbac('/roles/51/permissions', 'PUT', { ids: originalIds })
  await coursePage.goto('http://127.0.0.1:5174/admin/terms')
  await expect(coursePage.getByRole('button', { name: '新建学期', exact: true })).toBeEnabled()
  evidence.push('恢复授权后原登录可再次访问')

  /** 新上下文走老师真实登录，逐页检查现有学生和教师工作区。 */
  async function loginAs(account, role) {
    const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
    const page = await context.newPage()
    await page.goto('http://127.0.0.1:5174/')
    await page.getByRole('tab', { name: role === 'student' ? '学生登录' : '教师登录' }).click()
    await page.locator('input[name="username"]').fill(account)
    await page.locator('input[name="password"]').fill(password)
    await page.getByRole('button', { name: /登录/ }).last().click()
    await page.waitForURL(`**/${role}/courses`)
    return page
  }
  async function inspect(page, target, name) {
    const failures = []
    const response = (reply) => {
      if (reply.status() >= 400 && /127\.0\.0\.1:(8080|8085)/.test(reply.url()))
        failures.push(`${reply.status()} ${new URL(reply.url()).pathname}`)
    }
    page.on('response', response)
    await page.goto('http://127.0.0.1:5174' + target)
    await page.waitForLoadState('networkidle')
    expect(failures, name).toEqual([])
    await expect(page.getByRole('heading', { name: '无法访问' })).not.toBeVisible()
    await page.screenshot({ path: path.join(output, name + '.png'), fullPage: true })
    page.off('response', response)
    evidence.push(name + '：真实业务请求无错误')
  }
  for (const [target,name] of [
    ['/admin/courses','04-admin-courses'], ['/admin/teachers','05-admin-teachers'],
    ['/admin/audit','06-admin-audit'], ['/admin/courses/2/team','07-admin-team'],
  ]) await inspect(coursePage,target,name)
  const studentPage = await loginAs('ui-user-09','student')
  for (const [target,name] of [
    ['/student/courses','08-student-courses'],
    ['/student/courses/1/milestones','09-student-individual-milestones'],
    ['/student/courses/1/milestones/1','10-student-individual-submission'],
    ['/student/courses/1/milestones/2','11-student-individual-booking'],
    ['/student/courses/2/groups','12-student-groups'],
    ['/student/courses/2/milestones/3','13-student-group-submission'],
    ['/student/courses/2/milestones/4','14-student-group-booking'],
    ['/student/courses/2/grades','15-student-grades'],
  ]) await inspect(studentPage,target,name)
  const teacherPage = await loginAs('ui-user-05','teacher')
  for (const [target,name] of [
    ['/teacher/courses','16-teacher-courses'],
    ['/teacher/courses/2/classes','17-teacher-classes'],
    ['/teacher/courses/2/milestones','18-teacher-milestones'],
    ['/teacher/classes/2/roster?courseId=2','19-teacher-roster'],
    ['/teacher/classes/2/groups?courseId=2','20-teacher-groups'],
    ['/teacher/classes/2/milestones?courseId=2','21-teacher-review'],
  ]) await inspect(teacherPage,target,name)
  // 管理人员含业务角色时仍可编辑，服务端锁定的自定义项不得被页面删除。
  await adminPage.goto('http://127.0.0.1:5173/users')
  await adminPage.getByLabel('搜索人员').fill('ui-user-09')
  await adminPage.getByRole('button',{ name:'查询', exact:true }).click()
  await adminPage.getByRole('row').filter({hasText:'ui-user-09'}).getByRole('button',{name:'调整角色'}).click()
  await expect(adminPage.getByRole('dialog')).toBeVisible()
  evidence.push('RBAC人员含业务角色仍可读取和调整，未整体冻结')
} finally {
  if (originalIds && adminToken) await rbac('/roles/51/permissions', 'PUT', { ids: originalIds })
  await fs.writeFile(path.join(output, 'result.json'), JSON.stringify(evidence, null, 2) + '\n')
  await browser.close()
}
console.log(`两个生产前端真实交互通过：${evidence.length}项；截图位于.local/course-integration/full/browser。`)
