<template>
  <div class="detail-page">
    <div class="card">
      <div class="detail-head">
        <div class="head-left">
          <el-button link type="primary" @click="goBack">← 返回激励统计</el-button>
          <span class="title">{{ childName }} 明细</span>
          <span v-if="parentName" class="subtitle">· {{ parentName }}</span>
        </div>
        <div class="meta">
          <span>周期：<b>{{ period || '全部' }}</b></span>
          <span>细分积分：<b>{{ score || '—' }}</b></span>
          <span>记录：<b>{{ records.length }}</b> 条</span>
          <span>合计：<b class="blue">{{ totalScore }}</b> 分</span>
        </div>
      </div>

      <el-table
        v-loading="loading"
        :data="records"
        stripe
        size="small"
        style="width:100%"
        empty-text="该细分暂无激励记录"
      >
        <el-table-column label="客户" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.customerName || '—' }}</template>
        </el-table-column>
        <el-table-column prop="userName" label="上传人" width="90" />
        <el-table-column label="上传时间" width="150">
          <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="分" width="60" align="center">
          <template #default="{ row }">
            <span :style="{ color: scoreColor(row.score), fontWeight: 600 }">{{ row.score }}</span>
          </template>
        </el-table-column>
        <el-table-column v-for="slot in slots" :key="slot.key" :label="slot.label" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="files">
              <span
                v-for="file in slotFiles(row, slot)"
                :key="`${slot.key}-${file.id}`"
                class="file-tag"
                :title="`下载 ${file.name}`"
                @click="download(file)"
              >{{ file.name }}</span>
              <span v-if="!slotFiles(row, slot).length" class="empty">—</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark || row.preInstallQuestion || '—' }}</template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getRecords } from '@/api/incentive'
import { periodQueryValue } from '@/utils/incentive-period'
import { SLOT_FIELDS, filterRecordsByCategory, slotFiles, sumScore } from '@/utils/incentive-detail'
import { getDownloadToken, getToken, refreshDownloadToken } from '@/utils/token'
import { downloadFile as downloadFileUtil } from '@/utils/download'

const route = useRoute()
const router = useRouter()

const childId = computed(() => route.params.childId)
const parentId = computed(() => route.query.parentId || null)
const parentName = computed(() => route.query.parentName || '')
const childName = computed(() => route.query.childName || '激励明细')
const score = computed(() => route.query.score || '')
const period = computed(() => route.query.period || '')
const slots = SLOT_FIELDS

const loading = ref(false)
const records = ref([])
const totalScore = computed(() => sumScore(records.value))

function goBack() {
  router.back()
}

function formatDate(value) {
  if (!value) return '—'
  return String(value).replace('T', ' ').slice(0, 16)
}

function scoreColor(value) {
  const score = Number(value) || 0
  if (score >= 3) return 'var(--el-color-danger)'
  if (score >= 2) return 'var(--el-color-warning)'
  return 'var(--el-color-primary)'
}

function fileUrl(id) {
  const token = getDownloadToken()
  return token ? `/api/file/download/${id}?token=${token}` : `/api/file/download/${id}`
}

async function download(file) {
  if (!file?.id) return
  try {
    await refreshDownloadToken()
    await downloadFileUtil(fileUrl(file.id), {
      name: file.name,
      headers: { Authorization: 'Bearer ' + (getToken() || '') },
    })
  } catch (e) {
    ElMessage.error('文件下载失败')
  }
}

async function load() {
  loading.value = true
  try {
    // 后端 records 接口按 parentCategoryId 过滤，明细再按 categoryId 精确收敛
    const res = await getRecords({
      period: periodQueryValue(period.value),
      parentCategoryId: parentId.value ? Number(parentId.value) : null,
    })
    records.value = filterRecordsByCategory(res.data || [], childId.value)
  } catch (e) {
    records.value = []
    ElMessage.error('激励明细加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.detail-page { padding: 0 0 16px; }
.card { background: #fff; border: 1px solid #ebeef5; border-radius: 12px; padding: 20px 24px; }
.detail-head { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 16px; flex-wrap: wrap; }
.title { font-size: 16px; font-weight: 600; color: #1d2129; }
.subtitle { font-size: 13px; color: var(--text-muted); margin-left: 6px; }
.meta { display: flex; align-items: center; gap: 18px; font-size: 13px; color: #4e5969; }
.meta b { color: #1d2129; }
.blue { color: var(--el-color-primary); }
.files { display: flex; flex-direction: column; gap: 2px; }
.file-tag { color: var(--el-color-primary); cursor: pointer; }
.file-tag:hover { text-decoration: underline; }
.empty { color: var(--text-placeholder); }
</style>
