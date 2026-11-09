<template>
  <!-- 【2026-11-05 用户令"系统设置骨架重新设计"】内层导航弃 el-tabs 改横向 chip 条:
       config 外层曾对 el-tabs 写 :deep 全量样式会劫持嵌套页签(横排页签被塞窄列折行截断),
       自定义 chip 无此劫持面; 四 pane 内容单份, visited-once+v-show 惰性挂载保持原 lazy 语义。
       (embedded 仅为兼容入参保留, 全系统只有 系统设置 一处消费; 保存即生效口径常驻顶行) -->
  <div class="perm-center">
    <p class="pc-context">全系统权限唯一配置入口 · 保存即生效（用户刷新页面即可见）</p>
    <div class="pc-chipbar">
      <button class="pc-chip" :class="{ active: activePane === 'scope' }" @click="activePane = 'scope'">功能与范围</button>
      <button class="pc-chip" :class="{ active: activePane === 'special' }" @click="activePane = 'special'">专项权限</button>
      <button class="pc-chip" :class="{ active: activePane === 'preview' }" @click="activePane = 'preview'">生效预览</button>
      <button class="pc-chip" :class="{ active: activePane === 'audit' }" @click="activePane = 'audit'">变更审计</button>
    </div>
    <div class="pc-panebody">

        <div v-show="activePane === 'scope'"><PermScopeManager /></div>
      <div v-if="paneVisited.has('special')" v-show="activePane === 'special'">

        <div class="pc-sec">
          <div class="pc-sec-head"><span class="pc-sec-title">公海池名单</span><span class="pc-sec-hint">查看 / 编辑 / 删除 / 导入 / 捞取 · 名单可勾部门/人员/角色（勾角色=成员自动获得，换届免改）</span></div>
          <SeaConfigManager />
        </div>
        <div class="pc-sec" style="margin-top:20px">
          <div class="pc-sec-head">
            <span class="pc-sec-title">激励专项授权</span>
            <span class="pc-sec-hint">查看分享 / 查看全部 / 编辑他人 / 删除他人 · 支持按角色批量（如"全员互看"= 勾三个角色）</span>
            <el-button v-if="canEdit" type="primary" size="small" style="margin-left:auto" @click="openPermDialog">添加授权</el-button>
          </div>
          <el-table :data="permList" size="small" style="width:100%">
            <el-table-column prop="realName" label="人员" min-width="140">
              <template #default="scope">
                {{ scope?.row?.realName }} <span class="pc-muted">({{ scope?.row?.username }})</span>
                <span class="pc-muted"> · {{ scope?.row?.officeName || '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="专项权限" min-width="220">
              <template #default="scope">
                <el-tag v-if="scope?.row?.canViewShared" size="small" type="info" class="pc-pill">已分享</el-tag>
                <el-tag v-if="scope?.row?.canViewAll" size="small" type="success" class="pc-pill">全部</el-tag>
                <el-tag v-if="scope?.row?.canEdit" size="small" type="warning" class="pc-pill">编辑</el-tag>
                <el-tag v-if="scope?.row?.canDelete" size="small" type="danger" class="pc-pill">删除</el-tag>
                <span v-if="!scope?.row?.canViewShared && !scope?.row?.canViewAll && !scope?.row?.canEdit && !scope?.row?.canDelete" class="pc-muted">仅本人</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="80" align="center">
              <template #default="scope">
                <el-button v-if="canEdit" link type="danger" size="small" @click="revokePerm(scope?.row?.id)">撤销</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!permList.length" description="暂无授权人员，点击「添加授权」配置" :image-size="64" />
        </div>
        <PermGrantDialog />
        <!-- 【2026-11-09 页签合并(用户令)】文件权限并入专项权限大页签: 公共/私人空间授权(人|角色×空间×动作, 实时生效) -->
        <FileSpaceDefaults :can-edit="canEdit" />
      </div>
      <div v-if="paneVisited.has('preview')" v-show="activePane === 'preview'">

        <div class="pc-preview-bar">
          <el-select v-model="previewUserId" filterable remote :remote-method="searchPreviewUsers" :loading="userSearching"
                     placeholder="输入姓名 / 账号搜索，选人后实时计算其真实权限" style="width:320px" @change="changePreviewUser" @focus="searchPreviewUsers('')">
            <el-option v-for="u in previewUserOptions" :key="u.id" :label="`${u.realName}（${u.username}）`" :value="u.id" />
          </el-select>
          <span class="pc-sec-hint">预览=与真实请求同一套引擎判定；数字为范围口径（可见客户数不含运行时规则条件）</span>
        </div>
        <div v-if="previewLoading" v-loading="true" class="pc-preview-body" style="min-height:200px" />
        <div v-else-if="preview" class="pc-preview-body">
          <div class="pc-user-card">
            <b class="pc-user-name">{{ preview.user.realName }}</b>
            <span class="pc-muted">（{{ preview.user.username }}）</span>
            <el-tag size="small" type="info" class="pc-pill">{{ (preview.user.roleCodes || []).join(' / ') || '无角色' }}</el-tag>
            <el-tag size="small" class="pc-pill">数据范围: {{ preview.user.dataScope }}</el-tag>
            <el-tag v-if="preview.user.admin" size="small" type="danger" class="pc-pill">系统管理员(全通)</el-tag>
            <el-tag size="small" type="success" class="pc-pill">功能码 {{ (preview.permissions || []).length }} 项</el-tag>
          </div>
          <el-table :data="preview.scopes" size="small" border style="width:100%">
            <el-table-column prop="label" label="权限范围" min-width="150" />
            <el-table-column prop="scope" label="生效范围" min-width="110">
              <template #default="scope"><code class="pc-code">{{ scope?.row?.scope }}</code></template>
            </el-table-column>
            <el-table-column label="锚点" width="90">
              <template #default="scope">{{ scope?.row?.anchor === 'ORG' ? '按组织' : '按人' }}</template>
            </el-table-column>
            <el-table-column label="可见人数" width="100">
              <template #default="scope">
                <span v-if="scope?.row?.visibleUserCount === -1">全部</span>
                <span v-else>{{ scope?.row?.visibleUserCount }} 人</span>
              </template>
            </el-table-column>
            <el-table-column label="可见部门" width="90">
              <template #default="scope">{{ scope?.row?.visibleDeptCount != null ? scope?.row?.visibleDeptCount + ' 个' : '—' }}</template>
            </el-table-column>
            <el-table-column label="可见客户" width="110">
              <template #default="scope">
                <b v-if="scope?.row?.visibleCustomerCount != null" class="pc-hl">{{ scope?.row?.visibleCustomerCount }} 户</b>
                <span v-else class="pc-muted">—</span>
              </template>
            </el-table-column>
          </el-table>
          <div class="pc-mini-sec">公海池判定</div>
          <div class="pc-chips">
            <span v-for="p in preview.seaPools" :key="p.poolId" class="pc-pool-chip">
              {{ p.poolName }}<template v-if="p.status !== 1">（禁用）</template>：
              <el-tag v-if="p.view" size="small" class="pc-pill">看</el-tag>
              <el-tag v-if="p.edit" size="small" type="warning" class="pc-pill">编</el-tag>
              <el-tag v-if="p.delete" size="small" type="danger" class="pc-pill">删</el-tag>
              <el-tag v-if="p.claim" size="small" type="success" class="pc-pill">捞</el-tag>
              <span v-if="!p.view && !p.edit && !p.delete && !p.claim" class="pc-muted">无权限</span>
            </span>
          </div>
          <div class="pc-mini-sec">四项服务专项授权</div>
          <div class="pc-chips">
            <el-tag :type="preview.incentive.canViewShared ? 'info' : 'danger'" size="small" class="pc-pill">查看分享: {{ preview.incentive.canViewShared ? '开' : '关' }}</el-tag>
            <el-tag :type="preview.incentive.canViewAll ? 'success' : 'info'" size="small" class="pc-pill">查看全部: {{ preview.incentive.canViewAll ? '开' : '关' }}</el-tag>
            <el-tag :type="preview.incentive.canEdit ? 'warning' : 'info'" size="small" class="pc-pill">编辑他人: {{ preview.incentive.canEdit ? '开' : '关' }}</el-tag>
            <el-tag :type="preview.incentive.canDelete ? 'danger' : 'info'" size="small" class="pc-pill">删除他人: {{ preview.incentive.canDelete ? '开' : '关' }}</el-tag>
          </div>
        </div>
        <el-empty v-else description="搜索并选择一个人，实时查看 TA 的全部真实权限" :image-size="80" />
      </div>
      <div v-if="paneVisited.has('audit')" v-show="activePane === 'audit'">

        <el-table :data="auditRecords" size="small" border style="width:100%">
          <el-table-column prop="createdAt" label="时间" width="170" />
          <el-table-column prop="ruleKey" label="配置项" min-width="240">
            <template #default="scope"><code class="pc-code">{{ scope?.row?.ruleKey }}</code></template>
          </el-table-column>
          <el-table-column prop="oldValue" label="旧值" min-width="130" />
          <el-table-column prop="newValue" label="新值" min-width="130" />
          <el-table-column prop="operatorName" label="操作人" width="110" />
        </el-table>
        <el-pagination v-if="auditTotal > auditSize" v-model:current-page="auditPage" :page-size="auditSize" :total="auditTotal"
                       layout="prev, pager, next, total" style="margin-top:12px;justify-content:flex-end" @current-change="loadAudit" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import PermScopeManager from './PermScopeManager.vue'
import SeaConfigManager from './SeaConfigManager.vue'
import PermGrantDialog from './PermGrantDialog.vue'
import FileSpaceDefaults from './FileSpaceDefaults.vue'
import { usePermissionData } from '@/composables/usePermissionData'
import { useUserStore } from '@/store/user'
import { getUserList, getPermCenterPreview, getRuleVersions } from '@/api/system'

// 【2026-11-05 用户令"权限中心收敛到系统设置里面"】embedded=true 时作为系统设置内页签挂载:
// 隐藏自带页头(避免与页签标题双重「权限中心」), 功能(四页签)零变化
defineProps({ embedded: { type: Boolean, default: false } })

const userStore = useUserStore()
const canEdit = computed(() => userStore.isAdmin() && userStore.hasPermission('system:config:edit'))

const activePane = ref('scope')
// 惰性挂载: 专项权限/生效预览/变更审计 首访才渲染(原 el-tab-pane lazy 语义), 之后保态
const paneVisited = reactive(new Set(['scope']))
watch(activePane, (v) => { if (v) paneVisited.add(v) }, { immediate: true })

// ---- 页签②: 激励专项授权(从系统设置 CategoryManager 收编, 复用共享 composable 单例) ----
const { permList, loadPermList, openPermDialog, revokePerm } = usePermissionData()

// ---- 页签③: 生效预览 ----
const previewUserId = ref(null)
const previewUserOptions = ref([])
const userSearching = ref(false)
const preview = ref(null)
const previewLoading = ref(false)

const searchPreviewUsers = async (query) => {
  userSearching.value = true
  try {
    const res = await getUserList({ current: 1, size: 20, keyword: query || undefined })
    previewUserOptions.value = res.data?.records || []
  } catch (e) { /* 检索失败保留现有选项 */ }
  userSearching.value = false
}

const loadPreview = async () => {
  if (!previewUserId.value) { preview.value = null; return }
  previewLoading.value = true
  try {
    preview.value = (await getPermCenterPreview(previewUserId.value)).data
  } catch (e) { preview.value = null }
  previewLoading.value = false
}

// 换人=全新预览(清旧结果再拉, 避免残留上一人的判定闪现)
const changePreviewUser = () => {
  preview.value = null
  loadPreview()
}

// ---- 页签④: 变更审计(sys_rule_version append-only) ----
const auditRecords = ref([])
const auditPage = ref(1)
const auditSize = 20
const auditTotal = ref(0)
const loadAudit = async () => {
  try {
    const res = await getRuleVersions({ current: auditPage.value, size: auditSize })
    auditRecords.value = (res.data?.records || []).map(r => ({
      createdAt: String(r.createdAt || r.created_at || '').replace('T', ' ').slice(0, 19),
      ruleKey: r.ruleKey || r.rule_key,
      oldValue: r.oldValue || r.old_value || '—',
      newValue: r.newValue || r.new_value || '—',
      operatorName: r.operatorName || r.operator_name || '—'
    }))
    auditTotal.value = Number(res.data?.total) || 0
  } catch (e) { auditRecords.value = [] }
}

onMounted(() => { loadPermList() })
</script>

<style scoped>
.perm-center { padding: 2px 0 8px; }
.pc-context { margin: 0 0 10px; font-size: 12px; color: var(--el-color-info); }
/* 横向 chip 条: 品牌蓝实底选中(醒目胶囊语言), 无 el-tabs 无劫持面 */
.pc-chipbar { display: flex; gap: 8px; flex-wrap: wrap; margin: 0 0 14px; }
.pc-chip { height: 32px; padding: 0 16px; border: none; border-radius: 16px; background: #f2f4f8; color: #4b5563; font-size: 13px; cursor: pointer; transition: all .2s; }
.pc-chip:hover { color: var(--el-color-primary); }
.pc-chip.active { background: var(--el-color-primary); color: #fff; font-weight: 600; }
.pc-sec { margin-top: 6px; }
.pc-sec-head { display: flex; align-items: baseline; flex-wrap: wrap; gap: 4px 10px; font-size: 14px; color: #1f2d3d; margin-bottom: 10px; }
.pc-sec-title { font-weight: 600; white-space: nowrap; flex-shrink: 0; }
.pc-sec-hint { font-size: 12px; font-weight: 400; color: var(--el-color-info); }
.pc-muted { color: var(--el-color-info); font-size: 12px; }
.pc-pill { margin-right: 4px; }
.pc-preview-bar { display: flex; align-items: center; gap: 12px; margin-bottom: 14px; }
.pc-preview-body { display: flex; flex-direction: column; gap: 12px; }
.pc-user-card { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; padding: 10px 12px; background: var(--el-color-primary-light-9); border-radius: 8px; }
.pc-user-name { font-size: 15px; }
.pc-mini-sec { font-size: 13px; font-weight: 600; color: #4e5969; margin-top: 4px; }
.pc-chips { display: flex; flex-wrap: wrap; gap: 10px; }
.pc-pool-chip { font-size: 13px; color: #4e5969; }
.pc-code { background: #f4f4f5; padding: 1px 6px; border-radius: 4px; font-size: 12px; }
.pc-hl { color: var(--el-color-primary); font-size: 14px; }
</style>
