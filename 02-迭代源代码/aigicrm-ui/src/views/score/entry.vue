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
          <span class="se-year-suffix">全年</span>
        </div>
      </div>
      <div class="se-head-right">
        <template v-if="isAdmin">
          <span class="se-hint">输入 0-{{ maxRange }} 分</span>
          <el-button type="primary" @click="handleSave" :loading="saving">保存</el-button>
          <!-- 【2026-11-01 及时更新】保存后带当前周期直达排行(排行默认周期已与录入对齐) -->
          <el-button @click="goRanking">查看排行</el-button>
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
                  <!-- 2026-10-04 D14：产品下拉接线按大类联动过滤（原 getProductOptions 定义后从未接线） -->
                  <el-option v-for="p in getProductOptions(item.category)" :key="p.dictLabel" :label="p.dictLabel" :value="p.dictLabel" />
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
              <td colspan="2" class="col-prod se-total-label"><strong>合计</strong></td>
              <td class="col-range"></td>
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
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { confirmAction } from '@/utils/confirm'
import { Plus, Delete } from '@element-plus/icons-vue'
import { getScoreGrid, batchSaveEntries, addScoreItem, updateScoreItem, deleteScoreItem } from '@/api/score'
import { getDictData } from '@/api/system'
import { useUserStore } from '@/store/user'
import { getDownloadToken, refreshDownloadToken } from '@/utils/token'
import { downloadFile } from '@/utils/download'

const userStore = useUserStore()
// 【修复 2026-08-10 P2-07】与后端 score:manage 权限码对齐(此前仅 admin 可操作, 与权限字典矛盾:
// 字典中 score:manage 已授销售员/总监, 后端也放行, 前端却隐藏)
const isAdmin = computed(() => userStore.hasPermission('score:manage'))

const currentYear = new Date().getFullYear()
const year = ref(currentYear)
// 【2026-11-01 用户令】学分周期只留「全年」(原 上/下 半年切换退役; 存量半年数据由 V2026.11.01.2 并入全年)
const period = computed(() => `${year.value}全年`)
const yearOptions = computed(() => { const l=[]; for(let y=currentYear+1;y>=2024;y--)l.push(y);return l })

const items = ref([]), userPool = ref([]), visibleUsers = ref([]), scores = ref({}), saving = ref(false)
const hiddenUsers = computed(() => userPool.value.filter(u => !visibleUsers.value.some(v => v.id === u.id)))
const categoryOptions = ref([]), productOptions = ref([]), rangeOptions = ref([])
const maxRange = computed(() => Math.max(...items.value.map(i => i.scoreMax || 5), 5))

// 【D13 2026-10-04】错误处理统一口径：request 拦截器已对所有非 silent 请求弹全局错误提示，
// 本地不再叠加笼统文案（如"查询失败/保存失败"），catch 仅用于阻断后续流程；确认框取消与接口失败分开处理。
const loadDicts = async () => { try { categoryOptions.value = (await getDictData('score_category')).data||[] } catch{}; try { productOptions.value = (await getDictData('score_product')).data||[] } catch{}; try { rangeOptions.value = (await getDictData('score_range')).data||[] } catch{} }
// 【D14 2026-10-04】按大类联动过滤产品下拉：score_product 字典项的 dictValue 存所属大类码时生效；
// 若线上字典未按该口径维护（过滤结果为空），回退展示全部产品，避免空下拉卡死录入。
const getProductOptions = (catCode) => {
  if (!catCode) return productOptions.value
  const filtered = productOptions.value.filter(p => p.dictValue === catCode)
  return filtered.length ? filtered : productOptions.value
}
const onCategoryChange = (item) => { item.product = '' }
const onRangeChange = async (item, val) => { const p=val.split('-'); item.scoreMin=parseInt(p[0])||0; item.scoreMax=parseInt(p[1])||5; if(item.id) try{await updateScoreItem(item.id,{scoreMin:item.scoreMin,scoreMax:item.scoreMax})}catch{} }

const loadGrid = async () => { try { const d=(await getScoreGrid(period.value)).data||{}; items.value=d.items||[]; userPool.value=d.pool||[]; visibleUsers.value=d.users||[]; scores.value=d.scores||{} } catch{} }
// 【L3 2026-10-04】0 分是合法得分：用 ?? 区分"未评分(undefined/null)"与"0 分"，原 || 会把 0 显示成空
const getScore = (itemId, userId) => scores.value[itemId+'_'+userId] ?? ''
const setScore = (itemId, userId, event) => { const v=parseInt(event.target.value)||0; const item=items.value.find(i=>i.id===itemId); const mx=item?.scoreMax||5; const mn=item?.scoreMin||0; event.target.value=Math.max(mn,Math.min(mx,v)); scores.value[itemId+'_'+userId]=Math.max(mn,Math.min(mx,v)) }
const getUserTotal = (userId) => { let s=0; for(const item of items.value) s+=scores.value[item.id+'_'+userId]||0; return s }

// 保存失败由拦截器统一提示具体原因，本地不再弹笼统"保存失败"造成双重提示；
// 【C12 2026-10-08 二轮】失败分支显式留痕(console.error)，避免静默失败被误读为保存成功
const handleSave = async () => { saving.value=true; try { const entries=[]; for(const item of items.value) for(const u of visibleUsers.value) { const s=scores.value[item.id+'_'+u.id]; if(s!==undefined&&s!==''&&s!==null) entries.push({itemId:item.id,userId:u.id,score:s}) }; await batchSaveEntries({period:period.value,entries,participantIds:visibleUsers.value.map(u=>u.id)}); ElMessage.success('保存成功') } catch(e){ console.error('学分保存失败', e) }; saving.value=false }
// 【2026-11-01 及时更新】带当前录入周期跳排行, 保证"录完即看"周期必对齐
const router = useRouter()
const goRanking = () => router.push({ path: '/score/ranking', query: { period: period.value } })
const handleAddRow = async () => { try { const r=await addScoreItem({category:'',product:'',scoreMin:0,scoreMax:5}); if(r.data) items.value.push(r.data); ElMessage.success('已添加') } catch{} }
// 删除行：确认框取消 → 直接返回不动数据；接口失败 → 拦截器已提示，本地不再叠加（行不删除）
const handleDeleteRow = async (item, idx) => {
  // 【UI 整改 2026-10-08】删除类确认走 confirmAction：红色确认钮 + 取消左置
  try { await confirmAction('确定删除？所有周期数据一并删除。','删除确认',{ confirmText: '删除' }) } catch { return }
  try { await deleteScoreItem(item.id) } catch { return }
  items.value.splice(idx,1); ElMessage.success('已删除')
}
const handleRemoveUser = (user) => { visibleUsers.value = visibleUsers.value.filter(u => u.id !== user.id) }
const handleAddUser = (user) => { if(!visibleUsers.value.some(u=>u.id===user.id)) visibleUsers.value=[...visibleUsers.value,{id:user.id,realName:user.realName}] }
// 【需求】导出(FEAT 0804):window.open 被拦截时"没反应",改 fetch blob + a.download 强制下载
// 【修复 2026-08-14】先确保下载 token 就绪；未就绪时不拼空 token=（避免必然 403 的无效导出）
const handleExport = async () => {
  await refreshDownloadToken()
  const t = getDownloadToken() || ''
  const url = `/api/score/export/grid?period=${encodeURIComponent(period.value)}${t ? '&token=' + encodeURIComponent(t) : ''}`
  await downloadFile(url, { name: '学分统计-' + (period.value || '') + '.xlsx' })
}

watch(period, loadGrid)
onMounted(() => { loadDicts(); loadGrid() })
</script>

<style scoped>
.score-entry { height:calc(100vh - 100px); display:flex; flex-direction:column }

.se-head { display:flex; justify-content:space-between; align-items:center; margin-bottom:14px; flex-shrink:0 }
.se-head-left { display:flex; align-items:center; gap:16px }
.se-head-left h2 { font-size:20px; font-weight:700; color:#1f2329; margin:0 }
.se-period { display:flex; align-items:center; gap:0; background:#f2f4f7; border-radius:8px; overflow:hidden; padding-right:12px }
.se-year { width:90px } .se-year :deep(.el-input__wrapper) { box-shadow:none; background:transparent }
.se-year-suffix { font-size:13px; font-weight:600; color:#4e5969; padding-left:6px }
.se-head-right { display:flex; align-items:center; gap:10px }
.se-hint { font-size:12px; color:var(--el-color-info) }

.se-table-card { flex:1; min-height:0; background:#fff; border-radius:12px; border:1px solid #e8ecf1; overflow:hidden; box-shadow:0 1px 3px rgba(0,0,0,.03) }
.se-table-wrap { overflow:auto; max-height:100% }

.se-table { width:100%; border-collapse:collapse; font-size:13px }
.se-table th,.se-table td { border:1px solid #f0f1f3; padding:8px 6px; text-align:center; white-space:nowrap }
.se-table thead th { background:#f8f9fb; color:#4e5969; font-weight:500; position:sticky; top:0; z-index:2 }

.col-idx { width:36px; color:var(--el-color-info) } .col-cat { min-width:110px; width:110px } .col-prod { min-width:200px; width:200px } .col-range { min-width:90px; width:90px }
.col-op { width:44px } .col-user { min-width:72px; width:72px } .col-add-user { width:36px }

/* 【2026-11-01 用户令: 最左侧栏冻结】同事列横向滚动时 #/大类/产品 三列钉在左侧;
   单元格须不透明底色, 否则滚动内容从冻结列底下透出 */
.se-table td.col-idx, .se-table th.col-idx { position:sticky; left:0; z-index:1; background:#fff }
.se-table td.col-cat, .se-table th.col-cat { position:sticky; left:36px; z-index:1; background:#fff }
.se-table td.col-prod, .se-table th.col-prod { position:sticky; left:146px; z-index:1; background:#fff; box-shadow:4px 0 8px rgba(31,35,41,.06) }
/* 表头本身已 sticky top(z2); 冻结列在表头内要更高一层(z3), 否则横向滚动时内容穿过表头 */
.se-table thead th.col-idx, .se-table thead th.col-cat, .se-table thead th.col-prod { z-index:3 }
/* 行悬停/合计行底色需盖过冻结列白底(规则在后, 同特异性后者胜) */
.se-data-row:hover td.col-idx, .se-data-row:hover td.col-cat, .se-data-row:hover td.col-prod { background:#fafbfc }
.se-total-row td.col-idx, .se-total-row td.col-cat { background:#f5f7fa }
/* 合计行"合计"格横跨 大类+产品 两冻结列, 粘左偏移取大类列起点 */
.se-total-row td.se-total-label { left:36px; background:#f5f7fa; text-align:left; padding-left:12px }

.se-th-group th { background:#f2f4f7; font-size:13px; font-weight:600; color:#1f2329; padding:10px 6px }
.se-th-label { font-size:13px; font-weight:600 }
.se-th-row th { font-weight:600; font-size:13px; color:#4e5969 }

.se-user-head { display:flex; align-items:center; justify-content:center; gap:2px; font-size:12px }
.se-user-rm { cursor:pointer; color: var(--text-placeholder); font-size:14px } .se-user-rm:hover { color:var(--el-color-danger) }
.se-add-user-btn { font-size:18px; color:var(--el-color-primary) }

.se-data-row:hover { background:#fafbfc }
.se-data-row :deep(.el-select) { width:100% } .se-data-row :deep(.el-input__inner) { font-size:13px; color:#1f2329 }

.se-input { width:54px; height:30px; border:1px solid #e5e6eb; border-radius: var(--radius-sm); text-align:center; font-size:13px; outline:none; transition:border-color .15s }
.se-input:focus { border-color:var(--el-color-primary); box-shadow:0 0 0 3px color-mix(in srgb, var(--el-color-primary) 10%, transparent) }
.se-input::-webkit-inner-spin-button { -webkit-appearance:none }

.se-add-row td { background:#fafbfc; text-align:left!important; padding:10px 16px!important }
.se-total-row { background:#f5f7fa } .se-total-row td { border-top:2px solid var(--el-color-primary); font-weight:700 }

.se-pick-empty { color: var(--text-placeholder); font-size:13px; text-align:center; padding:12px 0 }
.se-pick-item { padding:8px 12px; cursor:pointer; font-size:13px; border-radius:4px } .se-pick-item:hover { background:var(--el-color-primary-light-9); color:var(--el-color-primary) }
</style>
