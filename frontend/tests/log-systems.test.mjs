import test from 'node:test'
import assert from 'node:assert/strict'
import vm from 'node:vm'
import { readFileSync } from 'node:fs'

test('R16 日志系统下拉沿日志响应提供目录，按编码筛选且无资源目录请求', async () => {
  const calls = []
  const context = vm.createContext({
    ref: value => ({ value }), URLSearchParams, onMounted() {}, onUnmounted() {},
    api: async path => {
      calls.push(path)
      return { items: [], total: 0, hasMore: false, systems: [
        { id: 1, code: 'rbac', name: '权限管理', enabled: true },
        { id: 2, code: 'course-project-management', name: '课程管理', enabled: false },
      ] }
    },
  })
  const source = readFileSync(new URL('../src/pages/AuditLogs.vue', import.meta.url), 'utf8')
  vm.runInContext(source.match(/<script setup>([\s\S]*?)<\/script>/)[1].replace(/^import .*$/gm, ''), context)
  const run = code => vm.runInContext(code, context)
  await run('load()')
  assert.equal(run('typeof systems'), 'object', '日志响应的系统目录应保留供筛选')
  assert.equal(run('systems.value[1].name'), '课程管理')
  await run("filters.value.appCode='course-project-management';load()")
  assert.match(calls[1], /appCode=course-project-management/)
  assert(calls.every(path => path.startsWith('/authorization-logs?')))
  assert.match(source, /<select v-model="filters.appCode">/)
})
