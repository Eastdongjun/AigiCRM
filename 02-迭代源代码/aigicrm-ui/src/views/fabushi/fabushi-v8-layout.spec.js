import { describe, it, expect } from 'vitest'
import { existsSync, readFileSync } from 'node:fs'

// PC 法布施按原型 v8 来做的两条骨架约定（2026-11-26，用户裁决“PC 端按最新的原型来做”）。
// ① 页面骨架：原型里法布施页面是“灰画布上直接摆卡片”，没有全局的白色整体容器 ——
//    法布施路由 meta.bareShell = true，layout/index.vue 据此去掉白底、换成原型的内边距；别的页面不变。
// ② 公共样式：src/assets/fabushi-v8.css 放原型的通用组件（页签、筛选条、标签、侧栏卡片……），
//    所有选择器都限定在 .fx-page 之下，不会波及其它模块（全局 app 里同名类很多，11-26 做小程序时就是这样被盖掉过）。
// vitest 的 cwd 是 aigicrm-ui 根目录（与 FabushiMuyu.spec.js 同一个做法）。
const routerSrc = readFileSync('src/router/index.js', 'utf-8')
const layoutSrc = readFileSync('src/layout/index.vue', 'utf-8')
const SHARED = 'src/assets/fabushi-v8.css'
const FABUSHI_ROUTES = [
  'fabushi/feed', 'fabushi/detail/:id', 'fabushi/library', 'fabushi/shelf', 'fabushi/highlights',
  'fabushi/read/:bookId', 'fabushi/book', 'fabushi/content', 'fabushi/stats',
]

/** 路由表里每个路由的 path 与 meta（一条路由 = path … name … component … meta: { … }） */
function routeMetas(text) {
  return [...text.matchAll(/path: '([^']+)',\s*\n\s*name: '[^']+',\s*\n\s*component: [^\n]+,\s*\n\s*meta: \{([^\n]*)\}/g)]
    .map((m) => ({ path: m[1], meta: m[2] }))
}

/** 样式表里所有规则的选择器（去掉注释；@media 之类的外层块不算，只取里面的规则；字符串里的内容不算语法） */
function selectors(css) {
  const out = []
  let buf = ''
  let quote = ''
  for (const ch of css.replace(/\/\*[\s\S]*?\*\//g, '')) {
    if (quote) {
      if (ch === quote) quote = ''
    } else if (ch === '"' || ch === "'") {
      quote = ch
    } else if (ch === '{') {
      const s = buf.trim()
      if (s && !s.startsWith('@')) out.push(s)
      buf = ''
    } else if (ch === '}' || ch === ';') {
      buf = ''
    } else {
      buf += ch
    }
  }
  return out
}

describe('法布施页面的骨架', () => {
  it('九个法布施路由都带 meta.bareShell', () => {
    const metas = routeMetas(routerSrc)
    const fabushi = metas.filter((r) => r.path.startsWith('fabushi/'))
    expect(fabushi.map((r) => r.path)).toEqual(FABUSHI_ROUTES)
    for (const r of fabushi) expect(r.meta, r.path).toMatch(/bareShell: true/)
  })

  it('别的路由都没有带 bareShell（白底骨架只对法布施去掉）', () => {
    const others = routeMetas(routerSrc).filter((r) => !r.path.startsWith('fabushi/') && /bareShell/.test(r.meta))
    expect(others.map((r) => r.path)).toEqual([])
  })

  it('layout/index.vue 按 bareShell 去掉白底并换成原型的内边距', () => {
    expect(layoutSrc).toMatch(/const bareShell = computed\(\(\) => Boolean\(route\.meta\.bareShell\)\)/)
    expect(layoutSrc).toMatch(/<el-main class="main-content" :class="\{ 'main-content--bare': bareShell \}">/)
    expect(layoutSrc).toMatch(/<div class="page-shell" :class="\{ 'page-shell--bare': bareShell \}">/)
    expect(layoutSrc).toMatch(/\.main-content\.main-content--bare\s*\{[^}]*padding: 12px 16px/)
    expect(layoutSrc).toMatch(/\.page-shell\.page-shell--bare\s*\{[^}]*background: transparent/)
  })
})

describe('公共样式 fabushi-v8.css', () => {
  it('文件在，并且每个选择器都以 .fx- 开头（.fx-page 页面内 / .fx-dialog 挂在 body 下的弹窗）', () => {
    expect(existsSync(SHARED)).toBe(true)
    const bad = selectors(readFileSync(SHARED, 'utf-8'))
      .flatMap((s) => s.split(','))
      .map((s) => s.replace(/"[^"]*"|'[^']*'/g, '""').trim())
      .filter((s) => s && !/^(from|to|\d+%)$/.test(s) && !s.startsWith('.fx-'))
    expect(bad).toEqual([])
  })

  it('对照：选择器提取能用', () => {
    const css = '/* 注释 { } */\n.fx-page .a, .fx-page .b { color: red; }\n@media (max-width: 900px) { .fx-page .c { color: blue } }\n.fx-page .d::after { content: "{" }\n.x .y { color: green; }'
    expect(selectors(css)).toEqual(['.fx-page .a, .fx-page .b', '.fx-page .c', '.fx-page .d::after', '.x .y'])
  })
})
