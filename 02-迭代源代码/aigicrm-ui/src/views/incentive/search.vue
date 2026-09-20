<template>
  <div style="margin:-20px">
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

    <div v-loading="loading" element-loading-text="加载中..." style="display:grid;grid-template-columns:repeat(4,1fr);gap:16px;padding:20px;min-height:200px">
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
            <span class="rec-tag" :class="'rec-tag-'+getCls(c.records[0])">{{ getMain(c.records[0]) }}</span>
            <span class="card-sub">{{ c.records[0].categoryName }}</span>
            <span class="card-info">{{ c.records[0].period }} · {{ c.records[0].userName }}</span>
            <span class="card-pts" :style="{color:scoreColor(getCls(c.records[0]))}">+{{ c.records[0].score }}</span>
          </div>
          <div v-if="getFiles(c.records[0]).length" class="card-imgs">
            <div v-for="f in getFiles(c.records[0]).slice(0,4)" :key="f.id" class="card-img">
              <template v-if="f.type==='image'">
                <div class="img-wrap">
                  <img :src="thumbUrl(f.id)" @click="preview(f.id, f.type, f.label)" @error="onImgError" />
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
    <div v-if="custs.length > pageSize" class="search-pager">
      <span class="search-pager-count">共 <b>{{ custs.length }}</b> 个客户</span>
      <el-pagination v-model:current-page="pageNum" :page-size="pageSize" :total="custs.length" :page-sizes="pageSizes(pageSize)" layout="sizes, prev, pager, next, jumper" size="small" background @size-change="onSizeChange" />
    </div>

    <!-- 图片/视频灯箱预览：深色底 + 顶部信息条 + 左右切换 + 缩略图条 + 键盘方向键 -->
    <el-dialog v-model="previewVisible" width="1000px" top="3vh" :close-on-click-modal="true" destroy-on-close append-to-body class="preview-dlg">
      <template #header></template>
      <div class="pv-wrap" v-if="previewCurrent">
        <div class="pv-bar">
          <span class="pv-name">{{ previewName || '文件预览' }}</span>
          <span v-if="previewCurrent.tag" class="pv-tag">{{ previewCurrent.tag }}</span>
          <span v-if="previewCount > 1" class="pv-count">{{ previewIndex + 1 }} / {{ previewCount }}</span>
          <span class="pv-spacer"></span>
          <el-button size="small" @click="downloadPreview"><el-icon><Download /></el-icon> 下载</el-button>
          <el-button size="small" circle @click="previewVisible=false"><el-icon><Close /></el-icon></el-button>
        </div>
        <div class="pv-stage">
          <button v-if="previewCount > 1" class="pv-nav prev" @click="previewStep(-1)"><el-icon><ArrowLeft /></el-icon></button>
          <video v-if="previewType==='video'" :src="previewUrl" controls autoplay class="pv-media" />
          <img v-else-if="!previewError" :src="previewUrl" class="pv-media" @error="onPreviewError" />
          <div v-else class="pv-missing">
            <el-icon :size="34"><WarningFilled /></el-icon>
            <div class="pv-missing-title">该文件当前不可用</div>
            <div class="pv-missing-hint">文件可能已被清理，或本环境的存储中还没有这个文件</div>
            <el-button size="small" type="primary" plain @click="downloadPreview">仍要下载试试</el-button>
          </div>
          <button v-if="previewCount > 1" class="pv-nav next" @click="previewStep(1)"><el-icon><ArrowRight /></el-icon></button>
        </div>
        <div v-if="previewCount > 1" class="pv-thumbs">
          <div v-for="(f,i) in previewList" :key="f.id" class="pv-thumb" :class="{active:i===previewIndex}" @click="previewIndex=i; previewError=false">
            <img v-if="f.type!=='video' && !thumbFailed.has(f.id)" :src="thumbUrl(f.id)" @error="markThumbFailed(f.id)" />
            <span v-else-if="thumbFailed.has(f.id)" class="pv-thumb-na">不可用</span>
            <div v-else class="pv-thumb-vid"><el-icon :size="18"><VideoPlay /></el-icon></div>
            <span v-if="f.tag" class="pv-thumb-tag">{{ f.tag }}</span>
          </div>
        </div>
      </div>
    </el-dialog>

    <el-dialog v-model="detailVisible" width="1080px" top="4vh" :close-on-click-modal="true" destroy-on-close class="detail-dlg">
      <template #header></template>
      <div class="dtl-wrap" v-if="detailCust">
        <!-- 吸顶操作条：长明细滚动时仍能看到客户与返回入口 -->
        <div class="dtl-bar">
          <span class="dtl-back" @click="detailVisible=false">← 返回列表</span>
          <span class="dtl-bar-name">{{ detailCust.customerName }}</span>
          <span class="dtl-bar-meta">共 {{ recordTotal }} 条激励记录</span>
          <span class="dtl-bar-pts"><b>{{ detailCust.totalScore }}</b> 分</span>
        </div>

        <!-- 客户概览：头像 + 名称/标签 + 三项指标 -->
        <div class="dtl-hero">
          <div class="dtl-avatar">{{ (detailCust.customerName || '?').charAt(0) }}</div>
          <div class="dtl-hero-main">
            <div class="dtl-hero-name">{{ detailCust.customerName }}</div>
            <div class="dtl-hero-tags">
              <span v-if="detailCust.industry" class="dtl-pill blue">{{ detailCust.industry }}</span>
              <span class="dtl-pill grey">最近更新 {{ lastUpdateText }}</span>
            </div>
          </div>
          <div class="dtl-metrics">
            <div class="dtl-metric">
              <div class="dtl-metric-num primary">{{ detailCust.totalScore }}</div>
              <div class="dtl-metric-label">累计积分</div>
            </div>
            <div class="dtl-metric">
              <div class="dtl-metric-num">{{ recordTotal }}</div>
              <div class="dtl-metric-label">激励条数</div>
            </div>
            <div class="dtl-metric">
              <div class="dtl-metric-num">{{ recordsByMonth.length }}</div>
              <div class="dtl-metric-label">覆盖月份</div>
            </div>
          </div>
        </div>

        <!-- 时间轴工具条：记录多了以后的关键检索入口（2026-09-20）——
             关键字搜索(分类/产品/上传人/描述/备注/附件名) + 时间正序(按时间增量)/倒序切换 + 命中计数 -->
        <div class="dtl-toolbar">
          <el-input
            v-model="detailKeyword" size="small" clearable
            placeholder="搜索：分类 / 产品 / 上传人 / 描述 / 备注 / 附件名"
            :prefix-icon="Search" class="dtl-toolbar-search"
          />
          <el-radio-group v-model="detailOrder" size="small">
            <el-radio-button value="desc">最新在前</el-radio-button>
            <el-radio-button value="asc">按时间增量</el-radio-button>
          </el-radio-group>
          <span class="dtl-toolbar-hit">
            {{ detailKeyword ? `命中 ${filteredRecords.length} / ${recordTotal} 条` : `${recordTotal} 条` }}
          </span>
          <el-button v-if="detailKeyword" link size="small" @click="detailKeyword=''">清除</el-button>
        </div>

        <div class="dtl-section">
          <span class="dtl-section-title">激励记录</span>
          <span class="dtl-section-count">{{ filteredRecords.length }} 条</span>
        </div>
        <div v-if="recordsByMonth.length" class="dtl-timeline">
          <div v-for="group in recordsByMonth" :key="group.key" class="dtl-month">
            <div class="dtl-month-head">
              <span class="dtl-month-label">{{ group.label }}</span>
              <span class="dtl-month-count">{{ group.count }} 条</span>
            </div>
            <div v-for="r in group.items" :key="r.id" class="dtl-item">
              <div class="dtl-head">
                <span class="dtl-date">{{ fmtDt(r.createdAt) }}</span>
                <span v-if="relativeDay(r.createdAt)" class="dtl-ago">{{ relativeDay(r.createdAt) }}</span>
                <span class="rec-tag" :class="'rec-tag-'+getCls(r)">{{ getMain(r) }}</span>
                <span class="dtl-subcat">{{ r.categoryName }}</span>
                <span class="dtl-score" :style="{ color: scoreColor(getCls(r)), background: scoreColor(getCls(r)) + '14', borderColor: scoreColor(getCls(r)) + '33' }">+{{ r.score }} 分</span>
              </div>
              <div class="dtl-content">
                <div class="dtl-upload-chips">
                  <span class="dtl-chip cust"><i>客户</i><b :style="{color:scoreColor(getCls(r))}">{{ r.customerName || detailCust.customerName }}</b></span>
                  <span class="dtl-chip" v-if="r.industry"><i>行业</i>{{ r.industry }}</span>
                  <span class="dtl-chip" v-if="r.relatedProducts"><i>相关产品</i>{{ r.relatedProducts }}</span>
                  <span class="dtl-chip" v-if="r.specificProduct"><i>具体产品</i>{{ r.specificProduct }}</span>
                  <span class="dtl-chip"><i>上传人</i>{{ r.userName }}</span>
                  <span class="dtl-chip" v-if="r.period"><i>周期</i>{{ r.period }}</span>
                </div>
                <div v-if="r.preInstallQuestion" class="dtl-field desc">
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
                        <img :src="thumbUrl(f.id)" @click="preview(f.id, f.type, f.label, previewableOf(r))" @error="onImgError" />
                        <div v-if="f.tag" class="dtl-file-tag" :style="{color:tagColor(f.tag), borderColor:tagColor(f.tag)}">{{ f.tag }}</div>
                        <div class="dtl-file-acts">
                          <el-button size="small" circle type="primary" @click.stop="preview(f.id, f.type, f.label, previewableOf(r))"><el-icon><ZoomIn /></el-icon></el-button>
                          <el-button size="small" circle @click.stop="downloadFile(f.id, f.label)"><el-icon><Download /></el-icon></el-button>
                        </div>
                      </div>
                    </template>
                    <div v-else-if="f.type==='video'" class="dtl-file-vid">
                      <video :src="fileUrl(f.id)" controls preload="metadata"></video>
                      <div class="dtl-file-meta">
                        <span v-if="f.tag" class="dtl-file-tag inline" :style="{color:tagColor(f.tag), borderColor:tagColor(f.tag)}">{{ f.tag }}</span>
                        <span class="doc-label">{{ f.label }}</span>
                        <el-button size="small" circle @click.stop="preview(f.id, f.type, f.label, previewableOf(r))"><el-icon><ZoomIn /></el-icon></el-button>
                        <el-button size="small" circle @click.stop="downloadFile(f.id, f.label)"><el-icon><Download /></el-icon></el-button>
                      </div>
                    </div>
                    <div v-else class="dtl-file-doc" @click="downloadFile(f.id, f.label)">
                      <span v-if="f.tag" class="dtl-file-tag inline" :style="{color:tagColor(f.tag), borderColor:tagColor(f.tag)}">{{ f.tag }}</span>
                      <el-icon :size="26"><Document /></el-icon>
                      <span class="doc-label">{{ f.label }}</span>
                      <el-button size="small" circle @click.stop="downloadFile(f.id, f.label)"><el-icon><Download /></el-icon></el-button>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
        <el-empty v-else :description="detailKeyword ? '没有匹配「' + detailKeyword + '」的记录' : '该客户暂无激励记录'" :image-size="90">
          <el-button v-if="detailKeyword" size="small" @click="detailKeyword=''">清除搜索</el-button>
        </el-empty>

        <!-- 底部操作条 -->
        <div class="dtl-footer">
          <span class="dtl-footer-hint">共 {{ recordTotal }} 条记录 · {{ recordsByMonth.length }} 个月份</span>
          <el-button size="small" @click="detailVisible=false">关闭</el-button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { getActivePinia } from 'pinia'
import { getCategoryTree, markIncentiveRead } from '@/api/incentive'
import { isCurrentMonth, sortByScore } from '@/utils/incentive-read'
import { cachedGet } from '@/utils/layout-cache'
import { getToken, getDownloadToken, refreshDownloadToken } from '@/utils/token'
import { downloadFile as downloadFileUtil } from '@/utils/download'
import { Search, User, VideoPlay, Document, ZoomIn, Download, Filter, Folder, OfficeBuilding, PriceTag, Calendar, MapLocation, Notebook, Warning, Close, ArrowLeft, ArrowRight } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { ElMessage } from 'element-plus'
import { paginate, pageSizes } from '@/utils/pagination'
import { groupRecordsByMonth, filterRecordsByKeyword, relativeDay } from '@/utils/time-label'
import { useUserStore } from '@/store/user'

const fCustomer=ref(''),fSales=ref(''),fIndustry=ref(''),fDevType=ref(''),fDateRange=ref(null),fOrgIds=ref([]),showFilter=ref(false)
const fProdCat=ref(''),fProdSub=ref('')
const scoreOrder=ref('')
const productCats={ '涂层类':['防腐','加固','钢结构','煤气管道','其他'], '防滑板类':['公共区','装置区'], '机封类':['水泵','渣泵','介质泵'], '密封类':['静密封','动密封','橡胶密封'], '润滑类':['润滑油','润滑脂','润滑剂'] }
const productCatKeys=Object.keys(productCats)
const custs=ref([]),catMap=ref({})
// 图片/视频灯箱：一次打开记录内的全部可预览文件，支持左右切换、缩略图条与键盘方向键
const previewVisible=ref(false),previewList=ref([]),previewIndex=ref(0),previewError=ref(false),thumbFailed=ref(new Set())
const previewCurrent=computed(()=>previewList.value[previewIndex.value]||null)
const previewCount=computed(()=>previewList.value.length)
const previewUrl=computed(()=>previewCurrent.value?fileUrl(previewCurrent.value.id):'')
const previewType=computed(()=>previewCurrent.value&&previewCurrent.value.type==='video'?'video':'image')
const previewName=computed(()=>previewCurrent.value?.label||'')
const loading=ref(false)
// 客户卡片网格分页（前端切片）
const pageNum = ref(1)
const pageSize = ref(8)

// 切换每页条数后回到第 1 页（避免停在已不存在的页码看到空网格）
const onSizeChange = (size) => { pageSize.value = size; pageNum.value = 1 }
const pagedCusts = computed(() => paginate(custs.value, pageNum.value, pageSize.value))
const detailVisible=ref(false),detailCust=ref(null)
// 详情弹窗概览用：取该客户最新一条记录的时间（无记录时显示 —）
const lastUpdateText = computed(() => { const r = latestRecord(detailCust.value); return r ? fmtDt(r.createdAt) : '—' })
const industries=['冶金','煤矿','化工','电力','有色金属','机械','工程公司','维修厂','其他终端客户']
const devTypes=['成熟创新','突破开拓','铺垫宣传']
const userList=ref([]),orgTree=ref([])
const userStore=getActivePinia() ? useUserStore() : null
const activeFilterCount=computed(()=>[fSales.value,fIndustry.value,fDevType.value,fDateRange.value,fOrgIds.value?.length>0].filter(v=>!!v).length)

function onImgError(e){e.target.src='data:image/svg+xml,'+encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="320" height="240" viewBox="0 0 320 240"><rect fill="#f5f6f7" width="320" height="240"/><text fill="#c0c4cc" font-size="14" font-family="sans-serif" text-anchor="middle" x="160" y="120">图片加载失败</text></svg>')}
// 取客户记录中最新一条（排序已按品类，不与时间线顺序绑定）
function latestRecord(c){let best=null;for(const r of (c?.records||[])){if(!best||String(r.createdAt||'')>String(best.createdAt||''))best=r}return best}
// 【修复 2026-08-14】下载 token 未就绪时不拼 token=（避免产生必然 403 的空 token 请求）；
// token 就绪后 getDownloadToken() 读取的 dlToken ref 变化会触发渲染链重跑，<img> 自动用带 token 的 URL 重载
function fileUrl(id){const t=getDownloadToken();return t?'/api/file/download/'+id+'?token='+t:'/api/file/download/'+id}
function thumbUrl(id){const t=getDownloadToken();return t?'/api/file/download/'+id+'?token='+t+'&thumbnail=1':'/api/file/download/'+id}
// 打开灯箱：gallery 传入该记录的可预览文件（图片/视频）列表，可左右切换；不传则只看单个文件
async function preview(id,type,name,gallery){
  await refreshDownloadToken()
  const clicked={id:Number(id),type:type==='video'?'video':'image',label:name||''}
  let list=(Array.isArray(gallery)?gallery:[]).filter(f=>f&&f.id!=null).map(f=>({id:Number(f.id),type:f.type==='video'?'video':'image',label:f.label||'',tag:f.tag||''}))
  let idx=list.findIndex(f=>f.id===clicked.id)
  if(idx<0){list=[clicked];idx=0}
  previewList.value=list;previewIndex.value=idx;previewError.value=false;thumbFailed.value=new Set();previewVisible.value=true
}
// 灯箱左右切换（循环）
function previewStep(delta){const n=previewList.value.length;if(n<2)return;previewIndex.value=(previewIndex.value+delta+n)%n;previewError.value=false}
// 灯箱主图加载失败：不再只是替换成占位图，而是给出可读的失败原因 + 下载兜底
function onPreviewError(){previewError.value=true}
// 缩略图失败：标记该文件不可用（用清晰文字块替代 7px 灰字的占位图）
function markThumbFailed(id){thumbFailed.value=new Set([...thumbFailed.value,Number(id)])}
function onPreviewKeydown(e){
  if(!previewVisible.value)return
  if(e.key==='ArrowRight'){previewStep(1)}else if(e.key==='ArrowLeft'){previewStep(-1)}
}
// 记录内可预览（图片/视频）文件
function previewableOf(r){return getFiles(r).filter(f=>f.type==='image'||f.type==='video').map(f=>({id:f.id,type:f.type,label:f.label,tag:f.tag}))}
// 【需求】文件下载(FEAT 0804):window.open 下载在新标签闪开/被拦截时表现为"没反应";
// 改为 fetch blob + a.download 强制保存,任何浏览器/任何状态下可靠触发
async function downloadFile(id, name) {
  if (!id) return
  await refreshDownloadToken()
  await downloadFileUtil(fileUrl(id), { name: name || ('file-' + id), headers: { 'Authorization': 'Bearer ' + (getToken() || '') } })
}
function downloadPreview(){ const f=previewCurrent.value; if(f) downloadFile(f.id, f.label) }
async function showDetail(c){
  detailCust.value=c;detailVisible.value=true
  detailKeyword.value='';detailOrder.value='desc' // 换客户详情即复位检索与方向
  const ids=(c.records||[]).map(r=>r.id).filter(Boolean)
  if(ids.length){
    await Promise.all(ids.map(id=>markIncentiveRead(id).catch(()=>null)))
    c.unread=false
    c.records.forEach(r=>{r.unread=false})
  }
}

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
  // 【2026-08-08 首次加载体验】加 loading 态，避免首次并发请求时误显示"暂无数据"
  loading.value = true
  if(scoreOrder.value)p.scoreOrder=scoreOrder.value
  try{
    const res=await request.get('/incentive/customer-records',{params:p})
    const rows=(res.data||[]).filter(c=>c.records?.length>0&&c.customerName)
    rows.forEach(c=>{c.isNew=(c.records||[]).some(r=>isCurrentMonth(r.createdAt));c.unread=(c.records||[]).some(r=>r.unread)})
    custs.value=sortByScore(rows,scoreOrder.value)
  }catch(e){ElMessage.error('查询失败')}finally{loading.value=false}
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
// 时间轴：记录按月倒序分组（展示口径，不改动接口返回顺序）
// 【2026-09-20 详情工具条】记录检索 + 时间轴方向；打开新客户详情时自动复位
const detailKeyword = ref('')
const detailOrder = ref('desc')
const filteredRecords = computed(() => filterRecordsByMonthSafe())
const recordsByMonth=computed(()=>groupRecordsByMonth(filteredRecords.value, detailOrder.value))
const recordTotal=computed(()=>detailCust.value?.records?.length||0)
function filterRecordsByMonthSafe() {
  const source = detailCust.value?.records || []
  return filterRecordsByKeyword(source, detailKeyword.value)
}

onMounted(()=>{loadCats();loadUsers();loadOrgs();doSearch();window.addEventListener('keydown',onPreviewKeydown)})
onBeforeUnmount(()=>window.removeEventListener('keydown',onPreviewKeydown))
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

/* ===== 图片/视频灯箱预览（2026-09-13） ===== */
.preview-dlg :deep(.el-dialog){background:transparent;box-shadow:none;border-radius:16px;overflow:hidden}
.preview-dlg :deep(.el-dialog__header){display:none}
.preview-dlg :deep(.el-dialog__body){padding:0}
.pv-wrap{background:#0b1220;border-radius:16px;overflow:hidden;box-shadow:0 24px 60px rgba(0,0,0,.5)}
.pv-bar{display:flex;align-items:center;gap:10px;padding:12px 16px;background:rgba(255,255,255,.06);
  border-bottom:1px solid rgba(255,255,255,.08);color:#e5eaf3}
.pv-name{font-size:14px;font-weight:600;max-width:46%;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.pv-tag{font-size:11px;color:#9ec5ff;background:rgba(64,158,255,.18);border:1px solid rgba(64,158,255,.36);
  border-radius:999px;padding:2px 10px;white-space:nowrap}
.pv-count{font-size:12px;color:#8b97a8;font-variant-numeric:tabular-nums}
.pv-spacer{flex:1}
.pv-bar :deep(.el-button){background:rgba(255,255,255,.10);border-color:rgba(255,255,255,.16);color:#e5eaf3}
.pv-bar :deep(.el-button:hover){background:rgba(64,158,255,.28);border-color:rgba(64,158,255,.45);color:#fff}
.pv-stage{position:relative;display:flex;align-items:center;justify-content:center;min-height:420px;padding:16px}
.pv-media{max-width:100%;max-height:74vh;border-radius:10px;background:#05070d;display:block}
.pv-nav{position:absolute;top:50%;transform:translateY(-50%);width:44px;height:44px;border-radius:50%;border:none;
  background:rgba(255,255,255,.12);color:#fff;font-size:20px;display:flex;align-items:center;justify-content:center;
  cursor:pointer;transition:all .18s;backdrop-filter:blur(6px)}
.pv-nav:hover{background:rgba(64,158,255,.85);transform:translateY(-50%) scale(1.06)}
.pv-nav.prev{left:18px}
.pv-nav.next{right:18px}
.pv-thumbs{display:flex;gap:8px;padding:10px 16px 14px;overflow-x:auto;border-top:1px solid rgba(255,255,255,.08)}
.pv-thumb{position:relative;width:76px;height:56px;flex-shrink:0;border-radius:8px;overflow:hidden;cursor:pointer;
  border:2px solid transparent;background:rgba(255,255,255,.06);transition:all .15s}
.pv-thumb:hover{border-color:rgba(64,158,255,.6)}
.pv-thumb.active{border-color:#409eff;box-shadow:0 0 0 2px rgba(64,158,255,.28)}
.pv-thumb img{width:100%;height:100%;object-fit:cover;display:block}
.pv-thumb-vid{width:100%;height:100%;display:flex;align-items:center;justify-content:center;color:#cfe2ff}
.pv-thumb-na{width:100%;height:100%;display:flex;align-items:center;justify-content:center;font-size:11px;color:#aab6c8;background:rgba(255,255,255,.06)}
.pv-missing{display:flex;flex-direction:column;align-items:center;justify-content:center;gap:10px;color:#c9d4e3;padding:60px 20px;text-align:center}
.pv-missing-title{font-size:15px;font-weight:600;color:#e5eaf3}
.pv-missing-hint{font-size:12px;color:#8b97a8;max-width:420px;line-height:1.7}
.pv-thumb-tag{position:absolute;left:2px;bottom:2px;font-size:9px;color:#fff;background:rgba(0,0,0,.5);
  border-radius:4px;padding:0 4px;line-height:14px}

/* ===== 激励查询「查看详情」弹窗（2026-09-13 UI 美化） ===== */
.detail-dlg :deep(.el-dialog){border-radius:16px;overflow:hidden;box-shadow:0 18px 48px rgba(15,23,42,.18)}
.detail-dlg :deep(.el-dialog__header){display:none}
.detail-dlg :deep(.el-dialog__body){padding:0;background:#f6f8fb}
.dtl-wrap{max-width:1000px;margin:0 auto;background:#fff;display:flex;flex-direction:column;max-height:92vh}

/* 吸顶操作条 */
.dtl-bar{position:sticky;top:0;z-index:6;display:flex;align-items:center;gap:12px;padding:12px 24px;
  background:rgba(255,255,255,.92);backdrop-filter:blur(10px)}
.dtl-back{font-size:13px;color:#4e5969;cursor:pointer;display:inline-flex;align-items:center;gap:4px;
  padding:4px 10px;border-radius:8px;transition:all .15s;flex-shrink:0}
.dtl-back:hover{color:#409eff;background:#ecf5ff}
.dtl-bar-name{font-size:14px;font-weight:600;color:#1f2329;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.dtl-bar-meta{font-size:12px;color:#86909c;flex-shrink:0}
.dtl-bar-pts{margin-left:auto;font-size:12px;color:#86909c;flex-shrink:0;
  background:#f2f6ff;border-radius:999px;padding:3px 12px}
.dtl-bar-pts b{font-size:15px;color:#409eff;margin-right:2px}

/* 客户概览 */
.dtl-hero{display:flex;align-items:center;gap:16px;padding:22px 24px;
  background:linear-gradient(120deg,#eef5ff 0%,#f7fafe 44%,#ffffff 100%)}
.dtl-avatar{width:54px;height:54px;border-radius:16px;flex-shrink:0;color:#fff;font-size:22px;font-weight:700;
  display:flex;align-items:center;justify-content:center;background:linear-gradient(135deg,#409eff,#2563eb);
  box-shadow:0 6px 16px rgba(64,158,255,.32)}
.dtl-hero-main{flex:1;min-width:0}
.dtl-hero-name{font-size:20px;font-weight:700;color:#1f2329;line-height:1.35;margin-bottom:8px;
  overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.dtl-hero-tags{display:flex;align-items:center;gap:8px;flex-wrap:wrap}
.dtl-pill{font-size:12px;border-radius:999px;padding:3px 12px;border:none;white-space:nowrap}
.dtl-pill.blue{color:#1d6fd0;background:#e8f2ff;border-color:transparent}
.dtl-pill.green{color:#0d7a4a;background:#e7f8ef;border-color:transparent}
.dtl-pill.grey{color:#6b7280;background:#f4f5f7;border-color:transparent}
.dtl-metrics{display:flex;align-items:stretch;gap:2px;flex-shrink:0;background:#f4f8ff;
  border-radius:16px;padding:6px}
.dtl-metric{padding:8px 18px;text-align:center;min-width:88px;border-radius:12px}
.dtl-metric + .dtl-metric{}
.dtl-metric-num{font-size:24px;font-weight:800;color:#1f2329;line-height:1.1;font-variant-numeric:tabular-nums}
.dtl-metric-num.primary{color:#409eff}
.dtl-metric-label{font-size:12px;color:#86909c;margin-top:4px}

/* 区块标题 */
.dtl-section{display:flex;align-items:baseline;gap:8px;padding:18px 24px 0}
/* 【2026-09-20】详情工具条：搜索 + 时间方向切换（吸顶条下方常驻，不随滚动消失） */
.dtl-toolbar{display:flex;align-items:center;gap:10px;padding:12px 24px 0;flex-wrap:wrap}
.dtl-toolbar-search{width:300px}
.dtl-toolbar-hit{font-size:12px;color:#86909c;white-space:nowrap}
.dtl-section-title{font-size:15px;font-weight:700;color:#1f2329;letter-spacing:.5px}
.dtl-section-count{font-size:12px;color:#86909c}

/* 时间轴 */
.dtl-timeline{position:relative;padding:12px 24px 4px;overflow-y:auto;flex:1;min-height:0}
.dtl-month{position:relative;padding-left:0;margin-bottom:4px}
.dtl-month-head{display:flex;align-items:center;gap:10px;padding:14px 0 12px}
.dtl-month-label{font-size:14px;font-weight:700;color:#1f2329;background:#f4f6fa;border-radius:10px;padding:4px 12px}
.dtl-month-count{font-size:12px;color:#86909c}
.dtl-item{position:relative;padding-left:0;margin-bottom:18px}
.dtl-item:last-child{margin-bottom:6px}
.dtl-head{display:flex;align-items:center;gap:8px;margin-bottom:8px;flex-wrap:wrap}
.dtl-date{font-size:15px;font-weight:700;color:#1f2329;font-variant-numeric:tabular-nums}
.dtl-ago{font-size:12px;color:#86909c;background:#f4f5f7;border-radius:999px;padding:2px 8px}
.dtl-subcat{font-size:13px;color:#86909c}
.dtl-score{margin-left:auto;font-size:13px;font-weight:700;border-radius:999px;padding:2px 12px;border:none}

/* 记录内容卡 */
.dtl-content{padding:16px 18px;border:none;border-radius:14px;
  background:#f8f9fc;box-shadow:none;transition:background .2s,box-shadow .2s,transform .2s}
.dtl-item:hover .dtl-content{background:#fff;box-shadow:0 8px 24px rgba(15,23,42,.07);transform:translateY(-1px)}
.dtl-upload-chips{display:flex;flex-wrap:wrap;gap:8px;margin-bottom:12px}
.dtl-chip{display:inline-flex;align-items:center;gap:6px;font-size:13px;color:#1f2329;
  background:#eef1f6;border-radius:8px;padding:4px 10px}
.dtl-chip i{font-style:normal;font-size:12px;color:#9aa0a8}
.dtl-chip.cust{background:#e8f2ff}
.dtl-chip.cust b{font-weight:700}
.dtl-field{margin-bottom:12px;padding:12px 14px;background:#f2f4f8;border-radius:12px}
.dtl-field.desc{background:#f0f5ff;border-radius:12px;padding:14px 16px}
.dtl-field.desc .dtl-field-label{color:#4e5969;font-size:13px;font-weight:600}
.dtl-field.remark{background:#fdf6ec}
.dtl-field.remark .dtl-field-label{color:#b45309}
.dtl-field-label{font-size:12px;color:#8f959e;margin-bottom:6px;letter-spacing:.5px}
.dtl-field-text{font-size:14px;color:#1f2329;line-height:1.8;white-space:pre-wrap;word-break:break-word}

/* 底部翻页区：计数在左、分页器在右，窄屏自动换行（与 reminder 的 .rem-pager 同口径）
   2026-09-13 修复：本轮给分页器补了「每页条数」下拉与跳页输入（layout 变宽）时，
   误删了 .search-pager / .search-pager-count 两条样式 ⇒ 容器退化成 block、无内边距，
   计数文案与分页器塌成两行并贴边。这里按现有口径补回并加上换行保护。 */
.search-pager{display:flex;align-items:center;flex-wrap:wrap;gap:12px;padding:8px 20px 16px}
.search-pager-count{font-size:13px;color:#606266;white-space:nowrap;margin-right:auto}
.search-pager-count b{color:#409eff;font-weight:700;margin:0 2px}
.search-pager .el-pagination{flex-shrink:0}

/* 附件网格 */
.dtl-files{display:grid;grid-template-columns:repeat(auto-fill,minmax(200px,1fr));gap:12px;margin-top:4px}
.dtl-file{min-width:0}
.dtl-file-meta{display:flex;align-items:center;gap:8px;margin-top:6px}
.dtl-img-wrap{position:relative;border-radius:14px;overflow:hidden;background:#f2f4f8}
.dtl-file img{width:100%;aspect-ratio:4/3;object-fit:cover;cursor:pointer;display:block;transition:transform .25s}
.dtl-file img:hover{transform:scale(1.03)}
.dtl-file-tag{position:absolute;top:8px;left:8px;font-size:11px;font-weight:600;background:rgba(255,255,255,.94);
  padding:2px 8px;border-radius:999px;border:1px solid transparent;white-space:nowrap;z-index:1}
.dtl-file-tag.inline{position:static;background:#fff;flex-shrink:0}
.dtl-file-vid{display:flex;flex-direction:column}
.dtl-file-vid video{width:100%;aspect-ratio:4/3;border-radius:12px;background:#0b1020;display:block;object-fit:cover}
.dtl-file-doc{position:relative;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:8px;
  aspect-ratio:4/3;padding:12px;border:none;border-radius:14px;background:#f2f4f8;
  color:#646a73;font-size:13px;text-align:center;cursor:pointer;transition:all .15s}
.dtl-file-doc:hover{background:#e8f2ff;color:#1d6fd0}
.dtl-file-doc .dtl-file-tag.inline{position:absolute;top:8px;left:8px}
.doc-label{color:#1f2329;font-weight:500;font-size:13px;flex:1;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.dtl-file-acts{position:absolute;top:8px;right:8px;display:flex;gap:6px;opacity:0;transition:opacity .2s;z-index:2}
.dtl-img-wrap:hover .dtl-file-acts{opacity:1}

/* 底部操作条 */
.dtl-footer{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:12px 24px;
  background:rgba(255,255,255,.92);backdrop-filter:blur(10px);position:sticky;bottom:0}
.dtl-footer-hint{font-size:12px;color:#86909c}
</style>
