const request = require('../utils/request')

// 【D38 域归位】表单草稿接口自 api/system.js 迁出至本模块: 草稿(表单暂存)与
// 系统字典/部门是两套领域, 混在 system 模块造成域混乱。system.js 暂以再导出方式
// 过渡, 存量调用方迁移由后续去重批完成。
// 【议题四裁决】表单草稿(7天保留/账号+集团隔离/版本冲突检测)
function getFormDraft(draftType) { return request({ url: '/api/draft/' + draftType }) }
function saveFormDraft(draftType, draftData, version) { return request({ url: '/api/draft/' + draftType, method: 'PUT', data: { draftData: JSON.stringify(draftData), version: version || 0 } }) }
function forceSaveFormDraft(draftType, draftData) { return request({ url: '/api/draft/' + draftType + '/force', method: 'PUT', data: { draftData: JSON.stringify(draftData) } }) }
function deleteFormDraft(draftType) { return request({ url: '/api/draft/' + draftType, method: 'DELETE' }) }

module.exports = {
  getFormDraft,
  saveFormDraft,
  forceSaveFormDraft,
  deleteFormDraft
}
