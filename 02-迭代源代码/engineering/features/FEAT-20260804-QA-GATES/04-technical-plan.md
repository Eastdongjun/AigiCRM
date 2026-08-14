# 技术实施计划

## 影响范围
- 新增 scripts/check-frontend-quality.sh、scripts/check-prod-perf.sh
- 修改 scripts/pre-delivery-check.sh(接入)
- 无前端/后端/DB 变更

## 实现
1. check-frontend-quality.sh:
   - grep size: 9999 / size=9999(排除 loadFilterOptions 已改)
   - createObjectURL 出现处所在文件需含 revokeObjectURL(项目级检查)
   - onUploadProgress 块需含 Date.now() 或节流逻辑(宽松:检查 uploadProgress.value 赋值处上下文含间隔判断)
   - nginx-ssl.conf:每个含 add_header 的 location 上下文需含 X-Frame-Options;文件需含 gzip on
2. check-prod-perf.sh <base>:curl 验证 gzip(Content-Encoding)、4 安全头、no-cache、首页耗时<2s
3. pre-delivery-check.sh 末尾调用

## 测试策略
- AC-001:当前代码运行 0 违规
- AC-002:临时注入 size:9999 到临时文件,脚本检出非 0
- AC-003:生产实测 PASS

## 回滚
- 脚本删除即可;pre-delivery-check 恢复备份

## 风险
- 扫描规则保守,可能存在漏网(后续按新问题补充规则)

## 已确认影响范围
### 影响范围清单
| 层 | 涉及项 | 核对结果 |
|---|---|---|
| 脚本 | check-frontend-quality.sh / check-prod-perf.sh(新增)、pre-delivery-check.sh | 本地 |
| 前端 | config.vue / incentive/index.vue(size:9999→500) | 已部署 |

### 隐藏入口检查
- [x] 定时任务/自动流程:pre-delivery-check 交付时自动执行

## 架构与关键决策
- 反模式扫描(零依赖)优于 eslint(现有代码零配置会大量告警)
- gzip 检测用静态 JS(API 小响应不触发 gzip_min_length)

## 数据、API 与事件契约
- 脚本退出码:0 通过/1 违规;输出格式 "✅/❌" 每项一行

## 权限与安全边界
- 本地扫描;生产检查只读

## 实施顺序
1. 两个脚本 → 2. pre-delivery-check 接入 → 3. 修复门禁发现的 size:9999 → 4. 验证 AC

## 测试策略
- AC-001 当前代码 0 违规;AC-002 注入反模式检出;AC-003 生产全 PASS

## 迁移、发布与兼容
- 脚本本地生效;前端已部署

## 可观测性
- pre-delivery-check 输出含 [5] 前端质量检查结果

## 回滚
- 删除脚本/pre-delivery-check 还原;前端旧 dist 在 NAS

## 风险与盲区
- 扫描规则保守可能漏网;浏览器 E2E 未纳入
