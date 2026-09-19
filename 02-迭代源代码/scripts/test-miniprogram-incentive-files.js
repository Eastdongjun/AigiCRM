#!/usr/bin/env node

const assert = require('node:assert/strict')
const {
  buildIncentiveFileGroups,
  buildIncentiveFiles,
  detectType,
  parseIds,
  typeText
} = require('../aigicrm-miniprogram/utils/incentive-files')

assert.deepEqual(parseIds('[101, 102]'), [101, 102])
assert.deepEqual(parseIds('201, 202'), [201, 202])

assert.equal(detectType('现场照片.JPG?token=abc'), 'image')
assert.equal(detectType('附件-88', 'image/jpeg'), 'image')
assert.equal(detectType('验收视频', 'video/mp4'), 'video')
assert.equal(detectType('方案', 'application/pdf'), 'document')
assert.equal(detectType('统计表', 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'), 'document')
assert.equal(typeText('image'), '图片')
assert.equal(typeText('video'), '视频')
assert.equal(typeText('document'), '文档')
assert.equal(typeText('file'), '文件')

const files = buildIncentiveFiles({
  filePreId: '[301,302]',
  filePreName: '安装前照片,现场说明',
  filePreMimeType: 'image/png,application/pdf',
  fileMidId: 303,
  fileMidName: '调试视频',
  fileMidType: 'video/mp4',
  filePostId: '304',
  filePostUrl: 'https://example.com/report.xlsx?download=1',
  fileTrackIds: '305',
  fileTrackName: '跟踪记录.txt',
  fileId: '306',
  fileName: ''
})

assert.equal(files.length, 6)
assert.deepEqual(
  files.map((file) => [file.id, file.tag, file.type, file.typeText]),
  [
    [301, '安装前', 'image', '图片'],
    [302, '安装前', 'document', '文档'],
    [303, '安装中', 'video', '视频'],
    [304, '安装后', 'document', '文档'],
    [305, '跟踪', 'document', '文档'],
    [306, '佐证', 'file', '文件']
  ]
)

const aliasFiles = buildIncentiveFiles({
  filePreIds: [401],
  filePreUrl: 'https://example.com/pre.jpeg',
  fileMidIds: '[402]',
  fileMidName: '',
  fileMidMimeType: 'application/msword',
  filePostIds: '403,404',
  filePostName: '安装后验收.pdf,安装后现场.png',
  fileTrackIds: '[405]',
  fileTrackUrl: 'https://example.com/follow-up.txt',
  fileIds: 406,
  fileUrl: 'https://example.com/evidence.bin'
})

assert.deepEqual(
  aliasFiles.map((file) => [file.id, file.name, file.tag, file.type]),
  [
    [401, '附件-401', '安装前', 'image'],
    [402, '附件-402', '安装中', 'document'],
    [403, '安装后验收.pdf', '安装后', 'document'],
    [404, '安装后现场.png', '安装后', 'image'],
    [405, '附件-405', '跟踪', 'document'],
    [406, '附件-406', '佐证', 'file']
  ]
)

const groups = buildIncentiveFileGroups({
  filePreId: '[501,502]',
  filePreName: '安装前照片.jpg,安装前说明.pdf',
  fileMidId: '503',
  fileMidName: '安装中视频.mp4',
  fileTrackIds: '[504]',
  fileTrackName: '回访记录.docx'
})

assert.deepEqual(
  groups.map((group) => [group.key, group.label, group.files.map((file) => file.id)]),
  [
    ['pre', '安装前', [501, 502]],
    ['mid', '安装中', [503]],
    ['track', '跟踪', [504]]
  ]
)

console.log('PASS: miniprogram incentive file metadata parsing is aligned.')
