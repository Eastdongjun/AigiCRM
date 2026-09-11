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
      <div v-for="(c, idx) in contacts" :key="idx" class="contact-row">
        <el-input v-model="c.name" placeholder="姓名" />
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

const { contacts, addContactRow } = injectCustomerForm()
</script>

<style scoped>
.contacts-list { margin-top: 4px; }
.contact-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
  padding: 10px 12px;
  background: #fafbfc;
  border-radius: 8px;
  border: 1px solid #f0f1f3;
}
.contact-row .el-input { width: 130px; }
.contact-row .el-input:last-of-type { flex: 1; }
.empty-hint {
  color: #c0c4cc;
  font-size: 13px;
  text-align: center;
  padding: 16px 0;
  background: #fafbfc;
  border-radius: 8px;
}
</style>
