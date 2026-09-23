# AIGICRM 小程序发布与体验版操作手册

> 面向要把小程序**真正用起来（手机扫码收数据）**的操作者。三条路径按"从快到正式"排列：
> **预览/真机调试（今天可用）→ 体验版（多人可用）→ 正式发布（对外开放）**。

## 0. 前置事实（本项目已确认）

| 项 | 现状 |
|---|---|
| 小程序源码 | `02-迭代源代码/aigicrm-miniprogram`（根目录含 `app.json` + `project.config.json`，`miniprogramRoot: ./`） |
| AppID | **`wx513e6dd7eabdae7b`**（AIGICRM 小程序在微信公众平台的登记值，2026-09-13 所有者确认；已写入 `project.config.json` 并由 `scripts/check-wechat-project-root.sh` 守住） |
| 接口地址档位 | `config/env.js` 写死：`develop` 读本机覆盖档、`trial`/`release` **固定** `https://www.aigicrm.com` |
| 本机覆盖档 | `config/env.local.js`（已 gitignore，不入库）。当前指向**生产** `https://www.aigicrm.com`；改成 `http://111.229.37.220` 即收进测试库；**删除即回 demo 档** |
| 生产后端 | HTTPS 可用；小程序用到的 17 个接口路径在生产均返回 403（存在且受鉴权保护）、无 404 |
| 包体积 | 源码约 800KB / 183 个文件（主包上限 2MB，余量充足） |
| 上传目录 | 后端上传卷已修好（属主 10001），手机拍照上传会真正落盘并可回看 |

## 1. 路径 A：开发者工具「预览 / 真机调试」（不发布、不审核，今天可用）

1. 打开**微信开发者工具** → 导入项目 → 目录选 `02-迭代源代码/aigicrm-miniprogram`（**别选上一级**）。
2. 工具右上角用**该 AppID 归属的开发者微信**扫码登录。
3. 「详情 → 本地设置」勾选 **不校验合法域名、web-view（业务域名）、TLS 版本以及 HTTPS 证书**。
4. 点工具栏 **预览**（或 **真机调试**）→ 生成二维码 → 手机微信扫码打开。
5. 数据去向由 `config/env.local.js` 的 `apiBaseUrl` 决定：
   - `https://www.aigicrm.com` → **收进生产库**（当前配置）
   - `http://111.229.37.220` → 收进测试库
6. 登录：小程序登录页是**用户名 + 口令**（同一套 `/api/auth/login`），用对应环境的账号登录即可开始录入。

**限制**：二维码由扫码的开发者本人使用、有效期短、改代码要重新扫码 → 适合先自己收一批数据。

### 1.1 DevTools 自动化预检：移动端 PC 对齐

移动端首页、客户、激励、文件、审批、公海、统计、排行、提醒、资料、登录、反馈等页面可以在本地微信开发者工具里先跑自动化预检，再进入真机和真实账号验收。该预检只使用模拟页面数据，不会提交真实业务记录。运行页面自动化前先跑 `dev-verify changed`，其中的 `check-mobile-pc-api-parity-contract.sh` 会确认小程序与 PC 已实现模块共享同一批后端路径，并确认客户导入/导出、公海配置、审批配置、激励授权发放、学分录入/导出等 PC-only API 没有进入小程序 API 层。

```bash
# 1) 确认开发者工具已登录，并打开正确的小程序项目
/Applications/wechatwebdevtools.app/Contents/MacOS/cli islogin \
  --project /Users/dongjun/东来/项目/02-已上线/01-AIGICRM/02-迭代源代码/aigicrm-miniprogram \
  --port 9420 --lang zh

/Applications/wechatwebdevtools.app/Contents/MacOS/cli open \
  --project /Users/dongjun/东来/项目/02-已上线/01-AIGICRM/02-迭代源代码/aigicrm-miniprogram \
  --port 9420 --lang zh

# 2) 开启 automator WebSocket；保持该进程运行
# 先确认当前开发者工具是否仍支持旧版 --auto-port：
/Applications/wechatwebdevtools.app/Contents/MacOS/cli auto --help

# 旧版开发者工具支持 --auto-port 时使用：
/Applications/wechatwebdevtools.app/Contents/MacOS/cli auto \
  --project /Users/dongjun/东来/项目/02-已上线/01-AIGICRM/02-迭代源代码/aigicrm-miniprogram \
  --port 9420 --auto-port 9421 --lang zh

# 新版开发者工具若 help 中没有 --auto-port，即使 cli auto 返回成功也可能不开放
# miniprogram-automator WebSocket。此时不要把页面自动化标记为通过；
# 需换用兼容的开发者工具/automator 组合，或显式提供有效的 WECHAT_AUTOMATOR_WS。

# 3) 另开终端运行完整移动端 PC 对齐自动化
cd /Users/dongjun/东来/项目/02-已上线/01-AIGICRM
02-迭代源代码/scripts/dev-verify.sh changed

cd /Users/dongjun/东来/项目/02-已上线/01-AIGICRM/02-迭代源代码/scripts/miniprogram
WECHAT_AUTOMATOR_WS=ws://127.0.0.1:9421 npm run verify:mobile-parity

# 可选：只复测激励附件专项
WECHAT_AUTOMATOR_WS=ws://127.0.0.1:9421 npm run verify:incentive-attachments
```

通过标准：聚合脚本最终每个子项均输出 `PASS`，且进程退出码为 0。它覆盖首页看板和快捷入口、客户中心和客户表单、激励上传/列表/详情/时间轴附件、文件中心、审批中心、公海、经营统计、核心客户指标、排行、提醒、个人资料、登录、反馈/通知，以及应用中心对客户导入/导出、系统管理、配置、ACL、学分录入、批量操作等 PC-only 入口的隐藏边界。

边界：该预检不能替代真机选择文件、真实提交记录、真实下载保存、真实授权/拒绝账号差异和 PC 同数据对账。发布或体验前仍要按 `docs/mobile-manual-qa-checklist.md` 执行对应手工项，并把脱敏后的 PC/小程序同数据对账表交给 `python3 02-迭代源代码/scripts/check-mobile-reconciliation-evidence.py /path/to/sanitized-reconciliation.md` 校验；该脚本只检查 Markdown 表格结构、必填项、结论状态和敏感字段痕迹，不会连接后端或替代真实账号验收。

## 2. 路径 B：体验版（多人长期使用）

### 2.1 你必须在微信公众平台做的三件事

1. **小程序账号与成员**：用 AppID `wx513e6dd7eabdae7b` 登录微信公众平台（企业主体需已认证）→ 成员管理里把使用者加为**体验成员**（上限 15 人/可调整）。
2. **登记服务器域名**：开发管理 → 开发设置 → 服务器域名 → **request 合法域名** 添加 `https://www.aigicrm.com`。
   > 微信**只接受 HTTPS 域名，不接受裸 IP**（如 `111.229.37.220`），因此**测试环境不能用于体验版**；测试服只能在路径 A 里用。
3. **生成上传密钥**（若走 CI 上传）：开发管理 → 开发设置 → 小程序代码上传 → 生成密钥并下载（`private.<appid>.key`），同时把**当前出口 IP** 加入白名单。

### 2.2 上传代码（两种方式，二选一）

**方式 1：开发者工具手动上传（最省事）**
工具右上角「上传」→ 填版本号与备注 → 上传成功后在公众平台「版本管理」里 **设为体验版** → 把二维码发给体验成员。

**方式 2：CI/命令行上传（可重复、可脚本化）**

```bash
cd 02-迭代源代码/scripts/miniprogram
npm install                     # 安装 miniprogram-ci（只在工具目录，不进小程序包）

# 上传体验版代码
MINIPROGRAM_PRIVATE_KEY=~/.wechat-mp/private.wx513e6dd7eabdae7b.key \
MINIPROGRAM_VERSION=1.0.0 \
MINIPROGRAM_DESC="首次体验版" \
npm run upload

# 或：不开开发者工具，直接生成预览二维码（等价"预览"）
MINIPROGRAM_PRIVATE_KEY=~/.wechat-mp/private.wx513e6dd7eabdae7b.key npm run preview
```

可选环境变量：`MINIPROGRAM_APPID`（默认取 `project.config.json`）、`MINIPROGRAM_ROBOT`（1-30，同版本重复上传时换一个）、`MINIPROGRAM_QR_OUTPUT`（预览二维码输出路径）。

**密钥纪律**：密钥是凭据，**不要放进仓库**（脚本检测到密钥在仓库内会告警）；建议放 `~/.wechat-mp/` 并 `chmod 600`。

### 2.3 体验版注意事项

- 体验版请求**受域名白名单约束**：不要依赖 `project.config.json` 里的 `setting.urlCheck=false`（那主要作用于开发版/工具环境），务必完成 §2.1-2 的域名登记。
- 体验版走 `trial` 档 → 固定 `https://www.aigicrm.com`（生产数据）。**要拿体验版收测试数据是不可行的**（需 HTTPS 测试域名 + 登记）。
- 上传前必须先跑本地门禁，确保 PC/小程序差异、PC-only 边界、手工 QA 契约和小程序基础配置仍然一致：

```bash
cd /Users/dongjun/东来/项目/02-已上线/01-AIGICRM
02-迭代源代码/scripts/dev-verify.sh changed
02-迭代源代码/scripts/check-mobile-manual-qa-contract.sh
bash 02-迭代源代码/scripts/check-wechat-project-root.sh
bash 02-迭代源代码/scripts/check-miniprogram-local-profile.sh
python3 02-迭代源代码/scripts/check-miniprogram-permission-contract.py
python3 02-迭代源代码/scripts/check-mobile-reconciliation-evidence.py /path/to/sanitized-reconciliation.md
```

## 3. 路径 C：正式发布

1. 公众平台「版本管理」把体验版**提交审核**（需填写类目、功能页、隐私协议等）。
2. 审核通过后点**发布**，全量用户可用。
3. 发布前确认：域名已登记且证书有效、隐私政策与用户信息收集说明已配置、`env.js` 的 `release` 档指向正确（当前固定生产 HTTPS ✔）。

## 4. 常见报错对照

| 现象 | 原因 | 处理 |
|---|---|---|
| 上传报 `invalid ip` / IP 白名单 | CI 上传要求出口 IP 在白名单 | 公众平台 → 开发设置 → 小程序代码上传 → 添加当前公网 IP |
| 上传报密钥错误 | 密钥与 AppID 不匹配或已重置 | 重新生成并下载密钥，确认 `MINIPROGRAM_APPID` 一致 |
| 体验版里接口全部失败 | 域名未登记或非 HTTPS | 登记 `https://www.aigicrm.com`；测试环境 IP 不能用于体验版 |
| 工具里提示"项目根目录未找到 app.json" | 导入目录选错 | 必须直接选 `aigicrm-miniprogram` 目录本身 |
| 真机打开白屏/接口失败 | 手机访问不到所配地址（如 127.0.0.1） | 用公网可访问地址（生产域名或测试服公网 IP） |
| 主包超 2MB | 静态资源过多 | 本项目当前约 800KB，余量充足；新增大图请走接口上传而非打包 |
| 重复上传同版本被拒 | 版本号/机器人重复 | 换 `MINIPROGRAM_VERSION` 或 `MINIPROGRAM_ROBOT` |

## 5. 数据收集链路的健康前提

- **后端**：生产 `https://www.aigicrm.com` 接口可达（已核验）；测试服 `http://111.229.37.220` 同上。
- **文件上传**：后端上传卷属主必须可写（容器以 uid 10001 运行）。2026-09-13 已修复测试环境并加固 Dockerfile；**若生产上传照片失败**，按同一清单检查生产上传卷属主。
- **权限**：小程序登录后按 `/api/auth/info` 返回的权限集控制页面；新增权限码需在 `sys_menu` 登记并绑定角色（见 `scripts/check-permission-registration-contract.py`）。
