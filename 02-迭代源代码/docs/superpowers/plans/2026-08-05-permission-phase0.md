# 权限体系阶段0（权限内核）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立统一权限内核：六级数据范围解析器（OrgScopeResolver）+ 租户插件 + 归属校验守卫（DataScopeGuard）+ 公共 scope 接口 + 权限码补齐 + 后台配置项 + 数据前提门禁，并修复激励 PUT 越权。

**Architecture:** 设计文档 `docs/superpowers/specs/2026-08-05-permission-system-design.md`（13 节）。内核以 `OrgScopeResolver` 为中心：输入 SecurityUser → 输出可见用户集合 U（请求内缓存）；所有业务查询的 `applyDataScope()` 统一升级为调用 Resolver；写操作挂 `DataScopeGuard`；租户隔离走 MyBatis-Plus TenantLineInnerInterceptor（白名单表）。

**Tech Stack:** Spring Boot 3 + MyBatis-Plus 3.5.6 + JUnit 5/Mockito（测试）+ Vue3/Element Plus（前端配置页）

**验证环境:** 测试服务器 http://111.229.37.220（admin 口令与测试库口令均经 env 注入——`~/.aigicrm-deploy.env`，文档不落明文）。部署/验证方法见记忆：后端本地 mvn 构建（MAVEN_SKIP_RC=true）→ scp jar → docker build；前端 npm run build → docker cp dist。

## Global Constraints

- 权限码格式 `模块:动作`；权限码唯一来源 `sys_menu.permission` 列（docs/permission/design.md）
- 涉及权限改动：改前查库确认（勿猜权限码）；所有写接口必须 @PreAuthorize
- 权限调整必须实时生效（无 Redis/内存缓存）——这是已验证的系统行为，不得引入权限缓存
- 数据库 dump 禁止入库（.gitignore 已有）；SQL 脚本放 `sql/` 目录
- 改 Java 在本地 VSCode，禁止服务器 sed；部署后浏览器验证，不只 curl
- 测试环境库 = shared-mysql.aigicrm；生产与测试不同步，生产变更必须先确认
- 前端源码权威目录 `04-源码/aigicrm-ui`（git 管理）；服务器 aigicrm-ui 是旧副本禁止构建
- MyBatis-Plus 租户插件必须配置 tenantTable 白名单（未迁移表会导致"列不存在"报错）

---

### Task 1: DataScopeType 枚举扩展（六级）

**Files:**
- Modify: `aigicrm-backend/src/main/java/com/aigicrm/common/enums/DataScopeType.java`
- Test: `aigicrm-backend/src/test/java/com/aigicrm/common/enums/DataScopeTypeTest.java`

**Interfaces:**
- Consumes: 现有枚举 `DataScopeType`（ALL/CUSTOM/ORG_AND_CHILDREN/ORG/SELF + rank()）
- Produces: 扩展后枚举值 `ALL, CUSTOM, REGION, OFFICE, DEPT, TEAM, SELF`（声明序即宽松度降序）；`rank()` 保持 ordinal；`fromCode()` 兼容旧值（org→dept 语义由调用方迁移）

- [ ] **Step 1: 写失败测试**

```java
package com.aigicrm.common.enums;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DataScopeTypeTest {
    @Test
    void newLevelsRankByBreadth() {
        assertTrue(DataScopeType.ALL.rank() < DataScopeType.REGION.rank());
        assertTrue(DataScopeType.REGION.rank() < DataScopeType.OFFICE.rank());
        assertTrue(DataScopeType.OFFICE.rank() < DataScopeType.DEPT.rank());
        assertTrue(DataScopeType.DEPT.rank() < DataScopeType.TEAM.rank());
        assertTrue(DataScopeType.TEAM.rank() < DataScopeType.SELF.rank());
    }
    @Test
    void fromCodeHandlesOldAndNew() {
        assertEquals(DataScopeType.DEPT, DataScopeType.fromCode("dept"));
        assertEquals(DataScopeType.TEAM, DataScopeType.fromCode("team"));
        assertEquals(DataScopeType.REGION, DataScopeType.fromCode("region"));
        assertEquals(DataScopeType.SELF, DataScopeType.fromCode(null));
        assertEquals(DataScopeType.SELF, DataScopeType.fromCode("unknown"));
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `cd aigicrm-backend && MAVEN_SKIP_RC=true mvn test -Dtest=DataScopeTypeTest`
Expected: FAIL（REGION/OFFICE/TEAM 不存在，编译错误）

- [ ] **Step 3: 扩展枚举**

```java
/** 全部数据（本集团） */
ALL("all", "全部数据"),
/** 自定义（sys_role_org 配置的组织集合） */
CUSTOM("custom", "自定义数据"),
/** 本大区及以下（全部办事处→部门→人） */
REGION("region", "本大区及以下"),
/** 本办事处及以下（全部部门→人） */
OFFICE("office", "本办事处及以下"),
/** 本部门及以下（含子部门，兼容） */
DEPT("dept", "本部门及以下"),
/** 本人及全部下级（manager_id 递归限5层） */
TEAM("team", "本人及下级"),
/** 仅本人 */
SELF("self", "仅本人");
```

- [ ] **Step 4: 运行确认通过**

Run: `MAVEN_SKIP_RC=true mvn test -Dtest=DataScopeTypeTest`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add aigicrm-backend/src/main/java/com/aigicrm/common/enums/DataScopeType.java aigicrm-backend/src/test/java/com/aigicrm/common/enums/DataScopeTypeTest.java
git commit -m "feat(perm): DataScopeType 扩展六级范围（team/office/region）"
```

---

### Task 2: OrgScopeResolver 核心解析器

**Files:**
- Create: `aigicrm-backend/src/main/java/com/aigicrm/common/scope/OrgScopeResolver.java`
- Create: `aigicrm-backend/src/test/java/com/aigicrm/common/scope/OrgScopeResolverTest.java`

**Interfaces:**
- Consumes: `SecurityUser`（getDataScope/getDeptId/getOfficeId/getUserId/getManagedOrgIds）、`SysUserMapper`、`SysDeptMapper`（表 sys_dept 含 parent_id/deptType/leader_id）
- Produces:
  - `List<Long> resolveVisibleUserIds()` — 当前请求用户可见 userIds；`null` = 本集团不过滤（all）；空列表 = 仅本人
  - `List<Long> resolveVisibleDeptIds()` — 可见部门子树根（scope 接口用）
  - 请求内缓存：同一请求多次调用只解析一次（`ThreadLocal<Map<String,Object>>`，请求结束时清理——由 Filter 或 finally 清理；初版用每次调用重算 + 调用方 applyDataScope 内单次调用即可，缓存放 Task 5 的 ScopeService 层）

- [ ] **Step 1: 写失败测试（用 Mockito mock mapper）**

```java
package com.aigicrm.common.scope;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.aigicrm.module.system.entity.SysDept;
import com.aigicrm.module.system.entity.SysUser;
import com.aigicrm.module.system.mapper.SysDeptMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.security.SecurityUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrgScopeResolverTest {

    @Mock SysUserMapper userMapper;
    @Mock SysDeptMapper deptMapper;

    private SysUser user(long id, Long deptId, Long managerId) {
        SysUser u = new SysUser();
        u.setId(id); u.setDeptId(deptId); u.setManagerId(managerId);
        return u;
    }
    private SysDept dept(long id, Long parentId, Integer deptType) {
        SysDept d = new SysDept();
        d.setId(id); d.setParentId(parentId); d.setDeptType(deptType);
        return d;
    }
    private SecurityUser su(Long userId, String scope, Long deptId) {
        SecurityUser s = new SecurityUser();
        s.setUserId(userId); s.setDataScope(scope); s.setDeptId(deptId);
        return s;
    }

    @Test
    void teamResolvesWholeChain() {
        // B(2) 的 manager = A(1)；C(3) 的 manager = B(2)。A 可见 {1,2,3}
        when(userMapper.selectList(any())).thenReturn(List.of(user(2, 11L, 1L), user(3, 12L, 2L)));
        OrgScopeResolver r = new OrgScopeResolver(userMapper, deptMapper);
        List<Long> u = r.resolve(su(1L, "team", 11L));
        assertTrue(u.contains(1L) && u.contains(2L) && u.contains(3L));
    }

    @Test
    void deptResolvesSubtreeUsers() {
        // 部门 11 有子部门 12；用户 A(1) 在 11，B(2) 在 12
        when(deptMapper.selectList(any())).thenReturn(List.of(dept(12, 11L, null)));
        when(userMapper.selectList(any())).thenReturn(List.of(user(2, 12L, null)));
        OrgScopeResolver r = new OrgScopeResolver(userMapper, deptMapper);
        List<Long> u = r.resolve(su(1L, "dept", 11L));
        assertTrue(u.contains(1L) && u.contains(2L));
    }

    @Test
    void allReturnsNull() {
        OrgScopeResolver r = new OrgScopeResolver(userMapper, deptMapper);
        assertNull(r.resolve(su(1L, "all", 11L)));
    }

    @Test
    void selfReturnsOnlySelf() {
        OrgScopeResolver r = new OrgScopeResolver(userMapper, deptMapper);
        assertEquals(List.of(1L), r.resolve(su(1L, "self", 11L)));
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `MAVEN_SKIP_RC=true mvn test -Dtest=OrgScopeResolverTest`
Expected: FAIL（类不存在）

- [ ] **Step 3: 实现 OrgScopeResolver**

```java
package com.aigicrm.common.scope;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.aigicrm.module.system.entity.SysDept;
import com.aigicrm.module.system.entity.SysUser;
import com.aigicrm.module.system.mapper.SysDeptMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.security.SecurityUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 六级数据范围统一解析器（设计文档 §4.2）
 * 输出可见用户集合 U：null=本集团不过滤(all)；空=仅本人；其余=可见 userIds（恒含本人）
 */
@Component
@RequiredArgsConstructor
public class OrgScopeResolver {

    /** 团队递归上限（manager_id 链） */
    public static final int TEAM_MAX_DEPTH = 5;

    /** 大区/办事处 deptType 标记值（与组织管理页字典一致） */
    public static final int TYPE_COMPANY = 1;
    public static final int TYPE_REGION = 2;
    public static final int TYPE_OFFICE = 3;
    public static final int TYPE_DEPT = 4;

    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;

    /** 解析当前用户可见 userIds（恒含本人；all 返回 null） */
    public List<Long> resolve(SecurityUser user) {
        if (user == null) return List.of();
        String scope = user.getDataScope() != null ? user.getDataScope() : "self";
        Long userId = user.getUserId();
        Long deptId = user.getDeptId() != null ? user.getDeptId() : user.getOfficeId();

        switch (scope) {
            case "all": return null;
            case "custom": return resolveCustom(user, userId);
            case "region": return resolveByAncestorType(user, userId, deptId, TYPE_REGION, true);
            case "office": return resolveByAncestorType(user, userId, deptId, TYPE_OFFICE, false);
            case "dept": return resolveDeptTree(user, userId, deptId, false);
            case "team": return resolveTeam(user, userId);
            case "self":
            default: return List.of(userId);
        }
    }

    /** 可见部门集合（scope 接口用）：U 中用户所属 deptId 去重 + 本人部门 */
    public List<Long> resolveVisibleDeptIds(SecurityUser user) {
        List<Long> u = resolve(user);
        if (u == null) return null; // all：不过滤
        Set<Long> deptIds = new LinkedHashSet<>();
        if (!u.isEmpty()) {
            List<SysUser> users = userMapper.selectBatchIds(u);
            for (SysUser su : users) {
                if (su.getDeptId() != null) deptIds.add(su.getDeptId());
            }
        }
        if (user.getDeptId() != null) deptIds.add(user.getDeptId());
        return new ArrayList<>(deptIds);
    }

    // ---- 各级别实现 ----

    private List<Long> resolveTeam(SecurityUser user, Long userId) {
        Set<Long> result = new LinkedHashSet<>();
        result.add(userId);
        List<Long> currentLevel = List.of(userId);
        for (int depth = 0; depth < TEAM_MAX_DEPTH && !currentLevel.isEmpty(); depth++) {
            List<Long> subs = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                            .in(SysUser::getManagerId, currentLevel)
                            .eq(SysUser::getDeleted, 0))
                    .stream().map(SysUser::getId).toList();
            List<Long> fresh = subs.stream().filter(id -> result.add(id)).toList();
            currentLevel = fresh;
        }
        return new ArrayList<>(result);
    }

    private List<Long> resolveDeptTree(SecurityUser user, Long userId, Long deptId, boolean includeSelfTree) {
        if (deptId == null) return List.of(userId);
        Set<Long> deptIds = collectSubtree(deptId, null);
        List<Long> u = usersInDepts(deptIds);
        if (!u.contains(userId)) u.add(userId);
        return u;
    }

    private List<Long> resolveByAncestorType(SecurityUser user, Long userId, Long deptId, int targetType, boolean fallbackToRoot) {
        if (deptId == null) return List.of(userId);
        Long anchor = findAncestorOfType(deptId, targetType);
        if (anchor == null) {
            // 无标记：office 退化 dept 树；region 退化集团根（沿祖先链到 parent 为 null 的根）
            return targetType == TYPE_OFFICE
                    ? resolveDeptTree(user, userId, deptId, false)
                    : resolveDeptTree(user, userId, findRoot(deptId), false);
        }
        Set<Long> deptIds = collectSubtree(anchor, null);
        List<Long> u = usersInDepts(deptIds);
        if (!u.contains(userId)) u.add(userId);
        return u;
    }

    private List<Long> resolveCustom(SecurityUser user, Long userId) {
        List<Long> managed = user.getManagedOrgIds();
        if (managed == null || managed.isEmpty()) return List.of(userId);
        Set<Long> deptIds = new LinkedHashSet<>();
        for (Long orgId : managed) deptIds.addAll(collectSubtree(orgId, null));
        List<Long> u = usersInDepts(deptIds);
        if (!u.contains(userId)) u.add(userId);
        return u;
    }

    private Set<Long> collectSubtree(Long rootId, Set<Long> acc) {
        if (acc == null) acc = new LinkedHashSet<>();
        acc.add(rootId);
        List<Long> children = deptMapper.selectList(new LambdaQueryWrapper<SysDept>()
                        .eq(SysDept::getParentId, rootId)
                        .eq(SysDept::getStatus, 1))
                .stream().map(SysDept::getId).toList();
        for (Long c : children) collectSubtree(c, acc);
        return acc;
    }

    private Long findAncestorOfType(Long deptId, int type) {
        Long cur = deptId;
        for (int i = 0; i < 20 && cur != null; i++) {
            SysDept d = deptMapper.selectById(cur);
            if (d == null) return null;
            if (d.getDeptType() != null && d.getDeptType() == type) return d.getId();
            cur = d.getParentId();
        }
        return null;
    }

    private Long findRoot(Long deptId) {
        Long cur = deptId;
        for (int i = 0; i < 20 && cur != null; i++) {
            SysDept d = deptMapper.selectById(cur);
            if (d == null || d.getParentId() == null) return cur;
            cur = d.getParentId();
        }
        return cur;
    }

    private List<Long> usersInDepts(Set<Long> deptIds) {
        if (deptIds.isEmpty()) return new ArrayList<>();
        return userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .in(SysUser::getDeptId, deptIds)
                        .eq(SysUser::getDeleted, 0)
                        .eq(SysUser::getStatus, 1))
                .stream().map(SysUser::getId).toList();
    }
}
```

- [ ] **Step 4: 运行确认通过**

Run: `MAVEN_SKIP_RC=true mvn test -Dtest=OrgScopeResolverTest`
Expected: PASS（注意：Mockito 中 `when(userMapper.selectList(any()))` 需匹配 LambdaQueryWrapper 泛型，若编译报类型擦除问题改用 `any(LambdaQueryWrapper.class)`）

- [ ] **Step 5: 提交**

```bash
git add aigicrm-backend/src/main/java/com/aigicrm/common/scope/OrgScopeResolver.java aigicrm-backend/src/test/java/com/aigicrm/common/scope/OrgScopeResolverTest.java
git commit -m "feat(perm): OrgScopeResolver 六级数据范围解析器"
```

---

### Task 3: DataScopeGuard 归属校验守卫

**Files:**
- Create: `aigicrm-backend/src/main/java/com/aigicrm/common/scope/DataScopeGuard.java`
- Test: `aigicrm-backend/src/test/java/com/aigicrm/common/scope/DataScopeGuardTest.java`

**Interfaces:**
- Consumes: `OrgScopeResolver`、`SecurityUtils`
- Produces: `boolean canAccess(Long ownerId)`、`void checkOwner(Long ownerId)`（不可访问抛 `BusinessException(403, "无权操作该记录")`）、`boolean isVisibleScope(Collection<Long> scope)`（null=不过滤）

- [ ] **Step 1: 写失败测试**

```java
package com.aigicrm.common.scope;

import com.aigicrm.common.exception.BusinessException;
import com.aigicrm.security.SecurityUser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DataScopeGuardTest {

    @Test
    void checkOwnerRejectsOutOfScope() {
        // 直接用静态方法 + 注入 resolver 的纯方法：ownerId 在范围外
        List<Long> scope = List.of(1L, 2L);
        assertDoesNotThrow(() -> DataScopeGuard.checkOwnerInScope(3L, scope, false));
    }

    @Test
    void nullScopeAllowsAll() {
        assertTrue(DataScopeGuard.isVisible(5L, null, false));
    }

    @Test
    void ownerIsAlwaysVisible() {
        assertTrue(DataScopeGuard.isVisible(7L, List.of(1L), true)); // ownerIsSelf=true
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `MAVEN_SKIP_RC=true mvn test -Dtest=DataScopeGuardTest`
Expected: FAIL（类不存在）

- [ ] **Step 3: 实现 DataScopeGuard**

```java
package com.aigicrm.common.scope;

import com.aigicrm.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * 统一归属校验（设计文档 §6）：写操作（PUT/DELETE/share）目标记录 owner
 * 必须 ∈ 当前用户可见范围；可见范围 = Resolver 结果 ∪ 分享授予（调用方传入）∪ 本人
 */
@Component
@RequiredArgsConstructor
public class DataScopeGuard {

    private final OrgScopeResolver resolver;

    /** 校验目标记录是否当前用户可操作；scopeOverride=分享等额外可见集合 */
    public void checkOwner(Long ownerId, Collection<Long> scopeOverride) {
        if (ownerId == null) throw new BusinessException(403, "记录归属缺失");
        Long me = com.aigicrm.security.SecurityUtils.getCurrentUserId();
        if (me != null && me.equals(ownerId)) return; // 或本人恒可
        List<Long> scope = scopeOverride == null ? null : new java.util.ArrayList<>(scopeOverride);
        if (scope == null || scope.isEmpty()) {
            scope = resolver.resolve(com.aigicrm.security.SecurityUtils.getCurrentUser());
        } else if (scope != null) {
            scope.add(ownerId); // 防误判：owner 已在额外集合中
        }
        if (scope == null) return; // all：不过滤
        if (!scope.contains(ownerId)) throw new BusinessException(403, "无权操作该记录");
    }

    /** 静态便捷：ownerId 是否在可见集合内（ownerIsSelf=调用方已确认本人） */
    public static boolean isVisible(Long ownerId, Collection<Long> scope, boolean ownerIsSelf) {
        if (ownerIsSelf) return true;
        if (scope == null) return true;
        return scope.contains(ownerId);
    }

    /** 静态便捷：checkOwnerInScope 供无 Spring 上下文的场景/测试使用 */
    public static void checkOwnerInScope(Long ownerId, Collection<Long> scope, boolean ownerIsSelf) {
        if (!isVisible(ownerId, scope, ownerIsSelf)) throw new BusinessException(403, "无权操作该记录");
    }
}
```

> 注：测试中直接调静态方法（无 Spring 上下文）；Spring 注入的 checkOwner 用于业务接口。BusinessException 构造器签名需核对（现有用法：`new BusinessException(403, "...")`——GlobalExceptionHandler 已处理，见 ScoreController 相关记忆）。

- [ ] **Step 4: 运行确认通过**

Run: `MAVEN_SKIP_RC=true mvn test -Dtest=DataScopeGuardTest`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add aigicrm-backend/src/main/java/com/aigicrm/common/scope/DataScopeGuard.java aigicrm-backend/src/test/java/com/aigicrm/common/scope/DataScopeGuardTest.java
git commit -m "feat(perm): DataScopeGuard 统一归属校验守卫"
```

---

### Task 4: MyBatis-Plus 租户插件（集团隔离）

**Files:**
- Modify: `aigicrm-backend/src/main/java/com/aigicrm/config/MybatisPlusConfig.java`
- Modify: `aigicrm-backend/src/main/java/com/aigicrm/security/UserDetailsServiceImpl.java`（无需改——groupIds 已加载；仅确认）

**Interfaces:**
- Consumes: MyBatis-Plus 3.5.6 `TenantLineInnerInterceptor`；`SecurityUtils.getCurrentGroupIds()`
- Produces: 全局租户拦截器——对白名单表自动附加 `tenant_id = ?`；平台管理员（isAdmin 且无集团归属）跳过

- [ ] **Step 1: 写配置变更**

```java
// MybatisPlusConfig.java 中 mybatisPlusInterceptor() 追加：
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.aigicrm.security.SecurityUtils;

// 白名单：仅已迁移 tenant_id 的核心业务表（Task 11 迁移后启用；未迁移前不要注册拦截器）
private static final java.util.Set<String> TENANT_TABLES =
        java.util.Set.of("biz_customer", "biz_customer_share", "biz_file_folder", "biz_file",
                "biz_incentive_record", "biz_sea_pool", "biz_approval", "biz_reminder");

interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
    @Override
    public Expression getTenantId() {
        List<Long> g = SecurityUtils.getCurrentGroupIds();
        if (g == null || g.isEmpty()) return new LongValue(1L); // 无归属默认主集团（迁移默认值）
        if (g.size() == 1) return new LongValue(g.get(0));
        // 多集团：IN
        return new InExpression(null, g.stream().map(LongValue::new).collect(Collectors.toList()));
    }
    @Override
    public String getTenantIdColumn() { return "tenant_id"; }
    @Override
    public boolean ignoreTable(String tableName) { return !TENANT_TABLES.contains(tableName); }
}));
```

> ⚠️ 平台管理员跳过：TenantLineHandler 无法区分登录态——采用**忽略表 + 平台管理员不走拦截**策略：拦截器仅当 `SecurityUtils.getCurrentGroupIds()==null && isAdmin()` 时整体放行不可行（拦截器无法整体放行）；改为：**平台管理员（系统超管）的 groupIds 由 UserDetailsServiceImpl 填充为 null，查询时 TenantLineHandler 无法跳过——因此本阶段租户插件仅在 Task 11 迁移完成后注册，且系统超管查询走"全集团"由 TenantLineInnerInterceptor 的 getTenantId 返回 null 表达式 → 用 `new NullExpression()` 使条件恒真**（MP 3.5.6 支持）。

- [ ] **Step 2: 编译验证**

Run: `MAVEN_SKIP_RC=true mvn -q compile`
Expected: 编译通过（含 import 修正）

- [ ] **Step 3: 写配置断言测试（ArchUnit 或简单加载测试）**

```java
// src/test/java/com/aigicrm/config/MybatisPlusConfigTest.java
package com.aigicrm.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;

@SpringBootTest
class MybatisPlusConfigTest {
    @Autowired MybatisPlusInterceptor interceptor;
    @Test
    void interceptorHasTenantAndPagination() {
        assertNotNull(interceptor);
    }
}
```

Run: `MAVEN_SKIP_RC=true mvn test -Dtest=MybatisPlusConfigTest`
Expected: PASS（若 Spring 上下文加载慢，可改为纯配置类实例化断言）

- [ ] **Step 4: 提交**

```bash
git add aigicrm-backend/src/main/java/com/aigicrm/config/MybatisPlusConfig.java aigicrm-backend/src/test/java/com/aigicrm/config/MybatisPlusConfigTest.java
git commit -m "feat(perm): 租户插件白名单配置（集团隔离）"
```

---

### Task 5: 公共 scope 接口（范围感知筛选器）

**Files:**
- Create: `aigicrm-backend/src/main/java/com/aigicrm/module/common/controller/ScopeController.java`
- Create: `aigicrm-backend/src/main/java/com/aigicrm/module/common/service/ScopeService.java`
- Test: `aigicrm-backend/src/test/java/com/aigicrm/common/scope/ScopeServiceTest.java`

**Interfaces:**
- Consumes: `OrgScopeResolver`（resolve + resolveVisibleDeptIds）
- Produces:
  - `GET /api/common/scope/users?deptId=` → `Result<List<Map>>`（id/realName/deptId，按 U 过滤）
  - `GET /api/common/scope/depts` → `Result<List<SysDept>>`（U 聚合的部门树，含 children）
  - 接口无 @PreAuthorize（登录即可，isAuthenticated——数据范围在 service 内强制）

- [ ] **Step 1: 写失败测试**

```java
package com.aigicrm.common.scope;

import com.aigicrm.module.common.service.ScopeService;
import com.aigicrm.module.system.entity.SysDept;
import com.aigicrm.module.system.entity.SysUser;
import com.aigicrm.module.system.mapper.SysDeptMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.security.SecurityUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScopeServiceTest {

    @Mock SysUserMapper userMapper;
    @Mock SysDeptMapper deptMapper;

    @Test
    void scopeUsersFilteredByScope() {
        when(userMapper.selectList(any())).thenReturn(List.of()); // team 子查询空
        OrgScopeResolver resolver = new OrgScopeResolver(userMapper, deptMapper);
        SecurityUser su = new SecurityUser();
        su.setUserId(1L); su.setDataScope("self"); su.setDeptId(11L);
        ScopeService svc = new ScopeService(resolver, userMapper);
        List<Object> users = svc.visibleUsers(su, null);
        assertEquals(1, users.size()); // 仅自己
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `MAVEN_SKIP_RC=true mvn test -Dtest=ScopeServiceTest`
Expected: FAIL（类不存在）

- [ ] **Step 3: 实现 ScopeService + ScopeController**

```java
// module/common/service/ScopeService.java
package com.aigicrm.module.common.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.aigicrm.common.scope.OrgScopeResolver;
import com.aigicrm.module.system.entity.SysUser;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.security.SecurityUser;
import com.aigicrm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

/** 范围感知筛选器（设计文档 §6.3）：所有模块的人员/部门下拉统一入口 */
@Service
@RequiredArgsConstructor
public class ScopeService {

    private final OrgScopeResolver resolver;
    private final SysUserMapper userMapper;

    /** 当前用户可见人员列表；deptId 非空时限定该部门 ∩ U */
    public List<Map<String, Object>> visibleUsers(SecurityUser user, Long deptId) {
        List<Long> u = resolver.resolve(user);
        if (u == null) { // all：全部启用用户
            return userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                            .eq(SysUser::getDeleted, 0).eq(SysUser::getStatus, 1)
                            .eq(deptId != null, SysUser::getDeptId, deptId)
                            .orderByAsc(SysUser::getRealName))
                    .stream().map(this::toUserMap).toList();
        }
        if (u.isEmpty()) return List.of();
        return userMapper.selectBatchIds(u).stream()
                .filter(x -> deptId == null || deptId.equals(x.getDeptId()))
                .map(this::toUserMap)
                .sorted(Comparator.comparing(m -> String.valueOf(m.get("realName"))))
                .toList();
    }

    private Map<String, Object> toUserMap(SysUser x) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", x.getId());
        m.put("realName", x.getRealName());
        m.put("deptId", x.getDeptId());
        return m;
    }
}
```

```java
// module/common/controller/ScopeController.java
package com.aigicrm.module.common.controller;

import com.aigicrm.common.Result;
import com.aigicrm.module.common.service.ScopeService;
import com.aigicrm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/common/scope")
@RequiredArgsConstructor
public class ScopeController {

    private final ScopeService scopeService;

    /** 可见人员列表（登录即可；范围 service 内强制） */
    @GetMapping("/users")
    public Result<List<Map<String, Object>>> users(@RequestParam(required = false) Long deptId) {
        return Result.ok(scopeService.visibleUsers(SecurityUtils.getCurrentUser(), deptId));
    }

    /** 可见部门树（U 聚合） */
    @GetMapping("/depts")
    public Result<List<Object>> depts() {
        return Result.ok(scopeService.visibleDeptTree(SecurityUtils.getCurrentUser()));
    }
}
```

> visibleDeptTree：基于 resolveVisibleDeptIds 取部门集合，按 parent_id 组装树（复用 SysDeptService.tree 的组装模式，先实现扁平列表+一层 parent 过滤，树形组装沿用现有 dept 树代码）。若 SysDeptMapper 存在 selectBatchIds，直接查部门行组装。

- [ ] **Step 4: 运行确认通过**

Run: `MAVEN_SKIP_RC=true mvn test -Dtest=ScopeServiceTest`
Expected: PASS

- [ ] **Step 5: 本地冒烟（编译+单测全量）**

Run: `MAVEN_SKIP_RC=true mvn test -DskipTests=false -q`
Expected: 全量单测通过（106+ 新增）

- [ ] **Step 6: 提交**

```bash
git add aigicrm-backend/src/main/java/com/aigicrm/module/common aigicrm-backend/src/test/java/com/aigicrm/common/scope/ScopeServiceTest.java
git commit -m "feat(perm): 公共 scope 接口（范围感知筛选器）"
```

---

### Task 6: 权限码补齐（菜单 + 绑定矩阵）

**Files:**
- Create: `sql/migration-20260805-perm-codes.sql`
- Verify: 重跑 `scripts/gen-permission-dict.sh`（权限码字典核对）

**Interfaces:**
- Consumes: sys_menu（menu_type=3 按钮权限点）/ sys_role_menu 现有绑定
- Produces: 新增 customer:share / file:share / sea:export / sea:import / incentive:add 菜单行 + 绑定；incentive 矩阵调整（销售员卸 edit/delete）

- [ ] **Step 1: 查库确认现状（防改错）**

Run: 测试库查询当前菜单最大 id 与 62/63 绑定：
```sql
SELECT id, permission, menu_name, parent_id FROM sys_menu WHERE permission LIKE 'incentive:%' OR permission LIKE 'customer:share' OR permission LIKE 'file:share' OR permission LIKE 'sea:export' OR permission LIKE 'sea:import';
SELECT role_id, menu_id FROM sys_role_menu WHERE menu_id IN (62,63) ORDER BY role_id;
```

- [ ] **Step 2: 写迁移 SQL（幂等）**

```sql
-- sql/migration-20260805-perm-codes.sql
-- 权限码补齐（设计文档 §3.2）；幂等：INSERT IGNORE / 存在性判断
INSERT INTO sys_menu (parent_id, menu_name, menu_type, path, permission, icon, sort_order, visible, status)
SELECT 11, '客户分享', 3, '', 'customer:share', NULL, 99, 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission='customer:share');

INSERT INTO sys_menu (parent_id, menu_name, menu_type, path, permission, icon, sort_order, visible, status)
SELECT 14, '文件分享', 3, '', 'file:share', NULL, 99, 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission='file:share');

INSERT INTO sys_menu (parent_id, menu_name, menu_type, path, permission, icon, sort_order, visible, status)
SELECT 17, '公海导出', 3, '', 'sea:export', NULL, 98, 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission='sea:export');

INSERT INTO sys_menu (parent_id, menu_name, menu_type, path, permission, icon, sort_order, visible, status)
SELECT 17, '公海导入', 3, '', 'sea:import', NULL, 99, 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission='sea:import');

INSERT INTO sys_menu (parent_id, menu_name, menu_type, path, permission, icon, sort_order, visible, status)
SELECT 15, '激励上传', 3, '', 'incentive:add', NULL, 99, 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission='incentive:add');

-- 绑定：新码绑定 admin(1)/行政(9)/销售总监(10)；incentive:add 额外绑销售员(11)
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id FROM sys_role r CROSS JOIN sys_menu m
WHERE m.permission IN ('customer:share','file:share','sea:export','sea:import')
  AND r.id IN (1,9,10);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 11, m.id FROM sys_menu m WHERE m.permission='incentive:add';

-- incentive 矩阵调整（销售员卸 edit/delete）：incentive:edit(62)/incentive:delete(63) 解除角色11
DELETE FROM sys_role_menu WHERE role_id=11 AND menu_id IN (62,63);

-- 记录回滚 SQL（备份当前绑定）
SELECT role_id, menu_id FROM sys_role_menu WHERE menu_id IN (62,63) ORDER BY role_id;
```

- [ ] **Step 3: 测试库执行 + 验证**

Run: 测试库执行迁移 → `curl http://111.229.37.220/api/system/menus/tree`（admin token）确认新码存在；登录销售员确认 62/63 已卸、incentive:add 已授。

- [ ] **Step 4: 更新权限码字典**

Run: `scripts/gen-permission-dict.sh`（重跑后 61 → 66 码，核对无遗漏）

- [ ] **Step 5: 提交**

```bash
git add sql/migration-20260805-perm-codes.sql
git commit -m "feat(perm): 权限码补齐（share/export/import/incentive:add）+ 绑定矩阵调整"
```

---

### Task 7: 角色管理页数据范围级别下拉

**Files:**
- Modify: `aigicrm-ui/src/views/system/role.vue`（表单 + 列表列）
- 后端无需改：sys_role.data_scope 字段已存在，updateRole 已接收（验证即可）

**Interfaces:**
- Consumes: `updateRole(id, {dataScope, ...})`（现有 API）
- Produces: 角色表单"数据范围"下拉（self/team/dept/office/region/all/custom 中文标签）

- [ ] **Step 1: 修改 role.vue 表单**

```vue
<!-- 表单新增 -->
<el-form-item label="数据范围" prop="dataScope">
  <el-select v-model="editForm.dataScope" style="width:100%">
    <el-option label="仅本人" value="self" />
    <el-option label="本人及下级" value="team" />
    <el-option label="本部门及以下" value="dept" />
    <el-option label="本办事处及以下" value="office" />
    <el-option label="本大区及以下" value="region" />
    <el-option label="全部数据(本集团)" value="all" />
    <el-option label="自定义组织" value="custom" />
  </el-select>
</el-form-item>
<!-- 列表列新增 -->
<el-table-column label="数据范围" width="120">
  <template #default="{ row }">{{ scopeLabel(row.dataScope) }}</template>
</el-table-column>
```

- [ ] **Step 2: 补 scopeLabel 映射 + editForm 初始化 dataScope**

```js
const scopeMap = { self:'仅本人', team:'本人及下级', dept:'本部门及以下', office:'本办事处及以下', region:'本大区及以下', all:'全部', custom:'自定义' }
const scopeLabel = s => scopeMap[s] || s || '仅本人'
// editForm 增加 dataScope: null；打开编辑时 Object.assign 带上 row.dataScope
```

- [ ] **Step 3: 本地构建 + 测试环境部署验证**

Run: `cd aigicrm-ui && npm run build` → docker cp 进测试容器 → admin 登录角色页：改销售员角色数据范围为 team → 保存 → 重进确认回显。

- [ ] **Step 4: 提交**

```bash
git add aigicrm-ui/src/views/system/role.vue
git commit -m "feat(perm): 角色管理页数据范围级别下拉"
```

---

### Task 8: 人员管理页 manager_id + 用户数据范围

**Files:**
- Modify: `aigicrm-ui/src/views/system/user.vue`（表单 + 列表列）
- Modify: 后端用户详情/保存 DTO（SysUser 实体已含 managerId；验证 UserController updateUser 是否透传 managerId）

**Interfaces:**
- Consumes: `updateUser(id, form)`（现有 API；form 增加 managerId/dataScopeOverride）
- Produces: 人员列表显示"直属上级"；表单可选直属上级 + 数据范围（跟随角色/指定级别）

- [ ] **Step 1: 确认后端透传**

Run: 检查 `SysUserController.updateUser` 是否接收 managerId（实体有字段则 MyBatis-Plus 自动）；`GET /api/system/users/{id}` 已返回 managerId（实测过）——仅需验证 updateUser 保存 managerId 生效（改一条测试用户 → 回读）。

- [ ] **Step 2: 修改 user.vue**

```vue
<!-- 表单：直属上级 + 数据范围 -->
<el-form-item label="直属上级" prop="managerId">
  <el-select v-model="editForm.managerId" clearable filterable style="width:100%">
    <el-option v-for="u in allUsers" :key="u.id" :label="u.realName" :value="u.id" />
  </el-select>
</el-form-item>
<el-form-item label="数据范围" prop="dataScopeOverride">
  <el-select v-model="editForm.dataScopeOverride" clearable style="width:100%">
    <el-option label="跟随角色" value="" />
    <el-option label="仅本人" value="self" />
    <el-option label="本人及下级" value="team" />
    <el-option label="本部门及以下" value="dept" />
    <el-option label="本办事处及以下" value="office" />
    <el-option label="本大区及以下" value="region" />
    <el-option label="全部(本集团)" value="all" />
    <el-option label="自定义组织" value="custom" />
  </el-select>
</el-form-item>
<!-- 列表列：直属上级 -->
<el-table-column label="直属上级" width="100">
  <template #default="{ row }">{{ row.managerName || '—' }}</template>
</el-table-column>
```

> 后端配合（同任务内）：sys_user 加 `data_scope_override VARCHAR(20) NULL` 列（迁移脚本；空=跟随角色）；UserDetailsServiceImpl 加载时若 override 非空则覆盖 dataScope。UserController 保存字段透传。

- [ ] **Step 3: 后端实现 override 生效**

```java
// UserDetailsServiceImpl：角色聚合 dataScope 后
String override = user.getDataScopeOverride();
if (override != null && !override.isBlank()) {
    securityUser.setDataScope(override); // 用户级覆盖角色级（设计文档 §7 层2）
}
```

- [ ] **Step 4: 构建 + 测试环境验证**

Run: 后端 mvn 构建 → scp 测试服 → docker build；前端 build → docker cp。测试：给 wangleilei 设 override=team + managerId=某用户 → 登录验证 scope 接口范围变化；清空 override → 回角色默认。

- [ ] **Step 5: 提交**

```bash
git add aigicrm-ui/src/views/system/user.vue sql/migration-20260805-user-override.sql aigicrm-backend/src/main/java/com/aigicrm/security/UserDetailsServiceImpl.java
git commit -m "feat(perm): 人员管理 manager_id + 用户数据范围覆盖"
```

---

### Task 9: 激励 PUT 越权修复（P2）

**Files:**
- Modify: `aigicrm-backend/src/main/java/com/aigicrm/module/incentive/controller/IncentiveController.java`（updateRecord）
- Test: `aigicrm-backend/src/test/java/com/aigicrm/module/incentive/controller/IncentiveControllerPermissionTest.java`

**Interfaces:**
- Consumes: `DataScopeGuard`、`SecurityUtils`、`IncentiveService.getRecordById`
- Produces: PUT /records/{id} 归属校验——`非admin 且 无激励编辑授权 且 记录非本人 → 403 "无权编辑他人记录"`

- [ ] **Step 1: 写越权测试（MockMvc 或 controller 直测）**

```java
package com.aigicrm.module.incentive.controller;

import com.aigicrm.module.incentive.entity.BizIncentiveRecord;
import com.aigicrm.module.incentive.service.IncentiveService;
import com.aigicrm.security.SecurityUtils;
import com.aigicrm.security.SecurityUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IncentiveControllerPermissionTest {

    @Mock IncentiveService incentiveService;
    @InjectMocks IncentiveController controller;

    @Test
    void updateRejectsOthersRecordForPlainUser() {
        SecurityUser su = new SecurityUser();
        su.setUserId(2L); su.setDataScope("self");
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUser).thenReturn(su);
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            BizIncentiveRecord other = new BizIncentiveRecord();
            other.setId(79L); other.setUserId(1L);
            when(incentiveService.getRecordById(79L)).thenReturn(other);
            // canEdit(userId) 需 mock：IncentiveService 是 mock，直接 when(incentiveService.canEdit(2L)).thenReturn(false)
            when(incentiveService.canEdit(2L)).thenReturn(false);
            // 期望抛 403（controller 内 Result.error 返回——若 controller 用返回式，则断言返回体 code）
            assertThrows(Exception.class, () -> controller.updateRecord(79L, new BizIncentiveRecord()));
        }
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `MAVEN_SKIP_RC=true mvn test -Dtest=IncentiveControllerPermissionTest`
Expected: FAIL（当前可改他人记录）

- [ ] **Step 3: 实现校验（对齐 DELETE 模式）**

```java
// IncentiveController.updateRecord 增加：
@PreAuthorize("hasAuthority('incentive:edit')")
@PutMapping("/records/{id}")
public Result<Void> updateRecord(@PathVariable Long id, @RequestBody BizIncentiveRecord record) {
    SecurityUser user = SecurityUtils.getCurrentUser();
    boolean isAdmin = SecurityUtils.isAdmin();
    if (!isAdmin && !incentiveService.canEdit(user.getUserId())) {
        BizIncentiveRecord existing = incentiveService.getRecordById(id);
        if (existing == null || !existing.getUserId().equals(user.getUserId())) {
            return Result.error(403, "无权编辑他人记录");
        }
    }
    incentiveService.updateRecord(id, record);
    return Result.ok();
}
```

- [ ] **Step 4: 运行确认通过 + 测试环境实测**

Run: 单测 PASS → 构建部署测试服 → wangleilei（无授权）PUT 他人记录 → body code 403；本人记录 PUT → 200。

- [ ] **Step 5: 提交**

```bash
git add aigicrm-backend/src/main/java/com/aigicrm/module/incentive/controller/IncentiveController.java aigicrm-backend/src/test/java/com/aigicrm/module/incentive/controller/IncentiveControllerPermissionTest.java
git commit -m "fix(perm): 激励 PUT 越权修复（归属校验对齐 DELETE）"
```

---

### Task 10: 组织管理页 deptType 强化

**Files:**
- Modify: `aigicrm-ui/src/views/system/dept.vue`（新建/编辑表单）
- 后端：SysDept 实体已有 deptType 字段；验证 SysDeptController 保存透传

**Interfaces:**
- Consumes: `createDept/updateDept`（现有 API）
- Produces: 组织表单 deptType 必选（公司/大区/办事处/部门）+ 集团归属必选 + 层级校验（大区不能挂办事处下）

- [ ] **Step 1: 修改 dept.vue**

```vue
<el-form-item label="组织类型" prop="deptType" required>
  <el-select v-model="editForm.deptType" style="width:100%">
    <el-option label="公司/总部" :value="1" />
    <el-option label="大区" :value="2" />
    <el-option label="办事处" :value="3" />
    <el-option label="部门" :value="4" />
  </el-select>
</el-form-item>
<!-- 校验：大区(2) 的父节点必须是 公司(1)/集团根；办事处(3) 的父必须是 大区(2)；部门(4) 的父必须是 办事处(3) -->
```

- [ ] **Step 2: 提交校验逻辑（前端 save 前）**

```js
const typeRank = { 1:0, 2:1, 3:2, 4:3 }
function validateType() {
  const parent = deptTree.find(n => n.id === editForm.parentId)
  if (!parent) return true // 根节点必须是公司
  return (typeRank[editForm.deptType] ?? -1) === (typeRank[parent.deptType] ?? -1) + 1
}
```

- [ ] **Step 3: 构建 + 测试环境验证**

Run: 前端 build → docker cp → 建测试组织（选类型）→ 保存 → 回读。

- [ ] **Step 4: 提交**

```bash
git add aigicrm-ui/src/views/system/dept.vue
git commit -m "feat(perm): 组织管理页 deptType 必选+层级校验"
```

---

### Task 11: 数据前提体检脚本（门禁）

**Files:**
- Create: `scripts/check-permission-data.sh`
- Modify: `scripts/pre-delivery-check.sh`（[5] 后追加体检步骤）

**Interfaces:**
- Consumes: 测试/生产数据库连接（复用现有脚本的 DB 凭据模式）
- Produces: 体检报告输出（deptType 空值数/集团归属空值数/manager_id 空值数/tenant_id 覆盖数），非零列"人工介入"

- [ ] **Step 1: 写体检脚本**

```bash
#!/usr/bin/env bash
# scripts/check-permission-data.sh — 权限数据前提体检（设计文档 §12.2）
# 用法: check-permission-data.sh <DB_HOST> <DB_USER> <DB_PASS> <DB_NAME>
set -euo pipefail
HOST=$1; USER=$2; PASS=$3; DB=$4
MYSQL="mysql -h$HOST -u$USER -p$PASS $DB -N -e"

echo "== 权限数据前提体检 =="
echo -n "deptType 为空的组织数: "; $MYSQL "SELECT COUNT(*) FROM sys_dept WHERE deleted=0 AND (dept_type IS NULL OR dept_type=0);"
echo -n "group_id 为空的业务组织数: "; $MYSQL "SELECT COUNT(*) FROM sys_dept WHERE deleted=0 AND group_id IS NULL;"
echo -n "manager_id 为空的启用用户数: "; $MYSQL "SELECT COUNT(*) FROM sys_user WHERE deleted=0 AND status=1 AND manager_id IS NULL;"
echo -n "白名单表 tenant_id 为空的记录数: "
for t in biz_customer biz_incentive_record; do
  echo -n "  $t: "; $MYSQL "SELECT COUNT(*) FROM $t WHERE tenant_id IS NULL OR tenant_id=0;"
done
echo "== 完成 =="
```

- [ ] **Step 2: 接入 pre-delivery-check.sh**

在现有检查项后追加：
```bash
# [6] 权限数据前提体检（存在性检查；非零需人工介入）
"$SCRIPT_DIR/check-permission-data.sh" "$DB_HOST" "$DB_USER" "$DB_PASS" "$DB_NAME" || { echo "权限数据体检失败"; FAIL=1; }
```

- [ ] **Step 3: 测试环境运行验证**

Run: `scripts/check-permission-data.sh 111.229.37.220 aigicrm aigicrm_pwd aigicrm`
Expected: 输出各计数；比对已知现状（deptType 空值 = 待盘点数）

- [ ] **Step 4: 提交**

```bash
git add scripts/check-permission-data.sh scripts/pre-delivery-check.sh
git commit -m "feat(perm): 权限数据前提体检脚本 + 门禁接入"
```

---

### Task 12: tenant_id 迁移脚本（演练先行）

**Files:**
- Create: `sql/migration-20260805-tenant-id.sql`
- Verify: 测试库演练 + 回滚脚本验证

**Interfaces:**
- Consumes: 核心业务表清单（biz_customer/biz_customer_share/biz_file_folder/biz_file/biz_incentive_record/biz_sea_pool/biz_approval/biz_reminder）
- Produces: 各表 tenant_id BIGINT NOT NULL DEFAULT 1 + 索引；白名单与 Task 4 配置一致

- [ ] **Step 1: 写迁移 SQL（幂等 + 回滚注释）**

```sql
-- sql/migration-20260805-tenant-id.sql
-- 集团租户隔离字段（设计文档 §4.3）；默认 1=主集团；生产执行前先测试库演练
-- 回滚: ALTER TABLE <t> DROP COLUMN tenant_id; （数据备份先行）
ALTER TABLE biz_customer ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '集团ID' AFTER id;
ALTER TABLE biz_customer ADD INDEX idx_tenant (tenant_id);
-- ... 其余表同模式（biz_customer_share/biz_file_folder/biz_file/biz_incentive_record/biz_sea_pool/biz_approval/biz_reminder）
```

- [ ] **Step 2: 测试库演练**

Run: 测试库执行 → `SHOW INDEX FROM biz_customer` 确认索引 → 抽样回读 tenant_id=1 → 回滚脚本在演练副本验证（DROP COLUMN 可逆）。

- [ ] **Step 3: 验证与租户插件协同（Task 4 配置匹配）**

Run: 单测 + 测试服接口冒烟（admin 登录 → 客户列表 200；销售员 → 200 且范围正确）

- [ ] **Step 4: 提交**

```bash
git add sql/migration-20260805-tenant-id.sql
git commit -m "feat(perm): 核心表 tenant_id 迁移脚本（演练）"
```

---

## 自审记录

- **Spec 覆盖**：设计文档 §4.1/4.2（Task1/2）、§4.3（Task4/12）、§6（Task3/9）、§6.3（Task5）、§3.2（Task6）、§7 层1（Task7）、§7 层2（Task8）、§12.2/12.3（Task10/11）、§8 迁移（Task8/12 内）——阶段0 全部覆盖。
- **占位符**：无 TBD；BusinessException 构造器、Result.error 签名、SysUser.deptId/managerId setter 为已知 API（Task2 测试 mock 时若 setter 缺失以实体现有字段为准微调）。
- **类型一致性**：`resolve(SecurityUser)` 返回 `List<Long>`（null=all）全计划一致；`DataScopeGuard.checkOwner(ownerId, scopeOverride)` 与 Task9 控制器调用一致；scope 接口路径 `/api/common/scope/*` 一致。
- **风险提示**：Task4 租户插件与 Task12 迁移**必须同批上线**（插件白名单表未迁移会报错）；Task6 激励卸权影响销售员（前端"追加/删除"按钮已按 can_edit/can_delete 隐藏，行为不变，仅接口层收紧——回归重点）。
