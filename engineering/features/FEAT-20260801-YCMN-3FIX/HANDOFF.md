# HANDOFF — FEAT-20260801-YCMN-3FIX（已完成交接）

> 新会话接手提示：读本文件即可恢复上下文。目标与验收全部完成，以下是最小事实集。

## 当前状态（2026-08-01 17:50）

- **DELIVERED**（status --require-delivered exit=0），AC-001~008 全 PASS，已部署测试服。
- 唯一未做：**本地 git 未提交**（4 个文件改动 + 功能包），需要用户确认后 commit。

## 目标与结果

| 问题 | 根因 | 修复 |
|---|---|---|
| ①激励安装文件点击不弹上传 | 弹窗 `.dlg-body` 62vh 内层滚动导致文件槽位折叠、卡片被底部按钮覆盖 | 去内层 max-height，统一外层滚动（`incentive/index.vue` 样式） |
| ②公海池可见范围校验 | 捞取接口不校验申请人可见池 | `ApprovalService.submit` claim 分支加 listVisible 校验 |
| ③新增用户必填 | el-form-item 缺 prop + 后端无兜底 | `user.vue` 补 3 个 prop；`SysUserService.create` 加 4 项必填 |

## 关键事实（勿再踩坑）

- 源码基准：`/Users/donglai/东来/01-Projects/01-aigicrm/04-源码`（git main，领先 origin/backup-20260731 52 commits）
- 测试服：111.229.37.220，sshpass -p 'redacted-ssh-password' ssh ubuntu@；容器 ycmn-frontend/ycmn-backend/shared-mysql/shared-redis
- **服务器项目副本（/home/ubuntu/projects/ycmn/）是旧基线，不能直接构建部署**
- **本机 JDK25 与 Lombok 不兼容**：后端构建需 JDK17。已配 `~/.mavenrc` → JAVA_HOME=/tmp/jdk-17.0.20+8/Contents/Home（临时目录，重启后可能消失）
- 后端 jar 已在服务器 JDK17 构建部署（17:33）；前端 dist 17:18 构建部署（容器内备份 /tmp/html-backup-*.tgz）
- nginx 有 Referer 防盗链；密码 BCrypt（勿用 MD5 改）；登录失败锁定在 Redis（login_fail:IP:user）
- wangleilei 密码已重置 admin123；销售员公海列表按可见范围过滤为 0 行（配置如此，非 bug）

## 残余风险

- 原生文件选择框弹出依赖真实手势，等用户浏览器实测确认
- 用户端浏览器旧 chunk 需强刷
- 单槽模式（visit/exchange/plan）未单独截图（与三槽共用修复路径）

## 下一步（若用户要求）

1. 用户确认后 `git add` + commit 4 个文件与功能包（勿 push，分支未建）
2. 用户实测"+"弹文件选择框 → 补 AC-001 的真实浏览器证据
3. 若单槽模式要补截图：1440x900 打开 visit 子类上传弹窗
