#!/usr/bin/env node
/**
 * 法布施上传类型/大小边界用例（2026-11-17 T5f → U1/U2 修复验收）
 *
 * 分层校验口径（2026-11-17 修复后）：
 *   case1  U1 魔数层：非 PDF 内容伪装 .pdf 上传 → 预期 400（FileService.assertPdfMagic，
 *          前 1024 字节须含 %PDF-，PDF 规范 7.5.1）
 *   case2  U2 上传层大小：110MB(带合法 PDF 头) 上传 → 预期 200（上传层不做 100MB 限制，
 *          唯一硬闸=Spring multipart 500MB，application.yml:50；避免误伤 feed 大附件）
 *   case2b U2 挂载层大小：case2 文件挂载到书目 → 预期 400（FabushiBookService.attachPdf
 *          MAX_PDF_BYTES=100MB，与 book.vue「≤100MB」文案一致）
 *   case3  正例护栏：真 PDF 小文件上传+挂载 → 预期 200/200
 *   case4  F2 音段数上限：同帖挂载第 1-10 段 audio 放行、第 11 段 → 预期 400
 *          「语音已达上限10段」(F1 enforceAttachmentLimits; 2026-11-19 实现, live 待测试服窗口)
 *   case5  F2 单段时长：【FABUSHI-DELTA-20261128 R1 300s】durationSeconds=301 → 预期 400
 *          「单段语音不得超过5分钟」；durationSeconds=300 → 预期放行
 *
 * 用法：FABUSHI_TEST_USER=admin FABUSHI_TEST_PASS=<测试口令> node scripts/test-fabushi-upload-limits.js [BASE_URL]
 *   BASE_URL 默认 http://111.229.37.220（测试服）
 * 凭据：必须经环境变量 FABUSHI_TEST_USER / FABUSHI_TEST_PASS 提供（禁止写入本文件，
 *   secret 扫描会拦截；测试口令见团队密码管理渠道）。
 * 清理：挂载用书目在收尾软删（deleteBook 已有独立验收）；上传文件行留 fabushi scope
 *   （孤儿清理通道口径，测试环境）。
 */
'use strict';

const BASE = process.argv[2] || 'http://111.229.37.220';
const API = `${BASE}/api`;
const USER = process.env.FABUSHI_TEST_USER;
const PASS = process.env.FABUSHI_TEST_PASS;
if (!USER || !PASS) {
  console.error('缺少凭据: 请以 FABUSHI_TEST_USER=<账号> FABUSHI_TEST_PASS=<口令> 提供(不落盘、不入库)');
  process.exit(2);
}

// 【F2】case4/case5 创建的帖子 id——main() finally 统一 offline 下架(best-effort)
const cleanupPosts = [];

async function login() {
  const res = await fetch(`${API}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: USER, password: PASS }),
  });
  const body = await res.json();
  if (res.status !== 200 || body.code !== 200 || !body.data || !body.data.token) {
    throw new Error(`login failed: http=${res.status} body=${JSON.stringify(body).slice(0, 200)}`);
  }
  return body.data.token;
}

async function api(token, method, path, body) {
  const res = await fetch(`${API}${path}`, {
    method,
    headers: { Authorization: `Bearer ${token}`, ...(body ? { 'Content-Type': 'application/json' } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  return { status: res.status, body: await res.json().catch(() => null) };
}

async function uploadFile(token, fileName, bytes) {
  const form = new FormData();
  form.append('file', new Blob([bytes], { type: 'application/pdf' }), fileName);
  form.append('scope', 'fabushi');
  const res = await fetch(`${API}/file/upload`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body: form,
  });
  const body = await res.json().catch(() => null);
  return { status: res.status, body };
}

async function createApprovedBook(token, title) {
  const created = await api(token, 'POST', '/fabushi/books', {
    // 【F5 2026-11-19】类别 v3 迁移(V2026.11.19.3)后旧码 jiaxun 不在字典——换 guoxue;
    // 本脚本在测试服窗口执行, 时序=先部署含本迁移的后端再跑 live
    categoryCode: 'guoxue', title, author: '验收员', editionType: 'original',
  });
  let bookId = created.body && created.body.data && created.body.data.id;
  if (!bookId) {
    const books = await api(token, 'GET', '/fabushi/books?status=pending&current=1&size=50');
    const rows = (books.body && books.body.data && books.body.data.records) || [];
    const found = rows.find((r) => r.title === title);
    bookId = found && found.id;
  }
  if (!bookId) throw new Error('setup: create book failed ' + JSON.stringify(created.body).slice(0, 300));
  const ap = await api(token, 'PUT', `/fabushi/books/${bookId}/approve`, {});
  if (!(ap.status === 200 && ap.body && ap.body.code === 200)) {
    throw new Error(`setup: approve failed ${JSON.stringify(ap.body).slice(0, 200)}`);
  }
  return bookId;
}

async function runCase1(token, results) {
  // ---------- case1: U1 魔数层——非 PDF 内容伪装 .pdf ----------
  // 预期：上传 400（assertPdfMagic 拦截"任意内容改名 .pdf"）
  const fake = Buffer.from('this is NOT a pdf, just plain text disguise\n'.repeat(40), 'utf8');
  const up = await uploadFile(token, 'fake.pdf', fake);
  const rejected = up.status === 400 && up.body && up.body.code === 400;
  results.push({
    name: 'case1 U1 魔数层：纯文本伪装 fake.pdf 上传',
    expect: '400 拒绝(缺 %PDF- 魔数)',
    got: `http=${up.status} code=${up.body && up.body.code} msg=${(up.body && up.body.message || '').slice(0, 40)}`,
    pass: rejected,
  });
}

async function runCase2(token, results) {
  // ---------- case2: U2 上传层——110MB 合法头文件 ----------
  // 预期：上传 200（上传层无 100MB 限制；合法头不触发魔数拒绝）
  const MB = 1024 * 1024;
  const head = Buffer.from('%PDF-1.4\n', 'utf8');
  const chunk = Buffer.alloc(1 * MB, 0x61);
  const parts = [head];
  for (let i = 0; i < 110; i++) parts.push(chunk);
  const big = Buffer.concat(parts);
  const t0 = Date.now();
  const up = await uploadFile(token, 'big110.pdf', big);
  const bigFileId = up.body && up.body.data && up.body.data.id;
  results.push({
    name: 'case2 U2 上传层大小：110MB(合法PDF头)上传',
    expect: '200 成功(上传层不限 100MB, 硬闸=Spring 500MB)',
    got: `http=${up.status} code=${up.body && up.body.code} fileId=${bigFileId} ${((Date.now() - t0) / 1000).toFixed(1)}s`,
    pass: up.status === 200 && up.body && up.body.code === 200 && !!bigFileId,
  });
  return bigFileId;
}

async function runCase2b(token, bookId, bigFileId, results) {
  // ---------- case2b: U2 挂载层——110MB 挂载被拒 ----------
  if (!bigFileId) return;
  // 预期：400「不能超过 100MB」
  const at = await api(token, 'POST', `/fabushi/books/${bookId}/pdf`, { fileId: bigFileId });
  const rejected = at.status === 400 && at.body && at.body.code === 400 && (at.body.message || '').includes('100MB');
  results.push({
    name: 'case2b U2 挂载层大小：110MB 挂载',
    expect: '400 拒绝(报错含 100MB)',
    got: `http=${at.status} code=${at.body && at.body.code} msg=${(at.body && at.body.message || '').slice(0, 40)}`,
    pass: rejected,
  });
}

async function runCase3(token, bookId, results) {
  // ---------- case3: 正例护栏——真 PDF 小文件全链 ----------
  // 预期：上传 200 + 挂载 200
  const real = Buffer.from('%PDF-1.4\n1 0 obj\n<<>>\nendobj\ntrailer\n%%EOF', 'utf8');
  const up = await uploadFile(token, 'real.pdf', real);
  const okUp = up.status === 200 && up.body && up.body.code === 200 && up.body.data && up.body.data.id;
  let okAttach = false;
  if (okUp) {
    const at = await api(token, 'POST', `/fabushi/books/${bookId}/pdf`, { fileId: up.body.data.id });
    okAttach = at.status === 200 && at.body && at.body.code === 200;
  }
  results.push({
    name: 'case3 正例护栏：真 PDF 上传+挂载',
    expect: 'upload=200 attach=200',
    got: `upload=${okUp} attach=${okAttach}`,
    pass: okUp && okAttach,
  });
}

async function runCase4(token, results) {
  // ---------- case4: F2 音段数上限——第 10 段放行 / 第 11 段 400 ----------
  // 注: 挂载 fileType 由 addAttachment body 决定, 上传文件本体用合法 PDF 头(与 case3 同构,
  //     不依赖音频魔数; 上传层对非 .pdf 名无魔数闸)
  const post = await api(token, 'POST', '/fabushi/posts', { scopeType: 'heart', title: 'F2限额-音段数', content: 'live case4' });
  const postId = post.body && post.body.data && post.body.data.id;
  if (!postId) throw new Error('case4 setup: create post failed ' + JSON.stringify(post.body).slice(0, 200));
  cleanupPosts.push(postId);
  const up = await uploadFile(token, 'seg.pdf', Buffer.from('%PDF-1.4\n' + 'case4 audio-limit filler\n'.repeat(30), 'utf8'));
  const fileId = up.body && up.body.data && up.body.data.id;
  let ok10 = 0;
  for (let i = 0; i < 10; i++) {
    const at = await api(token, 'POST', `/fabushi/posts/${postId}/attachments`,
      { fileId, fileType: 'audio', durationSeconds: 30 });
    if (at.status === 200 && at.body && at.body.code === 200) ok10++;
  }
  const eleventh = await api(token, 'POST', `/fabushi/posts/${postId}/attachments`,
    { fileId, fileType: 'audio', durationSeconds: 30 });
  results.push({
    name: 'case4 F2 音段数上限: 第10段放行/第11段拒绝',
    expect: '10x200 + 第11段 400「语音已达上限10段」',
    got: `first10=${ok10}/10, 11th: http=${eleventh.status} code=${eleventh.body && eleventh.body.code} msg=${((eleventh.body && eleventh.body.message) || '').slice(0, 30)}`,
    pass: ok10 === 10 && eleventh.status === 400 && ((eleventh.body && eleventh.body.message) || '').includes('10段'),
  });
}

async function runCase5(token, results) {
  // ---------- case5: F2 单段时长——301s 400 / 300s 放行（R1 2026-11-28 起 300s） ----------
  const post = await api(token, 'POST', '/fabushi/posts', { scopeType: 'heart', title: 'F2限额-单段时长', content: 'live case5' });
  const postId = post.body && post.body.data && post.body.data.id;
  if (!postId) throw new Error('case5 setup: create post failed ' + JSON.stringify(post.body).slice(0, 200));
  cleanupPosts.push(postId);
  const up = await uploadFile(token, 'seg301.pdf', Buffer.from('%PDF-1.4\n' + 'case5 duration filler\n'.repeat(30), 'utf8'));
  const fileId = up.body && up.body.data && up.body.data.id;
  const at = await api(token, 'POST', `/fabushi/posts/${postId}/attachments`,
    { fileId, fileType: 'audio', durationSeconds: 301 });
  results.push({
    name: 'case5 F2 音单段时长: 301s 拒绝',
    expect: '400 拒绝(单段语音不得超过5分钟)',
    got: `http=${at.status} code=${at.body && at.body.code} msg=${((at.body && at.body.message) || '').slice(0, 30)}`,
    pass: at.status === 400 && ((at.body && at.body.message) || '').includes('5分钟'),
  });
  const ok = await api(token, 'POST', `/fabushi/posts/${postId}/attachments`,
    { fileId, fileType: 'audio', durationSeconds: 300 });
  results.push({
    name: 'case5b F2 音单段时长: 300s 放行',
    expect: '200(300s 恰好在上限内)',
    got: `http=${ok.status} code=${ok.body && ok.body.code}`,
    pass: ok.status === 200,
  });
}

async function main() {
  const token = await login();
  console.log(`[setup] login ok as ${USER}`);
  const results = [];
  let bookId = null;

  try {
    await runCase1(token, results);
    bookId = await createApprovedBook(token, 'UI回归-上传边界书D');
    const bigFileId = await runCase2(token, results);
    await runCase2b(token, bookId, bigFileId, results);
    await runCase3(token, bookId, results);
    // 【F2 2026-11-19】分型限额 live 用例(实现于本地批, 执行待测试服窗口; 20 提前知会 QC pin 基线 A)
    await runCase4(token, results);
    await runCase5(token, results);
  } finally {
    if (bookId) await api(token, 'DELETE', `/fabushi/books/${bookId}`);
    for (const pid of cleanupPosts) {
      await api(token, 'PUT', `/fabushi/posts/${pid}/offline`, { reason: 'F2 live 用例收尾下架' }).catch(() => {});
    }
  }

  printSummary(results);
}

function printSummary(results) {
  // ---------- 汇总 ----------
  console.log('\n===== 结果 =====');
  for (const r of results) {
    console.log(`${r.pass ? 'PASS' : 'FAIL'} | ${r.name}\n     预期: ${r.expect}\n     实际: ${r.got}`);
  }
  const failed = results.filter((r) => !r.pass).length;
  console.log(`\n总结: ${results.length - failed}/${results.length} 用例符合预期声明`);
  process.exit(failed === 0 ? 0 : 1);
}

main().catch((e) => {
  console.error('ABORT:', e.message);
  process.exit(2);
});
