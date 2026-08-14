<template>
  <div class="customer-add">
    <div class="breadcrumb-bar">
      <el-button link @click="goBack" class="back-btn">
        <el-icon><ArrowLeft /></el-icon> 返回
      </el-button>
      <span class="page-subtitle">{{ isEdit ? '编辑客户信息' : '新增客户' }}</span>
    </div>

    <div class="form-card">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="add-form">
        <BasicSection />
        <BizSection />
        <OrgSection />
        <AddressSection />
        <ContactsSection />

        <!-- ======== 备注 ======== -->
        <el-form-item label="备注" class="remark-field">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="备注信息（选填）" />
        </el-form-item>

        <!-- ======== 提交按钮 ======== -->
        <div class="form-actions">
          <el-button size="large" @click="goBack">取消</el-button>
          <el-button type="primary" size="large" :loading="loading" @click="handleSubmit">
            <el-icon><Document /></el-icon> {{ isEdit ? '更新客户' : '保存客户' }}
          </el-button>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { provide, onMounted } from 'vue'
import { useCustomerForm, CustomerFormKey } from '@/composables/useCustomerForm'
import BasicSection from './BasicSection.vue'
import BizSection from './BizSection.vue'
import OrgSection from './OrgSection.vue'
import AddressSection from './AddressSection.vue'
import ContactsSection from './ContactsSection.vue'

// 共享上下文: 父级实例化一次并 provide,各 section 子组件 inject
const ctx = useCustomerForm()
provide(CustomerFormKey, ctx)

// form 解构为内部响应式客户数据(el-form :model 必须绑定它,而非整个 composable 实例)
const { form, goBack, isEdit, formRef, rules, loading, handleSubmit, loadFormData } = ctx

onMounted(() => { loadFormData() })
</script>

<style scoped>
.breadcrumb-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}
.back-btn { font-size: 14px; color: #606266; }
.page-subtitle { font-size: 13px; color: #909399; }

.form-card {
  background: #fff;
  border-radius: 12px;
  border: 1px solid #e8ecf1;
  padding: 32px 40px;
  box-shadow: 0 1px 4px rgba(0,0,0,.03);
}

:deep(.el-form-item__label) {
  font-size: 13px;
  color: #4e5969;
  font-weight: 600;
  padding-bottom: 6px;
}
:deep(.el-input__wrapper) { box-shadow: 0 0 0 1px #e5e6eb inset; }
:deep(.el-input__wrapper:hover) { box-shadow: 0 0 0 1px #c9cdd4 inset; }

.remark-field {
  margin-top: 8px;
}

.form-actions {
  display: flex;
  justify-content: center;
  gap: 16px;
  margin-top: 32px;
  padding-top: 24px;
  border-top: 1px solid #f2f3f5;
}
</style>
