#!/usr/bin/env node

const assert = require('node:assert/strict')
const store = new Map()
global.wx = {
  getStorageSync(key) { return store.get(key) },
  setStorageSync(key, value) { store.set(key, value) }
}

const { readDemoContacts, saveDemoContact } = require('../aigicrm-miniprogram/utils/demo-contacts')
saveDemoContact('customer-1', { name: '演示联系人', position: '采购负责人' })
assert.equal(readDemoContacts('customer-1').length, 1)
assert.equal(readDemoContacts('customer-2').length, 0)
console.log('PASS: demo contacts persist per customer id.')
