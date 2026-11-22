# 阶段 B 批次落地 runbook（协调方 01-claude-0b）

> 前置：QC 通报 W2b 完成、基线 A 已还原（jar 2ea180c6 / DB aigicrm_prod_20261016 /
> health 200 / Redis perm:ctx=0 login_fail=0）。主工作区在此之前保持冻结。

## B-java2（11 文件 / 14 函数）

源：`/tmp/aigicrm-next/02-迭代源代码/...`（FileService 已基于 c0a50b13，含 upload+thumbnail 两处拆分）
目标：主工作区 `/Users/dongjun/东来/项目/02-已上线/01-AIGICRM`

```bash
cd /Users/dongjun/东来/项目/02-已上线/01-AIGICRM
BASE=02-迭代源代码/aigicrm-backend/src/main/java/com/aigicrm
SRC=/tmp/aigicrm-next/02-迭代源代码/aigicrm-backend/src/main/java/com/aigicrm
for f in \
  module/file/service/FileGrantService.java \
  module/file/service/FileService.java \
  module/file/service/FolderPermissionService.java \
  module/system/service/SysUserService.java \
  module/system/service/SysConfigService.java \
  module/system/service/SysPositionService.java \
  module/approval/service/ApprovalService.java \
  module/customer/service/CustomerService.java \
  module/customer/controller/CustomerController.java \
  module/incentive/service/IncentiveService.java \
  common/util/ClientIpUtils.java; do
  cp "$SRC/$f" "$BASE/$f"
done
cd 02-迭代源代码/aigicrm-backend && mvn -q test
cd /Users/dongjun/东来/项目/02-已上线/01-AIGICRM/02-迭代源代码
bash scripts/dev-verify.sh contracts
cd /Users/dongjun/东来/项目/02-已上线/01-AIGICRM
git add <上述 11 个文件>
git commit -m "fix(gov): P0-5 B-java2——FileGrant/SysUser/Approval/Customer/Incentive/FileService 等 S3776 拆分"
git rev-parse --short HEAD
```

断言：后端 1194/0；契约 0 FAIL；提交仅含 11 个 Java 文件。
提交后消息 QC：新 HEAD + 预期 java 72→58、critical 107→93，请扫并回 SCAN_DONE。

## B-java4（3 文件 / 4 函数，java2 扫后）

源：`/tmp/aigicrm-next-j4/...`（detached c0a50b13；与 java2 无文件重叠）
文件：FileChunkUploadService、SeaConfigService、SysRoleService。
断言：后端 1194/0；契约 0 FAIL；commit 后预期 java 58→54、critical 93→89。

## B-java5（3 文件 / 3 函数，java4 扫后）

源：`/tmp/aigicrm-next-j5/...`（detached c0a50b13）
文件：PermissionMetadataService、FabushiDashboardService、ScoreService。
断言：后端 1194/0（ScoreService 架构测试要求 `result.put("scores", scores)` 字面量，已保留）；
commit 后预期 java 54→51、critical 89→86。

## B-py（12 文件 / 15 函数）

源：`/tmp/aigicrm-next-py/02-迭代源代码/scripts/...`（detached c0a50b13）
在 Java 复扫 SCAN_DONE 后执行：

```bash
cd /Users/dongjun/东来/项目/02-已上线/01-AIGICRM
SRC=/tmp/aigicrm-next-py/02-迭代源代码/scripts
DST=02-迭代源代码/scripts
for f in \
  check-action-gate-coverage.py check-mobile-navigation-contract.py \
  check-permission-registration-contract.py check_permission_strings.py \
  fabushi/permission_matrix_audit.py fabushi/permission_matrix_full.py \
  fabushi/permission_matrix_v2.py fix-file-perm-dedup.py gen-api.py gen-dict.py \
  gen-perm.py gen-unified-feature-audit.py; do
  cp "$SRC/$f" "$DST/$f"
done
cd 02-迭代源代码 && bash scripts/dev-verify.sh contracts
```

断言：契约 0 FAIL；`check-permission-codes-generated.sh` PASS；然后 commit（仅 12 个 py 文件），
消息 QC 复扫。预期 python 21→6、整体 critical 93→78。

## 剩余

- Python 6：4 个 permission_matrix_* main、permission_role_smoke main、gen-unified render。
- JS 14：scripts 6 + 产品 8（router/useCustomerForm×2/useCustomerList/useIncentiveUpload/
  PermScopeManager/FileSpaceDefaults/SeaConfigManager）。
- 每批仍按：commit → 冻结 → QC 扫 → SCAN_DONE → 下一批。
