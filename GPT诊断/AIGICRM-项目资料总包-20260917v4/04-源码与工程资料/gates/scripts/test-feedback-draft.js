const assert = require('node:assert/strict')
const { getFeedbackDraftKey } = require('../aigicrm-miniprogram/utils/feedback-draft')

const app = { globalData: { currentUser: { id: 7 }, demoMode: false } }
assert.equal(getFeedbackDraftKey(app), 'feedbackDraft:7')
assert.equal(getFeedbackDraftKey({ globalData: { currentUser: { username: 'alice' } } }), 'feedbackDraft:alice')
assert.equal(getFeedbackDraftKey({ globalData: { demoMode: true } }), 'feedbackDraft:demo')
assert.equal(getFeedbackDraftKey({ globalData: {} }), 'feedbackDraft:anonymous')
console.log('PASS: feedback drafts use an account-scoped storage key.')
