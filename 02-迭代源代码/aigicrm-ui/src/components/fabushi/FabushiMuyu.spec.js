import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { mount } from '@vue/test-utils'
import FabushiMuyu from './FabushiMuyu.vue'

// 【FABUSHI-DELTA-20261128 S2】木鱼素材一致性契约（取代旧的 wxss 逐项对账——美术已从 CSS 拼装改为 SVG 源）。
// 唯一美术源 = 本目录 muyu-art.svg：
//   ① PC 端 FabushiMuyu.vue 内联的 SVG 必须与 muyu-art.svg 逐字符一致（谁改了图没同步另一份就红）；
//   ② 小程序发布成功页 muyu-stage 必须引用 assets/img/muyu.png + muyu-mallet.png（同一源导出的 3x 透明 PNG），
//      并保留光晕/涟漪/+1 舞台件；
//   ③ PNG 实际尺寸必须等于画布尺寸×3（muyu.png 552×378 = 184×126@3x；muyu-mallet.png 138×228 = 46×76@3x），
//      小程序 wxss 的显示尺寸必须等于画布尺寸×2rpx（184×126px → 368×252rpx；46×76px → 92×152rpx）；
//   ④ 音效链路不动：小程序 muyu.mp3 播放逻辑与 PC muyuSfx 仍在（换图不换声）。
//   ⑤ 行为：敲击挂 go 类、+1 只在新增点赞时飞起、缩放写在 --muyu-s 一个变量上。
// 抓不到：PNG 内容是否真的由当前 SVG 导出（由导出流程保证；改了 muyu-art.svg 记得重导 PNG）。
// vitest 的 cwd 是 aigicrm-ui 根目录；跨仓读小程序文件沿用旧对账的相对路径做法。

const MP_DIR = '../aigicrm-miniprogram/pages/fabushi/upload'
const MP_IMG = '../aigicrm-miniprogram/assets/img'
const mpWxml = readFileSync(`${MP_DIR}/index.wxml`, 'utf-8')
const mpWxss = readFileSync(`${MP_DIR}/index.wxss`, 'utf-8')
const mpJs = readFileSync(`${MP_DIR}/index.js`, 'utf-8')
const pcDetailVue = readFileSync('src/views/fabushi/detail.vue', 'utf-8')
const artSvg = readFileSync('src/components/fabushi/muyu-art.svg', 'utf-8')
const pcVue = readFileSync('src/components/fabushi/FabushiMuyu.vue', 'utf-8')

/** PNG 的 IHDR 宽高（字节 16-23，大端） */
const pngSize = (buf) => ({ width: buf.readUInt32BE(16), height: buf.readUInt32BE(20) })

/** 取 SVG 的内层内容（根标签内、剔除注释与空白差异）——根标签属性允许不同，内层必须逐字符一致 */
const svgInner = (text) => {
  const matched = text.match(/<svg[^>]*>([\s\S]*?)<\/svg>/)
  if (!matched) return ''
  return matched[1].replace(/<!--[\s\S]*?-->/g, '').replace(/\s+/g, ' ').trim()
}
const inlineSvgInner = () => {
  const matched = pcVue.match(/<svg class="muyu-svg"[\s\S]*?<\/svg>/)
  return matched ? svgInner(matched[0]) : ''
}

describe('木鱼素材一致性契约(S2)：muyu-art.svg 单一源', () => {
  it('① PC 组件内联 SVG 与 muyu-art.svg 内层逐字符一致，画布同为 34 42 184 126', () => {
    expect(artSvg).toContain('viewBox="34 42 184 126"')
    expect(pcVue).toContain('viewBox="34 42 184 126"')
    expect(inlineSvgInner()).toBe(svgInner(artSvg))
  })

  it('② 小程序发布页引用同一源导出的 PNG，并保留光晕/涟漪/+1', () => {
    expect(mpWxml).toContain('src="/assets/img/muyu.png"')
    expect(mpWxml).toContain('src="/assets/img/muyu-mallet.png"')
    for (const part of ['muyu-glow', 'muyu-ripple', 'muyu-plus', 'muyu-img-fish', 'muyu-img-mallet']) {
      expect(mpWxml).toContain(part)
    }
    // 旧 CSS 拼装件不能再出现（防止两套美术并存）
    for (const gone of ['muyu-body', 'muyu-cushion']) {
      expect(mpWxml).not.toContain(`muyu-${gone}`)
    }
  })

  it('③ PNG 实际尺寸 = 画布×3；wxss 显示尺寸 = 画布×2rpx', () => {
    const fish = pngSize(readFileSync(`${MP_IMG}/muyu.png`))
    const mallet = pngSize(readFileSync(`${MP_IMG}/muyu-mallet.png`))
    expect(fish).toEqual({ width: 552, height: 378 })
    expect(mallet).toEqual({ width: 138, height: 228 })
    expect(mpWxss).toContain('.muyu-img-fish { left: -34rpx; top: 4rpx; width: 368rpx; height: 252rpx;')
    expect(mpWxss).toContain('.muyu-img-mallet { left: 250rpx; top: 92rpx; width: 92rpx; height: 152rpx;')
  })

  it('④ 换图不换声：两端木鱼音效链路仍在', () => {
    expect(mpJs).toContain('muyu.mp3')
    expect(mpJs).toContain('playMuyuSound')
    expect(pcDetailVue).toContain("new Audio('/audio/muyu.mp3')")
  })
})

describe('木鱼组件行为', () => {
  it('结构：鱼身/音槽闪金/木槌分组在；敲击挂 go；+1 只在新增点赞时飞起', async () => {
    const wrapper = mount(FabushiMuyu, { props: { scale: 0.5 } })
    expect(wrapper.find('svg.muyu-svg').exists()).toBe(true)
    expect(wrapper.find('.m-fish').exists()).toBe(true)
    expect(wrapper.find('.m-mallet').exists()).toBe(true)
    expect(wrapper.find('.m-slotflash').exists()).toBe(true)
    expect(wrapper.find('.muyu-plus').exists()).toBe(false)
    await wrapper.setProps({ hit: true, plusKey: 1 })
    expect(wrapper.find('.fmuyu').classes()).toContain('go')
    expect(wrapper.find('.muyu-plus').exists()).toBe(true)
    await wrapper.setProps({ hit: false, plusKey: 1 })
    expect(wrapper.find('.fmuyu').classes()).not.toContain('go')
    expect(wrapper.find('.muyu-plus').exists()).toBe(false)
    // 缩放写在唯一变量上
    expect(wrapper.find('.fmuyu').attributes('style')).toContain('--muyu-s: 0.5')
  })
})
