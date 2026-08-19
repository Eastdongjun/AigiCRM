<template>
  <div class="user-manage">
    <!-- 框1: 页头 + 筛选 -->
    <UserFilterBar />

    <!-- 框2: 表格 -->
    <UserTable />

    <!-- 框3: 翻页 -->
    <UserPager />

    <!-- 新增/编辑对话框 -->
    <UserFormDialog />
  </div>
</template>

<script setup>
import { provide, onMounted } from 'vue'
import { useUserList, UserListKey } from '@/composables/useUserList'
import UserFilterBar from './UserFilterBar.vue'
import UserTable from './UserTable.vue'
import UserPager from './UserPager.vue'
import UserFormDialog from './UserFormDialog.vue'

// 共享上下文: 父级实例化一次并 provide,各区块/弹窗 inject
const list = useUserList()
provide(UserListKey, list)

const { loadData, loadDepts, loadRoles, loadUserOptions, loadPositions } = list

onMounted(() => {
  loadData()
  loadDepts()
  loadRoles()
  loadUserOptions()
  loadPositions()
})
</script>

<style scoped>
.user-manage { height: calc(100vh - 40px); display: flex; flex-direction: column; gap: 10px }
</style>
