# DEF-1-TY005 — office 范围被 auto_share 收窄（P0 业务缺陷）

- 来源：§8 Batch 0（新库 `aigicrm_prod_20261118`，2026-11-18）
- 对象：uid 41 `TY005`，USER/office 显式授权
- 证据：配置应见 **121**；`apply_auto_share` 后最终应见 **17**；**少 104**（missing>0）
- 判定：**P0 业务缺陷**（§8.3：missing>0 = 阻断级业务缺陷）
- 用户裁决（2026-11-18）：**office = 全办事处；auto_share 不应再收窄已勾选范围**
- 修复方向：`resolveScope/applyAutoShare` 对"显式 USER/ROLE office 档"不应用 superior 收窄；与 DEF-1 旧库结论同源
- 验收：
  1. 新库/测试库上 uid 41 的 expected==actual==121（列表/counts/导出/选择器同集合）
  2. 其余 27 用户 0 变化（回归）
  3. 负例：未勾选用户仍 fail-closed
  4. QC 复跑 `scope_oracle.py` + API 实见，附证据
- 归属：实施方修复（协调方 0b 派单）；QC 复验
- 状态：OPEN（2026-11-18）
