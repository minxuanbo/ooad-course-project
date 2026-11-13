import test from 'node:test'
import assert from 'node:assert/strict'
import vm from 'node:vm'
import { readFileSync } from 'node:fs'

test('R16 在途修改筛选及每批条数后，旧响应不清除待查询且禁止续批', async () => {
  let finish, requests = 0
  const context = vm.createContext({ ref: value => ({ value }), URLSearchParams, onMounted() {}, onUnmounted() {},
    api: () => { requests++; return new Promise(resolve => { finish = resolve }) } })
  const source = readFileSync(new URL('../src/pages/AuditLogs.vue', import.meta.url), 'utf8').match(/<script setup>([\s\S]*?)<\/script>/)[1].replace(/^import .*$/gm, '')
  vm.runInContext(source, context)
  const pending = vm.runInContext('load()', context)
  vm.runInContext("limit.value=50; filters.value.username='changed'", context)
  finish({ items: [{ id: 1 }], total: 73, nextCursor: 'one', hasMore: true }); await pending
  assert.equal(vm.runInContext('dirty()', context), true)
  assert.equal(vm.runInContext('total.value', context), 73)
  await vm.runInContext('load(true)', context)
  assert.equal(requests, 1)
})
