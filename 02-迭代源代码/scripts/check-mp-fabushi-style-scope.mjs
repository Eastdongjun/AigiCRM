#!/usr/bin/env node
/**
 * 静态门禁：法布施 v7 样式必须限定在 .fb-page 之下。
 *
 * 背景（2026-11-25）：小程序法布施界面按原型 v7 重做外观，公共样式在
 * pages/fabushi/fabushi-v7.wxss，各页面用 @import 引入。原型里的类名
 * （.card / .stats-grid / .stats-tile / .search-input / .secondary-btn …）
 * 与全局 app.wxss 的同名规则撞车，而 app.wxss 里有些规则优先级更高
 * （例如 `.stats-tile text { display:block; color:#697991; font-size:23rpx }`），
 * 单类名选择器会被它们盖掉——统计数字格曾因此缩成一行一个灰色小字，
 * 页面不报错，只是“看起来不对”。限定到 `.fb-page` 之后优先级更高，
 * 也不会影响别的页面。
 *
 * 检查范围：
 *   - pages/fabushi/fabushi-v7.wxss 本身；
 *   - pages 下所有 @import 了 fabushi-v7.wxss 的 *.wxss（按文件内容判断，不靠目录）。
 *   尚未改用 v7 样式的法布施页面不在范围内，改到哪个页面哪个页面就受约束。
 *
 * 规则：去掉注释后，每个顶层规则的每一段选择器（逗号分开）只能是
 *   - `page`（页面底色），或
 *   - 以 `.fb-page` 开头（后面不能再接类名里的字符，例如 .fb-page-foo 不算）。
 * @import / @keyframes 这类 at-rule 整块跳过。
 *
 * 退出码：0 = 通过；1 = 存在未限定的选择器，或自检不通过；2 = 参数/读取错误。
 *
 * 用法：
 *   node scripts/check-mp-fabushi-style-scope.mjs
 *   node scripts/check-mp-fabushi-style-scope.mjs --selftest     先用内置样例验证检查器自己
 *   node scripts/check-mp-fabushi-style-scope.mjs --root ../aigicrm-miniprogram
 */

import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const SCRIPT_DIR = path.dirname(fileURLToPath(import.meta.url))
const DEFAULT_MP_ROOT = path.resolve(SCRIPT_DIR, '..', 'aigicrm-miniprogram')
const SHARED_STYLE_DIR = path.join('pages', 'fabushi')
const SHARED_STYLE_NAME = 'fabushi-v7.wxss'
const SHARED_STYLE_IMPORT = /@import\s+["'](?:\.\.\/)?fabushi-v7\.wxss["']/
const SCOPED = /^\.fb-page(?![\w-])/

function usage() {
  return `Usage: node scripts/check-mp-fabushi-style-scope.mjs [options]

Options:
  --root <dir>   小程序根目录（默认: ../aigicrm-miniprogram）
  --selftest     只跑检查器自检（内置样例：该拦的必须拦、不该拦的必须放）
  -h, --help     显示帮助
`
}

function fail(message, code = 2) {
  process.stderr.write(`ERROR: ${message}\n`)
  process.exit(code)
}

function parseArgs(argv) {
  const options = { root: DEFAULT_MP_ROOT, selftest: false }
  let i = 0
  while (i < argv.length) {
    const arg = argv[i]
    if (arg === '-h' || arg === '--help') {
      process.stdout.write(usage())
      process.exit(0)
    }
    if (arg === '--selftest') {
      options.selftest = true
      i += 1
      continue
    }
    if (arg === '--root') {
      const value = argv[i + 1]
      if (!value || value.startsWith('--')) fail('missing value for --root')
      options.root = path.resolve(value)
      i += 2
      continue
    }
    fail(`unknown argument: ${arg}\n\n${usage()}`)
  }
  return options
}

function lineIndex(source) {
  const starts = [0]
  for (let i = 0; i < source.length; i += 1) {
    if (source[i] === '\n') starts.push(i + 1)
  }
  return (offset) => {
    let low = 0
    let high = starts.length - 1
    while (low <= high) {
      const mid = (low + high) >> 1
      if (starts[mid] <= offset) low = mid + 1
      else high = mid - 1
    }
    return high + 1
  }
}

/**
 * 取出源码里的顶层规则：{ selector, line }。
 * 逐字符扫描：跳过注释；at-rule（@import …; 与带嵌套花括号的 @keyframes … { … }）整块跳过；
 * 其余按花括号配对取“选择器 { 声明 }”。声明里出现花括号字面量的情况（极罕见）不处理。
 */
function topLevelRules(source) {
  const rules = []
  const lineOf = lineIndex(source)
  const n = source.length
  let i = 0
  while (i < n) {
    if (source.startsWith('/*', i)) {
      const end = source.indexOf('*/', i + 2)
      i = end < 0 ? n : end + 2
      continue
    }
    if (source[i] === '@') {
      let depth = 0
      let j = i
      while (j < n) {
        const ch = source[j]
        if (ch === '{') depth += 1
        else if (ch === '}') {
          depth -= 1
          if (depth === 0) { j += 1; break }
        } else if (ch === ';' && depth === 0) { j += 1; break }
        j += 1
      }
      i = j
      continue
    }
    if (/\s/.test(source[i])) {
      i += 1
      continue
    }
    const open = source.indexOf('{', i)
    if (open < 0) break
    let depth = 1
    let j = open + 1
    while (j < n && depth > 0) {
      if (source[j] === '{') depth += 1
      else if (source[j] === '}') depth -= 1
      j += 1
    }
    rules.push({ selector: source.slice(i, open).trim().replace(/\s+/g, ' '), line: lineOf(i) })
    i = j
  }
  return rules
}

/** 返回没限定在 .fb-page 之下的选择器：{ selector, part, line } */
function unscopedSelectors(source) {
  const out = []
  for (const rule of topLevelRules(source)) {
    for (const part of rule.selector.split(',')) {
      const text = part.trim()
      if (!text || text === 'page' || SCOPED.test(text)) continue
      out.push({ selector: rule.selector, part: text, line: rule.line })
    }
  }
  return out
}

function walkStyles(dir) {
  const files = []
  if (!fs.existsSync(dir)) return files
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name)
    if (entry.isDirectory()) files.push(...walkStyles(full))
    else if (entry.isFile() && entry.name.endsWith('.wxss')) files.push(full)
  }
  return files.sort()
}

function toPosix(value) {
  return value.split(path.sep).join('/')
}

function collect(mpRoot) {
  const pagesRoot = path.join(mpRoot, 'pages')
  if (!fs.existsSync(pagesRoot) || !fs.statSync(pagesRoot).isDirectory()) {
    fail(`pages directory not found under miniprogram root: ${mpRoot}`)
  }
  const shared = path.join(mpRoot, SHARED_STYLE_DIR, SHARED_STYLE_NAME)
  if (!fs.existsSync(shared)) {
    fail(`shared style not found: ${shared}（改名或挪位置时请同步本脚本）`)
  }
  const files = [shared]
  for (const file of walkStyles(pagesRoot)) {
    if (file === shared) continue
    if (SHARED_STYLE_IMPORT.test(fs.readFileSync(file, 'utf8'))) files.push(file)
  }
  return files
}

function selftest() {
  const cases = [
    { name: 'page 底色规则放行', css: 'page { background: #f5f7fb; }', ok: true },
    { name: '@import 行放行', css: '@import "fabushi-v7.wxss";\n.fb-page .a { color: red; }', ok: true },
    { name: '子目录页面的 @import 行放行', css: "@import '../fabushi-v7.wxss';\n.fb-page .a { color: red; }", ok: true },
    { name: '注释里的选择器不算', css: '/* 说明 .card { } */\n.fb-page .b { color: red; }', ok: true },
    { name: '多段选择器都限定了', css: '.fb-page .cover.navy, .fb-page .book-ico.navy { color: red; }', ok: true },
    { name: '伪元素', css: '.fb-page .stats-hero::after { content: none; }', ok: true },
    { name: '@keyframes 整块跳过', css: '@keyframes flyup { 0% { opacity: 0; } 100% { opacity: 1; } }\n.fb-page .plus { color: red; }', ok: true },
    { name: '单类名要拦', css: '.card { color: red; }', ok: false },
    { name: '多段里有一段没限定要拦', css: '.fb-page .a, .b { color: red; }', ok: false },
    { name: '.fb-page-foo 不算限定', css: '.fb-page-foo { color: red; }', ok: false },
    { name: '.fb-page 在后面不算限定', css: '.card .fb-page { color: red; }', ok: false },
    { name: 'page 加了类名不算 page', css: 'page.x { color: red; }', ok: false },
    { name: '元素选择器要拦', css: 'view { color: red; }', ok: false },
  ]
  let failed = 0
  for (const item of cases) {
    const found = unscopedSelectors(item.css)
    const passed = (found.length === 0) === item.ok
    if (!passed) failed += 1
    process.stdout.write(`  ${passed ? 'ok  ' : 'FAIL'}  ${item.name}\n`)
  }
  if (failed > 0) {
    process.stdout.write(`\nFAIL: style-scope selftest: ${failed}/${cases.length} cases behaved unexpectedly (the checker itself is wrong).\n`)
    process.exitCode = 1
    return
  }
  process.stdout.write(`OK: style-scope selftest passed (${cases.length} cases).\n`)
}

function main() {
  const options = parseArgs(process.argv.slice(2))
  if (options.selftest) {
    selftest()
    return
  }
  const mpRoot = options.root
  if (!fs.existsSync(mpRoot) || !fs.statSync(mpRoot).isDirectory()) {
    fail(`miniprogram root not found: ${mpRoot}`)
  }

  const files = collect(mpRoot)
  process.stdout.write('MP fabushi style-scope guard\n')
  process.stdout.write(`  root: ${mpRoot}\n`)
  let ruleCount = 0
  const violations = []
  for (const file of files) {
    const source = fs.readFileSync(file, 'utf8')
    ruleCount += topLevelRules(source).length
    for (const item of unscopedSelectors(source)) {
      violations.push({ file: toPosix(path.relative(mpRoot, file)), ...item })
    }
  }
  process.stdout.write(`  files: ${files.length}; top-level rules: ${ruleCount}\n`)

  if (violations.length > 0) {
    process.stdout.write(`\nFAIL: ${violations.length} selector(s) are not scoped under .fb-page\n`)
    for (const item of violations) {
      process.stdout.write(`  ${item.file}:${item.line}  ${item.part}  (in "${item.selector}")\n`)
    }
    process.stdout.write('\nFix: prefix the selector with ".fb-page " (only `page` may stay unscoped).\n')
    process.exitCode = 1
    return
  }
  process.stdout.write('\nOK: every selector in the fabushi v7 styles is scoped under .fb-page.\n')
}

main()
