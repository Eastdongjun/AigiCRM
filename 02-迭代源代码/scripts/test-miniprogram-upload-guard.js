#!/usr/bin/env node
// 小程序上传守卫的行为测试（GL-10, 2026-11-24，先写本测试(红)→实现(绿)）
//
// 背景: scripts/miniprogram/upload.mjs 此前没有任何干净态检查, 也不区分"指向生产"与"指向测试环境"的包。
//   本机覆盖档 config/env.local.js 已 gitignore, 但会被打进上传包; GL-15 让体验版也读它之后,
//   带着覆盖档上传的包就可能指向非生产环境 —— 一个本应上线的包被悄悄指向了别的后端。
//
// 守卫规则:
//   ① 小程序源码目录有未提交改动(含未跟踪文件) → 拒绝。上传的包必须对应一个已提交的版本, 备注里记下提交号。
//   ② 存在本机覆盖档而没有显式声明 MP_TARGET=staging → 拒绝。
//   ③ 声明了 MP_TARGET=staging 却不存在覆盖档 → 拒绝(这个包其实指向生产, 名不副实)。
//   ④ MP_TARGET 取值非法 / 不在 git 仓库里 → 拒绝。
//   ⑤ 预览(自己扫码看)不受守卫限制; 开发时带着改动预览是常态。
//
// 两层验证: 判定函数的矩阵; 在临时 git 仓库里用演练模式真实运行 upload.mjs
// (演练模式不加载 miniprogram-ci、不联网、不要求密钥), 逐个验证退出码。
const assert = require('assert')
const { spawnSync } = require('child_process')
const { mkdtempSync, mkdirSync, writeFileSync, rmSync, existsSync, readFileSync } = require('fs')
const { tmpdir } = require('os')
const { join, dirname } = require('path')

const HERE = dirname(__filename)
const UPLOAD = join(HERE, 'miniprogram', 'upload.mjs')
const GUARD = join(HERE, 'miniprogram', 'upload-guard.mjs')
assert.ok(existsSync(UPLOAD), `缺少被测脚本 ${UPLOAD}`)
assert.ok(existsSync(GUARD), '缺少 scripts/miniprogram/upload-guard.mjs —— 上传守卫尚未实现')

const REFUSED = 3   // 守卫拒绝的退出码

async function main() {
  const guard = await import(GUARD)

  // ===== 第一层: 判定函数 =====
  assert.strictEqual(guard.parseTarget(undefined), 'prod', '不声明目标时默认是生产')
  assert.strictEqual(guard.parseTarget(''), 'prod', '空串等同于不声明')
  assert.strictEqual(guard.parseTarget('prod'), 'prod')
  assert.strictEqual(guard.parseTarget('staging'), 'staging')
  assert.strictEqual(guard.parseTarget('Staging'), null, '目标取值区分大小写, 不做模糊匹配')
  assert.strictEqual(guard.parseTarget('test'), null, '只认 prod / staging')
  assert.strictEqual(guard.parseTarget('bogus'), null)

  const clean = { dirtyEntries: [], hasOverride: false, target: 'prod' }
  const decided = (over) => guard.decideUpload({ ...clean, ...over })
  assert.strictEqual(decided({}).ok, true, '干净、无覆盖档、目标生产: 放行')
  assert.strictEqual(decided({ dirtyEntries: [' M app.js'] }).code, 'dirty', '有未提交改动: 拒绝')
  assert.strictEqual(decided({ dirtyEntries: ['?? notes.txt'] }).code, 'dirty', '未跟踪文件同样会被打进包: 拒绝')
  assert.strictEqual(decided({ hasOverride: true }).code, 'override-without-staging', '有覆盖档却没声明测试环境: 拒绝')
  assert.strictEqual(decided({ hasOverride: true, target: 'staging' }).ok, true, '有覆盖档且显式声明测试环境: 放行')
  assert.strictEqual(decided({ target: 'staging' }).code, 'staging-without-override', '声明测试环境却没有覆盖档: 拒绝')
  assert.strictEqual(decided({ target: null }).code, 'bad-target', '目标非法: 拒绝')
  assert.strictEqual(decided({ target: 'prod', hasOverride: true }).code, 'override-without-staging', '显式写 prod 也不放过覆盖档')
  assert.strictEqual(
    decided({ dirtyEntries: [' M app.js'], hasOverride: true, target: 'staging' }).code, 'dirty',
    '同时有多个问题时, 未提交改动优先报')
  for (const r of [decided({ dirtyEntries: [' M app.js'] }), decided({ hasOverride: true }), decided({ target: 'staging' }), decided({ target: null })]) {
    assert.ok(Array.isArray(r.reasons) && r.reasons.length > 0, `拒绝时必须说明原因(${r.code})`)
    assert.ok(Array.isArray(r.hints) && r.hints.length > 0, `拒绝时必须给出下一步怎么做(${r.code})`)
  }

  // ===== 第二层: 在临时 git 仓库里真实运行 upload.mjs(演练模式) =====
  // 前缀不要以 X 结尾: Node 自己会在前缀后追加 6 个随机字符, 以 X 结尾是 C 库 mkdtemp 的写法, Node 会警告不可移植
  const T = mkdtempSync(join(tmpdir(), 'aigicrm-mp-upload-guard.'))
  try {
    const MP = join(T, 'mp')
    const GIT = (args) => spawnSync('git', ['-C', T].concat(args), { encoding: 'utf8' })
    const writeFile = (rel, text) => {
      const p = join(MP, rel)
      mkdirSync(dirname(p), { recursive: true })
      writeFileSync(p, text)
    }
    mkdirSync(MP, { recursive: true })
    writeFile('project.config.json', JSON.stringify({ appid: 'wx-fixture', miniprogramRoot: './' }))
    writeFile('config/env.js', "module.exports = { appName: 'X', apiBaseUrl: '', demoMode: true }\n")
    writeFile('app.js', "App({})\n")
    writeFile('.gitignore', 'config/env.local.js\n')          // 与真实仓库一致: 覆盖档不入库
    writeFile('config/env.local.example.js', "module.exports = {}\n")
    assert.strictEqual(GIT(['init', '-q']).status, 0)
    assert.strictEqual(GIT(['add', '-A']).status, 0)
    assert.strictEqual(GIT(['-c', 'user.name=fixture', '-c', 'user.email=fixture@example.invalid', 'commit', '-q', '--no-verify', '-m', 'fixture']).status, 0)
    const HEAD = GIT(['rev-parse', '--short', 'HEAD']).stdout.trim()
    assert.ok(/^[0-9a-f]{7,}$/.test(HEAD), '夹具仓库没有产生提交号')

    const OVERRIDE = join(MP, 'config', 'env.local.js')
    // 运行被测脚本: 固定用参数数组启动 node, 不经过 shell; 演练模式不要求密钥
    const run = (opts) => {
      const mode = opts.mode || 'upload'
      const env = Object.assign({}, process.env, { MINIPROGRAM_DRY_RUN: '1', MINIPROGRAM_PROJECT_PATH: opts.projectPath || MP })
      delete env.MINIPROGRAM_PRIVATE_KEY
      delete env.MP_TARGET
      if (opts.target !== undefined) env.MP_TARGET = opts.target
      const r = spawnSync(process.execPath, [UPLOAD, mode], { encoding: 'utf8', env })
      return { status: r.status, out: r.stdout + r.stderr }
    }
    const expectPass = (label, r, needles) => {
      assert.strictEqual(r.status, 0, `${label}: 应放行(退出 0), 实际 ${r.status}; 输出:\n${r.out}`)
      for (const n of needles || []) assert.ok(r.out.includes(n), `${label}: 输出应包含 [${n}]; 输出:\n${r.out}`)
    }
    const expectRefused = (label, r, needles) => {
      assert.strictEqual(r.status, REFUSED, `${label}: 应被守卫拒绝(退出 ${REFUSED}), 实际 ${r.status}; 输出:\n${r.out}`)
      for (const n of needles || []) assert.ok(r.out.includes(n), `${label}: 输出应包含 [${n}]; 输出:\n${r.out}`)
    }
    const setOverride = (on) => {
      if (on) writeFile('config/env.local.js', "module.exports = { apiBaseUrl: 'https://staging.example.test' }\n")
      else rmSync(OVERRIDE, { force: true })
    }

    // ① 干净、无覆盖档: 放行, 目标生产, 备注带提交号
    let r = run({})
    expectPass('干净 / 无覆盖档 / 未声明目标', r, ['目标=prod', `commit=${HEAD}`, '未上传'])
    assert.ok(!/miniprogram-ci|缺少依赖/.test(r.out), '演练模式不应加载上传依赖')

    // ② 已跟踪文件有未提交改动: 拒绝
    writeFile('app.js', "App({ changed: true })\n")
    r = run({})
    expectRefused('已跟踪文件有未提交改动', r, ['未提交', 'app.js'])
    // 同时声明测试环境也一样拒绝: 未提交改动优先
    setOverride(true)
    expectRefused('脏 + 覆盖档 + staging', run({ target: 'staging' }), ['未提交'])
    setOverride(false)
    GIT(['checkout', '-q', '--', 'mp/app.js'])

    // ② 未跟踪文件(会被打进包): 拒绝
    writeFile('notes.txt', 'x')
    expectRefused('小程序目录里有未跟踪文件', run({}), ['未提交', 'notes.txt'])
    rmSync(join(MP, 'notes.txt'))

    // ② 小程序目录之外的改动不算(上传的只是这个目录)
    writeFileSync(join(T, 'README.md'), 'dirty outside the miniprogram dir\n')
    expectPass('小程序目录之外的未跟踪文件不拦', run({}), ['目标=prod'])
    rmSync(join(T, 'README.md'))

    // ③ 有覆盖档、没声明测试环境: 拒绝; 显式写 prod 也拒绝
    setOverride(true)
    expectRefused('有覆盖档 / 未声明目标', run({}), ['env.local.js', 'MP_TARGET=staging'])
    expectRefused('有覆盖档 / 显式 prod', run({ target: 'prod' }), ['MP_TARGET=staging'])
    // 显式声明测试环境: 放行, 输出标明目标与覆盖档
    expectPass('有覆盖档 / 显式 staging', run({ target: 'staging' }), ['目标=staging', '覆盖档=有', `commit=${HEAD}`])
    setOverride(false)

    // ④ 声明测试环境却没有覆盖档: 拒绝
    expectRefused('staging / 无覆盖档', run({ target: 'staging' }), ['staging', 'env.local.js'])
    // ④ 目标非法
    expectRefused('目标非法', run({ target: 'bogus' }), ['MP_TARGET'])

    // ⑤ 预览不受守卫限制
    writeFile('app.js', "App({ dirty: true })\n")
    expectPass('预览模式不受守卫限制', run({ mode: 'preview' }), ['预览'])
    GIT(['checkout', '-q', '--', 'mp/app.js'])
    expectPass('预览模式带着嵌套仓库也不受限', (() => {
      spawnSync('git', ['-C', MP, 'init', '-q'])
      const r = run({ mode: 'preview' })
      rmSync(join(MP, '.git'), { recursive: true, force: true })
      return r
    })(), ['预览'])

    // ④ 不在 git 仓库里: 拒绝(不猜)。夹具必须放在任何仓库之外 —— 临时目录本身就在仓库外
    const NONGIT_ROOT = mkdtempSync(join(tmpdir(), 'aigicrm-mp-upload-guard-nogit.'))
    try {
      const NONGIT = join(NONGIT_ROOT, 'mp')
      mkdirSync(NONGIT, { recursive: true })
      writeFileSync(join(NONGIT, 'project.config.json'), '{"appid":"wx-fixture"}')
      expectRefused('不在 git 仓库里', run({ projectPath: NONGIT }), ['不在 git 仓库里'])
    } finally {
      rmSync(NONGIT_ROOT, { recursive: true, force: true })
    }

    // ④ 嵌套仓库: 小程序目录自己有一个 .git(2026-11-24 在真实仓库里发现的情形)。
    //    这时 git 看到的是嵌套仓库的状态, 不是外层仓库里这个目录的状态 —— 嵌套仓库再"干净"也说明不了外层是干净的。
    assert.strictEqual(spawnSync('git', ['-C', MP, 'init', '-q']).status, 0)
    mkdirSync(join(MP, 'pages', 'fabushi'), { recursive: true })
    writeFileSync(join(MP, 'pages', 'fabushi', 'new-page.js'), '// 外层仓库里未跟踪的新文件, 嵌套仓库的索引里没有它\n')
    const nested = run({})
    expectRefused('小程序目录里有嵌套的 git 仓库', nested, ['嵌套'])
    assert.ok(!/未提交改动/.test(nested.out.split('怎么处理')[0]), '嵌套仓库时应说明是嵌套仓库, 而不是报一堆"未提交改动"')
    rmSync(join(MP, '.git'), { recursive: true, force: true })
    rmSync(join(MP, 'pages', 'fabushi'), { recursive: true, force: true })
    expectPass('移走嵌套的 .git 后恢复正常判断', run({}), ['目标=prod'])

    // 判定函数: 嵌套仓库的识别
    assert.strictEqual(guard.isNestedRepo({ topSelf: '/r/mp', topParent: '/r', project: '/r/mp' }), true, '目录自己是仓库顶层且处在另一个仓库里')
    assert.strictEqual(guard.isNestedRepo({ topSelf: '/r', topParent: '/r', project: '/r/mp' }), false, '与上级目录同属一个仓库')
    assert.strictEqual(guard.isNestedRepo({ topSelf: '/r/mp', topParent: null, project: '/r/mp' }), false, '独立的仓库(上级不在任何仓库里)不算嵌套')
    assert.strictEqual(guard.isNestedRepo({ topSelf: null, topParent: '/r', project: '/r/mp' }), false, '不在仓库里由另一条规则处理')

    // ===== 源码级: 真实上传之前必须先过守卫, 拒绝即退出 =====
    const src = readFileSync(UPLOAD, 'utf8')
    const guardAt = src.indexOf('decideUpload(')
    const uploadAt = src.indexOf('ci.upload(')
    assert.ok(guardAt > 0 && uploadAt > 0, 'upload.mjs 应调用 decideUpload 与 ci.upload')
    assert.ok(guardAt < uploadAt, '守卫必须出现在 ci.upload 之前')
    assert.ok(/MINIPROGRAM_DRY_RUN/.test(src), 'upload.mjs 应支持演练模式 MINIPROGRAM_DRY_RUN=1')
  } finally {
    rmSync(T, { recursive: true, force: true })
  }

  console.log('PASS: 小程序上传守卫(未提交改动与未跟踪文件拒绝 / 覆盖档必须显式声明测试环境 / 声明测试环境必须有覆盖档 / 嵌套仓库、非法目标、非 git 目录拒绝 / 只看小程序目录 / 预览不受限 / 演练模式不加载上传依赖)。')
}

main().catch((e) => {
  console.error('FAIL:', e && e.message ? e.message : e)
  process.exit(1)
})
