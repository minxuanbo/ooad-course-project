import vm from 'node:vm'
import { readFileSync } from 'node:fs'

/** 在隔离上下文执行真实HTTP客户端；测试只提供响应及初始身份，不复制能力计算逻辑。 */
export function createApiClient(fetch, token = 'current') {
  const context = vm.createContext({
    reactive: (value) => value,
    sessionStorage: { getItem: () => token, setItem() {}, removeItem() {} },
    fetch,
    AbortSignal,
    setTimeout,
    clearTimeout,
  })
  vm.runInContext(
    readFileSync(new URL('../../src/api.js', import.meta.url), 'utf8')
      .replace(/^import .*$/gm, '')
      .replace(/export /g, ''),
    context,
  )
  return (code) => vm.runInContext(code, context)
}
