import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'

// 【T3 2026-11-12】夹行按钮视图(FOLDER 授权)回归钉:
// 勾"文件夹"列 改名/删除 后夹按钮必须亮——消费 folderActions 分字段细词,
// 不得回退用文件动作词 perms('edit') 判夹按钮(词义混叠)。
const src = readFileSync('src/views/file/FolderPanel.vue', 'utf-8')

describe('夹行按钮视图(T3: FOLDER 授权)', () => {
  it('夹改名/删除按钮消费 folderActions 细词(rename/delete)', () => {
    expect(src).toContain("folder.folderActions?.includes('rename')")
    expect(src).toContain("folder.folderActions?.includes('delete')")
  })

  it('外层显隐同看 folderActions(至少一个动作才渲染操作区)', () => {
    expect(src).toContain("folder.folderActions?.includes('rename') || folder.folderActions?.includes('delete')")
  })

  it('不再用文件动作词 perms(edit/delete) 判夹按钮——防词义混叠回退', () => {
    expect(src).not.toContain("folder.perms?.includes('edit')")
    expect(src).not.toContain('folder.perms?.includes("edit")')
  })

  it('全局码双门保留: canManageFolders(文件全局功能码 file:folder:manage)与行级并合', () => {
    expect(src).toContain('canManageFolders')
  })
})
