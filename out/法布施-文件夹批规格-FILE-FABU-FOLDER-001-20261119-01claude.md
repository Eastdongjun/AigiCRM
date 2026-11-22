# 批规格 FILE-FABU-FOLDER-001 · 法布施文件分「书籍/分享心得」两文件夹（实施：01-claude-20）

> 用户 2026-11-19 拍板：**方案 A 真文件夹**，**PC+小程序双端**。原话："书籍放一个文件夹，分享心得放另外一个文件夹"。
> 现状坐实（QC）：法布施空间（scope=fabushi）单一平铺——PC 文件管理 fabushi tab（file/index.vue:44/75 特意隐藏 FolderPanel）与 MP 文件页法布施 tab（pages/files/index.js:449 跳过夹加载）都是无夹列表；物理目录按上传者分（fabushi/{uid}/）。生产现量：3 文件（1 书 PDF + 2 帖子附件，0 孤儿）。
> 归类口径（按引用关系，非上传入口）：被 `biz_fabushi_book.pdf_file_id` 引用 = 书籍；其余 scope=fabushi（帖子附件/未引用）= 分享心得。

## 一、DB 迁移 `V2026.11.19.4__fabushi_file_folders.sql`

1. 两个**系统级文件夹**（biz_file_folder）：「书籍」「分享心得」——归属法布施空间（folderType 方案由实施定：新增 'fabushi' 型或现有型+系统标记，须与 FolderPermissionService/FolderAcl 兼容）；系统夹不可删/不可改名/用户不可在其下建夹（服务端拒绝，前端隐藏入口）
2. 存量回填（幂等 NOT EXISTS 风格，迁移头逆写）：书籍引用 → 书籍夹；其余 → 分享心得夹
3. 测试验证走 `scripts/deploy-db-test.sh`（坑：容器 FLYWAY_ENABLED=true 是无效摆设，迁移不自动跑）

## 二、上传路由（服务端定死，不依赖前端自觉）

| 通道 | 落夹 |
|---|---|
| PC 书目管理「挂 PDF」（book.vue 上传→attachBookPdf） | **书籍** |
| MP 帖子附件·原生 uploadFile(scope=fabushi) | **分享心得** |
| H5 `/api/fabushi/upload/h5`（票 scene=fabushi） | **分享心得** |
| H5 `/api/file/upload/h5`（票 scene=files，若选了法布施目标） | 按票内目标夹 |

- attachPdf 校验加强：scope=fabushi **∧** folder=书籍（不满足 400，报错文案说明应从书目挂载入口上传）
- 实现方式（FileService.upload 默认夹解析 vs fabushi 侧显式传 folderId）由实施定，红线：**归类由服务端按通道/引用判定**

## 三、双端展示

- **PC**：文件管理法布施 tab 显示固定两夹（FolderPanel 的法布施变体或固定夹行）；夹内文件列表/删除（fabushi:file:delete）等行为沿用
- **MP**：文件页法布施 tab 加载并显示两夹（改 index.js:449 的跳过逻辑 + wxml 夹行渲染，样式沿用现有夹组件）
- 夹排序：书籍在前、分享心得在后（sort_order 定死）

## 四、权限/可见性（保持现口径，零放宽）

- 书籍夹：全员可见可下载（电子书本就是给大家读的，与书目库一致）
- 分享心得夹：沿用 fabushi 空间现有可见口径（不做任何放宽）
- 系统夹本身无增删改入口；权限码（fabushi:file:delete / file:*）零变化

## 五、门禁与纪律（照旧）

mvn 全绿+新单测（路由/校验/回填幂等/系统夹保护）；Sonar P0-5 new 全 0；S1192 防复发；typecheck 536 不升；vitest 全绿；契约（若加新端点走刷新流程）；迁移幂等重跑验证。commit→冻结→申报（HEAD+diff+自报门禁+预期指标）。

## 六、QC 验收

1. 元素对账：PC（法布施 tab 两夹+夹内列表）+ MP（法布施 tab 两夹）
2. **路由实测**（测试服）：PC 挂 PDF→书籍 ✓；MP 发帖附件→分享心得 ✓；H5 fabushi 上传→分享心得 ✓；从普通文件入口传文件→不进法布施夹 ✓；attachPdf 对"分享夹里的文件"挂书→400 ✓
3. 存量回填对账：生产 3 文件 1 书/2 附件归位（迁移后 SQL 三查）
4. 系统夹保护：删夹/改名/夹下建夹→服务端拒绝
5. 双端截图 + 门禁自跑

## 七、联动模块设计（用户质询补强 2026-11-19："权限中心呢？日志呢？"）

### 7.1 权限中心（设计=复用，零新增）

- **模型落点**：两系统夹 folderType=`fabushi`（复用现有空间型）。FolderPermissionService 现行兜底（FolderPermissionService.java:220 无夹文件以 fileScope 充当 folderType）在回填后升级为"真夹锚定"——现有 fabushi 型授权（targetFolderId=NULL=全空间）**继续覆盖两夹**，语义零破坏
- **细粒度预留**：biz_folder_acl 支持逐夹定向（书籍夹/分享夹可分别配 ACL），本批不建新 ACL 行；未来"仅书籍夹可下载"等需求=加一行 ACL，**非代码改动**
- **权限中心 UI**：PermScopeManager「文件夹权限」面板天然覆盖，本批零 UI 改动；权限码**零新增**（权限绑定覆盖契约 94 码不动，`check-auth-menu-tree-contract.sh` 照跑）
- **红字**：书籍夹"全员可读"的实现=沿用 fabushi 型现有可见性默认（type default 行），**不发明新权限码**

### 7.2 日志（现状=文件模块无操作日志，本批处理如下）

- **现状坐实**：FileController/文件夹操作全链**无 @AigicrmLog**（存量空白，非本批引入）；法布施侧仅书目审核/挂 PDF 服务层有日志
- **本批新增日志点（最小集）**：①attachPdf（挂书,含"落书籍夹"事实）②H5 上传消费 consumeUpload/consumeFileUpload（permitAll 端点必须留痕：票内 uid/scene/落夹）③法布施文件的删除（fabushi:file:delete 路径）——module=法布施，action 用现有枚举风格，targetType=file，targetId=文件 id+夹名入参
- **不产生伪日志**：迁移回填不动业务日志表（无操作人），迁移头注释记录口径（惯例）
- **存量空白**：文件模块（公共/私人/四项服务）全量操作日志缺口**登记债务台账**，不塞进本批（避免批膨胀）

### 7.3 其他联动盘点（本批不动，逐项核过）

- 配额：fabushi 上传已计个人配额，分夹不影响
- 病毒扫描 FileScanService：挂 uploadStorage 后不变
- 分享（file:share/分享给我 tab）：法布施文件沿用现口径，分夹不引入新共享面
- 文件版本/替换 replaceFile：fabushi 空间不涉
- 存储统计（容量条）：口径不变
- API 契约：若加夹列表端点走契约刷新并申报；权限契约 94 码零变化
