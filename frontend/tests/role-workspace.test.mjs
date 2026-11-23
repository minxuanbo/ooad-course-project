import test from 'node:test'
import assert from 'node:assert/strict'
import vm from 'node:vm'
import { readFileSync } from 'node:fs'
import { RequestGeneration, errorText } from '../src/lib/presentation.js'

/** 运行真实角色页，独立启停只修改状态且继续经过确认、拒绝和刷新处理。 */
function workspace(api) {
  const context = vm.createContext({
    ref: (value) => ({ value }),
    onMounted() {},
    onUnmounted() {},
    api,
    canRoute: () => true,
    notify() {},
    refreshSession: async () => {},
    RequestGeneration,
    errorText,
    URLSearchParams,
  })
  vm.runInContext(
    readFileSync(new URL('../src/pages/Roles.vue', import.meta.url), 'utf8')
      .match(/<script setup>([\s\S]*?)<\/script>/)[1]
      .replace(/^import .*$/gm, ''),
    context,
  )
  return (code) => vm.runInContext(code, context)
}

test('R13/R17 单独启停前不写入，确认后仅提交状态字段', async () => {
  const writes = []
  const run = workspace(async (path, method, body) => {
    if (method === 'PATCH') writes.push({ path, body })
    return { items: [], total: 0 }
  })
  run("openToggle({id: 7, name: '查询角色', enabled: true})")
  assert.equal(writes.length, 0)
  await run('toggleRole()')
  assert.equal(writes[0].path, '/roles/7')
  assert.deepEqual(JSON.parse(JSON.stringify(writes[0].body)), { enabled: false })
  assert.equal(run('toggling.value'), null)
})

test('R13/R17 启停遇到403保留确认目标并禁重复写，重置清除筛选', async () => {
  let writes = 0
  const run = workspace(async (path, method) => {
    if (method === 'PATCH') {
      writes++
      throw Object.assign(Error('拒绝'), { status: 403 })
    }
    return { items: [], total: 0 }
  })
  run('openToggle({id: 7, enabled: true})')
  await run('toggleRole()')
  await run('toggleRole()')
  assert.equal(writes, 1)
  assert.equal(run('toggling.value.id'), 7)
  run("q.value = 'draft'; enabled.value = 'false'; resetFilters()")
  assert.equal(run('q.value'), '')
  assert.equal(run('enabled.value'), '')
})
