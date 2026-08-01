# 实现记录

## 变更文件

| 文件 | 对应 | 行为变化 |
|---|---|---|
| `ycmn-ui/src/views/incentive/index.vue` | REQ-001/AC-001~003 | 移除 `.dlg-body` 的 max-height:62vh 双滚动限制，滚动统一由 `.upload-dlg :deep(.el-dialog__body){flex:1;overflow-y:auto}` 处理；弹窗 92vh 定高下文件槽位首屏可见，卡片不再被底部按钮覆盖 |
| `ycmn-ui/src/views/system/user.vue` | REQ-003/AC-006 | 账号/姓名/角色 el-form-item 补 prop（username/realName/roleId），使 formRules 必填生效 |
| `ycmn-backend/.../approval/service/ApprovalService.java` | REQ-002/AC-004~005 | submit(claim) 增加可见范围校验：非 all 数据范围用户须可见客户所在公海池（listVisible 命中），否则抛"您没有该公海池的可见权限，无法捞取"；pool_id NULL 视为不可见 |
| `ycmn-backend/.../system/service/SysUserService.java` | REQ-004/AC-007 | create() 增加必填兜底：账号/姓名/角色/手机号非空，缺一抛 BusinessException；新增 import StringUtils |

## 实现决策

- 弹窗修复采用"去内层 62vh、统一外层滚动"而非改数值：弹窗定高 CSS（92vh flex 布局）已存在于 917-922 行，问题只是 .dlg-body 内层 max-height 造成的双重滚动与折叠。
- 捞取校验复用 `SeaConfigService.listVisible()`（内存过滤，与列表展示同源），不新增平行逻辑。
- 后端必填校验放在 create() 开头（唯一性校验之前），保持 @Transactional 语义。

## 新增或更新的测试

无单元测试文件；按测试策略以测试服浏览器实测 + 接口直调覆盖 AC-001~008（见 06-test-matrix.md）。

## 实际执行命令

- 前端构建：`cd ycmn-ui && npm run build` → 成功（incentive-CYscvzRD.js / incentive-BPPrFo2Z.css / index-DGGfgsWb.js 等）
- 后端构建：首次 `mvn -q package -DskipTests` 失败（Lombok × JDK 25 不兼容，ExceptionInInitializerError: TypeTag）；已转 brew install temurin@17，待完成后用 JDK 17 重构建

## 失败与纠正

1. 后端 mvn package 失败：`java.lang.ExceptionInInitializerError: com.sun.tools.javac.code.TypeTag :: UNKNOWN` —— 根因 Lombok 版本与 JDK 25 不兼容，项目要求 java.version=17。
   - 纠正：安装 JDK 17（brew temurin@17），用 `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17...` 重跑。
2. 排查中发现服务器项目副本（/home/ubuntu/projects/ycmn/）是旧基线（ApprovalService 无 BizApprovalConfig、index.vue 无四区排序新 UI），不能作为构建源 —— 改用本地源码构建后部署。
3. 测试中发现 wangleilei 登录失败：根因 ①密码哈希曾被我以 MD5 误写（BCrypt 校验不匹配）②Redis 登录失败锁（login_fail:IP:username）。
   - 纠正：以 htpasswd 生成 $2y$10$ BCrypt 哈希经 stdin 管道写入（避免 $ 远程展开），并 DEL Redis 锁定 key；wangleilei 密码现为 admin123（记录于交付报告）。

## 未完成与残余风险

- 后端 jar 待 JDK 17 构建成功后部署；部署与全部 AC 验证待完成。
- 浏览器缓存：部署后旧 chunk 引用可能 404，需强刷。
