const request = require('../utils/request')

/** 部门列表（大区 deptType=2 / 办事处 deptType=3）——客户表单"所属大区/办事处"下拉用。
 *  后端 /api/system/depts 已按 FEAT-20260804-DICT-DEPT-READ 放宽为"登录即可读"，
 *  普通销售在小程序端同样可读，不会 403。 */
function getDepts() {
  return request({ url: '/api/system/depts' })
}

/** 字典数据（industry / dev_type 等）——与 PC 端表单同源，避免手输造成口径不一致 */
function getDictData(dictCode) {
  return request({ url: '/api/system/dict/data/' + dictCode })
}

/** 当前用户数据范围可见人员（id/realName）——负责人"筛选"下拉专用(2026-09-23):
 *  筛选用范围感知(选范围外人只得空列表);选人(转移/分享接收人)仍走 customer/users/simple(集团边界) */
function getScopeUsers() {
  return request({ url: '/api/common/scope/users' })
}

module.exports = {
  getFormDraft,
  saveFormDraft,
  forceSaveFormDraft,
  deleteFormDraft, getDepts, getDictData, getScopeUsers }

// 【议题四裁决】表单草稿(7天保留/账号+集团隔离/版本冲突检测)
function getFormDraft(draftType) { return request({ url: '/api/draft/' + draftType }) }
function saveFormDraft(draftType, draftData, version) { return request({ url: '/api/draft/' + draftType, method: 'PUT', data: { draftData: JSON.stringify(draftData), version: version || 0 } }) }
function forceSaveFormDraft(draftType, draftData) { return request({ url: '/api/draft/' + draftType + '/force', method: 'PUT', data: { draftData: JSON.stringify(draftData) } }) }
function deleteFormDraft(draftType) { return request({ url: '/api/draft/' + draftType, method: 'DELETE' }) }
