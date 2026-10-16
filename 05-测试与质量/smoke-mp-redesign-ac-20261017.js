// MP 冒烟(需求A+C) —— 2026-10-17 连测试环境(生产快照数据 aigicrm_prod_20261016)
// 路径: 登录(admin/生产凭据) → 文件管理(两行全名/⋯面板/时间分组/行点击进详情) →
//       四项激励(客户封面卡 → customer-detail 详情页: 锚点/照片墙/历史/吸底栏)
// 结果: 29/29 通过(2026-10-17, 真实生产数据: 49文件行/108客户封面卡/46条历史默认展开)
// 运行: 前置 DevTools `cli auto --project <mp目录> --auto-port 9420`; 本文件拷贝自
//       /tmp/mp-smoke/smoke-mp-ac.js, 依赖 miniprogram-automator(scratch 目录安装)。
// 纪律: 真实 UI 登录(官方 setter); reLaunch ≤4 次(automator 密集 reLaunch 会崩)
const automator = require('miniprogram-automator')

const USER = process.env.USER_NAME || 'admin'
const PASS = process.env.PASS || ''
const results = []
function ok(name, cond, note = '') {
  results.push(`${cond ? '✅' : '❌'} ${name}${note ? ' — ' + note : ''}`)
  if (!cond) process.exitCode = 1
}
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

async function currentPagePath(mini) {
  const pages = await mini.currentPage()
  return pages ? pages.path : ''
}

;(async () => {
  const mini = await automator.connect({ wsEndpoint: 'ws://localhost:9420' })
  try {
    // DevTools 冷启动竞态: 首次 reLaunch 可能撞上 webview 重建, 失败则等 3s 重试一次
    await sleep(2500)
    try {
      await mini.reLaunch('/pages/login/index')
    } catch (_) { await sleep(3000); await mini.reLaunch('/pages/login/index') }
    await sleep(2500)
    // 0) 清 storage(防历史假 token 污染 401 报错墙)
    await mini.callWxMethod('clearStorageSync')
    await mini.reLaunch('/pages/login/index')
    await sleep(2500)
    let page = await mini.currentPage()
    ok('到达登录页', page && page.path.includes('pages/login/index'), page && page.path)

    // 1) 真实登录(走页面官方 setter; 先勾协议——合规门, 不勾提交会被拦)
    const userInput = await page.$('input[placeholder="请输入用户名"]')
    await userInput.input(USER)
    const passInput = await page.$('input[placeholder="请输入密码"]')
    await passInput.input(PASS)
    const agreeCheck = await page.$('.agreement-check')
    if (agreeCheck) await agreeCheck.tap()
    const loginBtn = await page.$('.login-btn')
    await loginBtn.tap()
    await sleep(4000)
    page = await mini.currentPage()
    ok(`登录 ${USER}`, page && !page.path.includes('login'), page && page.path)

    // 2) 需求A: 文件管理页
    await mini.reLaunch('/pages/files/index')
    await sleep(3500)
    page = await mini.currentPage()
    ok('到达文件管理页', page && page.path.includes('pages/files/index'), page && page.path)
    const fileRows = await page.$$('.file-row')
    ok('文件行渲染', fileRows.length > 0, `${fileRows.length} 行`)
    const firstName = await (await page.$('.file-name')).text()
    ok('文件名可见', Boolean(firstName && firstName.trim()), (firstName || '').slice(0, 30))
    const groupHeads = await page.$$('.file-group-head')
    ok('时间分组标题出现', groupHeads.length > 0, `${groupHeads.length} 组`)
    const moreBtns = await page.$$('.file-more-btn')
    ok('行尾「···」按钮存在', moreBtns.length > 0, `${moreBtns.length} 个`)
    const oldActions = await page.$$('.file-row-actions')
    ok('旧的内联按钮组已移除', oldActions.length === 0)

    // ⋯ → 半屏操作面板(admin 应见 打开/下载/分享/替换/删除)
    await moreBtns[0].tap()
    await sleep(900)
    const sheetPanel = await page.$('.sheet-panel')
    ok('半屏操作面板弹出', Boolean(sheetPanel))
    const sheetActs = await page.$$('.sheet-act')
    const actTexts = []
    for (const act of sheetActs) actTexts.push(await act.text())
    ok('面板含全部 5 个动作(admin)', actTexts.filter((t) => /打开|下载|分享|替换|删除/.test(t)).length >= 5, actTexts.join('/'))
    const dangerAct = await page.$('.sheet-act.danger')
    ok('删除为红色危险态', Boolean(dangerAct))
    const sheetName = await (await page.$('.sheet-file-name')).text()
    ok('面板标题显示文件名', Boolean(sheetName && sheetName.trim()), (sheetName || '').slice(0, 24))
    const cancelBtn = await page.$('.sheet-cancel')
    await cancelBtn.tap()
    await sleep(600)
    ok('面板可关闭', !(await page.$('.sheet-panel')))

    // 整行点击 = 打开(进 files/detail)
    await fileRows[0].tap()
    await sleep(2200)
    page = await mini.currentPage()
    ok('整行点击进入文件详情', page && page.path.includes('pages/files/detail/index'), page && page.path)

    // 3) 需求C: 四项激励客户封面卡
    await mini.reLaunch('/pages/incentive/index')
    await sleep(4500)
    page = await mini.currentPage()
    ok('到达四项激励页', page && page.path.includes('pages/incentive/index'), page && page.path)
    let custCards = await page.$$('.cust-card')
    let guard = 0
    while (custCards.length === 0 && guard < 6) { await sleep(1500); custCards = await page.$$('.cust-card'); guard += 1 }
    ok('客户封面卡渲染', custCards.length > 0, `${custCards.length} 张`)
    const covers = await page.$$('.cust-cover')
    const fallbacks = await page.$$('.cust-cover-fallback')
    ok('封面区渲染(图/渐变首字)', covers.length === custCards.length, `图${covers.length - fallbacks.length}/渐变${fallbacks.length}`)
    const oldWalls = await page.$$('.incentive-record-card')
    ok('旧巨卡结构已移除', oldWalls.length === 0)
    const oldFooters = await page.$$('.incentive-record-foot')
    ok('旧卡底按钮排已移除(红圈④)', oldFooters.length === 0)
    const cardName = await (await page.$('.cust-name')).text()
    ok('卡上客户名可见', Boolean(cardName && cardName.trim()), (cardName || '').slice(0, 20))

    // 点卡 → customer-detail 详情页
    await custCards[0].tap()
    await sleep(2800)
    page = await mini.currentPage()
    ok('点卡进入客户激励详情页', page && page.path.includes('pages/incentive/customer-detail/index'), page && page.path)
    if (page && page.path.includes('customer-detail')) {
      const heroName = await page.$('.cd-name')
      const heroNameText = heroName ? await heroName.text() : ''
      ok('详情页客户名渲染', Boolean(heroNameText && heroNameText.trim()), (heroNameText || '').slice(0, 20))
      const anchors = await page.$$('.cd-anchor-chip')
      const groups = await page.$$('.record-file-group')
      ok('分类锚点/分组照片墙(数据齐时)', anchors.length > 0 || groups.length > 0, `锚点${anchors.length}/分组${groups.length}`)
      const history = await page.$$('.cd-child-row')
      ok('历史记录默认展开', history.length > 0, `${history.length} 条`)
      const childMore = await page.$$('.cd-child-more')
      ok('单条记录「···」操作位', childMore.length === history.length)
      const bottomBtns = await page.$$('.cd-bottom-btn')
      ok('吸底操作栏', bottomBtns.length >= 1, `${bottomBtns.length} 个`)
      const wallActs = await page.$$('.wall-acts')
      ok('照片墙无内联按钮(红圈③消除)', wallActs.length === 0)
      const wallTags = await page.$$('.wall-tag')
      ok('照片墙无分类角标(红圈②消除)', wallTags.length === 0)
      await mini.navigateBack()
      await sleep(1200)
    }
    ok('脚本完整执行', true)
  } catch (e) {
    ok('脚本执行', false, String(e).slice(0, 300))
  } finally {
    try { await mini.disconnect() } catch (_) {}
    console.log(results.join('\n'))
    console.log(`\n${results.filter((r) => r.startsWith('✅')).length}/${results.length} 通过`)
  }
})()
