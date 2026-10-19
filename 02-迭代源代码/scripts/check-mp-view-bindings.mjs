#!/usr/bin/env node
/**
 * 静态门禁：WXML 读取派生视图字段，但同目录 JS 没有生产处。
 *
 * 背景：小程序页面习惯在 JS 里把接口/表单数据加工成「视图字段」再交给
 * WXML（例如 nameText、optionsView、showTrialRemark）。漏生产时 WXML 静默
 * 渲染为空，页面看似正常，回归时很难发现。本脚本在提交前拦截这类问题。
 *
 * 检查范围：
 *   - 扫描 pages 下所有 *.wxml；
 *   - 只取形如 item.xxx 且 xxx 命中以下规则的绑定：
 *       /^(?:show|can|is|has)[A-Z]/         （showXxx / canXxx / isXxx / hasXxx）
 *       /(?:Text|Label|Class|Tone|Value|Display|View|ToneClass)$/
 *   - 在同目录 index.js、shared.js 的「生产端文本」里查找字段名；
 *     文本中不出现（已去掉注释）即视为缺少生产处。
 *
 * 基线：
 *   已确认的误报（例如跨目录 shared.js 生产、后端直接下发）写入
 *   scripts/mp-view-bindings-baseline.json，按「wxml 相对路径 + 字段名」匹配，
 *   每条必须带 reason。行号变化不会让基线失效。
 *
 * 退出码：0 = 通过；1 = 存在未基线化的缺失；2 = 参数/配置/读取错误。
 *
 * 用法：
 *   node scripts/check-mp-view-bindings.mjs
 *   node scripts/check-mp-view-bindings.mjs --root ../aigicrm-miniprogram
 *   node scripts/check-mp-view-bindings.mjs --json
 *   node scripts/check-mp-view-bindings.mjs --fail-on-stale
 */

import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const SCRIPT_DIR = path.dirname(fileURLToPath(import.meta.url))
const DEFAULT_MP_ROOT = path.resolve(SCRIPT_DIR, '..', 'aigicrm-miniprogram')
const DEFAULT_BASELINE = path.join(SCRIPT_DIR, 'mp-view-bindings-baseline.json')

// 与需求保持一致：前缀式派生字段 + 后缀式派生字段。
const DERIVED_PREFIX = /^(?:show|can|is|has)[A-Z]/
const DERIVED_SUFFIX = /(?:Text|Label|Class|Tone|Value|Display|View|ToneClass)$/
const ITEM_BINDING = /(?<![\w$.])item\??\.([A-Za-z_$][\w$]*)/g

const SAME_DIR_JS = ['index.js', 'shared.js']

function usage() {
  return `Usage: node scripts/check-mp-view-bindings.mjs [options]

Options:
  --root <dir>      小程序根目录（默认: ../aigicrm-miniprogram）
  --baseline <file> 误报基线（默认: scripts/mp-view-bindings-baseline.json）
  --json            输出机器可读 JSON
  --fail-on-stale   基线条目已不再命中时也以非 0 退出（默认只告警）
  -h, --help        显示帮助
`
}

function fail(message, code = 2) {
  process.stderr.write(`ERROR: ${message}\n`)
  process.exit(code)
}

function parseArgs(argv) {
  const options = {
    root: DEFAULT_MP_ROOT,
    baseline: DEFAULT_BASELINE,
    baselineExplicit: false,
    json: false,
    failOnStale: false,
  }

  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i]
    if (arg === '-h' || arg === '--help') {
      process.stdout.write(usage())
      process.exit(0)
    }
    if (arg === '--json') {
      options.json = true
      continue
    }
    if (arg === '--fail-on-stale') {
      options.failOnStale = true
      continue
    }
    if (arg === '--root' || arg === '--baseline') {
      const value = argv[i + 1]
      if (!value || value.startsWith('--')) fail(`missing value for ${arg}`)
      i += 1
      if (arg === '--root') options.root = path.resolve(value)
      else {
        options.baseline = path.resolve(value)
        options.baselineExplicit = true
      }
      continue
    }
    fail(`unknown argument: ${arg}\n\n${usage()}`)
  }

  return options
}

function isDerivedField(name) {
  return DERIVED_PREFIX.test(name) || DERIVED_SUFFIX.test(name)
}

function walkWxml(dir) {
  const files = []
  const entries = fs.readdirSync(dir, { withFileTypes: true })
  for (const entry of entries) {
    const full = path.join(dir, entry.name)
    if (entry.isDirectory()) files.push(...walkWxml(full))
    else if (entry.isFile() && entry.name.endsWith('.wxml')) files.push(full)
  }
  return files
}

// 把注释替换成等长空白，保留换行，保证行号仍准确。
function stripWxmlComments(source) {
  return source.replace(/<!--[\s\S]*?-->/g, (comment) => comment.replace(/[^\n]/g, ' '))
}

// 去掉 JS 注释（字符串/模板字符串内的 //、/* 不误删），保留换行与偏移。
function stripJsComments(source) {
  const chars = source.split('')
  let state = 'code'
  for (let i = 0; i < source.length; i += 1) {
    const current = source[i]
    const next = source[i + 1]

    if (state === 'code') {
      if (current === '"' || current === "'" || current === '`') {
        state = current
      } else if (current === '/' && next === '/') {
        chars[i] = ' '
        chars[i + 1] = ' '
        i += 1
        state = 'line-comment'
      } else if (current === '/' && next === '*') {
        chars[i] = ' '
        chars[i + 1] = ' '
        i += 1
        state = 'block-comment'
      }
      continue
    }

    if (state === 'line-comment') {
      if (current === '\n') state = 'code'
      else chars[i] = ' '
      continue
    }

    if (state === 'block-comment') {
      if (current === '*' && next === '/') {
        chars[i] = ' '
        chars[i + 1] = ' '
        i += 1
        state = 'code'
      } else if (current !== '\n') {
        chars[i] = ' '
      }
      continue
    }

    // 字符串 / 模板字符串：跳过转义，遇到同种引号退出。
    if (current === '\\') {
      i += 1
    } else if (current === state) {
      state = 'code'
    }
  }
  return chars.join('')
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

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

function hasProductionEvidence(jsSource, field) {
  const pattern = new RegExp(`(^|[^A-Za-z0-9_$])${escapeRegExp(field)}([^A-Za-z0-9_$]|$)`)
  return pattern.test(jsSource)
}

function toPosix(value) {
  return value.split(path.sep).join('/')
}

function loadBaseline(file, explicit) {
  if (!fs.existsSync(file)) {
    if (explicit) fail(`baseline not found: ${file}`)
    return { path: file, entries: [], warnings: [`baseline not found (${file}); treating as empty`] }
  }

  let parsed
  try {
    parsed = JSON.parse(fs.readFileSync(file, 'utf8'))
  } catch (error) {
    fail(`cannot parse baseline ${file}: ${error.message}`)
  }

  if (!parsed || !Array.isArray(parsed.entries)) {
    fail(`baseline ${file} must contain an "entries" array`)
  }

  const entries = []
  const seen = new Set()
  for (const [index, raw] of parsed.entries.entries()) {
    if (!raw || typeof raw !== 'object') fail(`baseline entry #${index + 1} is not an object`)
    const fileValue = typeof raw.file === 'string' ? raw.file.trim() : ''
    const fieldValue = typeof raw.field === 'string' ? raw.field.trim().replace(/^item\./, '') : ''
    const reason = typeof raw.reason === 'string' ? raw.reason.trim() : ''
    if (!fileValue) fail(`baseline entry #${index + 1} is missing "file"`)
    if (!fieldValue) fail(`baseline entry #${index + 1} is missing "field"`)
    if (!reason) fail(`baseline entry #${index + 1} (${fileValue} ${fieldValue}) is missing "reason"`)

    const normalizedFile = toPosix(fileValue).replace(/^\.\//, '')
    const key = `${normalizedFile}#${fieldValue}`
    if (seen.has(key)) fail(`duplicate baseline entry: ${key}`)
    seen.add(key)
    entries.push({ file: normalizedFile, field: fieldValue, reason, key, matched: false })
  }

  return { path: file, entries, warnings: [] }
}

function collectFindings(mpRoot) {
  const pagesRoot = path.join(mpRoot, 'pages')
  if (!fs.existsSync(pagesRoot) || !fs.statSync(pagesRoot).isDirectory()) {
    fail(`pages directory not found under miniprogram root: ${mpRoot}`)
  }

  const wxmlFiles = walkWxml(pagesRoot).sort()
  if (wxmlFiles.length === 0) fail(`no .wxml files found under ${pagesRoot}`)

  const findings = new Map()
  let derivedBindingCount = 0

  for (const wxmlFile of wxmlFiles) {
    const relativeFile = toPosix(path.relative(mpRoot, wxmlFile))
    const source = stripWxmlComments(fs.readFileSync(wxmlFile, 'utf8'))
    const lineOf = lineIndex(source)
    const pageDir = path.dirname(wxmlFile)

    const jsFiles = SAME_DIR_JS.map((name) => path.join(pageDir, name)).filter((file) => fs.existsSync(file))
    const jsSource = jsFiles
      .map((file) => stripJsComments(fs.readFileSync(file, 'utf8')))
      .join('\n')

    ITEM_BINDING.lastIndex = 0
    let match
    while ((match = ITEM_BINDING.exec(source)) !== null) {
      const field = match[1]
      if (!isDerivedField(field)) continue
      derivedBindingCount += 1

      const key = `${relativeFile}#${field}`
      if (!findings.has(key)) {
        findings.set(key, {
          key,
          file: relativeFile,
          field,
          binding: `item.${field}`,
          lines: [],
          jsFiles: jsFiles.map((file) => toPosix(path.relative(mpRoot, file))),
        })
      }
      const finding = findings.get(key)
      const line = lineOf(match.index)
      if (!finding.lines.includes(line)) finding.lines.push(line)
      finding.produced = finding.produced || hasProductionEvidence(jsSource, field)
    }
  }

  return { wxmlFiles: wxmlFiles.length, derivedBindingCount, findings: [...findings.values()] }
}

function main() {
  const options = parseArgs(process.argv.slice(2))
  const mpRoot = options.root
  if (!fs.existsSync(mpRoot) || !fs.statSync(mpRoot).isDirectory()) {
    fail(`miniprogram root not found: ${mpRoot}`)
  }

  const baseline = loadBaseline(options.baseline, options.baselineExplicit)
  const baselineByKey = new Map(baseline.entries.map((entry) => [entry.key, entry]))
  const { wxmlFiles, derivedBindingCount, findings } = collectFindings(mpRoot)

  const violations = []
  const staleBaseline = []

  for (const finding of findings) {
    const baselineEntry = baselineByKey.get(finding.key)
    if (finding.produced) {
      if (baselineEntry) staleBaseline.push(baselineEntry)
      continue
    }
    if (baselineEntry) {
      baselineEntry.matched = true
      continue
    }
    violations.push(finding)
  }

  // 基线里有、但当前 WXML 已不再读取的条目：默认只告警，避免合并冲突时误伤。
  for (const entry of baseline.entries) {
    if (!entry.matched && !staleBaseline.includes(entry)) staleBaseline.push(entry)
  }

  const staleOnlyFailure = options.failOnStale && staleBaseline.length > 0
  const ok = violations.length === 0 && !staleOnlyFailure

  if (options.json) {
    process.stdout.write(JSON.stringify({
      ok,
      root: mpRoot,
      wxmlFiles,
      derivedBindings: derivedBindingCount,
      uniqueBindings: findings.length,
      violations: violations.map((item) => ({
        file: item.file,
        field: item.field,
        binding: item.binding,
        lines: item.lines.slice().sort((a, b) => a - b),
        jsFiles: item.jsFiles,
        reason: 'field name does not occur in same-directory index.js/shared.js',
      })),
      baseline: {
        path: baseline.path,
        entries: baseline.entries.length,
        matched: baseline.entries.filter((entry) => entry.matched).length,
        stale: staleBaseline.map((entry) => ({
          file: entry.file,
          field: entry.field,
          reason: entry.reason,
        })),
      },
      warnings: baseline.warnings,
    }, null, 2) + '\n')
  } else {
    process.stdout.write('MP view binding guard\n')
    process.stdout.write(`  root: ${mpRoot}\n`)
    process.stdout.write(`  wxml files: ${wxmlFiles}; derived item bindings: ${derivedBindingCount}; unique bindings: ${findings.length}\n`)
    process.stdout.write(`  baseline: ${baseline.path} (${baseline.entries.length} entries, ${baseline.entries.filter((entry) => entry.matched).length} matched)\n`)
    for (const warning of baseline.warnings) process.stderr.write(`WARN: ${warning}\n`)

    if (violations.length > 0) {
      process.stdout.write(`\nFAIL: ${violations.length} derived binding(s) have no production evidence in same-directory index.js/shared.js\n`)
      for (const item of violations) {
        const lines = item.lines.slice().sort((a, b) => a - b).join(',')
        const checked = item.jsFiles.length > 0 ? item.jsFiles.join(', ') : '(no index.js/shared.js)'
        process.stdout.write(`  ${item.file}:${lines}  ${item.binding}  checked: ${checked}\n`)
      }
      process.stdout.write('\nFix: produce the field in the page JS, or add a justified baseline entry.\n')
    }

    if (staleBaseline.length > 0) {
      process.stdout.write(`\nWARN: ${staleBaseline.length} baseline entr${staleBaseline.length === 1 ? 'y is' : 'ies are'} stale (no matching binding, or field is now produced)\n`)
      for (const entry of staleBaseline) {
        process.stdout.write(`  ${entry.file}  item.${entry.field}  reason: ${entry.reason}\n`)
      }
      if (options.failOnStale) process.stdout.write('  --fail-on-stale is set, so this run fails.\n')
    }

    if (ok) process.stdout.write('\nOK: every derived item.* binding has same-directory production evidence.\n')
  }

  // 不直接 process.exit：stdout 走管道时避免输出未刷完就退出。
  process.exitCode = ok ? 0 : 1
}

main()
