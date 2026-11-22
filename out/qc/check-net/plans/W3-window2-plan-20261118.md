# W3 第二窗计划（C5 故障注入 + DEF-EXTRADATA-001 运行时复验）

> QC `01-claude-1c` ｜ 排期待用户醒后由 0b 定 ｜ 前置：W3 首窗 PASS（C1/C2/C3）
> 护栏同首窗：仅测试服；新库执行；基线 A 不碰；结束还原清缓存复核 pin。

## 一、本窗可执行项

| 项 | 内容 | 脚本 | 备注 |
|---|---|---|---|
| **C5 故障注入** | 停测试服 `shared-mysql` → `auth-probe` 断言 500 带支持编号且文案明确 → 恢复 → 复核 200 | `/tmp/qc-w4-c5.sh`（已备，测试服容器，恢复兜底） | 需 0b 明确授权停 DB（比停 Redis 影响大） |
| **DEF-EXTRADATA-001 运行时复验** | `extraData:""` 提交 → 断言 200（规范化 `{}`）或 400，**不再 500** | 探针加 `--empty` 模式（见下） | 复用首窗编排 |
| **C3 回归** | 断 Redis 快速复测（已有脚本） | 首窗脚本 | 可选 |

## 二、运行时不可行项（判定说明，非缺陷）

| 项 | 结论 | 理由 |
|---|---|---|
| **C4 时间边界** | 静态 PASS / 运行时 **BLOCKED** | 需"时间旅行"或造数后等待跨天（23:59→00:01），单窗不可行；静态已核（时区全链 Asia/Shanghai、cron 无显式 zone=P3、跨天逻辑读 `LocalDateTime.now()`）。替代：单元级时钟注入（仅文件域有 LongSupplier）。 |
| **C10 三端差分** | 静态 PASS / 运行时 **BLOCKED** | 需真实浏览器/微信 DevTools/真机（用户侧资源）；静态对照已完成（页面/端点/权限码无孤儿，2 处待口径：MP 无积分页、H5 两处 JSSDK）。 |

## 二之二、完整编排脚本（已备）

- `/tmp/qc-w3b-orchestrator.sh`：前置 pin → enter 新库 → **DEF-EXTRADATA 空串复验**（`empty-extradata`）→ **C5 停 DB 注入**（`/tmp/qc-w4-c5.sh`）→ restore 基线 A → 清缓存 → 后 pin → **自动 diff pin**。
- 护栏内置：任一 enter/restore 失败即停；pin diff 输出 `PIN_NO_DRIFT` 或 `PIN_DRIFT_SEE_DIFF`。
- 执行前提：0b/用户明确授权 C5 停 DB。

## 三、DEF-EXTRADATA-001 复验探针（补充）

探针加模式：
```python
elif mode == "empty-extradata":
    # 传空串，断言非 500
    code, body = call("/api/approval/submit", T[1][1], "POST",
                      {"type": "claim", "customerId": int(sys.argv[2]), "actionAfterApprove": "", "extraData": ""})
    print(f"EMPTY_EXTRADATA status={code} body={body[:120]!r} expect=200-or-400")
```

## 四、验收清单

1. C5：DB 停机期间 `auth-probe` → 500（带 `S-` 支持编号）且恢复后 200；无脏数据；
2. DEF-EXTRADATA：空串 → 200/400，不再 500；
3. pin 前后一致、缓存清理、写残留仅新库；
4. 报告落 `out/阶段B-检查网-第3波-20261118-01claude.md` 增补节。

## 五、等待项

- 用户醒后：0b 排期本窗（C5 停 DB 需更明确授权）；
- 用户拍板：MP P1 四张、B3-B13、DEF-TRANSFER-001；
- 生产窗口：C7-NGINX-001 + MP-TOKEN-001。
