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
      </div>
    </div>

    <div style="display:grid;grid-template-columns:repeat(4,1fr);gap:16px;padding:20px">
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

    <el-dialog v-model="detailVisible" width="1000px" top="4vh" :close-on-click-modal="true" destroy-on-close class="detail-dlg">
      <template #header></template>
      <div class="dtl-wrap" v-if="detailCust">
        <div class="dtl-back" @click="detailVisible=false">← 返回列表</div>
        <div class="dtl-summary">
          <div class="dtl-sum-name">{{ detailCust.customerName }}</div>
          <div class="dtl-sum-meta">
            <span>{{ detailCust.industry }}</span>
            <span class="dtl-sum-sep">|</span>
            <span>{{ detailCust.count }}项激励</span>
            <span class="dtl-sum-sep">|</span>
            <span class="dtl-sum-pts">{{ detailCust.totalScore }}分</span>
            <span class="dtl-sum-sep">|</span>
            <span>最近更新 {{ detailCust.records[0] ? fmtDt(detailCust.records[0].createdAt) : '-' }}</span>
          </div>
        </div>
        <div class="dtl-timeline">
          <div v-for="(r,i) in detailCust.records" :key="r.id" class="dtl-item" :class="{first:i===0}">
            <div class="dtl-dot"></div>
            <div class="dtl-head">
              <span class="dtl-date">{{ fmtDt(r.createdAt) }}</span>
              <span class="rec-tag" :class="'rec-tag-'+getCls(r)">{{ getMain(r) }}</span>
            </div>
            <div class="dtl-content">
              <div class="dtl-info-line">
                <span>{{ r.categoryName }}</span>
                <span>· {{ r.userName }}</span>
                <span>· {{ r.period }}</span>
                <span v-if="r.industry">· {{ r.industry }}</span>
                <span v-if="r.relatedProducts">· {{ r.relatedProducts }}</span>
                <span class="dtl-info-pts" :style="{color:scoreColor(getCls(r))}">+{{ r.score }}分</span>
              </div>
              <div v-if="getFiles(r).length" class="dtl-files">
                <div v-for="f in getFiles(r)" :key="f.id" class="dtl-file">
                  <div class="dtl-file-tag">{{ f.tag }}</div>
                  <img v-if="f.type==='image'" :src="'/api/file/download/'+f.id" @click="preview(f.id)" />
                  <div v-else-if="f.type==='video'" class="dtl-file-vid" @click="preview(f.id)">▶</div>
                  <div v-else class="dtl-file-doc" @click="window.open('/api/file/download/'+f.id)">📄 {{ f.label }}</div>
                </div>
              </div>
            </div>
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

const fCustomer=ref(''),fSales=ref(''),fIndustry=ref(''),fType=ref(''),fDateRange=ref(null)
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
function fmtDt(t){if(!t)return'';const d=new Date(t);return d.getFullYear()+'-'+(d.getMonth()+1).toString().padStart(2,'0')+'-'+d.getDate().toString().padStart(2,'0')+' '+d.getHours().toString().padStart(2,'0')+':'+d.getMinutes().toString().padStart(2,'0')}

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

.d-page{max-width:1000px;margin:0 auto;padding:40px 20px}
.d-top{margin-bottom:40px}
.d-back{font-size:14px;color:#999;cursor:pointer;display:inline-block;margin-bottom:16px}
.d-back:hover{color:#555}
.d-name{font-size:24px;font-weight:700;margin-bottom:4px}
.d-subt{font-size:14px;color:#888}

.d-list{position:relative;padding-left:140px}
.d-list::before{content:'';position:absolute;left:8px;top:0;bottom:0;width:2px;background:#e8eaed}

.d-node{position:relative;margin-bottom:40px}
.d-node:last-child{margin-bottom:0}
.d-node-left{position:absolute;left:-132px;top:0;width:120px;text-align:right}
.d-node-dot{position:absolute;right:-16px;top:5px;width:10px;height:10px;border-radius:50%;background:#409eff;border:2px solid #fff;box-shadow:0 0 0 2px #409eff;z-index:1}
.d-node-time{font-size:13px;color:#333;font-weight:500}

.d-node-right{}
.d-node-tags{display:flex;align-items:center;gap:8px;flex-wrap:wrap;margin-bottom:12px;font-size:14px}
.d-node-sub{color:#333;font-weight:600}
.d-node-who{color:#999;font-size:13px}

.d-node-imgs{display:grid;grid-template-columns:repeat(auto-fill,minmax(240px,1fr));gap:12px}
.d-node-img{position:relative;border-radius:8px;overflow:hidden;border:1px solid #eee}
.d-node-img img{width:100%;height:220px;object-fit:cover;display:block;cursor:pointer}
.d-node-vid{width:100%;height:220px;background:#111;display:flex;align-items:center;justify-content:center;color:#fff;font-size:28px;cursor:pointer}
.d-node-doc{width:100%;height:220px;background:#fefce8;display:flex;flex-direction:column;align-items:center;justify-content:center;color:#a16207;cursor:pointer;font-size:13px;text-align:center;padding:16px}
.d-node-lbl{position:absolute;bottom:0;left:0;right:0;padding:4px 10px;background:rgba(0,0,0,.55);color:#fff;font-size:10px;font-weight:600;text-align:center}

.detail-dlg :deep(.el-dialog__header){display:none}
.detail-dlg :deep(.el-dialog__body){padding:0}
.dtl-wrap{max-width:900px;margin:0 auto;padding:32px 0}
.dtl-back{font-size:15px;color:#999;cursor:pointer;margin-bottom:32px}
.dtl-back:hover{color:#555}
.dtl-title{font-size:22px;font-weight:700;color:#1f2329;margin-bottom:8px}
.dtl-subtitle{font-size:15px;color:#8f959e;margin-bottom:40px}
.dtl-summary{margin-bottom:40px;padding:24px 28px;background:#f7f8fa;border-radius:10px}
.dtl-sum-name{font-size:22px;font-weight:800;color:#1f2329;margin-bottom:12px}
.dtl-sum-meta{display:flex;align-items:center;gap:6px;font-size:14px;color:#646a73;flex-wrap:wrap}
.dtl-sum-sep{color:#d0d3d8}
.dtl-sum-pts{color:#0052d9;font-weight:700;font-size:15px}
.dtl-timeline{position:relative}
.dtl-timeline::before{content:'';position:absolute;left:6px;top:0;bottom:0;width:2px;background:#e5e6eb}
.dtl-item{position:relative;padding:0 0 0 36px;margin-bottom:44px}
.dtl-item.first .dtl-dot{background:#0052d9;box-shadow:0 0 0 4px rgba(0,82,217,.15)}
.dtl-item.first .dtl-date{color:#0052d9}
.dtl-dot{position:absolute;left:-1px;top:5px;width:16px;height:16px;border-radius:50%;background:#c9cdd4;border:3px solid #fff;box-shadow:0 0 0 1px #c9cdd4}
.dtl-head{display:flex;align-items:center;gap:12px;margin-bottom:8px}
.dtl-date{font-size:18px;font-weight:700;color:#1f2329}
.dtl-content{margin-top:8px}
.dtl-info-line{font-size:15px;color:#646a73;margin-bottom:12px}
.dtl-info-pts{font-weight:700;font-size:16px;margin-left:auto}
.dtl-files{display:flex;gap:14px;flex-wrap:wrap;margin-top:14px}
.dtl-file{width:260px}
.dtl-file-tag{font-size:12px;color:#8f959e;margin-bottom:6px}
.dtl-file img{width:100%;height:220px;object-fit:cover;border-radius:6px;cursor:pointer;border:1px solid #e5e6eb;display:block}
.dtl-file img:hover{opacity:.85}
.dtl-file-vid{width:100%;height:220px;background:#1a1a2e;border-radius:6px;display:flex;align-items:center;justify-content:center;cursor:pointer;color:#fff;font-size:26px}
.dtl-file-doc{width:100%;height:220px;background:#f5f6f7;border:1px solid #e5e6eb;border-radius:6px;display:flex;flex-direction:column;align-items:center;justify-content:center;cursor:pointer;color:#646a73;font-size:13px;text-align:center;gap:4px;padding:8px}
</style>
