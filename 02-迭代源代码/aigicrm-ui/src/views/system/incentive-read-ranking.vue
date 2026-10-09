<template>
  <div class="read-ranking-page">
    <div class="page-header">
      <div class="head-left">
        <h2>本月激励查询阅读排行榜</h2>
        <span class="head-hint">口径：本月发布的激励记录为分母，本人已读条数为分子（与激励查询页「已读/未读」一致）</span>
      </div>
      <div class="head-right">
        <el-date-picker
          v-model="period"
          type="month"
          value-format="YYYY-MM"
          placeholder="选择月份"
          :clearable="false"
          size="small"
          style="width:140px"
          @change="load"
        />
        <el-button size="small" @click="load">刷新</el-button>
      </div>
    </div>

    <el-alert
      v-if="loadError"
      :title="loadError"
      type="error"
      :closable="false"
      show-icon
      class="mb12"
    />

    <div class="stat-row">
      <div class="stat-card">
        <div class="sc-label">本月发布记录</div>
        <div class="sc-num blue">{{ data.publishedTotal || 0 }}</div>
        <div class="sc-desc">{{ monthText }} 已发布的激励记录条数</div>
      </div>
      <div class="stat-card">
        <div class="sc-label">已阅读人数</div>
        <div class="sc-num green">{{ data.participantCount || 0 }}</div>
        <div class="sc-desc">本月至少读过 1 条的人数</div>
      </div>
      <div class="stat-card">
        <div class="sc-label">已读总条数</div>
        <div class="sc-num purple">{{ data.readTotal || 0 }}</div>
        <div class="sc-desc">全员已读条数合计</div>
      </div>
      <div class="stat-card">
        <div class="sc-label">平均已读率</div>
        <div class="sc-num yellow">{{ data.avgReadRate || 0 }}%</div>
        <div class="sc-desc">已读总条数 / 应读总条数</div>
      </div>
    </div>

    <div class="table-card">
      <div class="table-head">
        <span class="table-title">阅读排行榜</span>
        <span class="table-count">{{ monthText }} · 共 {{ rows.length }} 人</span>
      </div>
      <el-table
        v-loading="loading"
        :data="pagedRows"
        stripe
        size="small"
        style="width:100%"
        empty-text="该月暂无发布记录或阅读数据"
      >
        <el-table-column label="排名" width="80" align="center">
          <template #default="{ row }">
            <span v-if="medalOf(row.rank)" class="medal">{{ medalOf(row.rank) }}</span>
            <span v-else class="rank-num">{{ row.rank }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="realName" label="姓名" min-width="110" />
        <el-table-column label="部门" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ row.deptName || '—' }}</template>
        </el-table-column>
        <el-table-column label="已读" width="80" align="center">
          <template #default="{ row }"><b class="num-read">{{ row.readCount }}</b></template>
        </el-table-column>
        <el-table-column label="未读" width="80" align="center">
          <template #default="{ row }"><span class="num-unread">{{ row.unreadCount }}</span></template>
        </el-table-column>
        <el-table-column label="已读率" min-width="180">
          <template #default="{ row }">
            <div class="rate-cell">
              <el-progress
                :percentage="Math.min(100, Number(row.readRate) || 0)"
                :stroke-width="10"
                :show-text="false"
                :class="'rate-' + rateTone(row.readRate)"
              />
              <span class="rate-text">{{ readRateText(row.readCount, row.publishedCount) }}</span>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <div class="table-footer">
        <span class="footer-count">共 <b>{{ rows.length }}</b> 人</span>
        <el-pagination
          v-model:current-page="pageNum"
          :page-size="pageSize"
          :total="rows.length"
          :page-sizes="pageSizes(pageSize)"
          layout="sizes, prev, pager, next, jumper"
          size="small"
          background
          @size-change="onSizeChange"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { getIncentiveReadRanking } from '@/api/incentive'
import { paginate, pageSizes } from '@/utils/pagination'
import {
  currentMonth,
  medalOf,
  monthLabel,
  normalizeRanking,
  rateTone,
  readRateText
} from '@/utils/incentive-read-ranking'

const period = ref(currentMonth())
const data = ref({})
const rows = ref([])
const loading = ref(false)
const loadError = ref('')
const pageNum = ref(1)
const pageSize = ref(10)

const monthText = computed(() => monthLabel(period.value))
const pagedRows = computed(() => paginate(rows.value, pageNum.value, pageSize.value))

const load = async () => {
  loading.value = true
  loadError.value = ''
  pageNum.value = 1
  try {
    const res = await getIncentiveReadRanking({ period: period.value })
    data.value = res.data || {}
    rows.value = normalizeRanking(res.data?.ranking)
  } catch (e) {
    rows.value = []
    data.value = {}
    loadError.value = e?.response?.data?.message || '阅读排行榜加载失败（需要 incentive:read-ranking:view 权限）'
  } finally {
    loading.value = false
  }
}

const onSizeChange = (size) => {
  pageSize.value = size
  pageNum.value = 1
}

onMounted(load)
</script>

<style scoped>
.read-ranking-page { display: flex; flex-direction: column; gap: 12px; padding-bottom: 16px; }
.page-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.page-header h2 { font-size: 18px; font-weight: 600; color: #333; margin: 0 0 4px; }
.head-hint { font-size: 12px; color: var(--text-muted); }
.head-right { display: flex; align-items: center; gap: 8px; flex-shrink: 0; }
.mb12 { margin-bottom: 4px; }

.stat-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; }
.stat-card { background: #fff; border: 1px solid #ebeef5; border-radius: 12px; padding: 18px 22px; }
.sc-label { font-size: 13px; color: var(--text-muted); margin-bottom: 4px; }
/* 【B6 三轮 2026-10-09】大数字唯一档 --font-num-hero(30px)，收敛 32px 游离档 */
.sc-num { font-size: var(--font-num-hero); font-weight: 700; line-height: 1.2; }
.sc-num.blue { color: var(--el-color-primary); }
.sc-num.green { color: var(--el-color-success); }
.sc-num.purple { color: #8b5cf6; }
.sc-num.yellow { color: var(--el-color-warning); }
.sc-desc { font-size: 12px; color: var(--text-muted); margin-top: 4px; }

.table-card { display: flex; flex-direction: column; background: #fff; border: 1px solid #e4e7ed; border-radius: var(--radius-md); padding: 14px 16px; }
.table-head { display: flex; align-items: baseline; justify-content: space-between; margin-bottom: 10px; }
.table-title { font-size: 15px; font-weight: 600; color: #333; }
.table-count { font-size: 12px; color: var(--text-muted); }
.medal { font-size: 18px; }
.rank-num { color: var(--el-color-info); font-weight: 600; }
.num-read { color: var(--el-color-success); }
.num-unread { color: var(--text-placeholder); }
.rate-cell { display: flex; align-items: center; gap: 10px; }
.rate-cell :deep(.el-progress) { flex: 1; }
.rate-text { font-size: 12px; color: #606266; width: 48px; text-align: right; }
.rate-excellent :deep(.el-progress-bar__inner) { background-color: var(--el-color-success); }
.rate-good :deep(.el-progress-bar__inner) { background-color: var(--el-color-primary); }
.rate-fair :deep(.el-progress-bar__inner) { background-color: var(--el-color-warning); }
.rate-none :deep(.el-progress-bar__inner) { background-color: #dcdfe6; }

.table-footer { display: flex; align-items: center; justify-content: space-between; padding: 10px 2px 0; margin-top: 8px; border-top: 1px solid #f0f2f5; }
.footer-count { font-size: 13px; color: #606266; }
.footer-count b { color: var(--el-color-primary); font-weight: 700; margin: 0 2px; }
</style>
