<template>
  <span class="fmuyu" :class="{ go: hit }" :style="{ '--muyu-s': scale }" aria-hidden="true">
    <span class="muyu-stage">
      <span class="muyu-glow"></span>
      <span class="muyu-ripple"></span>
      <span class="muyu-ground"></span>
      <svg class="muyu-svg" viewBox="34 42 184 126" xmlns="http://www.w3.org/2000/svg">
        <defs>
          <radialGradient id="fm-wood" cx="86" cy="84" r="112" gradientUnits="userSpaceOnUse">
            <stop offset="0" stop-color="#b97a42"/>
            <stop offset=".5" stop-color="#8a4e22"/>
            <stop offset="1" stop-color="#57300f"/>
          </radialGradient>
          <radialGradient id="fm-slot" cx="40%" cy="30%" r="90%">
            <stop offset="0" stop-color="#241204"/>
            <stop offset="1" stop-color="#0a0502"/>
          </radialGradient>
          <filter id="fm-b5" x="-60%" y="-60%" width="220%" height="220%"><feGaussianBlur stdDeviation="5"/></filter>
          <filter id="fm-b3" x="-60%" y="-60%" width="220%" height="220%"><feGaussianBlur stdDeviation="3"/></filter>
        </defs>
        <g class="m-fish">
          <path d="M 92,50 C 122,50 146,64 158,88 C 167,102 180,108 198,109 C 206,109.5 211,106 212,101 C 214,110 210,119 200,121 C 192,123 185,125 179,128 C 170,146 150,160 118,162 C 82,164 38,148 38,104 C 38,72 62,50 92,50 Z" fill="url(#fm-wood)" stroke="rgba(36,16,6,.5)" stroke-width="1.3"/>
          <ellipse cx="136" cy="76" rx="30" ry="13" fill="rgba(255,230,190,.22)" filter="url(#fm-b3)" transform="rotate(-8 136 76)"/>
          <ellipse cx="112" cy="60" rx="6" ry="3" fill="rgba(255,244,214,.45)" transform="rotate(-8 112 60)"/>
          <path d="M 52,140 C 62,152 82,160 104,162" fill="none" stroke="rgba(255,214,160,.2)" stroke-width="4" stroke-linecap="round" filter="url(#fm-b3)"/>
          <path d="M 176,120 C 180,130 178,140 172,148" fill="none" stroke="rgba(255,205,160,.16)" stroke-width="5" stroke-linecap="round" filter="url(#fm-b3)"/>
          <path d="M 42,88 C 48,72 62,60 80,57 C 92,55 100,60 104,68 C 111,83 126,99 142,106 C 129,111 112,109 99,103 C 82,96 58,92 42,88 Z" fill="url(#fm-slot)"/>
          <path class="m-slotflash" d="M 42,88 C 48,72 62,60 80,57 C 92,55 100,60 104,68 C 111,83 126,99 142,106 C 129,111 112,109 99,103 C 82,96 58,92 42,88 Z" fill="#ffcf78"/>
          <path d="M 52,90 C 60,86 72,84 84,84" fill="none" stroke="rgba(255,196,140,.3)" stroke-width="1.4" stroke-linecap="round"/>
        </g>
        <g class="m-mallet">
          <path d="M 210,150 L 193,112" stroke="#2e1c10" stroke-width="9" stroke-linecap="round"/>
          <path d="M 207,146 L 195,115" stroke="rgba(255,220,180,.16)" stroke-width="2.4" stroke-linecap="round"/>
          <ellipse cx="190" cy="101" rx="9.5" ry="12.5" fill="#54301a" stroke="rgba(24,10,4,.7)" stroke-width="1.4" transform="rotate(-16 190 101)"/>
          <path d="M 183.5,93 C 181.5,97 181.5,102 183,105" fill="none" stroke="rgba(255,228,190,.3)" stroke-width="1.6" stroke-linecap="round"/>
        </g>
      </svg>
      <span v-if="plusOn" class="muyu-plus">+1</span>
    </span>
  </span>
</template>

<script setup>
import { ref, watch } from 'vue'

// PC 木鱼：与小程序发布成功页那只同源——美术源 = 同目录 muyu-art.svg(FABUSHI-DELTA-20261128 S2)。
// 【2026-11-25 用户裁决】PC 取一半尺寸(scale 由使用方传 0.5)。部件与动画由 fabushi-muyu.css 承担；
// FabushiMuyu.spec.js 做素材一致性契约：本组件内联 SVG 必须与 muyu-art.svg 逐字符一致，
// 小程序端 assets/img/muyu.png(552×378 = 184×126@3x) / muyu-mallet.png(138×228 = 46×76@3x) 由同一源导出。
// 这个组件只负责图和动画：点赞接口、音效、计数文字都在用它的页面里。
const props = defineProps({
  // 1 = 小程序原尺寸（美术画布 184×126，整只含槌约 184×126 像素）
  scale: { type: Number, default: 1 },
  // 敲下去：为真时播放敲击动画（木槌摆击 + 鱼身压缩 + 光晕/涟漪各 0.8 秒）
  hit: { type: Boolean, default: false },
  // 每新增一次点赞加一：+1 只在这时飞起（取消点赞也会敲一下，但不该显示 +1）
  plusKey: { type: Number, default: 0 },
})

const plusOn = ref(false)
watch(() => props.plusKey, (value) => { if (value > 0) plusOn.value = true })
watch(() => props.hit, (value) => { if (!value) plusOn.value = false })
</script>

<style src="./fabushi-muyu.css"></style>
