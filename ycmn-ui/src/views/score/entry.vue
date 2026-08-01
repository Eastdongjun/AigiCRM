<template>
  <div class="score-entry">
    <!-- 头部 -->
    <div class="se-head">
      <div class="se-head-left">
        <h2>学分录入</h2>
        <div class="se-period">
          <el-select v-model="year" class="se-year">
            <el-option v-for="y in yearOptions" :key="y" :label="y" :value="y" />
          </el-select>
          <div class="se-half">
            <span :class="{on:half==='上'}" @click="half='上'">上半年</span>
            <span :class="{on:half==='下'}" @click="half='下'">下半年</span>
          </div>
        </div>
      </div>
      <div class="se-head-right">
        <template v-if="isAdmin">
          <span class="se-hint">输入 0-{{ maxRange }} 分</span>
          <el-button type="primary" @click="handleSave" :loading="saving">保存</el-button>
          <el-button @click="handleExport">导出</el-button>
        </template>
        <el-tag v-else type="info">只读</el-tag>
      </div>
    </div>

    <!-- 表格 -->
    <div class="se-table-card">
      <div class="se-table-wrap">
        <table class="se-table">
          <thead>
            <tr class="se-th-group">
              <th class="col-idx"></th>
              <th class="col-cat"></th>
              <th class="col-prod"></th>
              <th class="col-range"></th>
              <th class="col-op"></th>
              <th class="col-user" :colspan="visibleUsers.length">
                <div class="se-th-label">同事姓名</div>
              </th>
              <th class="col-add-user"></th>
            </tr>
            <tr class="se-th-row">
              <th class="col-idx">#</th>
              <th class="col-cat">大类</th>
              <th class="col-prod">产品</th>
              <th class="col-range">区间</th>
              <th class="col-op"></th>
              <th class="col-user" v-for="u in visibleUsers" :key="u.id">
                <div class="se-user-head">
                  <span>{{ u.realName }}</span>
                  <span v-if="isAdmin" class="se-user-rm" title="移除" @click="handleRemoveUser(u)">&times;</span>
                </div>
              </th>
              <th v-if="isAdmin" class="col-add-user">
                <el-popover trigger="click" :width="180" placement="bottom-end">
                  <template #reference>
                    <el-button link size="small" class="se-add-user-btn">+</el-button>
                  </template>
                  <div v-if="hiddenUsers.length===0" class="se-pick-empty">所有同事已添加</div>
                  <div v-for="u in hiddenUsers" :key="u.id" class="se-pick-item" @click="handleAddUser(u)">{{ u.realName }}</div>
                </el-popover>
              </th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(item, idx) in items" :key="item.id||idx" class="se-data-row">
              <td class="col-idx">{{ idx+1 }}</td>
              <td class="col-cat">
                <el-select v-model="item.category" size="small" :disabled="!isAdmin" placeholder="选择大类" @change="onCategoryChange(item)">
                  <el-option v-for="c in categoryOptions" :key="c.dictValue" :label="c.dictLabel" :value="c.dictValue" />
                </el-select>
              </td>
              <td class="col-prod">
                <el-select v-model="item.product" size="small" :disabled="!isAdmin" placeholder="选择产品">
                  <el-option v-for="p in allProductOptions" :key="p.dictLabel" :label="p.dictLabel" :value="p.dictLabel" />
                </el-select>
              </td>
              <td class="col-range">
                <el-select :model-value="item.scoreMin+'-'+item.scoreMax" size="small" :disabled="!isAdmin" placeholder="选择区间" @change="onRangeChange(item,$event)">
                  <el-option v-for="r in rangeOptions" :key="r.dictValue" :label="r.dictLabel" :value="r.dictValue" />
                </el-select>
              </td>
              <td class="col-op">
                <el-button v-if="isAdmin" link type="danger" size="small" @click="handleDeleteRow(item,idx)"><el-icon><Delete /></el-icon></el-button>
              </td>
              <td class="col-user" v-for="u in visibleUsers" :key="u.id">
                <input type="number" class="se-input" :min="item.scoreMin" :max="item.scoreMax"
                  :value="getScore(item.id,u.id)" :disabled="!isAdmin"
                  @change="setScore(item.id,u.id,$event)" @focus="$event.target.select()" />
              </td>
              <td class="col-add-user"></td>
            </tr>
            <tr v-if="isAdmin" class="se-add-row">
              <td :colspan="5+visibleUsers.length+1">
                <el-button link type="primary" @click="handleAddRow"><el-icon><Plus /></el-icon> 添加行</el-button>
              </td>
            </tr>
            <tr class="se-total-row">
              <td class="col-idx"></td>
              <td colspan="3"><strong>合计</strong></td>
              <td class="col-op"></td>
              <td class="col-user" v-for="u in visibleUsers" :key="'t'+u.id"><strong>{{ getUserTotal(u.id) }}</strong></td>
              <td class="col-add-user"></td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete } from '@element-plus/icons-vue'
import { getScoreGrid, batchSaveEntries, addScoreItem, updateScoreItem, deleteScoreItem } from '@/api/score'
import { getDictData } from '@/api/system'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()
const isAdmin = computed(() => userStore.roles?.includes('admin'))

const currentYear = new Date().getFullYear()
const year = ref(currentYear)
const half = ref(new Date().getMonth() < 6 ? '上' : '下')
const period = computed(() => `${year.value}${half.value}半年`)
const yearOptions = computed(() => { const l=[]; for(let y=currentYear+1;y>=2024;y--)l.push(y);return l })

const items = ref([]), userPool = ref([]), visibleUsers = ref([]), scores = ref({}), saving = ref(false)
const hiddenUsers = computed(() => userPool.value.filter(u => !visibleUsers.value.some(v => v.id === u.id)))
const categoryOptions = ref([]), productOptions = ref([]), rangeOptions = ref([])
const maxRange = computed(() => Math.max(...items.value.map(i => i.scoreMax || 5), 5))

const loadDicts = async () => { try { categoryOptions.value = (await getDictData('score_category')).data||[] } catch{}; try { productOptions.value = (await getDictData('score_product')).data||[] } catch{}; try { rangeOptions.value = (await getDictData('score_range')).data||[] } catch{} }
const allProductOptions = computed(() => productOptions.value)
const getProductOptions = (catCode) => catCode ? productOptions.value.filter(p => p.dictValue === catCode) : productOptions.value
const onCategoryChange = (item) => { item.product = '' }
const onRangeChange = async (item, val) => { const p=val.split('-'); item.scoreMin=parseInt(p[0])||0; item.scoreMax=parseInt(p[1])||5; if(item.id) try{await updateScoreItem(item.id,{scoreMin:item.scoreMin,scoreMax:item.scoreMax})}catch{} }

const loadGrid = async () => { try { const d=(await getScoreGrid(period.value)).data||{}; items.value=d.items||[]; userPool.value=d.pool||[]; visibleUsers.value=d.users||[]; scores.value=d.scores||{} } catch{} }
const getScore = (itemId, userId) => scores.value[itemId+'_'+userId] || ''
const setScore = (itemId, userId, event) => { const v=parseInt(event.target.value)||0; const item=items.value.find(i=>i.id===itemId); const mx=item?.scoreMax||5; const mn=item?.scoreMin||0; event.target.value=Math.max(mn,Math.min(mx,v)); scores.value[itemId+'_'+userId]=Math.max(mn,Math.min(mx,v)) }
const getUserTotal = (userId) => { let s=0; for(const item of items.value) s+=scores.value[item.id+'_'+userId]||0; return s }

const handleSave = async () => { saving.value=true; try { const entries=[]; for(const item of items.value) for(const u of visibleUsers.value) { const s=scores.value[item.id+'_'+u.id]; if(s!==undefined&&s!==''&&s!==null) entries.push({itemId:item.id,userId:u.id,score:s}) }; await batchSaveEntries({period:period.value,entries,participantIds:visibleUsers.value.map(u=>u.id)}); ElMessage.success('保存成功') } catch{ElMessage.error('保存失败')}; saving.value=false }
const handleAddRow = async () => { try { const r=await addScoreItem({category:'',product:'',scoreMin:0,scoreMax:5}); if(r.data) items.value.push(r.data); ElMessage.success('已添加') } catch{} }
const handleDeleteRow = async (item, idx) => { try { await ElMessageBox.confirm('确定删除？所有周期数据一并删除。','提示',{type:'warning'}); await deleteScoreItem(item.id); items.value.splice(idx,1); ElMessage.success('已删除') } catch{} }
const handleRemoveUser = (user) => { visibleUsers.value = visibleUsers.value.filter(u => u.id !== user.id) }
const handleAddUser = (user) => { if(!visibleUsers.value.some(u=>u.id===user.id)) visibleUsers.value=[...visibleUsers.value,{id:user.id,realName:user.realName}] }
const handleExport = () => { const t=localStorage.getItem('token'); window.open(`/api/score/export/grid?period=${encodeURIComponent(period.value)}&token=${encodeURIComponent(t)}`,'_blank') }

watch(period, loadGrid)
onMounted(() => { loadDicts(); loadGrid() })
</script>

<style scoped>
.score-entry { height:calc(100vh - 100px); display:flex; flex-direction:column }

.se-head { display:flex; justify-content:space-between; align-items:center; margin-bottom:14px; flex-shrink:0 }
.se-head-left { display:flex; align-items:center; gap:16px }
.se-head-left h2 { font-size:20px; font-weight:700; color:#1f2329; margin:0 }
.se-period { display:flex; align-items:center; gap:0; background:#f2f4f7; border-radius:8px; overflow:hidden }
.se-year { width:80px } .se-year :deep(.el-input__wrapper) { box-shadow:none; background:transparent }
.se-half { display:flex }
.se-half span { padding:7px 14px; font-size:13px; cursor:pointer; color:#606266; transition:all .15s; border-radius:6px; margin:2px }
.se-half span.on { background:#fff; color:#409eff; font-weight:600; box-shadow:0 1px 3px rgba(0,0,0,.08) }
.se-head-right { display:flex; align-items:center; gap:10px }
.se-hint { font-size:12px; color:#909399 }

.se-table-card { flex:1; min-height:0; background:#fff; border-radius:12px; border:1px solid #e8ecf1; overflow:hidden; box-shadow:0 1px 3px rgba(0,0,0,.03) }
.se-table-wrap { overflow:auto; max-height:100% }

.se-table { width:100%; border-collapse:collapse; font-size:13px }
.se-table th,.se-table td { border:1px solid #f0f1f3; padding:8px 6px; text-align:center; white-space:nowrap }
.se-table thead th { background:#f8f9fb; color:#4e5969; font-weight:500; position:sticky; top:0; z-index:2 }

.col-idx { width:36px; color:#909399 } .col-cat { min-width:110px; width:110px } .col-prod { min-width:200px; width:200px } .col-range { min-width:90px; width:90px }
.col-op { width:44px } .col-user { min-width:72px; width:72px } .col-add-user { width:36px }

.se-th-group th { background:#f2f4f7; font-size:13px; font-weight:600; color:#1f2329; padding:10px 6px }
.se-th-label { font-size:13px; font-weight:600 }
.se-th-row th { font-weight:600; font-size:13px; color:#4e5969 }

.se-user-head { display:flex; align-items:center; justify-content:center; gap:2px; font-size:12px }
.se-user-rm { cursor:pointer; color:#c0c4cc; font-size:14px } .se-user-rm:hover { color:#f56c6c }
.se-add-user-btn { font-size:18px; color:#409eff }

.se-data-row:hover { background:#fafbfc }
.se-data-row :deep(.el-select) { width:100% } .se-data-row :deep(.el-input__inner) { font-size:13px; color:#1f2329 }

.se-input { width:54px; height:30px; border:1px solid #e5e6eb; border-radius:6px; text-align:center; font-size:13px; outline:none; transition:border-color .15s }
.se-input:focus { border-color:#409eff; box-shadow:0 0 0 3px rgba(64,158,255,.1) }
.se-input::-webkit-inner-spin-button { -webkit-appearance:none }

.se-add-row td { background:#fafbfc; text-align:left!important; padding:10px 16px!important }
.se-total-row { background:#f5f7fa } .se-total-row td { border-top:2px solid #409eff; font-weight:700 }

.se-pick-empty { color:#c0c4cc; font-size:13px; text-align:center; padding:12px 0 }
.se-pick-item { padding:8px 12px; cursor:pointer; font-size:13px; border-radius:4px } .se-pick-item:hover { background:#ecf5ff; color:#409eff }
</style>
