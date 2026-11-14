<template>
  <div class="page-container">
    <!-- 团队统计四联卡 -->
    <div class="stat-cards">
      <div class="stat-card">
        <div class="stat-label">今日已读人数</div>
        <div class="stat-value">{{ team.todayReadUsers || 0 }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">覆盖率</div>
        <div class="stat-value">
          {{ team.coveragePct != null ? team.coveragePct + '%' : '—' }}
          <span v-if="team.memberCount === -1" class="stat-sub">全部</span>
          <span v-else class="stat-sub">/ {{ team.memberCount || 0 }} 人</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-label">已发布内容</div>
        <div class="stat-value">{{ team.totalPosts || 0 }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">木鱼总数</div>
        <div class="stat-value">{{ team.likesTotal || 0 }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">评论总数</div>
        <div class="stat-value">{{ team.commentsTotal || 0 }}</div>
      </div>
    </div>

    <!-- 个人统计 -->
    <el-card shadow="never" class="personal-card">
      <template #header><span>个人统计</span></template>
      <div class="personal-grid">
        <div class="p-item"><span class="p-label">我的内容</span><span class="p-value">{{ personal.myPosts || 0 }}</span></div>
        <div class="p-item"><span class="p-label">我的评论</span><span class="p-value">{{ personal.myComments || 0 }}</span></div>
        <div class="p-item"><span class="p-label">我的阅读</span><span class="p-value">{{ personal.myReadCount || 0 }}</span></div>
        <div class="p-item"><span class="p-label">获木鱼</span><span class="p-value">{{ personal.myLikesReceived || 0 }}</span></div>
      </div>
    </el-card>

    <p class="scope-note" v-if="team.memberCount === -1">
      当前账号数据范围为「全部」，以上团队指标为全集团口径。
    </p>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getFabushiTeamStats, getFabushiPersonalStats } from '@/api/fabushi'

const team = ref({})
const personal = ref({})

async function loadData() {
  const [t, p] = await Promise.all([getFabushiTeamStats(), getFabushiPersonalStats()])
  team.value = (t && t.data) || {}
  personal.value = (p && p.data) || {}
}

onMounted(() => { loadData() })
</script>

<style scoped>
.stat-cards { display: flex; gap: 12px; flex-wrap: wrap; margin-bottom: 16px; }
.stat-card { flex: 1; min-width: 150px; background: #fff; border: 1px solid var(--el-border-color-light); border-radius: 8px; padding: 16px; }
.stat-label { color: var(--el-text-color-secondary); font-size: 13px; margin-bottom: 8px; }
.stat-value { font-size: 26px; font-weight: 600; color: #1d4ed8; }
.stat-sub { font-size: 12px; color: var(--el-text-color-secondary); font-weight: normal; margin-left: 4px; }
.personal-card { margin-bottom: 12px; }
.personal-grid { display: flex; gap: 32px; flex-wrap: wrap; }
.p-item { display: flex; flex-direction: column; gap: 4px; }
.p-label { color: var(--el-text-color-secondary); font-size: 12px; }
.p-value { font-size: 20px; font-weight: 600; }
.scope-note { color: var(--el-text-color-secondary); font-size: 12px; }
</style>
