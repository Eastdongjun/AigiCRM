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

module.exports = { getDepts, getDictData }
