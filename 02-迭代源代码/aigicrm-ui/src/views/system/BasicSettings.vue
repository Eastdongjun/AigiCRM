<template>
  <!-- 【2026-11-05 用户令"系统设置骨架重新设计…功能要全，好用"】分组卡片化重排:
       原 20+ 字段单列长表单(仅两处分隔线)重排为五张白卡 + 吸底保存栏;
       script 逻辑与全部绑定零改动, 仅布局重排。卡片: 基本信息/客户与保护期/核心客户门槛/存储与回收站/客户规则与数据范围。 -->
  <el-form label-position="top" class="basic-form">
    <div class="bs-grid">
      <!-- ① 基本信息 -->
      <section class="bs-card">
        <h4 class="bs-card-title">基本信息</h4>
        <p class="bs-card-desc">系统显示名与标识，登录页与浏览器标题即时生效</p>
        <el-form-item label="系统名称">
          <el-input v-model="configMap['system.name']" style="width: 300px" />
        </el-form-item>
        <el-form-item label="系统副标题">
          <el-input v-model="configMap['system.subtitle']" style="width: 300px" placeholder="如：市场资源管理系统" />
        </el-form-item>
        <el-form-item label="系统Logo">
          <div style="display: flex; align-items: center; gap: 12px">
            <img v-if="configMap['system.logo']" :src="logoPreviewSrc" class="logo-preview" />
            <el-upload
              :action="'/api/file/upload'"
              :headers="uploadHeaders()"
              :data="{ folderId: 0, scope: 'public' }"
              :show-file-list="false"
              accept="image/*"
              :on-success="handleLogoUpload"
            >
              <el-button type="primary">上传Logo</el-button>
            </el-upload>
            <el-button v-if="canEditConfig && configMap['system.logo']" type="danger" link @click="configMap['system.logo'] = ''">清除</el-button>
          </div>
        </el-form-item>
      </section>

      <!-- ② 客户与保护期 -->
      <section class="bs-card">
        <h4 class="bs-card-title">客户与保护期</h4>
        <p class="bs-card-desc">保护期/提醒/调整期/开票超期四个日期口径（天），凌晨批按此重算客户状态</p>
        <el-form-item label="客户保护期">
          <el-input v-model="configMap['customer.protection.days']" style="width: 300px">
            <template #suffix>天</template>
          </el-input>
        </el-form-item>
        <el-form-item label="提醒提前">
          <el-input v-model="configMap['customer.warning.days']" style="width: 300px">
            <template #suffix>天</template>
          </el-input>
        </el-form-item>
        <el-form-item label="调整期天数">
          <el-input v-model="configMap['customer.adjust.days']" style="width: 300px">
            <template #suffix>天</template>
          </el-input>
        </el-form-item>
        <el-form-item label="开票超期天数">
          <el-input v-model="configMap['invoice.overdue.days']" style="width: 300px">
            <template #suffix>天</template>
          </el-input>
        </el-form-item>
      </section>

      <!-- ③ 核心客户门槛 -->
      <section class="bs-card">
        <h4 class="bs-card-title">核心客户门槛</h4>
        <p class="bs-card-desc">单位：万元。保存后自动按新门槛刷新全部客户核心标签</p>
        <!-- 【2026-11-03 用户令门槛配置化】核心客户判定双门槛(万元): 管理员可改, 保存后后端自动全量刷新核心标签 -->
        <el-form-item label="真核心门槛">
          <div class="retention-field">
            <el-input-number v-model="coreMajorWan" :min="0" :max="CORE_THRESHOLD_MAX" :step="10"
              aria-label="真核心门槛(万元)" />
            <span class="retention-unit">万</span>
            <span class="retention-hint">年度开票、回款均≥该值为真核心</span>
          </div>
        </el-form-item>
        <el-form-item label="争核心门槛">
          <div class="retention-field">
            <el-input-number v-model="coreMinorWan" :min="0" :max="CORE_THRESHOLD_MAX" :step="10"
              aria-label="争核心门槛(万元)" />
            <span class="retention-unit">万</span>
            <span class="retention-hint">均≥该值未双达真核心门槛=争核心，否则非核心</span>
          </div>
        </el-form-item>
      </section>

      <!-- ④ 存储与回收站 -->
      <section class="bs-card">
        <h4 class="bs-card-title">存储与回收站</h4>
        <p class="bs-card-desc">个人配额、文件回收站清理与扩容申请通道</p>
        <el-form-item label="默认存储空间">
          <el-input :model-value="storageGb" @update:model-value="onStorageGb" style="width: 300px">
            <template #suffix>GB</template>
          </el-input>
        </el-form-item>
        <el-form-item label="文件回收站保留">
          <div class="retention-field">
            <el-input-number v-model="retentionDays" :min="0" :max="RETENTION_MAX" :step="1"
              aria-label="文件回收站保留天数" />
            <span class="retention-unit">天</span>
            <span class="retention-hint">{{ retentionDays === 0 ? '不自动清理' : '超过保留天数的文件将自动清理' }}</span>
          </div>
        </el-form-item>
        <el-form-item label="允许申请扩容">
          <div class="apply-inline">
            <el-switch v-model="storageApplyEnabled" :disabled="!canEditConfig" />
            <span class="field-hint">开启后名单内成员可提交；关闭后仅管理员可申请</span>
          </div>
        </el-form-item>
        <el-form-item label="可申请人员">
          <div class="apply-block">
            <el-select v-model="storageApplyUsers" multiple filterable placeholder="选择人员（可多选）"
              :disabled="!canEditConfig" style="width: 100%">
              <el-option v-for="u in allUsers" :key="u.id" :label="u.realName" :value="u.id" />
            </el-select>
            <span class="field-hint">管理员始终可申请；名单内人员可提交扩容申请。管理员本人申请时，需在「审批流程配置」中安排其他审批人（禁止自审自批）。</span>
          </div>
        </el-form-item>
        <el-form-item label="单用户配额上限">
          <div class="apply-inline">
            <el-input-number v-model="storageMaxQuotaGb" :min="STORAGE_MAX_QUOTA_MIN_GB" :max="STORAGE_MAX_QUOTA_MAX_GB"
              :disabled="!canEditConfig" aria-label="单用户存储配额上限（GB）" />
            <span class="quota-unit">GB</span>
            <span class="field-hint">范围 1–1024 GB，默认 100 GB</span>
          </div>
        </el-form-item>
      </section>

      <!-- ⑤ 客户规则与数据范围 -->
      <section class="bs-card bs-card-wide">
        <h4 class="bs-card-title">客户规则与数据范围</h4>
        <p class="bs-card-desc">客户编号规则 · 新客户默认共享对象 · 系统级数据范围推导（上级/组织负责人）</p>
        <el-form-item label="编号规则">
          <el-select v-model="configMap['customer.code.rule']" style="width: 340px">
            <el-option label="大区首字母-年月-顺序号 (如 NM-202606-001)" value="region_date_seq" />
            <el-option label="大区首字母-顺序号 (如 NM-001)" value="region_seq" />
          </el-select>
        </el-form-item>
        <div class="bs-mini-caption">新客户默认共享规则</div>
        <div class="bs-switch-row">
          <span class="bs-switch-label">直接上级</span>
          <el-switch v-model="autoShare.superior" active-text="上级可见下级客户" />
        </div>
        <div class="bs-switch-row">
          <span class="bs-switch-label">管理员</span>
          <el-switch v-model="autoShare.admin" active-text="管理员可见全部客户" />
        </div>
        <el-form-item label="指定角色">
          <el-select v-model="autoShare.roles" multiple placeholder="选择角色（可多选）" style="width: 100%">
            <el-option v-for="r in allRoles" :key="r.roleCode" :label="r.roleName" :value="r.roleCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="指定人员">
          <el-select v-model="autoShare.users" multiple filterable placeholder="选择人员（可多选）" style="width: 100%">
            <el-option v-for="u in allUsers" :key="u.id" :label="u.realName" :value="u.id" />
          </el-select>
        </el-form-item>
        <div class="bs-mini-caption">系统级数据范围推导</div>
        <div class="bs-switch-row">
          <span class="bs-switch-label">上级看下属</span>
          <el-switch v-model="configMap['perm.superior.see.subordinate']"
            active-value="true" inactive-value="false"
            active-text="直接上级默认可见递归下属链数据（最多5层）" />
        </div>
        <div class="bs-switch-row">
          <span class="bs-switch-label">组织负责人</span>
          <el-switch v-model="configMap['perm.org.leader.see.org']"
            active-value="true" inactive-value="false"
            active-text="组织负责人默认可见本组织整棵子树全部数据" />
        </div>
      </section>
    </div>

    <!-- 吸底保存栏: 只读提示与保存动作常驻可视区, 不再沉在长表单底部 -->
    <div class="bs-savebar">
      <span v-if="!canEditConfig" class="readonly-tip">当前账号只读：需 system:config:edit 权限才能修改</span>
      <el-button v-if="canEditConfig" type="primary" @click="handleSave">保存设置</el-button>
    </div>
  </el-form>
</template>


<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getConfigList, updateConfigs, getRoleList } from '@/api/system'
import { useUserStore } from '@/store/user'
import { usePermissionData } from '@/composables/usePermissionData'
import { getToken } from '@/utils/token'
import {
  STORAGE_APPLY_ENABLED_KEY,
  STORAGE_APPLY_USERS_KEY,
  STORAGE_MAX_QUOTA_KEY,
  STORAGE_GB_BYTES,
  STORAGE_MAX_QUOTA_DEFAULT_GB,
  STORAGE_MAX_QUOTA_MAX_GB,
  STORAGE_MAX_QUOTA_MIN_GB,
  normalizeStorageApplyUsers,
  normalizeStorageMaxQuotaBytes,
  gbToBytes
} from '@/utils/storage-apply'

const router = useRouter()
const userStore = useUserStore()
const canEditConfig = computed(() => userStore.hasPermission('system:config:edit'))
// 【P2-6 2026-10-19】上传令牌走共享 getToken(与 FilePreview/StatsDetail/recycle 同款)且为渲染期活读:
// 原裸读 localStorage.getItem('token') 是 setup 时一次性快照(令牌刷新后一直发旧值)且硬编码存储键;
// 函数式绑定在每次重渲染时重取——el-upload 无行动期读取钩子, 这是绑定态下的最活口径
const uploadHeaders = () => ({ Authorization: 'Bearer ' + (getToken() || '') })
// 指定角色/指定人员下拉复用权限矩阵共享数据源（C4: 共享 composable）
const { allRoles, allUsers, loadAllUsers } = usePermissionData()

const configs = ref([])
const configMap = reactive({})
const autoShare = reactive({ superior: true, admin: true, roles: [], users: [] })

// 文件回收站保留天数：后端按字符串存储；UI 用 el-input-number 编辑（整数 0–3650）。
// 0 明确表示“不自动清理”，读取历史值/脏值时收敛为合法整数后再展示。
const RETENTION_KEY = 'file.recycle.retention.days'
const RETENTION_MAX = 3650
const retentionDays = computed({
  get() {
    const n = Number(configMap[RETENTION_KEY])
    if (!Number.isFinite(n)) return 0
    return Math.min(RETENTION_MAX, Math.max(0, Math.trunc(n)))
  },
  set(val) {
    configMap[RETENTION_KEY] = String(val ?? 0)
  }
})

// 【2026-11-03 门槛配置化】核心客户判定双门槛(万元, retentionDays 同款模式):
// major=真核心门槛(默认100), minor=争核心门槛(默认50); 保存后后端按新门槛全量刷新核心标签。
const CORE_MAJOR_KEY = 'customer.core.major.wan'
const CORE_MINOR_KEY = 'customer.core.minor.wan'
const CORE_THRESHOLD_MAX = 1000000
const coreMajorWan = computed({
  get() {
    const n = Number(configMap[CORE_MAJOR_KEY])
    if (!Number.isFinite(n)) return 100
    return Math.min(CORE_THRESHOLD_MAX, Math.max(1, Math.trunc(n)))
  },
  set(val) { configMap[CORE_MAJOR_KEY] = String(val ?? 100) }
})
const coreMinorWan = computed({
  get() {
    const n = Number(configMap[CORE_MINOR_KEY])
    if (!Number.isFinite(n)) return 50
    return Math.min(CORE_THRESHOLD_MAX, Math.max(1, Math.trunc(n)))
  },
  set(val) { configMap[CORE_MINOR_KEY] = String(val ?? 50) }
})

const normalizeSwitchValue = (value, defaultValue = 'true') => {
  if (value === undefined || value === null || value === '') return defaultValue
  if (value === true || value === 1) return 'true'
  if (value === false || value === 0) return 'false'
  const text = String(value).trim().toLowerCase()
  if (['true', '1', 'yes', 'y', 'on'].includes(text)) return 'true'
  if (['false', '0', 'no', 'n', 'off'].includes(text)) return 'false'
  return defaultValue
}

// 存储扩容申请：开关缺省开启；人员存 JSON 数组（兼容历史脏值）；上限以 bytes 存储、GB 编辑。
const storageApplyEnabled = computed({
  get: () => normalizeSwitchValue(configMap[STORAGE_APPLY_ENABLED_KEY], 'true') === 'true',
  set: (value) => { configMap[STORAGE_APPLY_ENABLED_KEY] = value ? 'true' : 'false' }
})
const storageApplyUsers = ref([])
const storageMaxQuotaGb = computed({
  get: () => Math.round(normalizeStorageMaxQuotaBytes(configMap[STORAGE_MAX_QUOTA_KEY]) / STORAGE_GB_BYTES),
  set: (value) => {
    const gb = Number(value)
    const safe = Number.isFinite(gb)
      ? Math.min(STORAGE_MAX_QUOTA_MAX_GB, Math.max(STORAGE_MAX_QUOTA_MIN_GB, Math.round(gb)))
      : STORAGE_MAX_QUOTA_DEFAULT_GB
    configMap[STORAGE_MAX_QUOTA_KEY] = String(gbToBytes(safe))
  }
})

const loadConfigs = async () => {
  try {
    const res = await getConfigList()
    configs.value = res.data || []
    configs.value.forEach(c => { configMap[c.configKey] = c.configValue })
    // 文件回收站保留天数：历史数值收敛为 0–3650 整数，保证输入框展示值与保存值一致
    const retentionRaw = configMap[RETENTION_KEY]
    const retentionNum = Number(retentionRaw)
    if (retentionRaw !== undefined && retentionRaw !== null && retentionRaw !== '' && Number.isFinite(retentionNum)) {
      configMap[RETENTION_KEY] = String(Math.min(RETENTION_MAX, Math.max(0, Math.trunc(retentionNum))))
    }
    // 【WP1.4 2026-11-13 锁定决策#8】系统级数据范围开关:缺行/脏值归一为**关**(与后端
    // getBooleanValue 缺省 false 的 fail-closed 口径一致; 旧前端缺行归一 'true' 是 fail-open 假象)。
    // 迁移 V2026.11.13.1 已为缺行环境补显式 'false', 此处口径仅兜底展示。
    configMap['perm.superior.see.subordinate'] = normalizeSwitchValue(configMap['perm.superior.see.subordinate'], 'false')
    configMap['perm.org.leader.see.org'] = normalizeSwitchValue(configMap['perm.org.leader.see.org'], 'false')
    // 客户默认共享规则:从 customer.auto_share(JSON 字符串)回填到 autoShare
    const asRaw = configMap['customer.auto_share']
    if (asRaw) {
      try {
        const parsed = JSON.parse(asRaw)
        autoShare.superior = !!parsed.superior
        autoShare.admin = !!parsed.admin
        autoShare.roles = parsed.roles || []
        autoShare.users = parsed.users || []
      } catch (e) { /* 历史脏值忽略,使用默认 */ }
    }
    // 存储扩容申请：缺省/脏值归一后再回填，保存时按同一口径序列化
    configMap[STORAGE_APPLY_ENABLED_KEY] = normalizeSwitchValue(configMap[STORAGE_APPLY_ENABLED_KEY], 'true')
    storageApplyUsers.value = normalizeStorageApplyUsers(configMap[STORAGE_APPLY_USERS_KEY])
    configMap[STORAGE_APPLY_USERS_KEY] = JSON.stringify(storageApplyUsers.value)
    configMap[STORAGE_MAX_QUOTA_KEY] = String(normalizeStorageMaxQuotaBytes(configMap[STORAGE_MAX_QUOTA_KEY]))
  } catch (e) {
    ElMessage.error(e.message || '系统设置加载失败，请检查服务或数据库状态')
  }
}

// Logo 预览统一走匿名可读的代理端点:配置值为路径式(如 /api/file/download/public/1/logo.jpg)
// 或 by-id 格式时,后端 /api/system/configs/logo 会解析 fileKey 并读磁盘返回,避免 404/403
// logoVersion 在保存完成后自增,强制 img 重新加载(上传时 configMap 先更新而 DB 仍是旧值,存在竞态)
const logoVersion = ref(0)
const logoPreviewSrc = computed(() => configMap['system.logo'] ? '/api/system/configs/logo?t=' + Date.now() + '-' + logoVersion.value : '')

const handleLogoUpload = async (res) => {
  if (res.code === 200 && res.data) {
    configMap['system.logo'] = res.data.ossUrl || res.data.url || ''
    ElMessage.success('Logo上传成功，正在保存...')
    // REV-20260802-02: 保存失败时回滚预览并提示, 不再无条件自增版本号
    const saved = await handleSave()
    if (saved) {
      logoVersion.value++ // 保存成功后强制预览重新加载新图
    } else {
      configMap['system.logo'] = ''
      ElMessage.error('Logo保存失败，请重试')
    }
  } else {
    ElMessage.error('上传失败')
  }
}

// 默认存储空间:配置存字节,UI 以 GB 展示/输入(1024^3 换算)
const storageGb = computed(() => {
  const v = Number(configMap['user.default.storage']) || 0
  return v > 0 ? String(Math.round(v / 1073741824 * 10) / 10) : '10'
})
const onStorageGb = (v) => {
  const gb = Number(v)
  if (!isNaN(gb) && gb > 0) configMap['user.default.storage'] = String(Math.round(gb * 1073741824))
}
const handleSave = async () => {
  // 仅 system:config:edit 可保存（按钮已按权限隐藏，此处防程序化调用/复制入口绕过）
  if (!canEditConfig.value) {
    ElMessage.warning('无权限修改系统设置（需 system:config:edit）')
    return false
  }
  // 文件回收站保留天数保存前兜底校验：必须是 0–3650 的整数（0 = 不自动清理）；
  // 输入控件已限制，这里防程序化赋值/历史脏值把非法内容提交给后端。
  if (configMap[RETENTION_KEY] !== undefined && configMap[RETENTION_KEY] !== null && configMap[RETENTION_KEY] !== '') {
    const days = Number(configMap[RETENTION_KEY])
    if (!Number.isInteger(days) || days < 0 || days > RETENTION_MAX) {
      ElMessage.error(`文件回收站保留天数需为 0–${RETENTION_MAX} 的整数（0 表示不自动清理）`)
      return false
    }
    configMap[RETENTION_KEY] = String(days)
  }
  // 存储扩容申请保存前校验：上限必须是 1–1024 的整数 GB（输入控件已限制，这里兜底）；
  // 人员名单按 JSON 数组序列化，避免把 Select 的响应式数组直接提交给后端。
  const maxQuotaGb = Number(storageMaxQuotaGb.value)
  if (!Number.isInteger(maxQuotaGb) || maxQuotaGb < STORAGE_MAX_QUOTA_MIN_GB || maxQuotaGb > STORAGE_MAX_QUOTA_MAX_GB) {
    ElMessage.error(`单用户配额上限需为 ${STORAGE_MAX_QUOTA_MIN_GB}–${STORAGE_MAX_QUOTA_MAX_GB} GB 的整数`)
    return false
  }
  configMap[STORAGE_MAX_QUOTA_KEY] = String(gbToBytes(maxQuotaGb))
  configMap[STORAGE_APPLY_USERS_KEY] = JSON.stringify(normalizeStorageApplyUsers(storageApplyUsers.value))
  configMap[STORAGE_APPLY_ENABLED_KEY] = storageApplyEnabled.value ? 'true' : 'false'
  // 【2026-11-03 门槛配置化】核心客户双门槛保存前兜底校验: 1–1000000 整数且 minor ≤ major
  // (输入控件已限制, 此处防程序化赋值/历史脏值提交; 后端 SysConfigController 同款校验双保险)
  const coreMajor = Number(configMap[CORE_MAJOR_KEY])
  const coreMinor = Number(configMap[CORE_MINOR_KEY])
  if (!Number.isInteger(coreMajor) || coreMajor < 1 || coreMajor > CORE_THRESHOLD_MAX
    || !Number.isInteger(coreMinor) || coreMinor < 1 || coreMinor > CORE_THRESHOLD_MAX) {
    ElMessage.error(`核心客户门槛需为 1–${CORE_THRESHOLD_MAX} 的整数（万元）`)
    return false
  }
  if (coreMinor > coreMajor) {
    ElMessage.error('争核心门槛（万）不能大于真核心门槛（万）')
    return false
  }
  configMap[CORE_MAJOR_KEY] = String(coreMajor)
  configMap[CORE_MINOR_KEY] = String(coreMinor)
  // 门槛是否变化(旧值在已加载列表里): 变化时保存成功文案提示已刷新核心标签
  const coreThresholdChanged = [CORE_MAJOR_KEY, CORE_MINOR_KEY].some(k => {
    const old = configs.value.find(c => c.configKey === k)
    return String(old?.configValue ?? '') !== String(configMap[k] ?? '')
  })
  try {
    // 客户默认共享规则:把 autoShare 序列化写回 configMap,随保存一并提交(否则该配置永不更新)
    configMap['customer.auto_share'] = JSON.stringify({
      superior: autoShare.superior,
      admin: autoShare.admin,
      roles: autoShare.roles || [],
      users: autoShare.users || []
    })
    // 以 configMap 为准提交所有键(含 DB 中尚不存在的键),后端按 configKey upsert
    const metaByKey = {}
    configs.value.forEach(c => { metaByKey[c.configKey] = c })
    const updated = Object.keys(configMap).map(key => ({
      id: metaByKey[key]?.id ?? null,
      configKey: key,
      configValue: configMap[key],
      description: metaByKey[key]?.description ?? null
    }))
    await updateConfigs(updated)
    ElMessage.success(coreThresholdChanged ? '设置已保存，已按新门槛刷新全部客户核心标签' : '设置已保存')
    await loadConfigs()
    userStore.loadSystemConfig()
    return true
  } catch (e) {
    ElMessage.error('保存失败，请重试')
    return false
  }
}

onMounted(() => {
  loadConfigs()
  loadAllUsers()
  // 【修复 20260805】"指定角色"下拉依赖 allRoles;挂载即加载(与权限矩阵共用数据源)
  getRoleList()
    .then(res => { allRoles.value = res.data || [] })
    .catch(e => { ElMessage.error(e.message || '角色列表加载失败') })
})
</script>

<style scoped>
.basic-form {
  padding: 4px 0 0;
}
.logo-preview {
  width: 48px;
  height: 48px;
  border-radius: 8px;
  object-fit: contain;
}
.retention-field { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.retention-unit { font-size: 13px; color: var(--el-text-color-regular); }
.retention-hint { font-size: 12px; color: var(--el-text-color-secondary); }
/* 存储扩容申请区：与既有表单行同宽；说明文字统一次级灰，只读提示对齐输入区 */
.apply-inline { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.apply-block { display: flex; flex-direction: column; gap: 4px; width: 100%; }
.field-hint { font-size: 12px; color: var(--el-text-color-secondary); line-height: 1.5; }
.quota-unit { font-size: 13px; color: var(--el-text-color-regular); }
.readonly-tip { margin: 0; font-size: 12px; color: var(--el-color-warning); }
/* ===== 【2026-11-05 骨架重排】分组卡片 + 吸底保存栏 ===== */
.bs-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(320px, 1fr)); gap: 14px; }
.bs-card { background: #fff; border: 1px solid #e4e7ed; border-radius: var(--radius-md, 10px); padding: 14px 18px 6px; min-width: 0 }
.bs-card-wide { grid-column: 1 / -1 }
.bs-card-title { margin: 0 0 2px; font-size: 14px; font-weight: 600; color: #1f2d3d }
.bs-card-desc { margin: 0 0 12px; font-size: 12px; color: var(--el-color-info) }
.bs-mini-caption { font-size: 12px; font-weight: 600; color: #4e5969; margin: 6px 0 8px; padding-left: 8px; border-left: 3px solid var(--el-color-primary-light-5) }
.bs-switch-row { display: flex; align-items: center; gap: 12px; margin: 0 0 10px }
.bs-switch-label { width: 72px; font-size: 13px; color: #4e5969; flex-shrink: 0 }
.bs-savebar { position: sticky; bottom: 0; z-index: 5; display: flex; align-items: center; justify-content: flex-end; gap: 12px; margin-top: 14px; padding: 12px 16px; background: #fff; border-top: 1px solid #ebeef5; border-radius: 0 0 var(--radius-md, 10px) }
/* label-position=top 后表单项纵向更紧凑 */
.bs-card :deep(.el-form-item) { margin-bottom: 14px }
.bs-card :deep(.el-form-item__label) { padding-bottom: 2px; font-size: 13px; color: #4e5969 }
</style>
