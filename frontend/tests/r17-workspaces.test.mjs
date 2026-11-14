import test from 'node:test'
import assert from 'node:assert/strict'
import vm from 'node:vm'
import { readFileSync } from 'node:fs'
import { RequestGeneration, errorText, organizationRows } from '../src/lib/presentation.js'

/** 执行真实页面脚本，使用可控HTTP响应核对状态，不复制页面加载算法。 */
function workspace(name, api) {
  let unmount
  const session = {
    token: 'one',
    me: { user: { id: 1, departmentId: 10, globalScope: true, routes: [] } },
  }
  const context = vm.createContext({
    ref: (value) => ({ value }),
    computed: (read) => ({
      get value() {
        return read()
      },
    }),
    onMounted() {},
    onUnmounted: (fn) => {
      unmount = fn
    },
    api,
    session,
    canRoute: () => true,
    notify() {},
    refreshSession: async () => {},
    errorText,
    organizationRows,
    RequestGeneration,
    URLSearchParams,
  })
  const source = readFileSync(new URL(`../src/pages/${name}.vue`, import.meta.url), 'utf8')
    .match(/<script setup>([\s\S]*?)<\/script>/)[1]
    .replace(/^import .*$/gm, '')
  vm.runInContext(source, context)
  return {
    run: (code) => vm.runInContext(code, context),
    session,
    unmount: () => unmount(),
  }
}

test('R17/R25 最近活动失败只降级日志区域，统计保留并可独立重试', async () => {
  let logFailure = true
  const calls = []
  const page = workspace('Overview', async (path) => {
    calls.push(path)
    if (path === '/overview') return { totalUsers: 12, distribution: [] }
    if (logFailure)
      throw Object.assign(Error('活动暂不可用'), {
        status: 500,
        requestId: 'activity-one',
      })
    return { items: [{ id: 3 }] }
  })
  await page.run('load()')
  assert.equal(page.run('result.value.totalUsers'), 12)
  assert.equal(page.run('error.value'), '', '日志故障不能污染总览主错误区')
  assert.match(page.run('activityError.value'), /activity-one/)
  logFailure = false
  await page.run('loadActivity()')
  assert.equal(page.run('activity.value[0].id'), 3)
  assert.equal(page.run('activityError.value'), '')
  assert.equal(calls.filter((path) => path === '/overview').length, 1)
})

test('R17/R25 卸载总览后丢弃晚到的日志失败', async () => {
  let fail, started
  const waiting = new Promise((resolve) => {
    started = resolve
  })
  const page = workspace('Overview', async (path) => {
    if (path === '/overview') return { totalUsers: 12, distribution: [] }
    started()
    return new Promise((resolve, reject) => {
      fail = reject
    })
  })
  const pending = page.run('load()')
  await waiting
  page.unmount()
  fail(Error('旧失败'))
  await pending
  assert.equal(page.run('error.value'), '')
  assert.equal(page.run('activityError.value'), '')
})

test('R10/R17 同一人员页翻页筛选复用部门树，范围变化和手动刷新重新读取', async () => {
  const calls = []
  const page = workspace('Users', async (path) => {
    calls.push(path)
    return path === '/departments/tree'
      ? [{ id: 10, parentId: null, name: '本部门' }]
      : { items: [], total: 0 }
  })
  await page.run('load(0)')
  await page.run("filters.value.q='员工';load(1)")
  assert.equal(calls.filter((path) => path === '/departments/tree').length, 1)
  assert.match(calls.at(-1), /page=1/)
  page.session.me.user.departmentId = 20
  await page.run('load(0)')
  assert.equal(calls.filter((path) => path === '/departments/tree').length, 2)
  await page.run('refreshDepartments()')
  assert.equal(calls.filter((path) => path === '/departments/tree').length, 3)
})

test('R10/R17 部门树读取失败不当作空树缓存，下一次查询完整重试', async () => {
  let treeReads = 0,
    userReads = 0
  const page = workspace('Users', async (path) => {
    if (path === '/departments/tree') {
      if (++treeReads === 1) throw Error('部门暂不可用')
      return []
    }
    userReads++
    return { items: [], total: 0 }
  })
  await page.run('load()')
  assert.equal(userReads, 0)
  await page.run('load()')
  assert.equal(treeReads, 2)
  assert.equal(userReads, 1)
})

test('R10/R17 人员读取期间范围变化，旧人员与部门选项均不能回填', async () => {
  let finish, started
  const waiting = new Promise((resolve) => {
    started = resolve
  })
  const page = workspace('Users', async (path) => {
    if (path === '/departments/tree') return [{ id: 10, parentId: null, name: '原范围' }]
    started()
    return new Promise((resolve) => {
      finish = resolve
    })
  })
  const pending = page.run('load()')
  await waiting
  page.session.me.user.departmentId = 20
  finish({ items: [{ id: 3, name: '旧范围人员' }], total: 1 })
  await pending
  assert.equal(page.run('rows.value.length'), 0)
  assert.equal(page.run('departments.value.length'), 0)
})

test('R10/R17 请求403刷新到新范围后清除旧树，下次读取新范围', async () => {
  let treeReads = 0,
    failed = true
  const page = workspace('Users', async (path) => {
    if (path === '/departments/tree') {
      treeReads++
      return [{ id: 10, parentId: null, name: '原部门' }]
    }
    if (failed) {
      page.session.me.user.departmentId = 20
      throw Object.assign(Error('范围变化'), { status: 403 })
    }
    return { items: [], total: 0 }
  })
  await page.run('load()')
  assert.equal(page.run('departments.value.length'), 0)
  failed = false
  await page.run('load()')
  assert.equal(treeReads, 2)
})
