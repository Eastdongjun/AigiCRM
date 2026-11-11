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
      // 【动作面板完整性钉 2026-11-11】NO001 词表错位教训(edit vs rename): 进有 rename 权限的夹,
      // 长按**非本人**文件必含"重命名"——钉死"perms 数组→UI 消费"最后一公里的词表一致性
      const fpage2 = await mini.switchTab('/pages/files/index');
      await sleep(2500);
      const fp = await mini.currentPage();
      // 根口径(最近文件)无 rename——点进第一个文件夹再验
      const fd0 = await fp.data();
      if (!(fd0.folderPerms || []).includes('rename')) {
        const cards = await fp.$$('.folder-card');
        if (cards.length) { await cards[0].tap(); await sleep(2200); }
      }
      const fd = await fp.data();
      const hasRenamePerm = (fd.folderPerms || []).includes('rename') || (fd.folderPerms || []).includes('edit');
      if (hasRenamePerm && (fd.filteredFiles || []).length) {
        // 优先挑非本人文件(词表错位只在非 isOwn 暴露); MP_UID=当前登录用户 id(NO001 视角跑=硬断言);
        // 未传且夹内全本人则 isOwn 兜底算通(弱模式)
        const myId = process.env.MP_UID || null;
        const others = (fd.filteredFiles || []).filter(f => myId != null && f.uploaderId != null && f.uploaderId !== '' && String(f.uploaderId) !== String(myId));
        const list = fd.filteredFiles || [];
        const target = others.length ? list.indexOf(others[0]) : 0;
        const rowsF = await fp.$$('.file-row');
        let rowEl = null;
        for (const r of rowsF) { const di = await r.attribute('data-index'); if (String(di) === String(target)) { rowEl = r; break; } }
        if (rowEl) {
          await rowEl.longpress();
          await sleep(1200);
          const sheet = (await fp.data()).actionSheet || {};
          const labels = (sheet.actions || []).map(a => a.label).join(',');
          const okSheet = labels.includes('重命名') || (!others.length);
          console.log(`${okSheet ? 'PASS' : 'FAIL'}: 动作面板完整性(夹 rename 权限→面板含重命名) 动作=[${labels}]`);
          if (!okSheet) process.exitCode = 1;
        }
      } else {
        console.log(`INFO: 无 rename 权限夹(视角受限), 面板钉跳过(folderPerms=${JSON.stringify(fd.folderPerms || [])})`);
      }
    } else {
      console.log('INFO: 无检索结果行, 详情页与面板探针跳过(非 FAIL)');
    }
    console.log(process.exitCode ? 'RESULT: FAIL' : 'RESULT: PASS');
  } finally { try { await mini.disconnect(); } catch (e) { /* 部分版本 disconnect 返回 undefined */ } }
})().catch(e => { console.error('ERROR: ' + e.message); process.exit(1); });
