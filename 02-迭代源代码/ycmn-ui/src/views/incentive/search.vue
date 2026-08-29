<template>
  <div style="margin:-20px">
    <div class="bar">
      <div class="bar-row">
        <el-input v-model="fCustomer" placeholder="搜索客户名称 / 关键字（如：煤气管道）..." clearable class="kw-main" @change="doSearch(true)"><template #prefix><el-icon><Search /></el-icon></template></el-input>
        <el-button :type="showFilter?'primary':'default'" size="default" @click="showFilter=!showFilter">
          <el-icon><Filter /></el-icon> 高级筛选 <el-tag v-if="activeFilterCount" size="small" type="danger" effect="dark" style="margin-left:4px">{{ activeFilterCount }}</el-tag>
        </el-button>
        <el-button type="primary" @click="doSearch(true)">查询</el-button>
        <el-button @click="doReset">重置</el-button>
        <span class="cnt"><el-icon><Folder /></el-icon> {{ custTotal }} 客户</span>
      </div>
      <div v-if="showFilter" class="bar-adv">
        <div class="adv-header">
          <span class="adv-title"><el-icon><Filter /></el-icon> 筛选条件</span>
          <span class="adv-header-right">
            <el-tag v-if="activeFilterCount" size="small" type="primary" effect="light" round>{{ activeFilterCount }} 项已选</el-tag>
            <el-button link size="small" @click="doReset" :disabled="activeFilterCount === 0">清空筛选</el-button>
          </span>
        </div>
        <div class="adv-grid">
          <div class="adv-field">
            <label class="adv-field-label"><el-icon><User /></el-icon> 同事姓名</label>
            <el-select v-model="fSales" placeholder="输入或选择同事" clearable filterable allow-create default-first-option @change="doSearch(true)" size="small">
              <el-option v-for="u in userList" :key="u.id" :label="u.realName" :value="u.realName" />
            </el-select>
          </div>
          <div class="adv-field">
            <label class="adv-field-label"><el-icon><PriceTag /></el-icon> 产品小类</label>
            <div style="display:flex;gap:8px">
              <el-select v-model="fProdCat" placeholder="大类" clearable @change="fProdSub='';doSearch(true)" size="small" style="flex:1">
                <el-option v-for="k in productCatKeys" :key="k" :label="k" :value="k" />
              </el-select>
              <el-select v-model="fProdSub" placeholder="小类" clearable :disabled="!fProdCat" @change="doSearch(true)" size="small" style="flex:1">
                <el-option v-for="s in (productCats[fProdCat] || [])" :key="s" :label="s" :value="s" />
              </el-select>
            </div>
          </div>
          <div class="adv-field">
            <label class="adv-field-label"><el-icon><OfficeBuilding /></el-icon> 行业</label>
            <el-select v-model="fIndustry" placeholder="全部行业" clearable @change="doSearch(true)" size="small"><el-option v-for="i in industries" :key="i" :label="i" :value="i" /></el-select>
          </div>
          <div class="adv-field">
            <label class="adv-field-label"><el-icon><PriceTag /></el-icon> 开发类型</label>
            <el-select v-model="fDevType" placeholder="全部类型" clearable @change="doSearch(true)" size="small"><el-option v-for="d in devTypes" :key="d" :label="d" :value="d" /></el-select>
          </div>
          <div class="adv-field span-2">
            <label class="adv-field-label"><el-icon><Calendar /></el-icon> 日期范围</label>
            <el-date-picker v-model="fDateRange" type="daterange" range-separator="~" start-placeholder="开始" end-placeholder="截止" value-format="YYYY-MM-DD" @change="doSearch(true)" size="small" style="width:100%" />
          </div>
          <div class="adv-field span-2">
            <label class="adv-field-label"><el-icon><MapLocation /></el-icon> 组织筛选</label>
            <el-tree-select v-model="fOrgIds" :data="orgTree" :props="{label:'deptName',value:'id',children:'children'}" placeholder="选择大区 / 办事处" clearable filterable multiple check-strictly show-checkbox collapse-tags collapse-tags-tooltip @change="doSearch(true)" size="small" style="width:100%" />
          </div>
        </div>
      </div>
    </div>

    <div v-loading="loading" element-loading-text="加载中..." style="display:grid;grid-template-columns:repeat(4,1fr);gap:16px;padding:20px;min-height:200px">
      <div v-if="!loading && custs.length===0" style="grid-column:1/-1;text-align:center;padding:80px 0;color:#bbb">暂无数据</div>
      <div v-for="c in pagedCusts" :key="c.customerId" class="card">
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
              <template v-if="f.type==='image'">
                <div class="img-wrap">
                  <img :src="thumbUrl(f.id)" alt="激励文件图片" @click="preview(f.id, f.type, f.label)" @error="onImgError" />
                  <div v-if="f.tag" class="img-tag">{{ f.tag }}</div>
                </div>
              </template>
              <div v-else-if="f.type==='video'" class="img-vid" @click="preview(f.id, f.type, f.label)">
                <div class="vid-overlay"><el-icon :size="28"><VideoPlay /></el-icon></div>
                <div v-if="f.tag" class="img-tag">{{ f.tag }}</div>
              </div>
              <div v-else class="img-doc" @click="downloadFile(f.id, f.label)">
                <el-icon :size="24"><Document /></el-icon>
                <div class="doc-name">{{ f.label?.substring(0,6) }}</div>
              </div>
            </div>
          </div>
        </div>
        <div class="card-ft" @click="showDetail(c)">查看详情 →</div>
      </div>
    </div>
    <div v-if="custTotal > pageSize" class="search-pager">
      <span class="search-pager-count">共 <b>{{ custTotal }}</b> 个客户</span>
      <el-pagination v-model:current-page="pageNum" v-model:page-size="pageSize" :page-sizes="[30, 50, 100]" :total="custTotal" layout="total, sizes, prev, pager, next" size="small" background @current-change="(p) => { pageNum = p; doSearch(false) }" @size-change="pageNum = 1; doSearch(false)" />
    </div>

    <!-- 预览弹窗：支持图片和视频 -->
    <el-dialog v-model="previewVisible" width="90vw" :close-on-click-modal="true" destroy-on-close>
      <template v-if="previewType==='video'">
        <video :src="previewUrl" controls autoplay style="width:100%;max-height:80vh;background:#000;border-radius:8px" />
      </template>
      <template v-else>
        <div style="text-align:center">
          <img :src="previewUrl" alt="文件预览" style="max-width:100%;max-height:80vh;object-fit:contain;border-radius:8px" @error="onImgError" />
        </div>
      </template>
      <div style="text-align:center;margin-top:12px">
        <el-button type="primary" size="small" @click="downloadPreview"><el-icon><Download /></el-icon> 下载文件</el-button>
      </div>
    </el-dialog>

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
            <span>最近更新 {{ latestRecord(detailCust) ? fmtDt(latestRecord(detailCust).createdAt) : '-' }}</span>
          </div>
        </div>
        <div class="dtl-timeline">
          <div v-for="(r,i) in detailCust.records" :key="r.id" class="dtl-item" :class="{first:i===0}">
            <div class="dtl-dot" :style="{background:scoreColor(getCls(r)), boxShadow:'0 0 0 3px '+scoreColor(getCls(r))+'22'}"></div>
            <div class="dtl-head">
              <span class="dtl-date">{{ fmtDt(r.createdAt) }}</span>
              <span class="rec-tag" :class="'rec-tag-'+getCls(r)">{{ getMain(r) }}</span>
              <span class="dtl-subcat">{{ r.categoryName }}</span>
              <span class="dtl-head-score" :style="{color:scoreColor(getCls(r))}">+{{ r.score }}分</span>
            </div>
            <div class="dtl-content" :style="{borderLeftColor:scoreColor(getCls(r))}">
              <div class="dtl-upload-chips">
                <span class="dtl-chip cust"><i>客户</i><b :style="{color:scoreColor(getCls(r))}">{{ r.customerName || detailCust.customerName }}</b></span>
                <span class="dtl-chip" v-if="r.industry"><i>行业</i>{{ r.industry }}</span>
                <span class="dtl-chip" v-if="r.relatedProducts"><i>相关产品</i>{{ r.relatedProducts }}</span>
                <span class="dtl-chip" v-if="r.specificProduct"><i>具体产品</i>{{ r.specificProduct }}</span>
                <span class="dtl-chip"><i>上传人</i>{{ r.userName }}</span>
                <span class="dtl-chip"><i>周期</i>{{ r.period }}</span>
              </div>
              <div v-if="r.preInstallQuestion" class="dtl-field desc" :style="{borderLeftColor:scoreColor(getCls(r))}">
                <div class="dtl-field-label"><el-icon style="vertical-align:-2px;margin-right:4px"><Notebook /></el-icon>{{ detailLabelOf(r) }}</div>
                <div class="dtl-field-text">{{ r.preInstallQuestion }}</div>
              </div>
              <div v-if="r.remark && isTrialRec(r)" class="dtl-field remark">
                <div class="dtl-field-label"><el-icon style="vertical-align:-2px;margin-right:4px"><Warning /></el-icon>{{ r.categoryName }}安装备注</div>
                <div class="dtl-field-text">{{ r.remark }}</div>
              </div>
              <div v-if="getFiles(r).length" class="dtl-files">
                <div v-for="f in getFiles(r)" :key="f.id" class="dtl-file">
                  <template v-if="f.type==='image'">
                    <div class="dtl-img-wrap">
                      <img :src="thumbUrl(f.id)" alt="激励文件图片" @click="preview(f.id, f.type, f.label)" @error="onImgError" />
                      <div v-if="f.tag" class="dtl-file-tag" :style="{color:tagColor(f.tag), borderColor:tagColor(f.tag)}">{{ f.tag }}</div>
                      <div class="dtl-file-acts">
                        <el-button size="small" circle type="primary" @click.stop="preview(f.id, f.type, f.label)"><el-icon><ZoomIn /></el-icon></el-button>
                        <el-button size="small" circle @click.stop="downloadFile(f.id, f.label)"><el-icon><Download /></el-icon></el-button>
                      </div>
                    </div>
                  </template>
                  <div v-else-if="f.type==='video'" class="dtl-file-vid">
                    <video :src="fileUrl(f.id)" controls preload="metadata" style="width:100%;max-height:240px;background:#000;border-radius:6px"></video>
                    <div style="display:flex;align-items:center;gap:8px;margin-top:4px">
                      <span v-if="f.tag" class="dtl-file-tag" :style="{color:tagColor(f.tag), borderColor:tagColor(f.tag)}">{{ f.tag }}</span>
                      <span class="doc-label" style="flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">{{ f.label }}</span>
                      <el-button size="small" circle @click.stop="downloadFile(f.id, f.label)"><el-icon><Download /></el-icon></el-button>
                    </div>
                  </div>
                  <div v-else class="dtl-file-doc" @click="downloadFile(f.id, f.label)">
                    <el-icon :size="28"><Document /></el-icon>
                    <span class="doc-label">{{ f.label }}</span>
                    <el-button size="small" circle @click.stop="downloadFile(f.id, f.label)"><el-icon><Download /></el-icon></el-button>
                  </div>
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
import { getCategoryTree } from '@/api/incentive'
import { downloadFile as downloadFileUtil } from '@/utils/download'
import { cachedGet } from '@/utils/layout-cache'
import request from '@/utils/request'
import { getDownloadToken, getToken, refreshDownloadToken } from '@/utils/token'
import { Calendar, Document, Download, Filter, Folder, MapLocation, Notebook, OfficeBuilding, PriceTag, Search, User, VideoPlay, Warning, ZoomIn } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onMounted, ref } from 'vue'

const fCustomer=ref(''),fSales=ref(''),fIndustry=ref(''),fDevType=ref(''),fDateRange=ref(null),fOrgIds=ref([]),showFilter=ref(false)
const fProdCat=ref(''),fProdSub=ref('')
const productCats={ '涂层类':['防腐','加固','钢结构','煤气管道','其他'], '防滑板类':['公共区','装置区'], '机封类':['水泵','渣泵','介质泵'], '密封类':['静密封','动密封','橡胶密封'], '润滑类':['润滑油','润滑脂','润滑剂'] }
const productCatKeys=Object.keys(productCats)
const custs=ref([]),catMap=ref({}),previewVisible=ref(false),previewUrl=ref(''),previewType=ref('image'),previewId=ref(null),previewName=ref('')
const loading=ref(false)
// 【列表页真分页 2026-08-28】后端分页（翻页才拉下一页），custs=当前页
const pageNum = ref(1)
const pageSize = ref(30)
const custTotal = ref(0)
const pagedCusts = computed(() => custs.value)
const detailVisible=ref(false),detailCust=ref(null)
const industries=['冶金','煤矿','化工','电力','有色金属','机械','工程公司','维修厂','其他终端客户']
const devTypes=['成熟创新','突破开拓','铺垫宣传']
const userList=ref([]),orgTree=ref([])
const activeFilterCount=computed(()=>[fSales.value,fIndustry.value,fDevType.value,fDateRange.value,fOrgIds.value?.length>0].filter(v=>!!v).length)

function onImgError(e){e.target.src='data:image/svg+xml,'+encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="320" height="240" viewBox="0 0 320 240"><rect fill="#f5f6f7" width="320" height="240"/><text fill="#c0c4cc" font-size="14" font-family="sans-serif" text-anchor="middle" x="160" y="120">图片加载失败</text></svg>')}
// 取客户记录中最新一条（排序已按品类，不与时间线顺序绑定）
function latestRecord(c){let best=null;for(const r of (c?.records||[])){if(!best||String(r.createdAt||'')>String(best.createdAt||''))best=r}return best}
// 【修复 2026-08-14】下载 token 未就绪时不拼 token=（避免产生必然 403 的空 token 请求）；
// token 就绪后 getDownloadToken() 读取的 dlToken ref 变化会触发渲染链重跑，<img> 自动用带 token 的 URL 重载
function fileUrl(id){const t=getDownloadToken();return t?'/api/file/download/'+id+'?token='+t:'/api/file/download/'+id}
function thumbUrl(id){const t=getDownloadToken();return t?'/api/file/download/'+id+'?token='+t+'&thumbnail=1':'/api/file/download/'+id}
async function preview(id,type,name){await refreshDownloadToken();previewUrl.value=fileUrl(id);previewType.value=type==='video'?'video':'image';previewVisible.value=true;previewName.value=name||''}
// 【需求】文件下载(FEAT 0804):window.open 下载在新标签闪开/被拦截时表现为"没反应";
// 改为 fetch blob + a.download 强制保存,任何浏览器/任何状态下可靠触发
async function downloadFile(id, name) {
  if (!id) return
  await refreshDownloadToken()
  await downloadFileUtil(fileUrl(id), { name: name || ('file-' + id), headers: { 'Authorization': 'Bearer ' + (getToken() || '') } })
}
function downloadPreview(){ if (previewUrl.value) { const m = previewUrl.value.match(/\/download\/(\d+)/); downloadFile(m ? m[1] : null, previewName.value) } }
function showDetail(c){detailCust.value=c;detailVisible.value=true}

function parseIds(v){if(!v)return[];if(typeof v==='number')return[v];try{const a=JSON.parse(v);return Array.isArray(a)?a.map(x=>Number(x)):[v]}catch{return String(v).split(',').map(s=>s.trim()).filter(Boolean)}}
function splitNames(n){if(!n)return[];return String(n).split(',').map(s=>s.trim()).filter(Boolean)}
function pushFiles(f, ids, names, tag){
  const idList=parseIds(ids), nameList=splitNames(names)
  idList.forEach((id,i)=>{f.push({id:Number(id),label:nameList[i]||'文件',type:detectType(nameList[i]||''),tag})})
}
function getFiles(r){
  const f=[]
  pushFiles(f, r.filePreId, r.filePreName, '安装前')
  pushFiles(f, r.fileMidId, r.fileMidName, '安装中')
  pushFiles(f, r.filePostId, r.filePostName, '安装后')
  pushFiles(f, r.fileId, r.fileName, '')
  pushFiles(f, r.fileTrackId, r.fileTrackName, '跟踪')
  return f.filter(x => x.id)
}
function detectType(n){if(!n)return'doc';if(/\.(jpg|jpeg|png|gif|webp|bmp)$/i.test(n))return'image';if(/\.(mp4|mov|avi|webm)$/i.test(n))return'video';return'doc'}
function getMain(r){const c=catMap.value[r.categoryId];if(!c)return'';if(c.parentId===0)return c.categoryName;const p=catMap.value[c.parentId];if(!p)return c.categoryName;const gp=catMap.value[p.parentId];return gp&&gp.parentId===0?gp.categoryName:p.categoryName}
function getCls(r){const c=catMap.value[r.categoryId];if(!c)return'';if(c.parentId===0)return c.categoryCode||'';const p=catMap.value[c.parentId];if(!p)return'';const gp=catMap.value[p.parentId];return(gp&&gp.parentId===0?gp.categoryCode:p.categoryCode)||''}
function scoreColor(c){return{visit:'#d97706',exchange:'#2563eb',plan:'#7c3aed',trial:'#059669'}[c]||'#303133'}
// 文件槽位标签颜色（安装前蓝/安装中绿/安装后橙/跟踪紫）
function tagColor(t){return{安装前:'#2563eb',安装中:'#059669',安装后:'#f59e0b',跟踪:'#7c3aed'}[t]||'#6b7280'}
// 详情字段标签 = 子分类名 + 阶段词（与上传弹窗一致）
function detailLabelOf(r){const code=getCls(r);const sub=r.categoryName||'';if(code==='plan')return sub+'文件详情描述';if(code==='visit'||code==='exchange')return sub+'详情描述';return sub+'安装详情'}
// 是否产品试点记录（安装备注仅试点有）
function isTrialRec(r){return (getCls(r)||'').startsWith('trial')}
function fmtDt(t){if(!t)return'';const d=new Date(t);return d.getFullYear()+'-'+(d.getMonth()+1).toString().padStart(2,'0')+'-'+d.getDate().toString().padStart(2,'0')+' '+d.getHours().toString().padStart(2,'0')+':'+d.getMinutes().toString().padStart(2,'0')}

// resetPage=true 时回到第 1 页（查询/筛选变化）；翻页调用传 false 保持当前页
async function doSearch(resetPage = false){
  if (resetPage) pageNum.value = 1
  const p={}
  // 主搜索框：客户名 + 关键字（相关产品）双匹配（后端 OR 组合）
  if(fCustomer.value){p.customerName=fCustomer.value;p.keyword=fCustomer.value}
  if(fSales.value)p.salesperson=fSales.value;if(fIndustry.value)p.industry=fIndustry.value
  if(fDevType.value)p.devType=fDevType.value
  if(fDateRange.value?.length===2){p.startDate=fDateRange.value[0];p.endDate=fDateRange.value[1]}
  if(fOrgIds.value?.length){p.orgIds=fOrgIds.value.join(',')}
  if(fProdSub.value)p.product=fProdSub.value
  // 【2026-08-08 首次加载体验】加 loading 态，避免首次并发请求时误显示"暂无数据"
  loading.value = true
  // 【列表页真分页 2026-08-28】翻页才拉下一页（修复一次拉全量 186KB 导致页面数据卡顿）
  p.current=pageNum.value;p.size=pageSize.value
  try{const res=await request.get('/incentive/customer-records',{params:p});const data=res.data||{};custs.value=Array.isArray(data.records)?data.records.filter(c=>c.records?.length>0&&c.customerName):(Array.isArray(data)?data.filter(c=>c.records?.length>0&&c.customerName):[]);custTotal.value=data.total||custs.value.length}catch(e){ElMessage.error('查询失败')}finally{loading.value=false}
}
function doReset(){fCustomer.value='';fSales.value='';fIndustry.value='';fDevType.value='';fDateRange.value=null;fOrgIds.value=[];fProdCat.value='';fProdSub.value='';doSearch(true)}
async function loadCats(){try{const res=await cachedGet('categoryTree',getCategoryTree);const walk=l=>{l.forEach(c=>{catMap.value[c.id]=c;if(c.children)walk(c.children)})};walk(res.data||[])}catch(e){}}
async function loadUsers(){try{const res=await cachedGet('incentiveUsers',()=>request.get('/incentive/users'));userList.value=res.data||[]}catch(e){}}
async function loadOrgs(){try{const res=await cachedGet('depts',()=>request.get('/system/depts'));const list=res.data||[]
  const build=(list,pId=0)=>list.filter(d=>Number(d.parentId)===Number(pId)).sort((a,b)=>(a.sortOrder||0)-(b.sortOrder||0)).map(d=>{const ch=build(list,d.id);return ch.length?{...d,children:ch}:{...d}})
  const filtered=list.filter(d=>d.deptType!==1) // 跳过总部级
  const rootIds=new Set(filtered.map(d=>d.id))
  const fixed=filtered.map(d=>({...d,parentId:rootIds.has(d.parentId)?d.parentId:0}))
  orgTree.value=build(fixed)}catch(e){}}
onMounted(()=>{loadCats();loadUsers();loadOrgs();doSearch()})
</script>

<style scoped>
.bar{background:#fff;border-bottom:1px solid #ebeef5;padding:12px 20px}
.bar-row{display:flex;gap:8px;align-items:center;flex-wrap:wrap}
.bar-adv{margin-top:14px;padding:16px 20px 18px;background:#fff;border-radius:12px;border:1px solid #e8ecf1;border-top:3px solid #409eff;box-shadow:0 2px 10px rgba(31,45,61,.05)}
.adv-header{display:flex;justify-content:space-between;align-items:center;margin-bottom:14px}
.adv-title{display:flex;align-items:center;gap:6px;font-size:14px;font-weight:600;color:#1f2329}
.adv-title .el-icon{color:#409eff;font-size:15px}
.adv-header-right{display:flex;align-items:center;gap:10px}
.adv-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:12px 14px}
.adv-field{min-width:0;background:#f7f9fc;border:1px solid #f0f2f5;border-radius:8px;padding:6px 10px 8px;transition:border-color .2s, box-shadow .2s}
.adv-field:hover{border-color:#d3e3f7;box-shadow:0 2px 6px rgba(64,158,255,.06)}
.adv-field.span-2{grid-column:span 2}
.adv-field.span-4{grid-column:1/-1}
.adv-field-label{display:flex;align-items:center;gap:5px;font-size:12px;font-weight:600;color:#3a4a5a;margin-bottom:5px}
.adv-field-label .el-icon{font-size:13px;color:#409eff}
.adv-field :deep(.el-select),.adv-field :deep(.el-date-picker){width:100%}
.kw-main{width:240px}
.cnt{display:flex;align-items:center;gap:4px;font-size:13px;color:#409eff;font-weight:600;white-space:nowrap;margin-left:auto}

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
.img-wrap{position:relative;border:1px solid rgba(0,0,0,.06);border-radius:6px;overflow:hidden}
.img-tag{position:absolute;top:0;left:50%;transform:translateX(-50%);font-size:10px;color:#999;background:rgba(255,255,255,.85);padding:2px 8px;border-radius:0 0 4px 4px;white-space:nowrap;z-index:1}
.card-img img{width:100%;aspect-ratio:4/3;object-fit:cover;cursor:pointer;transition:transform .3s;display:block}
.card-img img:hover{transform:scale(1.06)}
.img-vid{width:100%;aspect-ratio:4/3;background:linear-gradient(135deg,#1a1a2e,#2d2d44);border-radius:6px;display:flex;align-items:center;justify-content:center;color:#fff;font-size:24px;cursor:pointer;position:relative;overflow:hidden}
.vid-overlay{width:48px;height:48px;border-radius:50%;background:rgba(255,255,255,.2);display:flex;align-items:center;justify-content:center;backdrop-filter:blur(4px);transition:transform .2s}
.img-vid:hover .vid-overlay{transform:scale(1.1)}
.img-doc{width:100%;aspect-ratio:4/3;background:#fafbfc;border:1px solid #e8ecf1;border-radius:6px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:4px;cursor:pointer;color:#909399;transition:all .15s}
.img-doc:hover{background:#ecf5ff;border-color:#409eff;color:#409eff}
.doc-name{font-size:10px;max-width:80px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;text-align:center}
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
.dtl-wrap{max-width:900px;margin:0 auto;padding:24px 28px;border:1px solid #e3e6ec;border-radius:12px;background:#fff;box-shadow:0 2px 12px rgba(0,0,0,.04)}
.dtl-back{font-size:15px;color:#999;cursor:pointer;margin-bottom:20px;display:inline-flex;align-items:center;gap:4px;padding:4px 10px;border-radius:6px;transition:all .15s}
.dtl-back:hover{color:#0052d9;background:#f2f5fa}
.dtl-title{font-size:22px;font-weight:700;color:#1f2329;margin-bottom:8px}
.dtl-subtitle{font-size:15px;color:#8f959e;margin-bottom:40px}
.dtl-summary{margin-bottom:28px;padding:20px 24px;background:linear-gradient(135deg,#f8fafd,#eef2f8);border:1px solid #e3e6ec;border-radius:10px;border-left:4px solid #0052d9}
.dtl-sum-name{font-size:22px;font-weight:800;color:#1f2329;margin-bottom:12px}
.dtl-sum-meta{display:flex;align-items:center;gap:6px;font-size:14px;color:#646a73;flex-wrap:wrap}
.dtl-sum-sep{color:#d0d3d8}
.dtl-sum-pts{color:#0052d9;font-weight:700;font-size:15px}
.dtl-timeline{position:relative}
.dtl-timeline::before{content:'';position:absolute;left:6px;top:6px;bottom:6px;width:2px;background:#e5e6eb}
.dtl-item{position:relative;padding:0 0 0 36px;margin-bottom:24px}
.dtl-item.first .dtl-date{color:#0052d9}
.dtl-dot{position:absolute;left:-1px;top:20px;width:16px;height:16px;border-radius:50%;background:#c9cdd4;border:3px solid #fff;transition:transform .2s}
.dtl-head{display:flex;align-items:center;gap:10px;margin-bottom:10px;flex-wrap:wrap}
.dtl-date{font-size:17px;font-weight:700;color:#1f2329}
.dtl-subcat{font-size:14px;color:#8f959e;font-weight:500}
.dtl-head-score{font-weight:700;font-size:15px;margin-left:auto}
.dtl-content{margin-top:0;padding:14px 18px;border:1px solid #edf0f4;border-left:4px solid #e3e6ec;border-radius:10px;background:#fcfcfd;transition:box-shadow .2s,transform .2s}
.dtl-item:hover .dtl-content{box-shadow:0 3px 12px rgba(0,0,0,.06);transform:translateX(2px)}
.dtl-upload-chips{display:flex;flex-wrap:wrap;gap:8px;margin-bottom:12px}
.dtl-chip{display:inline-flex;align-items:center;gap:6px;font-size:13px;color:#4e5969;background:#fff;border:1px solid #e8ebef;border-radius:6px;padding:3px 10px}
.dtl-chip i{font-style:normal;font-size:12px;color:#9aa0a8}
.dtl-chip.cust b{font-weight:700}
.dtl-field{margin-bottom:12px;padding:10px 14px;background:#f7f8fa;border-radius:6px;border-left:3px solid #d5d9e0}
.dtl-field.desc{background:#fff;border:1px solid #eef0f3;border-left:4px solid #d5d9e0;padding:14px 16px}
.dtl-field.desc .dtl-field-label{color:#4e5969;font-size:13px;font-weight:600}
.dtl-field.remark{background:#fffaf5;border-left:4px solid #f59e0b;border-color:#fde6c8}
.dtl-field.remark .dtl-field-label{color:#b45309}
.dtl-field-label{font-size:12px;color:#8f959e;margin-bottom:6px;letter-spacing:.5px}
.dtl-field-text{font-size:14px;color:#1f2329;line-height:1.8;white-space:pre-wrap;word-break:break-word}
.search-pager{display:flex;align-items:center;justify-content:flex-end;gap:12px;padding:8px 20px 16px}
.search-pager-count{font-size:13px;color:#606266}
.search-pager-count b{color:#409eff;font-weight:700;margin:0 2px}
.dtl-files{display:flex;gap:14px;flex-wrap:wrap;margin-top:14px;padding-top:12px;border-top:1px solid #f0f2f5}
.dtl-file{width:260px}
.dtl-img-wrap{position:relative;border:1px solid rgba(0,0,0,.06);border-radius:6px;overflow:hidden}
.dtl-file-tag{position:absolute;top:0;left:50%;transform:translateX(-50%);font-size:11px;font-weight:600;background:rgba(255,255,255,.94);padding:2px 10px;border-radius:0 0 4px 4px;white-space:nowrap;z-index:1;border:1px solid transparent;border-top:none}
.dtl-file img{width:100%;height:220px;object-fit:cover;cursor:pointer;display:block}
.dtl-file img:hover{opacity:.85}
.dtl-file-vid{width:100%;height:220px;background:linear-gradient(135deg,#1a1a2e,#2d2d44);border-radius:8px;display:flex;align-items:center;justify-content:center;cursor:pointer;color:#fff;font-size:26px;position:relative;overflow:hidden}
.dtl-file-vid .vid-overlay{width:56px;height:56px;border-radius:50%;background:rgba(255,255,255,.2);display:flex;align-items:center;justify-content:center;transition:transform .2s}
.dtl-file-vid:hover .vid-overlay{transform:scale(1.12)}
.dtl-file-doc{width:100%;height:220px;background:#fafbfc;border:1px solid #e5e6eb;border-radius:8px;display:flex;flex-direction:column;align-items:center;justify-content:center;cursor:pointer;color:#646a73;font-size:13px;text-align:center;gap:8px;padding:8px;position:relative;transition:all .15s}
.dtl-file-doc:hover{background:#ecf5ff;border-color:#409eff;color:#409eff}
.doc-label{color:#333;font-weight:500;font-size:14px;max-width:180px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.dtl-file-acts{position:absolute;top:10px;right:10px;display:flex;gap:6px;opacity:0;transition:opacity .2s;z-index:2}
.dtl-img-wrap:hover .dtl-file-acts,.dtl-file-vid:hover .dtl-file-acts,.dtl-file-doc:hover .dtl-file-acts{opacity:1}
</style>
