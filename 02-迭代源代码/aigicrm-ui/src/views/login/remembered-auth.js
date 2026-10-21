// 【B1 安全修复 2026-10-21】登录口令不再写入 localStorage。
// 原实现把口令 base64(可逆)存入 localStorage，退出/改密/忘记密码/管理员重置均不清除，
// 强制改密成功后还会把新口令重新写入（审计报告 §4.2 B1）。
// 现在只保留"记住账号"；各路径调用 clearRememberedPassword() 清掉旧版本遗留在
// 浏览器里的口令键。此为安全优先的 UX 调整（"记住密码"勾选项已随之下线），
// 若产品后续要求恢复，须按审计建议走 WebCrypto + 设备绑定密钥 + 有效期，而非回退本文件。
export const REMEMBER_KEY_USER = 'aigicrm_remember_username'
export const REMEMBER_KEY_PASS = 'aigicrm_remember_password'

/** 清除口令遗留键（保留"记住账号"键）。存储不可用(隐私模式等)时静默。 */
export function clearRememberedPassword() {
  try { localStorage.removeItem(REMEMBER_KEY_PASS) } catch { /* 存储不可用时静默 */ }
}

/** 同时清除账号与口令键（忘记密码重置成功后使用）。 */
export function clearRemembered() {
  try {
    localStorage.removeItem(REMEMBER_KEY_USER)
    localStorage.removeItem(REMEMBER_KEY_PASS)
  } catch { /* 存储不可用时静默 */ }
}
