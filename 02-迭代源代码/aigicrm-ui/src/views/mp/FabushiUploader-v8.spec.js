import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { readFileSync } from 'node:fs'

// 【2026-11-26 按小程序原型 v7】小程序里的“手机文件上传”页（views/mp/FabushiUploader.vue，web-view 里的 H5）。
// 原型 v7 里没有单独画这一屏：做法是跟着原型的配色走（白底、蓝色主色），把书卷时期米色 + 墨绿 + 朱砂的硬编码颜色去掉，
// 图标不用表情符号；功能不变（多文件队列、类型与大小预检、串行上传、重试、完成后回传附件）。

const hoisted = vi.hoisted(() => ({ route: { params: {}, query: {} } }))
vi.mock('vue-router', async (importOriginal) => ({ ...await importOriginal(), useRoute: () => hoisted.route }))

import MpUploader from './FabushiUploader.vue'

// <script setup> 组件默认不对外暴露内部绑定: 测试里按 any 读取
const vm = (wrapper) => /** @type {any} */ (wrapper.vm)
const IN_WECHAT = 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148 MicroMessenger/8.0.50'
const realUA = window.navigator.userAgent

beforeEach(() => {
  hoisted.route = { params: {}, query: {} }
  Object.defineProperty(window.navigator, 'userAgent', { value: IN_WECHAT, configurable: true })
})
afterEach(() => {
  Object.defineProperty(window.navigator, 'userAgent', { value: realUA, configurable: true })
  document.head.querySelectorAll('script[src^="https://res.wx.qq.com"]').forEach((node) => node.remove())
})

async function mountPage() {
  const warnings = []
  const wrapper = mount(MpUploader, {
    attachTo: document.body,
    global: { stubs: { 'el-icon': { template: '<i class="el-stub"><slot /></i>' } }, config: { warnHandler: (msg) => warnings.push(msg) } },
  })
  await flushPromises()
  expect(warnings, 'Vue 警告').toEqual([])
  return wrapper
}

describe('小程序“手机文件上传”页按原型 v7 的配色', () => {
  it('不在微信里打开：提示从小程序进入', async () => {
    Object.defineProperty(window.navigator, 'userAgent', { value: 'Mozilla/5.0 (Macintosh) Chrome/120', configurable: true })
    const wrapper = await mountPage()
    expect(wrapper.find('.mfbu-block').text()).toContain('请从微信小程序进入')
  })

  it('没有上传票：提示票缺失', async () => {
    const wrapper = await mountPage()
    expect(wrapper.find('.mfbu-block').text()).toContain('上传票缺失或已过期')
  })

  it('有票：标题、说明、选文件的入口；还没选文件时没有“完成”按钮', async () => {
    hoisted.route = { params: {}, query: { ticket: 'ticket-for-test' } }
    const wrapper = await mountPage()
    expect(wrapper.find('.mfbu-head .mfbu-title').text()).toBe('上传手机文件到法布施')
    expect(wrapper.find('.mfbu-pick').text()).toContain('点击选择手机文件')
    expect(wrapper.find('.mfbu-done').exists()).toBe(false)
  })

  it('文件队列：每个文件一行，上传中有进度条，失败的有重试，有成功的是“完成并返回”', async () => {
    hoisted.route = { params: {}, query: { ticket: 'ticket-for-test' } }
    const wrapper = await mountPage()
    vm(wrapper).items.push(
      { name: 'a.png', state: 'done', progress: 100, fileId: 1, cat: 'image', error: '' },
      { name: 'b.pdf', state: 'uploading', progress: 40, cat: 'pdf', error: '' },
      { name: 'c.exe', state: 'error', progress: 0, cat: '', error: '不支持的类型' },
    )
    await flushPromises()
    const rows = wrapper.findAll('.mfbu-item')
    expect(rows.map((r) => r.find('.mfbu-item-name').text())).toEqual(['a.png', 'b.pdf', 'c.exe'])
    expect(rows[0].text()).toContain('已上传')
    expect(rows[1].find('.mfbu-bar-in').attributes('style')).toContain('width: 40%')
    expect(rows[1].find('.mfbu-retry').exists()).toBe(false)
    expect(rows[2].text()).toContain('不支持的类型')
    expect(rows[2].find('.mfbu-retry').exists()).toBe(true)
    expect(wrapper.find('.mfbu-done').text()).toBe('完成并返回')
    expect(wrapper.find('.mfbu-done').attributes('disabled')).toBeUndefined()
    // 还有文件在传：不能点完成
    vm(wrapper).uploading = true
    await flushPromises()
    expect(wrapper.find('.mfbu-done').attributes('disabled')).toBeDefined()
  })

  it('图标不用表情符号；样式里没有书卷皮肤的颜色', () => {
    const src = readFileSync('src/views/mp/FabushiUploader.vue', 'utf-8')
    const template = src.slice(0, src.indexOf('<script'))
    expect(template).not.toMatch(/🐟|🎤|⚙|⏹|📖|📄|⚠|🔒/)
    const style = src.slice(src.indexOf('<style'))
    for (const skin of ['#f7f3ec', '#fffdf8', '#2e2a24', '#9c9184', '#e8e0d3', '#d8cdbb', '#2d5a4a', '#b5543a', '#fff7ee', '#efe7d8']) {
      expect(style.toLowerCase(), `书卷色 ${skin}`).not.toContain(skin)
    }
  })
})
