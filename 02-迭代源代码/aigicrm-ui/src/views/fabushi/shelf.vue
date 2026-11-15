<template>
  <div class="fabushi-shelf">
    <div class="page-head">
      <div><h1>我的阅读</h1><p>书架 · 阅读记录 · 提醒设置</p></div>
      <el-button @click="router.push('/fabushi/library')">去书目浏览</el-button>
    </div>

    <div class="tabs">
      <button v-for="t in tabs" :key="t.value" type="button" class="tab" :class="{ active: active === t.value }" @click="active = t.value">{{ t.label }}</button>
    </div>

    <section v-if="active === 'shelf'" class="panel" v-loading="loading">
      <div v-if="!shelf.length" class="empty">书架还是空的，去书目浏览加入一本吧。</div>
      <article v-for="book in shelf" :key="book.id" class="shelf-row">
        <div class="cover">{{ (book.title || '书').slice(0, 4) }}</div>
        <div class="shelf-main">
          <div class="shelf-title">{{ book.title }}</div>
          <div class="shelf-meta">{{ book.author || '—' }}<template v-if="book.pages"> · {{ book.pages }} 页</template><template v-if="book.lastPage"> · 读到第 {{ book.lastPage }} 页</template></div>
          <div class="progress"><i :style="{ width: progressWidth(book) }" /></div>
        </div>
        <el-select :model-value="book.status" style="width: 96px" @change="(v) => changeStatus(book, v)">
          <el-option label="在读" value="reading" /><el-option label="已读完" value="finished" /><el-option label="收藏" value="favorite" />
        </el-select>
        <el-button v-if="book.pdfFileId" type="primary" link @click="router.push(`/fabushi/read/${book.bookId}`)">继续阅读</el-button>
        <el-button link type="danger" @click="remove(book)">移除</el-button>
      </article>
    </section>

    <section v-else-if="active === 'records'" class="panel">
      <div class="records-head">
        <span>阅读记录</span>
        <el-input v-model="month" placeholder="YYYY-MM" style="width: 130px" @change="loadRecords" />
      </div>
      <div class="record-stats"><span>本月阅读 <b>{{ records.readDays || 0 }}</b> 天</span><span>阅读 <b>{{ records.readCount || 0 }}</b> 次</span></div>
      <div class="record-list">
        <div v-for="day in records.days || []" :key="day.date" class="record-row"><span>{{ day.date }}</span><b>{{ day.count }} 次</b></div>
        <div v-if="!(records.days || []).length" class="empty">本月暂无阅读记录</div>
      </div>
    </section>

    <section v-else class="panel">
      <div class="settings-head">阅读提醒</div>
      <el-form label-width="120px" class="settings-form">
        <el-form-item label="缺卡提醒"><el-switch v-model="settings.reminderEnabled" :active-value="1" :inactive-value="0" /></el-form-item>
        <el-form-item label="频率">
          <el-select v-model="settings.freqCode" style="width: 180px">
            <el-option label="每天" value="daily" /><el-option label="工作日" value="weekday" /><el-option label="休息日" value="weekend" />
          </el-select>
        </el-form-item>
        <el-form-item label="提醒时间"><el-time-picker v-model="settings.remindTime" format="HH:mm" value-format="HH:mm" placeholder="20:00" /></el-form-item>
        <el-form-item label="木鱼音效"><el-switch v-model="settings.soundEnabled" :active-value="1" :inactive-value="0" /></el-form-item>
        <el-form-item><el-button type="primary" :loading="saving" @click="saveSettings">保存设置</el-button></el-form-item>
      </el-form>
      <p class="hint">微信订阅消息授权与降级策略为小程序端能力；PC 仅保存个人提醒设置。</p>
    </section>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getFabushiShelf, updateFabushiShelf, removeFabushiShelf, getFabushiRecords, getFabushiSettings, saveFabushiSettings,
} from '@/api/fabushi'

const router = useRouter()
const tabs = [{ value: 'shelf', label: '我的书架' }, { value: 'records', label: '阅读记录' }, { value: 'settings', label: '提醒设置' }]
const active = ref('shelf')
const loading = ref(false)
const saving = ref(false)
const shelf = ref([])
const month = ref(new Date().toISOString().slice(0, 7))
const records = ref({ days: [], readDays: 0, readCount: 0 })
const settings = reactive({ reminderEnabled: 0, freqCode: 'daily', remindTime: '20:00', soundEnabled: 1 })
const progressWidth = (book) => {
  if (!book.totalPages || !book.lastPage) return '0%'
  return Math.min(100, Math.round(Number(book.lastPage) * 100 / Number(book.totalPages))) + '%'
}

const loadShelf = async () => {
  loading.value = true
  try { const { data } = await getFabushiShelf(); shelf.value = (data && data.records) || [] } finally { loading.value = false }
}
const loadRecords = async () => {
  const { data } = await getFabushiRecords(month.value)
  records.value = data || { days: [], readDays: 0, readCount: 0 }
}
const loadSettings = async () => {
  const { data } = await getFabushiSettings()
  Object.assign(settings, data || {})
}
const changeStatus = async (book, status) => {
  await updateFabushiShelf(book.id, { status })
  book.status = status
  ElMessage.success('已更新书架')
}
const remove = async (book) => {
  await removeFabushiShelf(book.id)
  shelf.value = shelf.value.filter((item) => item.id !== book.id)
  ElMessage.success('已移除')
}
const saveSettings = async () => {
  saving.value = true
  try { await saveFabushiSettings({ ...settings }); ElMessage.success('设置已保存') } finally { saving.value = false }
}

onMounted(async () => { await Promise.all([loadShelf(), loadRecords(), loadSettings()]) })
</script>

<style scoped>
.fabushi-shelf { display: flex; flex-direction: column; gap: 10px; }
.page-head { display: flex; justify-content: space-between; align-items: flex-start; }
.page-head h1 { margin: 0; color: var(--text-primary); font-size: 20px; font-weight: 800; }
.page-head p { margin: 4px 0 0; color: var(--text-muted); font-size: 12px; }
.tabs { display: flex; gap: 4px; padding: 4px; border-radius: var(--radius-md); background: var(--el-fill-color-light); }
.tab { flex: 1; padding: 8px; border: 0; border-radius: var(--radius-sm); color: var(--text-secondary); background: transparent; font-size: 13px; font-weight: 600; cursor: pointer; }
.tab.active { color: var(--el-color-white); background: var(--hero-gradient); }
.panel { padding: 14px 16px; border: 1px solid var(--border-light); border-radius: var(--radius-md); background: var(--el-bg-color); }
.shelf-row { display: flex; align-items: center; gap: 12px; padding: 12px 0; border-bottom: 1px solid var(--border-light); }
.shelf-row:last-child { border-bottom: 0; }
.cover { display: flex; align-items: center; justify-content: center; width: 42px; height: 56px; padding: 4px; border-radius: var(--radius-sm); color: var(--el-color-white); background: var(--hero-gradient); font-size: 10px; text-align: center; line-height: 1.2; }
.shelf-main { flex: 1; min-width: 0; }
.shelf-title { color: var(--text-primary); font-size: 13px; font-weight: 700; }
.shelf-meta { margin-top: 5px; color: var(--text-muted); font-size: 11px; }
.progress { height: 4px; margin-top: 7px; border-radius: var(--radius-pill); background: var(--el-fill-color-light); overflow: hidden; }
.progress i { display: block; height: 100%; background: var(--primary-color); }
.records-head { display: flex; justify-content: space-between; align-items: center; color: var(--text-primary); font-size: 13px; font-weight: 700; }
.record-stats { display: flex; gap: 24px; margin-top: 12px; color: var(--text-muted); font-size: 12px; }
.record-stats b { color: var(--primary-color); font-size: 18px; }
.record-list { margin-top: 10px; border-top: 1px solid var(--border-light); }
.record-row { display: flex; justify-content: space-between; padding: 8px 0; color: var(--text-secondary); font-size: 12px; border-bottom: 1px solid var(--border-light); }
.record-row b { color: var(--text-primary); }
.settings-head { color: var(--text-primary); font-size: 13px; font-weight: 700; margin-bottom: 12px; }
.settings-form { max-width: 480px; }
.hint { color: var(--text-placeholder); font-size: 11px; }
.empty { padding: 24px 0; color: var(--text-placeholder); text-align: center; font-size: 12px; }
</style>
