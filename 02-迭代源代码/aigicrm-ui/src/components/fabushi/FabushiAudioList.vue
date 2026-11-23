<template>
  <!--
    【P6 2026-11-23】已上传语音段列表：原型「语音 1-10 段 × ≤60s」。
    保存前用录制器的 blob URL 试听；保存后仍用本地 blob URL 试听（不重新下载），
    不满意可逐段删除重录。三个发布器（P6/P7/P8）共用本组件，避免三份重复。
  -->
  <div v-if="items.length" class="audio-list">
    <div v-for="item in items" :key="item.key" class="audio-item">
      <audio :src="item.previewUrl" controls class="audio-el" />
      <span class="audio-name">{{ item.name }}</span>
      <button type="button" class="audio-x" title="删除该段" @click="emit('remove', item)">×</button>
    </div>
  </div>
</template>

<script setup>
defineProps({
  /** 附件数组（fileType === 'audio' 的子集），元素含 { key, name, previewUrl } */
  items: { type: Array, default: () => [] },
})
const emit = defineEmits(['remove'])
</script>

<style scoped>
.audio-list { display: flex; flex-direction: column; gap: 6px; margin-top: 8px; }
.audio-item { display: flex; align-items: center; gap: 8px; padding: 5px 8px; border: 1px solid var(--border-light); border-radius: var(--radius-sm); background: var(--el-fill-color-lighter); }
.audio-el { flex: 1; height: 30px; min-width: 0; }
.audio-name { flex-shrink: 0; max-width: 130px; overflow: hidden; color: var(--text-muted); font-size: 10.5px; text-overflow: ellipsis; white-space: nowrap; }
.audio-x { flex-shrink: 0; width: 20px; height: 20px; border: 0; border-radius: 50%; color: var(--text-muted); background: var(--el-fill-color-dark); font-size: 13px; line-height: 1; cursor: pointer; }
.audio-x:hover { color: var(--el-color-danger); background: var(--el-color-danger-light-9); }
</style>
