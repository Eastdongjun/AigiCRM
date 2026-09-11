# 需求规格

## 目标用户与场景
- 用户:系统管理员/销售员,在客户详情页查看某客户「四项激励」Tab
- 触发场景:点击「安装前/安装中/安装后」阶段文件,期望打开/下载对应文件
- 目标:点击后不再出现 400 错误页,文件正常打开

## 事实
- 数据库 file_pre_id / file_mid_id / file_post_id / file_id 存 JSON 数组字符串:单文件 '[152]'、多文件 '[149,165]'
- 2026-08-01 测试环境实测复现:点击阶段文件打开 `/api/file/download/[149,165]` 与 `/api/file/download/[152]`,均 HTTP 400
- 根因:customer/detail.vue:477 previewIncentiveFile() 直接拼接 getFiles() 产出的原始 id;getFiles()(约 488 行)未做 parseId
- 对照:incentive/search.vue、incentive/customer.vue 均有 parseId() 且 getFiles() 已应用,功能正常
- 测试环境:http://111.229.37.220(admin 会话),部署方式:前端构建 → docker cp → restart aigicrm-frontend

## 假设
- 多文件阶段点击后打开第一个文件(parseId 返回 a[0]),与激励查询页既有行为一致
- 仅前端修复,后端、数据库不变;本功能包不部署生产

## 阻断问题
无

## 功能需求
| 需求 ID | 描述 | 来源 | 优先级 |
|---|---|---|---|
| REQ-001 | 阶段文件 id 在拼入下载 URL 前解析为单个数字 | 用户反馈+实测复现 | Must |

## 业务规则与权限
- 文件下载沿用现有鉴权:URL 带 token 参数(localStorage token)
- 阶段文件 id 来源:file_pre_id / file_mid_id / file_post_id / file_id 字段

## 异常与边界
- 单文件 id 存数字或 '[N]' 形式 → parseId 均返回 N
- 多文件 '[N,M]' → 取第一个 N
- id 为空/格式异常 → parseId 原样返回,保持既有兜底

## 非功能需求
- 与 search.vue / customer.vue 的 parseId 实现完全一致,不做新设计
- 修复范围最小:仅 detail.vue 一个文件

## 验收标准
|验收 ID | 对应需求 | Given / When / Then |
|---|---|---|
| AC-001 | REQ-001 | 详情页四项激励点击单文件阶段('152')→ 打开 URL /api/file/download/152 → HTTP 200 非 400 |
| AC-002 | REQ-001 | 详情页四项激励点击多文件阶段('[149,165]')→ 打开 URL /api/file/download/149 → HTTP 200 非 400 |
| AC-003 | REQ-001 | 点击行为后客户详情页无新增控制台错误 |
| AC-004 | REQ-001 | 激励查询页(含 parseId)点击文件行为保持正常(回归) |

## 追踪关系
- REQ-001 → AC-001、AC-002、AC-003、AC-004
