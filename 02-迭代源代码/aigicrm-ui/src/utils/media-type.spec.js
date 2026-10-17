import { describe, it, expect } from 'vitest'
import { previewKindOf, unsupportedHintOf, mediaTypeOf } from './media-type'

// 文件阅读器(2026-10-16)的单一判定源：扩展名 → 渲染器。表驱动全覆盖，新增格式先改这里再改渲染器。
describe('previewKindOf', () => {
  const cases = [
    ['a.jpg', 'image'], ['a.JPEG', 'image'], ['a.png', 'image'], ['a.gif', 'image'], ['a.webp', 'image'], ['a.bmp', 'image'],
    ['v.mp4', 'video'], ['v.webm', 'video'], ['v.MOV', 'video'],
    ['s.mp3', 'audio'], ['s.wav', 'audio'], ['s.m4a', 'audio'],
    ['d.pdf', 'pdf'], ['d.PDF', 'pdf'],
    ['w.docx', 'docx'],
    ['x.xlsx', 'sheet'], ['x.xls', 'sheet'], ['x.csv', 'sheet'],
    ['t.txt', 'text'], ['t.md', 'text'], ['t.log', 'text'], ['t.json', 'text'], ['t.xml', 'text'],
  ]
  it.each(cases)('%s → %s', (name, kind) => {
    expect(previewKindOf(name)).toBe(kind)
  })

  it.each([
    ['p.pptx'], ['p.ppt'], ['w.doc'], ['i.heic'], ['v.avi'], ['v.mkv'], ['z.zip'], ['z.rar'], ['z.7z'],
    ['noext'], [''], [null], [undefined],
  ])('%s → unsupported', (name) => {
    expect(previewKindOf(name)).toBe('unsupported')
  })

  it('只看结尾扩展名，不被路径或中间点号误导', () => {
    expect(previewKindOf('报告.v2.final.docx')).toBe('docx')
    expect(previewKindOf('/some/dir/x.pdf')).toBe('pdf')
    expect(previewKindOf('x.pdf.exe')).toBe('unsupported')
  })

  it('与 mediaTypeOf 的图片/视频集合保持一致(双端瓦片判定与预览判定不分叉)', () => {
    for (const n of ['a.jpg', 'a.jpeg', 'a.png', 'a.gif', 'a.webp', 'a.bmp']) expect(mediaTypeOf(n)).toBe('image')
    for (const n of ['v.mp4', 'v.webm', 'v.mov']) expect(mediaTypeOf(n)).toBe('video')
    // avi 在瓦片判定里算视频(有视频图标)，但浏览器放不了，预览侧明确归 unsupported 并给出原因
    expect(mediaTypeOf('v.avi')).toBe('video')
    expect(previewKindOf('v.avi')).toBe('unsupported')
    expect(unsupportedHintOf('v.avi')).toMatch(/播放器/)
  })
})

describe('unsupportedHintOf', () => {
  it('按格式给出具体原因', () => {
    expect(unsupportedHintOf('a.pptx')).toMatch(/演示文稿/)
    expect(unsupportedHintOf('a.doc')).toMatch(/\.doc/)
    expect(unsupportedHintOf('a.heic')).toMatch(/HEIC/)
    expect(unsupportedHintOf('a.zip')).toMatch(/压缩包/)
  })
  it('未知格式给通用兜底', () => {
    expect(unsupportedHintOf('a.xyz')).toMatch(/暂不支持/)
    expect(unsupportedHintOf('')).toMatch(/暂不支持/)
  })
})
