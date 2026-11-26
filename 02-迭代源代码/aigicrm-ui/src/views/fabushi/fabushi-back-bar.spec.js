import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'

// 全局样式里不再有“法布施书卷皮肤”（2026-11-26，用户裁决“PC 端按最新的原型来做”）。
// 缘起：style.css 里曾有一段“法布施书卷皮肤”，用变量覆盖法把 .fabushi-* 页面染成米色纸底 + 墨绿 + 朱砂，
// 还给页面容器加了自己的底色、圆角、内边距和衬线标题 —— PC 法布施 13 屏因此全都与 PC 原型 v8（白底 + 蓝色主色）不一样。
// 其中一条 `.fabushi-feed .fb-fab, .fabushi-detail .back { background: #b5543a; }` 还把详情页顶部的返回条刷成红底。
// 现在的分工：法布施页面用的是应用默认的设计令牌（与原型一致），页面自己的样式在各视图和 assets/fabushi-v8.css 里；
// 全局 style.css 不再出现任何 .fabushi-* 的规则。jsdom 不加载全局样式，挂载测试看不到这种问题，所以直接读样式表逐条规则查。
// vitest 的 cwd 是 aigicrm-ui 根目录（与 FabushiMuyu.spec.js 同一个做法）。
const css = readFileSync('src/style.css', 'utf-8')

/** 把样式表拆成 { selector, body }（去掉注释；一行一条或一行多条都认） */
function rules(text) {
  return [...text.replace(/\/\*[\s\S]*?\*\//g, '').matchAll(/([^{}]+)\{([^{}]*)\}/g)]
    .map((m) => ({ selector: m[1].trim().replace(/\s+/g, ' '), body: m[2] }))
}

/** 选择器里提到 .fabushi- 开头的类名的规则（一个选择器列表里只要有一段提到就算） */
const fabushiRules = (text) => rules(text).filter((r) => /(^|[\s,>+~])\.fabushi-[\w-]*/.test(r.selector))

/** 所有“选择器里含 target 这一段、并且声明了 property”的规则 */
function rulesDeclaring(text, target, property) {
  const want = new RegExp(`(?:^|,)\\s*${target.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\s*(?:,|$)`)
  return rules(text).filter((r) => want.test(r.selector) && new RegExp(`(?:^|;)\\s*${property}\\s*:`, '').test(r.body))
}

describe('全局样式里的法布施页面', () => {
  it('先确认读到了那份样式表、匹配器能用', () => {
    // 全局页面骨架 .page-shell 的白底规则一直都在: 拿它证明解析器确实读到了样式表
    const shells = rulesDeclaring(css, '.page-shell', 'background')
    expect(shells).toHaveLength(1)
    expect(shells[0].body).toContain('background:#fff')
    // 对照：旧皮肤块的几条规则必须都能被“提到 .fabushi- 的规则”查出来
    const skin = '.fabushi-feed, .fabushi-detail { --primary-color: #2d5a4a; }\n.fabushi-feed .fb-fab, .fabushi-detail .back { background: #b5543a; }\n'
    expect(fabushiRules(skin).map((r) => r.selector)).toEqual(['.fabushi-feed, .fabushi-detail', '.fabushi-feed .fb-fab, .fabushi-detail .back'])
    // 同名前缀的别的类（例如 .fabushiish）不算
    expect(fabushiRules('.fabushiish-x { color: red; } .x .not-fabushi-detail { color: blue; }')).toEqual([])
    // 返回条那条规则：故意写出来必须被查出来；只设 display 或类名不完全相同的不能误判
    expect(rulesDeclaring(skin, '.fabushi-detail .back', 'background').map((r) => r.selector)).toEqual(['.fabushi-feed .fb-fab, .fabushi-detail .back'])
    expect(rulesDeclaring('.fabushi-detail .back { display: flex; }', '.fabushi-detail .back', 'background')).toEqual([])
    expect(rulesDeclaring('.fabushi-detail .backdrop { background: red; }', '.fabushi-detail .back', 'background')).toEqual([])
  })

  it('没有任何规则的选择器提到 .fabushi-（书卷皮肤已经撤掉）', () => {
    expect(fabushiRules(css)).toEqual([])
  })

  it('没有任何规则给 .fabushi-detail .back 上 background', () => {
    expect(rulesDeclaring(css, '.fabushi-detail .back', 'background')).toEqual([])
  })
})
