// 【2026-11-07 工程纪律守则 D6 执法器】PC 前端死引用扫描
// 缘起: 复盘条目1(结构性盲区)——PC 前端无类型/死引用检测, loadScopeCounts 悬空数周无人知。
// 规则: aigicrm-ui/src 下所有 .vue/.js 的静态 import 与动态 import() 中,
//       相对路径(./ ../)与别名(@/)引用必须能解析到真实文件(含 .js/.vue/.json/index.js/index.vue 后缀)。
// 判定只认退出码: 有死引用 exit 1, 全部可解析 exit 0。
// 用法: node scripts/check-ui-dead-imports.mjs <repo-root>
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

// 无参调用时默认 = 脚本所在目录的上一级(仓库源码根), 便于 dev-verify 直接 run
const ROOT = process.argv[2] ?? path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
if (!ROOT) {
  console.error('FAIL: 缺少 repo-root 参数')
  process.exit(2)
}
const uiSrc = path.join(ROOT, 'aigicrm-ui', 'src')
if (!fs.existsSync(uiSrc)) {
  console.error(`FAIL: 找不到 ${uiSrc}`)
  process.exit(2)
}

function walk(dir, out = []) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name)
    if (e.isDirectory()) walk(p, out)
    else if (/\.(vue|js|mjs)$/.test(e.name)) out.push(p)
  }
  return out
}

// 静态 import/export-from + 动态 import(): 字符串字面量限定, 模板字符串不做静态解析(动态拼接无法机判)
const IMPORT_RE = /(?:^|\n)\s*(?:import\s+[^'"]*?from\s+|import\s+|export\s+(?:\{[^}]*\}|\*)\s+from\s+|import\(\s*)['"]([^'"]+)['"]/g

function resolveSpec(spec, fromFile) {
  let base
  if (spec.startsWith('@/')) base = path.join(uiSrc, spec.slice(2))
  else if (spec.startsWith('./') || spec.startsWith('../')) base = path.resolve(path.dirname(fromFile), spec)
  else return true // 裸包名/别名包交给包管理器, 不在本扫描范围
  const candidates = [base, `${base}.js`, `${base}.vue`, `${base}.mjs`, `${base}.json`, path.join(base, 'index.js'), path.join(base, 'index.vue'), path.join(base, 'index.mjs')]
  return candidates.some((c) => fs.existsSync(c) && fs.statSync(c).isFile())
}

const files = walk(uiSrc)
const dead = []
let checked = 0
for (const file of files) {
  const text = fs.readFileSync(file, 'utf8')
  let m
  IMPORT_RE.lastIndex = 0
  while ((m = IMPORT_RE.exec(text))) {
    const spec = m[1]
    if (!spec.startsWith('./') && !spec.startsWith('../') && !spec.startsWith('@/')) continue
    checked += 1
    if (!resolveSpec(spec, file)) {
      const line = text.slice(0, m.index).split('\n').length
      dead.push(`${path.relative(ROOT, file)}:${line} → ${spec}`)
    }
  }
}

if (dead.length) {
  console.log(`FAIL: PC 前端死引用 ${dead.length} 处(共检查 ${checked} 条本地引用):`)
  dead.forEach((d) => console.log('  ' + d))
  process.exit(1)
}
console.log(`PASS: PC 前端零死引用(共检查 ${checked} 条本地 import/@ 引用, ${files.length} 文件)`)
process.exit(0)
