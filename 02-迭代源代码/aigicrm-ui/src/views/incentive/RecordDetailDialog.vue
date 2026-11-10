<template>
  <!-- 【2026-11-10 用户令"关键字检索只给上传的那一条记录"】检索命中记录的详情弹窗:
       只呈现该条上传记录(元信息+四槽位文件, 点缩略进灯箱), 不再端出客户拨盘整页;
       底部保留"查看该客户全部记录"小入口(偶尔要看上下文时用, 打开旧拨盘)。 -->
  <el-dialog v-model="visible" width="720px" top="6vh" :close-on-click-modal="true" destroy-on-close class="rec-dtl-dlg">
    <template #header></template>
    <div class="rd-wrap" v-if="rec">
      <div class="rd-bar">
        <span class="rd-back" @click="visible=false">← 返回列表</span>
        <span class="rd-bar-name">{{ rec.customerName }}</span>
        <span class="rd-bar-cat">{{ rec.categoryName }}</span>
        <span class="rd-bar-pts">+{{ rec.score }} 分</span>
      </div>

      <div class="rd-meta">
        <div class="rd-meta-item"><span class="rd-k">产品</span><span>{{ [rec.specificProduct, rec.relatedProducts].filter(Boolean).join(' · ') || '—' }}</span></div>
        <div class="rd-meta-item"><span class="rd-k">上传人</span><span>{{ rec.userName || '—' }}</span></div>
        <div class="rd-meta-item"><span class="rd-k">周期</span><span>{{ rec.period || '—' }}</span></div>
        <div class="rd-meta-item"><span class="rd-k">时间</span><span>{{ String(rec.createdAt || '').replace('T', ' ').slice(0, 16) }}</span></div>
      </div>

      <div class="rd-files-title">附件（{{ files.length }}）</div>
      <div v-if="files.length" class="rd-files">
        <div v-for="f in files" :key="f.id" class="rd-file" @click="openPreview(f)">
          <el-image v-if="f.type === 'image' && !thumbFailed[f.id]" class="rd-thumb" :src="thumbUrl(f)" fit="cover" lazy @error="thumbFailed[f.id] = true" />
          <div v-else class="rd-ext" :class="'ext-' + (f.ext || 'file')">{{ (f.ext || '文件').toUpperCase() }}</div>
          <div class="rd-file-name" :title="f.name">{{ f.name }}</div>
        </div>
      </div>
      <el-empty v-else description="该记录无附件" :image-size="60" />

      <div class="rd-foot">
        <span class="rd-cust-link" @click="$emit('open-customer', rec)">查看该客户全部记录 →</span>
      </div>
    </div>
    <!-- 灯箱预览(与查询页同源组件): 从缩略打开, 左右翻该记录全部文件 -->
    <SearchLightbox ref="lightbox" :thumb-failed="thumbFailed" @thumb-failed="id => (thumbFailed[id] = true)" />
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import SearchLightbox from './SearchLightbox.vue'
import { getFiles, thumbUrl } from './search-helpers'

const props = defineProps({ modelValue: Boolean, record: { type: Object, default: null } })
const emit = defineEmits(['update:modelValue', 'open-customer'])

const visible = computed({
  get: () => props.modelValue,
  set: v => emit('update:modelValue', v)
})
const rec = ref(null)
const thumbFailed = ref({})
const lightbox = ref(null)

watch(() => props.modelValue, v => { if (v) { rec.value = props.record; thumbFailed.value = {} } })

const files = computed(() => rec.value ? getFiles(rec.value) : [])
const openPreview = (f) => lightbox.value?.open(files.value, f)
</script>

<style scoped>
.rd-wrap { padding: 4px 8px 8px; }
.rd-bar { display: flex; align-items: baseline; gap: 10px; padding-bottom: 10px; border-bottom: 1px solid #f0f2f5; margin-bottom: 12px; }
.rd-back { font-size: 13px; color: var(--el-color-primary); cursor: pointer; }
.rd-bar-name { font-size: 16px; font-weight: 700; color: #1f2d3d; }
.rd-bar-cat { font-size: 12px; padding: 1px 8px; border-radius: 10px; background: var(--el-color-primary-light-9); color: var(--el-color-primary); }
.rd-bar-pts { margin-left: auto; font-size: 15px; font-weight: 700; color: #e6a23c; }
.rd-meta { display: grid; grid-template-columns: 1fr 1fr; gap: 8px 16px; padding: 10px 12px; background: #f7f8fa; border-radius: 8px; margin-bottom: 14px; }
.rd-meta-item { display: flex; gap: 8px; font-size: 13px; color: #4e5969; }
.rd-k { color: #9aa3ad; flex-shrink: 0; }
.rd-files-title { font-size: 13px; font-weight: 600; color: #4e5969; margin-bottom: 8px; }
.rd-files { display: flex; flex-wrap: wrap; gap: 12px; }
.rd-file { width: 118px; cursor: pointer; }
.rd-thumb { width: 118px; height: 88px; border-radius: 8px; background: #f2f4f8; }
.rd-ext { width: 118px; height: 88px; border-radius: 8px; background: var(--el-color-primary-light-9); color: var(--el-color-primary); display: flex; align-items: center; justify-content: center; font-size: 13px; font-weight: 700; }
.rd-file-name { font-size: 12px; color: #4e5969; margin-top: 4px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.rd-foot { margin-top: 14px; padding-top: 10px; border-top: 1px solid #f0f2f5; text-align: right; }
.rd-cust-link { font-size: 12px; color: var(--el-color-info); cursor: pointer; }
.rd-cust-link:hover { color: var(--el-color-primary); }
</style>
