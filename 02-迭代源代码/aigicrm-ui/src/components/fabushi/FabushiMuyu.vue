<template>
  <span class="fmuyu" :class="{ go: hit }" :style="{ '--muyu-s': scale }" aria-hidden="true">
    <span class="muyu-stage">
      <span class="muyu-glow"></span>
      <span class="muyu-ripple"></span>
      <span class="muyu-body"></span>
      <span class="muyu-cushion"></span>
      <span class="muyu-mallet"></span>
      <span v-if="plusOn" class="muyu-plus">+1</span>
    </span>
  </span>
</template>

<script setup>
import { ref, watch } from 'vue'

// PC 木鱼：与小程序发布成功页那只木鱼同源（aigicrm-miniprogram/pages/fabushi/upload/index.wxml 的 muyu-stage）。
// 【2026-11-25 用户裁决】“PC 端的木鱼需要做成和小程序的木鱼一样，注意尺寸就行”。
// 部件、顺序、样式、动画都照小程序；PC 只定一个缩放系数。画法在 fabushi-muyu.css 里，
// FabushiMuyu.spec.js 拿小程序的 wxml / wxss 与这个组件逐项对账。
// 这个组件只负责图和动画：点赞接口、音效、计数文字都在用它的页面里。
const props = defineProps({
  // 1 = 小程序原尺寸（750 设计稿 1rpx = 0.5px，整只木鱼约 144×108 像素）
  scale: { type: Number, default: 1 },
  // 敲下去：为真时播放小程序那三段动画（木槌 0.55 秒，波纹、光晕、+1 各 0.8 秒）
  hit: { type: Boolean, default: false },
  // 每新增一次点赞加一：+1 只在这时飞起（取消点赞也会敲一下，但不该显示 +1）
  plusKey: { type: Number, default: 0 },
})

const plusOn = ref(false)
watch(() => props.plusKey, (value) => { if (value > 0) plusOn.value = true })
watch(() => props.hit, (value) => { if (!value) plusOn.value = false })
</script>

<style src="./fabushi-muyu.css"></style>
