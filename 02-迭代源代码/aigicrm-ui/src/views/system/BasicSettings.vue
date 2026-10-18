<template>
  <el-form label-width="120px" class="basic-form">
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
          :headers="{ Authorization: 'Bearer ' + token }"
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
    <el-form-item label="默认存储空间">
      <el-input :model-value="storageGb" @update:model-value="onStorageGb" style="width: 300px">
        <template #suffix>GB</template>
      </el-input>
    </el-form-item>
    <el-form-item label="文件回收站保留">
      <div class="retention-field">
        <el-input-number
          v-model="retentionDays"
          :min="0" :max="3650" :step="1" :precision="0" :value-on-clear="0"
          controls-position="right" style="width: 180px"
          aria-label="文件回收站保留天数"
        />
        <span class="retention-unit">天</span>
        <span class="retention-hint">{{ retentionDays === 0 ? '不自动清理' : '超过保留天数的文件将自动清理' }}</span>
      </div>
    </el-form-item>
    <el-form-item label="编号规则">
      <el-select v-model="configMap['customer.code.rule']" style="width: 300px">
        <el-option label="大区首字母-年月-顺序号 (如 NM-202606-001)" value="region_date_seq" />
        <el-option label="大区首字母-顺序号 (如 NM-001)" value="region_seq" />
      </el-select>
    </el-form-item>
    <el-divider content-position="left">客户默认共享规则</el-divider>
    <el-form-item label="直接上级">
      <el-switch v-model="autoShare.superior" active-text="上级可见下级客户" />
    </el-form-item>
    <el-form-item label="管理员">
      <el-switch v-model="autoShare.admin" active-text="管理员可见全部客户" />
    </el-form-item>
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
    <el-divider content-position="left">系统级数据范围</el-divider>
    <el-form-item label="上级看下属">
      <el-switch v-model="configMap['perm.superior.see.subordinate']"
        active-value="true" inactive-value="false"
        active-text="直接上级默认可见递归下属链数据（最多5层）" />
    </el-form-item>
    <el-form-item label="组织负责人">
      <el-switch v-model="configMap['perm.org.leader.see.org']"
        active-value="true" inactive-value="false"
        active-text="组织负责人默认可见本组织整棵子树全部数据" />
    </el-form-item>
    <el-form-item>
      <el-button v-if="canEditConfig" type="primary" @click="handleSave">保存设置</el-button>
    </el-form-item>
  </el-form>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getConfigList, updateConfigs, getRoleList } from '@/api/system'
import { useUserStore } from '@/store/user'
import { usePermissionData } from '@/composables/usePermissionData'

const router = useRouter()
const userStore = useUserStore()
const canEditConfig = computed(() => userStore.hasPermission('system:config:edit'))
const token = localStorage.getItem('token')
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

const normalizeSwitchValue = (value, defaultValue = 'true') => {
  if (value === undefined || value === null || value === '') return defaultValue
  if (value === true || value === 1) return 'true'
  if (value === false || value === 0) return 'false'
  const text = String(value).trim().toLowerCase()
  if (['true', '1', 'yes', 'y', 'on'].includes(text)) return 'true'
  if (['false', '0', 'no', 'n', 'off'].includes(text)) return 'false'
  return defaultValue
}

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
    // 系统级数据范围开关:DB 无行时默认开启(与后端 getBooleanValue 缺省 true 一致),首存即持久化
    configMap['perm.superior.see.subordinate'] = normalizeSwitchValue(configMap['perm.superior.see.subordinate'], 'true')
    configMap['perm.org.leader.see.org'] = normalizeSwitchValue(configMap['perm.org.leader.see.org'], 'true')
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
    ElMessage.success('设置已保存')
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
  max-width: 520px;
  padding: 20px 0;
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
</style>
