// 【2026-11-03 UI 调整】四项服务页顶部截图(launch 版): launch → 冷启动稳定 → reLaunch home → 截图
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
      port: 9424,
      timeout: 90000
    })
    console.log('[launch] ok(port 9424)')
    await sleep(12000)
  }
  let page = await mini.reLaunch('/pages/incentive/home/index')
  console.log('[reLaunch] ->', page && page.path)
  await sleep(5000)
  const cur = await mini.currentPage()
  console.log('[currentPage]', cur && cur.path)
  if (!cur || !/incentive\/home/.test(cur.path || '')) {
    await mini.switchTab('/pages/incentive/home/index')
    await sleep(4000)
    console.log('[switchTab fallback] ->', (await mini.currentPage()).path)
  }
  await mini.screenshot({ path: '/Users/dongjun/东来/项目/02-已上线/01-AIGICRM/02-迭代源代码/scripts/miniprogram/home-top.png' })
  console.log('[screenshot] saved')
  await mini.disconnect()
}

main().catch((e) => { console.error('[fail]', e && (e.stack || e.message || e)); process.exit(1) })
