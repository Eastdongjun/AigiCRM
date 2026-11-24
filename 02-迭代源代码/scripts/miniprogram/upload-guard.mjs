// 小程序上传守卫：判定逻辑（GL-10, 2026-11-24）
//
// 为什么需要：scripts/miniprogram/upload.mjs 此前没有任何干净态检查，也不区分“指向生产”与
// “指向测试环境”的包。本机覆盖档 config/env.local.js 已 gitignore，但会被打进上传包；
// 体验版也读它之后（GL-15），带着覆盖档上传的包就会指向非生产环境——一个本应上线的包被悄悄指向了别的后端。
//
// 规则（全部满足才放行）：
//   ① 小程序源码目录没有未提交改动。未跟踪文件也会被打进包，同样算。
//      上传的包必须对应一个已提交的版本；提交号由调用方写进上传备注。
//   ② 存在本机覆盖档时，必须显式声明 MP_TARGET=staging。
//   ③ 声明了 MP_TARGET=staging 时，覆盖档必须存在（否则这个包其实指向生产，名不副实）。
//   ④ MP_TARGET 只认 prod / staging；不声明等同于 prod。
//
// 这个文件只做判定，不执行任何命令：取数（git 状态、文件是否存在）由调用方完成，
// 这样判定可以不联网、不碰仓库地被测透。行为测试见 ../test-miniprogram-upload-guard.js。

export const TARGETS = ['prod', 'staging']

/** 覆盖档在小程序目录里的相对位置（已 gitignore，见 aigicrm-miniprogram/.gitignore） */
export const OVERRIDE_REL = 'config/env.local.js'

/** 解析 MP_TARGET。不声明或空串 → 'prod'；取值非法 → null */
export function parseTarget(raw) {
  if (raw === undefined || raw === '') return 'prod'
  return TARGETS.includes(raw) ? raw : null
}

/**
 * @param {{ dirtyEntries: string[], hasOverride: boolean, target: string|null }} state
 *   dirtyEntries  `git status --porcelain -- <小程序目录>` 的各行；未跟踪文件也在其中
 *   hasOverride   本机覆盖档是否存在
 *   target        parseTarget 的结果
 * @returns {{ ok: true, target: string, hasOverride: boolean }
 *          | { ok: false, code: string, reasons: string[], hints: string[] }}
 */
export function decideUpload({ dirtyEntries, hasOverride, target }) {
  const refuse = (code, reasons, hints) => ({ ok: false, code, reasons, hints })
  if (target === null || target === undefined) {
    return refuse('bad-target',
      ['MP_TARGET 取值非法：只认 prod（生产）或 staging（测试环境）'],
      ['去掉 MP_TARGET（等同于 prod），或改成 prod / staging'])
  }
  if (dirtyEntries.length > 0) {
    return refuse('dirty',
      [`小程序源码目录有 ${dirtyEntries.length} 项未提交改动，上传的包必须对应一个已提交的版本：`, ...dirtyEntries.map((e) => `    ${e}`)],
      ['提交或还原这些改动后再上传；未跟踪的文件如果不需要入库，请移出小程序目录或加进 .gitignore'])
  }
  if (hasOverride && target === 'prod') {
    return refuse('override-without-staging',
      [`存在本机覆盖档 ${OVERRIDE_REL}，它会被打进上传包并让体验版指向非生产环境`],
      ['这个包只用于测试环境：显式声明 MP_TARGET=staging 后再上传，且不要提交审核',
        `要上传指向生产的包：先移走 ${OVERRIDE_REL}`])
  }
  if (!hasOverride && target === 'staging') {
    return refuse('staging-without-override',
      ['声明了 MP_TARGET=staging，但本机覆盖档不存在，这个包其实指向生产'],
      [`要指向测试环境：先创建 ${OVERRIDE_REL}`, '要上传指向生产的包：去掉 MP_TARGET'])
  }
  return { ok: true, target, hasOverride }
}

/**
 * 小程序目录是不是"嵌套在另一个仓库里的仓库"：目录自己是一个 git 仓库的顶层，而它的上级目录属于另一个仓库。
 * 这时 `git status` 看到的是嵌套仓库的状态，不是外层仓库里这个目录的状态 —— 嵌套仓库再"干净"
 * 也说明不了外层是干净的。2026-11-24 在真实仓库里发现过这种情形（aigicrm-miniprogram/.git）。
 *
 * @param {{ topSelf: string|null, topParent: string|null, project: string }} dirs 三个路径都应是真实路径
 *   topSelf    `git -C <小程序目录> rev-parse --show-toplevel`；不在仓库里为 null
 *   topParent  `git -C <小程序目录的上级> rev-parse --show-toplevel`；不在仓库里为 null
 *   project    小程序目录
 */
export function isNestedRepo({ topSelf, topParent, project }) {
  if (!topSelf || !topParent) return false
  return topSelf === project && topSelf !== topParent
}

/** 不在小程序目录所属的仓库里（或 git 不可用）：无法判断，不放行 */
export function noGitRefusal() {
  return {
    ok: false,
    code: 'no-git',
    reasons: ['无法判断小程序目录是否有未提交改动：它不在 git 仓库里，或 git 不可用'],
    hints: ['请在 git 仓库里上传：上传的包必须对应一个已提交的版本'],
  }
}

/** 小程序目录里有嵌套的 git 仓库 */
export function nestedRepoRefusal() {
  return {
    ok: false,
    code: 'nested-repo',
    reasons: [
      '小程序目录自己是一个 git 仓库的顶层（里面有一个 .git），而它同时处在外层仓库里：这是一个嵌套仓库。',
      '这种情况下 git 看到的是嵌套仓库的状态，不是外层仓库里这个目录的状态，守卫无法据此判断。',
    ],
    hints: [
      '先确认这个 .git 不是有意的。它通常是在这个目录里误执行 git init 留下的，不在外层仓库的版本控制之内。',
      '确认无用后删除小程序目录里的 .git，再重新上传。',
    ],
  }
}
