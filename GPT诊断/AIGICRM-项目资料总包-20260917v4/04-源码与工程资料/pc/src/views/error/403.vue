<template>
  <div class="no-permission">
    <div class="card">
      <div class="code">403</div>
      <h1>当前账号暂无可访问页面</h1>
      <p>请联系管理员在「角色管理」或「人员管理」中开通菜单权限和数据范围。</p>
      <el-button type="primary" @click="goLogin">返回登录</el-button>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()

const goLogin = () => {
  router.push('/login')
}
</script>

<style scoped>
.no-permission {
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
  color: #1f6feb;
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
</style>
