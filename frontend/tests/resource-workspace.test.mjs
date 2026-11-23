import test from 'node:test'
import assert from 'node:assert/strict'
import vm from 'node:vm'
import { readFileSync } from 'node:fs'
import { organizationRows, RequestGeneration, errorText } from '../src/lib/presentation.js'

/** 执行真实资源页面脚本，映射响应独立可控以检查切换节点和系统的晚响应。 */
function workspace(api, canRoute = () => true) {
  let unmount
  const session = { token: 'one', me: { user: { id: 1, departmentId: 10, globalScope: true } } }
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
    watch() {},
    api,
    canRoute,
    session,
    notify() {},
    refreshSession: async () => {},
    organizationRows,
    RequestGeneration,
    errorText,
    URLSearchParams,
  })
  const source = readFileSync(new URL('../src/pages/Resources.vue', import.meta.url), 'utf8')
    .match(/<script setup>([\s\S]*?)<\/script>/)[1]
    .replace(/^import .*$/gm, '')
  vm.runInContext(source, context)
  return { run: (code) => vm.runInContext(code, context), session, unmount: () => unmount() }
}

const roots = [
  {
    id: 1,
    systemId: 1,
    parentId: null,
    name: '人员管理',
    code: 'menu',
    type: 'MENU',
    enabled: true,
    children: [
      {
        id: 2,
        systemId: 1,
        parentId: 1,
        name: '查询人员',
        code: 'read',
        type: 'BUTTON',
        enabled: true,
      },
    ],
  },
]
const catalog = (path) =>
  path === '/systems'
    ? [
        { id: 1, name: 'RBAC' },
        { id: 9, name: '课程' },
      ]
    : path.startsWith('/permissions/tree')
      ? structuredClone(roots)
      : null

test('R14/R17 左侧选择权限只读取该节点完整映射，搜索不覆盖选中详情', async () => {
  const calls = []
  const page = workspace(async (path) => {
    calls.push(path)
    return (
      catalog(path) || {
        ids: [8],
        items: [{ id: 8, method: 'GET', path: '/api/users', enabled: true }],
      }
    )
  })
  await page.run('load()')
  await page.run('selectPermission(nodes.value[1])')
  assert.equal(page.run('selected.value.id'), 2)
  assert.equal(page.run('permissionApis.value[0].path'), '/api/users')
  page.run("q.value = '找不到'")
  assert.equal(page.run('visible.value.length'), 0)
  assert.equal(page.run('selected.value.id'), 2)
  assert.equal(calls.filter((path) => path === '/systems').length, 1)
})

test('R14/R17 节点映射后发查询胜出，切换系统或卸载后旧映射不回填', async () => {
  for (const action of ['node', 'system', 'unmount']) {
    const finishes = []
    const page = workspace(
      async (path) => catalog(path) || new Promise((resolve) => finishes.push(resolve)),
    )
    const loading = page.run('load()')
    await new Promise((resolve) => setImmediate(resolve))
    let later
    if (action === 'node') later = page.run('selectPermission(nodes.value[1])')
    if (action === 'system') {
      page.run("systemId.value = '9'")
      later = page.run('changeSystem()')
    }
    if (action === 'unmount') page.unmount()
    finishes[0]({ ids: [999], items: [{ id: 999, path: '/old' }] })
    await loading
    assert.ok(!page.run('permissionApis.value?.some(item => item.id === 999)'), action)
    if (later) {
      await new Promise((resolve) => setImmediate(resolve))
      finishes[1]({ ids: [], items: [] })
      await later
    }
  }
})

test('R14/R17 映射读取失败不显示为零映射，无读取能力时不请求或沿用旧详情', async () => {
  let failure = true
  const page = workspace(async (path) => {
    if (catalog(path)) return catalog(path)
    if (failure) throw Error('映射暂不可用')
    return { ids: [], items: [] }
  })
  await page.run('load()')
  assert.equal(page.run('permissionApis.value'), null)
  assert.match(page.run('mappingError.value'), /暂不可用/)
  failure = false
  await page.run('loadPermissionApis()')
  assert.equal(page.run('permissionApis.value.length'), 0)
  const paths = []
  const restricted = workspace(
    async (path) => {
      paths.push(path)
      return catalog(path)
    },
    (method, path) => path !== '/permissions/{id}/apis',
  )
  await restricted.run('load()')
  assert.equal(paths.filter((path) => /\/permissions\/\d+\/apis/.test(path)).length, 0)
  assert.equal(restricted.run('permissionApis.value'), null)
})

test('R14/R17 API页保留真实分页与搜索，切换系统清除旧权限及筛选', async () => {
  const paths = []
  const page = workspace(async (path) => {
    paths.push(path)
    return catalog(path) || { items: [], total: 0, ids: [] }
  })
  await page.run('load()')
  await page.run("selectTab('apis')")
  await page.run("q.value = 'POST'; load(1)")
  assert.match(paths.at(-1), /q=POST/)
  assert.match(paths.at(-1), /page=1/)
  await page.run("systemId.value = '9'; changeSystem()")
  assert.equal(page.run('selected.value'), null)
  assert.equal(page.run('permissionApis.value'), null)
  assert.equal(page.run('q.value'), '')
  assert.match(paths.at(-1), /systemId=9/)
})

test('R14/R17 只有权限树或API读取能力时使用服务端默认系统，不请求未授权目录', async () => {
  for (const tab of ['permissions', 'apis']) {
    const paths = []
    const capability = tab === 'permissions' ? '/permissions/tree' : '/api-resources'
    const page = workspace(
      async (path) => {
        paths.push(path)
        return path.startsWith('/permissions/tree')
          ? structuredClone(roots)
          : { items: [], total: 0 }
      },
      (method, path) => path === capability,
    )
    await page.run(`tab.value = '${tab}'; load()`)
    assert.ok(paths.every((path) => !path.startsWith('/systems')))
    assert.equal(new URLSearchParams(paths[0].split('?')[1]).has('systemId'), false)
    assert.equal(page.run('error.value'), '')
  }
})
