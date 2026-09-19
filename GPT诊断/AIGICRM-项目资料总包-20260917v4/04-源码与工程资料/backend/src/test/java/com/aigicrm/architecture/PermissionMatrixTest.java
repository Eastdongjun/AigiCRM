package com.aigicrm.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 权限矩阵自动化回归测试
 *
 * 【需求】Controller 写方法（POST/PUT/DELETE/PATCH）必须携带 @PreAuthorize 权限码注解，
 *         且所有权限码必须存在于权限码字典（docs/permission/permission-dict.md）。
 *         防止：新增接口忘挂权限码、引用已删除/未登记的权限码、字典与代码双向漂移。
 * 【方法】纯 JUnit5 + ArchUnit ClassFileImporter（与 ArchitectureTest 一致的类导入方式）+ 反射，
 *         不启动 Spring 上下文。
 * 【规则】
 *   正向（硬断言）：写方法必须命中 hasAuthority/hasAnyAuthority 权限码，或命中例外白名单；
 *                   提取出的每个权限码必须存在于权限码字典。
 *   反向（warning）：字典中存在、但 main 源码（含接口注解与服务层守卫）完全未引用的码 → 打印清单，不断言失败。
 * 【白名单】写方法命中「类名.方法名」精确匹配则跳过权限码校验（均为有意的设计取舍，见 WHITELIST 注释）。
 */
public class PermissionMatrixTest {

    /** 权限码字典相对路径：测试运行工作目录为 aigicrm-backend/，回溯到 04-源码/docs/permission/ */
    private static final String DICT_REL_PATH = "../docs/permission/permission-dict.md";

    /**
     * 例外白名单（类名 + 方法名 精确匹配，命中则跳过权限码校验）：
     * - AuthController：匿名/自服务端点（login/logout/forgot-password 系列）
     * - SysUserController.changePassword：自服务改密
     * - IncentiveController.updateRecord/deleteRecord：方法内守卫（admin ∨ 矩阵码 ∨ 用户级授权 ∨ 本人）
     * - FileController 写方法：服务层 FolderPermissionService 守卫
     * - SeaController.deletePool：入口已补 sea:view；池级 delete 权限继续在 SeaService.deletePool 内判定。
     * - ReminderController.process：有意用 reminder:view 保护写操作（设计取舍）
     * 【2026-08-14 声明式权限补齐】CustomerController 14 个客户写端点已补 @PreAuthorize('customer:edit')、
     *   FolderAclController 5 个端点已补 @PreAuthorize('file:acl:manage'), 移出白名单由正向规则硬校验;
     *   行级守卫 assertCanEdit / 文件夹 P_EDIT 保留为防御纵深(见 customerEditWriteMethods_mustRetainAssertCanEditGuard)。
     */
    private static final Set<String> WHITELIST = Set.of(
            "AuthController.login",
            "AuthController.logout",
            "AuthController.switchGroup", // 【批④ 2026-08-10】自服务会话切换(校验集团归属后重签 token, 类 logout)
            "AuthController.forgotPasswordLookup",
            "AuthController.forgotPasswordSendCode",
            "AuthController.forgotPasswordReset",
            "SysUserController.changePassword",
            "IncentiveController.updateRecord",
            "IncentiveController.deleteRecord",
            "FileController.upload",
            "FileController.createFolder",
            "FileController.renameFolder",
            "FileController.deleteFolder",
            "FileController.deleteFile",
            "FileController.replaceFile",
            "FileController.shareFile",
            "ReminderController.process");

    /**
     * 字典中保留但不再作为运行时 @PreAuthorize 或服务层 hasAuthority 门禁使用的兼容/历史码。
     * 这些码必须继续出现在权限字典里，并在字典备注中声明“不作为/已废弃/历史残留/统一使用/授权表”口径。
     * 这样反向规则只提示真正的“字典有码但代码和设计均无归属”的漂移。
     */
    private static final Set<String> DOCUMENTED_NON_RUNTIME_PERMISSION_CODES = Set.of(
            "customer:share",
            "incentive:delete",
            "incentive:view-all",
            "incentive:view-shared",
            "score:ranking",
            "sea:pool:delete",
            "statistics:scoreboard",
            "system:menu:edit");

    /** 匹配 hasAuthority('x') / hasAnyAuthority('a','b') / hasAnyAuthority('a', 'b') */
    private static final Pattern AUTHORITY_CALL =
            Pattern.compile("has(?:Any)?Authority\\s*\\(\\s*(.*?)\\s*\\)");

    /** 字典表格行：| `权限码` | ...（只匹配第一列反引号内的码） */
    private static final Pattern DICT_ROW = Pattern.compile("^\\|\\s*`([^`]+)`\\s*\\|");

    /** 扫描结果：一个写方法及其 HTTP 方法注解名 */
    private record WriteMethod(Class<?> clazz, Method method, String mapping) {
        String key() {
            return clazz.getSimpleName() + "." + method.getName();
        }
    }

    // ==================== 正向规则（硬断言） ====================

    @Test
    void writeMethodsRequireKnownPermissionCode() {
        Set<String> dict = loadPermissionCodes();
        List<WriteMethod> writeMethods = scanWriteMethods();

        assertFalse(writeMethods.isEmpty(),
                "未扫描到任何写方法，类扫描可能失效（检查 ArchUnit 导入与 controller 包路径）");

        // 白名单校验：每条白名单必须能匹配到真实写方法（防止方法改名后白名单失效而静默放行）
        Set<String> actualWriteKeys = writeMethods.stream()
                .map(WriteMethod::key)
                .collect(Collectors.toSet());
        Set<String> staleWhitelist = new TreeSet<>(WHITELIST);
        staleWhitelist.removeAll(actualWriteKeys);
        assertTrue(staleWhitelist.isEmpty(),
                () -> "白名单中存在未匹配到写方法的条目（可能已被改名/删除，请同步更新）：\n"
                        + String.join("\n", staleWhitelist));

        List<String> failures = new ArrayList<>();
        for (WriteMethod wm : writeMethods) {
            if (WHITELIST.contains(wm.key())) {
                continue;
            }
            PreAuthorize pa = wm.method().getAnnotation(PreAuthorize.class);
            if (pa == null) {
                failures.add("缺少 @PreAuthorize 权限码注解: " + describe(wm));
                continue;
            }
            Set<String> codes = extractAuthorityCodes(pa.value());
            if (codes.isEmpty()) {
                failures.add("@PreAuthorize 无权限码（需 hasAuthority/hasAnyAuthority 形式）: "
                        + describe(wm) + " 表达式=" + pa.value());
                continue;
            }
            for (String code : codes) {
                if (!dict.contains(code)) {
                    failures.add("权限码不在权限码字典: " + describe(wm) + " 缺失码=" + code
                            + " 表达式=" + pa.value());
                }
            }
        }
        assertTrue(failures.isEmpty(),
                () -> "权限矩阵违规（写方法缺权限码或引用未知权限码）：\n" + String.join("\n", failures));
    }

    /** 【2026-08-14】防止权限注解与行级守卫双向静默腐化:
     *  14 个客户写端点须全部携带 @PreAuthorize("hasAuthority('customer:edit')") 声明式注解,
     *  且方法体内 assertCanEdit 行级守卫必须保留(防御纵深, 正向规则只校验注解不校验守卫) */
    @Test
    void customerEditWriteMethods_mustRetainAssertCanEditGuard() {
        Path src = Paths.get("src/main/java/com/aigicrm/module/customer/controller/CustomerController.java")
                .toAbsolutePath().normalize();
        assertTrue(Files.exists(src), "CustomerController 源文件不存在: " + src);
        String body;
        try {
            body = Files.readString(src);
        } catch (IOException e) {
            throw new IllegalStateException("读取 CustomerController 源码失败", e);
        }
        long annotated = countSubstring(body, "hasAuthority('customer:edit')");
        long guards = countSubstring(body, "assertCanEdit(");
        assertTrue(annotated >= 14,
                () -> "客户写端点应补全 14 处 @PreAuthorize(\"hasAuthority('customer:edit')\"), 实际 " + annotated);
        assertTrue(guards >= 14,
                () -> "客户写端点须保留 14 处 assertCanEdit 行级守卫(防御纵深), 实际 " + guards
                        + " 处——守卫缺失, 权限将无兜底");
    }

    /** 财务写入端点必须同时要求客户编辑权限与财务查看权限，避免绕过财务页签可见性直接写入。 */
    @Test
    void customerFinanceMutationEndpoints_requireCustomerEditAndFinanceView() throws Exception {
        Class<com.aigicrm.module.customer.controller.CustomerController> controller =
                com.aigicrm.module.customer.controller.CustomerController.class;

        assertPreAuthorize(controller.getDeclaredMethod("addInvoice", Long.class, com.aigicrm.module.customer.entity.BizInvoice.class),
                "hasAuthority('customer:edit') and hasAuthority('finance:view')");
        assertPreAuthorize(controller.getDeclaredMethod("deleteInvoice", Long.class, Long.class),
                "hasAuthority('customer:edit') and hasAuthority('finance:view')");
        assertPreAuthorize(controller.getDeclaredMethod("addPayment", Long.class, com.aigicrm.module.customer.entity.BizPayment.class),
                "hasAuthority('customer:edit') and hasAuthority('finance:view')");
        assertPreAuthorize(controller.getDeclaredMethod("deletePayment", Long.class, Long.class),
                "hasAuthority('customer:edit') and hasAuthority('finance:view')");
    }

    /** 客户分享的产品口径：读取共享列表用 customer:view，新增/撤回共享沿用 customer:edit；
     *  legacy customer:share 只保留为字典兼容码，不作为运行时 Controller 门禁。 */
    @Test
    void customerShareEndpoints_keepDocumentedViewAndEditGates() throws Exception {
        Class<com.aigicrm.module.customer.controller.CustomerController> controller =
                com.aigicrm.module.customer.controller.CustomerController.class;

        assertPreAuthorize(controller.getDeclaredMethod("getShares", Long.class),
                "hasAuthority('customer:view')");
        assertPreAuthorize(controller.getDeclaredMethod("addShares", Long.class, java.util.Map.class),
                "hasAuthority('customer:edit')");
        assertPreAuthorize(controller.getDeclaredMethod("removeShare", Long.class, Long.class),
                "hasAuthority('customer:edit')");
    }

    /** 审批决策会改变业务状态：通过/驳回入口必须分别绑定 approval:approve / approval:reject。 */
    @Test
    void approvalDecisionEndpoints_requireApproveAndRejectPermissions() throws Exception {
        Class<com.aigicrm.module.approval.controller.ApprovalController> controller =
                com.aigicrm.module.approval.controller.ApprovalController.class;

        assertPreAuthorize(controller.getDeclaredMethod("approve", Long.class, java.util.Map.class),
                "hasAuthority('approval:approve')");
        assertPreAuthorize(controller.getDeclaredMethod("reject", Long.class, java.util.Map.class),
                "hasAuthority('approval:reject')");

        Path src = Paths.get("src/main/java/com/aigicrm/module/approval/controller/ApprovalController.java")
                .toAbsolutePath().normalize();
        assertTrue(Files.exists(src), "ApprovalController 源文件不存在: " + src);
        String body;
        try {
            body = Files.readString(src);
        } catch (IOException e) {
            throw new IllegalStateException("读取 ApprovalController 源码失败", e);
        }
        assertTrue(body.contains("approvalService.approve(id"),
                "ApprovalController.approve 必须调用 ApprovalService.approve 执行业务状态变更");
        assertTrue(body.contains("approvalService.reject(id"),
                "ApprovalController.reject 必须调用 ApprovalService.reject 执行业务状态变更");
    }

    /** 审批提交是多业务流入口：入口用 approval:view 进入审批域，服务层必须保留来源权限、并发锁和 reviewing 创建边界。 */
    @Test
    void approvalSubmitEndpoint_requiresApprovalViewAndRetainsWorkflowCreationGuards() throws Exception {
        Class<com.aigicrm.module.approval.controller.ApprovalController> controller =
                com.aigicrm.module.approval.controller.ApprovalController.class;

        assertPreAuthorize(controller.getDeclaredMethod("submit", java.util.Map.class),
                "hasAuthority('approval:view')");

        Path controllerSrc = Paths.get("src/main/java/com/aigicrm/module/approval/controller/ApprovalController.java")
                .toAbsolutePath().normalize();
        assertTrue(Files.exists(controllerSrc), "ApprovalController 源文件不存在: " + controllerSrc);
        String controllerBody;
        try {
            controllerBody = Files.readString(controllerSrc);
        } catch (IOException e) {
            throw new IllegalStateException("读取 ApprovalController 源码失败", e);
        }
        assertTrue(controllerBody.contains("firstPresent(params, \"type\", \"approvalType\")"),
                "ApprovalController.submit 必须兼容 type/approvalType 两种提交字段");
        assertTrue(controllerBody.contains("approvalService.submit("),
                "ApprovalController.submit 必须调用 ApprovalService.submit 创建审批流");

        Path serviceSrc = Paths.get("src/main/java/com/aigicrm/module/approval/service/ApprovalService.java")
                .toAbsolutePath().normalize();
        assertTrue(Files.exists(serviceSrc), "ApprovalService 源文件不存在: " + serviceSrc);
        String serviceBody;
        try {
            serviceBody = Files.readString(serviceSrc);
        } catch (IOException e) {
            throw new IllegalStateException("读取 ApprovalService 源码失败", e);
        }
        assertTrue(serviceBody.contains("\"claim\".equals(type)") && serviceBody.contains("SeaConfigService.PERM_CLAIM"),
                "ApprovalService.submit 的公海捞取分支必须保留池级 PERM_CLAIM 权限守卫");
        assertTrue(serviceBody.contains("SecurityUtils.hasAuthority(\"sea:claim\")"),
                "ApprovalService.submit 必须保留 sea:claim 兼容角色码兜底");
        assertTrue(serviceBody.contains("eq(BizSeaPool::getStatus, \"available\")"),
                "ApprovalService.submit 必须用 status=available 条件更新锁定公海记录，防止并发双捞");
        assertTrue(serviceBody.contains("approval.setStatus(\"reviewing\")"),
                "ApprovalService.submit 创建审批时必须进入 reviewing 状态");
        assertTrue(serviceBody.contains("approvalMapper.insert(approval)") && serviceBody.contains("approvalNodeMapper.insert(node)"),
                "ApprovalService.submit 必须创建审批主记录和审批节点");
    }

    /** 审批读取链路必须保留 approval:view 入口，以及列表数据范围和详情可见性守卫。 */
    @Test
    void approvalReadEndpoints_requireApprovalViewAndRetainScopeGuards() throws Exception {
        Class<com.aigicrm.module.approval.controller.ApprovalController> controller =
                com.aigicrm.module.approval.controller.ApprovalController.class;

        assertPreAuthorize(controller.getDeclaredMethod("page", int.class, int.class, String.class,
                        String.class, String.class, Long.class),
                "hasAuthority('approval:view')");
        assertPreAuthorize(controller.getDeclaredMethod("detail", Long.class),
                "hasAuthority('approval:view')");
        assertPreAuthorize(controller.getDeclaredMethod("statistics"),
                "hasAuthority('approval:view')");

        Path serviceSrc = Paths.get("src/main/java/com/aigicrm/module/approval/service/ApprovalService.java")
                .toAbsolutePath().normalize();
        assertTrue(Files.exists(serviceSrc), "ApprovalService 源文件不存在: " + serviceSrc);
        String serviceBody;
        try {
            serviceBody = Files.readString(serviceSrc);
        } catch (IOException e) {
            throw new IllegalStateException("读取 ApprovalService 源码失败", e);
        }
        assertTrue(serviceBody.contains("\"pending\".equals(tab)") && serviceBody.contains("BizApprovalNode::getApproverId"),
                "ApprovalService.page 的 pending 标签必须只返回当前审批人的待审批节点");
        assertTrue(serviceBody.contains("\"mine\".equals(tab)") && serviceBody.contains("BizApproval::getApplicantId"),
                "ApprovalService.page 的 mine 标签必须按当前申请人过滤");
        assertTrue(serviceBody.contains("applyDataScope(wrapper, userId)"),
                "ApprovalService.page 必须对非 admin 历史/全量列表应用数据范围");
        assertTrue(serviceBody.contains("applyKeywordFilter(wrapper, keyword)"),
                "ApprovalService.page 必须保留审批号/客户/申请人关键字过滤");
        assertTrue(serviceBody.contains("canViewApproval(approval, userId)"),
                "ApprovalService.detail 必须保留详情可见性校验");
        assertTrue(serviceBody.contains("approvalNodeMapper.selectCount") && serviceBody.contains("scopeResolver.resolve"),
                "ApprovalService.canViewApproval 必须允许申请人/审批人查看，并回退到组织数据范围");
        assertTrue(serviceBody.contains("result.put(\"approval\", approval)") && serviceBody.contains("result.put(\"nodes\", nodes)"),
                "ApprovalService.detail 必须返回审批主记录和节点时间线");
    }

    /** 组织架构入口必须区分基础参考数据读取、组织管理员读取和部门管理写操作。 */
    @Test
    void systemDeptEndpoints_keepAuthenticatedReadAndDeptViewEditBoundaries() throws Exception {
        Class<com.aigicrm.module.system.controller.SysDeptController> controller =
                com.aigicrm.module.system.controller.SysDeptController.class;

        assertPreAuthorize(controller.getDeclaredMethod("tree"),
                "isAuthenticated()");
        assertPreAuthorize(controller.getDeclaredMethod("list"),
                "isAuthenticated()");
        assertPreAuthorize(controller.getDeclaredMethod("getAdmins", Long.class),
                "hasAuthority('system:dept:view')");

        assertPreAuthorize(controller.getDeclaredMethod("create",
                        com.aigicrm.module.system.entity.SysDept.class),
                "hasAuthority('system:dept:edit')");
        assertPreAuthorize(controller.getDeclaredMethod("update", Long.class,
                        com.aigicrm.module.system.entity.SysDept.class),
                "hasAuthority('system:dept:edit')");
        assertPreAuthorize(controller.getDeclaredMethod("delete", Long.class),
                "hasAuthority('system:dept:edit')");
        assertPreAuthorize(controller.getDeclaredMethod("addAdmin", Long.class, Long.class),
                "hasAuthority('system:dept:edit')");
        assertPreAuthorize(controller.getDeclaredMethod("removeAdmin", Long.class, Long.class),
                "hasAuthority('system:dept:edit')");
    }

    /** 系统核心管理入口必须保留用户、角色、集团各自的读写删权限边界，组织管理员只能走 org-admin 兼容口。 */
    @Test
    void systemUserRoleAndGroupEndpoints_keepPreciseManagementPermissionsAndOrgAdminGuards() throws Exception {
        Class<com.aigicrm.module.system.controller.SysUserController> userController =
                com.aigicrm.module.system.controller.SysUserController.class;
        assertPreAuthorize(userController.getDeclaredMethod("page", int.class, int.class, String.class,
                        String.class, Integer.class),
                "hasAnyAuthority('system:user:view','system:org:admin')");
        assertPreAuthorize(userController.getDeclaredMethod("getById", Long.class),
                "hasAnyAuthority('system:user:view','system:org:admin')");
        assertPreAuthorize(userController.getDeclaredMethod("create",
                        com.aigicrm.module.system.entity.SysUser.class),
                "hasAnyAuthority('system:user:add','system:org:admin')");
        assertPreAuthorize(userController.getDeclaredMethod("update", Long.class,
                        com.aigicrm.module.system.entity.SysUser.class),
                "hasAnyAuthority('system:user:edit','system:org:admin')");
        assertPreAuthorize(userController.getDeclaredMethod("resetPassword", Long.class),
                "hasAnyAuthority('system:user:edit','system:org:admin')");
        assertPreAuthorize(userController.getDeclaredMethod("delete", Long.class),
                "hasAnyAuthority('system:user:delete','system:org:admin')");

        Path userSrc = Paths.get("src/main/java/com/aigicrm/module/system/controller/SysUserController.java")
                .toAbsolutePath().normalize();
        assertTrue(Files.exists(userSrc), "SysUserController 源文件不存在: " + userSrc);
        String userBody;
        try {
            userBody = Files.readString(userSrc);
        } catch (IOException e) {
            throw new IllegalStateException("读取 SysUserController 源码失败", e);
        }
        assertTrue(userBody.contains("SecurityUtils.isOrgAdmin()") && userBody.contains("orgAdminService.canManageOrg"),
                "SysUserController.create 必须保留组织管理员目标组织范围守卫");
        assertTrue(userBody.contains("orgAdminService.canManageUser(id)") && countSubstring(userBody, "无权操作该用户") >= 3,
                "SysUserController update/reset/delete 必须保留组织管理员目标用户范围守卫");

        Class<com.aigicrm.module.system.controller.SysRoleController> roleController =
                com.aigicrm.module.system.controller.SysRoleController.class;
        assertPreAuthorize(roleController.getDeclaredMethod("list"),
                "hasAuthority('system:role:view')");
        assertPreAuthorize(roleController.getDeclaredMethod("getById", Long.class),
                "hasAuthority('system:role:view')");
        assertPreAuthorize(roleController.getDeclaredMethod("getOrgs", Long.class),
                "hasAuthority('system:role:view')");
        assertPreAuthorize(roleController.getDeclaredMethod("create",
                        com.aigicrm.module.system.entity.SysRole.class),
                "hasAuthority('system:role:add')");
        assertPreAuthorize(roleController.getDeclaredMethod("update", Long.class,
                        com.aigicrm.module.system.entity.SysRole.class),
                "hasAuthority('system:role:edit')");
        assertPreAuthorize(roleController.getDeclaredMethod("updateOrgs", Long.class, List.class),
                "hasAuthority('system:role:edit')");
        assertPreAuthorize(roleController.getDeclaredMethod("delete", Long.class),
                "hasAuthority('system:role:delete')");

        Class<com.aigicrm.module.system.controller.SysGroupController> groupController =
                com.aigicrm.module.system.controller.SysGroupController.class;
        assertPreAuthorize(groupController.getDeclaredMethod("list"),
                "hasAuthority('system:group:view')");
        assertPreAuthorize(groupController.getDeclaredMethod("create",
                        com.aigicrm.module.system.entity.SysGroup.class),
                "hasAuthority('system:group:add')");
        assertPreAuthorize(groupController.getDeclaredMethod("update", Long.class,
                        com.aigicrm.module.system.entity.SysGroup.class),
                "hasAuthority('system:group:edit')");
        assertPreAuthorize(groupController.getDeclaredMethod("delete", Long.class),
                "hasAuthority('system:group:delete')");
    }

    /** 系统日志是审计面：列表和聚合计数必须都只读且绑定 system:log:view。 */
    @Test
    void systemLogEndpoints_areReadOnlyAndRequireLogViewPermission() throws Exception {
        Class<com.aigicrm.module.system.controller.SysLogController> controller =
                com.aigicrm.module.system.controller.SysLogController.class;

        assertPreAuthorize(controller.getDeclaredMethod("page", int.class, int.class, String.class,
                        String.class, String.class, String.class, String.class),
                "hasAuthority('system:log:view')");
        assertPreAuthorize(controller.getDeclaredMethod("counts", String.class, String.class),
                "hasAuthority('system:log:view')");
    }

    /** 系统配置编辑必须受 system:config:edit 保护；公开配置/Logo 可匿名读取但只能暴露白名单和 public 文件。 */
    @Test
    void systemConfigPublicAndLogoEndpoints_keepAnonymousReadButSafePublicBoundary() throws Exception {
        Class<com.aigicrm.module.system.controller.SysConfigController> controller =
                com.aigicrm.module.system.controller.SysConfigController.class;

        assertPreAuthorize(controller.getDeclaredMethod("list"),
                "hasAuthority('system:config:view')");
        assertPreAuthorize(controller.getDeclaredMethod("batchUpdate", List.class),
                "hasAuthority('system:config:edit')");
        assertTrue(controller.getDeclaredMethod("publicConfig").getAnnotation(PreAuthorize.class) == null,
                "publicConfig 是登录页公开配置入口，不应绑定后台配置权限");
        assertTrue(controller.getDeclaredMethod("logo").getAnnotation(PreAuthorize.class) == null,
                "logo 是登录页品牌资源入口，不应绑定后台配置权限");

        Path src = Paths.get("src/main/java/com/aigicrm/module/system/controller/SysConfigController.java")
                .toAbsolutePath().normalize();
        assertTrue(Files.exists(src), "SysConfigController 源文件不存在: " + src);
        String body;
        try {
            body = Files.readString(src);
        } catch (IOException e) {
            throw new IllegalStateException("读取 SysConfigController 源码失败", e);
        }
        assertTrue(body.contains("java.util.Set.of(")
                        && body.contains("\"system.name\"")
                        && body.contains("\"system.logo\"")
                        && body.contains("\"invoice.overdue.days\""),
                "publicConfig 必须保留显式白名单，不能匿名暴露全部系统配置");
        assertTrue(body.contains("logoUrl.startsWith(\"/api/file/download/\")")
                        && body.contains("!fileKey.startsWith(\"public/\")")
                        && body.contains("filePath.startsWith(uploadRoot)"),
                "logo 必须只允许 public 文件并保留路径穿越防护");
        assertTrue(body.contains("transparentSvg()"),
                "logo 配置缺失、越界或异常时必须回退透明 SVG，避免登录页破图或泄露错误");
    }

    /** 【2026-08-14】FolderAclController 5 个端点必须全部收口到 file:acl:manage(替换原 isAuthenticated), 一个都不能漏 */
    @Test
    void folderAclEndpoints_requireFileAclManage() {
        Set<String> annotated = new TreeSet<>();
        for (Method m : com.aigicrm.module.file.controller.FolderAclController.class.getDeclaredMethods()) {
            PreAuthorize pa = m.getAnnotation(PreAuthorize.class);
            if (pa != null && extractAuthorityCodes(pa.value()).equals(Set.of("file:acl:manage"))) {
                annotated.add(m.getName());
            }
        }
        assertEquals(Set.of("listAcl", "saveAcl", "removeAcl", "setDownloadable", "permissions"), annotated,
                "FolderAclController 5 个端点必须全部携带 hasAuthority('file:acl:manage')");
    }

    /** 文件删除是高风险动作：入口必须要求 file:delete，服务层仍必须保留文件夹级 P_DELETE 守卫。 */
    @Test
    void fileDeleteEndpoint_requiresFileDeleteAndRetainsFolderDeleteGuard() throws Exception {
        Method deleteFile = com.aigicrm.module.file.controller.FileController.class
                .getDeclaredMethod("deleteFile", Long.class);

        assertPreAuthorize(deleteFile, "hasAuthority('file:delete')");

        Path src = Paths.get("src/main/java/com/aigicrm/module/file/service/FileService.java")
                .toAbsolutePath().normalize();
        assertTrue(Files.exists(src), "FileService 源文件不存在: " + src);
        String body;
        try {
            body = Files.readString(src);
        } catch (IOException e) {
            throw new IllegalStateException("读取 FileService 源码失败", e);
        }
        assertTrue(body.contains("checkFile(fileId, FolderPermissionService.P_DELETE"),
                "FileService.deleteFile 必须保留 FolderPermissionService.P_DELETE 细粒度删除守卫");
    }

    /** 文件主链路必须保留入口权限码，并继续下沉到文件夹 ACL 细粒度动作。 */
    @Test
    void fileUploadDownloadReplaceShareAndFolderManageEndpoints_keepPrecisePermissionsAndAclGuards() throws Exception {
        Class<com.aigicrm.module.file.controller.FileController> controller =
                com.aigicrm.module.file.controller.FileController.class;

        assertPreAuthorize(controller.getDeclaredMethod("getFolderTree", String.class),
                "hasAuthority('file:view')");
        assertPreAuthorize(controller.getDeclaredMethod("pageFiles", int.class, int.class, Long.class,
                        String.class, String.class),
                "hasAuthority('file:view')");
        assertPreAuthorize(controller.getDeclaredMethod("getStorageInfo"),
                "hasAuthority('file:view')");
        assertPreAuthorize(controller.getDeclaredMethod("downloadById", Long.class, String.class,
                        boolean.class, jakarta.servlet.http.HttpServletResponse.class),
                "hasAuthority('file:view')");
        assertPreAuthorize(controller.getDeclaredMethod("download", jakarta.servlet.http.HttpServletRequest.class),
                "hasAuthority('file:view')");

        assertPreAuthorize(controller.getDeclaredMethod("upload",
                        org.springframework.web.multipart.MultipartFile.class, Long.class, String.class),
                "hasAuthority('file:upload')");
        assertPreAuthorize(controller.getDeclaredMethod("replaceFile", Long.class,
                        org.springframework.web.multipart.MultipartFile.class),
                "hasAuthority('file:edit')");
        assertPreAuthorize(controller.getDeclaredMethod("shareFile", Long.class, java.util.Map.class),
                "hasAuthority('file:share')");

        assertPreAuthorize(controller.getDeclaredMethod("createFolder",
                        com.aigicrm.module.file.entity.BizFileFolder.class),
                "hasAuthority('file:folder:manage')");
        assertPreAuthorize(controller.getDeclaredMethod("renameFolder", Long.class, java.util.Map.class),
                "hasAuthority('file:folder:manage')");
        assertPreAuthorize(controller.getDeclaredMethod("deleteFolder", Long.class),
                "hasAuthority('file:folder:manage')");

        Path src = Paths.get("src/main/java/com/aigicrm/module/file/service/FileService.java")
                .toAbsolutePath().normalize();
        assertTrue(Files.exists(src), "FileService 源文件不存在: " + src);
        String body;
        try {
            body = Files.readString(src);
        } catch (IOException e) {
            throw new IllegalStateException("读取 FileService 源码失败", e);
        }
        assertTrue(body.contains("folderPermissionService.check(folderId, FolderPermissionService.P_UPLOAD"),
                "FileService.upload 必须保留文件夹 P_UPLOAD 权限守卫");
        assertTrue(body.contains("folderPermissionService.check(folder.getParentId(), FolderPermissionService.P_UPLOAD"),
                "FileService.createFolder 必须保留父文件夹 P_UPLOAD 权限守卫");
        assertTrue(body.contains("folderPermissionService.check(folder.getId(), FolderPermissionService.P_EDIT"),
                "FileService.renameFolder 必须保留文件夹 P_EDIT 权限守卫");
        assertTrue(body.contains("folderPermissionService.check(folder.getId(), FolderPermissionService.P_DELETE"),
                "FileService.deleteFolder 必须保留文件夹 P_DELETE 权限守卫");
        assertTrue(body.contains("folderPermissionService.checkFile(fileId, FolderPermissionService.P_EDIT"),
                "FileService.replaceFile 必须保留文件 P_EDIT 权限守卫");
        assertTrue(body.contains("folderPermissionService.checkFile(file.getId(), FolderPermissionService.P_DOWNLOAD")
                        || body.contains("folderPermissionService.checkFile(fileId, FolderPermissionService.P_DOWNLOAD"),
                "FileService 下载链路必须保留文件 P_DOWNLOAD 权限守卫");
        assertTrue(body.contains("folderPermissionService.checkFile(fileId, FolderPermissionService.P_SHARE"),
                "FileService.shareFile 必须保留文件 P_SHARE 权限守卫");
    }

    /** 下载 URL 不得携带主 JWT：download-token 只能签发短时 scope=download token，并由过滤器限制到下载/导出路径。 */
    @Test
    void fileDownloadTokenEndpoint_issuesScopedShortLivedTokensOnlyForDownloadPaths() throws Exception {
        Class<com.aigicrm.module.file.controller.FileController> controller =
                com.aigicrm.module.file.controller.FileController.class;
        Method downloadToken = controller.getDeclaredMethod("downloadToken");

        assertTrue(downloadToken.isAnnotationPresent(org.springframework.web.bind.annotation.GetMapping.class),
                "FileController.downloadToken 必须保留 GET 入口");
        assertEquals("/download-token", downloadToken.getAnnotation(org.springframework.web.bind.annotation.GetMapping.class).value()[0],
                "file.download-token 路径不能漂移");
        assertTrue(downloadToken.getAnnotation(PreAuthorize.class) == null,
                "download-token 依赖当前认证上下文签发短时 token，不应错误绑定 file:view 或其他文件权限码");

        Path controllerSrc = Paths.get("src/main/java/com/aigicrm/module/file/controller/FileController.java")
                .toAbsolutePath().normalize();
        assertTrue(Files.exists(controllerSrc), "FileController 源文件不存在: " + controllerSrc);
        String controllerBody;
        try {
            controllerBody = Files.readString(controllerSrc);
        } catch (IOException e) {
            throw new IllegalStateException("读取 FileController 源码失败", e);
        }
        assertTrue(controllerBody.contains("jwtTokenProvider.generateDownloadToken(SecurityUtils.getCurrentUserId())"),
                "downloadToken 必须用当前登录用户签发下载专用 token，不能签发匿名或固定用户 token");

        Path providerSrc = Paths.get("src/main/java/com/aigicrm/security/JwtTokenProvider.java")
                .toAbsolutePath().normalize();
        assertTrue(Files.exists(providerSrc), "JwtTokenProvider 源文件不存在: " + providerSrc);
        String providerBody;
        try {
            providerBody = Files.readString(providerSrc);
        } catch (IOException e) {
            throw new IllegalStateException("读取 JwtTokenProvider 源码失败", e);
        }
        assertTrue(providerBody.contains(".claim(\"scope\", \"download\")"),
                "generateDownloadToken 必须写入 scope=download，供过滤器识别 URL token");
        assertTrue(providerBody.contains("DOWNLOAD_TOKEN_TTL_MS = 3600_000L")
                        && providerBody.contains("now.getTime() + DOWNLOAD_TOKEN_TTL_MS"),
                "generateDownloadToken 必须保持短时 1h 有效期，避免 URL token 长期暴露");

        Path filterSrc = Paths.get("src/main/java/com/aigicrm/security/JwtAuthFilter.java")
                .toAbsolutePath().normalize();
        assertTrue(Files.exists(filterSrc), "JwtAuthFilter 源文件不存在: " + filterSrc);
        String filterBody;
        try {
            filterBody = Files.readString(filterSrc);
        } catch (IOException e) {
            throw new IllegalStateException("读取 JwtAuthFilter 源码失败", e);
        }
        assertTrue(filterBody.contains("isDownloadPath(request)") && filterBody.contains("jwtTokenProvider.getScopeFromToken(queryToken)"),
                "JwtAuthFilter 必须按 scope 判断 URL token 是否可用");
        assertTrue(filterBody.contains("\"download\".equals(jwtTokenProvider.getScopeFromToken(queryToken))"),
                "JwtAuthFilter 只应接受 scope=download 的 URL token");
        assertTrue(filterBody.contains("uri.startsWith(\"/api/file/download/\")")
                        && filterBody.contains("uri.startsWith(\"/api/score/export/\")"),
                "下载专用 URL token 只能用于文件下载和积分导出路径");
        assertTrue(filterBody.contains("if (StringUtils.hasText(queryToken)")
                        && filterBody.contains("return queryToken;")
                        && filterBody.contains("return null;"),
                "JwtAuthFilter 必须在 query token 不是 scope=download 时返回 null，拒绝把主 JWT 放进 URL 参数");
    }

    /** public/item 下载入口可以匿名访问，但必须把真实路径限制在上传根目录下的 public 子树，并沿用安全下载头。 */
    @Test
    void fileItemPublicDownloadEndpoint_rejectsNonPublicAndPathTraversal() throws Exception {
        Method downloadPublic = com.aigicrm.module.file.controller.FileController.class
                .getDeclaredMethod("downloadPublic", jakarta.servlet.http.HttpServletRequest.class);

        assertTrue(downloadPublic.isAnnotationPresent(org.springframework.web.bind.annotation.GetMapping.class),
                "FileController.downloadPublic 必须保留 GET 入口");
        assertEquals("/download/public/**", downloadPublic.getAnnotation(org.springframework.web.bind.annotation.GetMapping.class).value()[0],
                "file.item/public 下载路径不能漂移");
        assertTrue(downloadPublic.getAnnotation(PreAuthorize.class) == null,
                "public 下载入口必须保持匿名可访问，但在方法体内限制 public 子树");

        Path controllerSrc = Paths.get("src/main/java/com/aigicrm/module/file/controller/FileController.java")
                .toAbsolutePath().normalize();
        assertTrue(Files.exists(controllerSrc), "FileController 源文件不存在: " + controllerSrc);
        String body;
        try {
            body = Files.readString(controllerSrc);
        } catch (IOException e) {
            throw new IllegalStateException("读取 FileController 源码失败", e);
        }
        assertTrue(body.contains("substring(\"/api/file/download/\".length())"),
                "downloadPublic 必须从真实下载前缀后解析对象 key，避免 public 路由切割错误");
        assertTrue(body.contains("toAbsolutePath().normalize()") && body.contains("uploadRoot.resolve(rawKey).normalize()"),
                "downloadPublic 必须先归一化上传根和目标路径");
        assertTrue(body.contains("!resolved.startsWith(uploadRoot)") && body.contains("ResponseEntity.badRequest().build()"),
                "downloadPublic 必须拒绝路径穿越到上传根之外");
        assertTrue(body.contains("uploadRoot.relativize(resolved)")
                        && body.contains("!\"public\".equals(relative.getName(0).toString())")
                        && body.contains("ResponseEntity.status(403).build()"),
                "downloadPublic 必须拒绝 public 子树之外的对象");
        assertTrue(body.contains("FileService.dispositionFor(filename)"),
                "downloadPublic 必须沿用 FileService.dispositionFor，防 SVG/HTML 匿名存储型 XSS");
        assertTrue(body.contains("MediaTypeFactory.getMediaType(filename)"),
                "downloadPublic 必须保留响应 content-type 推断，避免下载体验退化");
    }

    /** 公海导入会批量新建客户并入池：入口必须要求 sea:import，服务层必须保留目标池路由与池级导入权限。 */
    @Test
    void seaImportEndpoint_requiresSeaImportAndRetainsPoolImportGuard() throws Exception {
        Method importToSea = com.aigicrm.module.sea.controller.SeaController.class
                .getDeclaredMethod("importToSea", org.springframework.web.multipart.MultipartFile.class);

        assertPreAuthorize(importToSea, "hasAuthority('sea:import')");

        Path src = Paths.get("src/main/java/com/aigicrm/module/sea/service/SeaService.java")
                .toAbsolutePath().normalize();
        assertTrue(Files.exists(src), "SeaService 源文件不存在: " + src);
        String body;
        try {
            body = Files.readString(src);
        } catch (IOException e) {
            throw new IllegalStateException("读取 SeaService 源码失败", e);
        }
        assertTrue(body.contains("Long targetPoolId = matchSeaConfig(currentOfficeId);"),
                "SeaService.importToSea 必须按导入人当前办事处路由目标公海池");
        assertTrue(body.contains("hasPoolPerm(targetPoolId, SeaConfigService.PERM_IMPORT"),
                "SeaService.importToSea 必须保留 SeaConfigService.PERM_IMPORT 池级导入权限守卫");
    }

    /** 公海配置会暴露全组织与权限范围：查看入口必须用 sea:config:view，变更入口必须用 sea:config:edit。 */
    @Test
    void seaConfigEndpoints_requireConfigViewAndEditPermissions() throws Exception {
        Class<com.aigicrm.module.sea.controller.SeaController> controller =
                com.aigicrm.module.sea.controller.SeaController.class;

        assertPreAuthorize(controller.getDeclaredMethod("listConfigs"),
                "hasAuthority('sea:config:view')");
        assertPreAuthorize(controller.getDeclaredMethod("orgTree"),
                "hasAuthority('sea:config:view')");
        assertPreAuthorize(controller.getDeclaredMethod("createConfig",
                        com.aigicrm.module.sea.entity.BizSeaConfig.class),
                "hasAuthority('sea:config:edit')");
        assertPreAuthorize(controller.getDeclaredMethod("updateConfig", Long.class,
                        com.aigicrm.module.sea.entity.BizSeaConfig.class),
                "hasAuthority('sea:config:edit')");
        assertPreAuthorize(controller.getDeclaredMethod("deleteConfig", Long.class),
                "hasAuthority('sea:config:edit')");

        Path src = Paths.get("src/main/java/com/aigicrm/module/sea/service/SeaConfigService.java")
                .toAbsolutePath().normalize();
        assertTrue(Files.exists(src), "SeaConfigService 源文件不存在: " + src);
        String body;
        try {
            body = Files.readString(src);
        } catch (IOException e) {
            throw new IllegalStateException("读取 SeaConfigService 源码失败", e);
        }
        assertTrue(body.contains("config.setRegionIds(deriveRegionIds"),
                "SeaConfigService 必须在创建/更新配置时派生 region_ids，避免释放路由失效");
        assertTrue(body.contains("checkOfficeOverlap(config);"),
                "SeaConfigService 必须校验办事处重叠，避免同一办事处路由到多个公海池");
        assertTrue(body.contains("seaPoolMapper.selectCount"),
                "SeaConfigService.delete 必须保留池内客户检查，避免删除仍被客户引用的公海池");
    }

    /** 公海删除入口以 sea:view 进入公海域，最终必须由服务层按状态、池级删除权限和可见池范围收口。 */
    @Test
    void seaDeleteEndpoint_requiresSeaViewAndRetainsPoolDeleteGuard() throws Exception {
        Method deletePool = com.aigicrm.module.sea.controller.SeaController.class
                .getDeclaredMethod("deletePool", Long.class);

        assertPreAuthorize(deletePool, "hasAuthority('sea:view')");
        assertTrue(loadPermissionCodes().contains("sea:pool:delete"),
                "兼容权限码 sea:pool:delete 必须保留在权限字典中，用于角色配置和作战图追踪；运行时删除边界由池级 PERM_DELETE 守卫控制");

        Path src = Paths.get("src/main/java/com/aigicrm/module/sea/service/SeaService.java")
                .toAbsolutePath().normalize();
        assertTrue(Files.exists(src), "SeaService 源文件不存在: " + src);
        String body;
        try {
            body = Files.readString(src);
        } catch (IOException e) {
            throw new IllegalStateException("读取 SeaService 源码失败", e);
        }
        assertTrue(body.contains("\"available\".equals(pool.getStatus())"),
                "SeaService.deletePool 必须只允许删除待捞取 available 状态的公海客户");
        assertTrue(body.contains("hasPoolPerm(pool.getPoolId(), SeaConfigService.PERM_DELETE"),
                "SeaService.deletePool 必须保留 SeaConfigService.PERM_DELETE 池级删除权限守卫");
        assertTrue(body.contains("visiblePoolIds") && body.contains("!visiblePoolIds.contains(pool.getPoolId())"),
                "SeaService.deletePool 必须保留可见池范围校验，防止跨池横向删除");
        assertTrue(body.contains("customerMapper.deleteById(pool.getCustomerId())"),
                "SeaService.deletePool 必须逻辑删除对应客户，保持客户列表与公海池互斥");
        assertTrue(body.contains("seaPoolMapper.deleteById(seaPoolId)"),
                "SeaService.deletePool 必须物理删除对应公海池记录");
    }

    /** 公海列表是公海域主入口：列表/统计/可见池/捞取记录必须保留 sea:view，并返回池级行操作标记。 */
    @Test
    void seaListEndpoints_requireSeaViewAndRetainVisiblePoolScopeAndRowFlags() throws Exception {
        Class<com.aigicrm.module.sea.controller.SeaController> controller =
                com.aigicrm.module.sea.controller.SeaController.class;

        assertPreAuthorize(controller.getDeclaredMethod("page", int.class, int.class, String.class,
                        Long.class, Long.class, String.class, String.class, Long.class, String.class),
                "hasAuthority('sea:view')");
        assertPreAuthorize(controller.getDeclaredMethod("statistics"),
                "hasAuthority('sea:view')");
        assertPreAuthorize(controller.getDeclaredMethod("listVisibleConfigs"),
                "hasAuthority('sea:view')");
        assertPreAuthorize(controller.getDeclaredMethod("claimRecords", int.class, int.class, String.class,
                        String.class, String.class, String.class),
                "hasAuthority('sea:view')");

        Path src = Paths.get("src/main/java/com/aigicrm/module/sea/service/SeaService.java")
                .toAbsolutePath().normalize();
        assertTrue(Files.exists(src), "SeaService 源文件不存在: " + src);
        String body;
        try {
            body = Files.readString(src);
        } catch (IOException e) {
            throw new IllegalStateException("读取 SeaService 源码失败", e);
        }
        assertTrue(body.contains("visiblePoolIdsOrNull()"),
                "SeaService.page/statistics 必须通过 visiblePoolIdsOrNull 收口公海池可见范围");
        assertTrue(body.contains("wrapper.in(BizSeaPool::getPoolId, visiblePoolIds)"),
                "SeaService.page 必须按可见池列表过滤公海记录");
        assertTrue(body.contains("sp.setCanEdit") && body.contains("SeaConfigService.PERM_EDIT"),
                "SeaService.page 必须返回 canEdit 行级操作标记");
        assertTrue(body.contains("sp.setCanDelete") && body.contains("SeaConfigService.PERM_DELETE"),
                "SeaService.page 必须返回 canDelete 行级操作标记");
        assertTrue(body.contains("sp.setCanClaim") && body.contains("SeaConfigService.PERM_CLAIM"),
                "SeaService.page 必须返回 canClaim 行级操作标记");
        assertTrue(body.contains("stats.put(\"total\"") && body.contains("scoped(visiblePoolIds)"),
                "SeaService.statistics 必须按可见池范围统计公海数据");
    }

    /** 认证会话端点是匿名/自服务入口：必须保留可信 IP 限流来源、注销黑名单和集团归属校验。 */
    @Test
    void authSessionEndpoints_keepAnonymousEntryAndTokenLifecycleGuards() throws Exception {
        Class<com.aigicrm.module.auth.controller.AuthController> controller =
                com.aigicrm.module.auth.controller.AuthController.class;

        assertNoPreAuthorize(controller.getDeclaredMethod("login",
                        com.aigicrm.module.auth.dto.LoginRequest.class,
                        jakarta.servlet.http.HttpServletRequest.class),
                "login 是匿名入口，认证与限流由 AuthService.login 处理");
        assertNoPreAuthorize(controller.getDeclaredMethod("getUserInfo"),
                "info 由 JWT 过滤器提供认证上下文，不应误挂业务权限码");
        assertNoPreAuthorize(controller.getDeclaredMethod("logout", jakarta.servlet.http.HttpServletRequest.class),
                "logout 是当前会话自服务端点");
        assertNoPreAuthorize(controller.getDeclaredMethod("switchGroup", java.util.Map.class,
                        jakarta.servlet.http.HttpServletRequest.class),
                "switchGroup 是当前会话自服务端点，服务层必须校验集团归属");

        Path controllerSrc = Paths.get("src/main/java/com/aigicrm/module/auth/controller/AuthController.java")
                .toAbsolutePath().normalize();
        String controllerBody = readSource(controllerSrc, "AuthController");
        assertTrue(controllerBody.contains("@PostMapping(\"/login\")")
                        && controllerBody.contains("ClientIpUtils.getClientIp(httpRequest)")
                        && controllerBody.contains("authService.login(request, ip)"),
                "AuthController.login 必须用可信客户端 IP 调 AuthService.login，防伪造头绕过限流");
        assertTrue(controllerBody.contains("logMapper.insert(log)")
                        && controllerBody.contains("catch (Exception ignored)"),
                "登录审计日志应尽力而为，日志落库失败不能阻断登录主流程");
        assertTrue(controllerBody.contains("auth.startsWith(\"Bearer \")")
                        && controllerBody.contains("authService.logout(auth.substring(7))"),
                "AuthController.logout 必须只提取 Bearer token 并交给 AuthService.logout 拉黑");
        assertTrue(controllerBody.contains("body.get(\"groupId\")")
                        && controllerBody.contains("authService.switchGroup(token, groupId)"),
                "AuthController.switchGroup 必须接收 groupId 并委托服务层完成归属校验和重签");

        Path serviceSrc = Paths.get("src/main/java/com/aigicrm/module/auth/service/AuthService.java")
                .toAbsolutePath().normalize();
        String serviceBody = readSource(serviceSrc, "AuthService");
        assertTrue(serviceBody.contains("LOGIN_FAIL_PREFIX") && serviceBody.contains("LOGIN_FAIL_IP_PREFIX")
                        && serviceBody.contains("MAX_LOGIN_FAILS") && serviceBody.contains("MAX_IP_LOGIN_FAILS"),
                "AuthService.login 必须保留账号+IP 与 IP 级失败限流");
        assertTrue(serviceBody.contains("redisTemplate.delete(failKey)")
                        && serviceBody.contains("redisTemplate.delete(ipFailKey)"),
                "AuthService.login 成功后必须清除失败计数，避免正常用户持续被锁");
        assertTrue(serviceBody.contains("jwtTokenProvider.generateToken(user.getId(), user.getUsername()")
                        && serviceBody.contains("currentGroupId")
                        && serviceBody.contains("response.setPwdResetRequired(user.getPwdResetRequired())"),
                "AuthService.login 必须签发携带当前集团的 JWT，并下发强制改密标记");
        assertTrue(serviceBody.contains("jwtTokenProvider.blacklistToken(token)"),
                "AuthService.logout 必须把当前 token 加入黑名单");
        assertTrue(serviceBody.contains("!groupIds.contains(groupId)")
                        && serviceBody.contains("无权切换到该集团")
                        && serviceBody.contains("response.setCurrentGroupId(groupId)"),
                "AuthService.switchGroup 必须校验集团归属后才允许重签当前集团 token");
        assertTrue(serviceBody.contains("if (userId == null)")
                        && serviceBody.contains("throw new BusinessException(401, \"未登录\")")
                        && serviceBody.contains("dedupeByPath(menus)")
                        && serviceBody.contains("buildMenuTree")
                        && serviceBody.contains("response.setPermissions(permissions)")
                        && serviceBody.contains("response.setCurrentGroupId(SecurityUtils.getCurrentUser().getCurrentGroupId())"),
                "AuthService.getUserInfo 必须保留登录态校验、菜单去重/树化、权限与当前集团下发");
    }

    /** 找回密码端点必须兼顾可用性和反枚举/反轰炸：匿名入口不挂业务权限，但服务层要有完整安全边界。 */
    @Test
    void authForgotPasswordEndpoints_keepEnumerationAndSmsAbuseGuards() throws Exception {
        Class<com.aigicrm.module.auth.controller.AuthController> controller =
                com.aigicrm.module.auth.controller.AuthController.class;

        assertNoPreAuthorize(controller.getDeclaredMethod("forgotPasswordLookup",
                        com.aigicrm.module.auth.dto.ForgotPasswordLookupRequest.class,
                        jakarta.servlet.http.HttpServletRequest.class),
                "forgot-password lookup 是匿名入口，必须靠 IP 限流和中性响应防枚举");
        assertNoPreAuthorize(controller.getDeclaredMethod("forgotPasswordSendCode",
                        com.aigicrm.module.auth.dto.ForgotPasswordSendCodeRequest.class,
                        jakarta.servlet.http.HttpServletRequest.class),
                "forgot-password send-code 是匿名入口，必须靠服务层发码限流防轰炸");
        assertNoPreAuthorize(controller.getDeclaredMethod("forgotPasswordReset",
                        com.aigicrm.module.auth.dto.ForgotPasswordResetRequest.class),
                "forgot-password reset 是匿名入口，验证码校验在服务层完成");

        Path controllerSrc = Paths.get("src/main/java/com/aigicrm/module/auth/controller/AuthController.java")
                .toAbsolutePath().normalize();
        String controllerBody = readSource(controllerSrc, "AuthController");
        assertTrue(controllerBody.contains("lookupRateLimited(ClientIpUtils.getClientIp(httpRequest))")
                        && controllerBody.contains("throw new BusinessException(429, \"操作太频繁，请稍后再试\")"),
                "forgotPasswordLookup 必须在查用户前做可信 IP 限流");
        assertTrue(controllerBody.contains("authService.sendResetCode(request, ClientIpUtils.getClientIp(httpRequest))"),
                "forgotPasswordSendCode 必须把可信 IP 传入服务层，支撑 IP 级发码限流");
        assertTrue(controllerBody.contains("authService.resetPassword(request)"),
                "forgotPasswordReset 必须委托 AuthService.resetPassword 统一验证验证码和改密撤销");

        Path serviceSrc = Paths.get("src/main/java/com/aigicrm/module/auth/service/AuthService.java")
                .toAbsolutePath().normalize();
        String serviceBody = readSource(serviceSrc, "AuthService");
        assertTrue(serviceBody.contains("LOOKUP_RATE_PREFIX")
                        && serviceBody.contains("RATE_LIMIT_SECONDS")
                        && serviceBody.contains("redisTemplate.opsForValue().set(rateKey, 1, Duration.ofSeconds(RATE_LIMIT_SECONDS))"),
                "lookupRateLimited 必须保留 IP 查询限流");
        assertTrue(serviceBody.contains("catch (BusinessException e)")
                        && serviceBody.contains("return resp;")
                        && serviceBody.contains("maskPhone")
                        && serviceBody.contains("maskEmail"),
                "lookupUser 必须对不存在/禁用账号返回中性响应，并只返回脱敏联系方式");
        assertTrue(serviceBody.contains("邮箱验证暂未开通，请使用手机验证")
                        && serviceBody.contains("RESET_IP_PREFIX")
                        && serviceBody.contains("MAX_RESET_IP")
                        && serviceBody.contains("RESET_IP_LOCK_SECONDS")
                        && serviceBody.contains("redisTemplate.opsForValue().increment(ipRateKey)"),
                "sendResetCode 必须明确拒绝未实现邮箱、并在查用户前做 IP 级发码计数");
        assertTrue(serviceBody.contains("CODE_TTL_SECONDS")
                        && serviceBody.contains("RESET_RATE_PREFIX")
                        && serviceBody.contains("Duration.ofSeconds(CODE_TTL_SECONDS)")
                        && serviceBody.contains("Duration.ofSeconds(RATE_LIMIT_SECONDS)"),
                "sendResetCode 必须保留验证码 TTL 与同账号发码频率限制");
        assertTrue(serviceBody.contains("MAX_RESET_ERRORS")
                        && serviceBody.contains("RESET_ERROR_PREFIX")
                        && serviceBody.contains("passwordEncoder.encode(request.getNewPassword())")
                        && serviceBody.contains("user.setPwdChangedAt")
                        && serviceBody.contains("jwtTokenProvider.markPasswordChanged(user.getId())")
                        && serviceBody.contains("redisTemplate.delete(codeKey)")
                        && serviceBody.contains("redisTemplate.delete(errorKey)"),
                "resetPassword 必须限制验证码错误次数、加密新密码、落库改密时间、撤销旧 token 并清理验证码状态");
    }

    /** 提醒模块所有入口共用 reminder:view，但写入动作必须在服务层保留数据范围、幂等和显式 POST 边界。 */
    @Test
    void reminderEndpoints_keepViewGateAndServiceScopeMutationGuards() throws Exception {
        Class<com.aigicrm.module.reminder.controller.ReminderController> controller =
                com.aigicrm.module.reminder.controller.ReminderController.class;

        assertPreAuthorize(controller.getDeclaredMethod("expiringList", int.class, int.class,
                        String.class, Long.class, Long.class, Long.class),
                "hasAuthority('reminder:view')");
        assertPreAuthorize(controller.getDeclaredMethod("page", int.class, int.class, String.class,
                        String.class, Long.class, Long.class, String.class, Long.class),
                "hasAuthority('reminder:view')");
        assertPreAuthorize(controller.getDeclaredMethod("detail", Long.class),
                "hasAuthority('reminder:view')");
        assertPreAuthorize(controller.getDeclaredMethod("pendingCount"),
                "hasAuthority('reminder:view')");
        assertPreAuthorize(controller.getDeclaredMethod("ensureExpiring"),
                "hasAuthority('reminder:view')");
        assertPreAuthorize(controller.getDeclaredMethod("process", Long.class),
                "hasAuthority('reminder:view')");

        Path controllerSrc = Paths.get("src/main/java/com/aigicrm/module/reminder/controller/ReminderController.java")
                .toAbsolutePath().normalize();
        String controllerBody = readSource(controllerSrc, "ReminderController");
        assertTrue(controllerBody.contains("@GetMapping(\"/expiring-list\")")
                        && controllerBody.contains("reminderService.expiringPage"),
                "到期提醒分页必须保持 GET 只读入口并委托 expiringPage");
        assertTrue(controllerBody.contains("@PostMapping(\"/ensure-expiring\")")
                        && controllerBody.contains("reminderService.ensureExpiring()"),
                "今日到期提醒物化必须保持显式 POST 入口");
        assertTrue(controllerBody.contains("@PostMapping(\"/{id}/process\")")
                        && controllerBody.contains("reminderService.process(id)"),
                "提醒处理必须保持显式 POST 入口并委托服务层可见性校验");

        Path serviceSrc = Paths.get("src/main/java/com/aigicrm/module/reminder/service/ReminderService.java")
                .toAbsolutePath().normalize();
        String serviceBody = readSource(serviceSrc, "ReminderService");
        assertTrue(serviceBody.contains("private void applyOwnerScope")
                        && serviceBody.contains("scopeResolver.resolve(SecurityUtils.getCurrentUser())")
                        && serviceBody.contains("wrapper.eq(BizReminder::getOwnerId, -1L)")
                        && serviceBody.contains("wrapper.in(BizReminder::getOwnerId"),
                "ReminderService.page 必须通过 applyOwnerScope 收口提醒负责人数据范围，越权 ownerId 返回空集");
        String expiringPageBody = serviceBody.substring(
                serviceBody.indexOf("public IPage<Map<String, Object>> expiringPage"),
                serviceBody.indexOf("/** S8: 物化今日到期的 expiring 提醒"));
        assertTrue(expiringPageBody.contains("S8: GET 只读，不再自动建提醒")
                        && expiringPageBody.contains("reminderMapper.selectOne(")
                        && expiringPageBody.contains("row.put(\"reminderId\"")
                        && expiringPageBody.contains("row.put(\"todayFollowedUp\"")
                        && !expiringPageBody.contains("reminderMapper.insert"),
                "ReminderService.expiringPage 必须保持 GET 只读，只查询今日提醒状态，不自动写库");
        assertTrue(serviceBody.contains("@org.springframework.transaction.annotation.Transactional")
                        && serviceBody.contains("public void ensureExpiring()")
                        && serviceBody.contains("reminderMapper.selectCount")
                        && serviceBody.contains("reminderMapper.insert(r)")
                        && serviceBody.contains("visibleOwners.isEmpty() ? List.of(-1L) : visibleOwners"),
                "ReminderService.ensureExpiring 必须保持事务、数据范围过滤和当日幂等物化");
        assertTrue(serviceBody.contains("public long pendingCount()")
                        && serviceBody.contains("Math.max(0, totalExpiring - processedToday + sharePending)")
                        && serviceBody.contains("share_to\", \"share_by\", \"share_owner"),
                "ReminderService.pendingCount 必须合并到期未处理与共享提醒，并避免负数角标");
        assertTrue(serviceBody.contains("public void process(Long id)")
                        && serviceBody.contains("if (reminder == null) return")
                        && serviceBody.contains("assertVisible(reminder, \"无权处理该提醒\")")
                        && serviceBody.contains("reminder.setStatus(\"processed\")")
                        && serviceBody.contains("reminder.setProcessedAt(LocalDateTime.now())")
                        && serviceBody.contains("reminderMapper.updateById(reminder)"),
                "ReminderService.process 必须保留空提醒幂等、可见性守卫、处理状态与处理时间落库");
        assertTrue(serviceBody.contains("public Map<String, Object> detail(Long id)")
                        && serviceBody.contains("assertVisible(reminder, \"无权查看该提醒\")")
                        && serviceBody.contains("customerBrief(customer)")
                        && serviceBody.contains("private Map<String, Object> customerBrief"),
                "ReminderService.detail 必须复用可见性守卫并只返回提醒场景需要的客户摘要");
        assertTrue(serviceBody.contains("private void assertVisible")
                        && serviceBody.contains("SecurityUtils.isAdmin()")
                        && serviceBody.contains("reminder.getOwnerId() == null || !visible.contains(reminder.getOwnerId())")
                        && serviceBody.contains("throw new BusinessException(message)"),
                "ReminderService.assertVisible 必须保留 admin 放行与非 admin 负责人范围校验");
    }

    /** 激励记录编辑/删除必须保留方法内归属与专项授权守卫，不能误回退成粗粒度方法权限。 */
    @Test
    void incentiveRecordMutationEndpoints_keepInternalOwnershipAndGrantGuards() throws Exception {
        Class<com.aigicrm.module.incentive.controller.IncentiveController> controller =
                com.aigicrm.module.incentive.controller.IncentiveController.class;

        assertPreAuthorize(controller.getDeclaredMethod("getRecords",
                        Long.class, String.class, Long.class, Long.class),
                "hasAuthority('incentive:view')");
        assertPreAuthorize(controller.getDeclaredMethod("addRecord",
                        com.aigicrm.module.incentive.entity.BizIncentiveRecord.class),
                "hasAuthority('incentive:add')");
        assertNoPreAuthorize(controller.getDeclaredMethod("updateRecord", Long.class,
                        com.aigicrm.module.incentive.entity.BizIncentiveRecord.class),
                "本人记录编辑由方法内守卫放行，不能被粗粒度 incentive:edit 阻断");
        assertNoPreAuthorize(controller.getDeclaredMethod("deleteRecord", Long.class),
                "本人记录删除由方法内守卫放行，不能被粗粒度 incentive:edit 阻断");
        assertPreAuthorize(controller.getDeclaredMethod("recalculateAll"),
                "hasAuthority('incentive:edit')");
        assertPreAuthorize(controller.getDeclaredMethod("markRead", Long.class),
                "hasAuthority('incentive:view')");
        assertPreAuthorize(controller.getDeclaredMethod("readStats", String.class),
                "hasAuthority('incentive:read-stats')");
        assertPreAuthorize(controller.getDeclaredMethod("readRanking", String.class),
                "hasAuthority('incentive:read-ranking:view')");

        Path controllerSrc = Paths.get("src/main/java/com/aigicrm/module/incentive/controller/IncentiveController.java")
                .toAbsolutePath().normalize();
        String controllerBody = readSource(controllerSrc, "IncentiveController");
        assertTrue(controllerBody.contains("SecurityUser user = SecurityUtils.getCurrentUser()")
                        && controllerBody.contains("boolean isAdmin = SecurityUtils.isAdmin()"),
                "IncentiveController.updateRecord/deleteRecord 必须基于当前用户和 admin 状态做方法内守卫");
        assertTrue(controllerBody.contains("if (!isAdmin && !incentiveService.canEdit())")
                        && controllerBody.contains("BizIncentiveRecord existing = incentiveService.getRecordById(id)")
                        && controllerBody.contains("!existing.getUserId().equals(user.getUserId())")
                        && controllerBody.contains("throw new BusinessException(403, \"无权编辑他人记录\")")
                        && controllerBody.contains("incentiveService.updateRecord(id, record)"),
                "IncentiveController.updateRecord 必须保留 admin/can_edit/本人记录三段式编辑守卫");
        assertTrue(controllerBody.contains("if (!isAdmin && !incentiveService.canDelete())")
                        && controllerBody.contains("BizIncentiveRecord incRecord = incentiveService.getRecordById(id)")
                        && controllerBody.contains("!incRecord.getUserId().equals(user.getUserId())")
                        && controllerBody.contains("throw new BusinessException(403, \"无权删除他人记录\")")
                        && controllerBody.contains("incentiveService.deleteRecord(id)"),
                "IncentiveController.deleteRecord 必须保留 admin/can_delete/本人记录三段式删除守卫");

        Path serviceSrc = Paths.get("src/main/java/com/aigicrm/module/incentive/service/IncentiveService.java")
                .toAbsolutePath().normalize();
        String serviceBody = readSource(serviceSrc, "IncentiveService");
        assertTrue(serviceBody.contains("public boolean canEdit()")
                        && serviceBody.contains("grantFlag(SecurityUtils.getCurrentUserId(), \"can_edit\", false)")
                        && serviceBody.contains("public boolean canDelete()")
                        && serviceBody.contains("grantFlag(SecurityUtils.getCurrentUserId(), \"can_delete\", false)")
                        && serviceBody.contains("SELECT can_view_shared, can_view_all, can_edit, can_delete")
                        && serviceBody.contains("if (row == null) return defaultValue"),
                "IncentiveService 必须继续从 biz_incentive_grant 实时读取编辑/删除专项授权，并按默认值处理缺失行");
        assertTrue(serviceBody.contains("private boolean canSeeAllIncentive()")
                        && serviceBody.contains("grantFlag(SecurityUtils.getCurrentUserId(), \"can_view_all\", false)")
                        && serviceBody.contains("grantFlag(SecurityUtils.getCurrentUserId(), \"can_edit\", false)")
                        && serviceBody.contains("grantFlag(SecurityUtils.getCurrentUserId(), \"can_delete\", false)")
                        && serviceBody.contains("private void applyShareVisibility")
                        && serviceBody.contains("w.eq(BizIncentiveRecord::getUserId, userId)")
                        && serviceBody.contains("if (canShared) w.or().eq(BizIncentiveRecord::getShared, 1)"),
                "激励记录可见性必须保留专项授权看全量，否则只看本人和已分享记录");
        assertTrue(serviceBody.contains("@Transactional")
                        && serviceBody.contains("public void updateRecord(Long id, BizIncentiveRecord update)")
                        && serviceBody.contains("if (existing == null) return")
                        && serviceBody.contains("&& !canEdit())")
                        && serviceBody.contains("throw new BusinessException(403, \"无权修改记录归属客户\")")
                        && serviceBody.contains("currentUserId.equals(existing.getUserId())")
                        && serviceBody.contains("existing.setUpdatedBy(SecurityUtils.getCurrentUserId())")
                        && serviceBody.contains("existing.setScore(calcScore(existing, category, base))")
                        && serviceBody.contains("recordMapper.updateById(existing)"),
                "IncentiveService.updateRecord 必须保持事务、客户归属变更限制、本人分享开关、审计人和积分重算落库");
        String updateBody = serviceBody.substring(
                serviceBody.indexOf("public void updateRecord"),
                serviceBody.indexOf("public int recalculateAllScores"));
        assertFalse(updateBody.contains("setUserId("),
                "激励记录编辑不得重置初始上传人 userId");
        assertFalse(updateBody.contains("setCreatedAt("),
                "激励记录编辑不得重置初始上传时间 createdAt");
        assertFalse(updateBody.contains("setCategoryId("),
                "激励记录编辑不得改写初始积分分类 categoryId");
        assertTrue(serviceBody.contains("public void deleteRecord(Long id)")
                        && serviceBody.contains("recordMapper.deleteById(id)"),
                "IncentiveService.deleteRecord 只执行删除动作，越权判定必须留在 Controller 方法内守卫");
    }

    /** 积分录入/排行/导出必须保留 score:manage、score:entry、score:view 的清晰边界和防数据丢失保存语义。 */
    @Test
    void scoreEndpoints_keepEntryRankingExportPermissionsAndNoDataLossSaveSemantics() throws Exception {
        Class<com.aigicrm.module.score.controller.ScoreController> controller =
                com.aigicrm.module.score.controller.ScoreController.class;

        assertPreAuthorize(controller.getDeclaredMethod("addItem",
                        com.aigicrm.module.score.entity.BizScoreItem.class),
                "hasAuthority('score:manage')");
        assertPreAuthorize(controller.getDeclaredMethod("updateItem", Long.class,
                        com.aigicrm.module.score.entity.BizScoreItem.class),
                "hasAuthority('score:manage')");
        assertPreAuthorize(controller.getDeclaredMethod("deleteItem", Long.class),
                "hasAuthority('score:manage')");
        assertPreAuthorize(controller.getDeclaredMethod("getGrid", String.class),
                "hasAuthority('score:view')");
        assertPreAuthorize(controller.getDeclaredMethod("batchSave", java.util.Map.class),
                "hasAuthority('score:entry')");
        assertPreAuthorize(controller.getDeclaredMethod("ranking", String.class, String.class),
                "hasAuthority('score:view')");
        assertPreAuthorize(controller.getDeclaredMethod("rankingStats", String.class),
                "hasAuthority('score:view')");
        assertPreAuthorize(controller.getDeclaredMethod("exportGrid", String.class,
                        jakarta.servlet.http.HttpServletResponse.class),
                "hasAuthority('score:view')");

        assertTrue(loadPermissionCodes().contains("score:ranking"),
                "兼容权限码 score:ranking 必须保留在权限字典中；运行时排行榜统一使用 score:view");

        Path controllerSrc = Paths.get("src/main/java/com/aigicrm/module/score/controller/ScoreController.java")
                .toAbsolutePath().normalize();
        String controllerBody = readSource(controllerSrc, "ScoreController");
        assertTrue(controllerBody.contains("if (\"dept\".equals(type))")
                        && controllerBody.contains("scoreService.deptRanking(period)")
                        && controllerBody.contains("scoreService.ranking(period)"),
                "ScoreController.ranking 必须保留个人/部门排行榜分流");
        assertFalse(controllerBody.contains("score:ranking"),
                "ScoreController 运行时门禁不应回退到历史 score:ranking，应统一使用 score:view");

        Path serviceSrc = Paths.get("src/main/java/com/aigicrm/module/score/service/ScoreService.java")
                .toAbsolutePath().normalize();
        String serviceBody = readSource(serviceSrc, "ScoreService");
        assertTrue(serviceBody.contains("private List<Long> visibleUserIds()")
                        && serviceBody.contains("scopeResolver.resolve(SecurityUtils.getCurrentUser())"),
                "ScoreService 必须通过 OrgScopeResolver 取得当前账号可见用户集合");
        assertTrue(serviceBody.contains("public Map<String, Object> getGrid(String period)")
                        && serviceBody.contains("new LambdaQueryWrapper<SysUser>().eq(SysUser::getStatus, 1)")
                        && serviceBody.contains("if (visible != null) userWrapper.in(SysUser::getId, visible)")
                        && serviceBody.contains("participantMapper.selectList")
                        && serviceBody.contains("result.put(\"scores\", scores)"),
                "ScoreService.getGrid 必须按账号数据范围提供候选用户、还原参与名单并返回该周期分数矩阵");
        assertTrue(serviceBody.contains("@Transactional")
                        && serviceBody.contains("public void batchSave")
                        && serviceBody.contains("Map<String, Integer> submitted")
                        && serviceBody.contains("entryMapper.updateById(existing)")
                        && serviceBody.contains("entryMapper.insert(entry)")
                        && serviceBody.contains("participantSet.contains(ex.getUserId()) && !submitted.containsKey(key)")
                        && serviceBody.contains("participantMapper.physicalDeleteByPeriod(period)"),
                "ScoreService.batchSave 必须保持事务增量 upsert，只删除参与名单内被显式清空的分数，并单独持久化列顺序");
        String batchSaveBody = serviceBody.substring(
                serviceBody.indexOf("public void batchSave"),
                serviceBody.indexOf("private Map<String, Object> toUserMap"));
        assertFalse(batchSaveBody.contains("entryMapper.delete("),
                "ScoreService.batchSave 不得重新引入按周期批量删除分数记录，防止移出可见名单后历史分数丢失");
        assertTrue(serviceBody.contains("【2026-08-08 设计规则：积分排行全员可见】")
                        && serviceBody.contains("public List<Map<String, Object>> ranking(String period)")
                        && serviceBody.contains("if (period != null && !period.isEmpty() && !\"全部\".equals(period))")
                        && serviceBody.contains("getPrevRanks(period)")
                        && serviceBody.contains("row.put(\"trend\", trend)"),
                "ScoreService.ranking 必须保持公司级全员排行、周期过滤、趋势计算和排行榜字段输出");
        assertTrue(serviceBody.contains("public List<Map<String, Object>> deptRanking(String period)")
                        && serviceBody.contains("Map<String, int[]> deptAgg")
                        && serviceBody.contains("row.put(\"avgScore\""),
                "ScoreService.deptRanking 必须基于个人排行聚合部门总分、人数和均分");
        assertTrue(serviceBody.contains("public Map<String, Object> rankingStats(String period)")
                        && serviceBody.contains("participantCount")
                        && serviceBody.contains("topScoreName")
                        && serviceBody.contains("totalItemCount"),
                "ScoreService.rankingStats 必须保留排行榜人数、部门数、最高分和积分项统计");
        assertTrue(serviceBody.contains("public void exportGrid(String period, HttpServletResponse response)")
                        && serviceBody.contains("if (visible != null) userWrapper.in(SysUser::getId, visible)")
                        && serviceBody.contains("if (visible != null) ew.in(BizScoreEntry::getUserId, visible)")
                        && serviceBody.contains("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                        && serviceBody.contains("attachment;filename=")
                        && serviceBody.contains("EasyExcel.write(response.getOutputStream())"),
                "ScoreService.exportGrid 必须按数据范围导出、设置 XLSX 响应头并通过 EasyExcel 写出");
    }

    private static long countSubstring(String haystack, String needle) {
        long count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }

    private static void assertPreAuthorize(Method method, String expected) {
        PreAuthorize pa = method.getAnnotation(PreAuthorize.class);
        assertTrue(pa != null, method.getName() + " 必须保留 @PreAuthorize");
        assertEquals(expected, pa.value(), method.getName() + " 权限口径被意外改变");
    }

    private static void assertNoPreAuthorize(Method method, String reason) {
        assertTrue(method.getAnnotation(PreAuthorize.class) == null,
                method.getName() + " 不应挂 @PreAuthorize: " + reason);
    }

    private static String readSource(Path src, String label) {
        assertTrue(Files.exists(src), label + " 源文件不存在: " + src);
        try {
            return Files.readString(src);
        } catch (IOException e) {
            throw new IllegalStateException("读取 " + label + " 源码失败", e);
        }
    }

    // ==================== 反向规则（warning 级，不断言失败） ====================

    @Test
    void reverseRule_reportOrphanDictCodes_warningOnly() {
        Set<String> dict = loadPermissionCodes();
        Set<String> referenced = new HashSet<>();

        // 1) 控制器任意方法 @PreAuthorize 中引用的权限码（反射，含 GET 方法）
        scanControllerClasses().forEach(clazz ->
                Stream.of(clazz.getDeclaredMethods()).forEach(m -> {
                    PreAuthorize pa = m.getAnnotation(PreAuthorize.class);
                    if (pa != null) {
                        referenced.addAll(extractAuthorityCodes(pa.value()));
                    }
                }));

        // 2) main 源码（接口注解 + 服务层守卫字符串字面量）中引用的权限码
        referenced.addAll(findQuotedRefsInMainJava(dict));
        referenced.addAll(loadDocumentedNonRuntimePermissionCodes(dict));

        Set<String> drifted = new TreeSet<>(dict);
        drifted.removeAll(referenced);

        System.out.println("===== [warning] 权限矩阵反向漂移清单（字典有码但后端 main 源码未引用） =====");
        if (drifted.isEmpty()) {
            System.out.println("  无：字典内所有权限码均在 main 源码中有引用");
        } else {
            System.out.println("  以下 " + drifted.size() + " 个权限码未在接口注解或服务层守卫中引用（仅供参考，不影响构建）：");
            drifted.forEach(code -> System.out.println("    - " + code));
        }
        System.out.println("===== end =====");
    }

    // ==================== 权限码字典解析 ====================

    /** 读取 docs/permission/permission-dict.md，解析「一、权限码清单」节后的表格第一列 */
    static Set<String> loadPermissionCodes() {
        Path dictPath = Paths.get(DICT_REL_PATH).toAbsolutePath().normalize();
        if (!Files.exists(dictPath)) {
            throw new IllegalStateException(
                    "权限码字典不存在: " + dictPath + "\n"
                            + "当前工作目录: " + Paths.get("").toAbsolutePath() + "\n"
                            + "请确认从 aigicrm-backend/ 运行 mvn test（相对路径 ../docs/permission/permission-dict.md）");
        }
        String text;
        try {
            text = Files.readString(dictPath);
        } catch (IOException e) {
            throw new IllegalStateException("读取权限码字典失败: " + dictPath, e);
        }

        Set<String> codes = new LinkedHashSet<>();
        boolean inSection1 = false;
        for (String line : text.split("\\R")) {
            if (line.contains("一、权限码清单")) {
                inSection1 = true;
                continue;
            }
            if (inSection1 && line.startsWith("## 二")) {
                break;
            }
            if (inSection1) {
                Matcher m = DICT_ROW.matcher(line);
                if (m.find()) {
                    codes.add(m.group(1));
                }
            }
        }
        if (codes.isEmpty()) {
            throw new IllegalStateException("权限码字典解析失败，未提取到任何权限码: " + dictPath);
        }
        return codes;
    }

    // ==================== 扫描辅助 ====================

    /** 用 ArchUnit 扫描 com.aigicrm 下所有 @RestController 类（限定 controller 包） */
    private static List<Class<?>> scanControllerClasses() {
        JavaClasses classes = new ClassFileImporter().importPackages("com.aigicrm");
        return classes.stream()
                .filter(jc -> jc.isAnnotatedWith(RestController.class))
                .filter(jc -> jc.getPackageName().contains("controller"))
                .map(JavaClass::reflect)
                .collect(Collectors.toList());
    }

    /** 扫描所有 controller 类的写方法 */
    private static List<WriteMethod> scanWriteMethods() {
        List<WriteMethod> result = new ArrayList<>();
        for (Class<?> clazz : scanControllerClasses()) {
            for (Method m : clazz.getDeclaredMethods()) {
                if (m.isBridge() || m.isSynthetic()) {
                    continue;
                }
                String mapping = writeMappingName(m);
                if (mapping != null) {
                    result.add(new WriteMethod(clazz, m, mapping));
                }
            }
        }
        return result;
    }

    /** 判断方法是否为 HTTP 写方法，返回注解名；非写方法返回 null（含 @RequestMapping(method=...) 组合形态） */
    private static String writeMappingName(Method m) {
        if (m.isAnnotationPresent(PostMapping.class)) return "@PostMapping";
        if (m.isAnnotationPresent(PutMapping.class)) return "@PutMapping";
        if (m.isAnnotationPresent(DeleteMapping.class)) return "@DeleteMapping";
        if (m.isAnnotationPresent(PatchMapping.class)) return "@PatchMapping";
        RequestMapping rm = m.getAnnotation(RequestMapping.class);
        if (rm != null) {
            for (RequestMethod httpMethod : rm.method()) {
                if (httpMethod == RequestMethod.POST || httpMethod == RequestMethod.PUT
                        || httpMethod == RequestMethod.DELETE || httpMethod == RequestMethod.PATCH) {
                    return "@RequestMapping(method=" + httpMethod + ")";
                }
            }
        }
        return null;
    }

    /** 从 @PreAuthorize 表达式中提取所有权限码（支持 hasAuthority / hasAnyAuthority，含空格形态） */
    static Set<String> extractAuthorityCodes(String expression) {
        Set<String> codes = new LinkedHashSet<>();
        Matcher m = AUTHORITY_CALL.matcher(expression);
        while (m.find()) {
            for (String arg : m.group(1).split(",")) {
                String t = arg.trim();
                if (t.length() >= 2
                        && ((t.startsWith("'") && t.endsWith("'"))
                        || (t.startsWith("\"") && t.endsWith("\"")))) {
                    codes.add(t.substring(1, t.length() - 1));
                }
            }
        }
        return codes;
    }

    /** 在 src/main/java 下搜索每个权限码的引号引用（'code' 或 "code"），返回被引用的码集合 */
    private static Set<String> findQuotedRefsInMainJava(Set<String> dictCodes) {
        Set<String> referenced = new HashSet<>();
        Path srcDir = Paths.get("src", "main", "java");
        if (!Files.isDirectory(srcDir)) {
            System.out.println("[warning] 反向规则未找到 src/main/java（" + srcDir.toAbsolutePath()
                    + "），仅基于控制器反射统计引用");
            return referenced;
        }
        StringBuilder all = new StringBuilder();
        try (Stream<Path> paths = Files.walk(srcDir)) {
            paths.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                try {
                    all.append(Files.readString(p));
                } catch (IOException ignored) {
                    // 单文件读取失败不影响整体扫描
                }
            });
        } catch (IOException e) {
            System.out.println("[warning] 反向规则源码扫描失败: " + e.getMessage());
            return referenced;
        }
        String source = all.toString();
        for (String code : dictCodes) {
            if (source.contains("'" + code + "'") || source.contains("\"" + code + "\"")) {
                referenced.add(code);
            }
        }
        return referenced;
    }

    /**
     * 读取文档确认兼容/历史权限码确实仍在字典中，并且备注明确说明它们不再作为运行时独立门禁。
     * 缺少文档说明时让测试失败，避免用白名单静默吞掉真实漂移。
     */
    private static Set<String> loadDocumentedNonRuntimePermissionCodes(Set<String> dictCodes) {
        Path dictPath = Paths.get(DICT_REL_PATH).toAbsolutePath().normalize();
        String text;
        try {
            text = Files.readString(dictPath);
        } catch (IOException e) {
            throw new IllegalStateException("读取权限码字典失败: " + dictPath, e);
        }

        Set<String> documented = new HashSet<>();
        for (String code : DOCUMENTED_NON_RUNTIME_PERMISSION_CODES) {
            assertTrue(dictCodes.contains(code),
                    () -> "兼容/历史权限码必须仍存在于权限字典中: " + code);
            Pattern row = Pattern.compile("^\\|\\s*`" + Pattern.quote(code)
                    + "`\\s*\\|.*(?:不作为|已废弃|历史残留|统一使用|授权表).*", Pattern.MULTILINE);
            Matcher matcher = row.matcher(text);
            assertTrue(matcher.find(),
                    () -> "兼容/历史权限码缺少非运行时口径说明: " + code);
            documented.add(code);
        }
        return documented;
    }

    private static String describe(WriteMethod wm) {
        return wm.clazz().getName() + "." + wm.method().getName() + " (" + wm.mapping() + ")";
    }
}
