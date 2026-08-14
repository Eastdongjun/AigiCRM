# 实现记录

## 变更文件

- file/index.vue（REQ-001~004）：上传弹窗 el-upload → 自定义拖拽区+input 多选+自管 XHR；folderId null 不携带；成功/失败明细提示；@close 清理

## 实现决策

- 与计划一致；尝试过 v-model:file-list + :http-request 两方案均未解决 on-success 不触发 → 最终自定义上传（可靠）

## 新增或更新的测试

- 浏览器 E2E：真实 fileChooser 上传成功（提示/关弹窗/列表刷新）；DataTransfer 多文件注入

## 实际执行命令

- npm run build → 部署容器；curl folderId 三态复现（null→400 / 空→200 / 无→200）

## 失败与纠正

- 初次构建失败：formatSize 重复声明（文件已有）→ 删除新增定义
- v-model:file-list 方案无效（on-success 仍不触发）→ 换 :http-request 方案 → 仍无效 → 最终自定义上传

## 未完成与残余风险

- 多文件部分失败未浏览器实测；生产未部署
