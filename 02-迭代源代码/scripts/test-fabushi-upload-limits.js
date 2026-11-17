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
    categoryCode: 'jiaxun', title, author: '验收员', editionType: 'original',
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

async function main() {
  const token = await login();
  console.log(`[setup] login ok as ${USER}`);
  const results = [];
  let bookId = null;

  try {
    // ---------- case1: U1 魔数层——非 PDF 内容伪装 .pdf ----------
    {
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

    bookId = await createApprovedBook(token, 'UI回归-上传边界书D');

    // ---------- case2: U2 上传层——110MB 合法头文件 ----------
    let bigFileId = null;
    {
      // 预期：上传 200（上传层无 100MB 限制；合法头不触发魔数拒绝）
      const MB = 1024 * 1024;
      const head = Buffer.from('%PDF-1.4\n', 'utf8');
      const chunk = Buffer.alloc(1 * MB, 0x61);
      const parts = [head];
      for (let i = 0; i < 110; i++) parts.push(chunk);
      const big = Buffer.concat(parts);
      const t0 = Date.now();
      const up = await uploadFile(token, 'big110.pdf', big);
      bigFileId = up.body && up.body.data && up.body.data.id;
      results.push({
        name: 'case2 U2 上传层大小：110MB(合法PDF头)上传',
        expect: '200 成功(上传层不限 100MB, 硬闸=Spring 500MB)',
        got: `http=${up.status} code=${up.body && up.body.code} fileId=${bigFileId} ${((Date.now() - t0) / 1000).toFixed(1)}s`,
        pass: up.status === 200 && up.body && up.body.code === 200 && !!bigFileId,
      });
    }

    // ---------- case2b: U2 挂载层——110MB 挂载被拒 ----------
    if (bigFileId) {
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

    // ---------- case3: 正例护栏——真 PDF 小文件全链 ----------
    {
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
  } finally {
    if (bookId) await api(token, 'DELETE', `/fabushi/books/${bookId}`);
  }

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
