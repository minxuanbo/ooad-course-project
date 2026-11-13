import { reactive } from 'vue'
export const session = reactive({ token: sessionStorage.getItem('rbacToken') || '', me: null, permissions: [], logAccess: false, notice: '' })
let noticeTimer
export function notify(message) {
  session.notice = message
  clearTimeout(noticeTimer)
  noticeTimer = setTimeout(clearNotice, 6000)
}
function clearNotice() { session.notice = '' }
export function setToken(token) {
  session.token = token
  if (token) sessionStorage.setItem('rbacToken', token)
  else { sessionStorage.removeItem('rbacToken'); session.me = null; session.permissions = []; session.logAccess = false }
}
export async function api(path, method = 'GET', body) {
  const requestToken = session.token
  let response
  try {
    response = await fetch('/api' + path, {
      method, cache: 'no-store',
      headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + requestToken },
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: AbortSignal.timeout(20000)
    })
  } catch { throw Error('无法连接服务，请检查后端服务及网络。') }
  const text = await response.text()
  let data = null
  if (text) {
    try { data = JSON.parse(text) } catch { throw Error('接口响应异常，请检查 API 代理配置。') }
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
    throw error
  }
  if (session.token !== requestToken) throw Error('登录身份已改变，请重新加载。')
  if (data?.code !== 'OK' || !Object.hasOwn(data, 'data')) throw Error('接口响应格式不符合约定。')
  return data.data
}
export async function refreshSession() {
  const token = session.token
  const me = await api('/me')
  const permissions = await api('/me/permissions')
  const codes = (permissions || []).map(item => item.code)
  let logAccess = false
  try { await api('/authorization-logs?limit=1'); logAccess = true }
  catch (error) { if (error.status !== 403) throw error }
  if (session.token === token) {
    session.me = me
    session.permissions = codes
    session.logAccess = logAccess
  }
  return me
}
// 日志还要求共同根范围，入口使用服务端实际查询结果，不按角色名推断。
export function can(permission) {
  return permission === 'rbac.logs.read' ? session.logAccess : session.permissions.includes(permission)
}
