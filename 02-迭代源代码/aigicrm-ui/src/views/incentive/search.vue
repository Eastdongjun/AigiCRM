<template>
  <!-- 页面骨架（2026-09-20）：全局 .page-shell 已提供灰画布+白色整体容器，页内只保留三段小布局(筛选条 / 客户卡网格 / 底部分页)。
       【D9 2026-10-04 拆分】灯箱预览拆至 SearchLightbox.vue、客户详情(三联拨盘+已读观察器)拆至 SearchDetailDialog.vue、
       纯工具函数拆至 search-helpers.js，本组件只保留筛选/网格/分页与两者编排，行为不变。 -->
  <div class="isc-page">
    <div class="bar">
      <div class="bar-row">
        <el-input v-model="fCustomer" placeholder="搜索客户名称 / 关键字（如：煤气管道）..." clearable class="kw-main" @change="doSearch"><template #prefix><el-icon><Search /></el-icon></template></el-input>
        <el-button :type="showFilter?'primary':'default'" size="default" @click="showFilter=!showFilter">
          <el-icon><Filter /></el-icon> 高级筛选 <el-tag v-if="activeFilterCount" size="small" type="danger" effect="dark" style="margin-left:4px">{{ activeFilterCount }}</el-tag>
        </el-button>
        <el-select v-model="scoreOrder" placeholder="分值排序" clearable style="width:130px" @change="doSearch">
          <el-option label="分值从高到低" value="desc" />
          <el-option label="分值从低到高" value="asc" />
        </el-select>
        <el-button type="primary" @click="doSearch">查询</el-button>
        <el-button @click="doReset">重置</el-button>
        <span class="cnt"><el-icon><Folder /></el-icon> {{ custs.length }} 客户</span>
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
            <el-select v-model="fSales" placeholder="输入或选择同事" clearable filterable allow-create default-first-option @change="doSearch" size="small">
              <el-option v-for="u in userList" :key="u.id" :label="u.realName" :value="u.realName" />
            </el-select>
          </div>
          <div class="adv-field">
            <label class="adv-field-label"><el-icon><PriceTag /></el-icon> 产品小类</label>
            <div style="display:flex;gap:8px">
              <el-select v-model="fProdCat" placeholder="大类" clearable @change="fProdSub='';doSearch()" size="small" style="flex:1">
                <el-option v-for="k in productCatKeys" :key="k" :label="k" :value="k" />
              </el-select>
              <el-select v-model="fProdSub" placeholder="小类" clearable :disabled="!fProdCat" @change="doSearch" size="small" style="flex:1">
                <el-option v-for="s in (productCats[fProdCat] || [])" :key="s" :label="s" :value="s" />
              </el-select>
            </div>
          </div>
          <div class="adv-field">
            <label class="adv-field-label"><el-icon><OfficeBuilding /></el-icon> 行业</label>
            <el-select v-model="fIndustry" placeholder="全部行业" clearable @change="doSearch" size="small"><el-option v-for="i in industries" :key="i" :label="i" :value="i" /></el-select>
          </div>
          <div class="adv-field">
            <label class="adv-field-label"><el-icon><PriceTag /></el-icon> 开发类型</label>
            <el-select v-model="fDevType" placeholder="全部类型" clearable @change="doSearch" size="small"><el-option v-for="d in devTypes" :key="d" :label="d" :value="d" /></el-select>
          </div>
          <div class="adv-field span-2">
            <label class="adv-field-label"><el-icon><Calendar /></el-icon> 日期范围</label>
            <el-date-picker v-model="fDateRange" type="daterange" range-separator="~" start-placeholder="开始" end-placeholder="截止" value-format="YYYY-MM-DD" @change="doSearch" size="small" style="width:100%" />
          </div>
          <div class="adv-field span-2">
            <label class="adv-field-label"><el-icon><MapLocation /></el-icon> 组织筛选</label>
            <el-tree-select v-model="fOrgIds" :data="orgTree" :props="{label:'deptName',value:'id',children:'children'}" placeholder="选择大区 / 办事处" clearable filterable multiple check-strictly show-checkbox collapse-tags collapse-tags-tooltip @change="doSearch" size="small" style="width:100%" />
          </div>
        </div>
      </div>
    </div>

    <!-- 2026-09-13：原内嵌「本月激励查询阅读排行榜」文字条已移除，改由专门页面承载
         （系统设置 → 本月激励查询阅读排行榜，权限码 incentive:read-ranking:view，默认仅管理员可见） -->

    <div v-loading="loading" element-loading-text="加载中..." class="isc-grid">
      <div v-if="!loading && custs.length===0" style="grid-column:1/-1;text-align:center;padding:80px 0;color:#bbb">暂无数据</div>
      <div v-for="c in pagedCusts" :key="c.customerId" class="card">
        <div class="card-hd">
          <span class="card-name">{{ c.customerName }}</span>
          <el-tag v-if="c.isNew" size="small" type="danger" effect="dark">NEW</el-tag>
          <el-tag v-if="c.unread" size="small" type="warning">未读</el-tag>
          <span class="card-ind">{{ c.industry }}</span>
          <span class="card-num">{{ c.totalScore }}分</span>
        </div>
        <div v-if="c.records[0]" class="card-bd">
          <div class="card-meta">
            <span class="rec-tag" :class="'rec-tag-'+getCls(catMap, c.records[0])">{{ getMain(catMap, c.records[0]) }}</span>
            <span class="card-sub">{{ c.records[0].categoryName }}</span>
            <span class="card-info">{{ c.records[0].period }} · {{ c.records[0].userName }}</span>
            <span class="card-pts" :style="{color:scoreColor(getCls(catMap, c.records[0]))}">+{{ c.records[0].score }}</span>
          </div>
          <div v-if="getFiles(c.records[0]).length" class="card-imgs">
            <div v-for="f in getFiles(c.records[0]).slice(0,4)" :key="f.id" class="card-img">
              <template v-if="f.type==='image' && !thumbFailed.has(f.id)">
                <div class="img-wrap">
                  <img :src="thumbUrl(f.id)" @click="preview(f.id, f.type, f.label)" @error="markThumbFailed(f.id)" />
                  <div v-if="f.tag" class="img-tag">{{ f.tag }}</div>
                </div>
              </template>
              <div v-else-if="f.type==='image'" class="img-doc" @click="preview(f.id, f.type, f.label)">
                <el-icon :size="24"><Picture /></el-icon>
                <div class="doc-name">图片</div>
              </div>
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
    <div v-if="custs.length > pageSize" class="search-pager">
      <span class="search-pager-count">共 <b>{{ custs.length }}</b> 个客户</span>
      <el-pagination v-model:current-page="pageNum" :page-size="pageSize" :total="custs.length" :page-sizes="pageSizes(pageSize)" layout="sizes, prev, pager, next, jumper" size="small" background @size-change="onSizeChange" />
    </div>

    <!-- 灯箱预览（图片/视频/文档）与客户详情弹窗（三联拨盘时间轴） -->
    <SearchLightbox ref="lightbox" :thumb-failed="thumbFailed" @thumb-failed="markThumbFailed" />
    <SearchDetailDialog v-model="detailVisible" :cust="detailCust" :cat-map="catMap" :thumb-failed="thumbFailed"
      @preview="preview" @thumb-failed="markThumbFailed" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getCategoryTree } from '@/api/incentive'
import { isCurrentMonth, sortByScore } from '@/utils/incentive-read'
import { cachedGet } from '@/utils/layout-cache'
import { Search, User, VideoPlay, Document, Filter, Folder, OfficeBuilding, PriceTag, Calendar, MapLocation, Picture } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { paginate, pageSizes } from '@/utils/pagination'
// 【D10 2026-10-04】五大类产品目录改共享常量（原与 useIncentiveUpload.js 各硬编码一份）
import { PRODUCT_CATS as productCats, PRODUCT_CAT_KEYS as productCatKeys } from './product-catalog'
// 【D9 2026-10-04】文件/分类纯工具拆至共享模块；灯箱与详情弹窗拆为子组件
import { thumbUrl, downloadFile, getFiles, getMain, getCls, scoreColor } from './search-helpers'
import SearchLightbox from './SearchLightbox.vue'
import SearchDetailDialog from './SearchDetailDialog.vue'

const fCustomer=ref(''),fSales=ref(''),fIndustry=ref(''),fDevType=ref(''),fDateRange=ref(null),fOrgIds=ref([]),showFilter=ref(false)
const fProdCat=ref(''),fProdSub=ref('')
const scoreOrder=ref('')
const custs=ref([]),catMap=ref({})
// 缩略图加载失败标记：网格卡片 / 详情附件 / 灯箱缩略图共享同一份（打开灯箱时重置）
const thumbFailed=ref(new Set())
function markThumbFailed(id){thumbFailed.value=new Set([...thumbFailed.value,Number(id)])}
const loading=ref(false)
// 客户卡片网格分页（前端切片）
const pageNum = ref(1)
const pageSize = ref(8)

// 切换每页条数后回到第 1 页（避免停在已不存在的页码看到空网格）
const onSizeChange = (size) => { pageSize.value = size; pageNum.value = 1 }
const pagedCusts = computed(() => paginate(custs.value, pageNum.value, pageSize.value))
const detailVisible=ref(false),detailCust=ref(null)
const industries=['冶金','煤矿','化工','电力','有色金属','机械','工程公司','维修厂','其他终端客户']
const devTypes=['成熟创新','突破开拓','铺垫宣传']
const userList=ref([]),orgTree=ref([])
const activeFilterCount=computed(()=>[fSales.value,fIndustry.value,fDevType.value,fDateRange.value,fOrgIds.value?.length>0].filter(v=>!!v).length)

// 灯箱：委托子组件打开；缩略图失败标记随打开重置（原 preview 行为）
const lightbox = ref(null)
async function preview(id,type,name,gallery){
  thumbFailed.value=new Set()
  await lightbox.value?.open(id,type,name,gallery)
}
// 客户详情：设置当前客户并打开弹窗（弹窗内部自复位检索/拨盘/观察器）
function showDetail(c){ detailCust.value=c; detailVisible.value=true }

// 【M6 2026-10-04】请求序号守卫：慢的旧响应后到不再覆盖新结果，loading 以最后一个请求结束为准
let searchSeq = 0
async function doSearch(){
  pageNum.value = 1
  const p={}
  // 主搜索框：客户名 + 关键字（相关产品）双匹配（后端 OR 组合）
  if(fCustomer.value){p.customerName=fCustomer.value;p.keyword=fCustomer.value}
  if(fSales.value)p.salesperson=fSales.value;if(fIndustry.value)p.industry=fIndustry.value
  if(fDevType.value)p.devType=fDevType.value
  if(fDateRange.value?.length===2){p.startDate=fDateRange.value[0];p.endDate=fDateRange.value[1]}
  if(fOrgIds.value?.length){p.orgIds=fOrgIds.value.join(',')}
  if(fProdSub.value)p.product=fProdSub.value
  const seq = ++searchSeq
  // 【2026-08-08 首次加载体验】加 loading 态，避免首次并发请求时误显示"暂无数据"
  loading.value = true
  if(scoreOrder.value)p.scoreOrder=scoreOrder.value
  try{
    const res=await request.get('/incentive/customer-records',{params:p})
    if (seq !== searchSeq) return   // 已有更新的请求在途/完成，丢弃本次旧响应
    const rows=(res.data||[]).filter(c=>c.records?.length>0&&c.customerName)
    rows.forEach(c=>{c.isNew=(c.records||[]).some(r=>isCurrentMonth(r.createdAt));c.unread=(c.records||[]).some(r=>r.unread)})
    custs.value=sortByScore(rows,scoreOrder.value)
  }catch(e){
    // 【D13 2026-10-04】失败由 request 拦截器统一提示，本地不再叠加笼统"查询失败"；保留旧数据
  }finally{
    if (seq === searchSeq) loading.value = false
  }
}
function doReset(){fCustomer.value='';fSales.value='';fIndustry.value='';fDevType.value='';fDateRange.value=null;fOrgIds.value=[];fProdCat.value='';fProdSub.value='';scoreOrder.value='';doSearch()}
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
/* ===== 页面骨架（白色整体容器由全局 .page-shell 提供）===== */
.isc-page{display:flex;flex-direction:column;min-height:calc(100vh - 140px)}
.isc-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:14px;padding:16px 20px 20px;min-height:200px;
  flex:1;align-content:start}
.bar{background:transparent;padding:16px 20px 8px}
.bar-row{display:flex;gap:8px;align-items:center;flex-wrap:wrap}
.bar-adv{margin-top:12px;padding:16px 18px 18px;background:#f7f9fc;border-radius:12px;border:none;box-shadow:none}
.adv-header{display:flex;justify-content:space-between;align-items:center;margin-bottom:14px}
.adv-title{display:flex;align-items:center;gap:6px;font-size:14px;font-weight:600;color:#1f2329}
.adv-title .el-icon{color:var(--el-color-primary);font-size:15px}
.adv-header-right{display:flex;align-items:center;gap:10px}
.adv-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:12px 14px}
.adv-field{min-width:0;background:#fff;border:none;border-radius: var(--radius-md);padding:6px 10px 8px;transition:box-shadow .2s}
.adv-field:hover{box-shadow:0 2px 8px rgba(16,24,40,.07)}
.adv-field.span-2{grid-column:span 2}
.adv-field.span-4{grid-column:1/-1}
.adv-field-label{display:flex;align-items:center;gap:5px;font-size:12px;font-weight:600;color:#3a4a5a;margin-bottom:5px}
.adv-field-label .el-icon{font-size:13px;color:var(--el-color-primary)}
.adv-field :deep(.el-select),.adv-field :deep(.el-date-picker){width:100%}
.kw-main{width:240px}
.cnt{display:flex;align-items:center;gap:4px;font-size:13px;color:var(--el-color-primary);font-weight:600;white-space:nowrap;margin-left:auto}

/* 客户卡：白容器内的中性浅灰瓦片，悬浮抬升变白 */
.card{background:#f2f4f7;border:none;border-radius:12px;overflow:hidden;display:flex;flex-direction:column;min-height:264px;transition:background .18s,box-shadow .18s,transform .18s}
.card:hover{background:#fff;box-shadow:0 8px 22px rgba(16,24,40,.09);transform:translateY(-2px)}
.card-hd{padding:16px 18px 12px;display:flex;align-items:flex-start;gap:6px 8px;flex-wrap:wrap}
/* 【2026-09-20】客户名完整显示：独占整行(basis 100%)，最多两行，标签换行到名下不再挤占宽度 */
.card-name{font-size:15px;font-weight:700;flex:1 1 100%;min-width:0;line-height:1.45;
  display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden;word-break:break-all}
.card-ind{font-size:12px;padding:2px 8px;background:#eef0f4;border-radius: var(--radius-pill);color:#6b7280;white-space:nowrap}
.card-num{font-size:12px;padding:2px 0;background:transparent;border-radius:0;color:var(--el-color-primary);font-weight:800;white-space:nowrap}
.card-bd{flex:1;padding:0 18px 16px}
.card-meta{display:flex;align-items:center;gap:6px;margin-bottom:10px;flex-wrap:wrap;font-size:13px}
.card-sub{color:#555}.card-info{color: var(--text-muted);font-size:12px}
.card-pts{margin-left:auto;font-weight:600;font-size:15px}
.card-imgs{display:grid;grid-template-columns:repeat(4,1fr);gap:6px}
.card-img{width:100%;min-width:0}
.img-wrap{position:relative;border:none;border-radius:8px;overflow:hidden}
.img-tag{position:absolute;top:0;left:50%;transform:translateX(-50%);font-size:12px;color: var(--text-muted);background:rgba(255,255,255,.85);padding:2px 8px;border-radius:0 0 4px 4px;white-space:nowrap;z-index:1}
.card-img img{width:100%;aspect-ratio:1/1;object-fit:cover;cursor:pointer;transition:transform .3s;display:block;border-radius: var(--radius-sm)}
.card-img img:hover{transform:scale(1.06)}
.img-vid{width:100%;aspect-ratio:1/1;background:linear-gradient(135deg,#1a1a2e,#2d2d44);border-radius: var(--radius-sm);display:flex;align-items:center;justify-content:center;color:#fff;font-size:24px;cursor:pointer;position:relative;overflow:hidden}
.vid-overlay{width:48px;height:48px;border-radius:50%;background:rgba(255,255,255,.2);display:flex;align-items:center;justify-content:center;backdrop-filter:blur(4px);transition:transform .2s}
.img-vid:hover .vid-overlay{transform:scale(1.1)}
.img-doc{width:100%;aspect-ratio:1/1;background:#eef0f4;border:none;border-radius:8px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:4px;cursor:pointer;color:#9aa3ad;transition:all .15s}
.img-doc:hover{background:#e3ecf7;color:var(--el-color-primary)}
.doc-name{font-size:12px;max-width:80px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;text-align:center}
.card-ft{text-align:center;padding:11px;border-top:none;margin-top:auto;font-size:13px;color:var(--el-color-primary);cursor:pointer;font-weight:600}
.card-ft:hover{background:#f0f7ff}

.rec-tag{display:inline-block;padding:3px 10px;border-radius:4px;font-size:12px;font-weight:600}
.rec-tag-visit{background:#fef3c7;color:var(--chart-amber-deep)}.rec-tag-exchange{background:#dbeafe;color:#1e40af}
.rec-tag-plan{background:#ede9fe;color:#6d28d9}.rec-tag-trial{background:#d1fae5;color:#065f46}

/* 底部翻页区：计数在左、分页器在右，窄屏自动换行（与 reminder 的 .rem-pager 同口径）
   2026-09-13 修复：本轮给分页器补了「每页条数」下拉与跳页输入（layout 变宽）时，
   误删了 .search-pager / .search-pager-count 两条样式 ⇒ 容器退化成 block、无内边距，
   计数文案与分页器塌成两行并贴边。这里按现有口径补回并加上换行保护。 */
.search-pager{display:flex;align-items:center;flex-wrap:wrap;gap:12px;padding:10px 20px 14px;
  border-top:1px solid #eef0f3;margin:0 20px}  /* 【2026-09-20】详情卡区与分页区的浅色分割线 */
.search-pager-count{font-size:13px;color:#606266;white-space:nowrap;margin-right:auto}
.search-pager-count b{color:var(--el-color-primary);font-weight:700;margin:0 2px}
.search-pager .el-pagination{flex-shrink:0}
</style>
