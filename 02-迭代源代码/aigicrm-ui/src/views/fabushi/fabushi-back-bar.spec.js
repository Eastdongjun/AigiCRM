import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'

// PC 法布施详情页的返回条不能再被全局样式上底色（2026-11-26）。
// 缘起：全局 style.css 的“法布施书卷皮肤”块里有一条 `.fabushi-feed .fb-fab, .fabushi-detail .back { background: #b5543a; }`，
// 详情页顶部“← 返回认知广场”那一行被整条刷成朱砂色，蓝字压在红底上几乎看不清（PC 原型 v8 的 P3 里这一行是没有底色的）。
// jsdom 不加载全局样式，挂载测试看不到这种问题，所以直接读样式表逐条规则查。
// vitest 的 cwd 是 aigicrm-ui 根目录（与 FabushiMuyu.spec.js 同一个做法）。
const css = readFileSync('src/style.css', 'utf-8')

/** 把样式表拆成 { selector, body }（去掉注释；一行一条或一行多条都认） */
function rules(text) {
  return [...text.replace(/\/\*[\s\S]*?\*\//g, '').matchAll(/([^{}]+)\{([^{}]*)\}/g)]
    .map((m) => ({ selector: m[1].trim().replace(/\s+/g, ' '), body: m[2] }))
}

/** 所有“选择器里含 target 这一段、并且声明了 property”的规则 */
function rulesDeclaring(text, target, property) {
  const want = new RegExp(`(?:^|,)\\s*${target.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\s*(?:,|$)`)
  return rules(text).filter((r) => want.test(r.selector) && new RegExp(`(?:^|;)\\s*${property}\\s*:`, '').test(r.body))
}

describe('全局样式里的法布施详情页返回条', () => {
  it('先确认读到了那份样式表、匹配器能用（对照：故意写一条这样的规则必须被查出来）', () => {
    // 皮肤块里“发布”悬浮按钮的红底规则一直都在: 拿它证明解析器确实读到了这个块（否则下面那条“没有规则”可能是空转）
    expect(rulesDeclaring(css, '.fabushi-feed .fb-fab', 'background').map((r) => r.body.trim())).toEqual(['background: #b5543a;'])
    const buggy = '.x { color: red; }\n.fabushi-feed .fb-fab, .fabushi-detail .back { background: #b5543a; }\n'
    expect(rulesDeclaring(buggy, '.fabushi-detail .back', 'background').map((r) => r.selector)).toEqual(['.fabushi-feed .fb-fab, .fabushi-detail .back'])
    expect(rulesDeclaring('.fabushi-detail .back { display: flex; }', '.fabushi-detail .back', 'background')).toEqual([])
    expect(rulesDeclaring('.fabushi-detail .backdrop { background: red; }', '.fabushi-detail .back', 'background')).toEqual([])
  })

  it('没有任何规则给 .fabushi-detail .back 上 background', () => {
    expect(rulesDeclaring(css, '.fabushi-detail .back', 'background')).toEqual([])
  })
})
