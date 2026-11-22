import test from 'node:test'
import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { formatBeijingTime, beijingInputToUtc } from '../src/lib/presentation.js'

const auditUrl = new URL('../src/pages/AuditLogs.vue', import.meta.url).href
const presentationUrl = new URL('../src/lib/presentation.js', import.meta.url).href

for (const zone of ['UTC', 'Asia/Shanghai', 'America/Los_Angeles']) {
  test(`R16 北京时间筛选不受浏览器时区${zone}影响`, () => {
    const script = `
      import assert from 'node:assert/strict'
      import { readFileSync } from 'node:fs'
      import vm from 'node:vm'
      import * as presentation from ${JSON.stringify(presentationUrl)}
      const source = readFileSync(new URL(${JSON.stringify(auditUrl)}), 'utf8')
        .match(/<script setup>([\\s\\S]*?)<\\/script>/)[1].replace(/^import .*$/gm, '')
      const context = vm.createContext({ ...presentation, ref: value => ({ value }),
        URLSearchParams, onMounted() {}, onUnmounted() {} })
      vm.runInContext(source, context)
      vm.runInContext("filters.value.from='2026-11-22T12:28';filters.value.to='2026-11-22T12:29'", context)
      const params = new URLSearchParams(vm.runInContext('query()', context))
      assert.equal(params.get('from'), '2026-11-22T04:28:00.000Z')
      assert.equal(params.get('to'), '2026-11-22T04:29:00.000Z')
      vm.runInContext("filters.value.from='2026-11-22T00:15'", context)
      assert.equal(new URLSearchParams(vm.runInContext('query()', context)).get('from'),
        '2026-11-21T16:15:00.000Z')
      assert.equal(presentation.formatBeijingTime('2026-11-22T04:28:10.754Z'),
        '2026-11-22 12:28:10.754')
      assert.equal(presentation.formatBeijingTime('2026-12-31T23:58:00.000Z'),
        '2027-01-01 07:58:00.000')
    `
    const result = spawnSync(process.execPath, ['--input-type=module', '-e', script], {
      env: { ...process.env, TZ: zone },
      encoding: 'utf8',
    })
    assert.equal(result.status, 0, result.stderr)
  })
}

test('R16 时间展示安全处理空值，筛选拒绝非法日期且保留毫秒边界', () => {
  for (const value of ['', null, undefined, 'invalid']) assert.equal(formatBeijingTime(value), '—')
  for (const value of ['invalid', '2026-02-30T12:00', '2026-11-22T24:00', '2026-11-22T12:00Z'])
    assert.throws(() => beijingInputToUtc(value), /北京时间/)
  assert.equal(beijingInputToUtc('2026-11-22T12:28:10.754'), '2026-11-22T04:28:10.754Z')
})
