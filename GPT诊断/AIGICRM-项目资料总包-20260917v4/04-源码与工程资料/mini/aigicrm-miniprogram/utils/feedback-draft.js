function getFeedbackDraftKey(app) {
  const user = (app && app.globalData && app.globalData.currentUser) || {}
  const identity = user.id || user.userId || user.username || (app && app.globalData && app.globalData.demoMode ? 'demo' : 'anonymous')
  return `feedbackDraft:${identity}`
}

module.exports = { getFeedbackDraftKey }
