// 【2026-11-03 hero 压缩验证】完整版四项服务应用页: 顶栏元素存在性+截图
import automator from 'miniprogram-automator'

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

async function main() {
  let mini
  try {
    mini = await automator.connect({ wsEndpoint: 'ws://localhost:9423' })
    console.log('[connect] ok(复用实例)')
  } catch (e) {
    mini = await automator.launch({
      cliPath: '/Applications/wechatwebdevtools.app/Contents/MacOS/cli',
      projectPath: '/Users/dongjun/东来/项目/02-已上线/01-AIGICRM/02-迭代源代码/aigicrm-miniprogram',
      port: 9423,
      timeout: 90000
    })
    console.log('[launch] ok')
    await sleep(12000)
  }
  let page = await mini.reLaunch('/pages/incentive/index')
  console.log('[reLaunch] ->', page.path)
  await sleep(3000)
  page = await mini.currentPage()
  const hero = await page.$('.workbench-hero')
  console.log('[单行顶栏] 存在 =', Boolean(hero))
  const title = await page.$('.workbench-hero-title')
  if (title) console.log('[标题] =', await title.text())
  const btns = await page.$$('.workbench-hero-actions .hero-bar-btn')
  console.log('[右侧按钮数]', btns.length, '(期望2: 刷新+上传)')
  for (const b of btns) console.log('  -', await b.text())
  const oldHero = await page.$('.upload-entry-row')
  console.log('[旧两行结构残留] =', Boolean(oldHero), '(期望 false)')
  await mini.screenshot({ path: '/Users/dongjun/东来/项目/02-已上线/01-AIGICRM/02-迭代源代码/scripts/miniprogram/hero-compact.png' })
  console.log('[screenshot] saved')
  await mini.disconnect()
}

main().catch((e) => { console.error('[fail]', e && (e.stack || e.message || e)); process.exit(1) })
