# 测试矩阵

## 验收追踪
| AC | 测试 ID | 结果 |
|---|---|---|
| AC-001 | TEST-001 | PASS |
| AC-002 | TEST-002 | PASS |
| AC-003 | TEST-003 | PASS |
| AC-004 | TEST-004 | PASS |

## 静态与构建验证
- npm run build ✓ 363ms,产物 detail-C4-iKj8e.js 含 parseId(grep 验证)

## 功能与集成验证
- TEST-001:详情页四项激励点击单文件阶段 → /api/file/download/151 → HTTP 200 video/mp4 ✓ PASS(AC-001)
- TEST-002:点击多文件阶段('[149,165]')→ /api/file/download/149 → HTTP 200 ✓ PASS(AC-002)
- TEST-003:修复后详情页控制台 0 errors(仅既有 ElementPlus warning)✓ PASS(AC-003)

## UI 验证
- 基线/最终截图 1440x900、1280x720 已保存;视觉评分 88;视觉零变更(1 行逻辑 diff)

## 回归验证
- TEST-004:激励查询/激励上传/客户列表 3 页正常 ✓ PASS(AC-004)
- npm test 8/8 ✓

## 未覆盖与风险
- 生产未部署;多文件仅取第一个;未新增本功能单测
