# 设计令牌与已验证模式(AIGICRM)

全部模式已在本项目测试环境上线并通过视觉评审。写 CSS 前先来这里找,缺的才新造,新造的写回本文件。

## 设计令牌表

| 类别 | 令牌 | 值 |
|---|---|---|
| 画布 | 页面底色 | `#f5f7fa`(全局 .main-content) |
| 容器 | 白卡 | `#fff` + 圆角 16px + `box-shadow:0 1px 2px rgba(16,24,40,.04),0 10px 28px rgba(16,24,40,.06)` |
| 细线 | 分隔线 | `#eef0f3`(主分区) / `#f1f3f6`(条目间) |
| 强调蓝 | 单强调色 | `#2563eb`(选中态/主数字),hover `#409eff` |
| 未读红 | 状态色 | `#e11d48`,底 `#ffe9ee`,点光环 `rgba(225,29,72,.12)` |
| 文字 | 主/次/弱 | `#111827` / `#4e5969` / `#9ca3af` |
| 品类色点 | visit/exchange/plan/trial | 琥珀`#b45309`/蓝`#1e40af`/紫`#6d28d9`/绿`#065f46`(只做 7px 圆点+文字,不做药丸) |
| 字阶 | 大数字/正文/标签 | 28-40px·800·tabular-nums / 14.5px·1.85 / 11-12px·letter-spacing 1px |
| 圆角族 | 外卡/内块/瓦片 | 16px / 12px / 8px(严格递减,不混用) |
| 间距 | 节奏 | 小布局间 14-20px,条目间 20-28px,细线替代大间距 |

## 模式库

### 1. 全局页面骨架(所有菜单页)
`layout/index.vue` 已包 `.page-shell`;新页面不要再自己画白容器:
```css
.page-shell{background:#fff;border-radius:16px;padding:12px 14px;min-height:calc(100vh - 116px);
  box-shadow:0 1px 2px rgba(16,24,40,.04),0 10px 28px rgba(16,24,40,.06)}
.page-shell .el-card{border:none;box-shadow:none;background:transparent}
```
视口撑满页(提醒/审批/公海)高度用 `calc(100vh - 124px)`。

### 2. 列表页三段式(激励查询范式)
筛选条(无底边线) → 卡网格(`grid repeat(4,1fr)`,卡=浅灰瓦片 `#f2f4f7` 悬浮变白抬升 2px,`min-height` 统一) → 分页(`border-top:1px solid #eef0f3`)。
缩略图统一 1:1 四格:`.card-imgs{display:grid;grid-template-columns:repeat(4,1fr);gap:6px}`。
客户名全显:`flex-basis:100%` 独占整行 + `-webkit-line-clamp:2`。

### 3. 详情弹窗·账本风(白底细线)
```
吸顶条(blur+底细线) 丨 概览区(头像/名称/未读胶囊/三指标, 底细线) 丨 工具条(搜索+分段方向, 底细线) 丨 主从区 丨 底条(顶细线)
```
Element Plus 弹窗壳:`:deep(.el-dialog){border-radius:20px}` `:deep(.el-dialog__body){padding:0;background:#fff}`。

### 4. 主从骨架(左时间轴导航+右内容卡)
```css
.dtl-body{display:grid;grid-template-columns:212px minmax(0,1fr);flex:1;min-height:0}
.dtl-rail{position:relative;border-right:1px solid #eef0f3;overflow-y:auto}
.dtl-rail::before{content:'';position:absolute;left:31px;top:28px;bottom:28px;width:1px;background:#e6e9ef}
.dtl-node-dot{width:9px;height:9px;border-radius:50%;background:#d8dde5;box-shadow:0 0 0 3px #fff;z-index:1}
.dtl-node.active .dtl-node-dot{background:#2563eb;box-shadow:0 0 0 3px rgba(37,99,235,.16)}
.dtl-pane{overflow-y:auto;padding:20px 28px}
@media (max-width:760px){/* rail 转横向条,隐藏竖线 */}
```
节点 = 月份名 + 红色未读徽标 + 条数;状态 `activeMonth` watch 校准(换数据源重置到首项)。

### 5. 记录条目·双栏小布局
```css
.dtl-entry{display:grid;grid-template-columns:176px minmax(0,1fr);gap:6px 28px;align-items:start}
```
左栏:日期块(`09月18日` 15.5px/800 + `14:32 · 3天前` 12px 灰) → 已读未读点 → 品类色点 → 子类 → `+N分` 19px/800 彩色。
右栏:静默元信息行 → 正文块(小标签+`min-height:54px` 大正文) → 附件 16:9 瓦片(`min-height:128px`,圆角 12px)。

### 6. 静默元信息行
```css
.dtl-chip{display:inline-flex;align-items:baseline;gap:5px;font-size:13px;color:#374151;background:transparent;padding:0}
.dtl-chip i{font-style:normal;font-size:11.5px;color:#9ca3af;letter-spacing:.5px}
```

### 7. Bento 统计卡(工作台范式)
主卡 1.5fr 蓝渐变 `linear-gradient(135deg,#3b82f6,#2563eb,#1d4ed8)` + 40px 白色大数字 + 副文案;小卡 1fr×3 浅灰 `#f2f4f7` 彩色图标芯片;`align-items:stretch;height:100%` 保证同高。数字滚动:
```js
const ease = 1 - Math.pow(1 - p, 3)  // rAF 800ms cubic ease-out
```

### 8. 分段方向控件(去 Element 描边)
```css
:deep(.el-radio-button__inner){border:none!important;background:#f2f4f7;box-shadow:none!important;font-weight:600;padding:4px 12px}
:deep(.el-radio-button__original-radio:checked + .el-radio-button__inner){background:#1f2329;color:#fff}
```

### 9. 视野即读(IntersectionObserver)
`root=滚动容器, threshold:0.4`;进入即调已读接口,**乐观更新+失败回滚**(与小程序同口径);关键词/方向/月份切换后 `nextTick` 重挂,关闭弹窗/卸载时 disconnect。

### 10. 失败图占位
`@error="markThumbFailed(id)"` → `thumbFailed` Set → 模板渲染 Picture 图标文档瓦片(禁用灰底 SVG 占位)。
