#!/usr/bin/env python3
"""前端 API 路径契约：禁止在 axios 实例上重复写 /api 前缀。

背景（2026-11-17 生产缺陷）：axios 实例 baseURL='/api'，页面写 request.post('/api/file/upload')
实际请求变成 /api/api/file/upload → 后端 404「请求的地址不存在」（法布施挂载电子书/发布附件）。
单测/构建/后端接口测试都发现不了：字符串只在运行时拼 URL，mock 的 request 不校验路径。

判定：aigicrm-ui/src 下（排除 *.spec.js）出现
  request.get|post|put|delete|patch('/api/...')   或
  request({ url: '/api/...' })
即 FAIL，输出 file:line。
负例自证：--self-test 在临时目录放一条违规，必须检出。
"""
from __future__ import annotations
import re
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'aigicrm-ui' / 'src'

PATTERNS = [
    re.compile(r"request\s*\.\s*(?:get|post|put|delete|patch)\s*\(\s*[`'\"]/api/"),
    re.compile(r"request\s*\(\s*\{[^}]*?url\s*:\s*[`'\"]/api/"),
]


def scan(root: Path) -> list[str]:
    """返回违规行列表（file:line: content）。只读文本，不执行代码。"""
    hits: list[str] = []
    for path in sorted(root.rglob('*')):
        if path.suffix not in ('.js', '.vue') or '.spec.' in path.name:
            continue
        try:
            text = path.read_text(encoding='utf-8')
        except (UnicodeDecodeError, OSError):
            continue
        for idx, line in enumerate(text.splitlines(), 1):
            if any(p.search(line) for p in PATTERNS):
                hits.append(f'{path.relative_to(root)}:{idx}: {line.strip()[:160]}')
    return hits


def self_test() -> int:
    with tempfile.TemporaryDirectory() as tmp:
        bad = Path(tmp) / 'bad.vue'
        bad.write_text(
            "request.post('/api/file/upload', data)\n"
            "request({ url: '/api/file/upload' })\n",
            encoding='utf-8',
        )
        hits = scan(Path(tmp))
        if len(hits) != 2:
            print(f'FAIL: 负例自证未检出（命中 {len(hits)}，期望 2）', file=sys.stderr)
            return 1
        good = Path(tmp) / 'good.vue'
        good.parent.mkdir(exist_ok=True)
        good.write_text("request.post('/file/upload', data)\n", encoding='utf-8')
        if scan(Path(tmp)) != hits:  # good.vue 不应新增命中
            print('FAIL: 负例自证误报（合法路径被检出）', file=sys.stderr)
            return 1
        print('PASS: 负例自证（违规必红、合法不误报）')
        return 0


def main() -> int:
    if '--self-test' in sys.argv:
        return self_test()
    hits = scan(SOURCE)
    if hits:
        print(f'FAIL: 前端 axios 请求重复 /api 前缀（baseURL 已是 /api）{len(hits)} 处：')
        for h in hits:
            print(' ', h)
        return 1
    print('PASS: 前端 API 路径前缀契约（无 request.*("/api/...") 重复前缀）')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
