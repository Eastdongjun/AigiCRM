import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'

// 书架管理模式的布局（2026-11-26 按 PC 原型 v8 重做后仍然守着）：
// 管理模式下每本书的操作（状态下拉、置顶 / 上移 / 下移 / 移出）不能和书名挤在同一行。
// 出问题时的样子：按钮和封面、书名挤在同一行，一行放不下，书名被盖住、备注被压成一列一字。
// 原型 P9B 的封面格子是竖着排的：书脊 → 书名 → 进度文字 → 进度条 →（管理模式才有）操作区。
// 这里钉四件事：
//   ① 操作区只在管理模式出现，是格子里排在书名、进度条后面的一个独立块，书名那一行里没有任何按钮；
//   ② 格子本身是块级（没人把它改成横向的弹性容器），操作区另起一行；
//   ③ 操作按钮宽度固定、不收缩，放不下就换行；
//   ④ 列表视图同理：书名在可以收缩的区域里（min-width: 0），行尾的操作区不收缩、会换行。
// （管理模式下这些按钮真的在、点了走原来的接口，由 shelf-v8.spec.js 挂载视图验证。）
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

describe('书架管理模式的布局', () => {
  // 封面网格那一段模板：从格子（class="scell"）到列表视图（class="srow"）之前
  const grid = template.slice(template.indexOf('class="scell"'), template.indexOf('class="srow"'))

  it('操作区只在管理模式出现，是格子里排在书名、进度条后面的一个独立块', () => {
    const acts = (grid.match(/<div\b[^>]*class="scell-acts"[^>]*>/) || [])[0]
    expect(acts).toContain('v-if="manageMode"')
    // 点操作区里的东西不算“点了格子”（管理模式下点格子是勾选）
    expect(acts).toContain('@click.stop')
    // 顺序：书脊 → 书名 → 进度文字 → 进度条 → 操作区
    const order = ['class="book-spine"', 'class="sname"', 'class="sprog"', 'class="rb"', 'class="scell-acts"'].map((mark) => grid.indexOf(mark))
    expect(order.every((index) => index >= 0)).toBe(true)
    expect(order).toEqual([...order].sort((a, b) => a - b))
    // 书名那一行只有书名（和 NEW 标），没有任何按钮或下拉
    const name = (grid.match(/<div\b[^>]*class="sname"[^>]*>.*?<\/div>/) || [])[0] || ''
    expect(name).toContain('book.title')
    expect(name).not.toContain('<button')
    expect(name).not.toContain('<el-select')
  })

  it('封面格子竖着排：格子本身是块级（不是横向弹性容器），操作区另起一行', () => {
    expect(declared('.scell', 'display')).toEqual(['block'])
    expect(declared('.scell-acts', 'display')).toEqual(['block'])
    // 以后要是有人把格子改成横向弹性容器，这里会先红
    expect(rules(style).filter((r) => r.selector === '.scell').some((r) => /flex|grid/.test(r.body))).toBe(false)
  })

  it('操作按钮宽度固定、不收缩，放不下就换行', () => {
    expect(declared('.scell-btns', 'flex-wrap')).toEqual(['wrap'])
    expect(declared('.mini-btn', 'flex')).toEqual(['0 0 auto'])
    expect(declared('.mini-btn', 'width')).toEqual(['27px'])
    expect(declared('.mini-btn', 'padding')).toEqual(['0'])
  })

  it('列表视图：书名在可以收缩的区域里，行尾的操作区不收缩、会换行', () => {
    expect(declared('.srow-main', 'min-width')).toEqual(['0'])
    expect(declared('.srow-acts', 'flex')).toEqual(['0 0 auto'])
    expect(declared('.srow-acts', 'flex-wrap')).toEqual(['wrap'])
  })

  it('对照：匹配器确实读到了样式表', () => {
    // 封面网格：桌面五列，窄屏四列（媒体查询里那条也算一条规则）
    expect(declared('.shelf-grid', 'grid-template-columns')).toEqual(['repeat(5, minmax(0, 1fr))', 'repeat(4, minmax(0, 1fr))'])
    // 同一个选择器、同一个属性只有一条声明时只有一个值
    expect(declared('.scell', 'display')).toHaveLength(1)
  })
})
