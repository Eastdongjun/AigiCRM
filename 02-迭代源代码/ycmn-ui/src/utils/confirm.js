import { ElMessageBox } from 'element-plus'

/**
 * 统一删除/撤销确认弹窗（UI 优化 2026-08-08）
 * - 说明左对齐，危险操作用红色确认按钮（右侧），取消按钮在左
 * - 关闭(X/遮罩)与取消区分：关闭不触发操作
 * @param {string} message 说明文案
 * @param {string} title 标题
 * @param {object} opts { confirmText, danger, iconType }
 */
export function confirmAction(message, title = '操作确认', opts = {}) {
  return ElMessageBox.confirm(message, title, {
    type: opts.iconType || 'warning',
    confirmButtonText: opts.confirmText || '确认',
    cancelButtonText: '取消',
    confirmButtonClass: opts.danger === false ? '' : 'el-button--danger',
    customClass: 'app-confirm',
    center: false,
    distinguishCancelAndClose: true,
    ...opts,
  })
}
