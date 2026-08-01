# HANDOFF — FEAT-20260801-FILE-UPLOAD-FIX（已完成）

> 文件上传失败修复完成：双根因（folderId null 400 + el-upload on-success 不触发）→ 自定义上传。AC-001~004 全 PASS，测试服已部署。

## 当前状态（2026-08-01 21:35）

- 上传弹窗改为自定义：拖拽/多选/待传列表/开始上传/成功失败明细提示
- 关键认知：**el-upload 的 on-success 在此环境不触发**（element-plus 2.14.1 getFile 拦截，fileList 交互已知问题 #6697）——新功能避免依赖 el-upload 自动上传回调链
- 生产未部署

## 残余风险（待用户确认）

1. 生产未部署（需授权）
2. 用户浏览器需强刷（旧 chunk 缓存）
