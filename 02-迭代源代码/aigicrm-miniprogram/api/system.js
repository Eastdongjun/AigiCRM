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

// 【D38】exports 统一放文件尾: 此前写在函数定义前靠声明提升才成立, 易在维护中踩坑
// 【E10】草稿接口过渡再导出已移除: 存量调用方(customer/form、incentive/upload)
// 均已改引 ./draft, system 模块回归纯系统字典/部门/范围职责。
module.exports = {
  getDepts,
  getDictData,
  getScopeUsers
}
