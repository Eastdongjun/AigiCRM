<template>
  <div class="onboarding">
    <el-card class="onboarding-card">
      <template #header>
        <div class="onboarding-head">
          <div>
            <h2>首次启动向导</h2>
            <p class="sub">基础配置四步走；密码修改已在首次登录时强制完成。</p>
          </div>
          <el-tag type="info" effect="plain">仅首次</el-tag>
        </div>
      </template>

      <el-steps :active="step" finish-status="success" simple>
        <el-step title="企业信息" />
        <el-step title="行业词表" />
        <el-step title="初始数据" />
      </el-steps>

      <div v-loading="loading" class="onboarding-body">
        <template v-if="step === 0">
          <el-form label-width="96px" class="form">
            <el-form-item label="系统名称" required>
              <el-input v-model="companyName" maxlength="40" show-word-limit placeholder="例如：AIGICRM" />
            </el-form-item>
            <el-form-item label="系统副标题">
              <el-input v-model="subtitle" maxlength="60" show-word-limit placeholder="例如：市场资源管理系统" />
            </el-form-item>
            <el-form-item label="企业简称">
              <el-input v-model="companyShort" maxlength="30" placeholder="用于欢迎语/导出抬头（可选）" />
            </el-form-item>
          </el-form>
          <el-alert type="info" :closable="false" show-icon
            title="保存后会更新登录页与侧栏品牌名；之后可在「系统设置 → 基础设置」修改。" />
        </template>

        <template v-else-if="step === 1">
          <p class="hint">选择本企业使用的行业；客户/激励等表单会读取这里的词表。可多选或新增。</p>
          <el-checkbox-group v-model="selectedIndustries" class="industry-group">
            <el-checkbox v-for="name in industryOptions" :key="name" :label="name" :value="name" />
          </el-checkbox-group>
          <div class="custom-row">
            <el-input v-model="customIndustry" placeholder="输入新行业，多个用逗号分隔" @keyup.enter="addCustomIndustry" />
            <el-button @click="addCustomIndustry">新增行业</el-button>
          </div>
          <div v-if="selectedIndustries.length" class="selected">
            <el-tag v-for="name in selectedIndustries" :key="name" closable @close="removeIndustry(name)">{{ name }}</el-tag>
          </div>
          <el-alert v-if="!industryTypeId" type="warning" :closable="false" show-icon
            title="未找到 industry 字典类型，无法新增行业；可先跳过，稍后在「字典管理」配置。" />
        </template>

        <template v-else>
          <el-result icon="success" title="基础配置完成" sub-title="组织与管理员账号已由安装器创建；业务演示数据可在各模块按需导入。">
            <template #extra>
              <div class="summary">
                <p>系统名称：<b>{{ companyName || '未填写' }}</b></p>
                <p>行业词表：<b>{{ selectedIndustries.length ? selectedIndustries.join('、') : '保持默认' }}</b></p>
                <p>初始账号：<b>admin</b>（密码已在首次登录时修改）</p>
                <p>演示数据：<b>{{ demoSeeded ? '已导入' : '未导入（可在客户模块按需导入）' }}</b></p>
              </div>
            </template>
          </el-result>
          <div class="demo-row">
            <el-button v-if="!demoSeeded" type="primary" plain :loading="seeding" @click="seedDemoData">
              导入演示客户
            </el-button>
            <el-tag v-else type="success">演示数据已导入</el-tag>
            <span class="hint">仅写入 DEMO001..003，不覆盖真实客户；可跳过。</span>
          </div>
          <el-alert type="info" :closable="false" show-icon
            title="点击「进入系统」后向导不再出现；需要改词表可去「系统设置 → 字典管理」。" />
        </template>
      </div>

      <div class="onboarding-actions">
        <el-button v-if="step > 0" :disabled="saving" @click="step -= 1">上一步</el-button>
        <div class="spacer" />
        <el-button v-if="step < 2" type="primary" :loading="saving" @click="next">下一步</el-button>
        <el-button v-else type="primary" :loading="saving" @click="finish">进入系统</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createDictData, getConfigList, getDictData, getDictTypes, seedOnboardingDemo, updateConfigs } from '@/api/system'

const router = useRouter()
const loading = ref(true)
const saving = ref(false)
const step = ref(0)
const configRows = ref([])
const companyName = ref('')
const subtitle = ref('')
const companyShort = ref('')
const industryOptions = ref([])
const selectedIndustries = ref([])
const customIndustry = ref('')
const industryTypeId = ref(null)
const demoSeeded = ref(false)
const seeding = ref(false)

function configValue(key, fallback = '') {
  const row = configRows.value.find(c => c.configKey === key)
  return row?.configValue ?? fallback
}

async function load() {
  loading.value = true
  try {
    const [cfgRes, dictRes, typeRes] = await Promise.all([
      getConfigList(),
      getDictData('industry'),
      getDictTypes()
    ])
    configRows.value = cfgRes?.data || []
    companyName.value = configValue('system.name', 'AIGICRM')
    subtitle.value = configValue('system.subtitle', '市场资源管理系统')
    companyShort.value = configValue('onboarding.companyShort', '')
    demoSeeded.value = configValue('onboarding.demoSeeded', 'false') === 'true'
    industryOptions.value = (dictRes?.data || []).map(d => d.dictLabel).filter(Boolean)
    const type = (typeRes?.data || []).find(t => t.dictCode === 'industry')
    industryTypeId.value = type?.id ?? null
    const saved = configValue('onboarding.industries', '')
    selectedIndustries.value = saved ? saved.split(',').map(s => s.trim()).filter(Boolean) : []
  } catch (e) {
    ElMessage.error('加载首启配置失败，请确认当前账号有系统配置权限')
  } finally {
    loading.value = false
  }
}

async function persist(pairs) {
  const map = new Map(configRows.value.map(c => [c.configKey, { ...c }]))
  pairs.forEach(([key, value]) => {
    const old = map.get(key) || {}
    map.set(key, { ...old, configKey: key, configValue: String(value) })
  })
  const rows = [...map.values()].map(c => ({
    id: c.id ?? null,
    configKey: c.configKey,
    configValue: c.configValue,
    description: c.description ?? null
  }))
  await updateConfigs(rows)
  const res = await getConfigList()
  configRows.value = res?.data || []
}

function addCustomIndustry() {
  const names = customIndustry.value.split(',').map(s => s.trim()).filter(Boolean)
  names.forEach(name => {
    if (!industryOptions.value.includes(name)) industryOptions.value.push(name)
    if (!selectedIndustries.value.includes(name)) selectedIndustries.value.push(name)
  })
  customIndustry.value = ''
}

function removeIndustry(name) {
  selectedIndustries.value = selectedIndustries.value.filter(x => x !== name)
}

async function next() {
  if (step.value === 0) {
    if (!companyName.value.trim()) return ElMessage.warning('请填写系统名称')
    saving.value = true
    try {
      await persist([
        ['system.name', companyName.value.trim()],
        ['system.subtitle', subtitle.value.trim()],
        ['onboarding.companyShort', companyShort.value.trim()]
      ])
      step.value = 1
    } catch (e) {
      ElMessage.error('保存企业信息失败')
    } finally {
      saving.value = false
    }
    return
  }
  if (step.value === 1) {
    saving.value = true
    try {
      if (industryTypeId.value) {
        const existing = new Set(industryOptions.value)
        let order = industryOptions.value.length + 1
        for (const name of selectedIndustries.value) {
          if (!existing.has(name)) {
            await createDictData({
              dictTypeId: industryTypeId.value,
              dictLabel: name,
              dictValue: name,
              sortOrder: order++,
              status: 1
            })
            existing.add(name)
          }
        }
      }
      await persist([['onboarding.industries', selectedIndustries.value.join(',')]])
      step.value = 2
    } catch (e) {
      ElMessage.error('保存行业词表失败')
    } finally {
      saving.value = false
    }
  }
}

async function seedDemoData() {
  if (demoSeeded.value) return
  seeding.value = true
  try {
    const res = await seedOnboardingDemo()
    demoSeeded.value = true
    ElMessage.success(`已导入 ${res?.data?.inserted ?? 0} 条演示客户`)
  } catch (e) {
    ElMessage.error('导入演示数据失败')
  } finally {
    seeding.value = false
  }
}

async function finish() {
  saving.value = true
  try {
    await persist([
      ['onboarding.completed', 'true'],
      ['onboarding.completedAt', new Date().toISOString()]
    ])
    ElMessage.success('向导完成，欢迎使用')
    router.replace('/dashboard')
  } catch (e) {
    ElMessage.error('保存完成状态失败，请重试')
  } finally {
    saving.value = false
  }
}

onMounted(load)

defineExpose({ next, finish, seedDemoData, step, companyName, selectedIndustries, industryOptions, demoSeeded })
</script>

<style scoped>
.onboarding { min-height: 100vh; display: flex; align-items: center; justify-content: center; padding: 24px; background: #f5f7fa; }
.onboarding-card { width: min(760px, 96vw); }
.onboarding-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; }
.onboarding-head h2 { margin: 0 0 4px; font-size: 20px; }
.sub, .hint { margin: 0; color: #6b7280; font-size: 13px; }
.onboarding-body { min-height: 240px; padding: 24px 4px 8px; }
.form { max-width: 520px; }
.industry-group { display: flex; flex-wrap: wrap; gap: 4px 8px; margin: 16px 0; }
.custom-row { display: flex; gap: 8px; max-width: 520px; }
.selected { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 16px; }
.onboarding-actions { display: flex; align-items: center; gap: 8px; padding-top: 16px; border-top: 1px solid #ebeef5; }
.spacer { flex: 1; }
.summary { text-align: left; line-height: 1.9; color: #374151; }
.demo-row { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; margin: 12px 0; }
</style>
