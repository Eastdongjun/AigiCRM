<template>
  <div class="incentive-overview">
    <div class="page-header">
      <h2>激励总览</h2>
    </div>
    <el-row :gutter="16">
      <el-col :span="6" v-for="cat in categories" :key="cat.name">
        <el-card shadow="hover" class="overview-card" @click="$router.push('/incentive')">
          <div class="card-icon" :style="{ background: cat.color }">
            <el-icon :size="28"><component :is="cat.icon" /></el-icon>
          </div>
          <div class="card-name">{{ cat.name }}</div>
          <div class="card-score">{{ cat.score }} 分</div>
          <div class="card-count">{{ cat.count }} 项已完成</div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref } from 'vue'

const categories = ref([
  { name: '外出参观', icon: 'Location', color: 'rgba(64,158,255,0.15)', score: 6, count: 3 },
  { name: '交流拜访', icon: 'ChatDotSquare', color: 'rgba(103,194,58,0.15)', score: 4, count: 2 },
  { name: '两谈两做方案', icon: 'Document', color: 'rgba(230,162,60,0.15)', score: 3, count: 1 },
  { name: '产品试点', icon: 'Box', color: 'rgba(155,89,182,0.15)', score: 5, count: 3 }
])
</script>

<style scoped>
.page-header { margin-bottom: 16px; }
.page-header h2 { font-size: 20px; }
.overview-card { text-align: center; border-radius: 10px; cursor: pointer; padding: 12px 0; }
.overview-card:hover { transform: translateY(-2px); transition: 0.2s; }
.card-icon { width: 56px; height: 56px; border-radius: 14px; margin: 0 auto 12px; display: flex; align-items: center; justify-content: center; color: #409eff; }
.card-name { font-size: 15px; font-weight: 600; color: #333; }
.card-score { font-size: 24px; font-weight: bold; color: #409eff; margin: 4px 0; }
.card-count { font-size: 12px; color: #999; }
</style>
