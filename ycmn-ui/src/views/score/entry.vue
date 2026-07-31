<template>
  <div class="score-entry">
    <!-- 顶栏 -->
    <div class="top-bar">
      <div class="top-left">
        <h3 class="page-title">学分录入</h3>
        <div class="period-picker">
          <el-select v-model="year" style="width:90px">
            <el-option v-for="y in yearOptions" :key="y" :label="y" :value="y" />
          </el-select>
          <el-radio-group v-model="half" size="small">
            <el-radio-button label="上" />
            <el-radio-button label="下" />
          </el-radio-group>
        </div>
      </div>
      <div class="top-right">
        <template v-if="isAdmin">
          <span class="hint">点击单元格输入 0-{{ maxRange }} 分</span>
          <el-button type="primary" @click="handleSave" :loading="saving">保存</el-button>
          <el-button @click="handleExport">导出</el-button>
        </template>
        <el-tag v-else type="info" size="small">只读模式</el-tag>
      </div>
    </div>

    <!-- 表格 -->
    <div class="grid-wrap">
      <table class="score-table">
        <thead>
          <tr>
            <th class="col-idx" colspan="1"><div class="header-group">分享内容</div></th>
            <th class="col-cat"></th>
            <th class="col-prod"></th>
            <th class="col-range"></th>
            <th class="col-op"></th>
            <th class="col-user" :colspan="visibleUsers.length">
              <div class="header-group">同事姓名</div>
            </th>
            <th class="col-add-user"></th>
          </tr>
          <tr>
            <th class="col-idx">#</th>
            <th class="col-cat">大类</th>
            <th class="col-prod">产品</th>
            <th class="col-range">区间</th>
            <th class="col-op"></th>
            <th class="col-user" v-for="u in visibleUsers" :key="u.id">
              <div class="user-header">
                <span>{{ u.realName }}</span>
                <span v-if="isAdmin" class="user-remove" title="移除该同事" @click="handleRemoveUser(u)">&times;</span>
              </div>
            </th>
            <th v-if="isAdmin" class="col-add-user">
              <el-popover trigger="click" :width="180" placement="bottom-end">
                <template #reference>
                  <el-button type="primary" link size="small" title="添加同事">+</el-button>
                </template>
                <div class="user-pick-list">
                  <div v-if="hiddenUsers.length === 0" class="pick-empty">所有同事已添加</div>
                  <div v-for="u in hiddenUsers" :key="u.id" class="pick-item" @click="handleAddUser(u)">
                    {{ u.realName }}
                  </div>
                </div>
              </el-popover>
            </th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(item, idx) in items" :key="item.id || idx">
            <td class="col-idx">{{ idx + 1 }}</td>
            <td class="col-cat">
              <el-select v-model="item.category" size="small" style="width:100%" :disabled="!isAdmin" @change="onCategoryChange(item)">
                <el-option v-for="c in categoryOptions" :key="c.dictValue" :label="c.dictLabel" :value="c.dictValue" />
              </el-select>
            </td>
            <td class="col-prod">
              <el-select v-model="item.product" size="small" style="width:100%" :disabled="!isAdmin">
                <el-option v-for="p in getProductOptions(item.category)" :key="p.dictLabel" :label="p.dictLabel" :value="p.dictLabel" />
              </el-select>
            </td>
            <td class="col-range">
              <el-select :model-value="item.scoreMin + '-' + item.scoreMax" size="small" style="width:100%" :disabled="!isAdmin" @change="onRangeChange(item, $event)">
                <el-option v-for="r in rangeOptions" :key="r.dictValue" :label="r.dictLabel" :value="r.dictValue" />
              </el-select>
            </td>
            <td class="col-op">
              <el-button v-if="isAdmin" type="danger" link size="small" @click="handleDeleteRow(item, idx)">删除</el-button>
            </td>
            <td class="col-user" v-for="u in visibleUsers" :key="u.id">
              <input type="number" class="score-input" :min="item.scoreMin" :max="item.scoreMax"
                :value="getScore(item.id, u.id)" :disabled="!isAdmin"
                @change="setScore(item.id, u.id, $event)" @focus="$event.target.select()" />
            </td>
            <td class="col-add-user"></td>
          </tr>
          <!-- 添加行 -->
          <tr v-if="isAdmin" class="add-row">
            <td :colspan="5 + visibleUsers.length + 1" class="add-cell">
              <el-button type="primary" link @click="handleAddRow">+ 添加行</el-button>
            </td>
          </tr>
          <!-- 合计行 -->
          <tr class="total-row">
            <td class="col-idx"></td>
            <td colspan="3"><strong>合计</strong></td>
            <td class="col-op"></td>
            <td class="col-user" v-for="u in visibleUsers" :key="'t'+u.id">
              <strong>{{ getUserTotal(u.id) }}</strong>
            </td>
            <td class="col-add-user"></td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getScoreGrid, batchSaveEntries, addScoreItem, updateScoreItem, deleteScoreItem } from '@/api/score'
import { getDictData } from '@/api/system'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()
const isAdmin = computed(() => userStore.roles?.includes('admin'))

const currentYear = new Date().getFullYear()
const year = ref(currentYear)
const half = ref(new Date().getMonth() < 6 ? '上' : '下')
const period = computed(() => `${year.value}${half.value}半年`)
const yearOptions = computed(() => {
  const list = []
  for (let y = currentYear + 1; y >= 2024; y--) list.push(y)
  return list
})

const items = ref([])          // BizScoreItem list
const userPool = ref([])       // 全部启用用户（"添加同事"候选池）{id, realName}
const visibleUsers = ref([])   // 当前显示的同事列（持久化名单，保存时回传）
const scores = ref({})         // "itemId_userId" -> score
const saving = ref(false)

// 候选池里尚未显示的同事（供"+"添加）
const hiddenUsers = computed(() =>
  userPool.value.filter(u => !visibleUsers.value.some(v => v.id === u.id)))

const categoryOptions = ref([])
const productOptions = ref([])
const rangeOptions = ref([])

const maxRange = computed(() => {
  if (!items.value.length) return 5
  return Math.max(...items.value.map(i => i.scoreMax || 5))
})

// 加载字典
const loadDicts = async () => {
  try {
    const r1 = await getDictData('score_category')
    categoryOptions.value = r1.data || []
  } catch {}
  try {
    const r2 = await getDictData('score_product')
    productOptions.value = r2.data || []
  } catch {}
  try {
    const r3 = await getDictData('score_range')
    rangeOptions.value = r3.data || []
  } catch {}
}

// 按大类code过滤产品
const getProductOptions = (catCode) => {
  if (!catCode) return productOptions.value
  return productOptions.value.filter(p => p.dictValue === catCode)
}

const onCategoryChange = (item) => {
  item.product = ''
}

const onRangeChange = async (item, val) => {
  const parts = val.split('-')
  item.scoreMin = parseInt(parts[0]) || 0
  item.scoreMax = parseInt(parts[1]) || 5
  if (item.id) {
    try {
      await updateScoreItem(item.id, { scoreMin: item.scoreMin, scoreMax: item.scoreMax })
    } catch {}
  }
}

// 加载表格数据
const loadGrid = async () => {
  try {
    const res = await getScoreGrid(period.value)
    const data = res.data || {}
    items.value = data.items || []
    userPool.value = data.pool || []
    visibleUsers.value = data.users || []   // 已保存名单 或 首次默认全体启用用户
    scores.value = data.scores || {}
  } catch {}
}

const getScore = (itemId, userId) => {
  return scores.value[itemId + '_' + userId] || ''
}

const setScore = (itemId, userId, event) => {
  const val = parseInt(event.target.value) || 0
  const item = items.value.find(i => i.id === itemId)
  const max = item?.scoreMax || 5
  const min = item?.scoreMin || 0
  const clamped = Math.max(min, Math.min(max, val))
  event.target.value = clamped
  scores.value[itemId + '_' + userId] = clamped
}

const getUserTotal = (userId) => {
  let sum = 0
  for (const item of items.value) {
    sum += scores.value[item.id + '_' + userId] || 0
  }
  return sum
}

// 保存
const handleSave = async () => {
  saving.value = true
  try {
    const entries = []
    for (const item of items.value) {
      for (const u of visibleUsers.value) {
        const s = scores.value[item.id + '_' + u.id]
        if (s !== undefined && s !== '' && s !== null) {
          entries.push({ itemId: item.id, userId: u.id, score: s })
        }
      }
    }
    const participantIds = visibleUsers.value.map(u => u.id)
    await batchSaveEntries({ period: period.value, entries, participantIds })
    ElMessage.success('保存成功')
  } catch {
    ElMessage.error('保存失败')
  }
  saving.value = false
}

// 添加行
const handleAddRow = async () => {
  try {
    const res = await addScoreItem({ category: '', product: '', scoreMin: 0, scoreMax: 5 })
    if (res.data) {
      items.value.push(res.data)
    }
    ElMessage.success('已添加')
  } catch {}
}

// 删除行
const handleDeleteRow = async (item, idx) => {
  try {
    await ElMessageBox.confirm('确定删除该学分项？所有周期的该项评分数据将一并删除。', '提示', { type: 'warning' })
    await deleteScoreItem(item.id)
    items.value.splice(idx, 1)
    ElMessage.success('已删除')
  } catch {}
}

// 移除同事列（保存后才持久化）
const handleRemoveUser = (user) => {
  visibleUsers.value = visibleUsers.value.filter(u => u.id !== user.id)
}

// 添加同事列（追加到末尾）
const handleAddUser = (user) => {
  if (visibleUsers.value.some(u => u.id === user.id)) return
  visibleUsers.value = [...visibleUsers.value, { id: user.id, realName: user.realName }]
}

// 导出
const handleExport = () => {
  const token = localStorage.getItem('token')
  const url = `/api/score/export/grid?period=${encodeURIComponent(period.value)}&token=${encodeURIComponent(token)}`
  window.open(url, '_blank')
}

watch(period, loadGrid)

onMounted(() => {
  loadDicts()
  loadGrid()
})
</script>

<style scoped>
.score-entry { padding: 0; }
.top-bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; flex-wrap: wrap; gap: 12px; }
.top-left { display: flex; align-items: center; gap: 16px; }
.page-title { font-size: 18px; font-weight: 600; margin: 0; }
.period-picker { display: flex; align-items: center; gap: 8px; }
.top-right { display: flex; align-items: center; gap: 12px; }
.hint { color: #999; font-size: 13px; }

/* 表格 */
.grid-wrap { overflow-x: auto; border: 1px solid #ebeef5; border-radius: 8px; background: #fff; }
.score-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.score-table th, .score-table td { border: 1px solid #ebeef5; padding: 8px 6px; text-align: center; white-space: nowrap; }
.score-table thead th { background: #fafafa; color: #666; font-weight: 500; position: sticky; top: 0; z-index: 2; }
.header-group { font-size: 14px; font-weight: 600; color: #333; }

.col-idx { width: 36px; min-width: 36px; color: #999; }
.col-cat { width: 120px; min-width: 120px; }
.col-prod { width: 200px; min-width: 200px; }
.col-range { width: 90px; min-width: 90px; }
.col-op { width: 50px; min-width: 50px; }
.col-user { width: 80px; min-width: 80px; }
.col-add-user { width: 36px; min-width: 36px; }

/* 用户列头 */
.user-header { display: flex; align-items: center; justify-content: center; gap: 2px; }
.user-remove { cursor: pointer; color: #c0c4cc; font-size: 15px; line-height: 1; margin-left: 2px; }
.user-remove:hover { color: #f56c6c; }

/* 添加同事弹窗 */
.user-pick-list { max-height: 240px; overflow-y: auto; }
.pick-empty { color: #c0c4cc; font-size: 13px; text-align: center; padding: 12px 0; }
.pick-item { padding: 8px 12px; cursor: pointer; font-size: 13px; border-radius: 4px; }
.pick-item:hover { background: #ecf5ff; color: #409eff; }

/* 输入框 */
.score-input {
  width: 50px; height: 28px; border: 1px solid #dcdfe6; border-radius: 4px;
  text-align: center; font-size: 13px; outline: none; background: #fff;
}
.score-input:focus { border-color: #409eff; box-shadow: 0 0 0 2px rgba(64,158,255,.15); }
.score-input::-webkit-inner-spin-button { -webkit-appearance: none; }

/* 添加行 */
.add-row td { background: #fafafa; }
.add-cell { text-align: left !important; padding-left: 16px !important; }

/* 合计行 */
.total-row { background: #f5f7fa; }
.total-row td { border-top: 2px solid #409eff; }
</style>
