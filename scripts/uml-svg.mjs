import { createRequire } from 'node:module'
import { dirname, resolve } from 'node:path'
import { writeFileSync } from 'node:fs'

// 文档绘图工具，不由产品或测试启动入口加载；SVG与PNG始终同源生成。
const require = createRequire(import.meta.url)
const sharp = require(
  process.env.UML_SHARP_MODULE || resolve(dirname(process.execPath), '../node_modules/sharp'),
)
const ink = '#223046'
const blue = '#245a93'
export const esc = (value) =>
  String(value).replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;')
const widthUnits = (value) =>
  [...String(value)].reduce((n, c) => n + (c.charCodeAt(0) > 255 ? 1 : 0.59), 0)
export function text(x, y, value, size = 18, anchor = 'start', width = Infinity) {
  const fitted = Math.min(size, width / Math.max(1, widthUnits(value)))
  if (fitted < 10) throw Error(`文字过长，须拆图而非裁切：${value}`)
  return `<text x="${x}" y="${y}" font-size="${fitted}" text-anchor="${anchor}" fill="${ink}">${esc(value)}</text>`
}
export function svg(width, height, title, subtitle, body) {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}"><defs>
  <marker id="open" markerWidth="12" markerHeight="12" refX="11" refY="6" orient="auto"><path d="M1,1 L11,6 L1,11" fill="none" stroke="${ink}" stroke-width="1.5"/></marker>
  <marker id="triangle" markerWidth="14" markerHeight="14" refX="13" refY="7" orient="auto"><path d="M1,1 L13,7 L1,13 Z" fill="white" stroke="${blue}" stroke-width="1.4"/></marker>
  <marker id="filled" markerWidth="12" markerHeight="12" refX="11" refY="6" orient="auto"><path d="M1,1 L11,6 L1,11 Z" fill="${ink}"/></marker></defs>
  <rect width="100%" height="100%" fill="white"/><g font-family="Microsoft YaHei,Noto Sans CJK SC,Arial,sans-serif">${text(28, 38, title, 26)}${text(28, 70, subtitle, 16, 'start', width - 56)}${body}</g></svg>`
}
export function box(b) {
  const fields = b.fields?.length ? b.fields : ['—']
  const methods = b.methods?.length ? b.methods : ['—']
  const split = b.y + 68 + fields.length * 28
  if (split + methods.length * 28 + 16 > b.y + b.h) throw Error(`类框高度不足：${b.id}`)
  return `<rect x="${b.x}" y="${b.y}" width="${b.w}" height="${b.h}" rx="2" fill="${b.center ? '#eef5ff' : 'white'}" stroke="${b.center ? blue : ink}" stroke-width="${b.center ? 2.5 : 1.5}"/>
  <path d="M${b.x},${b.y + 48}H${b.x + b.w} M${b.x},${split}H${b.x + b.w}" stroke="${ink}"/>
  ${text(b.x + b.w / 2, b.y + 32, b.title, 23, 'middle', b.w - 30)}
  ${fields.map((v, i) => text(b.x + 13, b.y + 76 + i * 28, v, 18, 'start', b.w - 26)).join('')}
  ${methods.map((v, i) => text(b.x + 13, split + 26 + i * 28, v, 18, 'start', b.w - 26)).join('')}`
}
export function assertGeometry(boxes, edges) {
  for (let i = 0; i < boxes.length; i++)
    for (const b of boxes.slice(i + 1)) {
      const a = boxes[i]
      if (a.x < b.x + b.w && a.x + a.w > b.x && a.y < b.y + b.h && a.y + a.h > b.y)
        throw Error(`框重叠：${a.id}/${b.id}`)
    }
  for (const e of edges)
    for (let i = 1; i < e.points.length; i++) {
      const [x1, y1] = e.points[i - 1],
        [x2, y2] = e.points[i]
      if (x1 !== x2 && y1 !== y2) throw Error(`连线须为直角或直线：${e.from}/${e.to}`)
      for (const b of boxes.filter((v) => v.id !== e.from && v.id !== e.to)) {
        const cross =
          x1 === x2
            ? x1 > b.x && x1 < b.x + b.w && Math.max(y1, y2) > b.y && Math.min(y1, y2) < b.y + b.h
            : y1 > b.y && y1 < b.y + b.h && Math.max(x1, x2) > b.x && Math.min(x1, x2) < b.x + b.w
        if (cross) throw Error(`连线${e.from}/${e.to}穿过${b.id}`)
      }
    }
}
export function edge(e) {
  if (e.kind !== 'association' && e.role) throw Error('依赖或实现线上不能写文字')
  const role = e.role ? text(e.role[0], e.role[1], e.role[2], e.role[3] ?? 17, e.role[4], e.role[5]) : ''
  return `<polyline points="${e.points.map((p) => p.join(',')).join(' ')}" fill="none" stroke="${e.kind === 'realization' ? blue : ink}" stroke-width="1.7" ${e.kind === 'association' ? '' : 'stroke-dasharray="8 5"'} marker-end="url(#${e.kind === 'realization' ? 'triangle' : 'open'})"/>${role}`
}
export async function output(directory, name, value) {
  writeFileSync(resolve(directory, name + '.svg'), value, 'utf8')
  await sharp(Buffer.from(value))
    .png()
    .toFile(resolve(directory, name + '.png'))
}
export async function contactSheet(directory, names, target) {
  const thumbWidth = 600,
    thumbHeight = 420,
    columns = 3
  const tiles = []
  for (let i = 0; i < names.length; i++)
    tiles.push({
      input: await sharp(resolve(directory, names[i] + '.png'))
        .resize(thumbWidth, thumbHeight, { fit: 'contain', background: 'white' })
        .png()
        .toBuffer(),
      left: (i % columns) * thumbWidth,
      top: Math.floor(i / columns) * thumbHeight,
    })
  await sharp({
    create: {
      width: columns * thumbWidth,
      height: Math.ceil(names.length / columns) * thumbHeight,
      channels: 3,
      background: '#e7ebf0',
    },
  })
    .composite(tiles)
    .png()
    .toFile(target)
}
