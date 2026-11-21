// 【K-ENG-FE-001】Map.upsert polyfill 单测 + pdf.js 真实取数路径复现验证
//
// 背景：微信 web-view 内核缺 Map.prototype.getOrInsertComputed（TC39 Upsert，
// Chrome 145+），pdfjs-dist 6.x 主线程/worker 线程共 41 处调用，真机必炸。
// 本文件三层验证：
// 1. 语义对齐 TC39 提案（原生缺失路径：先 delete 再注入）；
// 2. 原生存在时不覆盖（向前兼容，未来内核自带时不被 polyfill 破坏）；
// 3. 缺陷级复现：delete 原生方法 → 不注入 polyfill 走 pdf.js getDocument
//    取数路径应复现 `getOrInsertComputed is not a function`；注入后同路径通过。
//    （Node 22 / jsdom 同样无原生 upsert，与微信内核同类环境；本机 Node 26
//    若已原生携带，delete 步骤同样把它清掉，不依赖运行时版本。）
import { describe, it, expect, beforeEach, afterEach } from 'vitest'

const mapProto = /** @type {any} */ (Map.prototype)
const weakMapProto = /** @type {any} */ (WeakMap.prototype)
const u8Proto = /** @type {any} */ (Uint8Array.prototype)
const POLYFILL = '../utils/map-upsert-polyfill.js'

// 测试里在 Map 实例上调运行时注入的方法：tslib ES2020 无 upsert 声明，
// 收口一处 any 断言（与 mapProto 同型，仅用于本文件断言）。
const upsertable = (m) => /** @type {any} */ (m)

// 唯一 query 串 → vite/vitest 视作不同模块 → 每次动态 import 都重新求值。
// polyfill 是全局副作用模块，模块缓存会让重复 import 不再执行注入体，
// 所以「重新应用」必须每次用全新 query（计数器保证文件内唯一）。
let applySeq = 0
async function applyPolyfillFresh() {
  applySeq += 1
  await import(`${POLYFILL}?apply=fresh-${applySeq}`)
}

function deleteUpsertMethods() {
  delete mapProto.getOrInsert
  delete mapProto.getOrInsertComputed
  delete weakMapProto.getOrInsert
  delete weakMapProto.getOrInsertComputed
}

function deleteUint8ArrayMethods() {
  for (const target of [u8Proto, Uint8Array]) {
    for (const name of ['toHex', 'toBase64', 'fromBase64', 'fromHex']) {
      try { delete target[name] } catch (e) { /* native non-configurable: leave */ }
    }
  }
}

afterEach(() => {
  // 不把注入方法泄漏给同文件后续用例（vitest 默认按文件隔离 fork，双保险）
  deleteUpsertMethods()
  deleteUint8ArrayMethods()
  delete globalThis.pdfjsWorker
})

describe('Map.upsert polyfill 语义（原生缺失路径）', () => {
  beforeEach(deleteUpsertMethods)

  it('缺失时注入两个方法且不可枚举', async () => {
    await applyPolyfillFresh()
    expect(typeof mapProto.getOrInsert).toBe('function')
    expect(typeof mapProto.getOrInsertComputed).toBe('function')
    expect(mapProto.propertyIsEnumerable('getOrInsert')).toBe(false)
    expect(mapProto.propertyIsEnumerable('getOrInsertComputed')).toBe(false)
  })

  it('getOrInsert：缺键插入并返回 value', async () => {
    await applyPolyfillFresh()
    const m = new Map()
    expect(upsertable(m).getOrInsert('a', 1)).toBe(1)
    expect(m.get('a')).toBe(1)
    expect(m.size).toBe(1)
  })

  it('getOrInsert：已有键返回现值且不覆盖', async () => {
    await applyPolyfillFresh()
    const m = new Map([['a', 'old']])
    expect(upsertable(m).getOrInsert('a', 'new')).toBe('old')
    expect(m.get('a')).toBe('old')
  })

  it('getOrInsertComputed：缺键以 key 调回调、无 this、插入返回值', async () => {
    await applyPolyfillFresh()
    const m = new Map()
    const calls = []
    const ret = upsertable(m).getOrInsertComputed('k', function (key) {
      calls.push(key)
      // 回调按提案以 undefined 为 this 调用（普通函数可观测 this）
      expect(this).toBeUndefined()
      return [key]
    })
    expect(calls).toEqual(['k'])
    expect(ret).toEqual(['k'])
    expect(m.get('k')).toEqual(['k'])
  })

  it('getOrInsertComputed：已有键直接返回现值且不调回调（pdf.js 热路径语义）', async () => {
    await applyPolyfillFresh()
    const m = new Map([['k', 'cached']])
    let calls = 0
    const ret = upsertable(m).getOrInsertComputed('k', () => {
      calls += 1
      return 'computed'
    })
    expect(calls).toBe(0)
    expect(ret).toBe('cached')
  })

  it('回调非函数抛 TypeError', async () => {
    await applyPolyfillFresh()
    const m = new Map()
    expect(() => upsertable(m).getOrInsertComputed('k', undefined)).toThrow(TypeError)
    expect(() => upsertable(m).getOrInsertComputed('k', 42)).toThrow(TypeError)
  })

  it('接收者非 Map 抛 TypeError（含 WeakMap / 普通对象）', async () => {
    await applyPolyfillFresh()
    expect(() => mapProto.getOrInsert.call({}, 'k', 1)).toThrow(TypeError)
    expect(() => mapProto.getOrInsertComputed.call(new WeakMap(), 'k', () => 1)).toThrow(TypeError)
  })

  it('子类实例可用（继承行为不被破坏）', async () => {
    await applyPolyfillFresh()
    class MyMap extends Map {}
    const m = new MyMap()
    expect(upsertable(m).getOrInsert('a', 1)).toBe(1)
    expect(upsertable(m).getOrInsertComputed('b', () => 2)).toBe(2)
    expect(m.get('b')).toBe(2)
  })

  it('正负零与 NaN 键按 SameValueZero 语义', async () => {
    await applyPolyfillFresh()
    const m = new Map()
    upsertable(m).getOrInsert(-0, 'zero')
    // -0 与 +0 是同一键
    expect(upsertable(m).getOrInsert(0, 'other')).toBe('zero')
    upsertable(m).getOrInsert(NaN, 'nan')
    expect(m.get(NaN)).toBe('nan')
  })
})

describe('原生存在时 polyfill 不覆盖', () => {
  beforeEach(() => {
    deleteUpsertMethods()
    // 模拟未来内核 / Node 26 原生实现
    mapProto.getOrInsert = function () { return 'native-insert' }
    mapProto.getOrInsertComputed = function () { return 'native-computed' }
  })

  it('注入后保留原生实现', async () => {
    await import(POLYFILL + '?guard=native-present')
    const m = new Map()
    expect(upsertable(m).getOrInsert('k', 1)).toBe('native-insert')
    expect(upsertable(m).getOrInsertComputed('k', () => 1)).toBe('native-computed')
    expect(m.has('k')).toBe(false) // 原生 stub 未真插键 → polyfill 没接管
  })
})

describe('pdf.js 真实取数路径复现（缺陷修复验证）', () => {
  // 极小单页 PDF：xref 偏移程序化计算，内容流含一次文本绘制
  function buildMinimalPdfBytes() {
    const encoder = new TextEncoder()
    const objects = [
      '<</Type/Catalog/Pages 2 0 R>>',
      '<</Type/Pages/Kids[3 0 R]/Count 1>>',
      '<</Type/Page/Parent 2 0 R/MediaBox[0 0 200 200]/Resources<</Font<</F1 4 0 R>>>>/Contents 5 0 R>>',
      '<</Type/Font/Subtype/Type1/BaseFont/Helvetica>>',
      '<</Length 44>>\nstream\nBT /F1 12 Tf 20 100 Td (Hi) Tj ET\nendstream',
    ]
    const header = '%PDF-1.4\n'
    let body = ''
    const offsets = []
    objects.forEach((obj, i) => {
      offsets.push(header.length + body.length)
      body += `${i + 1} 0 obj\n${obj}\nendobj\n`
    })
    let xref = `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n`
    for (const off of offsets) xref += String(off).padStart(10, '0') + ' 00000 n \n'
    const startxref = header.length + body.length
    const pdf = header + body + xref
      + `trailer\n<</Size ${objects.length + 1}/Root 1 0 R>>\nstartxref\n${startxref}\n%%EOF\n`
    return encoder.encode(pdf)
  }

  // Node 22 / jsdom 测试环境缺 pdf.js 6.x 依赖的两个新 API（均为测试补偿，
  // 与被修缺陷无关，不进业务代码；微信内核侧见报告残余风险）：
  // - Promise.try（ES2025，pdf.mjs 消息循环）
  // - Uint8Array.prototype.toHex（文档指纹路径 pdf.worker.mjs:60423，Chromium 140+）
  function installTestRuntimeCompensation() {
    const PromiseCtor = /** @type {any} */ (Promise)
    if (typeof PromiseCtor.try !== 'function') {
      PromiseCtor.try = function (fn, ...args) {
        return new Promise((resolve) => { resolve(fn(...args)) })
      }
    }
    const u8Proto = /** @type {any} */ (Uint8Array.prototype)
    if (typeof u8Proto.toHex !== 'function') {
      u8Proto.toHex = function () {
        return Array.from(this, (b) => b.toString(16).padStart(2, '0')).join('')
      }
    }
  }

  // jsdom/Node 环境无真 Worker：pdf.js 走 fake-worker，主线程直连
  // globalThis.pdfjsWorker.WorkerMessageHandler（官方 no-worker 集成口径）。
  // 变量形式的 import 说明符让类型检查不追入无类型的 worker 产物文件。
  async function loadWorkerMessageHandler() {
    const workerSpec = 'pdfjs-dist/build/pdf.worker.min.mjs'
    const mod = await import(workerSpec)
    globalThis.pdfjsWorker = mod
    return mod.WorkerMessageHandler
  }

  async function runPdfFetchPath() {
    installTestRuntimeCompensation()
    const pdfjs = await import('pdfjs-dist')
    await loadWorkerMessageHandler()
    const task = pdfjs.getDocument({ data: buildMinimalPdfBytes() })
    try {
      const doc = await task.promise
      const page = await doc.getPage(1) // 主线程 #objs.getOrInsertComputed（pdf.mjs:14603）
      const ops = await page.getOperatorList() // 主线程 _intentStates.getOrInsertComputed（pdf.mjs:16676）
      return { numPages: doc.numPages, opCount: ops.fnArray.length }
    } finally {
      await task.destroy()
    }
  }

  it('负例：无 polyfill 时复现原崩溃（getOrInsertComputed is not a function）', async () => {
    deleteUpsertMethods()
    expect(mapProto.getOrInsertComputed).toBeUndefined()
    // 产线签名是 worker 线程 this._requestsByChunk.getOrInsertComputed
    // （pdf.worker.mjs:3127，需网络分块加载才触发）；本用例在主线程
    // getOperatorList 的 this._intentStates.getOrInsertComputed（pdf.mjs:16676）
    // 处复现同型 TypeError —— 同一缺失方法、同一错误类别。
    await expect(runPdfFetchPath()).rejects.toThrow(/getOrInsertComputed is not a function/)
  })

  it('正例：注入 polyfill 后同路径通过（numPages + 渲染指令就绪）', async () => {
    deleteUpsertMethods()
    await applyPolyfillFresh()
    const result = await runPdfFetchPath()
    expect(result.numPages).toBe(1)
    expect(result.opCount).toBeGreaterThan(0)
  })
})

describe('WeakMap.upsert polyfill 语义（pdfjs CanvasGraphics.pathCaches）', () => {
  beforeEach(deleteUpsertMethods)

  it('缺失时注入两个方法', async () => {
    await applyPolyfillFresh()
    expect(typeof weakMapProto.getOrInsert).toBe('function')
    expect(typeof weakMapProto.getOrInsertComputed).toBe('function')
  })

  it('getOrInsertComputed：缺键插入并返回，已有键不调回调', async () => {
    await applyPolyfillFresh()
    const m = new WeakMap()
    const key = {}
    let calls = 0
    const first = weakMapProto.getOrInsertComputed.call(m, key, () => { calls += 1; return { ok: true } })
    const second = weakMapProto.getOrInsertComputed.call(m, key, () => { calls += 1; return { ok: false } })
    expect(calls).toBe(1)
    expect(second).toBe(first)
    expect(first).toEqual({ ok: true })
  })
})

describe('Uint8Array base64/hex polyfill（pdfjs 6.5.438 依赖）', () => {
  beforeEach(deleteUint8ArrayMethods)

  it('toHex：小写、每字节两位', async () => {
    await applyPolyfillFresh()
    expect(u8Proto.toHex.call(new Uint8Array([0, 15, 255]))).toBe('000fff')
  })

  it('toBase64：标准字母表与 padding', async () => {
    await applyPolyfillFresh()
    expect(u8Proto.toBase64.call(new Uint8Array([0, 15, 255]))).toBe('AA//')
    expect(u8Proto.toBase64.call(new Uint8Array([0, 15, 255]), { omitPadding: true })).toBe('AA//')
  })

  it('toBase64/fromBase64：base64url 与往返', async () => {
    await applyPolyfillFresh()
    const src = new Uint8Array([251, 255, 0, 1, 2])
    const url = u8Proto.toBase64.call(src, { alphabet: 'base64url' })
    expect(url).toBe('-_8AAQI=')
    expect(Array.from(Uint8Array.fromBase64(url, { alphabet: 'base64url' }))).toEqual(Array.from(src))
  })

  it('fromHex：往返', async () => {
    await applyPolyfillFresh()
    expect(Array.from(Uint8Array.fromHex('000fff'))).toEqual([0, 15, 255])
  })
})
