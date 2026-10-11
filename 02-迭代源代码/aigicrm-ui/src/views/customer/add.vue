<template>
  <div class="customer-add">
    <!-- 页头:返回 + 标题 + 填写须知 -->
    <div class="add-header">
      <el-button link @click="goBack" class="back-btn">
        <el-icon><ArrowLeft /></el-icon> 返回
      </el-button>
      <div class="title-wrap">
        <h2 class="add-title">{{ isEdit ? '编辑客户' : '新增客户' }}</h2>
        <span class="add-sub">带 <span class="req-star">*</span> 为必填 · 客户全称自动查重 · 保护期 = 注册日/开票日较晚者 + 360 天</span>
      </div>
    </div>

    <!-- 分段锚点导航(滚动高亮当前段) -->
    <div class="section-nav">
      <a v-for="s in sections" :key="s.id" class="nav-chip" :class="{ active: activeSection === s.id }"
         @click="scrollTo(s.id)">{{ s.label }}</a>
    </div>

    <div class="form-card">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="add-form">
        <div class="anchor-target" id="sec-basic"><BasicSection /></div>
        <div class="anchor-target" id="sec-biz"><BizSection /></div>
        <div class="anchor-target" id="sec-org"><OrgSection /></div>
        <div class="anchor-target" id="sec-address"><AddressSection /></div>
        <div class="anchor-target" id="sec-contacts"><ContactsSection /></div>
        <!-- 【裁决 2026-10-10】客情五项指标首次录入不体现: 新建不渲染, 编辑/详情维护(IndicatorTab 即维护入口) -->
        <div v-if="isEdit" class="anchor-target" id="sec-indicator"><IndicatorSection /></div>

        <el-form-item label="备注" class="remark-field">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="备注信息（选填）" />
        </el-form-item>
      </el-form>
    </div>

    <!-- 吸底操作栏:长表单滚动时始终可见 -->
    <div class="action-bar">
      <span class="action-hint">{{ isEdit ? '编辑保留原创建人与创建时间' : '保存后可继续补充联系人、地址与经营指标' }}</span>
      <div class="action-btns">
        <el-button size="large" @click="goBack">取消</el-button>
        <el-button type="primary" size="large" :loading="loading" @click="handleSubmit">
          <el-icon><Document /></el-icon> {{ isEdit ? '更新客户' : '保存客户' }}
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { provide, onMounted, onBeforeUnmount, ref, computed, nextTick } from 'vue'
import { useCustomerForm, CustomerFormKey } from '@/composables/useCustomerForm'
import BasicSection from './BasicSection.vue'
import BizSection from './BizSection.vue'
import OrgSection from './OrgSection.vue'
import AddressSection from './AddressSection.vue'
import ContactsSection from './ContactsSection.vue'
import IndicatorSection from './IndicatorSection.vue'

// 共享上下文: 父级实例化一次并 provide,各 section 子组件 inject
const ctx = useCustomerForm()
provide(CustomerFormKey, ctx)

// form 解构为内部响应式客户数据(el-form :model 必须绑定它,而非整个 composable 实例)
const { form, goBack, isEdit, formRef, rules, loading, handleSubmit, loadFormData } = ctx

// ——— 分段锚点导航(IntersectionObserver 滚动高亮) ———
// 【裁决 2026-10-10】经营指标锚点仅编辑态出现(新建不渲染五项指标区块)
const sections = computed(() => {
  const base = [
    { id: 'sec-basic', label: '基本信息' },
    { id: 'sec-biz', label: '业务分类' },
    { id: 'sec-org', label: '归属负责人' },
    { id: 'sec-address', label: '地址信息' },
    { id: 'sec-contacts', label: '联系人' }
  ]
  if (isEdit.value) base.push({ id: 'sec-indicator', label: '经营指标' })
  return base
})
const activeSection = ref('sec-basic')
let observer = null

const scrollTo = (id) => {
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

onMounted(() => {
  loadFormData()
  nextTick(() => {
    observer = new IntersectionObserver(
      (entries) => {
        for (const e of entries) {
          if (e.isIntersecting) activeSection.value = e.target.id
        }
      },
      // 视口中线偏上视为"当前段",避免高亮抖动
      { rootMargin: '-30% 0px -60% 0px', threshold: 0 }
    )
    sections.forEach((s) => {
      const el = document.getElementById(s.id)
      if (el) observer.observe(el)
    })
  })
})

onBeforeUnmount(() => { observer?.disconnect() })
</script>

<style scoped>
.add-header {
  display: flex; align-items: flex-start; gap: 16px;
  margin-bottom: 16px;
}
.back-btn { font-size: 14px; color: #606266; margin-top: 4px; }
.title-wrap { display: flex; flex-direction: column; gap: 4px; }
.add-title { font-size: 20px; font-weight: 700; color: #1f2329; margin: 0; letter-spacing: .2px; }
.add-sub { font-size: 12px; color: var(--el-color-info); }
.req-star { color: var(--el-color-danger); font-weight: 600; }

.section-nav {
  position: sticky; top: 0; z-index: 10;
  display: flex; gap: 8px; flex-wrap: wrap;
  background: rgba(245, 246, 248, .96);
  backdrop-filter: blur(6px);
  padding: 10px 4px; margin: 0 -4px 14px;
  border-bottom: 1px solid #eef1f5;
}
.nav-chip {
  font-size: 13px; color: #4e5969; cursor: pointer;
  padding: 5px 14px; border-radius: var(--radius-pill);
  background: #fff; border: 1px solid #e5e6eb;
  transition: all .18s; user-select: none;
}
.nav-chip:hover { color: #2563eb; border-color: #bfdbfe; }
.nav-chip.active {
  color: #fff; background: #1d4ed8; border-color: #1d4ed8;
  font-weight: 600;
}

.form-card {
  background: #fff;
  border-radius: 12px;
  border: 1px solid #e8ecf1;
  padding: 32px 40px;
  box-shadow: 0 1px 4px rgba(0,0,0,.03);
}
.anchor-target { scroll-margin-top: 68px; }

:deep(.el-form-item__label) {
  font-size: 13px;
  color: #4e5969;
  font-weight: 600;
  padding-bottom: 6px;
}
:deep(.el-input__wrapper) { box-shadow: 0 0 0 1px #e5e6eb inset; }
:deep(.el-input__wrapper:hover) { box-shadow: 0 0 0 1px #c9cdd4 inset; }

.remark-field { margin-top: 8px; }

.action-bar {
  position: sticky; bottom: 0; z-index: 10;
  display: flex; justify-content: space-between; align-items: center; gap: 16px;
  background: rgba(255, 255, 255, .97);
  backdrop-filter: blur(6px);
  border: 1px solid #e8ecf1; border-radius: 12px;
  padding: 14px 24px; margin-top: 16px;
  box-shadow: 0 -4px 16px rgba(15, 23, 42, .06);
}
.action-hint { font-size: 12px; color: var(--el-color-info); }
.action-btns { display: flex; gap: 12px; }
</style>
