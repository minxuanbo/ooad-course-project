import { reactive } from 'vue'
export const session = reactive({ token: sessionStorage.getItem('rbacToken') || '', me: null, notice: '' })
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
  else { sessionStorage.removeItem('rbacToken'); session.me = null }
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
    const error = Error(data?.error || data?.message || '请求失败：' + response.status)
    error.status = response.status
    throw error
  }
  if (session.token !== requestToken) throw Error('登录身份已改变，请重新加载。')
  return data
}
export async function refreshSession() {
  const token = session.token
  const me = await api('/me')
  if (session.token === token) session.me = me
  return me
}
export function employmentLabel(state) {
  if (state === 'HANDOVER') return '离职交接中'
  if (state === 'DEPARTED') return '已离职'
  return '在职'
}

export const routePermissions = { dashboard: 'user.read', users: 'user.read', roles: 'role.read', apps: 'app.read', logs: 'audit.read', organizations: 'org.read' }
export function can(permission, global = false) {
  return (session.me?.managementGrants || []).some(g => g.permission === permission && (!global || g.scope === 'GLOBAL'))
}
export function scopeSummary() {
  return [...new Set((session.me?.managementGrants || []).map(g => `${g.roleName}：` + (g.scope === 'GLOBAL' ? '全平台' : g.scope === 'COMPANY' ? g.company : `${g.company} / ${g.department}`)))].join('；') || '无管理范围'
}
