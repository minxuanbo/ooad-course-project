import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import test from 'node:test'
import assert from 'node:assert/strict'
import { createApiClient } from './helpers/api-client.mjs'
test('API unwraps the envelope and exposes business error and request ID', async () => {
  let response = {
    ok: true,
    status: 200,
    text: async () => JSON.stringify({ code: 'OK', data: null, requestId: 'one' }),
  }
  const run = createApiClient(async () => response, '')
  assert.equal(await run("api('/auth/logout','POST')"), null)
  response = {
    ok: false,
    status: 403,
    text: async () =>
      JSON.stringify({
        code: 'MANAGEMENT_SCOPE_DENIED',
        message: '范围外',
        data: null,
        requestId: 'two',
      }),
  }
  await assert.rejects(
    run("api('/users')"),
    (e) => e.code === 'MANAGEMENT_SCOPE_DENIED' && e.requestId === 'two' && e.status === 403,
  )
  response = { ok: true, status: 200, text: async () => '[]' }
  await assert.rejects(run("api('/users')"), /格式/)
})
test('log pagination binds filters to the first page and resets after unmount', async () => {
  const calls = []
  let unmount
  const context = vm.createContext({
    ref: (value) => ({ value }),
    URLSearchParams,
    onMounted() {},
    onUnmounted: (fn) => {
      unmount = fn
    },
    api: async (path, method, body) => {
      calls.push({ path, method, body })
      return {
        items: [{ id: calls.length }],
        nextCursor: 'cursor-one',
        hasMore: true,
      }
    },
  })
  const source = readFileSync(new URL('../src/pages/AuditLogs.vue', import.meta.url), 'utf8')
    .match(/<script setup>([\s\S]*?)<\/script>/)[1]
    .replace(/^import .*$/gm, '')
  vm.runInContext(source, context)
  const run = (s) => vm.runInContext(s, context)
  await run("filters.value.username='first';load()")
  await run('load(true)')
  assert.match(calls[1].path, /username=first/)
  assert.match(calls[1].path, /cursor=cursor-one/)
  assert.equal(run('rows.value.length'), 2)
  await run("filters.value.username='changed';load(true)")
  assert.equal(calls.length, 2, '修改条件后不能续查旧快照')
  unmount()
  await run('load()')
  assert.equal(run('rows.value.length'), 0)
})
test('archive requests and archived filters are removed from the log page', () => {
  const source = readFileSync(new URL('../src/pages/AuditLogs.vue', import.meta.url), 'utf8')
  assert(!source.includes('/authorization-logs/archive'))
  assert(!source.includes('storage'))
})

test('T12 navigation uses effective permissions and server-confirmed log scope', async () => {
  let denied = false
  const calls = []
  const run = createApiClient(async (path) => {
    calls.push(path)
    const status = path.includes('/authorization-logs') && denied ? 403 : 200
    const data =
      path === '/api/me'
        ? {
            user: {
              id: 1,
              globalScope: !denied,
              routes: [
                { method: 'GET', path: '/api/authorization-logs' },
                { method: 'GET', path: '/api/users' },
              ],
            },
          }
        : { items: [] }
    return {
      ok: status === 200,
      status,
      text: async () =>
        JSON.stringify({
          code: status === 200 ? 'OK' : 'MANAGEMENT_SCOPE_DENIED',
          data,
        }),
    }
  }, '')
  await run('refreshSession()')
  assert.equal(run("canRoute('GET','/authorization-logs',true)"), true)
  assert.equal(run("canRoute('GET','/users')"), true)
  assert.equal(run("canRoute('POST','/roles',true)"), false)
  denied = true
  await run('refreshSession()')
  assert.equal(run("canRoute('GET','/authorization-logs',true)"), false)
  assert.equal(run("canRoute('GET','/users')"), true)
  denied = false
  await run('refreshSession()')
  assert.equal(run("canRoute('GET','/authorization-logs',true)"), true)
  run("setToken('')")
  assert.equal(run("canRoute('GET','/users')"), false)
  assert(
    !calls.includes('/api/me/permissions'),
    '导航只读取一次最新实际路由，不再读取未使用的权限编码',
  )
  assert(
    !calls.some((path) => path.includes('/authorization-logs')),
    '能力已由本人接口返回，不应额外查询日志探测入口',
  )
})

test('T12 permitted department login reaches the shell without entering logs', async () => {
  const routes = []
  const context = vm.createContext({
    ref: (value) => ({ value }),
    useRouter: () => ({ replace: async (path) => routes.push(path) }),
    api: async () => ({ token: 'manager-token' }),
    setToken() {},
    refreshSession: async () => {},
    can: () => false,
  })
  const source = readFileSync(new URL('../src/pages/Login.vue', import.meta.url), 'utf8')
    .match(/<script setup>([\s\S]*?)<\/script>/)[1]
    .replace(/^import .*$/gm, '')
  vm.runInContext(source, context)
  await vm.runInContext('login()', context)
  assert.deepEqual(routes, ['/'])
})

test('R17 实际请求403后刷新本人能力，隐藏已被撤销的菜单与按钮', async () => {
  const calls = []
  const run = createApiClient(async (path) => {
    calls.push(path)
    const denied = path.includes('/roles/2/permissions')
    const data = path === '/api/me' ? { user: { id: 1, globalScope: true, routes: [] } } : []
    return {
      ok: !denied,
      status: denied ? 403 : 200,
      text: async () =>
        JSON.stringify({
          code: denied ? 'PERMISSION_DENIED' : 'OK',
          message: '权限已变化',
          data,
          requestId: 'denied-request',
        }),
    }
  })
  run(
    "session.me={user:{id:1,globalScope:true,routes:[{method:'PUT',path:'/api/roles/{id}/permissions'}]}}",
  )
  assert.equal(run("canRoute('PUT','/roles/{id}/permissions',true)"), true)
  await assert.rejects(
    run("api('/roles/2/permissions','PUT',{ids:[7]})"),
    (error) => error.status === 403 && error.requestId === 'denied-request',
  )
  assert.equal(run("canRoute('PUT','/roles/{id}/permissions',true)"), false)
  assert.deepEqual(calls, ['/api/roles/2/permissions', '/api/me'])
})
