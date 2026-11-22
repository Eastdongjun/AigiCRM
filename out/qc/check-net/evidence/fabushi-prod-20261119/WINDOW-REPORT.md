# 生产窗口报告（2026-11-19 06:12-06:45 UTC，法布施上线+C7 清账+CERT 取证）

> 授权：用户 2026-11-19「生产窗口安排上」。执行：实施 `01-claude-20`（步骤 0-4）+ QC `fabushi-upload-security-debt`（前置/门控/C7/L5，本报告）。
> 红线遵守：生产零越权写（全部动作在授权窗口内）；测试服窗口后已还原基线 A。

## 一、执行清单（全部 PASS）

| # | 步骤 | 执行 | 结果与证据 |
|---|---|---|---|
| 前置 | 生产只读 pin 六项+DEPLOY-PROD-001 复核+C7 取证+CERT | QC | 全符基线；新版部署脚本判定更优（PREFLIGHT.md） |
| 0 | 部署脚本×5 原子更新 | 20 | md5 对齐仓库、.bak-20261119×5、bash -n 全过 |
| 0.5 | 仓配补 mp 双路由 CSP | 20 | `79dbc930` |
| 1 | 迁移 V2026.11.19.3 | 20 | 19.2→19.3（0.008s）预 dump 自动；**QC 生产三查 PASS**（old5=0/dict=10/orphan=0/了凡四训→tuanduibidu/分布与测试服一致） |
| 2 | 后端 jar `9f8d2467…bfe`（zip 手术构建） | 20 | RESULT=PASS：旧 jar 备份 `…jar-20261119-061638`→原子上传→镜像 md5 门禁→公网 200，自动回滚未触发 |
| 3 | 前端 dist（入口 `index-DHMSLwcu.js`） | 20 | NAS 原子切换，回滚目录 `dist-rollback-20261119-062102`；新路由已验证在包内 |
| — | 测试容器基线 A 还原 | QC | jar 2ea180c6+基线库+health 200 |
| 5 | **C7-NGINX-001 清账** | QC | 见下节 |
| 6 | **L5 终验** | QC | 全绿（下表） |

## 二、C7 双缺口闭环（含一个真缺陷的发现与修复）

- **gap-A（P1 日志脱敏）**：referer 三级脱敏 map 上线；**负例实测**——构造 `Referer: …?token=SUPERSENSITIVE999` 请求后 access log **明文零落**（raw_leak=0）。
- **gap-B（P2 JSSDK 放行）**：**发现仓配设计缺陷**——mp 路由独立 CSP location 因 `try_files→/index.html` 内部重定向被 `location = /index.html` 接管而**从未生效**（该设计 11-12 起未真机验证过）。修复=`$request_uri` map 插值方案（QC 诊断→20 仓配修复 `47a2ca7a`→QC 宿主侧下发 `/root/ycmn/nginx-ssl.conf` 原地写+reload）。**实测：三路由 CSP 均含 res.wx.qq.com，首页对照不含**。
- 坑位存档：prod conf 是**单文件 bind mount**（宿主 `/root/ycmn/nginx-ssl.conf`），docker cp 不可用（device busy）；deploy-lib 的 ssh_prod_once 有挂起行为，窗口改用裸 ssh+keepalive。

## 三、L5 终验（全 PASS）

| 项 | 值 |
|---|---|
| health | 200 |
| 首页 CSP 对照 | res.wx.qq.com=0（严格面未放宽）✅ |
| .js MIME | application/javascript ✅ |
| /actuator | 404 ✅ |
| 伪 token 调业务 API | 401 ✅ |
| 生产 jar | 9f8d2467…bfe ✅ |
| Flyway | 2026.11.19.3 ✅ |
| 法布施字典 | 10 类 ✅ |
| 后端日志 ERROR（45min 窗口） | **0** ✅ |

## 四、CERT-EXPIRY-001（就地取证，解除 BLOCKED）

证书 notAfter=**2027-01-13**（约 55 天）→ 建议续期窗口：**2026-12 中旬前**。

## 五、回滚锚点（全在位）

脚本 .bak-20261119×5 ｜ 旧 jar `[备份 jar]` ｜ dist `[备份 dist]` ｜ nginx conf `[nginx 配置备份]` ｜ 迁移 19.3 逆写（迁移头注释）

## 六、判定与剩余

**窗口判定：PASS（0 FAIL）**。上线内容：法布施 F1-F5+F3a/F3b 全量（录音/三通道上传/票制/阅读器换票/十大类+39 种子/限额/NEW/提醒/评论三态/木鱼音效）+ MP-TOKEN-001 修复 + C7 双缺口 + 部署脚本现代化。

剩余（不阻断）：①MP 小程序包发布（cli upload+体验版/提审，微信平台侧待用户）；②真机 38 条验收；③PC Playwright 冒烟（L4）；④证书续期排期（12 月）。
