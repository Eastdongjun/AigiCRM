<template>
  <el-dialog v-model="showReleaseDialog" title="释放客户到公海池" width="480px" :close-on-click-modal="false">
    <div style="padding: 8px 0;">
      <p style="margin-bottom: 12px; color: #303133; font-size: 15px;">
        确认将客户 <strong style="color: #e6a23c;">{{ releaseTarget?.customerName }}</strong> 释放到公海池？
      </p>
      <el-form label-width="80px">
        <el-form-item label="释放原因">
          <el-input v-model="releaseReason" placeholder="请输入释放原因" maxlength="200" show-word-limit />
        </el-form-item>
      </el-form>
      <el-alert type="warning" :closable="false" show-icon style="margin-top: 4px;">
        <template #title>
          释放后，该客户的负责人将被清空，所有分享关系将被删除。其他用户可从公海池捞取该客户。
        </template>
      </el-alert>
    </div>
    <template #footer>
      <el-button @click="showReleaseDialog = false">取消</el-button>
      <el-button type="danger" :loading="releaseLoading" @click="confirmRelease">确认释放</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { injectCustomerList } from '@/composables/useCustomerList'

const { showReleaseDialog, releaseTarget, releaseReason, releaseLoading, confirmRelease } = injectCustomerList()
</script>
