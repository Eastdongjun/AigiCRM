# 功能受理

## 用户目标

修复文件管理页（/file）上传失败问题。

## 当前行为

- **根因 1（确定性 400）**：el-upload 的 `:data="{ folderId: selectedFolderId }"` 在未选文件夹时把 `null` 序列化成字符串 `"null"` → 后端 `@RequestParam Long folderId` 类型转换失败 → **400 "参数类型不正确：folderId 应为Long"** → 上传失败（curl 复现：folderId=null → 400；folderId= 空 → 200）
- **根因 2（on-success 不触发）**：即使参数修好，el-upload 自动上传的 XHR 返回 200 后 on-success 回调不触发（getFile 找不到内部文件 → handleSuccess 直接 return；element-plus 2.14.1 的 fileList/on-success 交互为已知问题区）→ 无成功提示、弹窗不关、列表不刷新 → 用户感知"上传失败"
- 对照组：激励上传页用自定义 XHR（request.post）上传正常（今天有文件经其上传成功）

## 范围

- file/index.vue 上传弹窗：弃用 el-upload 自动上传，改为**完全自定义**（拖拽区 + 原生 input 多选 + 自管 XHR + 成功/失败明细提示 + 关闭清理）
- folderId 为 null 时不携带该参数

## 不在范围

- 生产部署；后端（无需改动，参数本就正确）

## 来源与事实

- 用户报告："http://111.229.37.220/file 文件上传失败"
- curl 复现 400（folderId=null）；浏览器 XHR 拦截确认 200 但 on-success 不触发
- element-plus 已知问题：#6697（on-success 与 fileList 交互回归）

## 约束

- 测试服；纯前端改动；保持原交互（拖拽/多选/公共私人 scope）

## 初始风险

- 低：自定义上传模式已在激励上传页验证可用
