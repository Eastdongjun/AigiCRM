#!/usr/bin/env node
// 【GL-14 2026-11-24】Vue 单文件组件的模板不得引用 setup 里不存在的绑定。
//
// 缘起: incentive/UploadDialog.vue 的模板把 :files="trackFile" 传给“跟踪记录”那一格, 但 script 里漏了解构 trackFile。
//       翻页组件拿到 undefined, 在 setup 里直接抛错, 那一格整个渲染不出来——自 2026-10-30 起就在生产上。
//       挂载冒烟抓不到这一类: 属性与事件处理函数写在模板里, Vue 不发警告, 到渲染或点击那一格才报错。
//
// 做法: 用 Vue 自带的编译器。compileScript 得到 <script setup>(或 options API)的全部绑定;
//       compileTemplate 带上这份绑定信息编译模板——属于 setup 的名字编译成 $setup.x, 其余落到 _ctx.x。
//       _ctx.x 里除去实例上确实存在的内置名字($emit、$attrs、$router 等), 剩下的就是运行期找不到的名字。
// 契约自身的测试: scripts/test-check-vue-template-bindings.mjs(隔离夹具, 含上面那个真实出过问题的文件)。
//
// 用法: node scripts/check-vue-template-bindings.mjs [前端目录]   (默认 aigicrm-ui)
// 退出码: 0 全部通过 / 1 有模板引用了不存在的绑定(或无法编译) / 2 环境不具备(找不到编译器、目录不对)
import fs from 'node:fs'
import path from 'node:path'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'

// 实例上确实存在、但不来自 setup 的名字(组件内置 / 路由等插件挂的全局属性)
const BUILTIN = new Set([
  '$emit', '$attrs', '$slots', '$refs', '$el', '$props', '$parent', '$root', '$nextTick', '$forceUpdate',
  '$options', '$expose', '$data', '$watch', '$router', '$route', '$t',
])

const uiRoot = process.argv[2]
  ? path.resolve(process.argv[2])
  : path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', 'aigicrm-ui')

function insideRoot(root, target) {
  const rel = path.relative(root, target)
  return rel !== '' && !rel.startsWith('..') && !path.isAbsolute(rel)
}

function listVueFiles(dir, root, out) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.resolve(dir, entry.name)
    if (!insideRoot(root, full)) continue
    if (entry.isDirectory()) listVueFiles(full, root, out)
    else if (entry.name.endsWith('.vue')) out.push(full)
  }
  return out
}

function loadCompiler(root) {
  try {
    return createRequire(path.join(root, 'package.json'))('@vue/compiler-sfc')
  } catch (e) {
    console.error(`BLOCKED: 在 ${root} 下找不到 @vue/compiler-sfc —— 请先在该目录执行 npm install。(${e.message})`)
    process.exit(2)
  }
}

const srcDir = path.join(uiRoot, 'src')
if (!fs.existsSync(srcDir)) {
  console.error(`BLOCKED: ${srcDir} 不存在 —— 参数应当是前端目录(里面有 src/ 与 package.json)`)
  process.exit(2)
}
const sfc = loadCompiler(uiRoot)

const files = listVueFiles(srcDir, uiRoot, [])
const bad = []
const broken = []
for (const file of files) {
  const rel = path.relative(uiRoot, file)
  // 编译器分两段报错: 整个文件的解析(标签没闭合、属性重复…)与模板编译(v-else 没有 v-if…)。两段都要看。
  const parsed = sfc.parse(fs.readFileSync(file, 'utf8'), { filename: file })
  if (parsed.errors.length) {
    broken.push(`${rel}: 文件解析错误 ${parsed.errors.map((e) => (e && e.message) || e).join('; ')}`)
    continue
  }
  const descriptor = parsed.descriptor
  if (!descriptor.template) continue
  let bindings
  try {
    bindings = (descriptor.script || descriptor.scriptSetup) ? (sfc.compileScript(descriptor, { id: 'bindings' }).bindings || {}) : {}
  } catch (e) {
    broken.push(`${rel}: script 编译失败 ${String(e.message).split('\n')[0]}`)
    continue
  }
  const result = sfc.compileTemplate({
    source: descriptor.template.content,
    filename: file,
    id: 'bindings',
    compilerOptions: { bindingMetadata: bindings },
  })
  if (result.errors.length) {
    broken.push(`${rel}: 模板编译错误 ${result.errors.map((e) => (e && e.message) || e).join('; ')}`)
    continue
  }
  const unknown = new Set()
  for (const match of result.code.matchAll(/_ctx\.([A-Za-z_$][\w$]*)/g)) {
    if (!BUILTIN.has(match[1])) unknown.add(match[1])
  }
  if (unknown.size) bad.push(`${rel}: ${[...unknown].join(', ')}`)
}

if (broken.length) {
  console.error(`FAIL: ${broken.length} 个 .vue 无法编译:`)
  for (const line of broken) console.error(`  ${line}`)
}
if (bad.length) {
  console.error(`FAIL: ${bad.length} 个 .vue 的模板引用了 setup 里不存在的绑定:`)
  for (const line of bad) console.error(`  ${line}`)
}
if (broken.length || bad.length) process.exit(1)
console.log(`PASS: ${files.length} 个 .vue 模板引用的绑定都存在。`)
