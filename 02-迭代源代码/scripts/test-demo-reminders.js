#!/usr/bin/env node

const assert = require('node:assert/strict')
const store = new Map()
global.wx = { getStorageSync: (key) => store.get(key), setStorageSync: (key, value) => store.set(key, value) }
const { isProcessed, markProcessed } = require('../aigicrm-miniprogram/utils/demo-reminders')
assert.equal(isProcessed('reminder-1'), false)
markProcessed('reminder-1')
markProcessed('reminder-1')
assert.equal(isProcessed('reminder-1'), true)
console.log('PASS: demo reminders keep processed state without duplicates.')
