<template>
  <!--
    【PROTO-AUDIT-20261123】法布施发布器 —— 按原型 v8 的 P6 / P7 / P8 三屏重做。
    P6 每日诵读：双通道 A（先写后读）/ B（三级选书 → 章节 → 诵读）+ 录音试听
    P7 每日随施：朋友圈发布器（大框 + 九宫格 + 工具行）
    P8 再读一本：① 三级选书（类别→书目→版本）+ 版本切换 ② 章节（选已有/自录）③ 三类上传
  -->
  <el-dialog
    :model-value="modelValue"
    :title="dialogTitle"
    :width="form.scopeType === 'suishi' ? '560px' : '720px'"
    class="fabushi-pub-dialog"
    :close-on-click-modal="false"
    @update:model-value="(v) => emit('update:modelValue', v)"
    @closed="resetAll"
  >
    <p v-if="form.scopeType === 'suishi'" class="pub-lead">随手施 · 像发朋友圈一样简单</p>

    <!-- 类型（P7 随施是独立朋友圈发布器，不显示类型切换；由广场入口决定） -->
    <div v-if="form.scopeType !== 'suishi'" class="pub-row">
      <label class="pub-label">类型</label>
      <div class="pub-caps">
        <button
          v-for="tab in TYPE_TABS"
          :key="tab.value"
          type="button"
          class="pub-cap"
          :class="{ active: form.scopeType === tab.value }"
          @click="switchType(tab.value)"
        >{{ tab.label }}</button>
      </div>
    </div>

    <!-- ============ P6 每日诵读 ============ -->
    <template v-if="form.scopeType === 'songdu'">
      <div class="pub-row">
        <label class="pub-label">通道</label>
        <div class="pub-caps">
          <button type="button" class="pub-cap" :class="{ active: form.channel === 'A' }" @click="form.channel = 'A'">A · 先写后读</button>
          <button type="button" class="pub-cap" :class="{ active: form.channel === 'B' }" @click="form.channel = 'B'">B · 按书诵一段</button>
        </div>
        <p class="pub-hint">
          {{ form.channel === 'A'
            ? 'A 通道：先写/传今天要读的文字或图片，再对着它朗读背诵（不必选书）。'
            : 'B 通道：三级选书 → 具体段落 → 诵读，内容会挂到该书的章节下。' }}
        </p>
      </div>

      <template v-if="form.channel === 'B'">
        <div class="pub-row">
          <label class="pub-label">选择具体书 <span class="req">*</span></label>
          <div class="triple">
            <el-select v-model="form.categoryCode" clearable placeholder="类别" @change="onCategoryChange">
              <el-option v-for="c in categoryOptions" :key="c.value" :label="c.label" :value="c.value" />
            </el-select>
            <el-select v-model="form.bookTitle" clearable filterable placeholder="书目" @change="onTitleChange">
              <el-option v-for="t in titleOptions" :key="t" :label="t" :value="t" />
            </el-select>
            <el-select v-model="form.bookId" clearable filterable placeholder="版本">
              <el-option v-for="b in editionOptions" :key="b.id" :label="editionLabel(b)" :value="b.id" />
            </el-select>
          </div>
          <div v-if="editionOptions.length > 1" class="edition-chips">
            <span class="ec-label">版本切换</span>
            <button v-for="b in editionOptions" :key="b.id" type="button" class="ec" :class="{ on: form.bookId === b.id }" @click="form.bookId = b.id">{{ b.publisher || b.editionType || ('版本 ' + b.id) }}</button>
          </div>
        </div>
        <div class="pub-row">
          <label class="pub-label">具体段落 <span class="req">*</span></label>
          <el-select v-model="form.sectionId" filterable clearable placeholder="选择章节（按 部 / 章 / 节 层级）" style="width: 100%">
            <el-option v-for="s in sectionOptions" :key="s.id" :label="s.path" :value="s.id" />
          </el-select>
          <p v-if="form.bookId && !sectionOptions.length" class="pub-hint warn">该书还没有章节结构，先切到「再读一本」用自录章节建立，或改选其他书。</p>
        </div>
      </template>

      <div class="pub-row">
        <label class="pub-label">标题</label>
        <el-input v-model="form.title" maxlength="120" placeholder="给这段诵读起个标题（可选）" />
      </div>
      <div class="pub-row">
        <label class="pub-label">正文</label>
        <el-input v-model="form.content" type="textarea" :rows="5" maxlength="2000" show-word-limit placeholder="今天诵读的原文 / 你的批注…" />
      </div>
      <div class="pub-row">
        <label class="pub-label">诵读语音</label>
        <div class="rec-row">
          <button type="button" class="rec-btn" :class="{ on: rec.active }" @click="rec.active ? stopRec() : startRec()">
            {{ rec.active ? `● 录制中 ${rec.seconds}s / ${MAX_AUDIO_SECONDS}s（点击停止）` : '＋ 录一段' }}
          </button>
          <template v-if="rec.url">
            <audio :src="rec.url" controls class="rec-audio" />
            <el-button size="small" type="primary" @click="saveRec">保存语音</el-button>
            <el-button size="small" @click="clearRec">重录</el-button>
          </template>
          <el-button size="small" @click="pickFile('audio', false)">上传录音</el-button>
          <span class="rec-count">{{ audioAttachments.length }}/{{ MAX_AUDIO }} 段</span>
        </div>
        <div v-if="audioAttachments.length" class="audio-wrap">
          <FabushiAudioList :items="audioAttachments" @remove="(item) => removeAttachment(attachments.indexOf(item))" />
        </div>
        <p class="pub-hint">语音 1-{{ MAX_AUDIO }} 段，单段 ≤{{ MAX_AUDIO_SECONDS }} 秒；保存后可逐段试听，不满意删掉重录。</p>
      </div>
      <div class="pub-row">
        <label class="pub-label">图片</label>
        <div class="img-grid">
          <div v-for="img in images" :key="img.key" class="img-cell">
            <img :src="img.previewUrl" :alt="img.name" />
            <button type="button" class="img-x" @click="removeAttachment(attachments.indexOf(img))">×</button>
          </div>
          <button v-if="images.length < imageLimit" type="button" class="img-add" @click="pickFile('image', false)">
            ＋<span>{{ images.length }}/{{ imageLimit }}</span>
          </button>
        </div>
      </div>
    </template>

    <!-- ============ P7 每日随施 ============ -->
    <template v-else-if="form.scopeType === 'suishi'">
      <div class="pub-row">
        <label class="pub-label">这一刻的想法</label>
        <el-input v-model="form.content" type="textarea" :rows="5" maxlength="2000" show-word-limit placeholder="写点什么…（纯文字也能发，不设标题、不关联书目）" />
      </div>
      <div class="pub-row">
        <label class="pub-label">图片</label>
        <div class="suishi-grid">
          <div v-for="(img, idx) in images" :key="img.key" class="img-cell">
            <img :src="img.previewUrl" :alt="img.name" />
            <button type="button" class="img-x" @click="removeAttachment(attachments.indexOf(img))">×</button>
            <button type="button" class="img-zoom" title="放大" @click="openViewer(idx)">⤢</button>
          </div>
          <button v-if="images.length < imageLimit" type="button" class="img-add" @click="pickFile('image', false)">
            ＋<span>{{ images.length }}/{{ imageLimit }}</span>
          </button>
          <div v-for="n in emptyImageSlots" :key="'e'+n" class="img-empty" />
        </div>
        <div v-if="images.length" class="edit-tools">
          <span class="edit-tools-label">已选图片可：</span>
          <button v-for="(img, idx) in images" :key="'e'+img.key" type="button" class="edit-tool" @click="openEditor(idx, 'crop')">裁剪</button>
          <button v-for="(img, idx) in images" :key="'m'+img.key" type="button" class="edit-tool" @click="openEditor(idx, 'mark')">标注</button>
          <button v-for="(img, idx) in images" :key="'z'+img.key" type="button" class="edit-tool" @click="openViewer(idx)">放大</button>
        </div>
      </div>
      <div class="pub-row">
        <label class="pub-label">工具</label>
        <div class="tool-row">
          <button type="button" class="tool" @click="pickFile('image', false)"><i>🖼</i><span>图片</span></button>
          <button type="button" class="tool" @click="pickFile('image', true)"><i>📷</i><span>拍摄</span></button>
          <button type="button" class="tool" @click="pickFile('video', false)"><i>🎬</i><span>视频</span></button>
          <button type="button" class="tool" :class="{ on: rec.active }" @click="rec.active ? stopRec() : startRec()">
            <i>🎙</i><span>{{ rec.active ? '停止' : '说话' }}</span>
          </button>
          <button type="button" class="tool" @click="pickFile('file', false)"><i>📄</i><span>文件</span></button>
        </div>
        <div v-if="rec.url" class="rec-row" style="margin-top:8px">
          <audio :src="rec.url" controls class="rec-audio" />
          <el-button size="small" type="primary" @click="saveRec">保存语音</el-button>
          <el-button size="small" @click="clearRec">重录</el-button>
        </div>
        <FabushiAudioList :items="audioAttachments" @remove="(item) => removeAttachment(attachments.indexOf(item))" />
      </div>
      <!-- 【S4.3 2026-11-23】P7 工具行的「谁可以看 / 文件归档」按裁决做成静态标注：
           谁可以看=D21 全员共享（不做选择器）；归档位置=D27 文件中心›法布施（后端既有行为，仅显性化）。
           「位置」后端无地理字段，仍不做按钮。 -->
      <div class="pub-row">
        <label class="pub-label">谁可以看</label>
        <div class="pub-static">
          <span class="pub-static-chip">全员可见</span>
          <span class="pub-static-note">社区内容对全员共享；「部分可见 / 不给谁看」按 D21 裁决暂不做。</span>
        </div>
      </div>
      <div class="pub-row">
        <label class="pub-label">附件归档</label>
        <div class="pub-static">
          <span class="pub-static-chip">文件中心 › 法布施</span>
          <span class="pub-static-note">附件统一落在文件中心的法布施空间；内容下架或书目停用后，可见范围回落到上传人。</span>
        </div>
      </div>
      <p class="pub-hint">
        位置：后端暂无地理字段，未做（已登记 <code>docs/prototypes/README.md</code>），不放空按钮占位。
      </p>
      <div v-if="nonImages.length" class="pub-row">
        <label class="pub-label">已选附件</label>
        <div class="file-chips">
          <span v-for="f in nonImages" :key="f.key" class="chip">
            {{ f.name }}
            <button type="button" class="chip-x" @click="removeAttachment(attachments.indexOf(f))">×</button>
          </span>
        </div>
      </div>
    </template>

    <!-- ============ P8 再读一本 ============ -->
    <template v-else>
      <div class="pub-row">
        <label class="pub-label">① 选择具体书 <span class="req">*</span></label>
        <div class="triple">
          <el-select v-model="form.categoryCode" clearable placeholder="类别" @change="onCategoryChange">
            <el-option v-for="c in categoryOptions" :key="c.value" :label="c.label" :value="c.value" />
          </el-select>
          <el-select v-model="form.bookTitle" clearable filterable placeholder="书目" @change="onTitleChange">
            <el-option v-for="t in titleOptions" :key="t" :label="t" :value="t" />
          </el-select>
          <el-select v-model="form.bookId" clearable filterable placeholder="版本">
            <el-option v-for="b in editionOptions" :key="b.id" :label="editionLabel(b)" :value="b.id" />
          </el-select>
        </div>
        <div v-if="editionOptions.length > 1" class="edition-chips">
          <span class="ec-label">版本切换</span>
          <button v-for="b in editionOptions" :key="b.id" type="button" class="ec" :class="{ on: form.bookId === b.id }" @click="form.bookId = b.id">{{ b.publisher || b.editionType || ('版本 ' + b.id) }}</button>
        </div>
      </div>
      <div class="pub-row">
        <label class="pub-label">② 确定章节 <span class="req">*</span></label>
        <div class="pub-caps">
          <button type="button" class="pub-cap" :class="{ active: form.sectionMode === 'pick' }" @click="form.sectionMode = 'pick'">选择已有章节</button>
          <button type="button" class="pub-cap" :class="{ active: form.sectionMode === 'manual' }" @click="form.sectionMode = 'manual'">自我录入章节</button>
        </div>
        <template v-if="form.sectionMode === 'pick'">
          <el-select v-model="form.sectionId" filterable clearable placeholder="选择章节（按 部 / 章 / 节 层级）" style="width: 100%; margin-top: 8px">
            <el-option v-for="s in sectionOptions" :key="s.id" :label="s.path" :value="s.id" />
          </el-select>
          <p v-if="form.bookId && !sectionOptions.length" class="pub-hint warn">该书还没有章节结构，请切到「自我录入章节」。</p>
        </template>
        <el-input v-else v-model="form.manualSection" maxlength="100" placeholder="例如：第八章 · 上善若水" style="margin-top: 8px" />
      </div>
      <div class="pub-row">
        <label class="pub-label">③ 上传内容 <span class="req">*</span></label>
        <el-input v-model="form.content" type="textarea" :rows="5" maxlength="2000" show-word-limit placeholder="这一章读完的想法 / 摘抄 / 批注…" />
        <div class="tool-row">
          <button type="button" class="tool" @click="pickFile('image', false)"><i>📖</i><span>书页图片</span></button>
          <button type="button" class="tool" :class="{ on: rec.active }" @click="rec.active ? stopRec() : startRec()">
            <i>🎙</i><span>{{ rec.active ? '停止' : '诵读语音' }}</span>
          </button>
          <button type="button" class="tool" @click="pickFile('file', false)"><i>📄</i><span>文件 / 视频</span></button>
        </div>
        <div class="img-grid">
          <div v-for="img in images" :key="img.key" class="img-cell">
            <img :src="img.previewUrl" :alt="img.name" />
            <button type="button" class="img-x" @click="removeAttachment(attachments.indexOf(img))">×</button>
          </div>
          <button v-if="images.length < imageLimit" type="button" class="img-add" @click="pickFile('image', false)">
            ＋<span>{{ images.length }}/{{ imageLimit }}</span>
          </button>
        </div>
        <div v-if="rec.url" class="rec-row" style="margin-top:8px">
          <audio :src="rec.url" controls class="rec-audio" />
          <el-button size="small" type="primary" @click="saveRec">保存语音</el-button>
          <el-button size="small" @click="clearRec">重录</el-button>
        </div>
        <FabushiAudioList :items="audioAttachments" @remove="(item) => removeAttachment(attachments.indexOf(item))" />
        <div v-if="nonImages.length" class="file-chips">
          <span v-for="f in nonImages" :key="f.key" class="chip">
            {{ f.name }}
            <button type="button" class="chip-x" @click="removeAttachment(attachments.indexOf(f))">×</button>
          </span>
        </div>
      </div>
    </template>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">{{ submitLabel }}</el-button>
    </template>

    <!-- 统一文件选择器：capture=true 走相机 -->
    <input ref="fileInputRef" type="file" style="display:none" @change="onFileChosen" />

    <!-- P7 图片编辑：裁剪 / 标注（Canvas，不伪造按钮） -->
    <el-dialog v-model="editor.visible" title="编辑图片" width="720px" append-to-body @closed="resetEditor">
      <div class="editor-stage" @mousedown="onEditorDown" @mousemove="onEditorMove" @mouseup="onEditorUp" @mouseleave="onEditorUp">
        <canvas ref="editorCanvasRef" />
      </div>
      <div class="edit-tools" style="margin-top:10px">
        <span class="edit-tools-label">模式：</span>
        <button type="button" class="edit-tool" :class="{ on: editor.mode === 'crop' }" @click="setEditorMode('crop')">裁剪</button>
        <button type="button" class="edit-tool" :class="{ on: editor.mode === 'mark' }" @click="setEditorMode('mark')">标注</button>
        <span class="pub-hint" style="margin-left:8px">{{ editor.mode === 'crop' ? '拖拽选择裁剪区域，点“应用”' : '拖拽画红框，点“应用”' }}</span>
      </div>
      <template #footer>
        <el-button @click="editor.visible = false">取消</el-button>
        <el-button type="primary" :loading="editor.saving" @click="applyEditor">应用</el-button>
      </template>
    </el-dialog>

    <el-image-viewer v-if="viewer.visible" :url-list="images.map(i => i.previewUrl)" :initial-index="viewer.index" @close="viewer.visible = false" />
  </el-dialog>
</template>

<script setup>
/**
 * 发布器（P6/P7/P8 三屏合一）。
 *
 * 设计原则（沿用本仓库的「不做假功能」）：
 * - 每个按钮都接到真实接口；后端没有的能力（位置、谁可以看）不做占位按钮，只在提示里说明。
 * - 章节「自我录入」走后端 `/sections/self`（fabushi:create 权限 + 同名幂等），不是只在前端拼字符串。
 * - 录音用 MediaRecorder 真录，上传走 `/file/upload`（scope=fabushi），与图片同一条链路。
 */
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { ElImageViewer, ElMessage } from 'element-plus'
import request from '@/utils/request'
import { addFabushiAttachment, createFabushiPost, getFabushiBooks, getFabushiSections } from '@/api/fabushi'
import FabushiAudioList from './FabushiAudioList.vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  defaultScope: { type: String, default: 'songdu' },
  presetBookId: { type: [Number, String], default: null },
})
const emit = defineEmits(['update:modelValue', 'published'])

const TYPE_TABS = [
  { value: 'songdu', label: '每日诵读' },
  { value: 'suishi', label: '每日随施' },
  { value: 'zaidu', label: '再读一本' },
]

const form = reactive({
  scopeType: 'songdu',
  channel: 'A',
  bookId: null,
  sectionId: null,
  sectionMode: 'pick',
  manualSection: '',
  title: '',
  content: '',
  categoryCode: null,
  bookTitle: null,
})

const submitting = ref(false)
const books = ref([])
const sections = ref([])
const attachments = ref([])
const fileInputRef = ref(null)
const pendingPick = reactive({ type: 'image', capture: false })
let seq = 0

const dialogTitle = computed(() => {
  const t = TYPE_TABS.find((x) => x.value === form.scopeType)
  return `发布 · ${t ? t.label : '内容'}`
})
const submitLabel = computed(() => {
  if (form.scopeType === 'songdu') return '发布每日诵读'
  if (form.scopeType === 'suishi') return '发表'
  return '发布再读一本'
})

/* ---------------- 三级选书：类别 → 书目 → 版本 ---------------- */
const categoryOptions = computed(() => {
  const seen = new Map()
  books.value.forEach((b) => {
    if (!b.categoryCode) return
    if (!seen.has(b.categoryCode)) seen.set(b.categoryCode, b.categoryName || b.categoryCode)
  })
  return [...seen.entries()].map(([value, label]) => ({ value, label }))
})
const titleOptions = computed(() => {
  const set = new Set()
  books.value.filter((b) => !form.categoryCode || b.categoryCode === form.categoryCode).forEach((b) => b.title && set.add(b.title))
  return [...set]
})
const editionOptions = computed(() => books.value.filter((b) => {
  if (form.categoryCode && b.categoryCode !== form.categoryCode) return false
  if (form.bookTitle && b.title !== form.bookTitle) return false
  return true
}))
const editionLabel = (b) => [b.publisher, b.editionType, b.pages ? `${b.pages} 页` : ''].filter(Boolean).join(' · ') || '默认版本'

const onCategoryChange = () => { form.bookTitle = null; form.bookId = null; form.sectionId = null }
const onTitleChange = () => {
  form.sectionId = null
  const first = editionOptions.value[0]
  form.bookId = first ? first.id : null
}

/* ---------------- 章节树 ---------------- */
const LEVEL_LABEL = { part: '部', chapter: '章', section: '节', preface: '序' }
const sectionOptions = computed(() => {
  const out = []
  const walk = (nodes, prefix) => {
    ;(nodes || []).forEach((n) => {
      const label = `${LEVEL_LABEL[n.levelCode] || ''}${n.title}`
      const path = prefix ? `${prefix} / ${label}` : label
      out.push({ id: n.id, path })
      if (n.children && n.children.length) walk(n.children, path)
    })
  }
  walk(sections.value, '')
  return out
})

const loadSections = async (bookId) => {
  sections.value = []
  if (!bookId) return
  try {
    const { data } = await getFabushiSections(bookId)
    sections.value = (data && data.sections) || []
  } catch (e) {
    sections.value = []
  }
}
watch(() => form.bookId, (id) => { form.sectionId = null; loadSections(id) })

/* ---------------- 录音（MediaRecorder） ---------------- */
const rec = reactive({ active: false, seconds: 0, url: '', file: null })
let mediaRecorder = null
let chunks = []
let timer = null

const startRec = async () => {
  if (rec.active) return
  if (audioAttachments.value.length >= MAX_AUDIO) {
    ElMessage.warning(`语音最多 ${MAX_AUDIO} 段，请先删除不用的段`)
    return
  }
  if (!window.isSecureContext || !navigator.mediaDevices || !window.MediaRecorder) {
    ElMessage.warning('当前是 HTTP 或浏览器不支持麦克风；已切换为上传录音，请用手机录音后选择文件，或改用 HTTPS 打开')
    pickFile('audio', false)
    return
  }
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    chunks = []
    mediaRecorder = new MediaRecorder(stream)
    mediaRecorder.ondataavailable = (e) => { if (e.data && e.data.size) chunks.push(e.data) }
    mediaRecorder.onstop = () => {
      const type = mediaRecorder.mimeType || 'audio/webm'
      const blob = new Blob(chunks, { type })
      const ext = type.includes('mp4') ? 'm4a' : type.includes('ogg') ? 'ogg' : 'webm'
      rec.file = new File([blob], `rec-${Date.now()}.${ext}`, { type })
      if (rec.url) URL.revokeObjectURL(rec.url)
      rec.url = URL.createObjectURL(blob)
      stream.getTracks().forEach((t) => t.stop())
    }
    mediaRecorder.start()
    rec.active = true
    rec.seconds = 0
    timer = setInterval(() => {
      rec.seconds += 1
      // 【P6】后端单段上限 60s，前端到点自动停，避免录完才被 400 打回、白录一遍
      if (rec.seconds >= MAX_AUDIO_SECONDS) {
        stopRec()
        ElMessage.info(`单段语音上限 ${MAX_AUDIO_SECONDS} 秒，已自动停止`)
      }
    }, 1000)
  } catch (e) {
    ElMessage.warning('无法访问麦克风，请检查浏览器权限')
  }
}
const stopRec = () => {
  if (!rec.active) return
  if (timer) { clearInterval(timer); timer = null }
  try { mediaRecorder && mediaRecorder.stop() } catch (e) { /* 已停止 */ }
  rec.active = false
}
const clearRec = () => {
  if (rec.url) URL.revokeObjectURL(rec.url)
  rec.url = ''
  rec.file = null
  rec.seconds = 0
}
const saveRec = async () => {
  if (!rec.file) return
  const item = await uploadOne(rec.file, 'audio')
  if (item) { attachments.value.push(item); clearRec(); ElMessage.success('语音已上传') }
}

/* ---------------- 附件上传 ---------------- */
const pickFile = (type, capture) => {
  pendingPick.type = type
  pendingPick.capture = capture
  const el = fileInputRef.value
  if (!el) return
  el.accept = type === 'image' ? 'image/*' : type === 'video' ? 'video/*' : type === 'audio' ? 'audio/*' : ''
  if (capture) el.setAttribute('capture', 'environment')
  else el.removeAttribute('capture')
  el.value = ''
  el.click()
}

const guessType = (file) => (file.type.startsWith('image/') ? 'image' : file.type.startsWith('audio/') ? 'audio' : file.type.startsWith('video/') ? 'video' : 'file')

const uploadOne = async (file, fileType) => {
  const fd = new FormData()
  fd.append('file', file)
  fd.append('scope', 'fabushi')
  const res = await request.post('/file/upload', fd)
  const id = res.data && res.data.id
  if (!id) return null
  seq += 1
  return {
    key: `a${seq}`,
    fileId: id,
    name: file.name,
    fileType,
    // 【P6 2026-11-23】音/视频也给本地 blob URL：保存后仍可试听/预览，不必回源下载
    previewUrl: ['image', 'audio', 'video'].includes(fileType) ? URL.createObjectURL(file) : '',
  }
}

const imageLimit = computed(() => (form.scopeType === 'suishi' ? 9 : 5))
const emptyImageSlots = computed(() => Math.max(0, 9 - images.value.length - (images.value.length < imageLimit.value ? 1 : 0)))
const images = computed(() => attachments.value.filter((a) => a.fileType === 'image'))
// 语音有独立可播放列表（FabushiAudioList），文件胶囊只列其余非图片附件，避免同一段出现两次
const nonImages = computed(() => attachments.value.filter((a) => a.fileType !== 'image' && a.fileType !== 'audio'))
// 【P6 2026-11-23】原型「语音 1-10 段 × ≤60s」：已保存段可逐条试听/删除，超限与超时前端先挡
const MAX_AUDIO = 10
const MAX_AUDIO_SECONDS = 60

/* ---------------- P7 图片编辑：九宫格放大 / 裁剪 / 标注 ---------------- */
const editorCanvasRef = ref(null)
const editor = reactive({ visible: false, index: -1, mode: 'crop', saving: false })
const viewer = reactive({ visible: false, index: 0 })
let editorImage = null
let editorScale = 1
let dragStart = null
let dragEnd = null
let editorMarks = []
let cropRect = null

const openViewer = (idx) => { viewer.index = idx; viewer.visible = true }
const setEditorMode = (mode) => { editor.mode = mode; dragStart = null; dragEnd = null; cropRect = null; drawEditor() }
const resetEditor = () => {
  editorImage = null; dragStart = null; dragEnd = null; editorMarks = []; cropRect = null
  editor.index = -1; editor.saving = false
}
const openEditor = (idx, mode) => {
  const item = images.value[idx]
  if (!item) return
  const img = new Image()
  img.onload = () => {
    editorImage = img
    editor.index = idx
    editor.mode = mode || 'crop'
    dragStart = null; dragEnd = null; editorMarks = []; cropRect = null
    editor.visible = true
    nextTick(() => {
      const canvas = editorCanvasRef.value
      if (!canvas) return
      const maxW = 640; const maxH = 420
      const scale = Math.min(1, maxW / img.naturalWidth, maxH / img.naturalHeight)
      editorScale = scale
      canvas.width = Math.max(1, Math.round(img.naturalWidth * scale))
      canvas.height = Math.max(1, Math.round(img.naturalHeight * scale))
      drawEditor()
    })
  }
  img.onerror = () => ElMessage.error('图片加载失败')
  img.src = item.previewUrl
}

const drawEditor = () => {
  const canvas = editorCanvasRef.value
  if (!canvas || !editorImage) return
  const ctx = canvas.getContext('2d')
  ctx.clearRect(0, 0, canvas.width, canvas.height)
  ctx.drawImage(editorImage, 0, 0, canvas.width, canvas.height)
  ctx.save()
  ctx.strokeStyle = '#e6a23c'
  ctx.lineWidth = 2
  ctx.setLineDash([6, 4])
  if (dragStart && dragEnd) {
    ctx.strokeRect(dragStart.x, dragStart.y, dragEnd.x - dragStart.x, dragEnd.y - dragStart.y)
  } else if (cropRect && editor.mode === 'crop') {
    ctx.strokeRect(cropRect.x, cropRect.y, cropRect.w, cropRect.h)
  }
  ctx.setLineDash([])
  ctx.strokeStyle = '#f56c6c'
  ctx.lineWidth = 3
  editorMarks.forEach((m) => ctx.strokeRect(m.x, m.y, m.w, m.h))
  ctx.restore()
}

const editorPoint = (e) => {
  const canvas = editorCanvasRef.value
  const rect = canvas.getBoundingClientRect()
  return {
    x: (e.clientX - rect.left) * canvas.width / rect.width,
    y: (e.clientY - rect.top) * canvas.height / rect.height,
  }
}
const onEditorDown = (e) => {
  if (editor.mode !== 'crop' && editor.mode !== 'mark') return
  if (editor.mode === 'crop') cropRect = null
  dragStart = editorPoint(e); dragEnd = { ...dragStart }; drawEditor()
}
const onEditorMove = (e) => {
  if (!dragStart) return
  dragEnd = editorPoint(e); drawEditor()
}
const onEditorUp = () => {
  if (!dragStart || !dragEnd) return
  const x = Math.min(dragStart.x, dragEnd.x)
  const y = Math.min(dragStart.y, dragEnd.y)
  const w = Math.abs(dragEnd.x - dragStart.x)
  const h = Math.abs(dragEnd.y - dragStart.y)
  if (w > 4 && h > 4) {
    if (editor.mode === 'crop') cropRect = { x, y, w, h }
    if (editor.mode === 'mark') editorMarks.push({ x, y, w, h })
  }
  dragStart = null; dragEnd = null; drawEditor()
}

const applyEditor = async () => {
  const canvas = editorCanvasRef.value
  if (!canvas || !editorImage) return
  let out
  if (editor.mode === 'crop') {
    // 裁剪区域在 onEditorUp 保存到 cropRect
    if (!cropRect) { ElMessage.warning('请先拖拽选择裁剪区域'); return }
    const sx = cropRect.x / editorScale
    const sy = cropRect.y / editorScale
    const sw = cropRect.w / editorScale
    const sh = cropRect.h / editorScale
    if (sw < 8 || sh < 8) { ElMessage.warning('裁剪区域太小'); return }
    out = document.createElement('canvas')
    out.width = Math.max(1, Math.round(sw)); out.height = Math.max(1, Math.round(sh))
    out.getContext('2d').drawImage(editorImage, sx, sy, sw, sh, 0, 0, out.width, out.height)
  } else {
    out = document.createElement('canvas')
    out.width = canvas.width; out.height = canvas.height
    const ctx = out.getContext('2d')
    ctx.drawImage(editorImage, 0, 0, out.width, out.height)
    ctx.strokeStyle = '#f56c6c'; ctx.lineWidth = 3
    editorMarks.forEach((m) => ctx.strokeRect(m.x, m.y, m.w, m.h))
  }
  editor.saving = true
  try {
    const blob = await new Promise((resolve) => out.toBlob(resolve, 'image/png'))
    if (!blob) throw new Error('导出图片失败')
    const file = new File([blob], `edited-${Date.now()}.png`, { type: 'image/png' })
    const item = await uploadOne(file, 'image')
    if (!item) throw new Error('上传失败')
    const old = images.value[editor.index]
    const oldIndex = attachments.value.indexOf(old)
    if (oldIndex >= 0) attachments.value.splice(oldIndex, 1, item)
    else attachments.value.push(item)
    if (old && old.previewUrl) URL.revokeObjectURL(old.previewUrl)
    editor.visible = false
    ElMessage.success('图片已更新')
  } catch (e) {
    ElMessage.error(e.message || '图片编辑失败')
  } finally {
    editor.saving = false
  }
}

const audioAttachments = computed(() => attachments.value.filter((a) => a.fileType === 'audio'))

const onFileChosen = async (event) => {
  const files = Array.from(event.target.files || [])
  event.target.value = ''
  if (!files.length) return
  const type = pendingPick.type === 'file' ? guessType(files[0]) : pendingPick.type
  const room = type === 'image' ? Math.max(0, imageLimit.value - images.value.length) : 9
  if (room <= 0) { ElMessage.warning(`图片最多 ${imageLimit.value} 张`); return }
  for (const f of files.slice(0, room)) {
    const item = await uploadOne(f, type === 'file' ? guessType(f) : type)
    if (item) attachments.value.push(item)
  }
}
const removeAttachment = (index) => {
  if (index < 0) return
  const item = attachments.value[index]
  if (item && item.previewUrl) URL.revokeObjectURL(item.previewUrl)
  attachments.value.splice(index, 1)
}

/* ---------------- 类型切换 / 重置 ---------------- */
const switchType = (value) => {
  form.scopeType = value
  form.sectionId = null
  form.manualSection = ''
  form.title = ''
  if (value === 'suishi') {
    form.bookId = null
    form.categoryCode = null
    form.bookTitle = null
  }
}

const resetAll = () => {
  clearRec()
  attachments.value.forEach((a) => a.previewUrl && URL.revokeObjectURL(a.previewUrl))
  attachments.value = []
  form.scopeType = props.defaultScope || 'songdu'
  form.channel = 'A'
  form.bookId = props.presetBookId ? Number(props.presetBookId) : null
  form.sectionId = null
  form.sectionMode = 'pick'
  form.manualSection = ''
  form.title = ''
  form.content = ''
  form.categoryCode = null
  form.bookTitle = null
}

/* ---------------- 提交 ---------------- */
const submit = async () => {
  const isSuishi = form.scopeType === 'suishi'
  const isZaidu = form.scopeType === 'zaidu'
  const isSongdu = form.scopeType === 'songdu'

  if (isSuishi) {
    if (!form.content.trim() && !attachments.value.length) { ElMessage.warning('随施写点内容吧（一句话也行）'); return }
  } else {
    if (!form.bookId) { ElMessage.warning('请先选择具体书目'); return }
    if (isZaidu) {
      if (form.sectionMode === 'pick' && !form.sectionId) { ElMessage.warning('请选择章节'); return }
      if (form.sectionMode === 'manual' && !form.manualSection.trim()) { ElMessage.warning('请填写章节名称'); return }
      if (!form.content.trim() && !attachments.value.length) { ElMessage.warning('再读一本请写下这一章的所得，或上传书页/语音'); return }
    } else if (isSongdu && form.channel === 'B') {
      if (!form.sectionId) { ElMessage.warning('B 通道请选择具体段落'); return }
    }
    if (!form.title.trim() && !form.content.trim() && !attachments.value.length) {
      ElMessage.warning('标题、正文、附件至少填一项'); return
    }
  }

  submitting.value = true
  try {
    let sectionId = null
    if (isZaidu || (isSongdu && form.channel === 'B')) {
      if (isZaidu && form.sectionMode === 'manual') {
        const { data } = await request.post(`/fabushi/books/${form.bookId}/sections/self`, { title: form.manualSection.trim() })
        sectionId = data && data.id
      } else {
        sectionId = form.sectionId
      }
    }
    const created = await createFabushiPost({
      scopeType: form.scopeType,
      bookId: isSuishi ? null : form.bookId,
      title: isSuishi ? null : (form.title.trim() || null),
      content: form.content.trim() || null,
      sectionId,
    })
    const postId = created.data && created.data.id
    for (const f of attachments.value) {
      await addFabushiAttachment(postId, {
        fileId: f.fileId,
        fileType: f.fileType,
        durationSeconds: f.fileType === 'audio' ? (rec.seconds || null) : null,
      })
    }
    ElMessage.success('发布成功')
    emit('published', postId)
    emit('update:modelValue', false)
  } catch (e) {
    // request 拦截器已弹错，这里不重复提示
  } finally {
    submitting.value = false
  }
}

watch(() => props.modelValue, async (visible) => {
  if (!visible) return
  resetAll()
  if (!books.value.length) {
    try {
      const { data } = await getFabushiBooks({ status: 'approved', current: 1, size: 500 })
      books.value = (data && data.records) || []
    } catch (e) {
      books.value = []
    }
  }
  if (form.bookId) await loadSections(form.bookId)
})

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
  try { mediaRecorder && mediaRecorder.stop() } catch (e) { /* ignore */ }
})
</script>

<style scoped>
.pub-row { margin-bottom: 14px; }
.pub-label { display: block; margin-bottom: 6px; font-size: 13px; font-weight: 700; color: var(--text-primary); }
.req { color: var(--el-color-danger); }
.pub-caps { display: flex; gap: 6px; }
.pub-cap { flex: 1; padding: 7px 10px; border: 1px solid var(--el-border-color); border-radius: 8px; background: var(--el-fill-color-blank); color: var(--text-secondary); font-size: 13px; cursor: pointer; }
.pub-cap.active { border-color: var(--el-color-primary); background: var(--el-color-primary-light-9); color: var(--el-color-primary); font-weight: 700; }
.pub-hint { margin: 6px 0 0; font-size: 12px; color: var(--text-muted); line-height: 1.6; }
.pub-hint.warn { color: var(--el-color-warning); }
/* 【S4.3】静态标注行（谁可以看 / 附件归档）：只展示事实，不做成可点按钮 */
.pub-static { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.pub-static-chip { padding: 3px 10px; border: 1px solid var(--border-light); border-radius: 999px; color: var(--text-secondary); background: var(--el-fill-color-lighter); font-size: 12px; font-weight: 600; }
.pub-static-note { color: var(--text-muted); font-size: 12px; line-height: 1.6; }
.triple { display: grid; grid-template-columns: 1fr 1fr 1.2fr; gap: 8px; }
.edition-chips { display: flex; align-items: center; gap: 6px; margin-top: 8px; flex-wrap: wrap; }
.ec-label { font-size: 12px; color: var(--text-muted); }
.ec { padding: 3px 10px; border: 1px solid var(--el-border-color); border-radius: 999px; background: var(--el-fill-color-blank); font-size: 12px; cursor: pointer; color: var(--text-secondary); }
.ec.on { border-color: var(--el-color-primary); color: var(--el-color-primary); font-weight: 700; }
.tool-row { display: flex; gap: 8px; margin-top: 8px; flex-wrap: wrap; }
.tool { display: inline-flex; flex-direction: column; align-items: center; gap: 2px; width: 64px; padding: 8px 0; border: 1px solid var(--el-border-color); border-radius: 10px; background: var(--el-fill-color-blank); cursor: pointer; font-size: 11px; color: var(--text-secondary); }
.tool i { font-style: normal; font-size: 16px; }
.tool.on { border-color: var(--el-color-danger); color: var(--el-color-danger); }
.img-grid { display: grid; grid-template-columns: repeat(3, 96px); gap: 8px; margin-top: 8px; }
.img-cell { position: relative; width: 96px; height: 96px; border-radius: 8px; overflow: hidden; border: 1px solid var(--el-border-color); }
.img-cell img { width: 100%; height: 100%; object-fit: cover; }
.img-x { position: absolute; top: 2px; right: 2px; width: 20px; height: 20px; border: 0; border-radius: 50%; background: rgba(0,0,0,.55); color: #fff; cursor: pointer; line-height: 1; }
.img-add { width: 96px; height: 96px; display: inline-flex; flex-direction: column; align-items: center; justify-content: center; gap: 2px; border: 1px dashed var(--el-border-color); border-radius: 8px; background: var(--el-fill-color-blank); color: var(--text-muted); font-size: 18px; cursor: pointer; }
.img-add span { font-size: 11px; }
.rec-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.rec-btn { padding: 7px 14px; border: 1px solid var(--el-border-color); border-radius: 8px; background: var(--el-fill-color-blank); cursor: pointer; font-size: 13px; color: var(--text-secondary); }
.rec-btn.on { border-color: var(--el-color-danger); color: var(--el-color-danger); font-weight: 700; }
.rec-audio { height: 32px; }
.rec-count { margin-left: auto; color: var(--text-placeholder); font-size: 11px; font-variant-numeric: tabular-nums; }
.file-chips { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 8px; }
.chip { display: inline-flex; align-items: center; gap: 4px; padding: 3px 8px; border-radius: 999px; background: var(--el-fill-color-light); font-size: 12px; }
.chip-x { border: 0; background: transparent; color: var(--text-muted); cursor: pointer; font-size: 14px; line-height: 1; }

.pub-lead { margin: 0 0 10px; color: var(--text-muted); font-size: 12px; }
:global(.fabushi-pub-dialog .el-dialog__body) { max-height: calc(100vh - 300px); overflow-y: auto; }
.suishi-grid { display: grid; grid-template-columns: repeat(3, 96px); gap: 8px; margin-top: 8px; }
.img-empty { width: 96px; height: 96px; border: 1px dashed var(--el-border-color-lighter); border-radius: 8px; background: var(--el-fill-color-lighter); opacity: .55; }
.img-zoom { position: absolute; left: 2px; bottom: 2px; width: 20px; height: 20px; border: 0; border-radius: 4px; background: rgba(0,0,0,.55); color: #fff; cursor: pointer; line-height: 1; font-size: 12px; }
.edit-tools { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; margin-top: 8px; }
.edit-tools-label { color: var(--text-muted); font-size: 12px; }
.edit-tool { padding: 3px 10px; border: 1px solid var(--el-border-color); border-radius: 999px; background: var(--el-fill-color-blank); color: var(--text-secondary); font-size: 12px; cursor: pointer; }
.edit-tool.on, .edit-tool:hover { border-color: var(--el-color-primary); color: var(--el-color-primary); }
.editor-stage { display: flex; justify-content: center; align-items: center; min-height: 240px; background: #111; border-radius: 8px; overflow: hidden; }
.editor-stage canvas { display: block; max-width: 100%; cursor: crosshair; }
</style>
