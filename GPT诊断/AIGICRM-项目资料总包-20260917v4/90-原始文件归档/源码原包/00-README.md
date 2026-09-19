# AIGICRM 源码审计任务书（给 GPT）

> 包内容为 2026-09-17 工作区现状（含未提交改动）。测试服 IP 已脱敏为 `TEST_SERVER_IP`。
> 生产域名 `www.aigicrm.com` 为公开信息予以保留。包内无真实凭据，可放心展开。

## 包结构

| 包 | 内容 | 规模 |
|---|---|---|
| `pack-backend.tar.gz` | `backend/`：Spring Boot 4.1/Java 21 后端源码 + pom（含 `db/migration` 全量 Flyway 迁移） | ~2.3MB |
| `pack-pc.tar.gz` | `pc/`：Vue3 PC 前端 `src` + package/vite/vitest 配置 + public | ~1.4MB |
| `pack-mini.tar.gz` | `mini/aigicrm-miniprogram`：微信小程序 46 页全量 | ~1.2MB |
| `pack-gates.tar.gz` | `gates/`：`docs/`（功能蓝图/权限矩阵/审计报告/联调手册）+ `scripts/`（全部本地契约检查）+ `sql/` + `AGENTS.md` + `ROUTE.md` + 进度与最近 5 篇 evidence | ~2.7MB |

`SHA256SUMS` 为校验文件。

## 建议阅读顺序（分 4 轮喂，别一次全倒）

1. 先 `gates/`：`docs/system-functional-blueprint.md`（功能全景）→
   `docs/function-permission-matrix.md`（权限矩阵）→
   `docs/unified-feature-audit.md`（按钮级行号审计，机器生成）→
   `ROUTE.md`（工程执行路线）。
2. 再 `backend/`：`security/`（JwtAuthFilter/UserDetailsServiceImpl/PermissionCacheService，
   权限=Redis缓存+permVersion自愈）→ `module/{customer,incentive,file,approval,sea,statistics,system}/`。
3. 再 `pc/`：`src/api/` 对照后端 `@GetMapping/@PostMapping` 查漂移；
   `src/views/incentive/search.vue:387` 关键字同时送 customerName+keyword。
4. 最后 `mini/`：46 页，`app.json` 注册表；`utils/auth.js` 权限来自 `/api/auth/info`，
   登录/启动/首页/个人页/切集团时刷新。

## 已知结论（2026-09-17 刚验过，你重点复核，不要重复造轮子）

- `contracts` 门禁 116 PASS 全绿；刚修过两处契约（敏感权限字面过期、导航误报），见
  `gates/evidence-latest/20260917-miniprogram-sweep.md`。
- PC 改角色权限 → 小程序实时生效（evict+版本递增，无需重登；直连库改绑定会残留旧权限，
  属预期设计），见 `20260917-permission-parity-probe.md`。
- 本地库 `biz_file` 有 1081 条附件元数据但磁盘只有几个文件——老附件预览 404 是数据问题，
  功能闭环正常。
- `pages/notification/*` 是反馈契约的验证夹具页（零 UI 入口但被 4 处门禁钉死），别当死代码删；
  `pages/file/index`、`pages/cases/*` 是重定向桩。
- 搜索 `华晋焦煤沙曲一号煤矿` 查无数据是 LIKE 子串语义（真名含“有限责任公司”），非 bug。
- `?thumbnail=1&token=` 缩略图链路已通（短时 download-scope token）。

## 请你查的方向（按优先级）

1. **权限一致性**：PC 端 160+ 调用 vs 后端 183 端点的越权/漏守卫；`@PreAuthorize` 与
   service 层二次守卫是否成对；`hasAnyAuthority` 的 OR 是否放宽了本意。
2. **PC/小程序 parity**：四项激励（分类树/安装前中后跟踪槽/积分/统计/查询/阅读状态）、
   客户（财务/联系人字段级 `finance:view`/`contact:view`）、审批、公海、
   文件（移动端禁分享/ACL 写）——以 `docs/unified-feature-audit.md` 的行号清单逐条对。
3. **数据范围**：`self/team/dept/all/office/region/custom` 解析与 OrgScopeResolver，
   跨集团越权（GroupScopeUtil）有无漏网。
4. **契约脚本本身**：`scripts/check-*.sh` 的 grep 断言是否过松（误过）或过严（误杀，
   如本次修掉的两处）；测试是真断言还是走过场。
5. **迁移链**：`backend/src/main/resources/db/migration/` V1→最新是否可重放，
   有无不可逆/破坏性语句；V25（finance/contact 权限种子）在新库是否生效。

## 输出格式要求

- 按文件行号引用（`path:line`），每个问题给：现象 → 根因 → 影响面 → 修复建议。
- 区分【真问题 / 存疑需联调 / 误报】三类；不要把“未验证”等同于“通过”。
- 最后给 Top10 风险榜（按越权 > 丢数据 > 功能漂移 > 体验排序）。
