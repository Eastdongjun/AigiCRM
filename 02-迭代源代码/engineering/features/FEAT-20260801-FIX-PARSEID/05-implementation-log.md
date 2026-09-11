# 实现日志
## 变更文件
- aigicrm-ui/src/views/customer/detail.vue(parseId + getFiles)
- aigicrm-ui/vitest.setup.js(localStorage stub)


## 实现决策
- parseId 复用 search.vue/customer.vue 同款实现(数组取 a[0])
- 部署走 docker cp(前端容器无 bind mount)

## 新增或更新的测试
- 未新增单测;修复 vitest.setup.js(localStorage stub)使既有 8 用例通过

## 实际执行命令
- npm run build ✓(detail-C4-iKj8e.js,含 parseId 已验证)
- npm test ✓ 8/8(stash 验证失败为既有缺陷后修复 setup)
- 部署:tar → scp → docker cp → restart aigicrm-frontend ✓

## 失败与纠正
- 测试 8 连挂:既有 vitest 环境缺陷(jsdom 无 localStorage)→ 修复 setup.js
- 截图工具超时:环境故障,改用恢复后的 playwright 截图保存证据

## 未完成与残余风险
- 生产部署待授权;多文件仅取第一个;vitest 修复仅恢复既有测试
