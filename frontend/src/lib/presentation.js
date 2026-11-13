/** 将服务错误连同请求号展示，便于从鉴权日志定位。 */
export function errorText(error) {
  return error.message + (error.code ? `（${error.code}）` : '') + (error.requestId ? ` · 请求号 ${error.requestId}` : '')
}

/** 保留匹配节点的范围内祖先，深度优先输出连续子树。 */
export function organizationRows(nodes, search = '') {
  const indexed = new Map(nodes.map(item => [item.id, item]))
  const keep = new Set()
  for (const item of nodes) {
    if (!item.name.toLowerCase().includes(search.trim().toLowerCase())) continue
    for (let node = item; node && !keep.has(node.id); node = indexed.get(node.parentId)) keep.add(node.id)
  }
  const result = []
  function append(parent, depth) {
    for (const node of nodes.filter(item => item.parentId === parent && keep.has(item.id))) {
      result.push({ ...node, depth }); append(node.id, depth + 1)
    }
  }
  append(null, 0)
  return result
}

/** 列表请求代次使晚响应不能覆盖新筛选或已关闭的工作区。 */
export class RequestGeneration {
  value = 0
  next() { return ++this.value }
  current(value) { return this.value === value }
  cancel() { ++this.value }
}
