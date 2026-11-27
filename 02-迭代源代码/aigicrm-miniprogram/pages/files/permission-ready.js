// 【BUG-MP-TABBAR-PERM-RACE-20261127】公共实现已提升到 utils/permission-ready.js
// (tabBar 与页面守卫共用同一份判定)。本文件保留为兼容入口, 文件页两个调用点无需改动。
// 与旧实现的关系: whenAuthReady() 语义逐字不变(只等在途回填, 失败放行到页面自己的判定)。
module.exports = require('../../utils/permission-ready')
