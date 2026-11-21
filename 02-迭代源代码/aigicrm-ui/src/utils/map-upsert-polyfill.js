/**
 * 【K-ENG-FE-001】Map.prototype.getOrInsert / getOrInsertComputed polyfill
 *
 * 缘起：pdfjs-dist 6.5.438（aigicrm-ui 唯一 PDF 渲染库）在主线程与 worker 线程
 * 无条件调用 Map.prototype.getOrInsertComputed（TC39 Upsert 提案，
 * https://tc39.es/proposal-upsert ，Chrome/Edge 145+ 才有原生实现）。
 * 微信小程序 web-view 内核（XWeb，Chromium ~107-130 档）没有该方法，
 * H5 阅读页在真机上报
 * `this._requestsByChunk.getOrInsertComputed is not a function`（worker 线程，
 * pdf.worker.mjs:3127）后崩溃；主线程 pdf.mjs 还有 22 处同类调用。
 *
 * 方案：本文件被两条线程各注入一次——
 * - 主线程：useFabushiPdfReader.js 顶部静态 import（pdfjs 任何运行时调用前执行）；
 * - worker 线程：pdfjs-worker-shim.worker.js 静态 import，
 *   经 Vite `?worker&url` 打包为含 polyfill 的 worker 产物。
 *
 * 语义对齐 TC39 upsert 提案（2025-06 状态）：
 * - 已有键：直接返回现值，不覆盖、不调回调；
 * - getOrInsert(key, value)：缺键时插入 value 并返回 value；
 * - getOrInsertComputed(key, callbackfn)：缺键时以 key 为唯一实参、
 *   undefined 为 this 调 callbackfn，插入并返回其返回值；
 * - callbackfn 非函数 → TypeError；接收者非 Map → TypeError；
 * - 方法不可枚举（defineProperty），可写可配置（与原生一致）。
 *
 * 本文件禁止引第三方依赖（es-shims 等），改动面收口在 ~40 行自写实现。
 */

const mapProto = /** @type {any} */ (Map.prototype)

if (typeof mapProto.getOrInsert !== 'function') {
  Object.defineProperty(Map.prototype, 'getOrInsert', {
    value: function (key, value) {
      if (!(this instanceof Map)) {
        throw new TypeError('Method Map.prototype.getOrInsert called on incompatible receiver')
      }
      if (this.has(key)) {
        return this.get(key)
      }
      this.set(key, value)
      return value
    },
    writable: true,
    enumerable: false,
    configurable: true,
  })
}

if (typeof mapProto.getOrInsertComputed !== 'function') {
  Object.defineProperty(Map.prototype, 'getOrInsertComputed', {
    value: function (key, callbackfn) {
      if (!(this instanceof Map)) {
        throw new TypeError('Method Map.prototype.getOrInsertComputed called on incompatible receiver')
      }
      if (this.has(key)) {
        return this.get(key) // 已有键：不调回调，直接返回现值
      }
      if (typeof callbackfn !== 'function') {
        throw new TypeError('Map.prototype.getOrInsertComputed: callback must be a function')
      }
      const inserted = callbackfn(key) // 回调无 this、唯一实参为 key
      this.set(key, inserted)
      return inserted
    },
    writable: true,
    enumerable: false,
    configurable: true,
  })
}
