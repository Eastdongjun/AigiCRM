# 实现记录

## 变更文件

- aigicrm-ui/src/views/incentive/search.vue（REQ-001 → AC-001）：主搜索框 placeholder "搜索客户名称..." → "搜索客户名称 / 关键字（如：煤气管道）..."
- 测试服部署：aigicrm-frontend 容器 /usr/share/nginx/html 完整重装（assets/index-D0CHvRKk.js 新入口 + search-CzritNkw.js 含新文案）

## 实现决策

- 与技术计划一致：三项搜索为既有实现（c61acb0），本次补入口提示 + 完整部署
- 部署用完整重装而非增量：容器内残留 `._` AppleDouble 与旧 chunk 混装，完整替换消除隐患

## 新增或更新的测试

- 无单元测试（单行文案）；浏览器 E2E 回归 AC-001~004（见 06-test-matrix）

## 实际执行命令

- `npm run build` → dist 打包 zip → scp /tmp/frontend-dist.zip → 服务器解压 → `docker exec aigicrm-frontend rm -rf html 内容` → `docker cp` → 回读 index.html 引用 index-D0CHvRKk.js、search-CzritNkw.js 含"搜索客户名称 / 关键字"
- 旧版备份：容器内 /tmp/html-rollback/

## 失败与纠正

- 第一次部署（19:58 构建）发现容器内已有同内容构建（19:21），确认测试服功能本就存在——改为仅补文案后重新构建部署
- playwright 截图两次超时（字体加载卡死）→ 关闭浏览器会话重启后恢复
- 1440 截图视口未切换导致尺寸 1200x826 → 调整视口后重截

## 未完成与残余风险

- 生产未部署（需授权）
- 用户浏览器旧 chunk 缓存需强刷（?v= 参数可绕过）
