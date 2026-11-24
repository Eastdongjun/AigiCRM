#!/usr/bin/env node
// check-vue-template-bindings.mjs 的行为测试（2026-11-24 GL-14）。
// 在临时目录里搭一棵最小的前端树(src/ 放夹具, node_modules 软链到真实的 aigicrm-ui/node_modules 以便用真编译器),
// 逐个场景运行契约脚本, 判定只认退出码与输出里点名的绑定。最后两个场景对真实源码树与历史缺陷文件各跑一次。
// 用法: node scripts/test-check-vue-template-bindings.mjs
import fs from 'node:fs'
import path from 'node:path'
import os from 'node:os'
import { execFileSync, spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'

const HERE = path.dirname(fileURLToPath(import.meta.url))
const CONTRACT = path.join(HERE, 'check-vue-template-bindings.mjs')
const UI = path.resolve(HERE, '..', 'aigicrm-ui')
const SRC = path.resolve(HERE, '..')
const WORK = fs.mkdtempSync(path.join(os.tmpdir(), 'tpl-bindings-'))

let passed = 0
let failed = 0
function runCase(label, expectExit, expectWords, cwd = WORK) {
  const result = spawnSync(process.execPath, [CONTRACT, cwd], { encoding: 'utf8' })
  const output = (result.stdout || '') + (result.stderr || '')
  const wordsFound = expectWords.every((word) => output.includes(word))
  if (result.status === expectExit && wordsFound) {
    passed++
    console.log(`PASS ${label}`)
  } else {
    failed++
    console.log(`FAIL ${label} — 期望退出码 ${expectExit}${expectWords.length ? `、输出含 [${expectWords.join(' ')}]` : ''}; 实际退出码 ${result.status}, 输出: ${output.trim().split('\n')[0]}`)
  }
}

function reset(fixtures) {
  fs.rmSync(path.join(WORK, 'src'), { recursive: true, force: true })
  fs.rmSync(path.join(WORK, 'node_modules'), { recursive: true, force: true })
  fs.mkdirSync(path.join(WORK, 'src'), { recursive: true })
  fs.symlinkSync(path.join(UI, 'node_modules'), path.join(WORK, 'node_modules'), 'dir')
  fs.writeFileSync(path.join(WORK, 'package.json'), '{"name":"fixture","private":true}')
  for (const [name, content] of Object.entries(fixtures)) {
    const target = path.join(WORK, 'src', name)
    fs.mkdirSync(path.dirname(target), { recursive: true })
    fs.writeFileSync(target, content)
  }
}

try {
  // ① 合格: setup 里有的名字, 模板里随便用(插值、属性、事件、v-for 的别名、内置的 $emit/$slots)
  reset({
    'Good.vue': `<template>
  <div :title="title" @click="pick(list)"><slot /></div>
  <button v-for="item in list" :key="item.id" @click="pick(item)">{{ labelOf(item) }}</button>
  <i ref="root" @click="$emit('close')" />
</template>
<script setup>
import { ref } from 'vue'
const title = ref('t')
const list = ref([])
const pick = (x) => x
const labelOf = (x) => x.name
</script>
`,
    })
  runCase('① 合格写法: setup 里有的名字随便用', 0, ['1 个 .vue'])

  // ①b v-for 的别名出了循环就不存在: 在外面用要报出来
  reset({
    'Leak.vue': `<template><div @click="pick(item)"></div><p v-for="item in list" :key="item">{{ item }}</p></template>
<script setup>
const list = ref([])
const pick = (x) => x
</script>
`,
    })
  runCase('①b v-for 的别名在循环外面被使用', 1, ['item'])

  // ② options API 组件: data / methods / computed 里的名字在模板里可用, 不得误报
  reset({
    'Options.vue': `<template><div @click="go">{{ name }}{{ doubled }}</div></template>
<script>
export default {
  props: ['name'],
  data() { return { n: 2 } },
  computed: { doubled() { return this.n * 2 } },
  methods: { go() {} },
}
</script>
`,
    })
  runCase('② options API 组件不误报', 0, [])

  // ③ 事件处理函数不存在: 挂载冒烟抓不到的那一类
  reset({
    'Bad.vue': `<template><button @click="removeBookmarkX(item)">删</button></template>
<script setup>
const item = { id: 1 }
</script>
`,
    })
  runCase('③ 模板引用了不存在的事件处理函数', 1, ['removeBookmarkX', 'Bad.vue'])

  // ④ 属性绑定不存在(真实缺陷的形状: :files="trackFile")
  reset({
    'Pager.vue': `<template><Pager slot-key="track" :files="trackFile" /></template>
<script setup>
const slotKey = 'track'
</script>
`,
    })
  runCase('④ 模板把不存在的名字传给子组件的属性', 1, ['trackFile'])

  // ⑤ 多个文件各有问题: 都要点名
  reset({
    'a/One.vue': `<template><p>{{ missingOne }}</p></template>
<script setup>
const ok = 1
</script>
`,
    'b/Two.vue': `<template><p>{{ ok }}{{ missingTwo }}</p></template>
<script setup>
const ok = 1
</script>
`,
    })
  runCase('⑤ 多个文件的问题都点名', 1, ['missingOne', 'missingTwo', 'a/One.vue', 'b/Two.vue'])

  // ⑥ 无法编译的文件也要报出来, 不能静默跳过: 一种是模板编译阶段报错
  reset({
    'Broken.vue': `<template><div>a</div><div v-else>b</div></template>
<script setup>
const x = 1
</script>
`,
    })
  runCase('⑥ 模板编译报错的文件要报出来', 1, ['Broken.vue', '编译'])

  // ⑦ 另一种是整个文件的解析阶段报错(编译模板时不再报, 只看编译就会漏掉)
  reset({
    'Unclosed.vue': `<template><p>{{ x </p></template>
<script setup>
const x = 1
</script>
`,
    })
  runCase('⑦ 文件解析报错的文件要报出来', 1, ['Unclosed.vue', '解析'])

  // ⑧ 找不到编译器: 环境不具备, 退出码 2(不是 1)
  reset({})
  fs.rmSync(path.join(WORK, 'node_modules'), { recursive: true, force: true })
  runCase('⑧ 找不到编译器 → BLOCKED', 2, ['BLOCKED'])

  // ⑨ 参数不是前端目录
  runCase('⑨ 目录不对 → BLOCKED', 2, ['BLOCKED'], path.join(WORK, 'nope'))

  // ⑩ 真实源码树
  runCase('⑩ 真实源码树', 0, ['PASS'], UI)

  // ⑪ 历史缺陷文件: incentive/UploadDialog.vue 里 trackFile 漏了解构(2026-10-30 起, 11-24 修复), 必须被点名。
  //    第一版直接拿 HEAD 版文件当缺陷样本——那只是因为当时修复还没提交。修复一提交, HEAD 就是修好的版本,
  //    这个场景立刻变红(2026-11-25 提交之后跑全量契约时实测)。现在不依赖 git 历史: 取当前文件, 把解构里的
  //    trackFile 去掉, 还原出当年的缺陷; 替换没生效(代码写法变了)就报失败, 提醒更新这里, 而不是悄悄跳过。
  const current = fs.readFileSync(path.join(UI, 'src', 'views', 'incentive', 'UploadDialog.vue'), 'utf8')
  const reverted = current.replace('fileSlots, trackFile,', 'fileSlots,')
  if (current === reverted) {
    failed++
    console.log('FAIL ⑪ 历史缺陷 — 没能从当前文件还原出缺陷(UploadDialog.vue 的解构写法变了, 请更新本场景)')
  } else {
    reset({ 'UploadDialog.vue': reverted })
    runCase('⑪ 历史缺陷(从当前文件还原): trackFile 漏了解构', 1, ['trackFile', 'UploadDialog.vue'])
  }
} finally {
  fs.rmSync(WORK, { recursive: true, force: true })
}

console.log(`通过 ${passed} 项, 未通过 ${failed} 项`)
process.exit(failed ? 1 : 0)
