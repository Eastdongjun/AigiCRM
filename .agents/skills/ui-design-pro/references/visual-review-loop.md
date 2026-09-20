# 视觉验证闭环(browser-use + 视觉评审 + 几何断言)

改完 UI 不验证 = 没完成。本文件是本项目实测可用的操作序列与已知坑。

## 流程

```
门禁(npm test → build → 契约脚本) → rsync+compose 部署测试环境 → curl 验证新 bundle
→ browser-use 打开页面截图 → 视觉模型评审(给设计意图,要逐项核验)
→ DOM 几何断言(evaluate 量尺寸,不靠肉眼) → 不达标回改 → 归档证据
```

## 1. 部署验证(测试环境 111.229.37.220)
```bash
rsync -az --delete --exclude node_modules --exclude dist --exclude .vite aigicrm-ui/ aigicrm-test:~/aigicrm/aigicrm-ui/
ssh aigicrm-test "cd ~/aigicrm && docker compose build frontend && docker compose up -d frontend && sleep 3 && curl -s http://127.0.0.1/ | grep -oE 'assets/index-[A-Za-z0-9_-]+\\.js'"
```
bundle 哈希变了才算真上线。

## 2. 截图(browser-use)
- 每次调用都要重新 bootstrap(`ZCODE_PLUGIN_ROOT/scripts/browser-client.mjs` → `setupBrowserRuntime`),内核不跨调用。
- 换向操作:**一次调用只做一件事**;`goto` 后 `waitForLoadState({state:"domcontentloaded"})` + `waitForTimeout(2500-3500)` 再评估。
- 截图必须 `nodeRepl.emitImage(await tab.screenshot())`,不能用返回值。

## 3. 视觉评审(外部视觉模型)
把截图 URL 交给视觉分析工具,prompt 必须包含:设计意图 + 上轮扣分点 + "逐项核验是否解决" + "有无新破相" + 评分 + 一句话可交付结论。
**视觉模型会误读**(曾把视口截断误判为"分页跑出容器"):关键结论要用 DOM 几何断言复核。

## 4. DOM 几何断言(优先于肉眼)
```js
// 等高: Array.from(document.querySelectorAll('.card')).map(e=>Math.round(e.getBoundingClientRect().height))
// 在容器内: pagerRect.bottom <= panelRect.bottom
// 截断: el.scrollHeight > el.clientHeight + 2
// 双栏生效: getComputedStyle(entry).gridTemplateColumns === '176px 690px'
```

## 5. 已知坑(全部踩过)
| 坑 | 症状 | 解 |
|---|---|---|
| 渲染器卡死 | evaluate/domSnapshot 超时 32s | 关闭页签重建(`tabs.new()`),重试一次通常恢复 |
| 重负载页 evaluate 忙 | goto 后立即评估超时 | 等 2.5-3.5s 再评估;拆小调用 |
| `<script setup>` TDZ | watch 引用后声明的 ref,挂载即崩 | watch 钩子统一放所有声明之后 |
| evaluate 里原生 setter | Illegal invocation | 用 `locator.fill()` 代替手写 setter |
| macOS 大小写不敏感 | 归档里 `A.JPG`/`a.jpg` 落地塌成一个 | 原始归档另存为权威副本 |
| 视觉模型误读 | 断言"破相"但几何正常 | 裁剪局部图+几何断言复核 |

## 6. 归档
截图 PNG + 评审结论 + DOM 断言值 → `/Users/dongjun/东来/Agent/00-管理/项目执行/<id>/runs/<run>/evidence/`,提交信息写设计变更。
| CSS 竖向百分比 padding | 滚轮/拨盘首尾无法居中 | 居中填充用 JS 按容器高设置(padding 百分比竖向按宽度解析) |
| 程序滚动反向触发选中 | 双向同步死循环/筛选被破坏 | 跟随锁时间窗豁免 settle |
| python 批量补丁断言中断 | 后续替换全部丢失却以为已写入 | 每段替换后立即写盘,或分次执行 |
