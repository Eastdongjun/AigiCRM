<template>
  <!-- 【C9 2026-10-08 二轮】深蓝指标条全局组件：客户列表(cust-strip)/公海池(sea-strip)/审批管理(appr-strip)
       此前是三套手写的同视觉语言条带，收口为一处，改品牌色只需改 :root 令牌。
       视觉规格与原三套保持一致：--hero-gradient 深蓝底、24px 白字大数字、1px 竖分隔、右侧弱化提示语。 -->
  <div class="metric-strip">
    <template v-for="(m, i) in metrics" :key="i">
      <div v-if="i > 0" class="ms-divider"></div>
      <div class="ms-metric" :class="{ drill: interactive }"
        :role="interactive ? 'button' : null" :tabindex="interactive ? 0 : null"
        @click="interactive && emit('metric-click', i)"
        @keydown.enter="interactive && emit('metric-click', i)"
        @keydown.space.prevent="interactive && emit('metric-click', i)">
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
.ms-metric.drill:focus-visible { outline: 2px solid #fff; outline-offset: 3px; border-radius: 6px; }
.ms-num { font-size: 24px; font-weight: 700; color: #fff; line-height: 1.2; font-variant-numeric: tabular-nums }
/* 【对比度 2026-10-17】#fca5a5 在最亮端点 #2563eb 仅 2.72:1；#fecaca 为大字号(24px/700) 3.57:1 达 AA 大字 3:1 */
.ms-num.ms-alert { color: #fecaca }
/* 【对比度 2026-10-17】浅黄字压琥珀半透明底仅约 3.05:1；改实心琥珀底+深色字 8.97:1，12px 达 AA */
.ms-chip { font-size: 12px; font-weight: 600; padding: 2px 8px; border-radius: var(--radius-pill); white-space: nowrap; margin-left: 8px; vertical-align: 4px;
  background: #fbbf24; color: #451a03; border: 1px solid #fbbf24 }
/* 【对比度 2026-10-17】12px 小字按最亮渐变端点 #2563eb 取值：.66→.95(3.14→4.82:1)、
   .48→.92(2.34→4.65:1)、.55→.92(2.63→4.65:1)，全部达 WCAG AA；数字保持纯白维持层级 */
.ms-lbl { font-size: 12px; color: rgba(255, 255, 255, .95); margin-top: 2px }
.ms-desc { font-size: 12px; color: rgba(255, 255, 255, .92); margin-top: 2px; line-height: 1.4 }
.ms-divider { width: 1px; height: 34px; background: rgba(255, 255, 255, .18) }
.ms-tip { margin-left: auto; font-size: 12px; color: rgba(255, 255, 255, .92) }
</style>
