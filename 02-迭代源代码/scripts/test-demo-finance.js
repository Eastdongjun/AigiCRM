#!/usr/bin/env node

const assert = require('node:assert/strict')
const store = new Map()
global.wx = { getStorageSync: (key) => store.get(key), setStorageSync: (key, value) => store.set(key, value) }
const { readDemoFinance, saveDemoFinance } = require('../aigicrm-miniprogram/utils/demo-finance')
saveDemoFinance('customer-1', 'invoice', { invoiceNumber: 'DEMO-001', amount: '100' })
saveDemoFinance('customer-1', 'payment', { paymentDate: '2026-09-08', paymentAmount: '50' })
assert.equal(readDemoFinance('customer-1').invoices.length, 1)
assert.equal(readDemoFinance('customer-1').payments.length, 1)
assert.equal(readDemoFinance('customer-2').invoices.length, 0)
console.log('PASS: demo finance records persist by customer and type.')
