# 协作指令：收尾项（监督面板 → Claude 会话）

> 2026-08-12 ｜ 来自监督面板（Reasonix）
> 你正在处理的权限链路 + 诊断清理方向正确，以下是最优解路径，按序执行。

## ✅ 已确认做对的（保持）
- `UserDetailsServiceImpl`：ORG_AND_CHILDREN 保留 + `@SuppressWarnings("deprecation")` + 语义注释（与 DEPT 不等价，不可替换）——**正是最优解，禁止机械替换**
- `.settings` 规范化 + `nullUncheckedConversion=Ignore` 保留
- pom.xml Spring Boot 3.2.5 → 3.2.12（3.2.x 最终安全版）

## 📋 剩余任务（做完即收尾）

1. **剩余 2 条诊断**（VSCode 面板，JDT 重建后可见）：
   - `IncentiveSpecificProductTest.java:84` — `Class` raw type（`ArgumentCaptor.forClass` 链的 `Class<T>` 字面量），加 `@SuppressWarnings` 或显式类型
   - `CustomerService.java:781` — 未使用方法 `getUserIdsByDeptIds(List<Long>)`，grep 确认无调用后删除
2. **全量验证**：`mvn -q test` 全量测试必须全绿（升级 3.2.12 后确认无回归），编译 + 测试一起跑
3. **提交**：按主题分组提交（settings/pom 升级 / 权限链路 / 诊断清理），提交信息写清"零行为变更"或行为变更点
4. **不需要你做的**：桌面发布线（AIGICRM-v3 8-11 V10/V11）合入由监督面板在**你的会话结束后**自动执行，勿动 `99-归档/` 内容

## 边界（禁止越界）
- 不改 `sql/` 手写迁移体系（后续统一走 flyway V12+，由合入后基线决定）
- 不删除 `CustomerService.java:90` 的 `else if (false)` 分支（设计历史，保留）
- 数据库、生产环境零操作

## 完成后
- 提交全部改动 → 会话可结束（监督面板会自动接手合入）
- 如需继续，优先处理：`docs/生产上线清单-20260811.md` 里记录的观察项（V10 上线后 48 小时数据可见性观察）
