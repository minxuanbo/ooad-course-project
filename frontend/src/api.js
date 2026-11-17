import { reactive } from 'vue'
export const session = reactive({
  token: sessionStorage.getItem('rbacToken') || '',
  me: null,
  notice: '',
})
let noticeTimer
let refreshVersion = 0
export function notify(message) {
  session.notice = message
  clearTimeout(noticeTimer)
  noticeTimer = setTimeout(clearNotice, 6000)
}
function clearNotice() {
  session.notice = ''
}
export function setToken(token) {
  if (session.token !== token) {
    ++refreshVersion
    session.me = null
  }
  session.token = token
  if (token) sessionStorage.setItem('rbacToken', token)
  else {
    sessionStorage.removeItem('rbacToken')
    session.me = null
  }
}
export async function api(path, method = 'GET', body) {
  const requestToken = session.token
  let response
  try {
    response = await fetch('/api' + path, {
      method,
      cache: 'no-store',
      headers: {
        'Content-Type': 'application/json',
        Authorization: 'Bearer ' + requestToken,
      },
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: AbortSignal.timeout(20000),
    })
  } catch {
    throw Error('无法连接服务，请检查后端服务及网络。')
  }
  const text = await response.text()
  let data = null
  if (text) {
    try {
      data = JSON.parse(text)
    } catch {
      throw Error('接口响应异常，请检查 API 代理配置。')
    }
  }
  if (!response.ok) {
    if (response.status === 401 && path !== '/auth/login' && session.token === requestToken) {
      setToken('')
      notify('登录已失效或账号已停用，请重新登录。')
    }
    const error = Error(data?.message || '请求失败：' + response.status)
    error.status = response.status
    error.code = data?.code || 'HTTP_ERROR'
    error.requestId = data?.requestId || ''
    if (
      response.status === 403 &&
      requestToken &&
      session.token === requestToken &&
      !['/auth/login', '/me', '/me/permissions'].includes(path)
    ) {
      try {
        await refreshSession()
      } catch {
        // refreshSession按代次关闭失效能力；保留原403，不能覆盖较新的刷新结果。
      }
    }
    throw error
  }
  if (session.token !== requestToken) throw Error('登录身份已改变，请重新加载。')
  if (data?.code !== 'OK' || !Object.hasOwn(data, 'data')) throw Error('接口响应格式不符合约定。')
  return data.data
}
/** 导航和403只读一次最新身份能力；读取失败关闭旧按钮，不缓存授权结论。 */
export async function refreshSession() {
  const token = session.token
  const version = ++refreshVersion
  try {
    const me = await api('/me')
    if (session.token === token && version === refreshVersion) session.me = me
    return me
  } catch (error) {
    if (session.token === token && version === refreshVersion && session.me) {
      session.me = { ...session.me, user: { ...session.me.user, routes: [] } }
    }
    throw error
  }
}
/** 页面入口及按钮按服务端实际API能力显示，角色名和职位不参与判断。 */
export function canRoute(method, path, global = false) {
  return (
    !!session.me &&
    (!global || session.me.user.globalScope === true) &&
    session.me.user.routes?.some((item) => item.method === method && item.path === '/api' + path)
  )
}
