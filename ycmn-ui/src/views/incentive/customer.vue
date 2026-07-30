<template>
  <div style="margin:-20px">
    <!-- 筛选栏 -->
    <div style="background:#fff;padding:12px 20px;margin-bottom:12px;border-bottom:1px solid #ebeef5;display:flex;gap:10px;flex-wrap:wrap;align-items:center">
      <el-input v-model="fCustomer" placeholder="客户名称" clearable size="small" style="width:180px" @change="doSearch" />
      <el-input v-model="fSales" placeholder="销售人员" clearable size="small" style="width:140px" @change="doSearch" />
      <el-select v-model="fIndustry" placeholder="行业" clearable size="small" style="width:130px" @change="doSearch">
        <el-option v-for="i in industries" :key="i" :label="i" :value="i" />
      </el-select>
      <el-select v-model="fType" placeholder="激励类型" clearable size="small" style="width:130px" @change="doSearch">
        <el-option label="外出参观" value="visit" />
        <el-option label="交流拜访" value="exchange" />
        <el-option label="两谈两做方案" value="plan" />
        <el-option label="产品试点" value="trial" />
      </el-select>
      <el-date-picker v-model="fDateRange" type="daterange" range-separator="至" start-placeholder="开始日" end-placeholder="截止日" size="small" format="YYYY-MM-DD" value-format="YYYY-MM-DD" style="width:240px" @change="doSearch" />
      <el-button type="primary" size="small" @click="doSearch">查询</el-button>
      <el-button size="small" @click="doReset">重置</el-button>
      <span style="margin-left:auto;font-size:12px;color:#909399">{{ totalR }}条记录 · {{ custs.length }}个客户</span>
    </div>

    <div style="padding:0 20px;display:flex;gap:10px;margin-bottom:12px">
      <el-radio-group v-model="cols" size="small">
        <el-radio-button :value="2">两列</el-radio-button>
        <el-radio-button :value="4">四列</el-radio-button>
      </el-radio-group>
    </div>

    <!-- 结果：网格布局 -->
    <div v-if="custs.length===0" style="text-align:center;padding:60px;color:#c0c4cc">暂无数据</div>

    <div :style="{padding:'0 20px',columnCount:cols,columnGap:'12px'}">
      <div v-for="c in custs" :key="c.customerId" style="break-inside:avoid;margin-bottom:12px;background:#fff;border:1px solid #ebeef5;border-radius:8px;overflow:hidden">
        <!-- 客户标题 -->
        <div style="background:#f8f9fb;padding:10px 14px;border-bottom:1px solid #ebeef5;display:flex;align-items:center;gap:8px;flex-wrap:wrap">
          <span style="font-size:14px;font-weight:700">{{ c.customerName }}</span>
          <el-tag size="small">{{ c.industry || '未知' }}</el-tag>
          <span style="font-size:11px;color:#909399;margin-left:auto">{{ c.count }}项 · {{ c.totalScore }}分</span>
        </div>
        <!-- 记录 -->
        <div v-for="r in c.records" :key="r.id" style="padding:10px 14px;border-bottom:1px solid #f5f5f5">
          <div style="display:flex;align-items:center;gap:6px;margin-bottom:6px;flex-wrap:wrap;font-size:12px">
            <span class="itag" :class="'itag-'+getCls(r)">{{ getMain(r) }}</span>
            <span>{{ r.categoryName }}</span>
            <span style="color:#909399">· {{ r.period }} · {{ r.userName }}</span>
            <span style="margin-left:auto;font-weight:700;font-size:14px" :style="{color:scoreColor(getCls(r))}">+{{ r.score }}</span>
          </div>
          <div v-if="getFiles(r).length" style="display:flex;gap:6px;flex-wrap:wrap">
            <div v-for="f in getFiles(r)" :key="f.id" style="flex:1;min-width:100px;max-width:calc(50% - 3px)">
              <div v-if="f.tag" style="font-size:10px;color:#909399;margin-bottom:2px">{{ f.tag }}</div>
              <img v-if="f.type==='image'" :src="'/api/file/download/'+f.id" style="width:100%;max-height:180px;border-radius:4px;border:1px solid #e8e8e8;object-fit:cover;cursor:pointer" @click="preview(f.id)" @error="onImgError" />
              <div v-else-if="f.type==='video'" style="width:100%;height:120px;background:#1e1b4b;border-radius:4px;display:flex;align-items:center;justify-content:center;cursor:pointer" @click="preview(f.id)">
                <div style="width:36px;height:36px;background:rgba(255,255,255,.2);border-radius:50%;display:flex;align-items:center;justify-content:center;font-size:16px;color:#fff">▶</div>
              </div>
              <a v-else :href="'/api/file/download/'+f.id" target="_blank" style="display:block;padding:8px 10px;background:#fdf6ec;border-radius:4px;color:#e6a23c;text-decoration:none;font-size:11px;text-align:center">📄 {{ f.label }}</a>
            </div>
          </div>
        </div>
      </div>
    </div>

    </div>

    <!-- 图片预览大图 -->
    <el-dialog v-model="previewVisible" title="预览" width="80%" :close-on-click-modal="true">
      <img :src="previewUrl" style="width:100%;max-height:80vh;object-fit:contain" @error="onImgError" />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getCategoryTree } from '@/api/incentive'
import request from '@/utils/request'
import { ElMessage } from 'element-plus'

const fCustomer = ref(''), fSales = ref(''), fIndustry = ref(''), fType = ref(''), fDateRange = ref(null)
const cols = ref(2)
const custs = ref([]), catMap = ref({})
const previewVisible = ref(false), previewUrl = ref('')
const industries = ['冶金','煤矿','化工','电力','有色金属','机械','工程公司','维修厂','其他终端客户']

const totalR = computed(() => custs.value.reduce((s,c) => s + c.count, 0))

function onImgError(e){e.target.src='data:image/svg+xml,'+encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="320" height="240" viewBox="0 0 320 240"><rect fill="#f5f6f7" width="320" height="240"/><text fill="#c0c4cc" font-size="14" font-family="sans-serif" text-anchor="middle" x="160" y="120">图片已丢失</text></svg>')}
function preview(id) { previewUrl.value = '/api/file/download/' + id; previewVisible.value = true }

function getFiles(r) {
  const f = []
  if (r.filePreId) f.push({ id: r.filePreId, label: r.filePreName || '', type: detectType(r.filePreName), tag: '安装前' })
  if (r.fileMidId) f.push({ id: r.fileMidId, label: r.fileMidName || '', type: detectType(r.fileMidName), tag: '安装中' })
  if (r.filePostId) f.push({ id: r.filePostId, label: r.filePostName || '', type: detectType(r.filePostName), tag: '安装后' })
  if (r.fileId) f.push({ id: r.fileId, label: r.fileName || '', type: detectType(r.fileName), tag: '' })
  return f
}
function detectType(n) { if(!n)return 'doc'; if(/\.(jpg|jpeg|png|gif|webp|bmp)$/i.test(n))return 'image'; if(/\.(mp4|mov|avi|webm)$/i.test(n))return 'video'; return 'doc' }

function getMain(r) { const c=catMap.value[r.categoryId]; if(!c)return''; const p=catMap.value[c.parentId]; if(!p)return c.categoryName; const gp=catMap.value[p.parentId]; return gp&&gp.parentId===0?gp.categoryName:p.categoryName }
function getCls(r) { const c=catMap.value[r.categoryId]; if(!c)return''; const p=catMap.value[c.parentId]; if(!p)return''; const gp=catMap.value[p.parentId]; return (gp&&gp.parentId===0?gp.categoryCode:p.categoryCode)||'' }
function scoreColor(c) { return {visit:'#e6a23c',exchange:'#409eff',plan:'#9c27b0',trial:'#67c23a'}[c]||'#303133' }

async function doSearch() {
  const p = {}
  if (fCustomer.value) p.customerName = fCustomer.value
  if (fSales.value) p.salesperson = fSales.value
  if (fIndustry.value) p.industry = fIndustry.value
  if (fType.value) p.mainCategoryId = fType.value === 'visit' ? 1 : fType.value === 'exchange' ? 2 : fType.value === 'plan' ? 3 : 4
  if (fDateRange.value && fDateRange.value.length === 2) { p.startDate = fDateRange.value[0]; p.endDate = fDateRange.value[1] }
  try {
    const res = await request.get('/incentive/customer-records', { params: p })
    custs.value = (res.data || []).filter(c => c.records?.length > 0 && c.customerName)
  } catch(e) { ElMessage.error('查询失败') }
}

function doReset() { fCustomer.value=''; fSales.value=''; fIndustry.value=''; fType.value=''; fDateRange.value=null; doSearch() }

async function loadCats() {
  try { const res = await getCategoryTree(); const walk=l=>{l.forEach(c=>{catMap.value[c.id]=c;if(c.children)walk(c.children)})}; walk(res.data||[]) } catch(e) {}
}

onMounted(() => { loadCats(); doSearch() })
</script>

<style scoped>
.itag{display:inline-block;padding:2px 8px;border-radius:3px;font-size:12px;font-weight:600}
.itag-visit{background:rgba(230,162,60,.1);color:#e6a23c}
.itag-exchange{background:rgba(64,158,255,.1);color:#409eff}
.itag-plan{background:rgba(156,39,176,.1);color:#9c27b0}
.itag-trial{background:rgba(103,194,58,.1);color:#67c23a}
</style>
