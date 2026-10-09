<template>
  <div class="section-block">
    <div class="section-head">
      <span class="section-icon success"><el-icon><User /></el-icon></span>
      <span class="section-label">联系人</span>
      <el-button type="primary" link size="small" @click="addContactRow">
        <el-icon><Plus /></el-icon> 添加联系人
      </el-button>
    </div>
    <div v-if="contacts.length" class="contacts-list">
      <!-- 【C11 2026-10-08】联系人校验错误行内呈现：错误行红框+姓名框下方红字，提交时自动滚动到此 -->
      <div v-for="(c, idx) in contacts" :key="idx" class="contact-row" :class="{ 'contact-error': contactErrors[idx] }">
        <div class="contact-name-cell">
          <el-input v-model="c.name" placeholder="姓名" @input="contactErrors[idx] = ''" />
          <div v-if="contactErrors[idx]" class="contact-err-text">{{ contactErrors[idx] }}</div>
        </div>
        <el-input v-model="c.position" placeholder="职位" />
        <el-input v-model="c.phone" placeholder="电话" />
        <el-input v-model="c.remark" placeholder="备注" />
        <el-button type="danger" link size="small" @click="contacts.splice(idx, 1)">
          <el-icon><Delete /></el-icon>
        </el-button>
      </div>
    </div>
    <div v-else class="empty-hint">暂无联系人，点击上方按钮添加</div>
  </div>
</template>

<script setup>
import { injectCustomerForm } from '@/composables/useCustomerForm'
import './add-common.css'

const { contacts, contactErrors, addContactRow } = injectCustomerForm()
</script>

<style scoped>
.contacts-list { margin-top: 4px; }
.contact-row {
  display: grid;
  grid-template-columns: 1fr 1fr 1.2fr 1.6fr auto;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
  padding: 10px 14px;
  background: #fafbfc;
  border-radius: 8px;
  border: 1px solid #f0f1f3;
}
.empty-hint {
  color: var(--text-placeholder);
  font-size: 13px;
  text-align: center;
  padding: 16px 0;
  background: #fafbfc;
  border-radius: 8px;
}
/* 【C11】错误行内报错样式 */
.contact-row.contact-error { border-color: var(--el-color-danger); background: #fff8f8; }
.contact-name-cell { display: flex; flex-direction: column; gap: 4px; }
.contact-err-text { font-size: 12px; color: var(--el-color-danger); line-height: 1.3; }
</style>
