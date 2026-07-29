<template>
  <div style="margin:-20px">
    <div class="bar">
      <div class="bar-row">
        <el-input v-model="fCustomer" placeholder="客户名称" clearable class="kw" @change="doSearch"><template #prefix><el-icon><Search /></el-icon></template></el-input>
        <el-input v-model="fSales" placeholder="销售人员" clearable class="kw2" @change="doSearch"><template #prefix><el-icon><User /></el-icon></template></el-input>
        <el-select v-model="fIndustry" placeholder="行业" clearable class="sel" @change="doSearch"><el-option v-for="i in industries" :key="i" :label="i" :value="i" /></el-select>
        <el-select v-model="fType" placeholder="类型" clearable class="sel" @change="doSearch"><el-option label="外出参观" value="visit" /><el-option label="交流拜访" value="exchange" /><el-option label="两谈两做方案" value="plan" /><el-option label="产品试点" value="trial" /></el-select>
        <el-date-picker v-model="fDateRange" type="daterange" range-separator="~" start-placeholder="开始" end-placeholder="截止" value-format="YYYY-MM-DD" class="dt" @change="doSearch" />
        <el-button type="primary" @click="doSearch">查询</el-button>
        <el-button @click="doReset">重置</el-button>
        <span class="cnt">{{ custs.length }} 客户</span>
        <el-radio-group v-model="cols" size="small"><el-radio-button :value="2">两列</el-radio-button><el-radio-button :value="3">三列</el-radio-button><el-radio-button :value="4">四列</el-radio-button></el-radio-group>
      </div>
    </div>

    <div :style="{display:'grid',gridTemplateColumns:'repeat('+cols+',1fr)',gap:'16px',padding:'20px'}">
      <div v-if="custs.length===0" style="grid-column:1/-1;text-align:center;padding:80px 0;color:#bbb">暂无数据</div>
      <div v-for="c in custs" :key="c.customerId" class="card">
        <div class="card-hd">
          <span class="card-name">{{ c.customerName }}</span>
          <span class="card-ind">{{ c.industry }}</span>
          <span class="card-num">{{ c.totalScore }}分</span>
        </div>
        <div v-if="c.records[0]" class="card-bd">
          <div class="card-meta">
            <span class="rec-tag" :class="'rec-tag-'+getCls(c.records[0])">{{ getMain(c.records[0]) }}</span>
            <span class="card-sub">{{ c.records[0].categoryName }}</span>
            <span class="card-info">{{ c.records[0].period }} · {{ c.records[0].userName }}</span>
            <span class="card-pts" :style="{color:scoreColor(getCls(c.records[0]))}">+{{ c.records[0].score }}</span>
          </div>
          <div v-if="getFiles(c.records[0]).length" class="card-imgs">
            <div v-for="f in getFiles(c.records[0]).slice(0,4)" :key="f.id" class="card-img">
              <div v-if="f.tag" class="img-tag">{{ f.tag }}</div>
              <template v-if="f.type==='image'"><img :src="'/api/file/download/'+f.id" @click="preview(f.id)" /></template>
              <div v-else-if="f.type==='video'" class="img-vid" @click="preview(f.id)">▶</div>
            </div>
          </div>
        </div>
        <div class="card-ft" @click="showDetail(c)">查看详情 →</div>
      </div>
    </div>

    <el-dialog v-model="previewVisible" width="90vw" :close-on-click-modal="true"><img :src="previewUrl" style="width:100%;max-height:85vh;object-fit:contain" /></el-dialog>

    <el-dialog v-model="detailVisible" width="80%" top="2vh" :close-on-click-modal="true" destroy-on-close>
      <template #header><span style="font-size:20px;font-weight:800">{{ detailCust?.customerName }}</span><span style="font-size:13px;color:#999;margin-left:12px">{{ detailCust?.industry }} · {{ detailCust?.count }}项 · {{ detailCust?.totalScore }}分</span></template>
      <div v-if="detailCust" v-for="(r,i) in detailCust.records" :key="r.id" style="padding:22px 0;border-bottom:1px solid #f0f0f0">
        <div style="display:flex;align-items:center;gap:8px;margin-bottom:10px;flex-wrap:wrap;font-size:14px">
          <span class="rec-tag" :class="'rec-tag-'+getCls(r)">{{ getMain(r) }}</span><span>{{ r.categoryName }}</span>
          <span style="color:#999;font-size:13px">{{ r.period }} · {{ r.userName }}</span>
          <span v-if="r.industry" style="color:#999;font-size:12px">· {{ r.industry }}</span>
          <span v-if="r.relatedProducts" style="color:#999;font-size:12px">· {{ r.relatedProducts }}</span>
          <span style="margin-left:auto;font-weight:700;font-size:18px" :style="{color:scoreColor(getCls(r))}">+{{ r.score }}</span>
        </div>
        <div v-if="getFiles(r).length" style="display:grid;grid-template-columns:repeat(auto-fill,minmax(220px,1fr));gap:12px">
          <div v-for="f in getFiles(r)" :key="f.id">
            <div style="font-size:11px;color:#999;margin-bottom:4px">{{ f.tag }}</div>
            <template v-if="f.type==='image'"><img :src="'/api/file/download/'+f.id" @click="preview(f.id)" style="width:100%;max-height:360px;object-fit:contain;border-radius:8px;border:1px solid #eee;background:#fafafa;cursor:pointer" /></template>
            <div v-else-if="f.type==='video'" @click="preview(f.id)" style="width:100%;aspect-ratio:16/9;background:#1a1a2e;border-radius:8px;display:flex;align-items:center;justify-content:center;cursor:pointer;color:#fff;font-size:24px">▶</div>
            <a v-else :href="'/api/file/download/'+f.id" target="_blank" style="display:block;padding:20px;background:#fdf6ec;border-radius:8px;text-align:center;text-decoration:none;color:#a16207;font-size:12px">📄 {{ f.label }}</a>
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getCategoryTree } from '@/api/incentive'
import { Search, User } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { ElMessage } from 'element-plus'

const fCustomer=ref(''),fSales=ref(''),fIndustry=ref(''),fType=ref(''),fDateRange=ref(null),cols=ref(3)
const custs=ref([]),catMap=ref({}),previewVisible=ref(false),previewUrl=ref('')
const detailVisible=ref(false),detailCust=ref(null)
const industries=['冶金','煤矿','化工','电力','有色金属','机械','工程公司','维修厂','其他终端客户']

function preview(id){previewUrl.value='/api/file/download/'+id;previewVisible.value=true}
function showDetail(c){detailCust.value=c;detailVisible.value=true}

function getFiles(r){
  const f=[]
  if(r.filePreId)f.push({id:r.filePreId,label:r.filePreName||'文件',type:detectType(r.filePreName),tag:'安装前'})
  if(r.fileMidId)f.push({id:r.fileMidId,label:r.fileMidName||'文件',type:detectType(r.fileMidName),tag:'安装中'})
  if(r.filePostId)f.push({id:r.filePostId,label:r.filePostName||'文件',type:detectType(r.filePostName),tag:'安装后'})
  if(r.fileId)f.push({id:r.fileId,label:r.fileName||'文件',type:detectType(r.fileName),tag:''})
  if(r.fileTrackId)f.push({id:r.fileTrackId,label:r.fileTrackName||'',type:detectType(r.fileTrackName),tag:'跟踪'})
  return f
}
function detectType(n){if(!n)return'doc';if(/\.(jpg|jpeg|png|gif|webp|bmp)$/i.test(n))return'image';if(/\.(mp4|mov|avi|webm)$/i.test(n))return'video';return'doc'}
function getMain(r){const c=catMap.value[r.categoryId];if(!c)return'';if(c.parentId===0)return c.categoryName;const p=catMap.value[c.parentId];if(!p)return c.categoryName;const gp=catMap.value[p.parentId];return gp&&gp.parentId===0?gp.categoryName:p.categoryName}
function getCls(r){const c=catMap.value[r.categoryId];if(!c)return'';if(c.parentId===0)return c.categoryCode||'';const p=catMap.value[c.parentId];if(!p)return'';const gp=catMap.value[p.parentId];return(gp&&gp.parentId===0?gp.categoryCode:p.categoryCode)||''}
function scoreColor(c){return{visit:'#d97706',exchange:'#2563eb',plan:'#7c3aed',trial:'#059669'}[c]||'#303133'}

async function doSearch(){
  const p={};if(fCustomer.value)p.customerName=fCustomer.value;if(fSales.value)p.salesperson=fSales.value;if(fIndustry.value)p.industry=fIndustry.value
  if(fType.value)p.mainCategoryId=fType.value==='visit'?1:fType.value==='exchange'?2:fType.value==='plan'?3:4
  if(fDateRange.value?.length===2){p.startDate=fDateRange.value[0];p.endDate=fDateRange.value[1]}
  try{const res=await request.get('/incentive/customer-records',{params:p});custs.value=(res.data||[]).filter(c=>c.records?.length>0&&c.customerName)}catch(e){ElMessage.error('查询失败')}
}
function doReset(){fCustomer.value='';fSales.value='';fIndustry.value='';fType.value='';fDateRange.value=null;doSearch()}
async function loadCats(){try{const res=await getCategoryTree();const walk=l=>{l.forEach(c=>{catMap.value[c.id]=c;if(c.children)walk(c.children)})};walk(res.data||[])}catch(e){}}
onMounted(()=>{loadCats();doSearch()})
</script>

<style scoped>
.bar{background:#fff;border-bottom:1px solid #ebeef5;padding:12px 24px}
.bar-row{display:flex;gap:8px;align-items:center;flex-wrap:wrap}
.kw{width:170px}.kw2{width:130px}.sel{width:110px}.dt{width:200px}
.cnt{font-size:12px;color:#999;white-space:nowrap;margin-left:auto}

.card{background:#fff;border:1px solid #e4e7ed;border-radius:10px;overflow:hidden;display:flex;flex-direction:column;transition:box-shadow .2s}
.card:hover{box-shadow:0 4px 16px rgba(0,0,0,.06)}
.card-hd{padding:16px 18px 12px;display:flex;align-items:center;gap:8px;flex-wrap:wrap}
.card-name{font-size:15px;font-weight:700;flex:1;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.card-ind{font-size:11px;padding:2px 8px;background:#f0f2f5;border-radius:3px;color:#666;white-space:nowrap}
.card-num{font-size:11px;padding:2px 8px;background:#ecf5ff;border-radius:3px;color:#409eff;font-weight:600;white-space:nowrap}
.card-bd{flex:1;padding:0 18px 16px}
.card-meta{display:flex;align-items:center;gap:6px;margin-bottom:10px;flex-wrap:wrap;font-size:13px}
.card-sub{color:#555}.card-info{color:#999;font-size:12px}
.card-pts{margin-left:auto;font-weight:600;font-size:15px}
.card-imgs{display:flex;gap:8px;flex-wrap:wrap}
.card-img{flex:1;min-width:90px;max-width:calc(50% - 4px)}
.img-tag{font-size:10px;color:#999;margin-bottom:3px}
.card-img img{width:100%;aspect-ratio:4/3;object-fit:cover;border-radius:6px;cursor:pointer;transition:transform .3s}
.card-img img:hover{transform:scale(1.06)}
.img-vid{width:100%;aspect-ratio:4/3;background:#1a1a2e;border-radius:6px;display:flex;align-items:center;justify-content:center;color:#fff;font-size:24px;cursor:pointer}
.card-ft{text-align:center;padding:12px;border-top:1px solid #f0f2f5;font-size:13px;color:#409eff;cursor:pointer;font-weight:600}
.card-ft:hover{background:#ecf5ff}

.rec-tag{display:inline-block;padding:3px 10px;border-radius:4px;font-size:12px;font-weight:600}
.rec-tag-visit{background:#fef3c7;color:#b45309}.rec-tag-exchange{background:#dbeafe;color:#1e40af}
.rec-tag-plan{background:#ede9fe;color:#6d28d9}.rec-tag-trial{background:#d1fae5;color:#065f46}
</style>
