#!/usr/bin/env node

const assert = require('node:assert/strict')

const store = new Map()
global.wx = {
  getStorageSync(key) { return store.get(key) },
  setStorageSync(key, value) { store.set(key, value) }
}

const { readDemoCustomers, saveDemoCustomer } = require('../aigicrm-miniprogram/utils/demo-customers')
const created = saveDemoCustomer({ customerName: '本地演示客户', industry: '煤矿' })
assert.equal(readDemoCustomers().length, 1)
assert.equal(readDemoCustomers()[0].customerName, '本地演示客户')

const updated = saveDemoCustomer({ customerName: '已编辑演示客户', industry: '化工' }, created.id)
assert.equal(readDemoCustomers().length, 1)
assert.equal(updated.customerName, '已编辑演示客户')
assert.equal(readDemoCustomers()[0].industry, '化工')

console.log('PASS: demo customer persistence creates and updates one record by id.')
