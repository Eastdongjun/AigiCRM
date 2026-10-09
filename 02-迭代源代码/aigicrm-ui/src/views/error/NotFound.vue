<template>
  <!-- 【C12 2026-10-08 二轮】404 兜底页：深链错误路径不再白屏（视觉复用 403 页骨架） -->
  <div class="not-found">
    <div class="card">
      <div class="code">404</div>
      <h1>页面不存在或已被移动</h1>
      <p>请检查地址是否正确，或返回工作台从菜单重新进入。</p>
      <div class="actions">
        <el-button type="primary" @click="goHome">返回工作台</el-button>
        <el-button @click="goBack">返回上一页</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()

const goHome = () => router.push('/')
const goBack = () => (window.history.length > 1 ? router.back() : router.push('/'))
</script>

<style scoped>
.not-found {
  min-height: 100vh;
  display: grid;
  place-items: center;
  background:
    radial-gradient(circle at 20% 20%, rgba(64, 158, 255, .16), transparent 28%),
    linear-gradient(135deg, #f7fbff 0%, #eef4f8 100%);
}
.card {
  width: min(480px, calc(100vw - 40px));
  padding: 44px 40px;
  border-radius: 24px;
  background: rgba(255, 255, 255, .92);
  box-shadow: 0 24px 70px rgba(20, 45, 80, .14);
  text-align: center;
}
.code {
  font-size: 64px;
  line-height: 1;
  font-weight: 800;
  color: var(--el-color-primary);
  letter-spacing: -2px;
}
h1 {
  margin: 18px 0 10px;
  font-size: 22px;
  color: #1f2d3d;
}
p {
  margin: 0 0 28px;
  color: #6b778c;
  line-height: 1.7;
}
.actions { display: flex; justify-content: center; gap: 12px; }
</style>
