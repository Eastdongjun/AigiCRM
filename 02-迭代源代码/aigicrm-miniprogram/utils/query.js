// 页面 query 参数解码（2026-10-17 mp-0）
// 导航侧统一 encodeURIComponent 一次，页面 onLoad 只解码一次：真机上未解码的 options 会把
// %XX 串直接回填到表单并写回姓名/职位/备注；重复解码又会破坏本身就含 % 的合法文本。
// 非字符串（如数字 id）原样返回，不做转换。
function safeDecodeParam(value) {
  if (typeof value !== 'string') return value
  try {
    return decodeURIComponent(value)
  } catch (_) {
    return value
  }
}

module.exports = { safeDecodeParam }
