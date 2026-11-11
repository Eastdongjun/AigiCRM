// 【MP 性能回归钉 2026-11-11】历史卡顿三主因(全量数组拉平/拨盘全量重算/文件页无缓存, 2026-10-27 修复)的防退化探针。
// 用法: NODE_PATH=scripts/miniprogram/node_modules HCU=<测试账号> HCP=<密码> node scripts/test-miniprogram-perf-regression.js
// 断言: 三关键页(文件/激励查询/激励详情)切换耗时 < 阈值(默认 4s, PERF_BUDGET_MS 可调); 只认 exit code。
const automator = require('miniprogram-automator');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const BUDGET = Number(process.env.PERF_BUDGET_MS || 4000);
const WS = process.env.MP_WS || 'ws://127.0.0.1:9423';
const fail = m => { console.error('FAIL: ' + m); process.exitCode = 1; };

(async () => {
  const mini = await automator.connect({ wsEndpoint: WS });
  try {
    // 登录态守卫: 在登录页则自动登录(须勾协议, tap 不触发用 callMethod)
    let page = await mini.currentPage();
    if (page.path.includes('login')) {
      const inputs = await page.$$('input');
      if (inputs.length < 2) return fail('登录页输入框异常');
      await inputs[0].input(process.env.HCU || '');
      await inputs[1].input(process.env.HCP || '');
      if (!(await page.data()).agreed) await page.callMethod('toggleAgreed');
      const btns = await page.$$('button');
      for (const b of btns) { const t = (await b.text()) || ''; if (t.includes('登') && !t.includes('忘记')) { await b.tap(); break; } }
      await sleep(4500);
      page = await mini.currentPage();
      if (page.path.includes('login')) return fail('自动登录失败(检查 HCU/HCP 或 DevTools 环境)');
    }
    // 轮询等待式计时: 触发跳转后轮询目标页+data 就绪, 测真实切换耗时(不含人为 sleep)
    const timed = async (name, trigger, wantPath) => {
      const t0 = Date.now();
      await trigger();
      let p = null;
      for (let i = 0; i < 60; i++) {
        p = await mini.currentPage();
        if (p && p.path.includes(wantPath)) { try { await p.data(); } catch (e) {} break; }
        await sleep(200);
      }
      const ms = Date.now() - t0;
      const ok = ms < BUDGET;
      console.log(`${ok ? 'PASS' : 'FAIL'}: ${name} ${ms}ms (预算 ${BUDGET}ms) → ${p ? p.path : '未到达'}`);
      if (!ok) process.exitCode = 1;
    };
    // ① 文件页(30s 缓存+列表渲染)
    await timed('文件页切换', () => mini.switchTab('/pages/files/index'), 'files/index');
    // ② 激励查询页(检索列表)
    await timed('激励查询页切换', () => mini.reLaunch('/pages/incentive/index?mode=query'), 'incentive/index');
    // ③ 激励详情 single(单记录渲染, 5f1e68ac)
    const dpage = await mini.currentPage();
    const rows = await dpage.$$('.rec-tr');
    if (rows.length >= 2) {
      await timed('检索单记录详情', async () => { await rows[1].tap(); }, 'detail/index');
      const det = await mini.currentPage();
      const dd = await det.data();
      console.log(`INFO: singleMode=${dd.singleMode} 记录数=${(dd.records || []).length}`);
      if (dd.singleMode !== true) { console.error('FAIL: single 模式未生效(入口参数断链?)'); process.exitCode = 1; }
    } else {
      console.log('INFO: 无检索结果行, 详情页探针跳过(非 FAIL)');
    }
    console.log(process.exitCode ? 'RESULT: FAIL' : 'RESULT: PASS');
  } finally { try { await mini.disconnect(); } catch (e) { /* 部分版本 disconnect 返回 undefined */ } }
})().catch(e => { console.error('ERROR: ' + e.message); process.exit(1); });
