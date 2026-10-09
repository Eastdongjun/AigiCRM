<template>
  <!-- 【性能 2026-08-08】Element Plus 按需引入后无全局 locale，用 el-config-provider 提供中文 -->
  <el-config-provider :locale="zhCn">
    <!-- 全局路由加载指示:点击菜单后立即显示,消除"切换空白无反馈"的卡顿体感 -->
    <div v-if="routeLoading" class="route-loading">
      <div class="route-loading-box">
        <span class="route-spinner"></span>
        <span>页面加载中...</span>
      </div>
    </div>
    <router-view />
  </el-config-provider>
</template>

<script setup>
import { ref } from 'vue'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import router from '@/router'

const routeLoading = ref(false)
router.beforeEach((_to, _from, next) => {
  routeLoading.value = true
  next()
})
router.afterEach(() => {
  // 组件挂载后再关闭,保证新页面首帧出现
  setTimeout(() => { routeLoading.value = false }, 50)
})
router.onError(() => { routeLoading.value = false })
</script>

<style scoped>
.route-loading {
  position: fixed;
  inset: 0;
  z-index: 3000;
  background: rgba(255, 255, 255, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: none;
}
.route-loading-box {
  display: flex;
  align-items: center;
  gap: 10px;
  background: #fff;
  padding: 14px 24px;
  border-radius: var(--radius-md);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
  font-size: 14px;
  /* 白底上的正文需要 4.5:1 对比度；var(--el-color-primary) 仅 2.78:1，改用同色系 #2a76cf (4.57:1)。 */
  color: #2a76cf;
}
.route-spinner {
  width: 18px;
  height: 18px;
  border: 2px solid #1d4ed8;
  border-top-color: transparent;
  border-radius: 50%;
  animation: rspin 0.8s linear infinite;
}
@keyframes rspin {
  to { transform: rotate(360deg); }
}
</style>
