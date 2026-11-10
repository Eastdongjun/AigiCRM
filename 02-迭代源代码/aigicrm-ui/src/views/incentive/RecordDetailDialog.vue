<template>
  <!-- 【2026-11-10 用户令"关键字检索只给上传的那一条记录"】检索命中记录的详情弹窗:
       只呈现该条上传记录(元信息+四槽位文件, 点缩略进灯箱), 不再端出客户拨盘整页;
       底部保留"查看该客户全部记录"小入口(偶尔要看上下文时用, 打开旧拨盘)。 -->
  <el-dialog v-model="visible" width="1100px" top="4vh" :close-on-click-modal="true" destroy-on-close class="rec-dtl-dlg">
    <template #header></template>
    <div class="rd-wrap" v-if="rec">
      <div class="rd-bar">
        <span class="rd-back" @click="visible=false">← 返回列表</span>
        <span class="rd-bar-name">{{ rec.customerName }}</span>
        <span class="rd-bar-cat">{{ rec.categoryName }}</span>
        <span class="rd-bar-pts">+{{ rec.score }} 分</span>
        <el-button size="small" type="primary" plain class="rd-archive-btn" @click="goCustomer">客户完整档案</el-button>
      </div>

      <div class="rd-meta">
        <div class="rd-meta-item"><span class="rd-k">产品</span><span>{{ [rec.specificProduct, rec.relatedProducts].filter(Boolean).join(' · ') || '—' }}</span></div>
        <div class="rd-meta-item"><span class="rd-k">上传人</span><span>{{ rec.userName || '—' }}</span></div>
        <div class="rd-meta-item"><span class="rd-k">周期</span><span>{{ rec.period || '—' }}</span></div>
        <div class="rd-meta-item"><span class="rd-k">时间</span><span>{{ String(rec.createdAt || '').replace('T', ' ').slice(0, 16) }}</span></div>
      </div>

      <!-- 【用户令】大图区: 可左右滑动轮播, 点大图进灯箱看原图 -->
      <!-- 【2026-11-11 用户令"能预览的全给预览"】视频纳入轮播就地播放(此前只图, MP4 被排除在大图区外) -->
      <div v-if="media.length" class="rd-carousel">
        <el-carousel :autoplay="false" height="430px" arrow="always" indicator-position="none" trigger="click">
          <el-carousel-item v-for="f in media" :key="f.id">
            <div v-if="f.type === 'video'" class="rd-slide rd-slide-video">
              <video :src="fileUrl(f.id)" controls preload="metadata" class="rd-video"></video>
              <span class="rd-slide-name">{{ f.label }}</span>
            </div>
            <div v-else class="rd-slide" @click="openPreview(f)">
              <el-image :src="thumbUrl(f.id)" fit="contain" style="width:100%;height:100%" lazy
                        @error="markThumbFailed(f.id)" />
              <span class="rd-slide-name">{{ f.label }}</span>
            </div>
          </el-carousel-item>
        </el-carousel>
      </div>

      <div class="rd-files-title">附件（{{ files.length }}）</div>
      <div v-if="files.length" class="rd-files">
        <div v-for="f in files" :key="f.id" class="rd-file" @click="openPreview(f)">
          <!-- 【2026-11-10 扫描批】thumbFailed 是 Set, 索引访问恒 undefined 致失败图不退化; 对齐 case-library 的 .has() 写法(3891a4e6 漏改点) -->
          <el-image v-if="f.type === 'image' && !thumbFailed.has(f.id)" class="rd-thumb" :src="thumbUrl(f.id)" fit="cover" lazy @error="markThumbFailed(f.id)" />
          <div v-else class="rd-ext">{{ extOf(f.label) }}</div>
          <div class="rd-file-name" :title="f.label">{{ f.label }}</div>
        </div>
      </div>
      <el-empty v-else description="该记录无附件" :image-size="60" />

      <div class="rd-foot">
        <span class="rd-cust-link" @click="goCustomer">查看客户完整档案（拨盘时间轴） →</span>
      </div>
    </div>
    <!-- 灯箱预览(与查询页同源组件): 从缩略打开, 左右翻该记录全部文件 -->
    <SearchLightbox ref="lightbox" :thumb-failed="thumbFailed" @thumb-failed="markThumbFailed" />
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import SearchLightbox from './SearchLightbox.vue'
import { getFiles, thumbUrl, fileUrl } from './search-helpers'

const props = defineProps({ modelValue: Boolean, record: { type: Object, default: null } })
const emit = defineEmits(['update:modelValue', 'open-customer'])

const visible = computed({
  get: () => props.modelValue,
  set: v => emit('update:modelValue', v)
})
// 【用户报"第一下没反应"】直接读 props 消灭 watch 搬运时序(首渲染 rec 曾为 null)
const rec = computed(() => props.record)
const thumbFailed = ref(new Set())
const lightbox = ref(null)
watch(() => props.record, () => { thumbFailed.value = new Set() })

const files = computed(() => rec.value ? getFiles(rec.value) : [])
// 【2026-11-11 用户令"能预览的全给预览"】轮播媒体=图+视频(视频就地播放, 图点进灯箱)
const media = computed(() => files.value.filter(f => f.type === 'image' || f.type === 'video'))
const openPreview = (f) => lightbox.value?.open(f.id, f.type, f.label, files.value)
const markThumbFailed = (id) => { thumbFailed.value.add(id) }
// 【用户报"拨盘打不开"】先关本窗再上抛(避免双层 el-dialog 叠加), 父层负责打开拨盘
const goCustomer = () => { emit('update:modelValue', false); emit('open-customer', rec.value) }
const extOf = (label) => { const e = String(label || '').split('.').pop(); return (e && e !== label ? e : '文件').toUpperCase() }
</script>

<style scoped>
.rd-wrap { padding: 4px 8px 8px; }
.rd-bar { display: flex; align-items: baseline; gap: 10px; padding-bottom: 10px; border-bottom: 1px solid #f0f2f5; margin-bottom: 12px; }
.rd-back { font-size: 13px; color: var(--el-color-primary); cursor: pointer; }
.rd-bar-name { font-size: 16px; font-weight: 700; color: #1f2d3d; }
.rd-bar-cat { font-size: 12px; padding: 1px 8px; border-radius: 10px; background: var(--el-color-primary-light-9); color: var(--el-color-primary); }
.rd-archive-btn { margin-left: 10px; }
.rd-bar-pts { margin-left: auto; font-size: 15px; font-weight: 700; color: #e6a23c; }
.rd-meta { display: grid; grid-template-columns: 1fr 1fr; gap: 8px 16px; padding: 10px 12px; background: #f7f8fa; border-radius: 8px; margin-bottom: 14px; }
.rd-meta-item { display: flex; gap: 8px; font-size: 13px; color: #4e5969; }
.rd-k { color: #9aa3ad; flex-shrink: 0; }
.rd-carousel { border-radius: 10px; overflow: hidden; background: #101216; margin-bottom: 14px; }
.rd-slide { width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; cursor: zoom-in; position: relative; }
/* 【2026-11-11 用户令"能预览的全给预览"】轮播视频项就地播放 */
.rd-slide-video { cursor: default; background: #000; }
.rd-video { width: 100%; height: 100%; object-fit: contain; }
.rd-slide-name { position: absolute; bottom: 10px; left: 50%; transform: translateX(-50%); font-size: 12px; color: rgba(255,255,255,.85); background: rgba(0,0,0,.45); padding: 2px 10px; border-radius: 10px; max-width: 80%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
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
