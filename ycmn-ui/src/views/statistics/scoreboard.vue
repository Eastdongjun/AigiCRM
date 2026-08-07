<template>
  <div class="scoreboard-page">
    <div class="page-header">
      <h2>学分榜</h2>
      <el-select v-model="period" style="width:160px" @change="loadData">
        <el-option v-for="p in periodOptions" :key="p" :label="p" :value="p" />
      </el-select>
    </div>

    <!-- 前三名展示 -->
    <el-row :gutter="16" class="top-three" v-if="list.length >= 3">
      <el-col :span="8" v-for="(item, i) in [list[1], list[0], list[2]]" :key="i">
        <el-card shadow="hover" class="podium-card" :class="'podium-' + [2, 1, 3][i]">
          <div class="podium-rank">{{ [2, 1, 3][i] }}</div>
          <el-avatar :size="[48, 64, 48][i]" class="podium-avatar" :class="'avatar-' + [2, 1, 3][i]">
            {{ item?.realName?.charAt(0) || '?' }}
          </el-avatar>
          <div class="podium-name">{{ item?.realName || '—' }}</div>
          <div class="podium-score">{{ item?.totalScore || 0 }} 分</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 完整排行榜 -->
    <el-card shadow="never" style="margin-top: 16px">
      <el-table :data="pagedList" stripe :row-class-name="getRowClass">
        <el-table-column label="排名" width="80" align="center">
          <template #default="{ $index }">
            <div class="rank-badge" :class="'rank-' + ($index + 1)">
              <span v-if="$index < 3" class="medal">{{ ['🥇', '🥈', '🥉'][$index] }}</span>
              <span v-else>{{ $index + 1 }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="姓名" width="140">
          <template #default="{ row }">
            <div class="name-cell">
              <el-avatar :size="32" class="name-avatar">{{ row.realName?.charAt(0) }}</el-avatar>
              <span>{{ row.realName }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="总学分" width="120" align="center">
          <template #default="{ row }">
            <span class="score-value">{{ row.totalScore }}</span>
          </template>
        </el-table-column>
        <el-table-column label="进度" min-width="250">
          <template #default="{ row, $index }">
            <el-progress
              :percentage="maxScore ? (row.totalScore / maxScore) * 100 : 0"
              :show-text="false"
              :stroke-width="14"
              :color="getProgressColor($index)"
            />
          </template>
        </el-table-column>
        <el-table-column label="对比" width="100" align="center">
          <template #default="{ row }">
            <span class="vs-max">{{ maxScore ? Math.round((row.totalScore / maxScore) * 100) : 0 }}%</span>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-if="list.length > pageSize" v-model:current-page="pageNum" :page-size="pageSize" :total="list.length" layout="prev, pager, next" size="small" background style="justify-content:flex-end;margin-top:12px" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getScoreboard } from '@/api/incentive'

const periodOptions = (() => { const l = []; for (let y = 2030; y >= 2025; y--) { l.push(y + '下半年', y + '上半年') } return l })()
const period = ref('2026上半年')
const list = ref([])
// 积分榜分页（前端切片）
const pageNum = ref(1)
const pageSize = ref(15)
const pagedList = computed(() => list.value.slice((pageNum.value - 1) * pageSize.value, pageNum.value * pageSize.value))

const maxScore = computed(() => {
  if (list.value.length === 0) return 0
  return Math.max(...list.value.map(i => i.totalScore))
})

const loadData = async () => {
  pageNum.value = 1
  try {
    const res = await getScoreboard({ period: period.value })
    list.value = res.data || []
  } catch (e) {}
}

const getRowClass = ({ rowIndex }) => {
  if (rowIndex < 3) return 'top-row'
  return ''
}

const getProgressColor = (index) => {
  if (index === 0) return '#ffd700'
  if (index === 1) return '#c0c0c0'
  if (index === 2) return '#cd7f32'
  return '#409eff'
}

onMounted(loadData)
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-header h2 { font-size: 20px; }

/* 前三名领奖台 */
.top-three { margin-bottom: 0; }
.podium-card {
  border-radius: 12px;
  text-align: center;
  padding: 16px 0;
  position: relative;
}
.podium-1 { border-top: 3px solid #ffd700; }
.podium-2 { border-top: 3px solid #c0c0c0; margin-top: 20px; }
.podium-3 { border-top: 3px solid #cd7f32; margin-top: 20px; }
.podium-rank {
  position: absolute;
  top: -12px;
  left: 50%;
  transform: translateX(-50%);
  width: 24px;
  height: 24px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
  font-size: 12px;
  color: #fff;
}
.podium-1 .podium-rank { background: #ffd700; }
.podium-2 .podium-rank { background: #c0c0c0; }
.podium-3 .podium-rank { background: #cd7f32; }
.podium-avatar {
  margin: 8px auto;
  font-weight: bold;
}
.avatar-1 { background: #ffd700; color: #fff; }
.avatar-2 { background: #c0c0c0; color: #fff; }
.avatar-3 { background: #cd7f32; color: #fff; }
.podium-name { font-size: 16px; font-weight: 600; color: #333; margin-top: 8px; }
.podium-score { font-size: 20px; font-weight: bold; color: #409eff; margin-top: 4px; }

/* 排行榜表格 */
.rank-badge {
  display: inline-flex;
  width: 32px;
  height: 32px;
  align-items: center;
  justify-content: center;
  font-weight: bold;
  font-size: 14px;
}
.medal { font-size: 22px; }
.rank-badge:not(.rank-1):not(.rank-2):not(.rank-3) {
  background: #f0f0f0;
  border-radius: 50%;
  color: #666;
}

.name-cell { display: flex; align-items: center; gap: 8px; }
.name-avatar { background: #409eff; color: #fff; font-size: 14px; flex-shrink: 0; }

.score-value { font-size: 20px; font-weight: bold; color: #409eff; }
.vs-max { font-size: 13px; color: #999; }

:deep(.top-row) { background: #fafafa; }
</style>
