#!/usr/bin/env node
// B1 首启浏览器验收：登录 -> 强制改密 -> 重登 -> /onboarding -> 行业 -> 演示种子 -> 工作台。
// 需要已安装且 admin 仍为初始口令（pwdResetRequired=1）的隔离栈。
// 用法: E2E_BASE_URL=http://127.0.0.1:18104 E2E_ADMIN_PASSWORD=... E2E_NEW_PASSWORD=... node scripts/e2e/onboarding.mjs
import { chromium } from '@playwright/test'

const base = process.env.E2E_BASE_URL
const oldPass = process.env.E2E_ADMIN_PASSWORD
const newPass = process.env.E2E_NEW_PASSWORD
if (!base || !oldPass || !newPass) {
  console.error('缺少 E2E_BASE_URL / E2E_ADMIN_PASSWORD / E2E_NEW_PASSWORD')
  process.exit(2)
}

const errors = []
const browser = await chromium.launch({ headless: true })
const page = await browser.newPage({ viewport: { width: 1280, height: 900 } })
page.on('pageerror', e => errors.push('pageerror: ' + e.message))

async function login(password) {
  await page.goto(base + '/login', { waitUntil: 'networkidle' })
  await page.fill('input[placeholder="请输入账号"]', 'admin')
  await page.fill('input[placeholder="请输入密码"]', password)
  await page.click('button:has-text("登 录")')
}

try {
  await login(oldPass)
  const box = page.locator('.el-message-box')
  await box.waitFor({ state: 'visible', timeout: 10000 })
  const input = box.locator('input')
  await input.fill(newPass)
  await box.locator('button:has-text("确认修改")').click()
  // 改密成功后仍停留在 /login，必须等成功提示再重登，避免与请求竞态
  await page.getByText('密码已修改，请用新密码重新登录').waitFor({ timeout: 10000 })
  await page.fill('input[placeholder="请输入密码"]', newPass)
  await page.click('button:has-text("登 录")')
  await page.waitForURL(/\/onboarding$/, { timeout: 15000 })

  await page.getByText('首次启动向导').waitFor({ state: 'visible' })
  const company = page.locator('input[placeholder="例如：AIGICRM"]')
  await company.fill('E2E 演示企业')
  await page.getByRole('button', { name: '下一步' }).click()

  await page.locator('.el-checkbox:has-text("冶金")').first().click()
  await page.getByRole('button', { name: '下一步' }).click()
  await page.getByText('基础配置完成').waitFor({ state: 'visible' })

  await page.getByRole('button', { name: '导入演示客户' }).click()
  await page.getByText('演示数据已导入').waitFor({ state: 'visible', timeout: 10000 })
  await page.getByRole('button', { name: '进入系统' }).click()

  await page.waitForURL(/\/dashboard/, { timeout: 15000 })
  await page.getByText('客户总数').waitFor({ state: 'visible', timeout: 10000 })
  await page.getByText('系统管理员').first().waitFor({ state: 'visible', timeout: 10000 })
  if (errors.length) throw new Error(errors.join('\n'))
  console.log('PASS: 登录/强制改密/向导/演示种子/工作台')
} catch (e) {
  await page.screenshot({ path: '/tmp/aigicrm-onboarding-e2e-fail.png', fullPage: true }).catch(() => {})
  console.error('FAIL:', e.message)
  if (errors.length) console.error(errors.join('\n'))
  process.exit(1)
} finally {
  await browser.close()
}
