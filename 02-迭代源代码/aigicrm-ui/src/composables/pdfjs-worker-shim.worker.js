// 【K-ENG-FE-001】pdf.js worker 线程 Map.upsert polyfill 注入壳
//
// 为什么存在：微信 web-view 内核缺 Map.prototype.getOrInsertComputed，
// pdfjs-dist 6.x 的 pdf.worker 在 worker 线程有 19 处调用（pdf.worker.mjs:3127
// 即生产报错行）。原 `?url` 方式让 Vite 原样拷贝 pdf.worker 静态资源，
// 构建期无法注入 polyfill；本壳经 `?worker&url` 让 Vite 把
// 「polyfill + pdf.worker」一起打包成 worker 产物，再以 workerSrc 交给 pdf.js。
//
// 语义要点：
// - 静态 import 顺序保证 polyfill 先于 pdf.worker 求值（ESM import 求值序）；
// - re-export WorkerMessageHandler 是给 pdf.js 的 fake-worker 回退路径用的：
//   pdf.mjs `_setupFakeWorkerGlobal` 会 `import(workerSrc)` 并取
//   `WorkerMessageHandler`（pdf.mjs:17185），保持该回退可用且同样被 polyfill 覆盖；
// - pdf.worker 自身有 `static {}` 自引导（worker 环境下 initializeFromPort(self)），
//   壳不参与消息循环，只做依赖收敛。
import '../utils/map-upsert-polyfill.js'
export { WorkerMessageHandler } from 'pdfjs-dist/build/pdf.worker.min.mjs'
