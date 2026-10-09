<template>
  <!-- 【C9 2026-10-08 二轮】深蓝指标条全局组件：客户列表(cust-strip)/公海池(sea-strip)/审批管理(appr-strip)
       此前是三套手写的同视觉语言条带，收口为一处，改品牌色只需改 :root 令牌。
       视觉规格与原三套保持一致：--hero-gradient 深蓝底、24px 白字大数字、1px 竖分隔、右侧弱化提示语。 -->
  <div class="metric-strip">
    <template v-for="(m, i) in metrics" :key="i">
      <div v-if="i > 0" class="ms-divider"></div>
      <div class="ms-metric" :class="{ drill: interactive }" @click="interactive && emit('metric-click', i)">
        <div class="ms-num" :class="{ 'ms-alert': m.alert }">
          {{ m.num }}<span v-if="m.chip" class="ms-chip">{{ m.chip }}</span>
        </div>
        <div class="ms-lbl">{{ m.label }}</div>
        <!-- 【C9 三轮 2026-10-09】可选说明行：激励统计卡迁移到本组件时携带 desc（其余调用方不传不渲染） -->
        <div v-if="m.desc" class="ms-desc">{{ m.desc }}</div>
      </div>
    </template>
    <div v-if="tip" class="ms-tip">{{ tip }}</div>
  </div>
</template>

<script setup>
defineProps({
  /** 指标数组：[{ num, label, chip?, alert?, desc? }]；chip=琥珀警示徽标文案，alert=数字染警示色，desc=可选说明小字 */
  metrics: { type: Array, required: true },
  /** 右侧弱化提示语（可空） */
  tip: { type: String, default: '' },
  /** 指标是否可点击（点击触发 metric-click 事件，携带下标） */
  interactive: { type: Boolean, default: false },
})
const emit = defineEmits(['metric-click'])
</script>

<style scoped>
.metric-strip {
  display: flex; align-items: center; gap: 24px;
  background: var(--hero-gradient);
  border-radius: 12px; padding: 14px 24px; margin-bottom: 12px;
  box-shadow: var(--hero-shadow);
}
.ms-metric { min-width: 76px }
.ms-metric.drill { cursor: pointer }
.ms-num { font-size: 24px; font-weight: 700; color: #fff; line-height: 1.2; font-variant-numeric: tabular-nums }
.ms-num.ms-alert { color: #fca5a5 }
.ms-chip { font-size: 12px; font-weight: 600; padding: 2px 8px; border-radius: var(--radius-pill); white-space: nowrap; margin-left: 8px; vertical-align: 4px;
  background: rgba(251, 191, 36, .22); color: #fcd34d; border: 1px solid rgba(251, 191, 36, .45) }
.ms-lbl { font-size: 12px; color: rgba(255, 255, 255, .66); margin-top: 2px }
.ms-desc { font-size: 12px; color: rgba(255, 255, 255, .48); margin-top: 2px; line-height: 1.4 }
.ms-divider { width: 1px; height: 34px; background: rgba(255, 255, 255, .18) }
.ms-tip { margin-left: auto; font-size: 12px; color: rgba(255, 255, 255, .55) }
</style>
