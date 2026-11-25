import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { mount } from '@vue/test-utils'
import FabushiMuyu from './FabushiMuyu.vue'

// 【2026-11-25 用户裁决】“PC 端的木鱼需要做成和小程序的木鱼一样，注意尺寸就行”。
// 小程序的木鱼是发布成功页那只纯 CSS 画的（pages/fabushi/upload/index.wxml 的 muyu-stage）。
// 这个组件把它原样搬过来，只多一个缩放系数 scale（1 = 小程序原尺寸；750 设计稿 1rpx = 0.5px）。
// 这里钉住三件事：
//   ① 模板：部件与小程序同一个顺序——读小程序的 wxml 对账，不在这里另抄一份常量；
//   ② 样式：每一条尺寸、配色、动画都与小程序的 wxss 逐项相等——读小程序的 wxss 对账，rpx 换算成 px；
//   ③ 行为：敲击时挂动画类、+1 只在新增点赞时飞起；缩放系数写在一个 CSS 变量上，占位跟着它变。
// 以后小程序那边改了木鱼，这两条对账会红，提醒把 PC 这边同步过去。
// 抓不到：真实渲染出来的样子（要靠截图比对）。
// vitest 的 cwd 是 aigicrm-ui 根目录；jsdom 下 import.meta.url 不是 file 协议，所以用相对 cwd 的路径读文件。

const MP_DIR = '../aigicrm-miniprogram/pages/fabushi/upload'
const mpWxml = readFileSync(`${MP_DIR}/index.wxml`, 'utf-8')
const mpWxss = readFileSync(`${MP_DIR}/index.wxss`, 'utf-8')
const pcCss = readFileSync('src/components/fabushi/fabushi-muyu.css', 'utf-8')

/** 小程序模板里木鱼舞台下的部件类名，按出现顺序 */
const mpStageParts = () => {
  const lines = mpWxml.split('\n')
  const start = lines.findIndex((line) => line.includes('class="muyu-stage'))
  const parts = []
  for (const line of lines.slice(start + 1)) {
    if (/^\s*<\/view>\s*$/.test(line)) break // 舞台闭合
    const matched = line.match(/<view class="(muyu-[a-z]+)"/)
    if (matched) parts.push(matched[1])
  }
  return parts
}

/** 把“一行一条规则”的样式文本拆成 { 选择器: { 属性: 值 } }；小程序的 wxss 和 PC 的 css 都是这种写法 */
const parseRules = (text) => {
  const rules = {}
  let inMedia = false
  for (const line of text.split('\n')) {
    if (/^\s*@media/.test(line)) { inMedia = true; continue }
    if (inMedia) { if (/^\s*}\s*$/.test(line)) inMedia = false; continue }
    const matched = line.match(/^\s*([^{@}\s][^{]*?)\s*\{([^{}]*)\}\s*$/)
    if (!matched) continue
    const decls = {}
    for (const decl of matched[2].split(';')) {
      const colon = decl.indexOf(':')
      if (colon > 0) decls[decl.slice(0, colon).trim()] = decl.slice(colon + 1).trim().replace(/\s+/g, ' ')
    }
    rules[matched[1].trim()] = decls
  }
  return rules
}

/** 关键帧：{ 名称: { '0': { 属性: 值 }, … } } */
const parseKeyframes = (text) => {
  const frames = {}
  for (const line of text.split('\n')) {
    const head = line.match(/^\s*@keyframes\s+([\w-]+)\s*\{(.*)\}\s*$/)
    if (!head) continue
    const steps = {}
    for (const step of head[2].matchAll(/(\d+)%\s*\{([^{}]*)\}/g)) {
      const decls = {}
      for (const decl of step[2].split(';')) {
        const colon = decl.indexOf(':')
        if (colon > 0) decls[decl.slice(0, colon).trim()] = decl.slice(colon + 1).trim().replace(/\s+/g, ' ')
      }
      steps[step[1]] = decls
    }
    frames[head[1]] = steps
  }
  return frames
}

/** rpx → px：750 设计稿上 1rpx = 0.5px */
const rpxToPx = (value) => value.replace(/(-?\d+(?:\.\d+)?)rpx/g, (_, digits) => `${Number((Number(digits) / 2).toFixed(3))}px`)

/** 两边的根节点类名不同（小程序 .muyu-stage、PC .fmuyu），伪元素写法不同（:after / ::after）：都抹平后再比。
 *  根类名只在它后面还跟着别的（空格或 .go）时才剥掉，单独一条 .fmuyu / .muyu-stage 要留着。 */
const normalizeSelector = (selector) => selector
  .replace(/^\.muyu-stage(?=\s|\.)/, '')
  .replace(/^\.fmuyu(?=\s|\.)/, '')
  .trim()
  .replace(/(^|[^:]):(after|before)\b/g, '$1::$2')
  .trim()

const mpRules = parseRules(mpWxss)
const pcRules = parseRules(pcCss)
const pcByKey = {}
for (const [selector, decls] of Object.entries(pcRules)) pcByKey[normalizeSelector(selector)] = decls
const mpFrames = parseKeyframes(mpWxss)
const pcFrames = parseKeyframes(pcCss)

describe('FabushiMuyu：模板与小程序发布成功页那只木鱼同源', () => {
  it('先确认读得到小程序模板里的木鱼舞台（读不到的话下面的对账就是空转）', () => {
    const parts = mpStageParts()
    expect(parts.length, '小程序模板里木鱼舞台下的部件数').toBeGreaterThanOrEqual(5)
    expect(parts[0]).toBe('muyu-glow')
    expect(parts[parts.length - 1]).toBe('muyu-plus')
  })

  it('舞台下的部件与小程序同一个顺序（平时不显示 +1，所以是前 N-1 个）', () => {
    const wrapper = mount(FabushiMuyu)
    const rendered = [...wrapper.find('.muyu-stage').element.children].map((node) => node.className)
    const expected = mpStageParts().filter((name) => name !== 'muyu-plus')
    expect(rendered).toEqual(expected)
    // 舞台只是图：里面没有文字，也没有表情符号
    expect(wrapper.find('.muyu-stage').text()).toBe('')
    expect(wrapper.text()).not.toContain('🐟')
  })

  it('整个组件只有这一个根节点，是纯装饰（对读屏软件隐藏）', () => {
    const wrapper = mount(FabushiMuyu)
    expect(wrapper.element.className).toBe('fmuyu')
    expect(wrapper.element.tagName).toBe('SPAN')
    expect(wrapper.attributes('aria-hidden')).toBe('true')
  })
})

describe('FabushiMuyu：样式与小程序的 wxss 逐项相等（rpx 换算成 px）', () => {
  it('先确认读得到小程序的木鱼样式（读不到的话下面的对账就是空转）', () => {
    expect(Object.keys(mpRules).filter((key) => key.startsWith('.muyu-')).length).toBeGreaterThanOrEqual(8)
    expect(Object.keys(mpFrames)).toEqual(expect.arrayContaining(['muyu-hit', 'muyu-ripple', 'muyu-plus', 'muyu-glow']))
    expect(rpxToPx('5rpx -48rpx 170rpx 50%')).toBe('2.5px -24px 85px 50%')
    // 选择器归一化自带对照: 单冒号变双冒号、已是双冒号的不变、根类名只在后面还跟着别的时才剥
    expect(normalizeSelector('.muyu-body:after')).toBe('.muyu-body::after')
    expect(normalizeSelector('.fmuyu .muyu-body::after')).toBe('.muyu-body::after')
    expect(normalizeSelector('.muyu-stage.go .muyu-mallet')).toBe('.go .muyu-mallet')
    expect(normalizeSelector('.fmuyu.go .muyu-mallet')).toBe('.go .muyu-mallet')
    expect(normalizeSelector('.fmuyu')).toBe('.fmuyu')
  })

  it('小程序 .muyu-* 的每一条规则，PC 都有同名规则，且每个属性值都相等', () => {
    const problems = []
    for (const [selector, decls] of Object.entries(mpRules)) {
      if (!selector.startsWith('.muyu-')) continue
      const key = normalizeSelector(selector)
      if (selector === '.muyu-stage') continue // 舞台本身（position / margin）PC 另有摆法，见下一条用例
      const pcDecl = pcByKey[key]
      if (!pcDecl) { problems.push(`PC 没有 ${key} 这条规则`); continue }
      for (const [prop, value] of Object.entries(decls)) {
        const expected = rpxToPx(value)
        if (pcDecl[prop] !== expected) problems.push(`${key} { ${prop} }：小程序 ${value} → 应为 ${expected}，PC 是 ${pcDecl[prop]}`)
      }
    }
    expect(problems).toEqual([])
  })

  it('舞台还是小程序的 300×260rpx（= 150×130px）；敲击动画的每一段关键帧都相等', () => {
    const stage = pcByKey['.muyu-stage']
    expect(stage.width).toBe('150px')
    expect(stage.height).toBe('130px')
    const problems = []
    for (const [name, steps] of Object.entries(mpFrames)) {
      if (!name.startsWith('muyu-')) continue
      for (const [percent, decls] of Object.entries(steps)) {
        for (const [prop, value] of Object.entries(decls)) {
          const expected = rpxToPx(value)
          const actual = pcFrames[name] && pcFrames[name][percent] && pcFrames[name][percent][prop]
          if (actual !== expected) problems.push(`@keyframes ${name} ${percent}% { ${prop} }：小程序 ${value} → 应为 ${expected}，PC 是 ${actual}`)
        }
      }
    }
    expect(problems).toEqual([])
  })

  it('缩放只动一个 CSS 变量：占位 = 整只木鱼的包围盒 × 系数（小程序原尺寸下 x 27.5–171.1、y 10.9–119，含坐垫和探出去的木槌）', () => {
    const root = pcByKey['.fmuyu']
    expect(root['--muyu-s']).toBe('1')
    expect(root.width).toBe('calc(143.6px * var(--muyu-s))')
    expect(root.height).toBe('calc(108.1px * var(--muyu-s))')
    const stage = pcByKey['.muyu-stage']
    expect(stage.left).toBe('calc(-27.5px * var(--muyu-s))')
    expect(stage.top).toBe('calc(-10.9px * var(--muyu-s))')
    expect(stage.transform).toBe('scale(var(--muyu-s))')
    expect(stage['transform-origin']).toBe('0 0')
  })
})

describe('FabushiMuyu：行为', () => {
  const styleOf = (props) => {
    const wrapper = props ? mount(FabushiMuyu, { props }) : mount(FabushiMuyu)
    return wrapper.find('.fmuyu').attributes('style') || ''
  }

  it('缩放系数默认是 1（小程序原尺寸），传 0.5 就写成 0.5', () => {
    expect(styleOf()).toContain('--muyu-s: 1')
    expect(styleOf({ scale: 0.5 })).toContain('--muyu-s: 0.5')
  })

  it('敲击：hit 为真时挂上 go 类（与小程序触发动画的类同名）', async () => {
    const wrapper = mount(FabushiMuyu, { props: { hit: false } })
    expect(wrapper.find('.fmuyu').classes()).not.toContain('go')
    await wrapper.setProps({ hit: true })
    expect(wrapper.find('.fmuyu').classes()).toContain('go')
    await wrapper.setProps({ hit: false })
    expect(wrapper.find('.fmuyu').classes()).not.toContain('go')
  })

  it('+1 只在 plusKey 变化后出现，这一下敲完（hit 变回假）就收起；取消点赞的那一下不显示 +1', async () => {
    const wrapper = mount(FabushiMuyu, { props: { hit: false, plusKey: 0 } })
    expect(wrapper.find('.muyu-plus').exists()).toBe(false)
    await wrapper.setProps({ hit: true })
    expect(wrapper.find('.muyu-plus').exists()).toBe(false)
    await wrapper.setProps({ hit: false })

    await wrapper.setProps({ hit: true, plusKey: 1 })
    expect(wrapper.find('.muyu-plus').exists()).toBe(true)
    expect(wrapper.find('.muyu-plus').text()).toBe('+1')
    // +1 在舞台里的位置与小程序最后一个部件对应
    const parts = [...wrapper.find('.muyu-stage').element.children].map((node) => node.className)
    expect(parts).toEqual(mpStageParts())
    await wrapper.setProps({ hit: false })
    expect(wrapper.find('.muyu-plus').exists()).toBe(false)
  })
})
