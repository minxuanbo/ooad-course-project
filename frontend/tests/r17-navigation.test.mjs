import test from 'node:test'
import assert from 'node:assert/strict'
import { createApiClient } from './helpers/api-client.mjs'

test('R17 导航仅请求一次本人能力，下一导航仍读取最新撤权结果', async () => {
  let allowed = true
  const calls = []
  const run = createApiClient(async (path) => {
    calls.push(path)
    return {
      ok: true,
      status: 200,
      text: async () =>
        JSON.stringify({
          code: 'OK',
          data:
            path === '/api/me'
              ? {
                  user: {
                    id: 1,
                    routes: allowed ? [{ method: 'GET', path: '/api/users' }] : [],
                  },
                }
              : [],
        }),
    }
  })
  await run('refreshSession()')
  assert.equal(run("canRoute('GET', '/users')"), true)
  assert.deepEqual(calls, ['/api/me'])
  allowed = false
  await run('refreshSession()')
  assert.equal(run("canRoute('GET', '/users')"), false)
  assert.deepEqual(calls, ['/api/me', '/api/me'])
})

test('R17 最新能力读取失败关闭旧按钮，之后可重新读取恢复', async () => {
  let failed = false
  const run = createApiClient(async () => ({
    ok: !failed,
    status: failed ? 500 : 200,
    text: async () =>
      JSON.stringify({
        code: failed ? 'INTERNAL_ERROR' : 'OK',
        data: {
          user: { id: 1, routes: [{ method: 'GET', path: '/api/users' }] },
        },
      }),
  }))
  await run('refreshSession()')
  failed = true
  await assert.rejects(run('refreshSession()'), (error) => error.status === 500)
  assert.equal(run("canRoute('GET','/users')"), false)
  failed = false
  await run('refreshSession()')
  assert.equal(run("canRoute('GET','/users')"), true)
})

test('R17 旧403能力刷新失败不能覆盖较新成功刷新的能力', async () => {
  let finish,
    started,
    reads = 0
  const waiting = new Promise((resolve) => {
    started = resolve
  })
  const response = (ok, data) => ({
    ok,
    status: ok ? 200 : 500,
    text: async () => JSON.stringify({ code: ok ? 'OK' : 'INTERNAL_ERROR', data }),
  })
  const run = createApiClient(async (path) => {
    if (path === '/api/users') return { ...response(false, null), status: 403 }
    if (++reads === 1) {
      started()
      return new Promise((resolve) => {
        finish = resolve
      })
    }
    return response(true, {
      user: { id: 1, routes: [{ method: 'GET', path: '/api/users' }] },
    })
  })
  const pending = run("api('/users')")
  await waiting
  await run('refreshSession()')
  finish(response(false, null))
  await assert.rejects(pending, (error) => error.status === 403)
  assert.equal(run("canRoute('GET','/users')"), true)
})
