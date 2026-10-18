#!/usr/bin/env python3
# 数据字典 markdown 生成(由 gen-data-dictionary.sh 调用,读取 /tmp/dict-*.txt)
import os, sys, datetime, tempfile

def fix_mojibake(txt):
    # 修复双重编码乱码(latin1/cp1252 混合误读的 UTF-8): 返回 (状态, 文本)
    # 状态: 0=正常 1=已还原 2=不可逆损坏
    if not txt or txt.isascii():
        return 0, txt
    out = bytearray()
    for ch in txt:
        o = ord(ch)
        if o < 256:
            out.append(o)
        else:
            try:
                out.append(ch.encode('cp1252')[0])
            except Exception:
                return 0, txt  # 正常中文(无法按单字节编码)
    try:
        fixed = bytes(out).decode('utf-8')
        if '�' not in fixed and fixed != txt:
            return 1, fixed
        return 2, txt
    except Exception:
        return 2, txt

def _allowed_roots(allow_tempdir=False):
    repo_root = os.path.realpath(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    roots = [repo_root]
    if allow_tempdir:
        roots.append(os.path.realpath(tempfile.gettempdir()))
        roots.append(os.path.realpath('/tmp'))
    return roots

def guard_repo_path(path, what, allow_tempdir=False):
    """生成器只允许写项目目录(或系统临时目录, 供 check-doc-freshness 临时产物用)内的路径。
    先 abspath 归一化 ..，再 realpath 解析符号链接，最后做根前缀校验；输出父目录在
    校验通过后才创建，创建后再次 realpath 复核，拒绝越仓/绝对路径逃逸(2026-10-17 加固)。"""
    if not path or '\x00' in path:
        sys.exit('拒绝: %s 路径为空或包含 NUL 字节' % what)
    raw = os.path.abspath(path)
    p = os.path.realpath(raw)
    roots = _allowed_roots(allow_tempdir)
    allowed = any(p == r or p.startswith(r + os.sep) for r in roots)
    if not allowed:
        sys.exit('拒绝: %s 路径必须在项目目录(或系统临时目录)内(%s): %s' % (what, roots[0], p))
    parent = os.path.dirname(p) or '.'
    try:
        os.makedirs(parent, exist_ok=True)
    except OSError as exc:
        sys.exit('拒绝: %s 父目录无法创建(%s): %s' % (what, exc, parent))
    p = os.path.realpath(raw)
    if not any(p == r or p.startswith(r + os.sep) for r in roots):
        sys.exit('拒绝: %s 路径解析后逃逸允许根(%s): %s' % (what, roots[0], p))
    return p

def write_text(path, text, what='输出文件'):
    """落盘前紧邻调用 guard_repo_path，避免"校验一次后另行 open"的路径穿越/TOCTOU。"""
    p = guard_repo_path(path, what, allow_tempdir=True)
    if os.path.isdir(p):
        sys.exit('拒绝: %s 指向目录而非文件: %s' % (what, p))
    with open(p, 'w', encoding='utf-8') as f:
        f.write(text)

def main(out_path, host_label):
    out_path = guard_repo_path(out_path, '输出文件', allow_tempdir=True)
    tables = {}
    for line in open('/tmp/dict-tables.txt', encoding='utf-8'):
        p = line.rstrip('\n').split('\t')
        if len(p) >= 2:
            tables[p[0]] = p[1]

    cols = {}
    for line in open('/tmp/dict-cols.txt', encoding='utf-8'):
        p = line.rstrip('\n').split('\t')
        if len(p) >= 7:
            cols.setdefault(p[0], []).append({
                'name': p[1], 'type': p[2], 'null': p[3],
                'default': (p[4] or ''), 'key': p[5], 'comment': (p[6] or '')
            })

    L = []
    L.append("# AIGICRM 数据字典(自动生成)")
    L.append("")
    L.append("> 生成时间: %s | 来源: %s MySQL information_schema" % (datetime.date.today(), host_label))
    L.append("> 共 %d 张表。本文件由 scripts/gen-data-dictionary.sh 自动生成,**禁止手改**;库结构变更后重新运行脚本。" % len(tables))
    L.append("")
    for t in sorted(tables):
        st, comment = fix_mojibake(tables[t] or '')
        if st == 2:
            comment = comment + " ⚠️[注释已损坏]"
        L.append("## %s %s" % (t, comment))
        L.append("")
        L.append("| 字段 | 类型 | 可空 | 默认 | 键 | 说明 |")
        L.append("|---|---|---|---|---|---|")
        for c in cols.get(t, []):
            default = c['default'] if c['default'] != '' else ''
            st, cmt = fix_mojibake(c['comment'])
            if st == 2:
                cmt = cmt + " ⚠️[注释已损坏]"
            L.append("| `%s` | %s | %s | %s | %s | %s |" % (
                c['name'], c['type'], c['null'], default, c['key'], cmt))
        L.append("")

    write_text(out_path, "\n".join(L) + "\n")
    print("生成完成: %s (%d 张表)" % (out_path, len(tables)))

if __name__ == '__main__':
    if len(sys.argv) != 3:
        sys.exit('用法: gen-dict.py <输出md> <来源标签>')
    main(sys.argv[1], sys.argv[2])
