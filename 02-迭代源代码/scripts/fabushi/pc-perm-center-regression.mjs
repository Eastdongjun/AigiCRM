/**
 * PC 权限中心 · 法布施 9 码勾选/恢复回归（Playwright）
 *
 * 前置：测试环境存在隔离角色（默认 id=102, roleName=zz_mp_probe_role），
 *      可用 MP_REVOKE_ROLE_ID 覆盖；admin 凭据来自 ~/.aigicrm-deploy.env HEALTH_CHECK_*。
 * 运行：在 02-迭代源代码 根目录执行 `node scripts/fabushi/pc-perm-center-regression.mjs`
 * 输出：test-results/fabushi/pc-perm-center-evidence-20261116.json + 两张截图。
 */
import { createRequire } from 'node:module'
const require = createRequire(new URL('../../aigicrm-ui/package.json', import.meta.url))
const { chromium } = require('@playwright/test')
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'

const BASE = 'http://111.229.37.220'
const ROLE_ID = Number(process.env.MP_REVOKE_ROLE_ID || 102)
const OUT = path.resolve('test-results/fabushi')
const EVIDENCE = path.join(OUT, 'pc-perm-center-evidence-20261116.json')
const CODES = ['fabushi:view','fabushi:create','fabushi:book:view','fabushi:book:manage','fabushi:content:view','fabushi:content:manage','fabushi:stats:view','fabushi:stats:export','fabushi:activity:manage']
const ORDER = ['fabushi:view','fabushi:create','fabushi:book:view','fabushi:book:manage','fabushi:content:view','fabushi:content:manage','fabushi:stats:view','fabushi:stats:export','fabushi:activity:manage']

function loadEnv(p){const o={};for(const l of fs.readFileSync(p,'utf8').split(/\r?\n/)){const m=l.match(/^\s*([A-Za-z_]\w*)\s*=\s*(.*)\s*$/);if(m)o[m[1]]=m[2].replace(/^["']|["']$/g,'')}return o}
const env = loadEnv(path.join(os.homedir(), '.aigicrm-deploy.env'))
const evidence = { timestamp: new Date().toISOString(), environment: BASE, roleId: ROLE_ID, steps: {}, codes: {}, errors: [] }
function record(name, ok, detail){ evidence.steps[name]={ok,...detail}; console.log(`${ok?'PASS':'FAIL'} ${name}`, detail?JSON.stringify(detail):''); if(!ok) throw new Error(`step failed: ${name}`) }

const browser = await chromium.launch({ headless: true })
const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } })
page.on('console', m => { if (m.type()==='error') evidence.errors.push(m.text().slice(0,180)) })
try {
  await page.goto(BASE + '/login', { waitUntil:'networkidle', timeout:45000 })
  await page.fill('input[type="text"]', env.HEALTH_CHECK_USER)
  await page.fill('input[type="password"]', env.HEALTH_CHECK_PASS)
  await page.click('button:has-text("登")')
  await page.waitForURL(x => !String(x).includes('login'), { timeout:25000 })
  record('login', true, { url: page.url() })

  await page.goto(BASE + '/system/config', { waitUntil:'networkidle', timeout:45000 })
  await page.waitForTimeout(1200)
  await page.locator('.cfg-nav-item', { hasText:'权限中心' }).first().click()
  await page.waitForTimeout(1500)
  const chip = page.locator('.psm-who-chip', { hasText: 'zz_mp_probe_role' }).first()
  record('role-chip', (await chip.count()) === 1, { found: await chip.count() })
  await chip.click()
  await page.waitForTimeout(1500)

  const mod = page.locator('.psm-mod', { hasText:'法布施' }).first()
  record('fabushi-group', (await mod.count()) === 1, { found: await mod.count(), text: (await mod.textContent().catch(()=>''))?.trim() })
  await mod.click()
  await page.waitForTimeout(1000)

  const rowOf = (code) => page.locator('.psm-row').filter({ has: page.locator('.psm-row-code', { hasText: code }) }).first()
  const stateOf = async (code) => {
    const row = rowOf(code)
    if (!(await row.count())) return { exists:false, checked:null, disabled:null }
    const input = row.locator('input[type=checkbox]').first()
    return { exists:true, checked: await input.isChecked().catch(()=>null), disabled: await input.isDisabled().catch(()=>null) }
  }
  const before = {}
  for (const c of CODES) before[c] = await stateOf(c)
  record('all-9-present', CODES.every(c => before[c].exists), { before })
  const originallyChecked = CODES.filter(c => before[c].checked)
  evidence.codes.before = before

  // 逐个勾选（按依赖顺序）+ 每次等待 PUT 200
  for (const c of ORDER) {
    if (before[c].checked) { evidence.codes[c] = { action:'already-checked' }; continue }
    const row = rowOf(c)
    const respP = page.waitForResponse(r => r.url().includes(`/api/system/roles/${ROLE_ID}`) && r.request().method()==='PUT', { timeout:12000 }).catch(()=>null)
    await row.locator('.psm-row-check').click()
    const resp = await respP
    await page.waitForTimeout(500)
    const st = await stateOf(c)
    evidence.codes[c] = { action:'checked', http: resp ? resp.status() : null, checked: st.checked }
    record(`check:${c}`, st.checked === true && (!resp || resp.status() === 200), { http: resp ? resp.status() : null, checked: st.checked })
  }
  const afterCheck = {}
  for (const c of CODES) afterCheck[c] = await stateOf(c)
  record('all-9-checked', CODES.every(c => afterCheck[c].checked === true), { afterCheck })
  evidence.codes.afterCheck = afterCheck
  await page.screenshot({ path: path.join(OUT, 'pc-perm-center-fabushi-9.png') })

  // 恢复原始勾选状态（取消新增的 7 个），按依赖逆序
  for (const c of [...ORDER].reverse()) {
    if (originallyChecked.includes(c)) continue
    const row = rowOf(c)
    const st = await stateOf(c)
    if (!st.checked) continue
    const respP = page.waitForResponse(r => r.url().includes(`/api/system/roles/${ROLE_ID}`) && r.request().method()==='PUT', { timeout:12000 }).catch(()=>null)
    await row.locator('.psm-row-check').click()
    const resp = await respP
    await page.waitForTimeout(500)
    evidence.codes[c] = { ...(evidence.codes[c]||{}), restored: !(await stateOf(c)).checked, http: resp ? resp.status() : null }
  }
  const afterRestore = {}
  for (const c of CODES) afterRestore[c] = await stateOf(c)
  record('restored-original', CODES.every(c => afterRestore[c].checked === originallyChecked.includes(c)), { originallyChecked, afterRestore })
  evidence.codes.afterRestore = afterRestore
  await page.screenshot({ path: path.join(OUT, 'pc-perm-center-fabushi-restored.png') })
  evidence.result = 'PASS'
} catch (e) {
  evidence.result = 'FAIL'
  evidence.failure = String(e && e.message ? e.message : e)
  console.error('FAILED:', evidence.failure)
} finally {
  fs.mkdirSync(OUT, { recursive: true })
  fs.writeFileSync(EVIDENCE, JSON.stringify(evidence, null, 2))
  console.log('evidence:', EVIDENCE, 'result:', evidence.result)
  await browser.close()
  process.exit(evidence.result === 'PASS' ? 0 : 1)
}
