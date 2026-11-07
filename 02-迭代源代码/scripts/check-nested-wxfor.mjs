// 【2026-11-07 工程纪律守则 T10 执法器】嵌套 wx:for 必须显式别名(item+index)
// 缘起: 复盘条目#12(H2) 照片墙 wx:for index 遮蔽 → 跨客户 403。
// 规则: 任何 wx:for 元素若处于另一个 wx:for 的子孙位置, 必须同时声明 wx:for-item 与 wx:for-index,
//       否则内层默认名 item/index 与外层同名遮蔽——正是 H2 事故的形态。
// 执法口径: 违例按文件计数, 与 scripts/mp-nested-loop-baseline.json 冻结基线对账;
//       只防新增、不逼存量清零(存量已在守则台账登记为债务), 新文件出现违例=直接 FAIL。
// 用法: node scripts/check-nested-wxfor.mjs <repo-root> [--json]
//       (check-discipline-gates.sh §6 调用; --json 仅供生成基线)
import fs from 'node:fs'
import path from 'node:path'

const ROOT = process.argv[2]
if (!ROOT) {
  console.error('FAIL: 缺少 repo-root 参数')
  process.exit(2)
}
const pagesDir = path.join(ROOT, 'aigicrm-miniprogram', 'pages')
const baselinePath = path.join(ROOT, 'scripts', 'mp-nested-loop-baseline.json')

function walk(dir, out = []) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name)
    if (e.isDirectory()) walk(p, out)
    else if (e.name.endsWith('.wxml')) out.push(p)
  }
  return out
}

// 标签扫描器: 引号内内容不参与标签解析(排除 {{x ? '<a>' : '</a>'}} 之类),
// 多行标签由 [^>"'] 跨行匹配覆盖。
const TAG_RE = /<(\/?)([A-Za-z][\w-]*)((?:"[^"]*"|'[^']*'|[^>"'])*?)(\/?)>/g

function scanFile(text) {
  const stack = []
  const violations = []
  let m
  TAG_RE.lastIndex = 0
  while ((m = TAG_RE.exec(text))) {
    const closing = m[1] === '/'
    const tag = m[2]
    const attrs = m[3] || ''
    const selfClose = m[4] === '/'
    if (closing) {
      for (let i = stack.length - 1; i >= 0; i--) {
        if (stack[i].tag === tag) {
          stack.splice(i, 1)
          break
        }
      }
      continue
    }
    const hasFor = /(^|\s)wx:for=/.test(attrs)
    if (hasFor && !selfClose) {
      const hasIdx = /(^|\s)wx:for-index=/.test(attrs)
      const hasItem = /(^|\s)wx:for-item=/.test(attrs)
      const nested = stack.some((s) => s.isFor)
      if (nested && (!hasIdx || !hasItem)) {
        const line = text.slice(0, m.index).split('\n').length
        const missing = [!hasIdx ? 'index' : null, !hasItem ? 'item' : null].filter(Boolean).join('+')
        violations.push(`L${line}(${missing})`)
      }
      stack.push({ tag, isFor: true })
    } else if (!selfClose) {
      stack.push({ tag, isFor: false })
    }
  }
  return violations
}

function loadBaseline() {
  if (!fs.existsSync(baselinePath)) return {}
  return JSON.parse(fs.readFileSync(baselinePath, 'utf8'))
}

const files = walk(pagesDir).sort()
const baseline = loadBaseline()
const baselineFiles = new Set(Object.keys(baseline).filter((k) => !k.startsWith('_')))
const results = {}
let newViolations = 0

for (const file of files) {
  const rel = path.relative(ROOT, file)
  const violations = scanFile(fs.readFileSync(file, 'utf8'))
  const n = violations.length
  const base = baseline[rel] ?? 0
  if (n > base) {
    results[rel] = { now: n, baseline: base, detail: violations.slice(0, 10) }
    newViolations += n - base
  } else if (!baselineFiles.has(rel) && n === 0 && base === 0) {
    // 新文件且零违例 → 不入报告
  } else if (n < base) {
    results[rel] = { now: n, baseline: base, detail: violations.slice(0, 10) }
  }
}

const json = process.argv.includes('--json')
if (json) {
  const counts = {}
  for (const file of files) {
    const rel = path.relative(ROOT, file)
    counts[rel] = scanFile(fs.readFileSync(file, 'utf8')).length
  }
  console.log(JSON.stringify({ _comment: '扁平违例计数快照(文件→数量), 供生成/收紧 mp-nested-loop-baseline.json; 零计数文件已省略', ...Object.fromEntries(Object.entries(counts).filter(([, n]) => n > 0)) }, null, 2))
  process.exit(0)
}

if (newViolations > 0) {
  console.log('FAIL: 嵌套 wx:for 缺显式别名出现新增违例(存量见基线, 只防新增):')
  for (const [f, r] of Object.entries(results)) {
    if (r.now > r.baseline) console.log(`  ${f}: 现状 ${r.now} > 基线 ${r.baseline} — ${r.detail.join(', ')}`)
  }
  process.exit(1)
}

const reduced = Object.entries(results).filter(([, r]) => r.now < r.baseline)
if (reduced.length) {
  console.log('NOTE: 有文件的违例数低于基线(债务已清, 请同步收紧基线):')
  for (const [f, r] of reduced) console.log(`  ${f}: 现状 ${r.now} < 基线 ${r.baseline} — 请从 mp-nested-loop-baseline.json 删行或改数`)
}
console.log(`PASS: 嵌套 wx:for 别名基线对账通过(存量 ${Object.keys(baseline).filter((k) => !k.startsWith('_')).length} 文件在册, 新增 0)`)
process.exit(0)
