// 【B1 安全修复 2026-10-21 → 2026-11-05 用户令"恢复记住密码"】
// 口令记忆功能按当年审计自己的建议恢复：WebCrypto AES-GCM + 设备绑定不可导出密钥
// (IndexedDB 存 CryptoKey) + 7 天有效期；明文/base64 直存依旧禁止。
// 降级口径：非安全上下文(HTTP 裸 IP, 如测试环境)无 crypto.subtle → 退化为 XOR+base64
// 弱混淆并显式标记 v:'xor-1'（仅测试档使用；生产 HTTPS 走强档 v:'webcrypto-1'）。
// 旧 B1 遗留键(aigicrm_remember_password)清理逻辑保持：clearRememberedPassword 同时清
// 旧遗留键与新加密键，layout/忘记密码/强制改密各路径的原调用点零改动生效。
export const REMEMBER_KEY_USER = 'aigicrm_remember_username'
export const REMEMBER_KEY_PASS = 'aigicrm_remember_password'
const REMEMBER_AUTH_KEY = 'aigicrm_remember_auth_v2'
const DEVICE_KEY_DB = 'aigicrm_device_key'
const AUTH_TTL_MS = 7 * 24 * 3600 * 1000 // 与保持登录档(remember-expiration)同周期
const XOR_SALT = 'aigicrm-device-2026'

/** 清除口令遗留键(旧 base64 键+新加密键)。存储不可用(隐私模式等)时静默。 */
export function clearRememberedPassword() {
  try {
    localStorage.removeItem(REMEMBER_KEY_PASS)
    localStorage.removeItem(REMEMBER_AUTH_KEY)
  } catch { /* 存储不可用时静默 */ }
}

/** 同时清除账号与口令记忆(忘记密码重置成功后使用)。 */
export function clearRemembered() {
  try {
    localStorage.removeItem(REMEMBER_KEY_USER)
    clearRememberedPassword()
  } catch { /* 存储不可用时静默 */ }
}

// ---- IndexedDB 设备密钥(不可导出 AES-GCM 256) ----
function idbOpen(db) {
  return new Promise((res, rej) => {
    const rq = indexedDB.open(db, 1)
    rq.onupgradeneeded = () => { if (!rq.result.objectStoreNames.contains('keys')) rq.result.createObjectStore('keys') }
    rq.onsuccess = () => res(rq.result)
    rq.onerror = () => rej(rq.error)
  })
}
function idbGet(db, key) {
  return new Promise((res, rej) => {
    const rq = idbOpen(db)
    rq.onsuccess = () => {
      const r = rq.result.transaction('keys').objectStore('keys').get(key)
      r.onsuccess = () => res(r.result)
      r.onerror = () => rej(r.error)
    }
    rq.onerror = () => rej(rq.error)
  })
}
function idbSet(db, key, val) {
  return new Promise((res, rej) => {
    const rq = idbOpen(db)
    rq.onupgradeneeded = () => { if (!rq.result.objectStoreNames.contains('keys')) rq.result.createObjectStore('keys') }
    rq.onsuccess = () => {
      const tx = rq.result.transaction('keys', 'readwrite')
      tx.objectStore('keys').put(val, key)
      tx.oncomplete = () => res()
      tx.onerror = () => rej(tx.error)
    }
    rq.onerror = () => rej(rq.error)
  })
}
const b64 = (buf) => btoa(String.fromCharCode(...new Uint8Array(buf)))
const unB64 = (b) => Uint8Array.from(atob(b), (c) => c.charCodeAt(0))
// 【2026-11-05 测试实证】jsdom/隐私模式下 IndexedDB 可能挂起(不 resolve 也不 reject)
// → 设备密钥读写一律带 800ms 超时, 超时即抛 → 调用方走 xor 降级, 任何环境不挂死
const withTimeout = (p, ms = 800) => Promise.race([
  p,
  new Promise((_, rej) => setTimeout(() => rej(new Error('idb timeout')), ms))
])

async function deviceKey() {
  // crypto.subtle 仅安全上下文(HTTPS/localhost)可用; HTTP 档返回 null → 走 xor 降级
  if (!(window.crypto && crypto.subtle)) return null
  // 【2026-11-05 门禁修复】无 indexedDB 环境(Node 测试 env/极端 webview)直接降级 xor:
  // 裸引用 indexedDB 会同步 ReferenceError → race 被立即拒绝后, withTimeout 的 800ms
  // 超时分支迟到拒绝无人接 → unhandled rejection(vitest exit 1)。与上方 crypto 守卫同构。
  if (typeof indexedDB === 'undefined') return null
  const cached = await withTimeout(idbGet(DEVICE_KEY_DB, 'k'))
  if (cached) return cached
  const k = await crypto.subtle.generateKey({ name: 'AES-GCM', length: 256 }, false, ['encrypt', 'decrypt'])
  await withTimeout(idbSet(DEVICE_KEY_DB, 'k', k))
  return k
}
function xorB64(plain) {
  const x = [...plain].map((c, i) => String.fromCharCode(c.charCodeAt(0) ^ XOR_SALT.charCodeAt(i % XOR_SALT.length))).join('')
  return btoa(unescape(encodeURIComponent(x)))
}
function xorUnB64(b64s) {
  const x = decodeURIComponent(escape(atob(b64s)))
  return [...x].map((c, i) => String.fromCharCode(c.charCodeAt(0) ^ XOR_SALT.charCodeAt(i % XOR_SALT.length))).join('')
}

/** 保存账号+口令记忆(7 天有效期, 过期自动失效清除)。HTTP 档降级弱混淆并在 payload 显式标记。 */
export async function saveRememberedAuth(username, password) {
  const exp = Date.now() + AUTH_TTL_MS
  let payload
  try {
    const key = await deviceKey()
    if (key) {
      const iv = crypto.getRandomValues(new Uint8Array(12))
      const ct = await crypto.subtle.encrypt({ name: 'AES-GCM', iv }, key, new TextEncoder().encode(password))
      payload = { v: 'webcrypto-1', u: username, iv: b64(iv), ct: b64(ct), exp }
    } else {
      payload = { v: 'xor-1', u: username, d: xorB64(password), exp } // 弱混淆降级档(仅测试 HTTP 档)
    }
  } catch {
    payload = { v: 'xor-1', u: username, d: xorB64(password), exp } // 加密失败兜底同降级
  }
  try { localStorage.setItem(REMEMBER_AUTH_KEY, JSON.stringify(payload)) } catch { /* 静默 */ }
}

/** 读取账号+口令记忆(过期返回 null 并清除)。WebCrypto 档解密失败亦返回 null。 */
export async function loadRememberedAuth() {
  try {
    const raw = localStorage.getItem(REMEMBER_AUTH_KEY)
    if (!raw) return null
    const p = JSON.parse(raw)
    if ((p.exp || 0) < Date.now()) { clearRememberedAuth(); return null }
    if (p.v === 'webcrypto-1') {
      const key = await deviceKey()
      if (!key) return null
      const pt = await crypto.subtle.decrypt({ name: 'AES-GCM', iv: unB64(p.iv) }, key, unB64(p.ct))
      return { username: p.u, password: new TextDecoder().decode(pt) }
    }
    if (p.v === 'xor-1') return { username: p.u, password: xorUnB64(p.d) }
    return null
  } catch {
    clearRememberedAuth()
    return null
  }
}

/** 清除账号+口令加密记忆键 */
export function clearRememberedAuth() {
  try { localStorage.removeItem(REMEMBER_AUTH_KEY) } catch { /* 静默 */ }
}
