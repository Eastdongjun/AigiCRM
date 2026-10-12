<template>
  <!-- 激励查询「查看详情」弹窗（D9 2026-10-04 自 search.vue 拆出，行为不变）：
       吸顶条 + 客户概览 + 检索工具条 + 左侧 iOS 式三联拨盘(年/月/日) + 右侧当前月份内容卡 + 已读观察器 -->
  <el-dialog v-model="visible" width="1320px" top="3vh" :close-on-click-modal="true" destroy-on-close class="detail-dlg">
    <template #header></template>
    <div class="dtl-wrap" v-if="cust">
      <!-- 吸顶操作条：长明细滚动时仍能看到客户与返回入口 -->
      <div class="dtl-bar">
        <span class="dtl-back" @click="visible=false">← 返回列表</span>
        <span class="dtl-bar-name">{{ cust.customerName }}</span>
        <span class="dtl-bar-meta">共 {{ recordTotal }} 条激励记录</span>
        <span class="dtl-bar-pts"><b>{{ cust.totalScore }}</b> 分</span>
      </div>

      <!-- 客户概览：台账式——左身份右合计。整页唯一的大胆元素是右侧超大累计分值(胆量花在一处) -->
      <div class="dtl-hero">
        <div class="dtl-avatar">{{ (cust.customerName || '?').charAt(0) }}</div>
        <div class="dtl-hero-main">
          <div class="dtl-hero-name">{{ cust.customerName }}</div>
          <div class="dtl-hero-tags">
            <span v-if="cust.industry" class="dtl-pill blue">{{ cust.industry }}</span>
            <span class="dtl-pill grey">最近更新 {{ lastUpdateText }}</span>
          </div>
        </div>
        <div class="dtl-hero-score">
          <div class="dtl-score-line"><span class="dtl-score-big">{{ cust.totalScore }}</span><span class="dtl-score-unit">分</span></div>
          <div class="dtl-score-caption">累计积分</div>
          <div class="dtl-hero-sub">
            <span><b>{{ recordTotal }}</b> 条记录</span>
            <span><b>{{ recordsByMonth.length }}</b> 个月份</span>
            <span v-if="unreadCount" class="unread"><b>{{ unreadCount }}</b> 未读</span>
          </div>
        </div>
      </div>

      <!-- 时间轴工具条：记录多了以后的关键检索入口（2026-09-20）——
           关键字搜索(分类/产品/上传人/描述/备注/附件名) + 时间正序(按时间增量)/倒序切换 + 命中计数 -->
      <div class="dtl-toolbar">
        <el-input
          v-model="detailKeyword" size="small" clearable
          placeholder="搜索：分类 / 产品 / 上传人 / 描述 / 备注 / 附件名"
          :prefix-icon="Search" class="dtl-toolbar-search"
        />
        <el-radio-group v-model="detailOrder" size="small">
          <el-radio-button value="desc">最新在前</el-radio-button>
          <el-radio-button value="asc">按时间增量</el-radio-button>
        </el-radio-group>
        <span class="dtl-toolbar-hit">
          {{ detailKeyword ? `命中 ${filteredRecords.length} / ${recordTotal} 条` : `${recordTotal} 条` }}
        </span>
        <el-button v-if="detailKeyword" link size="small" @click="detailKeyword=''">清除</el-button>
      </div>

      <!-- 【2026-09-20 深夜】详情主从骨架：左侧月份时间轴导航 + 右侧当前月份内容卡 -->
      <div v-if="recordsByMonth.length" class="dtl-body">
        <!-- 左栏：iOS 日期选择器式三联拨盘(年/月/日)——各自吸附居中、拨动联动级联，右侧内容跟随 -->
        <aside class="dtl-dial">
          <!-- 实时时间：每秒跳动的时钟 + 回到今天 -->
          <div class="dtl-dial-now">
            <span class="dtl-dial-now-date">{{ nowDateText }}</span>
            <span class="dtl-dial-now-time">{{ nowTimeText }}</span>
            <span class="dtl-dial-today" @click="goToday">回到今天</span>
          </div>
          <div class="dtl-dial-cols">
          <div class="dtl-dial-band"></div>
          <div class="dtl-dial-col">
            <div class="dtl-dial-scroll" :ref="el => (dialEls.year.value = el)" @scroll="onDialScroll('year')">
              <div v-for="y in dialYears" :key="y" class="dtl-dial-item" :class="{active: y===selYear, dim: !dialYearHas(y), today: y===todayY}" :data-key="y" @click="dialPick('year', y)">{{ y }}</div>
            </div>
          </div>
          <div class="dtl-dial-col">
            <div class="dtl-dial-scroll" :ref="el => (dialEls.month.value = el)" @scroll="onDialScroll('month')">
              <div v-for="m in dialMonths" :key="m.m" class="dtl-dial-item" :class="{active: m.m===selMonth, dim: !m.group, today: m.m===todayM}"
                :data-key="String(m.m)" @click="dialPick('month', String(m.m))">
                {{ m.label }}<i v-if="m.group && monthUnread(m.group)" class="w-dot"></i>
              </div>
            </div>
          </div>
          <div class="dtl-dial-col">
            <div class="dtl-dial-scroll" :ref="el => (dialEls.day.value = el)" @scroll="onDialScroll('day')">
              <div v-for="it in dialDays" :key="it.d" class="dtl-dial-item" :class="{active: it.d===selDay, dim: !it.has, today: it.d===todayD}"
                :data-key="String(it.d)" @click="dialPick('day', String(it.d))">{{ it.label }}</div>
            </div>
          </div>
          </div><!-- /dtl-dial-cols -->
        </aside>
        <section class="dtl-pane">
          <div class="dtl-pane-head">
            <span class="dtl-month-label">{{ selYear }}年{{ selMonth }}月<template v-if="selDay"> · {{ selDay }}日</template></span>
            <span class="dtl-month-count">{{ displayItems.length }} 条</span>
          </div>
          <template v-if="displayItems.length">
            <div v-for="r in displayItems" :key="r.id" class="dtl-item" :data-id="r.id" :data-day="recDay(r)">
              <!-- 每个元素独立成块：信息瓦片(时间/阶段/得分/上传人/周期/行业/产品) + 宽块(详情正文/安装备注/附件凭证) -->
              <div class="dtl-ecard">
                <!-- 一行紧凑元信息条：时间/阶段/得分/上传人/周期/行业/产品 收进一行, 版面让给正文与图片 -->
                <div class="dtl-metabar">
                  <span class="dtl-meta time"><i v-if="r.unread" class="dtl-udot"></i>{{ fmtDay(r.createdAt) }} {{ fmtTime(r.createdAt) }}<em v-if="relativeDay(r.createdAt)">{{ relativeDay(r.createdAt) }}</em></span>
                  <span class="dtl-meta stage"><i class="dtl-stage-dot" :style="{ background: scoreColor(clsOf(r)) }"></i>{{ mainOf(r) }}<em>{{ r.categoryName }}</em></span>
                  <span class="dtl-meta dtl-score" :style="{ color: scoreColor(clsOf(r)) }">+{{ r.score }} 分</span>
                  <span class="dtl-meta"><em>上传人</em>{{ r.userName }}</span>
                  <span class="dtl-meta" v-if="r.period"><em>周期</em>{{ r.period }}</span>
                  <span class="dtl-meta" v-if="r.industry"><em>行业</em>{{ r.industry }}</span>
                  <span class="dtl-meta" v-if="r.relatedProducts"><em>相关产品</em>{{ r.relatedProducts }}</span>
                  <span class="dtl-meta" v-if="r.specificProduct"><em>具体产品</em>{{ r.specificProduct }}</span>
                </div>
                <div v-if="r.preInstallQuestion" class="dtl-eblock">
                  <div class="dtl-eblock-label"><el-icon style="vertical-align:-2px;margin-right:4px"><Notebook /></el-icon>{{ detailLabelOf(r) }}</div>
                  <div class="dtl-eblock-text">{{ r.preInstallQuestion }}</div>
                </div>
                <div v-if="r.remark && isTrialRec(r)" class="dtl-eblock remark">
                  <div class="dtl-eblock-label"><el-icon style="vertical-align:-2px;margin-right:4px"><Warning /></el-icon>{{ r.categoryName }}安装备注</div>
                  <div class="dtl-eblock-text">{{ r.remark }}</div>
                </div>
                <div v-if="getFiles(r).length" class="dtl-eblock files-block">
                  <div class="dtl-eblock-label">附件凭证 · {{ getFiles(r).length }} 项</div>
                  <div class="dtl-files">
                  <div v-for="f in getFiles(r)" :key="f.id" class="dtl-file">
                    <template v-if="f.type==='image' && !thumbFailed.has(f.id)">
                      <div class="dtl-img-wrap">
                        <img :src="thumbUrl(f.id)" @click="preview(f.id, f.type, f.label, previewableOf(r))" @error="markThumbFailed(f.id)" />
                        <div v-if="f.tag" class="dtl-file-tag" :style="{color:tagColor(f.tag), borderColor:tagColor(f.tag)}">{{ f.tag }}</div>
                        <div class="dtl-file-acts">
                          <el-button size="small" circle type="primary" @click.stop="preview(f.id, f.type, f.label, previewableOf(r))"><el-icon><ZoomIn /></el-icon></el-button>
                          <el-button size="small" circle @click.stop="downloadFile(f.id, f.label)"><el-icon><Download /></el-icon></el-button>
                        </div>
                      </div>
                    </template>
                    <div v-else-if="f.type==='image'" class="dtl-file-doc" @click="preview(f.id, f.type, f.label, previewableOf(r))">
                      <span v-if="f.tag" class="dtl-file-tag inline" :style="{color:tagColor(f.tag), borderColor:tagColor(f.tag)}">{{ f.tag }}</span>
                      <el-icon :size="26"><Picture /></el-icon>
                      <span class="doc-label">{{ f.label }}</span>
                      <el-button size="small" circle @click.stop="preview(f.id, f.type, f.label, previewableOf(r))"><el-icon><ZoomIn /></el-icon></el-button>
                    </div>
                    <div v-else-if="f.type==='video'" class="dtl-file-vid">
                      <video :src="fileUrl(f.id)" controls preload="metadata"></video>
                      <div class="dtl-file-meta">
                        <span v-if="f.tag" class="dtl-file-tag inline" :style="{color:tagColor(f.tag), borderColor:tagColor(f.tag)}">{{ f.tag }}</span>
                        <span class="doc-label">{{ f.label }}</span>
                        <el-button size="small" circle @click.stop="preview(f.id, f.type, f.label, previewableOf(r))"><el-icon><ZoomIn /></el-icon></el-button>
                        <el-button size="small" circle @click.stop="downloadFile(f.id, f.label)"><el-icon><Download /></el-icon></el-button>
                      </div>
                    </div>
                    <div v-else class="dtl-file-doc" @click="preview(f.id, f.type, f.label, previewableOf(r))">
                      <span v-if="f.tag" class="dtl-file-tag inline" :style="{color:tagColor(f.tag), borderColor:tagColor(f.tag)}">{{ f.tag }}</span>
                      <el-icon :size="26"><Document /></el-icon>
                      <span class="doc-label">{{ f.label }}</span>
                      <el-button size="small" circle @click.stop="downloadFile(f.id, f.label)"><el-icon><Download /></el-icon></el-button>
                    </div>
                  </div>
                </div>
                </div><!-- /files-block -->
              </div><!-- /dtl-ecard -->
            </div>
          </template>
          <div v-else class="dtl-pane-empty">
            <el-icon :size="34"><Calendar /></el-icon>
            <div class="dtl-pane-empty-title">{{ selYear }}年{{ selMonth }}月<template v-if="selDay"> · {{ selDay }}日</template>，此时间无数据</div>
            <div class="dtl-pane-empty-hint">拨动左侧年 / 月 / 日选择盘，亮起的选项才有激励记录</div>
          </div>
        </section>
      </div>
      <el-empty v-else :description="detailKeyword ? '没有匹配「' + detailKeyword + '」的记录' : '该客户暂无激励记录'" :image-size="90">
        <el-button v-if="detailKeyword" size="small" @click="detailKeyword=''">清除搜索</el-button>
      </el-empty>

      <!-- 底部操作条 -->
      <div class="dtl-footer">
        <span class="dtl-footer-hint">共 {{ recordTotal }} 条记录 · {{ recordsByMonth.length }} 个月份</span>
        <el-button size="small" @click="visible=false">关闭</el-button>
      </div>
    </div>
  </el-dialog>
</template>

<script setup>
// 客户激励详情弹窗：月份时间轴检索 + 三联拨盘导航 + 已读/未读观察器，全部状态自持；
// 仅"预览文件 / 标记缩略图失败 / 开关弹窗"经 props/emits 与页面交互。
import { ref, computed, watch, onBeforeUnmount, nextTick } from 'vue'
import { markIncentiveRead } from '@/api/incentive'
import { filterRecordsByKeyword, groupRecordsByMonth, relativeDay } from '@/utils/time-label'
import { Search, ZoomIn, Download, Document, Picture, Calendar, Notebook, Warning } from '@element-plus/icons-vue'
import { fileUrl, thumbUrl, downloadFile, getFiles, getMain, getCls, scoreColor, tagColor } from './search-helpers'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  // 当前客户（含 records 数组）
  cust: { type: Object, default: null },
  // 分类树扁平映射（id → 分类对象），用于大类名/品类码定位
  catMap: { type: Object, default: () => ({}) },
  // 页面级共享的"缩略图加载失败"文件 id 集合
  thumbFailed: { type: Set, default: () => new Set() },
})
const emit = defineEmits(['update:modelValue', 'preview', 'thumb-failed'])

const visible = computed({ get: () => props.modelValue, set: v => emit('update:modelValue', v) })

const mainOf = (r) => getMain(props.catMap, r)
const clsOf = (r) => getCls(props.catMap, r)
// 打开灯箱 / 标记缩略图失败 → 交给页面统一处理（灯箱与失败标记都是页面级共享）
function preview(id, type, label, gallery) { emit('preview', id, type, label, gallery) }
function markThumbFailed(id) { emit('thumb-failed', id) }

const recordTotal = computed(() => props.cust?.records?.length || 0)
const unreadCount = computed(() => (props.cust?.records || []).filter(r => r.unread).length)
// 详情弹窗概览用：取该客户最新一条记录的时间（无记录时显示 —）
const lastUpdateText = computed(() => { const r = latestRecord(props.cust); return r ? fmtDt(r.createdAt) : '—' })
// 取客户记录中最新一条（排序已按品类，不与时间线顺序绑定）
function latestRecord(c) { let best = null; for (const r of (c?.records || [])) { if (!best || String(r.createdAt || '') > String(best.createdAt || '')) best = r } return best }
// 记录内全部附件进灯箱：图片/视频直接预览, 文档按种类预览(pdf/text)或下载兜底
function previewableOf(r) { return getFiles(r).map(f => ({ id: f.id, type: f.type, label: f.label, tag: f.tag })) }
// 详情字段标签 = 子分类名 + 阶段词（与上传弹窗一致）
function detailLabelOf(r) { const code = clsOf(r); const sub = r.categoryName || ''; if (code === 'plan') return sub + '文件详情描述'; if (code === 'visit' || code === 'exchange') return sub + '详情描述'; return sub + '安装详情' }
// 是否产品试点记录（安装备注仅试点有）
function isTrialRec(r) { return (clsOf(r) || '').startsWith('trial') }
function fmtDt(t) { if (!t) return ''; const d = new Date(t); return d.getFullYear() + '-' + (d.getMonth() + 1).toString().padStart(2, '0') + '-' + d.getDate().toString().padStart(2, '0') + ' ' + d.getHours().toString().padStart(2, '0') + ':' + d.getMinutes().toString().padStart(2, '0') }
// 详情左栏日期块：日(粗) + 时刻(灰) 分行显示
function fmtDay(t) { if (!t) return ''; const d = new Date(t); return (d.getMonth() + 1).toString().padStart(2, '0') + '月' + d.getDate().toString().padStart(2, '0') + '日' }
function fmtTime(t) { if (!t) return ''; const d = new Date(t); return d.getHours().toString().padStart(2, '0') + ':' + d.getMinutes().toString().padStart(2, '0') }

// 时间轴：记录按月倒序分组（展示口径，不改动接口返回顺序）
// 【2026-09-20 详情工具条】记录检索 + 时间轴方向；打开新客户详情时自动复位
const detailKeyword = ref('')
const detailOrder = ref('desc')
const filteredRecords = computed(() => filterRecordsByKeyword(props.cust?.records || [], detailKeyword.value))
const recordsByMonth = computed(() => groupRecordsByMonth(filteredRecords.value, detailOrder.value))
// 【2026-09-20 深夜】主从骨架状态: 左栏时间轴选中的月份 → 右栏只渲染该月内容
const selYear = ref('')
const selMonth = ref(1)  // 1-12, 拨盘位置(不要求该月有数据)
const selDay = ref(0)     // 0 = 整月, 拨盘位置
// 选中 = 拨盘位置: 年+月定位组, 无则 null(右栏出"此时间无数据"); 有则再按日过滤
const monthKeyStr = computed(() => selYear.value + '-' + String(selMonth.value).padStart(2, '0'))
const activeGroup = computed(() => recordsByMonth.value.find(g => g.key === monthKeyStr.value) || null)
const displayItems = computed(() => {
  const g = activeGroup.value
  if (!g || !g.items || !g.items.length) return []
  if (!selDay.value) return g.items
  return g.items.filter(r => recDay(r) === selDay.value)
})
function monthUnread(g) { return (g.items || []).filter(r => r.unread).length }
// 实时时间：每秒跳动的时钟(拨盘即实时日历, 年/月/日盘上标记今天)
const now = ref(new Date())
const nowTimer = setInterval(() => { now.value = new Date() }, 1000)
onBeforeUnmount(() => { clearInterval(nowTimer) })
const nowDateText = computed(() => { const d = now.value; return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0') + ' 周' + '日一二三四五六'[d.getDay()] })
const nowTimeText = computed(() => now.value.toTimeString().slice(0, 8))
const todayY = computed(() => String(now.value.getFullYear()))
const todayM = computed(() => now.value.getMonth() + 1)
const todayD = computed(() => now.value.getDate())
function goToday() { selYear.value = todayY.value; selMonth.value = todayM.value; selDay.value = todayD.value }
// 三联拨盘数据：年列表 / 所选年的月份 / 所选月的可选日(0=整月)
const dialEls = { year: ref(null), month: ref(null), day: ref(null) }
const wheelGroups = computed(() => {
  const out = []
  for (const g of recordsByMonth.value) {
    const m = /^(\d{4})年(.+)$/.exec(g.label || '')
    const year = m ? m[1] : '其他'
    let last = out[out.length - 1]
    if (!last || last.year !== year) { last = { year, months: [] }; out.push(last) }
    last.months.push(g)
  }
  return out
})
// 年份盘自增：从最早记录年份到当前系统年份连续生成(每年跨年自动多一年), 无数据年份置暗
const dialYears = computed(() => {
  const nowY = new Date().getFullYear()
  const dataYears = wheelGroups.value.map(yg => Number(yg.year)).filter(y => !isNaN(y))
  const min = dataYears.length ? Math.min(...dataYears, nowY) : nowY
  const max = dataYears.length ? Math.max(...dataYears, nowY) : nowY
  const out = []
  for (let y = min; y <= max; y++) out.push(String(y))
  return out
})
function dialYearHas(y) { return wheelGroups.value.some(yg => yg.year === y) }
// 月盘展示全 12 个月(无数据的月份置灰不可选)——条目足够多, 拨盘才真正可拨动(iOS 同款)
const dialMonths = computed(() => {
  const yg = wheelGroups.value.find(y => y.year === selYear.value)
  const map = new Map((yg?.months || []).map(g => [Number(monthShort(g).replace('月', '')), g]))
  return Array.from({ length: 12 }, (_, i) => ({ m: i + 1, label: (i + 1) + '月', group: map.get(i + 1) || null }))
})
// 记录日期解析：兼容 "2026-08-15 10:30" / ISO "T" 分隔 / 时间戳三种格式
function recDay(r) {
  const t = r?.createdAt
  if (t == null) return NaN
  const str = String(t).replace(/-/g, '/').replace('T', ' ')
  const d = new Date(t instanceof Number ? t : (isNaN(Number(str)) ? str : Number(t)))
  return d.getDate()
}
// 日历盘: 整月 + 该月 1..月末(按拨盘选中的年+月生成完整日历, 与是否有记录无关; 闰年由 Date 正确处理)
const dialDays = computed(() => {
  const y = Number(selYear.value), m = Number(selMonth.value)
  if (!y || !m) return [{ d: 0, label: '整月', has: true }]
  const total = new Date(y, m, 0).getDate()
  const g = recordsByMonth.value.find(x => x.key === monthKeyStr.value)
  const withData = new Set((g?.items || []).map(recDay).filter(d => !isNaN(d)))
  return [{ d: 0, label: '整月', has: true },
    ...Array.from({ length: total }, (_, i) => ({ d: i + 1, label: (i + 1) + '日', has: withData.has(i + 1) }))]
})
function monthShort(g) { const m = /^(\d{4})年(.+)$/.exec(g.label || ''); return m ? m[2] : g.label }
// 拨盘视觉：条目随距中心距离缩放淡出(iOS 拨盘质感)
const dialRaf = {}; const dialTimers = {}
function styleDial(col) {
  const el = dialEls[col].value; if (!el) return
  const b = el.getBoundingClientRect(); const cy = b.top + b.height / 2
  el.querySelectorAll('.dtl-dial-item').forEach(it => {
    if (it.classList.contains('dim') && !it.classList.contains('active')) { it.style.opacity = '.16'; it.style.transform = 'scale(.92)'; return }
    const r = it.getBoundingClientRect()
    const t = Math.max(0, 1 - Math.abs(r.top + r.height / 2 - cy) / 200)
    it.style.opacity = (0.25 + 0.75 * t).toFixed(2)
    it.style.transform = 'scale(' + (0.9 + 0.1 * t).toFixed(3) + ')'
  })
}
// 居中填充: CSS 百分比竖向 padding 按宽度解析不可用, 按列高 JS 设置(首尾条目才能滚到中心)
function dialPad() {
  for (const col of ['year', 'month', 'day']) {
    const el = dialEls[col].value; if (!el) continue
    const pad = Math.max(0, el.clientHeight / 2 - 21)
    el.style.paddingTop = pad + 'px'
    el.style.paddingBottom = pad + 'px'
  }
}
function centerKey(col) {
  const el = dialEls[col].value; if (!el) return null
  const b = el.getBoundingClientRect(); const cy = b.top + b.height / 2
  let best = null, bd = Infinity
  el.querySelectorAll('.dtl-dial-item').forEach(it => {
    const r = it.getBoundingClientRect()
    const d = Math.abs(r.top + r.height / 2 - cy)
    if (d < bd) { bd = d; best = it }
  })
  return best
}
// 停稳后取居中条目为选中值，并按列级联(年→月重排, 月→日重排)
function onDialScroll(col) {
  if (!dialRaf[col]) dialRaf[col] = requestAnimationFrame(() => { dialRaf[col] = 0; styleDial(col) })
  clearTimeout(dialTimers[col])
  dialTimers[col] = setTimeout(() => {
    if (Date.now() < dialFollowLock) return   // 内容跟随引起的程序滚动不改选中
    dialTimers[col] = null
    const el = centerKey(col)
    if (!el) return
    const k = el.dataset.key
    if (col === 'year' && k !== selYear.value) { selYear.value = k; selDay.value = 0 }
    else if (col === 'month' && Number(k) !== selMonth.value) { selMonth.value = Number(k); selDay.value = 0 }
    else if (col === 'day' && Number(k) !== selDay.value) selDay.value = Number(k)
  }, 150)
}
function dialPick(col, key) {
  if (col === 'year') { if (key !== selYear.value) { selYear.value = key; selDay.value = 0 } }
  else if (col === 'month') { if (Number(key) !== selMonth.value) { selMonth.value = Number(key); selDay.value = 0 } }
  else if (col === 'day') { selDay.value = Number(key) }
}
// 【双向跟随】右侧内容滚动 → 左侧"日"拨片自动对位到视野顶部记录的日期
// 仅整月模式(selDay=0)下移动拨片视觉位置, 不改筛选; 手动拨日期才过滤
let dialFollowLock = 0
function followDialDay(day) {
  if (!day || isNaN(day) || selDay.value !== 0) return
  const el = dialEls.day.value
  const it = el?.querySelector('.dtl-dial-item[data-key="' + day + '"]')
  if (!it) return
  dialFollowLock = Date.now() + 450        // 程序滚动窗口期内, 拨盘停稳不改变选中
  it.scrollIntoView({ block: 'center' })
}
let paneObserver = null
function setupPaneObserver() {
  destroyPaneObserver()
  const pane = document.querySelector('.detail-dlg .dtl-pane')
  if (!pane || typeof IntersectionObserver === 'undefined') return
  paneObserver = new IntersectionObserver((entries) => {
    const top = entries.filter(e => e.isIntersecting)
      .sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top)[0]
    if (top) followDialDay(Number(top.target.dataset.day))
  }, { root: pane, threshold: 0.2 })
  pane.querySelectorAll('.dtl-item[data-day]').forEach(el => paneObserver.observe(el))
}
function destroyPaneObserver() { if (paneObserver) { paneObserver.disconnect(); paneObserver = null } }
// 选中值被外部改变(级联/重置/点选)后, 对应拨盘无动画对位并刷新视觉
function alignDial(col, key) {
  nextTick(() => {
    const el = dialEls[col].value
    const it = el?.querySelector('.dtl-dial-item[data-key="' + key + '"]')
    if (!it || !el) return
    const r = it.getBoundingClientRect(), b = el.getBoundingClientRect()
    if (Math.abs(r.top + r.height / 2 - (b.top + b.height / 2)) > 4) it.scrollIntoView({ block: 'center' })
    styleDial(col)
  })
}
watch(selYear, () => alignDial('year', selYear.value))
watch(selMonth, () => { alignDial('month', String(selMonth.value)) })
watch(selDay, () => alignDial('day', String(selDay.value)))
onBeforeUnmount(() => Object.values(dialTimers).forEach(clearTimeout))
// 月份列表变化(换客户/搜索/换向)时校准选中月份到首项
watch(recordsByMonth, (list) => {
  const first = list[0]
  const ym = /^(\d{4})年(\d{1,2})月/.exec(first?.label || '')
  selYear.value = ym ? ym[1] : String(new Date().getFullYear())
  selMonth.value = ym ? Number(ym[2]) : new Date().getMonth() + 1
  selDay.value = 0
  nextTick(dialPad)
  alignDial('year', selYear.value); alignDial('month', String(selMonth.value)); alignDial('day', '0')
}, { immediate: true })
// 搜索/换向会重建记录节点，需要重新挂监听；关弹窗/卸载时清理
watch([detailKeyword, detailOrder, selYear, selMonth, selDay], () => { if (visible.value) nextTick(() => { setupReadObserver(); setupPaneObserver() }) })

// 弹窗开关：打开即复位检索与方向并初始化拨盘/观察器；关闭时拆观察器（原页面 showDetail 逻辑）
watch(() => props.modelValue, (v) => {
  if (!v) { destroyReadObserver(); destroyPaneObserver(); return }
  detailKeyword.value = ''; detailOrder.value = 'desc'
  // 【2026-09-20】已读/未读与小程序同逻辑：不再打开即全标已读，
  // 记录滚动进入视野时逐条标记（乐观更新+失败回滚），未读数实时反映在概览胶囊上
  nextTick(() => {
    dialPad()
    alignDial('year', selYear.value); alignDial('month', String(selMonth.value)); alignDial('day', '0')
    setupReadObserver()
    setupPaneObserver()
  })
})

// 视野即读：IntersectionObserver 监听时间轴内每条记录，进入视野 40% 即调用已读接口
let readObserver = null
function setupReadObserver() {
  destroyReadObserver()
  const wrap = document.querySelector('.detail-dlg .dtl-pane')
  if (!wrap || typeof IntersectionObserver === 'undefined') return
  readObserver = new IntersectionObserver((entries) => {
    for (const en of entries) {
      if (!en.isIntersecting) continue
      readObserver.unobserve(en.target)
      markVisibleRead(Number(en.target.dataset.id))
    }
  }, { root: wrap, threshold: 0.4 })
  wrap.querySelectorAll('.dtl-item[data-id]').forEach(el => readObserver.observe(el))
}
function destroyReadObserver() { if (readObserver) { readObserver.disconnect(); readObserver = null } }
function markVisibleRead(id) {
  const c = props.cust
  const rec = c && (c.records || []).find(r => Number(r.id) === id)
  if (!rec || !rec.unread) return
  rec.unread = false                                  // 乐观更新（与小程序 markRead 同口径）
  c.unread = (c.records || []).some(r => r.unread)
  markIncentiveRead(id).catch(() => { rec.unread = true; c.unread = true })  // 失败回滚
}
onBeforeUnmount(() => { destroyReadObserver(); destroyPaneObserver() })
</script>

<style scoped>
/* ===== 激励查询「查看详情」弹窗（2026-09-20 高级感重设计：灰画布 + 白浮层卡 + 静默元信息） ===== */
.detail-dlg :deep(.el-dialog){border-radius:20px;overflow:hidden;box-shadow:0 24px 64px rgba(15,23,42,.22)}
.detail-dlg :deep(.el-dialog__header){display:none}
.detail-dlg :deep(.el-dialog__body){padding:0;background:#f5f7fa}
.dtl-wrap{max-width:1280px;margin:0 auto;background:transparent;display:flex;flex-direction:column;max-height:92vh}

/* 吸顶操作条 */
.dtl-bar{position:sticky;top:0;z-index:6;display:flex;align-items:center;gap:12px;padding:12px 28px;
  background:rgba(255,255,255,.94);backdrop-filter:blur(10px);border-bottom:1px solid #eef0f3}
.dtl-back{font-size:13px;color:#4e5969;cursor:pointer;display:inline-flex;align-items:center;gap:4px;
  padding:4px 10px;border-radius:8px;transition:all .15s;flex-shrink:0}
.dtl-back:hover{color:var(--el-color-primary);background:var(--el-color-primary-light-9)}
.dtl-bar-name{font-size:14px;font-weight:600;color:#1f2329;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.dtl-bar-meta{font-size:12px;color: var(--text-muted);flex-shrink:0}
.dtl-bar-pts{margin-left:auto;font-size:12px;color: var(--text-muted);flex-shrink:0;
  background:#f2f6ff;border-radius: var(--radius-pill);padding:3px 12px}
.dtl-bar-pts b{font-size:15px;color:var(--el-color-primary);margin-right:2px}

/* 客户概览：白浮层卡，排版即层级 */
.dtl-hero{display:flex;align-items:center;gap:18px;margin:0;padding:24px 28px 22px;
  background:linear-gradient(118deg,#0d2352 0%,#153e8f 52%,#1d4ed8 100%);border-bottom:none}
.dtl-avatar{width:58px;height:58px;border-radius:18px;flex-shrink:0;color:#fff;font-size:24px;font-weight:700;
  display:flex;align-items:center;justify-content:center;background:rgba(255,255,255,.16);
  border:1px solid rgba(255,255,255,.28);backdrop-filter:blur(6px)}
.dtl-hero-main{flex:1;min-width:0}
.dtl-hero-name{font-size:22px;font-weight:800;color:#fff;line-height:1.3;margin-bottom:9px;letter-spacing:-.2px;
  overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.dtl-hero-tags{display:flex;align-items:center;gap:8px;flex-wrap:wrap}
.dtl-pill{font-size:12px;border-radius: var(--radius-pill);padding:3px 12px;border:none;white-space:nowrap}
.dtl-pill.blue{color:#1d6fd0;background:#e8f2ff;border-color:transparent}
.dtl-pill.grey{color:#6b7280;background:#f4f5f7;border-color:transparent}
.dtl-hero .dtl-pill{background:rgba(255,255,255,.14);border:1px solid rgba(255,255,255,.22)}
.dtl-hero .dtl-pill.blue{color:#dbeafe;background:rgba(255,255,255,.14)}
.dtl-hero .dtl-pill.grey{color:rgba(255,255,255,.82);background:rgba(255,255,255,.10)}
/* 台账式概览合计：整页唯一的大胆元素(56px 超大分值)，副行静默退后 */
.dtl-hero-score{margin-left:auto;flex-shrink:0;text-align:right;display:flex;flex-direction:column;align-items:flex-end}
.dtl-score-line{display:flex;align-items:baseline;gap:5px}
.dtl-score-big{font-size:54px;font-weight:800;color:#fff;line-height:1;letter-spacing:-1.5px;font-variant-numeric:tabular-nums;
  text-shadow:0 2px 12px rgba(13,35,82,.35)}
.dtl-score-unit{font-size:15px;font-weight:700;color:#f5b94b}
.dtl-score-caption{font-size:12px;color:rgba(255,255,255,.72);margin-top:7px;letter-spacing:3px}
.dtl-hero-sub{display:flex;gap:20px;margin-top:12px;font-size:12px;color:rgba(255,255,255,.68)}
.dtl-hero-sub span{display:inline-flex;align-items:baseline;padding-left:12px;border-left:1px solid rgba(255,255,255,.18)}
.dtl-hero-sub span:first-child{padding-left:0;border-left:none}
.dtl-hero-sub b{font-size:15px;font-weight:700;color:#fff;margin-right:4px;font-variant-numeric:tabular-nums}
.dtl-hero-sub .unread b,.dtl-hero-sub .unread{color:#f5b94b}

/* 【2026-09-20】详情工具条：灰画布上的白色工具胶囊（搜索 + 时间方向切换 + 命中计数） */
.dtl-toolbar{display:flex;align-items:center;gap:10px;margin:14px 20px 0;padding:10px 14px;flex-wrap:wrap;
  background:#fff;border:1px solid #e5e9f0;border-radius:14px;
  box-shadow:0 1px 2px rgba(16,24,40,.03),0 6px 18px rgba(16,24,40,.05)}
.dtl-toolbar-search{width:280px}
.dtl-toolbar-hit{font-size:12px;color:#6b7280;white-space:nowrap}
/* 方向切换统一成分段控件（无描边软填充，选中深底白字），与整页"去线条"语言一致 */
.dtl-toolbar :deep(.el-radio-button__inner){border:none!important;background:#f2f4f7;color:#4e5969;
  box-shadow:none!important;font-weight:600;padding:4px 12px}
.dtl-toolbar :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner){background:#1f2329;color:#fff}
.dtl-toolbar :deep(.el-radio-button:first-child .el-radio-button__inner){border-radius:8px 0 0 8px}
.dtl-toolbar :deep(.el-radio-button:last-child .el-radio-button__inner){border-radius:0 8px 8px 0}

/* 【2026-09-20 深夜】主从骨架：左月份时间轴导航(竖线+节点) + 右当前月份内容卡 */
.dtl-body{display:grid;grid-template-columns:250px minmax(0,1fr);gap:14px;flex:1;min-height:0;
  margin:14px 20px 0}
/* iOS 日期选择器式三联拨盘(年/月/日)：三列各自吸附居中 + 贯通选中带 + 距离缩放淡出(JS 驱动) */
.dtl-dial{position:relative;background:#fff;border:1px solid #e5e9f0;border-radius:14px;min-height:0;
  box-shadow:0 1px 2px rgba(16,24,40,.03),0 6px 18px rgba(16,24,40,.05);display:flex;flex-direction:column;overflow:hidden}
.dtl-dial-now{display:flex;align-items:baseline;gap:9px;padding:12px 14px 9px;border-bottom:1px solid #eef0f3;flex-shrink:0}
.dtl-dial-now-date{font-size:12px;color:#6b7280;font-variant-numeric:tabular-nums}
.dtl-dial-now-time{font-size:20px;font-weight:800;color:#1d4ed8;font-variant-numeric:tabular-nums;letter-spacing:.5px;line-height:1}
.dtl-dial-today{margin-left:auto;font-size:12px;font-weight:600;color:#2563eb;cursor:pointer;padding:3px 9px;
  border-radius: var(--radius-pill);background:#eff4fe;transition:background .15s}
.dtl-dial-today:hover{background:#dbe6fb}
.dtl-dial-cols{position:relative;display:flex;gap:2px;padding:8px 4px;flex:1;min-height:0}
.dtl-dial-band{position:absolute;left:10px;right:10px;top:50%;height:42px;transform:translateY(-50%);
  background:#eaf1fe;border:1px solid #dbe6fb;border-radius:12px;pointer-events:none;z-index:0}
.dtl-dial-col{flex:1;min-width:0;display:flex;flex-direction:column}
.dtl-dial-scroll{flex:1;overflow-y:auto;scroll-snap-type:y mandatory;overscroll-behavior:contain;
  padding:0;scrollbar-width:none;position:relative;z-index:1;text-align:center}
.dtl-dial-scroll::-webkit-scrollbar{display:none}
.dtl-dial-item{position:relative;height:42px;scroll-snap-align:center;display:flex;align-items:center;justify-content:center;gap:4px;
  cursor:pointer;opacity:.25;transform:scale(.9);transition:opacity .15s,transform .15s;
  font-size:15px;font-weight:700;color:#111827;font-variant-numeric:tabular-nums}
.dtl-dial-item.today::after{content:'';position:absolute;bottom:5px;left:50%;transform:translateX(-50%);
  width:4px;height:4px;border-radius:50%;background:#2563eb}
.dtl-dial-item.active{font-weight:800;color:#153e8f}
.dtl-dial-item:hover{color:#1d4ed8}
.dtl-dial-item.dim{opacity:.16}
.dtl-pane-empty{display:flex;flex-direction:column;align-items:center;justify-content:center;gap:8px;
  padding:70px 0;color:#8a94a3}
.dtl-pane-empty-title{font-size:15px;font-weight:600;color:#6b7280}
.dtl-pane-empty-hint{font-size:12px;color:#9ca3af}
.w-dot{width:5px;height:5px;border-radius:50%;background:#e11d48;flex-shrink:0}
.dtl-pane{overflow-y:auto;padding:0 6px 6px}
.dtl-pane::-webkit-scrollbar{width:6px}
.dtl-pane::-webkit-scrollbar-thumb{background:#d3d9e2;border-radius: var(--radius-pill)}
.dtl-pane::-webkit-scrollbar-track{background:transparent}
.dtl-pane-head{display:flex;align-items:baseline;gap:10px;padding:8px 6px 14px}
.dtl-month-label{font-size:16px;font-weight:800;color:#111827;background:transparent;border-radius:0;padding:0;letter-spacing:.2px}
.dtl-month-count{font-size:12px;color:#6b7280}
.dtl-item{position:relative;margin-bottom:14px}
.dtl-item:last-child{margin-bottom:4px}
/* 【2026-09-20 深夜V】每个元素独立成块：信息瓦片网格 + 宽块，整条记录一张独立定义卡 */
.dtl-ecard{background:#fff;border:1px solid #e5e9f0;border-radius: var(--radius-lg);padding:22px 24px;
  box-shadow:0 1px 2px rgba(16,24,40,.03),0 6px 18px rgba(16,24,40,.05);
  display:flex;flex-direction:column;gap:16px}
/* 一行紧凑元信息条：标签灰小字内联, 值深色; 时间/得分加重, 未读红点 */
.dtl-metabar{display:flex;flex-wrap:wrap;align-items:baseline;gap:6px 20px;padding:2px 2px 0}
/* 【#6 A 2026-10-12】metabar 固定两行高: 各记录 meta 数量不同导致换行数不同, 图片区顶边随之下移错位;
   固定最小高度让所有记录卡片的附件区顶边落在同一横线 */
.dtl-metabar{min-height:48px;align-content:flex-start}
.dtl-meta{display:inline-flex;align-items:baseline;gap:5px;font-size:13px;font-weight:600;color:#1f2937;white-space:nowrap}
.dtl-meta em{font-style:normal;font-size:12px;font-weight:500;color:#8a94a3;letter-spacing:.5px}
.dtl-meta.time{font-size:16px;font-weight:800;color:#1d4ed8;font-variant-numeric:tabular-nums;letter-spacing:.2px}
.dtl-meta.time em{font-weight:500;color:#8a94a3;margin-left:2px}
.dtl-meta.stage em{font-weight:600;color:#6b7280}
.dtl-score{font-size:16px;font-weight:800;font-variant-numeric:tabular-nums;letter-spacing:-.2px}
.dtl-stage-dot{display:inline-block;width:8px;height:8px;border-radius:50%;margin-right:6px;flex-shrink:0;align-self:center}
.dtl-udot{display:inline-block;width:7px;height:7px;border-radius:50%;background:#e11d48;
  box-shadow:0 0 0 3px rgba(225,29,72,.12);margin-right:6px;align-self:center;flex-shrink:0}
.dtl-eblock{background:#f8fafd;border:1px solid #edf1f7;border-radius:12px;padding:15px 20px}
.dtl-eblock.remark{background:#fffaf2;border-color:#f3e4c8}
.dtl-eblock-label{font-size:12px;font-weight:600;color:#4b5563;margin-bottom:6px;letter-spacing:1px}
.dtl-eblock.remark .dtl-eblock-label{color:#a16207}
.dtl-eblock-text{font-size:15px;color:#111827;line-height:1.9;white-space:pre-wrap;word-break:break-word}
/* 【#6 A 2026-10-12】问询/备注限三行: 可选块有无/长短是图片区顶边错位的另一来源, 收敛行数把差异压到一行内 */
.dtl-ecard > .dtl-eblock:not(.files-block) .dtl-eblock-text{display:-webkit-box;-webkit-line-clamp:3;-webkit-box-orient:vertical;overflow:hidden}
@media (max-width: 760px){
  .dtl-body{grid-template-columns:1fr}
  .dtl-dial{max-height:230px}
}

/* 附件网格：16:9 瓦片压缩高度，避免失败占位时空洞感；网格保底一行的 */
.dtl-files{display:grid;grid-template-columns:repeat(auto-fill,minmax(300px,1fr));gap:16px}
.files-block{padding:16px 20px}
.dtl-file{min-width:0}
.dtl-file-meta{display:flex;align-items:center;gap:8px;margin-top:6px}
.dtl-img-wrap{position:relative;border-radius:14px;overflow:hidden;background:#f2f4f8}
.dtl-file img{width:100%;aspect-ratio:3/2;object-fit:cover;cursor:pointer;display:block;transition:transform .25s}
.dtl-file img:hover{transform:scale(1.03)}
.dtl-file-tag{position:absolute;top:8px;left:8px;font-size:12px;font-weight:600;background:rgba(255,255,255,.94);
  padding:2px 8px;border-radius: var(--radius-pill);border:1px solid transparent;white-space:nowrap;z-index:1}
.dtl-file-tag.inline{position:static;background:#fff;flex-shrink:0}
.dtl-file-vid{position:relative;display:flex;flex-direction:column}
.dtl-file-vid video{width:100%;aspect-ratio:3/2;border-radius:12px;background:#0b1020;display:block;object-fit:cover}
/* 【#6 B 2026-10-12】视频瓦片文件名/按钮收进画面底部悬浮条: 原先 meta 单占一行使视频瓦片比图片瓦片高一截,
   同行图片/视频混排底边不齐; 现所有瓦片统一 3:2, 底边严格对齐 */
.dtl-file-vid .dtl-file-meta{position:absolute;left:0;right:0;bottom:0;margin-top:0;padding:22px 10px 8px;
  background:linear-gradient(180deg,rgba(11,16,32,0),rgba(11,16,32,.82));border-radius:0 0 12px 12px}
.dtl-file-vid .dtl-file-meta .doc-label{color:#fff}
.dtl-file-vid .dtl-file-meta .el-button{background:rgba(255,255,255,.16);border-color:rgba(255,255,255,.35)}
.dtl-file-doc{position:relative;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:8px;
  aspect-ratio:3/2;padding:12px;border:none;border-radius:12px;background:#f7f8fa;
  color:#646a73;font-size:13px;text-align:center;cursor:pointer;transition:all .15s}
.dtl-file-doc:hover{background:#e8f2ff;color:#1d6fd0}
.dtl-file-doc .dtl-file-tag.inline{position:absolute;top:8px;left:8px}
.doc-label{color:#1f2329;font-weight:500;font-size:13px;flex:1;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.dtl-file-acts{position:absolute;top:8px;right:8px;display:flex;gap:6px;opacity:0;transition:opacity .2s;z-index:2}
.dtl-img-wrap:hover .dtl-file-acts{opacity:1}

/* 底部操作条：画布上透明收尾 */
.dtl-footer{display:flex;align-items:center;justify-content:space-between;gap:12px;margin:14px 20px 16px;
  padding:10px 6px 0;background:transparent;border-top:1px solid #e2e8f0}
.dtl-footer-hint{font-size:12px;color:#6b7280}
</style>
