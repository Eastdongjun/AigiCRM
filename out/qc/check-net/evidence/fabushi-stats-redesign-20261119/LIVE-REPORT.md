# UI-STATS-REDESIGN-001 测试服实测报告（2026-11-19 · QC fabushi-upload-security-debt）

> 批次：`8d2b4344` + 修复批 `ee85bf3e`（基线 45e55eb3）。部署执行 01-claude-20，实测 QC。
> 结论：**全项 PASS**——按用户常设授权（"申报到了就扫 扫完推送"）放行生产推送。

## 一、部署真身核验（对 20 申报清单）

| 项 | 申报 | QC 实测 |
|---|---|---|
| jar md5 | 878840ef…9fec9cd | 一致 ✓ |
| 前端入口 | index-BtipTiCr.js | 一致 ✓ |
| health | 200 | 200 ✓ |
| DB | 未动（基线 aigicrm_prod_20261016） | ✓（旧 6 门类字典=基线态，非缺陷；生产库为 10 大类） |

## 二、口径 SQL 独立复算（6/6）

| 口径 | SQL 独立值 | API 值 | 判定 |
|---|---|---|---|
| 周期已读人数(周) | 3 | 3 | ✓ |
| 书目 rollup(周) | 空（周内 3 条阅读全为无章节帖，section_id=NULL） | bookRanking=[] | ✓ 一致 |
| myStreakDays(admin) | 0（仅 11-16 读过，11-18 即断） | 0 | ✓ |
| myTopCategory(admin) | null（其阅读为 feed 帖，按规格排除） | null | ✓ |
| dailyGoalRate | null（reminder_enabled=1 行数=0） | null | ✓ |
| monthly activeUsers | 28（四表 union distinct） | 28 | ✓ |

## 三、字典跟随实测（五幕全过）

造链（字典行 qc_cat/QC临时类 + 书 + 章 + 帖 + 阅读）→ **出现**：bookRanking=[QC临时书/QC临时类/1/1]、categoryReads=[QC临时类/1] ✓ → 字典改名 → **即时跟随**（两处同步变 QC改名类，零缓存）✓ → 字典停用 → **按规格回退显示码本身**（数据保留、标签回退——规格§四.1 明文行为）✓ → 清理 → **零残留**、指标还原 3/0/0 ✓。

## 四、CSV 一致性

周报指标块（周期已读人数 3/达成率空/新增内容 1/木鱼 28/评论 6）与 dashboard API 逐值同源 ✓；书目/类别区块结构在、值随数据 ✓。月报同构 ✓。

## 五、元素运行时对账（PC 12/12 · MP 6/6）

- PC（DOM+视觉双证）：KPI 四新卡 ✓（今日已读/周期已读/覆盖率/达成率）、成员榜列序（已读前置）✓、书目榜表+空态 ✓、类别分布空态+已读人次标注 ✓、趋势/部门/筛选保留 ✓、月度**阅读组+互动组** ✓、我的**六格精确**（累计已读天数/连续/最常读类别/阅读人次/我的内容/获木鱼）✓、导出空态样式 ✓
- MP：代码级 6/6（M1 四格/M2/M3/M4 Top5/M5/M6）——真机视觉留用户体验版验收
- 边框批 45e55eb3 五子项：#1 **数学级确认**（末行 bottom 1081 ≤ 表底 1081 完全包含）+#2/#4/#5 视觉确认消除，#3 代码确认（10px 留白）；生产视觉随推送窗复核

## 六、测试服环境修复（QC 执行，可逆）

- 发现：ycmn-frontend（ycmn_default 网）与 ycmn-backend（shopjoy-net 网）**跨网**→ nginx /api 502（存量缺陷，浏览器路径首次被使用即暴露；此前测试均走 8080 直连）
- 处置：`docker network connect shopjoy-net ycmn-frontend` + nginx reload → API 恢复 200。**转 20 沉淀**：compose/网络配置固化，防重建复现
- 登录通道：测试库 admin 密码与生产不同（10-16 拷贝），实测走 JWT 注入 localStorage（零改库）

## 七、放行

按用户常设授权，通知 20 执行：生产推送（dist+jar，回滚锚点先行）+ MP cli upload 体验版（二维码交用户，提审用户平台操作）。QC 随后 L5 终验。

## 八、生产 L5 终验（推送后，全 PASS）

| 项 | 值 |
|---|---|
| jar md5 | 878840efc46aba501674bc5569fec9cd ✓（与测试容器同源） |
| health / ERROR(15min) | 200 / 0 ✓ |
| 前端入口（公网+服务器） | index-BtipTiCr.js ✓（确定性构建与测试服同哈希=渲染一致双证） |
| Flyway / 字典 | 2026.11.19.3 / fabushi_category 10 类 ✓ |
| 安全面 | 首页 CSP res.wx=0 ✓ · .js MIME ✓ · /actuator 404 ✓ · 伪 token 401 ✓ |
| 新字段生产活性 | dashboard: periodReadUsers=3、dailyGoalRate=25.0（真实数据）、periodCoveragePct=null（全量档口径）✓；personal: myStreakDays/myReadDays ✓ |
| 回滚锚点 | jar 082924 / dist 083248（20 落位）+ 早前锚点在档 |

**批次闭环**：UI-STATS-REDESIGN-001（8d2b4344+ee85bf3e）生产上线完成；UI-STATS-BORDER-001（45e55eb3）随窗上线，工单转 CLOSED（生产 dist 与测试服实测构建同哈希，测试服视觉+DOM 数学级确认即生产确认）。
**MP**：cli 通道无私钥未遂（全机查证），转用户微信开发者工具手动上传→体验版→真机复验。
**附带 infra commit**：dffb60fd（compose 双网固化，20；非代码路径，免扫记录在案）。
