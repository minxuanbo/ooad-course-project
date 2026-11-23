/** 将服务错误连同请求号展示，便于从鉴权日志定位。 */
export function errorText(error) {
  return (
    error.message +
    (error.code ? `（${error.code}）` : '') +
    (error.requestId ? ` · 请求号 ${error.requestId}` : '')
  )
}

/** 权限查询的否定结果不是请求拒绝，认证结果也须与业务鉴权区分展示。 */
export function authorizationResultLabel(row) {
  if (row.eventType === 'PERMISSION_CHECK') return row.allowed ? '有权限' : '无权限'
  if (row.eventType === 'LOGIN_SUCCESS' || row.eventType === 'LOGIN_FAILED')
    return row.allowed ? '认证成功' : '认证失败'
  return row.allowed ? '允许' : '拒绝'
}

/** 资源页可由任一目录读取能力进入，系统列表是独立的系统切换能力。 */
export function resourcePageAvailable(canRoute) {
  return canRoute('GET', '/permissions/tree', true) || canRoute('GET', '/api-resources', true)
}

const beijingFormatter = new Intl.DateTimeFormat('zh-CN', {
  timeZone: 'Asia/Shanghai',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  fractionalSecondDigits: 3,
  hourCycle: 'h23',
})

/** 将接口时间转换为北京时间，保留毫秒且不受浏览器所在时区影响。 */
export function formatBeijingTime(value) {
  if (value === null || value === undefined || value === '') return '—'
  const instant = new Date(value)
  if (!Number.isFinite(instant.getTime())) return '—'
  const parts = Object.fromEntries(
    beijingFormatter.formatToParts(instant).map((part) => [part.type, part.value]),
  )
  return `${parts.year.padStart(4, '0')}-${parts.month}-${parts.day} ${parts.hour}:${parts.minute}:${parts.second}.${parts.fractionalSecond}`
}

/** 将北京时间输入转换为接口UTC边界，拒绝非法日期和无声滚动到其他日期。 */
export function beijingInputToUtc(value) {
  const parts =
    typeof value === 'string' &&
    /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2})(?:\.(\d{1,3}))?)?$/.exec(value)
  if (!parts) throw Error('请输入有效的北京时间。')
  const instant = new Date(`${value}+08:00`)
  const expected = `${parts[1]}-${parts[2]}-${parts[3]} ${parts[4]}:${parts[5]}:${parts[6] || '00'}.${(parts[7] || '').padEnd(3, '0')}`
  if (!Number.isFinite(instant.getTime()) || formatBeijingTime(instant) !== expected)
    throw Error('请输入有效的北京时间。')
  return instant.toISOString()
}

/** 保留匹配节点的范围内祖先，深度优先输出连续子树。 */
export function organizationRows(
  nodes,
  search = '',
  matches = (item, query) => item.name.toLowerCase().includes(query),
) {
  const indexed = new Map(nodes.map((item) => [item.id, item]))
  const keep = new Set()
  for (const item of nodes) {
    if (!matches(item, search.trim().toLowerCase())) continue
    for (let node = item; node && !keep.has(node.id); node = indexed.get(node.parentId))
      keep.add(node.id)
  }
  const result = []
  function append(parent, depth) {
    for (const node of nodes.filter((item) => item.parentId === parent && keep.has(item.id))) {
      result.push({ ...node, depth })
      append(node.id, depth + 1)
    }
  }
  append(null, 0)
  return result
}

/** 列表请求代次使晚响应不能覆盖新筛选或已关闭的工作区。 */
export class RequestGeneration {
  value = 0
  next() {
    return ++this.value
  }
  current(value) {
    return this.value === value
  }
  cancel() {
    ++this.value
  }
}
