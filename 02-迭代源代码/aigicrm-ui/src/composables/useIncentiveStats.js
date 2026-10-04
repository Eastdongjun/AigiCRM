// 激励统计页共享态（C4 拆组件 2026-08-09）
// 页面级 provide/inject（per-route）：incentive/stats.vue 实例化一次并 provide，
// 概览卡/统计表/个人排行/部门排行 4 个视图组件 inject 同一上下文。
// 共享内容：周期/Tab/加载态 + 分类树/个人记录/全员聚合/排行数据 + 各计算派生 + 导出动作。
import { ref, computed, inject } from 'vue'
import { getCategoryTree, getRecords, getCategoryStats, getCustomerRecords, getScoreboard } from '@/api/incentive'
import { cachedGet } from '@/utils/layout-cache'
import { filterCustomerRankRows } from '@/utils/customer-rank'
import { useUserStore } from '@/store/user'
import { buildPeriodOptions, periodQueryValue } from '@/utils/incentive-period'

export const IncentiveStatsKey = Symbol('incentiveStats')

/** 子组件注入父级 incentive/stats.vue 提供的共享上下文 */
export function injectIncentiveStats() {
  const ctx = inject(IncentiveStatsKey)
  if (!ctx) throw new Error('injectIncentiveStats 必须在 incentive/stats.vue 的组件内使用')
  return ctx
}

export function useIncentiveStats() {
  const userStore = useUserStore()
  const loading = ref(false)
  // 【2026-09-12】周期口径统一（选项含「全年」、默认「全部」）——原来默认 '2026上半年' 且每次请求都带该参数，
  // 而库内数据用的是 '2026全年' ⇒ 页面必然 0 条。见 utils/incentive-period.js。
  const period = ref('')
  const activeTab = ref('table')
  const records = ref([])            // 个人记录(当前登录人) — 仅供顶部卡片
  const catStat = ref({ byCategory: [], totalFiles: 0, totalScore: 0 })  // 全员聚合 — 供激励统计表
  const categories = ref([])
  const scoreboard = ref([])
  // 【2026-09-23 需求】个人排行: 按人员统计四项激励(后端 /incentive/scoreboard 按人聚合)
  const personBoard = ref([])
  const personPageNum = ref(1)
  const personPageSize = ref(15)
  const pagedPersonBoard = computed(() => personBoard.value.slice((personPageNum.value - 1) * personPageSize.value, personPageNum.value * personPageSize.value))
  // 客户排行矩阵分页（前端切片）
  const pageNum = ref(1)
  const pageSize = ref(15)
  const pagedScoreboard = computed(() => scoreboard.value.slice((pageNum.value - 1) * pageSize.value, pageNum.value * pageSize.value))
  const periodOptions = buildPeriodOptions()
  const catColors = ['cat-blue', 'cat-orange', 'cat-red', 'cat-yellow']
  const catIcons = { visit: 'location', exchange: 'chat', plan: 'document', trial: 'box' }

  // ---- 统计 ----
  const totalCategories = computed(() => categories.value.filter(c => c.parentId !== 0).length)
  const shareableCount = computed(() => {
    return categories.value.filter(c => c.parentId !== 0 && c.categoryName?.includes('共享')).length
  })
  // 顶部卡片：个人口径（当前登录人本周期 文件数 / 积分）
  const fileCountOf = (r) => {
    let n = 0
    if (r.filePreId) n++
    if (r.fileMidId) n++
    if (r.filePostId) n++
    if (n === 0 && r.fileId) n = 1
    return n
  }
  const totalRecords = computed(() => records.value.reduce((s, r) => s + fileCountOf(r), 0))
  const totalScore = computed(() => records.value.reduce((s, r) => s + (r.score || 0), 0))

  // 激励统计表合计：全员口径
  const tableTotalFiles = computed(() => catStat.value.totalFiles || 0)
  const tableTotalScore = computed(() => catStat.value.totalScore || 0)
  const tableTotalPre = computed(() => catStat.value.totalPre || 0)
  const tableTotalMid = computed(() => catStat.value.totalMid || 0)
  const tableTotalPost = computed(() => catStat.value.totalPost || 0)

  // 激励统计表：全员口径 categoryId -> { fileCount, totalScore, preCount, midCount, postCount }
  const catStatMap = computed(() => {
    const m = {}
    for (const s of catStat.value.byCategory || []) m[s.categoryId] = s
    return m
  })

  // 客户排行矩阵表头：按大类分组的子分类列（模板：激励政策分列）
  const rankParents = computed(() => {
    return categories.value
      .filter(c => c.parentId === 0)
      .map(parent => ({ id: parent.id, categoryName: parent.categoryName, children: categories.value.filter(c => c.parentId === parent.id) }))
      .filter(g => g.children.length > 0)
  })

  // 分组数据：按大类分组，统计每个子类的文件数(=积分数量)、总积分与阶段分布（按数据范围）
  const groupedData = computed(() => {
    const parents = categories.value.filter(c => c.parentId === 0)
    return parents.map((parent) => {
      const children = categories.value.filter(c => c.parentId === parent.id).map(child => ({
        ...child,
        uploadCount: catStatMap.value[child.id]?.fileCount || 0,
        totalScore: catStatMap.value[child.id]?.totalScore || 0,
        preCount: catStatMap.value[child.id]?.preCount || 0,
        midCount: catStatMap.value[child.id]?.midCount || 0,
        postCount: catStatMap.value[child.id]?.postCount || 0,
      }))
      return {
        parentId: parent.id,
        parentName: parent.categoryName,
        icon: catIcons[parent.categoryCode] || 'box',
        children,
      }
    }).filter(g => g.children.length > 0)
  })

  // ---- 排行导出（CSV，含 BOM 防中文乱码）----
  // 【2026-10-04 D11】删除死组件 DeptRankTableView 后，'dept' 部门排行导出分支再无调用方
  // （deptList 原本只被该死组件消费、永远为空，导出必然空文件），一并清理为仅客户排行导出。
  const exportRank = () => {
    const periodLabel = period.value || '全部周期'
    const cats = rankParents.value.flatMap(g => g.children.map(c => c.categoryName))
    const header = ['排名', '客户', '激励总额', ...cats, '上传数']
    const rows = scoreboard.value.map((r, i) => [i + 1, r.customerName, r.totalScore, ...rankParents.value.flatMap(g => g.children.map(c => r.byCategory?.[c.id]?.[0] || 0)), r.recordCount || r.count || 0])
    const esc = (v) => {
      const s = String(v ?? '')
      return /[",\n]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s
    }
    const csv = '﻿' + [header, ...rows].map(row => row.map(esc).join(',')).join('\n')
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `激励客户排行_${periodLabel}.csv`
    document.body.appendChild(a)
    a.click()
    URL.revokeObjectURL(url)
    a.remove()
  }

  const loadData = async () => {
    pageNum.value = 1
    loading.value = true
    const p = periodQueryValue(period.value)
    // 【性能 2026-08-08】五个接口相互独立，由串行 await 改为并发 Promise.allSettled
    // （单页从 ~5×RTT 降到 ~1×max(RTT)，缓解激励统计页卡顿）；各接口独立容错。
    try {
      const [catRes, recRes, csRes, sbRes, pbRes] = await Promise.allSettled([
        cachedGet('categoryTree', getCategoryTree),
        // 【2026-08-08 分享制】顶部"我的"卡片只统计本人上传记录（显式传本人 userId）
        getRecords({ period: p, userId: userStore.userId }),
        getCategoryStats({ period: p }),
        getCustomerRecords({ period: p }),
        // 【2026-09-23 个人排行】按人员聚合四项激励(总积分/分类细分/三阶段文件)
        getScoreboard({ period: p }),
      ])

      if (catRes.status === 'fulfilled') {
        const flat = []
        const flatten = (list) => {
          for (const item of list) {
            flat.push({ ...item, children: undefined })
            if (item.children) flatten(item.children)
          }
        }
        flatten(catRes.value?.data || [])
        categories.value = flat
      }
      // 顶部卡片：当前登录人的个人记录
      if (recRes.status === 'fulfilled') records.value = recRes.value?.data || []
      // 激励统计表：全员聚合
      if (csRes.status === 'fulfilled') catStat.value = csRes.value?.data || { byCategory: [], totalFiles: 0, totalScore: 0 }
      if (sbRes.status === 'fulfilled') {
        // rank 先按全量(公司级)定序,再做销售行过滤——序号保留公司级名次不重编
        const rankedRows = (sbRes.value?.data || []).map((row, index) => ({
          ...row,
          rank: index + 1,
          recordCount: row.recordCount || row.count || 0,
          byCategory: buildCustomerCategoryMap(row.records || []),
        }))
        // 【2026-09-29 裁决】客户排行销售视图: 06销售只看自己名下客户的行(非admin),rank不变
        scoreboard.value = filterCustomerRankRows(
          rankedRows,
          userStore.userId,
          !userStore.isAdmin() && (userStore.roles || []).includes('06'),
        )
      }
      // 【2026-09-23 个人排行】后端已按总积分降序并给出 rank/realName/officeName/byCategory
      if (pbRes.status === 'fulfilled') {
        personBoard.value = pbRes.value?.data || []
        personPageNum.value = 1
      }
    } finally {
      loading.value = false
    }
  }

  const buildCustomerCategoryMap = (customerRecords) => {
    const map = {}
    for (const record of customerRecords) {
      const categoryId = record.categoryId
      if (!categoryId) continue
      if (!map[categoryId]) map[categoryId] = [0, 0]
      map[categoryId][0] += 1
      map[categoryId][1] += record.score || 0
    }
    return map
  }

  return {
    userStore, loading, period, activeTab, records, catStat, categories,
    scoreboard, pageNum, pageSize, pagedScoreboard, periodOptions,
    personBoard, personPageNum, personPageSize, pagedPersonBoard,
    catColors, catIcons,
    totalCategories, shareableCount, fileCountOf, totalRecords, totalScore,
    tableTotalFiles, tableTotalScore, tableTotalPre, tableTotalMid, tableTotalPost,
    catStatMap, rankParents, groupedData,
    exportRank, loadData,
  }
}
