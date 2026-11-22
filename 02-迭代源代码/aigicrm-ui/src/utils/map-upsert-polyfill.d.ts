/**
 * 【K-ENG-FE-001b 2026-11-22 类型收口】map-upsert-polyfill.js 补装的新 API 的类型声明。
 *
 * fromBase64/fromHex 为 ES2025 新增静态方法, 微信内核(140-144)与当前 TS lib 均未内置——
 * polyfill 在运行时补装, 此处补声明使 vue-tsc 认识调用点(基线只减不增, 不用 @ts-ignore)。
 * 见 scripts/check-ui-typecheck.sh 基线契约与 docs/test-gate-template-20261122.md H5。
 */
interface Uint8ArrayConstructor {
  fromBase64(base64: string, options?: { alphabet?: string; lastChunkHandling?: string }): Uint8Array;
  fromHex(hex: string): Uint8Array;
}
