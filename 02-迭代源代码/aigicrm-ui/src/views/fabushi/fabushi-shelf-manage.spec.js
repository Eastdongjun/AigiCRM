import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'

// 书架管理模式的卡片布局（2026-11-26）：卡片里的四个操作按钮（置顶 / 上移 / 下移 / 移出）必须独占一行。
// 出问题时的样子：按钮和封面、书名挤在同一行，一行放不下，书名被盖住、备注被压成一列一字。
const sfc = readFileSync('src/views/fabushi/shelf.vue', 'utf-8')
const template = sfc.slice(0, sfc.indexOf('<script'))
const style = (sfc.match(/<style scoped>([\s\S]*?)<\/style>/) || [])[1] || ''

function rules(css) {
  return [...css.replace(/\/\*[\s\S]*?\*\//g, '').matchAll(/([^{}]+)\{([^{}]*)\}/g)]
    .map((m) => ({ selector: m[1].trim().replace(/\s+/g, ' '), body: m[2] }))
}
// 某个选择器的规则里，某项属性声明的值（同一个选择器有几条规则就有几个值）
function declared(selector, property) {
  return rules(style)
    .filter((r) => r.selector === selector)
    .map((r) => (r.body.match(new RegExp(`(?:^|;)\\s*${property}\\s*:\\s*([^;]+);`)) || [])[1])
    .filter(Boolean)
}

describe('书架管理模式的卡片布局', () => {
  it('卡片在管理模式下带 managing 类，由 manageMode 决定', () => {
    // 页面上还有别的 <article>（觉醒总览的三个板块），这里只看书架卡片那一个
    const tag = (template.match(/<article\b[^>]*class="shelf-card"[^>]*>/) || [])[0]
    expect(tag).toContain('managing: manageMode')
  })

  it('管理模式的卡片允许换行，操作按钮独占第二行', () => {
    expect(declared('.shelf-card.managing', 'flex-wrap')).toEqual(['wrap'])
    expect(declared('.shelf-card.managing .card-actions', 'flex')).toEqual(['1 0 100%'])
    expect(declared('.shelf-card.managing .card-actions', 'max-width')).toEqual(['none'])
  })

  // 对照：匹配器确实读到了样式表 —— 不带 managing 的那条规则原本就把按钮限制在最宽 130px
  it('匹配器对照', () => {
    expect(declared('.card-actions', 'max-width')).toEqual(['130px'])
    expect(declared('.shelf-card.managing', 'flex-wrap')).not.toEqual(['wrap wrap'])
  })
})
