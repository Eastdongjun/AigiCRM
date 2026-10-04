// 跨页共用的部门/组织树工具。两类口径(行为均与各页原实现逐字一致, 勿"统一"改动):
// 1) flattenDepts: 递归压平"部门树"为 picker 选项(approval/reminder 筛选共用, 全角空格缩进);
// 2) buildDeptLevelOptions: "组织列表"按 parent 挂靠分级缩进(incentive applyOrgs 与
//    sea/statistics applyDepts 三处共用, 半角双空格缩进; deptType===1 的公司节点过滤;
//    父节点不在列表内的孤儿节点归到根; 同级按 sortOrder 升序)。

function flattenDepts(nodes = [], depth = 0) {
  const rows = []
  for (const node of Array.isArray(nodes) ? nodes : []) {
    rows.push({ id: node.id, name: `${'　'.repeat(depth)}${node.deptName || node.name || '未命名部门'}` })
    rows.push(...flattenDepts(node.children || [], depth + 1))
  }
  return rows
}

// 组织/部门筛选选项: 根节点"全部部门(rootLabel)" + 逐级缩进 label
function buildDeptLevelOptions(list, options = {}) {
  const rootLabel = options.rootLabel || '全部部门'
  const fallbackName = options.fallbackName || '未命名部门'
  const source = list || []
  const filtered = source.filter((item) => item && item.deptType !== 1)
  const byParent = {}
  filtered.forEach((item) => {
    const parentId = filtered.some((node) => String(node.id) === String(item.parentId)) ? item.parentId : 0
    const key = String(parentId || 0)
    if (!byParent[key]) byParent[key] = []
    byParent[key].push(item)
  })
  const result = [{ id: '', deptName: rootLabel, label: rootLabel }]
  const walk = (parentId, level) => {
    ;(byParent[String(parentId || 0)] || [])
      .sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0))
      .forEach((item) => {
        result.push({ ...item, label: `${'  '.repeat(level)}${item.deptName || item.name || fallbackName}` })
        walk(item.id, level + 1)
      })
  }
  walk(0, 0)
  return result
}

module.exports = {
  buildDeptLevelOptions,
  flattenDepts
}
