# 实现记录

## 变更文件

- index.vue：handleFileSelected 安装槽 `[...installFiles[currentSlot], ...files].slice(0, MAX_FILES)`（原 filter _existing）

## 实现决策

- 与 track 分支一致；编辑回填（_existing 标记）不受影响

## 新增或更新的测试

- 浏览器两次 fileChooser 注入：修复前第二次替换第一次（1 文件），修复后 2 文件共存

## 实际执行命令

- npm build → 部署 → playwright 两次选择验证

## 失败与纠正

- 无

## 未完成与残余风险

- 生产未部署
