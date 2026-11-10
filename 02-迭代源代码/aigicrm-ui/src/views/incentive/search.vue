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
            <label class="adv-field-label"><el-icon><PriceTag /></el-icon> 分类</label>
            <!-- 【2026-10-28 用户令】分类两级化（与小程序轻量页 B 案同口径）: 一级=分类树大类
                 (外出参观/交流拜访/两谈两做方案/产品试点), 二级=该大类子类。一级走后端 mainCategoryId
                 (接口按大类含子孙类过滤), 二级按子类名客户端过滤记录。标签用「分类」与 MP 对齐,
                 与下方「开发类型」(客户属性字典)是两个不同的轴。 -->
            <div style="display:flex;gap:8px">
              <el-select v-model="fMainCat" placeholder="一级分类" clearable @change="onMainCatChange" size="small" style="flex:1">
                <el-option v-for="c in mainCats" :key="c.id" :label="c.categoryName" :value="c.id" />
              </el-select>
              <el-select v-model="fSubCat" placeholder="二级分类" clearable :disabled="!fMainCat" @change="doSearch" size="small" style="flex:1">
                <el-option v-for="s in subCats" :key="s" :label="s" :value="s" />
              </el-select>
            </div>
          </div>
          <div class="adv-field">
            <label class="adv-field-label"><el-icon><PriceTag /></el-icon> 开发类型</label>
            <!-- 【2026-10-29 用户令】改调系统字典(系统设置→开发类型数据列表, /system/dict/data/dev_type,
                 isAuthenticated 即可拉): 原硬编码三项缺「新建改建扩建大修」, 字典后续增删值下拉自动跟随;
                 接口失败兜底=四项全量(与客户表单 useCustomerForm 兜底同口径) -->
            <el-select v-model="fDevType" placeholder="全部类型" clearable filterable @change="doSearch" size="small">
              <el-option v-for="d in devTypeOptions" :key="d" :label="d" :value="d" />
            </el-select>
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

    <!-- 【2026-11-09 用户令"只把含有关键字的记录呈现成列表"】关键字搜索=纯记录列表(打散客户分组) -->
    <template v-if="isKeywordSearch">
      <div v-loading="loading" element-loading-text="加载中..." class="flat-rec-list">
        <div v-if="!loading && flatRecords.length===0" style="text-align:center;padding:80px 0;color:#bbb">暂无数据</div>
        <div v-for="r in pagedFlatRecords" :key="r.id" class="flat-rec-row" @click="openRecordDetail(r)">
          <div class="flat-rec-main">
            <span class="flat-rec-cust">{{ r.customerName }}</span>
            <span class="flat-rec-cat">{{ r.categoryName }}</span>
            <span class="flat-rec-prod">{{ [r.specificProduct, r.relatedProducts].filter(Boolean).join(' · ') || '—' }}</span>
          </div>
          <div class="flat-rec-meta">
            <span class="flat-rec-date">{{ (r.createdAt || '').substring(0, 10) }}</span>
            <span class="flat-rec-user">{{ r.userName }}</span>
            <span class="flat-rec-pts" :style="{color: scoreColor(getCls(catMap, r))}">+{{ r.score }}</span>
            <el-tag v-if="r.unread" size="small" type="warning">未读</el-tag>
          </div>
        </div>
      </div>
      <div v-if="flatRecords.length > pageSize" class="search-pager">
        <span class="search-pager-count">共 <b>{{ flatRecords.length }}</b> 条记录</span>
        <el-pagination v-model:current-page="pageNum" :page-size="pageSize" :total="flatRecords.length" :page-sizes="pageSizes(pageSize)" layout="sizes, prev, pager, next, jumper" size="small" background @size-change="onSizeChange" />
      </div>
    </template>

    <!-- 非搜索态=原客户卡片网格 -->
    <template v-else>
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
              <!-- 【文件阅读器 2026-10-16】文档面卡从直接下载改为打开灯箱内嵌预览（Word/Excel/PDF/文本），灯箱内保留下载 -->
              <div v-else class="img-doc" @click="preview(f.id, f.type, f.label)">
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
    </template>

    <!-- 灯箱预览（图片/视频/文档）与客户详情弹窗（三联拨盘时间轴） -->
    <SearchLightbox ref="lightbox" :thumb-failed="thumbFailed" @thumb-failed="markThumbFailed" />
    <SearchDetailDialog v-model="detailVisible" :cust="detailCust" :cat-map="catMap" :thumb-failed="thumbFailed"
      @preview="preview" @thumb-failed="markThumbFailed" />
    <!-- 【2026-11-10 用户令】检索命中只看该条上传记录, 不再端出客户拨盘; 需要上下文走底部小入口 -->
    <RecordDetailDialog v-model="recDetailVisible" :record="recDetailRecord" @open-customer="onRecOpenCustomer" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getCategoryTree } from '@/api/incentive'
import { getDictData } from '@/api/system'
import { isCurrentMonth, sortByScore } from '@/utils/incentive-read'
import { cachedGet } from '@/utils/layout-cache'
import { Search, User, VideoPlay, Document, Filter, Folder, OfficeBuilding, PriceTag, Calendar, MapLocation, Picture } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { paginate, pageSizes } from '@/utils/pagination'
// 【D10 2026-10-04】五大类产品目录改共享常量（原与 useIncentiveUpload.js 各硬编码一份）
// 【2026-10-29 归树批】产品目录权威来源=分类树"产品试点"分支(系统设置→激励类别管理),
// loadCats 里经 buildProductCatsFromTree 派生覆盖; 常量为树缺行/接口失败兜底
import { PRODUCT_CATS, PRODUCT_CAT_KEYS, buildProductCatsFromTree } from './product-catalog'
// 【D9 2026-10-04】文件/分类纯工具拆至共享模块；灯箱与详情弹窗拆为子组件
import { thumbUrl, getFiles, getMain, getCls, scoreColor } from './search-helpers'
import SearchLightbox from './SearchLightbox.vue'
import SearchDetailDialog from './SearchDetailDialog.vue'
import RecordDetailDialog from './RecordDetailDialog.vue'

const fCustomer=ref(''),fSales=ref(''),fIndustry=ref(''),fDevType=ref(''),fDateRange=ref(null),fOrgIds=ref([]),showFilter=ref(false)
// 【2026-10-28 两级分类】一级=大类 id(发后端 mainCategoryId), 二级=子类名(客户端按记录 categoryName 过滤)
const fMainCat=ref(null),fSubCat=ref('')
// 【2026-10-29 归树批】产品大类/小类选项: 树派生优先, 常量兜底
const productCats=ref(PRODUCT_CATS)
const productCatKeys=ref(PRODUCT_CAT_KEYS)
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

// 【2026-11-09 用户令"只把含有关键字的记录呈现成列表, 不要呈现客户的卡片"】
// 关键字搜索激活时: 打散客户分组→纯记录列表(每行=一条记录, 附客户名内联展示), 不再按客户卡片渲染
const isKeywordSearch = computed(() => !!(fCustomer.value || '').trim())
const flatRecords = computed(() => {
  if (!isKeywordSearch.value) return []
  const rows = []
  for (const c of custs.value) {
    for (const r of (c.records || [])) {
      rows.push({ ...r, customerName: c.customerName, customerId: c.customerId, industry: c.industry })
    }
  }
  return rows
})
const pagedFlatRecords = computed(() => paginate(flatRecords.value, pageNum.value, pageSize.value))
const detailVisible=ref(false),detailCust=ref(null)
// 【2026-11-10 用户令】检索命中=单记录详情(客户拨盘不再随检索触达)
const recDetailVisible=ref(false),recDetailRecord=ref(null)
function openRecordDetail(r){ recDetailRecord.value=r; recDetailVisible.value=true }
// 单记录详情→客户全部记录(拨盘)的上下文小入口
function onRecOpenCustomer(r){ const c = custs.find(x=>x.customerId===r.customerId); if (c) showDetail(c) }
// 【2026-10-29 字典接线】行业选项改调系统字典(industry); 兜底=当前字典全量(含「新行业」, 原硬编码
// 缺该项致新行业客户筛不出)。改字典后此兜底需人工同步(门禁 check-dict-source-contract.sh 盯调用不删)。
const INDUSTRY_FALLBACK=['冶金','煤矿','化工','电力','有色金属','机械','工程公司','维修厂','其他终端客户','新行业']
const industries=ref([])
async function loadIndustries(){
  try{
    const res=await getDictData('industry')
    const list=(res.data||[]).filter(d=>d&&d.status!==0).sort((a,b)=>(a.sortOrder??0)-(b.sortOrder??0)).map(d=>d.dictValue).filter(Boolean)
    if(!list.length) throw new Error('empty dict')
    industries.value=list
  }catch(e){ industries.value=[...INDUSTRY_FALLBACK] }
}
// 一级分类选项=分类树顶层（getCategoryTree 返回嵌套树, 顶层数组即大类, loadCats 里灌入）
const mainCats=ref([])
const subCats=computed(()=>{
  const m=mainCats.value.find(c=>c.id===fMainCat.value)
  return ((m&&m.children)||[]).map(c=>c.categoryName)
})
function onMainCatChange(){ fSubCat.value=''; doSearch() }
// 开发类型选项=系统字典 dev_type（与客户表单同源同兜底口径）
const DEV_TYPE_FALLBACK=['成熟创新','突破开拓','铺垫宣传','新建改建扩建大修']
const devTypeOptions=ref([])
async function loadDevTypes(){
  try{
    const res=await getDictData('dev_type')
    devTypeOptions.value=(res.data||[]).map(d=>d.dictValue)
  }catch(e){ devTypeOptions.value=[...DEV_TYPE_FALLBACK] }
}
const userList=ref([]),orgTree=ref([])
const activeFilterCount=computed(()=>[fSales.value,fIndustry.value,fMainCat.value,fSubCat.value,fDevType.value,fDateRange.value,fOrgIds.value?.length>0].filter(v=>!!v).length)

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
  if(fMainCat.value)p.mainCategoryId=fMainCat.value
  if(fDevType.value)p.devType=fDevType.value
  if(fDateRange.value?.length===2){p.startDate=fDateRange.value[0];p.endDate=fDateRange.value[1]}
  if(fOrgIds.value?.length){p.orgIds=fOrgIds.value.join(',')}
  if(fProdSub.value)p.product=fProdSub.value
  // 【2026-11-01 审计修复】只选大类也发参(后端 product=LIKE, 大类名可前缀命中 大类-小类)
  else if(fProdCat.value)p.product=fProdCat.value
  const seq = ++searchSeq
  // 【2026-08-08 首次加载体验】加 loading 态，避免首次并发请求时误显示"暂无数据"
  loading.value = true
  // 【P2-6 2026-10-19】scoreOrder 不发后端——接口不收该参数(排序由下方 sortByScore 客户端完成),
  // 死参数会让"分值排序"看似服务端语义, 误导排障
  try{
    const res=await request.get('/incentive/customer-records',{params:p})
    if (seq !== searchSeq) return   // 已有更新的请求在途/完成，丢弃本次旧响应
    const rows0=(res.data||[]).filter(c=>c.records?.length>0&&c.customerName)
    // 二级分类(子类名)客户端过滤: 客户命中=有该子类记录, 卡片记录集与 totalScore 随过滤结果走
    // (与小程序轻量页"大类·子类→按子记录分类名匹配"同语义; 一级大类已由 mainCategoryId 在服务端收窄)
    let rows=rows0
    if(fSubCat.value){
      rows=rows0.map(c=>({...c,records:c.records.filter(r=>r.categoryName===fSubCat.value)}))
        .filter(c=>c.records.length>0)
      rows.forEach(c=>{c.totalScore=c.records.reduce((s,r)=>s+(r.score||0),0)})
    }
    rows.forEach(c=>{c.isNew=(c.records||[]).some(r=>isCurrentMonth(r.createdAt));c.unread=(c.records||[]).some(r=>r.unread)})
    custs.value=sortByScore(rows,scoreOrder.value)
  }catch(e){
    // 【D13 2026-10-04】失败由 request 拦截器统一提示，本地不再叠加笼统"查询失败"；保留旧数据
  }finally{
    if (seq === searchSeq) loading.value = false
  }
}
function doReset(){fCustomer.value='';fSales.value='';fIndustry.value='';fMainCat.value=null;fSubCat.value='';fDevType.value='';fDateRange.value=null;fOrgIds.value=[];fProdCat.value='';fProdSub.value='';scoreOrder.value='';doSearch()}
async function loadCats(){try{const res=await cachedGet('categoryTree',getCategoryTree);const tree=res.data||[];mainCats.value=tree;const walk=l=>{l.forEach(c=>{catMap.value[c.id]=c;if(c.children)walk(c.children)})};walk(tree);const derived=buildProductCatsFromTree(tree);if(derived){productCats.value=derived.cats;productCatKeys.value=derived.keys}}catch(e){}}
async function loadUsers(){try{const res=await cachedGet('incentiveUsers',()=>request.get('/incentive/users'));userList.value=res.data||[]}catch(e){}}
async function loadOrgs(){try{const res=await cachedGet('depts',()=>request.get('/system/depts'));const list=res.data||[]
  const build=(list,pId=0)=>list.filter(d=>Number(d.parentId)===Number(pId)).sort((a,b)=>(a.sortOrder||0)-(b.sortOrder||0)).map(d=>{const ch=build(list,d.id);return ch.length?{...d,children:ch}:{...d}})
  const filtered=list.filter(d=>d.deptType!==1) // 跳过总部级
  const rootIds=new Set(filtered.map(d=>d.id))
  const fixed=filtered.map(d=>({...d,parentId:rootIds.has(d.parentId)?d.parentId:0}))
  orgTree.value=build(fixed)}catch(e){}}

onMounted(()=>{loadCats();loadDevTypes();loadIndustries();loadUsers();loadOrgs();doSearch()})
</script>

<style scoped>
/* ===== 页面骨架（白色整体容器由全局 .page-shell 提供）===== */
.isc-page{display:flex;flex-direction:column;min-height:calc(100vh - 140px)}

/* 【2026-11-09 用户令"关键字搜索=纯记录列表"】打散客户分组的扁平记录行 */
.flat-rec-list{flex:1;padding:14px 20px 20px;display:flex;flex-direction:column;gap:2px;min-height:200px}
.flat-rec-row{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:10px 14px;
  border-bottom:1px solid var(--el-border-color-extra-light);cursor:pointer;transition:background .12s}
.flat-rec-row:hover{background:var(--el-fill-color-light)}
.flat-rec-main{display:flex;align-items:baseline;gap:12px;flex:1;min-width:0}
.flat-rec-cust{font-weight:600;font-size:14px;color:var(--el-text-color-primary);flex-shrink:0;max-width:180px;
  overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.flat-rec-cat{font-size:12px;color:var(--el-color-primary);background:var(--el-color-primary-light-9);
  padding:2px 8px;border-radius:3px;flex-shrink:0}
.flat-rec-prod{font-size:13px;color:var(--el-text-color-regular);overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.flat-rec-meta{display:flex;align-items:center;gap:10px;flex-shrink:0;font-size:12px;color:var(--el-text-color-secondary)}
.flat-rec-date{min-width:72px;text-align:right}
.flat-rec-user{min-width:48px}
.flat-rec-pts{font-weight:600;font-size:14px;min-width:32px;text-align:right}

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
