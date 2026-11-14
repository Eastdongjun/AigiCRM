<template>
  <div class="page-container">
    <div class="filter-bar">
      <el-radio-group v-model="query.scopeType" @change="onFilterChange">
        <el-radio-button value="">全部</el-radio-button>
        <el-radio-button value="heart">心得</el-radio-button>
        <el-radio-button value="recitation">诵读</el-radio-button>
        <el-radio-button value="znl">正能量</el-radio-button>
        <el-radio-button value="offline">已下架</el-radio-button>
      </el-radio-group>
      <el-button type="primary" @click="loadData">刷新</el-button>
    </div>

    <el-table :data="posts" stripe size="small" v-loading="loading">
      <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.title || (row.content || '').slice(0, 40) }}
        </template>
      </el-table-column>
      <el-table-column label="类型" width="80">
        <template #default="{ row }">
          <el-tag size="small" :type="row.scopeType === 'heart' ? 'primary' : row.scopeType === 'recitation' ? 'warning' : 'success'">
            {{ scopeText(row.scopeType) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="userName" label="发布人" width="100" />
      <el-table-column prop="likeCount" label="木鱼" width="70" />
      <el-table-column prop="commentCount" label="评论" width="70" />
      <el-table-column prop="createdAt" label="发布时间" width="160" />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag v-if="row.status === 'offline'" type="danger" size="small">已下架</el-tag>
          <el-tag v-else type="success" size="small">已发布</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="offlineReason" label="下架原因" min-width="140" show-overflow-tooltip />
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 'published' && userStore.hasPermission('fabushi:content:manage')"
                     link type="danger" @click="openOffline(row)">下架</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="list-pager">
      <el-pagination v-model:current-page="query.current" v-model:page-size="query.size" :total="total"
        :page-sizes="pageSizes" layout="sizes, prev, pager, next" size="small" @change="loadData" />
    </div>

    <!-- 下架(原因必填留痕) -->
    <el-dialog v-model="offlineVisible" title="下架内容" width="420px">
      <el-input v-model="offlineReason" type="textarea" :rows="3" placeholder="下架原因(必填, 留痕)" />
      <template #footer>
        <el-button @click="offlineVisible = null">取消</el-button>
        <el-button type="danger" :loading="actionLoading" @click="doOffline">确认下架</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import { getFabushiPosts, offlineFabushiPost } from '@/api/fabushi'

const userStore = useUserStore()
const loading = ref(false)
const actionLoading = ref(false)
const posts = ref([])
const total = ref(0)
const pageSizes = [10, 20, 50]
const query = ref({ scopeType: '', current: 1, size: 10 })

const offlineVisible = ref(null)
const offlineReason = ref('')

const scopeMap = { heart: '心得', recitation: '诵读', znl: '正能量' }
const scopeText = (s) => scopeMap[s] || s

async function loadData() {
  loading.value = true
  try {
    const params = { ...query.value }
    if (params.scopeType === 'offline') {
      params.scopeType = ''
      params.status = 'offline'
    }
    const { data } = await getFabushiPosts(params)
    posts.value = (data && data.records) || []
    total.value = Number(data && data.total) || 0
    // 详情下钻: 治理页不点已读(已读是 MP 社区行为)
  } finally {
    loading.value = false
  }
}

function onFilterChange() {
  query.value.current = 1
  loadData()
}

function openOffline(row) {
  offlineVisible.value = row
  offlineReason.value = ''
}

async function doOffline() {
  if (!offlineReason.value.trim()) { ElMessage.warning('下架原因不能为空'); return }
  actionLoading.value = true
  try {
    await offlineFabushiPost(offlineVisible.value.id, offlineReason.value.trim())
    ElMessage.success('已下架(留痕)')
    offlineVisible.value = null
    loadData()
  } finally {
    actionLoading.value = false
  }
}

onMounted(() => { loadData() })
</script>

<style scoped>
.filter-bar { display: flex; gap: 10px; margin-bottom: 12px; align-items: center; }
.list-pager { margin-top: 12px; display: flex; justify-content: flex-end; }
</style>
