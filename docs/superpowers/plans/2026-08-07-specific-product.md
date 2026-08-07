# 激励记录"具体产品"字段 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在激励记录中新增"具体产品"可选文本字段（仅产品试点 trial 分类显示与可编辑），并在激励上传、激励查询详情、客户卡片激励关联三处展示，接入关键字搜索。

**Architecture:** 新增 `biz_incentive_record.specific_product` 独立列（MyBatis-Plus 自动映射）。后端只动实体 + updateRecord 字段合并 + getCustomerRecords 搜索/结果 map 三处；前端 index.vue（trial 条件渲染文本框 + 创建/编辑）、search.vue（详情展示）、customer/detail.vue（条目展示）三处。积分计算（calcScore 只看文件槽位）不受影响。

**Tech Stack:** Spring Boot / MyBatis-Plus / Vue 3 / Element Plus / MySQL 8.0

## Global Constraints

- 字段名规范：Java `specificProduct` ↔ DB `specific_product`（MP 驼峰转下划线自动映射）
- 仅产品试点（顶层 code='trial'）显示；plan/exchange/visit 三个分类不显示
- 可选、非必填；空值存 NULL，前端有值才显示
- 不参与积分/统计；不进列表列；关键字搜索需命中
- 迁移执行必须带库名 + `--default-character-set=utf8mb4`（沿用规范）
- 生产部署前必须备份；部署顺序：迁移 SQL → 后端 jar → 前端 dist
- 参考设计文档：`docs/superpowers/specs/2026-08-07-specific-product-design.md`

---

### Task 1: 数据库迁移脚本

**Files:**
- Create: `sql/migration/20260807/specific-product-column.sql`

**Interfaces:**
- Produces: SQL 迁移文件（后续 Task 7/8 部署时执行）

- [ ] **Step 1: 写迁移脚本**

```sql
-- 激励记录"具体产品"字段（产品试点专属，可选文本）
-- 执行：mysql -uroot -pXXXX --default-character-set=utf8mb4 ycmn < specific-product-column.sql
ALTER TABLE biz_incentive_record
  ADD COLUMN specific_product varchar(200) NULL AFTER related_products;
```

- [ ] **Step 2: 语法自检**

Run: `mysql -uroot -pXXXX ycmn < sql/migration/20260807/specific-product-column.sql`（测试库）
Expected: 无报错，`SHOW COLUMNS FROM biz_incentive_record` 出现 `specific_product varchar(200) NULL`

- [ ] **Step 3: Commit**

```bash
git add sql/migration/20260807/specific-product-column.sql
git commit -m "feat(db): 激励记录新增 specific_product 列(产品试点专属可选文本)"
```

---

### Task 2: 后端实体 + updateRecord 合并字段

**Files:**
- Modify: `ycmn-backend/src/main/java/com/ycmn/module/incentive/entity/BizIncentiveRecord.java`（约 19 行后）
- Modify: `ycmn-backend/src/main/java/com/ycmn/module/incentive/service/IncentiveService.java:231-251`
- Test: `ycmn-backend/src/test/java/com/ycmn/module/incentive/service/IncentiveSpecificProductTest.java`（新建）

**Interfaces:**
- Consumes: `BizIncentiveRecord` 实体已有构造（无参），`IncentiveService.updateRecord(Long id, BizIncentiveRecord update)`
- Produces: `BizIncentiveRecord.getSpecificProduct()/setSpecificProduct(String)`；`updateRecord` 支持保存/清空具体产品

- [ ] **Step 1: 写失败测试**

```java
package com.ycmn.module.incentive.service;

import com.ycmn.module.incentive.entity.BizIncentiveRecord;
import com.ycmn.module.incentive.mapper.BizIncentiveCategoryMapper;
import com.ycmn.module.incentive.mapper.BizIncentiveRecordMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class IncentiveSpecificProductTest {

    private IncentiveService newService(BizIncentiveRecordMapper recMapper, BizIncentiveCategoryMapper catMapper) {
        return new IncentiveService(
                catMapper, recMapper,
                mock(com.ycmn.module.system.mapper.SysUserMapper.class),
                mock(com.ycmn.module.file.mapper.BizFileMapper.class),
                mock(com.ycmn.module.customer.mapper.BizCustomerMapper.class),
                mock(com.ycmn.module.customer.mapper.BizCustomerShareMapper.class),
                mock(com.ycmn.module.system.mapper.SysDeptMapper.class),
                mock(com.ycmn.module.system.mapper.SysOrgMapper.class),
                mock(org.springframework.jdbc.core.JdbcTemplate.class),
                mock(com.ycmn.common.scope.OrgScopeResolver.class));
    }

    @Test
    void updateRecordMergesSpecificProduct() {
        BizIncentiveRecordMapper recMapper = mock(BizIncentiveRecordMapper.class);
        BizIncentiveCategoryMapper catMapper = mock(BizIncentiveCategoryMapper.class);

        BizIncentiveRecord existing = new BizIncentiveRecord();
        existing.setId(10L);
        existing.setCategoryId(1L);
        existing.setFilePreId("[1]");
        existing.setRemark("old");
        when(recMapper.selectById(10L)).thenReturn(existing);
        when(catMapper.selectById(eq(1L))).thenReturn(null); // 积分 base=0，不干扰

        IncentiveService svc = newService(recMapper, catMapper);
        BizIncentiveRecord update = new BizIncentiveRecord();
        update.setSpecificProduct("XX密封圈");

        svc.updateRecord(10L, update);

        ArgumentCaptor<BizIncentiveRecord> cap = ArgumentCaptor.forClass(BizIncentiveRecord.class);
        verify(recMapper).updateById(cap.capture());
        assertEquals("XX密封圈", cap.getValue().getSpecificProduct(), "具体产品应被合并保存");
        assertEquals("[1]", cap.getValue().getFilePreId(), "其他字段不受影响");
        assertEquals("old", cap.getValue().getRemark(), "其他字段不受影响");
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn test -pl . -Dtest=IncentiveSpecificProductTest -DskipTests=false`（在 ycmn-backend 下）
Expected: 编译失败（`getSpecificProduct`/`setSpecificProduct` 不存在）

- [ ] **Step 3: 实体加字段**

`BizIncentiveRecord.java` 的 `relatedProducts` 字段后加：

```java
    private String specificProduct;   // 具体产品（可选，仅 trial 分类使用）
```

- [ ] **Step 4: updateRecord 加合并行**

`IncentiveService.java` updateRecord 内 `remark` 合并行后加：

```java
        if (update.getSpecificProduct() != null) existing.setSpecificProduct(update.getSpecificProduct());
```

- [ ] **Step 5: 运行测试确认通过**

Run: `mvn test -Dtest=IncentiveSpecificProductTest`
Expected: PASS（updateRecordMergesSpecificProduct 通过）

- [ ] **Step 6: Commit**

```bash
git add ycmn-backend/src/main/java/com/ycmn/module/incentive/entity/BizIncentiveRecord.java ycmn-backend/src/main/java/com/ycmn/module/incentive/service/IncentiveService.java ycmn-backend/src/test/java/com/ycmn/module/incentive/service/IncentiveSpecificProductTest.java
git commit -m "feat: 激励记录支持具体产品字段(实体+updateRecord合并+测试)"
```

---

### Task 3: 后端 getCustomerRecords 搜索接入 + 结果 map

**Files:**
- Modify: `ycmn-backend/src/main/java/com/ycmn/module/incentive/service/IncentiveService.java:807` 与 `:838-852`
- Test: `ycmn-backend/src/test/java/com/ycmn/module/incentive/service/IncentiveSpecificProductTest.java`（追加）

**Interfaces:**
- Consumes: `getCustomerRecords(industry, mainCategoryId, customerName, salesperson, startDate, endDate, orgIds, devType, product, keyword)`
- Produces: 关键字匹配相关产品 OR 具体产品；返回 map 含 `specificProduct` 键

- [ ] **Step 1: 写失败测试（追加到 IncentiveSpecificProductTest）**

```java
    @Test
    void getCustomerRecordsKeywordMatchesSpecificProduct() {
        BizIncentiveRecordMapper recMapper = mock(BizIncentiveRecordMapper.class);
        BizIncentiveCategoryMapper catMapper = mock(BizIncentiveCategoryMapper.class);
        when(recMapper.selectList(any())).thenReturn(List.of());
        IncentiveService svc = newService(recMapper, catMapper);

        try (MockedStatic<com.ycmn.security.SecurityUtils> m = mockStatic(com.ycmn.security.SecurityUtils.class)) {
            m.when(com.ycmn.security.SecurityUtils::isAdmin).thenReturn(true);
            m.when(com.ycmn.security.SecurityUtils::getCurrentUserId).thenReturn(1L);

            // keyword 命中"具体产品"场景（无客户名）
            svc.getCustomerRecords(null, null, null, null, null, null, null, null, null, "XX密封圈");
        }

        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper> cap =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class);
        verify(recMapper).selectList(cap.capture());
        String sql = cap.getValue().getExpression().getSqlSegment().toLowerCase();
        org.junit.jupiter.api.Assertions.assertTrue(
                sql.contains("specific_product"), "关键字搜索应包含 specific_product 条件，实际: " + sql);
    }
```

（需在测试文件顶部加 `import org.mockito.MockedStatic;`）

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn test -Dtest=IncentiveSpecificProductTest`
Expected: FAIL（sql 不包含 specific_product）

- [ ] **Step 3: getCustomerRecords 关键字搜索接入**

`IncentiveService.java` 关键字分支（约 838-852 行）三处 `relatedProducts` 匹配改为 OR specific_product：

```java
        if (keyword != null && !keyword.isBlank()) {
            if (customerName != null && !customerName.isBlank()) {
                List<BizCustomer> matched = customerMapper.selectList(
                    new LambdaQueryWrapper<BizCustomer>().like(BizCustomer::getCustomerName, customerName)
                );
                if (matched.isEmpty()) {
                    wrapper.and(w -> w.like(BizIncentiveRecord::getRelatedProducts, keyword)
                            .or().like(BizIncentiveRecord::getSpecificProduct, keyword));
                } else {
                    List<Long> cids = matched.stream().map(BizCustomer::getId).toList();
                    wrapper.and(w -> w.in(BizIncentiveRecord::getCustomerId, cids)
                        .or().like(BizIncentiveRecord::getRelatedProducts, keyword)
                        .or().like(BizIncentiveRecord::getSpecificProduct, keyword));
                }
            } else {
                wrapper.and(w -> w.like(BizIncentiveRecord::getRelatedProducts, keyword)
                        .or().like(BizIncentiveRecord::getSpecificProduct, keyword));
            }
        }
```

- [ ] **Step 4: getCustomerRecords 结果 map 加 specificProduct**

`IncentiveService.java` 约 807 行 `m.put("relatedProducts", ...)` 后加：

```java
            m.put("specificProduct", r.getSpecificProduct());
```

- [ ] **Step 5: 运行测试确认通过**

Run: `mvn test -Dtest=IncentiveSpecificProductTest`
Expected: PASS（两个测试均通过）

- [ ] **Step 6: 跑全量后端测试回归**

Run: `mvn test`
Expected: 全绿（202+2 测试通过，无新增失败）

- [ ] **Step 7: Commit**

```bash
git add ycmn-backend/src/main/java/com/ycmn/module/incentive/service/IncentiveService.java ycmn-backend/src/test/java/com/ycmn/module/incentive/service/IncentiveSpecificProductTest.java
git commit -m "feat: 激励关键字搜索接入具体产品 + 客户卡片结果带出 specificProduct"
```

---

### Task 4: 前端激励上传页 index.vue（trial 专属文本框）

**Files:**
- Modify: `ycmn-ui/src/views/incentive/index.vue`
  - 相关产品 ref 区（约 388 行）
  - 相关产品字段块（约 169-177 行）下方
  - 打开弹窗重置（约 619 行）
  - 编辑回填（约 668 行）
  - 创建/追加 payload（约 837、852 行）

**Interfaces:**
- Consumes: `categoryCodeOf(pendingChild.value)`（已有，上溯顶层 code）
- Produces: 提交 payload 含 `specificProduct`；编辑回填 `specificProduct`

- [ ] **Step 1: 加 ref 与重置**

在 `relatedProducts` ref 附近加：

```js
const specificProduct = ref('')
```

打开弹窗重置处（`relatedProducts.value = ''` 附近，约 619 行）加：

```js
  specificProduct.value = ''
```

- [ ] **Step 2: 相关产品下方加文本框（trial 专属）**

`index.vue` 相关产品字段块（177 行 `</div></div>` 之后、`</div>` 基本区结束前）加：

```html
          <div v-if="categoryCodeOf(pendingChild.value) === 'trial'" class="dlg-field">
            <label>具体产品 <span class="dlg-opt">选填</span></label>
            <el-input v-model="specificProduct" placeholder="填写具体产品名称/型号，选填" size="large" maxlength="200" show-word-limit clearable />
          </div>
```

- [ ] **Step 3: 编辑回填**

在编辑回填处（`relatedProducts.value = row.relatedProducts || ''` 附近，约 668 行）加：

```js
  specificProduct.value = row.specificProduct || ''
```

- [ ] **Step 4: 创建/追加 payload 加 specificProduct**

两处 `addRecord({...})`（约 837 与 852 行）的 `relatedProducts: ...` 行后各加：

```js
        specificProduct: specificProduct.value || null,
```

- [ ] **Step 5: UI 样式**

在 `index.vue` `<style>` 中为选填标签加样式（若无 `.dlg-opt`）：

```css
.dlg-opt { font-size:12px; font-weight:400; color:#a8abb2; margin-left:4px }
```

- [ ] **Step 6: 构建验证**

Run: `cd ycmn-ui && npm run build`
Expected: 构建成功，无报错

- [ ] **Step 7: Commit**

```bash
git add ycmn-ui/src/views/incentive/index.vue
git commit -m "feat(ui): 激励上传产品试点分类新增具体产品文本框(选填)"
```

---

### Task 5: 前端激励查询页 search.vue（详情展示）

**Files:**
- Modify: `ycmn-ui/src/views/incentive/search.vue`（详情时间线 dtl-info-line 区，约 130-150 行）

**Interfaces:**
- Consumes: `record.specificProduct`（customer-records 接口返回）

- [ ] **Step 1: 详情时间线加具体产品展示**

在 `dtl-info-line` 的 `r.relatedProducts` 展示（约 141 行）后加：

```html
                <span v-if="r.specificProduct">· {{ r.specificProduct }}</span>
```

- [ ] **Step 2: 构建验证**

Run: `cd ycmn-ui && npm run build`
Expected: 构建成功

- [ ] **Step 3: Commit**

```bash
git add ycmn-ui/src/views/incentive/search.vue
git commit -m "feat(ui): 激励查询详情展示具体产品"
```

---

### Task 6: 前端客户卡片 customer/detail.vue（激励关联展示）

**Files:**
- Modify: `ycmn-ui/src/views/customer/detail.vue`（激励条目 `.incentive-item` 区，约 277-295 行）

**Interfaces:**
- Consumes: `record.specificProduct`（customer-records 接口返回，Task 3 已带出）

- [ ] **Step 1: 激励条目加具体产品展示**

在 `.inc-meta` 后、`.inc-files` 前（约 286 行后）加：

```html
            <div v-if="r.specificProduct" class="inc-meta" style="margin-top:2px">
              <span style="color:#909399">具体产品：</span><span>{{ r.specificProduct }}</span>
            </div>
```

- [ ] **Step 2: 构建验证**

Run: `cd ycmn-ui && npm run build`
Expected: 构建成功

- [ ] **Step 3: Commit**

```bash
git add ycmn-ui/src/views/customer/detail.vue
git commit -m "feat(ui): 客户卡片激励关联展示具体产品"
```

---

### Task 7: 测试环境部署验证（DoD）

**Files:**
- 使用：Task 1 迁移脚本、Task 2/3 后端 jar、Task 4/5/6 前端 dist

**Interfaces:**
- 依赖：Task 1-6 全部完成

- [ ] **Step 1: 测试库执行迁移**

Run（测试服务器 111.229.37.220）:
```bash
# 备份后执行
docker exec shared-mysql mysqldump -uroot -p<redacted> --single-transaction --default-character-set=utf8mb4 ycmn > backup-pre-specific.sql
cat sql/migration/20260807/specific-product-column.sql | sshpass ssh ubuntu@111.229.37.220 "docker exec -i shared-mysql mysql -uroot -p<redacted> --default-character-set=utf8mb4 ycmn"
```
Expected: `SHOW COLUMNS FROM biz_incentive_record` 含 `specific_product`

- [ ] **Step 2: 构建并部署后端**

Run: `mvn package -DskipTests` → 上传 jar → 重建镜像 → 替换容器（沿用 `~/.ycmn-deploy.env` 的 JWT_SECRET，勿硬编码）
Expected: 健康检查 200，登录正常

- [ ] **Step 3: 构建并部署前端**

Run: `cd ycmn-ui && npm run build` → dist 打包 → `docker cp` 到 ycmn-frontend（完整替换，勿增量）
Expected: 首页 200，版本号更新

- [ ] **Step 4: 浏览器 DoD 验证**

逐项（用测试服 admin/admin123 + 销售员账号）：
1. trial 分类上传：具体产品可填可不填，保存后 DB 有值
2. plan/exchange/visit 三类：**不显示**该文本框
3. 编辑：回填 → 修改 → 保存 → 清空，全部生效
4. 激励查询详情：显示具体产品
5. 客户卡片激励关联：显示具体产品
6. 关键字搜索"具体产品名"能命中
7. 积分值与加字段前一致

Expected: 7 项全部通过

- [ ] **Step 5: 推送 GitHub**

```bash
git push origin main
```

---

### Task 8: 生产部署（需用户确认后执行）

**Files:**
- 依赖：Task 7 测试环境验证通过

**Interfaces:**
- 输入：用户确认部署

- [ ] **Step 1: 生产备份（含全量备份已做 20260807-1556）**

Run: 确认 `/Users/donglai/正式系统数据备份/01-客户管理系统/prod-20260807-1556/` 已存在；再 dump 一次最新 DB
Expected: 备份完成

- [ ] **Step 2: 生产执行迁移**

Run: 带库名 + utf8mb4 执行 `specific-product-column.sql`
Expected: `SHOW COLUMNS` 含 `specific_product`

- [ ] **Step 3: 部署后端 jar + 前端 dist**

Run: deploy-backend.sh / deploy-frontend.sh（沿用规范；注意生产落后 15+ commit，本次一并带上前序功能）
Expected: 健康检查 200，浏览器登录正常

- [ ] **Step 4: 生产浏览器 DoD 验证**

Expected: 7 项验收通过（用生产 admin + 一个销售员账号）

- [ ] **Step 5: 同步 git + 更新记忆**

```bash
git push origin main
```
更新 `YCMN-客户管理-完整记忆.md`：记录新字段、部署时间、遗留项
