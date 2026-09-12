#!/usr/bin/env node

const assert = require('node:assert/strict')
const store = new Map()
global.wx = {
  getStorageSync(key) { return store.get(key) },
  setStorageSync(key, value) { store.set(key, value) }
}

const { readDemoFolders, saveDemoFolder, removeDemoFolder } = require('../aigicrm-miniprogram/utils/demo-folders')
const created = saveDemoFolder('演示资料')
assert.equal(readDemoFolders().length, 1)
saveDemoFolder('重命名资料', created.id)
assert.equal(readDemoFolders()[0].name, '重命名资料')
removeDemoFolder(created.id)
assert.equal(readDemoFolders().length, 0)
console.log('PASS: demo folder persistence creates, renames, and removes one record.')
