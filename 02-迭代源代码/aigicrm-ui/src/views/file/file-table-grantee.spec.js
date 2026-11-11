import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'

// 【修复 2026-11-12 根因1/2/5】grantee(被授权对象)场景的文件按钮视图契约:
// "类型默认授权勾选了, 目标用户前端看不到"的直接回归钉——授权对象(非上传者/非夹主)
// 的按钮显隐必须能被后端细词(rename/replace)点亮, 不再只认历史伞词 edit。
const tableSrc = readFileSync('src/views/file/FileTable.vue', 'utf-8')
const panelSrc = readFileSync('src/views/file/FolderPanel.vue', 'utf-8')
const pageSrc = readFileSync('src/composables/useFilePage.js', 'utf-8')

describe('文件按钮视图 grantee 场景(根因1/2/5)', () => {
  it('改名门控双词兼容: 夹 perms 输出细词 rename 也能点亮(与 MP files:290 同构)', () => {
    expect(tableSrc).toContain("canFileAction(row, 'file:edit', 'edit', 'rename')")
  })

  it('替换门控双词兼容: 细词 replace 也能点亮', () => {
    expect(tableSrc).toContain("canFileAction(row, 'file:edit', 'edit', 'replace')")
  })

  it('canFileAction 消费端多词化(some 任一命中), 不再单词 includes', () => {
    expect(tableSrc).toContain('perms.some((p) => folderPerms.value.includes(p))')
  })

  it('夹行按钮消费 folderActions 分字段, 不再误用文件动作词 perms', () => {
    expect(panelSrc).toContain("folder.folderActions?.includes('rename')")
    expect(panelSrc).not.toContain("folder.perms?.includes('edit')")
  })

  it('公共根目录 perms 并入后端类型授权动作(不再纯硬编码挡住授权对象)', () => {
    expect(pageSrc).toContain("getTypePerms('public')")
    expect(pageSrc).toContain('[...base, ...granted]')
  })
})
