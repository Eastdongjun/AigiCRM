/**
 * 【K-ENG-FE-001】微信 web-view 内核兼容 polyfill
 *
 * pdfjs-dist 6.5.438 在微信 web-view（XWeb，Chromium ~107-130 档）里会用到
 * 比内核更新的 JS API：
 * - Map.prototype.getOrInsertComputed（TC39 Upsert，主线程 22 处 + worker 19 处）
 * - Uint8Array.prototype.toHex（worker 计算 PDF 指纹）
 * - Uint8Array.prototype.toBase64 / Uint8Array.fromBase64（附件/签名数据）
 *
 * 本文件被两条线程各注入一次：
 * - 主线程：useFabushiPdfReader.js 顶部静态 import；
 * - worker：pdfjs-worker-shim.worker.js 静态 import，经 Vite `?worker&url` 打包。
 *
 * 语义按 TC39 提案实现；只补缺失方法，不覆盖内核原生实现。
 * 禁止引第三方依赖，保持单文件、可审计。
 */

const mapProto = /** @type {any} */ (Map.prototype)
const weakMapProto = /** @type {any} */ (WeakMap.prototype)
const u8Proto = /** @type {any} */ (Uint8Array.prototype)

function defineMethod(target, name, value) {
  if (typeof target[name] === 'function') return
  Object.defineProperty(target, name, {
    value,
    writable: true,
    enumerable: false,
    configurable: true,
  })
}

function assertUint8Array(receiver, method) {
  if (!(receiver instanceof Uint8Array)) {
    throw new TypeError(`Method ${method} called on incompatible receiver`)
  }
  return receiver
}

// ---------------------------------------------------------------------------
// Map.getOrInsert / getOrInsertComputed（TC39 Upsert）
// ---------------------------------------------------------------------------

defineMethod(mapProto, 'getOrInsert', function (key, value) {
  if (!(this instanceof Map)) {
    throw new TypeError('Method Map.prototype.getOrInsert called on incompatible receiver')
  }
  if (this.has(key)) return this.get(key)
  this.set(key, value)
  return value
})

defineMethod(mapProto, 'getOrInsertComputed', function (key, callbackfn) {
  if (!(this instanceof Map)) {
    throw new TypeError('Method Map.prototype.getOrInsertComputed called on incompatible receiver')
  }
  if (this.has(key)) return this.get(key)
  if (typeof callbackfn !== 'function') {
    throw new TypeError('Map.prototype.getOrInsertComputed: callback must be a function')
  }
  const inserted = callbackfn(key)
  this.set(key, inserted)
  return inserted
})

// CanvasGraphics 的路径缓存、部分 pdfjs 内部缓存使用 WeakMap；TC39 Upsert 同样覆盖 WeakMap。
defineMethod(weakMapProto, 'getOrInsert', function (key, value) {
  if (!(this instanceof WeakMap)) {
    throw new TypeError('Method WeakMap.prototype.getOrInsert called on incompatible receiver')
  }
  if (this.has(key)) return this.get(key)
  this.set(key, value)
  return value
})

defineMethod(weakMapProto, 'getOrInsertComputed', function (key, callbackfn) {
  if (!(this instanceof WeakMap)) {
    throw new TypeError('Method WeakMap.prototype.getOrInsertComputed called on incompatible receiver')
  }
  if (this.has(key)) return this.get(key)
  if (typeof callbackfn !== 'function') {
    throw new TypeError('WeakMap.prototype.getOrInsertComputed: callback must be a function')
  }
  const inserted = callbackfn(key)
  this.set(key, inserted)
  return inserted
})

// ---------------------------------------------------------------------------
// Uint8Array base64/hex（TC39 uint8array-base64 / uint8array-hex）
// ---------------------------------------------------------------------------

const B64_STD = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/'
const B64_URL = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_'

function alphabetOf(options) {
  return options && options.alphabet === 'base64url' ? B64_URL : B64_STD
}

defineMethod(u8Proto, 'toHex', function () {
  const bytes = assertUint8Array(this, 'Uint8Array.prototype.toHex')
  let out = ''
  for (let i = 0; i < bytes.length; i += 1) {
    out += bytes[i].toString(16).padStart(2, '0')
  }
  return out
})

defineMethod(u8Proto, 'toBase64', function (options) {
  const bytes = assertUint8Array(this, 'Uint8Array.prototype.toBase64')
  const alphabet = alphabetOf(options)
  const omitPadding = Boolean(options && options.omitPadding)
  let out = ''
  for (let i = 0; i < bytes.length; i += 3) {
    const b0 = bytes[i]
    const has1 = i + 1 < bytes.length
    const has2 = i + 2 < bytes.length
    const b1 = has1 ? bytes[i + 1] : 0
    const b2 = has2 ? bytes[i + 2] : 0
    out += alphabet[b0 >> 2]
    out += alphabet[((b0 & 3) << 4) | (b1 >> 4)]
    out += has1 ? alphabet[((b1 & 15) << 2) | (b2 >> 6)] : (omitPadding ? '' : '=')
    out += has2 ? alphabet[b2 & 63] : (omitPadding ? '' : '=')
  }
  return out
})

function decodeBase64(text, options) {
  const alphabet = alphabetOf(options)
  const table = Object.create(null)
  for (let i = 0; i < alphabet.length; i += 1) table[alphabet[i]] = i

  const clean = String(text).replace(/[\t\n\f\r ]/g, '')
  if (clean.length % 4 === 1) {
    throw new SyntaxError('Invalid base64 string')
  }
  const bytes = []
  let buffer = 0
  let bits = 0
  for (const ch of clean) {
    if (ch === '=') break
    const value = table[ch]
    if (value === undefined) throw new SyntaxError('Invalid base64 character')
    buffer = ((buffer << 6) | value) & 0xffffff
    bits += 6
    if (bits >= 8) {
      bits -= 8
      bytes.push((buffer >> bits) & 0xff)
    }
  }
  return new Uint8Array(bytes)
}

if (typeof Uint8Array.fromBase64 !== 'function') {
  Object.defineProperty(Uint8Array, 'fromBase64', {
    value: function (text, options) {
      return decodeBase64(text, options)
    },
    writable: true,
    enumerable: false,
    configurable: true,
  })
}

if (typeof Uint8Array.fromHex !== 'function') {
  Object.defineProperty(Uint8Array, 'fromHex', {
    value: function (text) {
      const clean = String(text).replace(/[\t\n\f\r ]/g, '')
      if (clean.length % 2 !== 0 || /[^0-9a-fA-F]/.test(clean)) {
        throw new SyntaxError('Invalid hex string')
      }
      const bytes = new Uint8Array(clean.length / 2)
      for (let i = 0; i < bytes.length; i += 1) {
        bytes[i] = parseInt(clean.slice(i * 2, i * 2 + 2), 16)
      }
      return bytes
    },
    writable: true,
    enumerable: false,
    configurable: true,
  })
}
