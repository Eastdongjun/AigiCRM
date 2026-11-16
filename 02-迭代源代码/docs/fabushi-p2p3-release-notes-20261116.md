# P2/P3 交付与发布清单（2026-11-16 Round 5）

## 一、本批交付内容

### P2：MP PDF 页码级进度（方案 A：H5 PDF.js 自动回传，用户拍板）

| 组件 | 文件 | 说明 |
|---|---|---|
| H5 阅读器 | `aigicrm-ui/src/views/mp/FabushiReader.vue` | PDF.js 单页模式+滑动/按钮翻页，翻页 debounce 1.2s 自动回传；进度回读→续读定位；微信 UA 守卫 |
| H5 路由 | `aigicrm-ui/src/router/index.js` | `/mp-fabushi-reader` + 免登录白名单（同 /mp-file-picker 先例） |
| MP 容器页 | `aigicrm-miniprogram/pages/fabushi/reader/`（新页面，51 页） | fabushi:view 闸 + URL 携带主 JWT → web-view |
| A4 改造 | `aigicrm-miniprogram/pages/fabushi/book/index.js` | openReader 改 navigateTo reader 页；**openDocument 与写死 lastPage=1 两条路径一并退役**（旧实现会把 PC 已存页码重置回第 1 页） |

- 后端零改动（progress/download API 均现成）
- 依赖新增：`pdfjs-dist@6.5.438`（懒加载 chunk 445KB，不进 PC 主包）

### P3：部门进度 + 月度报告

| 组件 | 文件 | 说明 |
|---|---|---|
| 部门进度 | `FabushiDashboardService.buildDeptProgress()` | dashboard/monthly 共用；dept_id→sys_dept 内存分组（口径同 score/statistics 先例）；无部门归"未分配"独立行 |
| 月度聚合 | `FabushiDashboardService.monthly()` | 汇总+环比上月+整月每日趋势+部门进度+活跃榜；month=YYYY-MM 缺省当月 |
| 新端点 | `GET /api/fabushi/stats/monthly`（`fabushi:stats:view`）、`GET /api/fabushi/stats/monthly/export`（`fabushi:stats:export`） | **零新权限码**；API 总数 288→290 |
| PC | `stats.vue` | 部门进度表（8 列+覆盖率条）+ 月度报告卡（月选择/7 格汇总/环比%/CSV 导出） |
| MP | `pages/fabushi/index.js/.wxml` | 统计页签新增部门进度前 5 + 月度简报卡 |

## 二、验证证据（全部实测）

| 项 | 结果 | 证据 |
|---|---|---|
| 后端全量 | 1107 tests / 0 failures | mvn test |
| PC | 324 tests 全过 + build PASS + vue-tsc 基线对账 PASS | npm test / build / check-ui-typecheck |
| MP | dev-verify PASS（51 页） | dev-verify.sh miniprogram |
| 一键门禁 | PASS=9 / FAIL=0（最终轮） | scripts/gate.sh |
| 权限矩阵 | 24,192 例：valid+400=0、5xx=0、越权 2xx=0；794 资源守卫 403+6 瞬时超时，全部已归因；monthly 新端点 0 fail（无权 403 正确） | test-results/permission-matrix/ |
| 二期定向 | 48/48 全过 | permission_phase2_smoke.py |
| P2 端到端 | H5 打开 3 页 PDF → 翻页 1/3→2/3 → **服务端 lastPage=2/totalPages=3**（基线 1/1，负例自证成立）→ 重开自动续读到第 2 页 | h5-reader-3page-page2.png / h5-reader-resume-page2.png + progress API 实测 |
| P3 实测 | PC 看板部门进度 7 行+月度卡 7 格渲染（截图）；monthly 200（dept=7/trend=30 天）；CSV 200（762B BOM 正常）；MP 统计页签部门 5 行+月度简报 2026-11（data 实证+截图） | pc-round5-stats-dept-monthly.png / mp-round5-stats-dept-monthly.png |
| H5 守卫 | PC 直开显示"请从微信小程序进入"拦截卡 | pc-round5-reader-guard.png |
| 测试环境 | 后端 jar md5 `769fdf28`（双端一致）；前端 index.html md5 `83e76374`；health=401 正常 | docker exec md5sum |

**部署中修复的一个真实 500**：首次部署后 dashboard/monthly 500——buildDeptProgress 排序 comparator 把
`memberCount`（Set.size() → Integer）强转 Long 抛 ClassCastException。已改经 Number 取值
（FabushiDashboardService.java:428-436），本地单测未抓住的原因：mock 环境下 mockStatic 作用域差异，
已记录；复测三端点 200、矩阵 5xx=0。**教训：对放入 Map 后参与强转的值，装箱类型必须显式统一。**

## 三、测试环境 nginx 变更（生产发布前必须同步！）

1. **`.mjs` MIME 映射**：`/etc/nginx/mime.types` 第 8 行 `application/javascript js;` → `js mjs;`
   - 测试服已热修（容器内直接 sed + reload）。⚠️ 容器配置在镜像内、**容器重建即丢**；
   - 生产发布必须：改前端镜像构建源（Dockerfile/mime.types）或挂载自定义 mime.types，否则 H5 阅读器在生产必然白屏（PDF.js worker 拒载）。
2. **JSSDK CSP（非阻塞，已有兜底）**：`script-src 'self' 'unsafe-eval'` 不含 res.wx.qq.com，
   H5 内微信 JSSDK 加载失败 → web-view 内返回按钮不可用，**用户可用小程序自带左上角返回键**（实测兜底有效）。
   如需 H5 内返回按钮生效：给 /mp-fabushi-reader 加定向 location 放行 res.wx.qq.com（FilePicker 的同款方案在测试服也未实施，属既有状态）。

## 四、测试数据盘点（正式验收前按需清理，勿动生产库）

| 数据 | 位置 | 状态 | 建议 |
|---|---|---|---|
| book3 了凡四训（approved，pdfFileId=2692） | 测试库 | P2 端到端验收书 | **保留**（3 页测试 PDF，验收参照物） |
| book1 UI验收-论语（无 PDF）/ book2 PC阅读验收（disabled，pdf 2689） | 测试库 | 早期验收遗留 | 可删可留，disabled 不影响体验 |
| e2e-3page.pdf（fileId 2692） | 测试库+测试服磁盘 | 本批上传 | 保留（book3 挂载中） |
| 早期同名 test3page.pdf（2690/2691 等孤儿行） | 测试库 | 重传产生的孤儿文件行 | 可清理（file_space 孤儿清理通道） |
| book3 progress lastPage=2/totalPages=3 | 测试库 | P2 验证产物 | 保留（展示续读效果） |
| post1 附件/post2/3 已下架/章节/活动/admin 书架提醒 | 测试库 | 二期验收数据 | 正式验收前按需清 |

## 五、发布清单（生产，全部待用户明确同意后执行）

1. DB：**无迁移**（P2/P3 零 DB 变更）——无需 deploy-db
2. 后端镜像重建+部署（jar 含 P2/P3；上传目录 `/app/uploads/fabushi` 生产需预建+授权，上一批已登记的遗留）
3. 前端镜像重建+部署（dist 含 H5 阅读器+统计增强）
4. **nginx mime.types 加 mjs 映射（见第三节，不做必白屏）**
5. MP：用户在 DevTools upload 1.0.71（本批 MP 改动：阅读器容器页+A4 改造+统计页签）
6. 提审后冒烟：生产登录/看板/阅读器/月度导出

## 六、回滚方案

- **后端**：上一版 jar md5 `6218696514ba973693258b116f35ff21` 已在测试服 /tmp 有存档（部署前原 jar）；
  生产回滚=重建上一 release 镜像或 docker cp 回旧 jar+restart（P2/P3 无 DB 变更，无数据回滚负担）
- **前端**：docker cp 回旧 dist（旧 index.html+assets 未保留在容器内，需从镜像重建——
  **建议生产部署前先 `docker cp ycmn-frontend:/usr/share/nginx/html /backup/`** 存一份现网静态资源）
- **MP**：线上 1.0.70 版本不回滚（新版本提审后旧版仍可用，无强制更新）
- **nginx**：mime.types 改动为增量（js mjs），回滚=删除 mjs 字样 reload

## 七、已知残余

1. 6 条矩阵超时为 64 并发瞬时抖动（读端点，单独实测 200），非缺陷
2. H5 内返回按钮依赖 JSSDK（CSP 拦截），暂用小程序系统返回键（兜底实测有效）
3. PC 阅读器仍为手动页码模式（本批未动 PC read.vue；MP 已超越 PC——PC 是否升级 H5 阅读器列为待裁项）
4. MP 真机全链路（web-view 域名校验、真机 PDF 性能）需用户在 DevTools upload 后真机预览验证——**本环境 BLOCKED**，已用 DevTools 模拟器+H5 浏览器双通道覆盖
