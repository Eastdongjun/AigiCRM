<template>
  <div class="section-box" style="padding:6px 20px">
    <div class="list-pager" style="padding:0;border:none">
      <span>{{ total > 0 ? (query.current - 1) * query.size + 1 : 0 }}-{{ Math.min(query.current * query.size, total) }}条 / 共{{ total }}条</span>
      <el-pagination v-model:current-page="query.current" v-model:page-size="query.size" :total="total" :page-sizes="pageSizes(query.size)" layout="sizes, prev, pager, next, jumper" size="small" @change="loadData" @size-change="query.current = 1" />
    </div>
  </div>
</template>

<script setup>
import { injectUserList } from '@/composables/useUserList'
import { pageSizes } from '@/utils/pagination'

const { query, total, loadData } = injectUserList()
</script>

<style scoped>
.section-box { background: #fff; border: 1px solid #e4e7ed; border-radius: var(--radius-md); padding: 14px 20px; flex-shrink: 0 }
.list-pager {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  padding: 10px 0;
  border-top: 1px solid #ebeef5;
  color: var(--text-muted);
  font-size: 12px;
  flex-shrink: 0;
}
</style>
