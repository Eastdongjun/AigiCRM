# FILE-FABU-FOLDER-002 — PC 法布施 tab 两夹不显示（P1 · QC 生产实测发现 2026-11-20）

- 现象：生产 /file 切法布施 tab：文件平铺 6 条，左侧面板只有"全部文件"，**无「书籍」「分享心得」两夹**
- 根因：`aigicrm-ui/src/composables/useFilePage.js:247` switchTab 的 `if (tab !== 'incentive' && tab !== 'shared_to_me' && tab !== 'fabushi') loadFolders()` —— f1247a8f 改了模板 v-if 放行面板，但**漏删这个加载跳过条件**，切 fabushi 时夹树永不加载（allFolders 停留 public 态）
- 后端无问题（getFolderTree('fabushi') 生产 API 直测返回两夹 ✓）；MP 侧同类点均已修 ✓
- 修法：该行去掉 `&& tab !== 'fabushi'`（一行）
- 验证：QC 复测 /file 法布施 tab 面板出现两夹+点夹过滤列表
- 状态：CLOSED 2026-11-20：eb335e8a 一行修复（switchTab 加载门），测试服+生产浏览器双证两夹显示；联动 003 一并修复后终关

---
## 补充（2026-11-20 复测）

- 显示层（eb335e8a 一行修复）测试服浏览器复测 ✓：两夹渲染、文案五查全过
- **挡出第三例同型缺陷 → 另开 FILE-FABU-FOLDER-003**：pageFiles 对 scope=fabushi 跳过 folderId 过滤（点夹 0 行；DB 5/12 在位；API total=0 vs 平铺 17）
- 本工单状态改为：FIXED-PENDING-003（显示已修，联动 003 一起终关）
