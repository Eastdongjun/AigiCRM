# AIGICRM 问题跟踪清单（workbuddy）

- **建立日期**：2026-10-16
- **来源**：《全维度审查报告-workbuddy-20261016.md》（108 项检查，通过 80 / 存疑 18 / 不符 0）
- **跟踪规则**：每条问题状态变更必须附证据；修复后回写"复核结论"列；P0 未关闭前不得宣称交付完成。

---

## P0（需立即处理）

| ID | 问题 | 状态 | 证据/下一步 | 复核结论 |
|---|---|---|---|---|
| **P0-1** | F14/23 延期审批 extend 分支归属校验：代码已修（8c2dc039，10-16 21:28）但测试环境 jar（10-13 22:36）与生产（a8dde617）均未部署 | 🔴 待部署 | 复现：NM002 对无权限客户 131692 发起 extend → 200 生成 P2026-053（已驳回）。下一步：部署 8c2dc039 到测试环境 → 回归 F14/23 → 排查存量 reviewing extend 审批单 → 上生产 | — |

## P1（需排期）

| ID | 问题 | 状态 | 证据/下一步 | 复核结论 |
|---|---|---|---|---|
| **P1-1** | M2：`/api/incentive/customer-records` 忽略 period 参数（前端 useIncentiveStats.js:135 传了，后端 IncentiveController.java:173-186 不收） | 🔴 待修复 | 无 period 与 period=2026H1 响应 md5 完全一致（fcc9fa9d…）。下一步：后端按 biz_incentive_record.period 过滤，与 category-stats 同口径 | — |
| **P1-2** | F28：测试环境 uploads 卷历史文件大面积丢失（1045 记录 vs 11 物理文件，老文件全 404） | 🔴 环境待治理 | 4 天未恢复、无根因结论（非代码缺陷）。下一步：定位卷丢失根因、确认可恢复备份；恢复前 UI 给"文件缺失"提示 | — |
| **P1-3** | 前端分享按钮对 admin 恒 403 仍展示（detail.vue:161-162 vs CustomerService.java:1810-1812） | 🔴 待修复 | admin 无有效集团归属时后端恒抛 403。下一步：UI 对 admin 隐藏入口或给说明文案 | — |
| **P1-4** | 激励上传 trial 品类 0 文件可提交（useIncentiveUpload.js:151-155 vs IncentiveService.java:664-666） | 🔴 待修复 | canSubmit 对 trial 只校验 selectedCustomerId。下一步：trial 分支同样要求至少一个文件 | — |

## P2（建议改进）

| ID | 问题 | 状态 | 证据/下一步 | 复核结论 |
|---|---|---|---|---|
| P2-1 | 文档落后源码：8c2dc039 未同步 docs（doc-freshness FAIL） | 🔴 待补档 | gate.sh [4]。下一步：补功能文档/需求分篇对应改动 | — |
| P2-2 | SysUserService.deleteUser 副作用先于校验（SysUserService.java:415，事务兜底） | 🔴 待修复 | purgeByUser 在存在性/权限校验之前 | — |
| P2-3 | CustomerService 空 catch（CustomerService.java:1477-1478，审计日志静默丢失） | 🔴 待修复 | 财务审计 insert 失败无日志 | — |
| P2-4 | SeaService 命名误导（SeaService.java:58 visiblePoolIdsOrNull 死分支） | 🔴 待修复 | 重命名消除误导 | — |
| P2-5 | 迁移遗留风险×3：硬编码 id 9001+ 潜在冲突（V2026.09.11.6）、uk_sea_pool_active 未预检重复数据（V2026.09.22.1）、中文 INSERT 无文件级 SET NAMES 兜底 | 🔴 待评估 | 当前数据量下概率极低 | — |
| P2-6 | 前端：scoreOrder 死参数、BasicSettings.vue:101 裸读 localStorage token、激励列表加载失败无错误态 | 🔴 待修复 | — | — |
| P2-7 | 小程序：empty-icon 等约 27 个静态 class 无定义（L17）、7 页缺 .wxss（L16）、M3 瓦片高度 10rpx 错齐、未锁 libVersion | 🔴 待修复 | — | — |
| P2-8 | 客户删除后公海池记录不自动清理（131693 软删但 sea_pool 183 残留 available） | 🔴 数据卫生待确认 | 是否设计行为待裁决 | — |
| P2-9 | 未跟踪文件"全方面体检报告-workbuddy-20261016.md"（并行会话产物）待收口 | 🔴 待处理 | 按纪律未清理 | — |

## 已验证关闭（本轮已核状态，供回归对照）

| ID | 问题 | 状态 | 证据 |
|---|---|---|---|
| F11 | 关闭共享反扩可见性 | ✅ 已修 | 分享→可见→关闭→400 收回 |
| F06 | dataScope 三处口径矛盾 | ✅ 已修 | NO001 列表 total=285=admin 全量 |
| F03 | period 乱码行 | ✅ 已清理 | id=266 period HEX=32303236E585A8E5B9B4，全表单值 |
| H2 | 小程序照片墙索引遮蔽 | ✅ 已修全仓 | 嵌套 wx:for 0 残留 |
| H1 | auto_share 字符串 ID | ✅ 触发条件不成立 | 当前配置无字符串 ID，矩阵 21/21 绿 |

---

*下次状态更新时，逐条回写"复核结论"并附证据；P0-1 关闭 = 8c2dc039 部署 + F14/23 回归通过 + 存量审批单排查完成。*
