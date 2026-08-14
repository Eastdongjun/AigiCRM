#!/usr/bin/env python3
# 数据字典 markdown 生成(由 gen-data-dictionary.sh 调用,读取 /tmp/dict-*.txt)
import sys, datetime

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

def main(out_path, host_label):
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
    L.append("# YCMN 数据字典(自动生成)")
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

    with open(out_path, 'w', encoding='utf-8') as f:
        f.write("\n".join(L) + "\n")
    print("生成完成: %s (%d 张表)" % (out_path, len(tables)))

if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
