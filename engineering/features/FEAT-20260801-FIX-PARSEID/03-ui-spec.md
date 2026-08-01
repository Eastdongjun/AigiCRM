# UI 规格

## 设计系统
- 沿用既有 Element Plus 组件与 AigiCRM 设计 Token;本功能无样式/视觉变更

## 状态矩阵
| 阶段文件 | id 形态 | 点击行为 |
|---|---|---|
| 单文件 | '[152]' 或 152 | /api/file/download/152 |
| 多文件 | '[149,165]' | /api/file/download/149(取第一个,既有约定) |
| 无文件 | — | 不渲染文件块 |

## 交互规格
- 目标元素:客户详情页「四项激励」Tab 内 .inc-file 文件块
- 点击:window.open('/api/file/download/' + parseId(f.id) + '?token=...', '_blank')

## 响应式与可访问性
- 无布局变更;视口 1440x900 / 1280x720 均验证

## 视觉验证
- 修改前后视觉零变更(仅 1 行逻辑 diff)
- 基线:evidence/ui-baseline-1440x900.png
- 最终:evidence/ui-1440x900.png、evidence/ui-1280x720.png
- 视觉评分:88(console 0 errors,交互通过)
