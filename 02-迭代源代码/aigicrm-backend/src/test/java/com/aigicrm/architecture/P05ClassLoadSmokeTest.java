package com.aigicrm.architecture;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * P0-5 批次 A 的类加载冒烟：S1192 常量声明在类静态初始化时执行。
 * 本测试只加载类、不启动 Spring、不访问外部资源。
 */
class P05ClassLoadSmokeTest {

    private static final String[] CLASSES = {
            "com.aigicrm.common.engine.PermissionRuleEngine",
            "com.aigicrm.common.scope.OrgScopeResolver",
            "com.aigicrm.module.approval.controller.ApprovalController",
            "com.aigicrm.module.approval.service.ApprovalService",
            "com.aigicrm.module.auth.controller.AuthController",
            "com.aigicrm.module.common.controller.FormDraftController",
            "com.aigicrm.module.customer.controller.CustomerController",
            "com.aigicrm.module.customer.service.CustomerService",
            "com.aigicrm.module.customer.service.CustomerVisibilityService",
            "com.aigicrm.module.fabushi.controller.FabushiStatsController",
            "com.aigicrm.module.fabushi.service.FabushiActivityService",
            "com.aigicrm.module.fabushi.service.FabushiBookService",
            "com.aigicrm.module.fabushi.service.FabushiPostService",
            "com.aigicrm.module.fabushi.service.FabushiReadingService",
            "com.aigicrm.module.fabushi.service.FabushiSectionService",
            "com.aigicrm.module.fabushi.service.FabushiStatsService",
            "com.aigicrm.module.file.controller.FileController",
            "com.aigicrm.module.file.controller.FileRecycleController",
            "com.aigicrm.module.file.service.FileChunkUploadService",
            "com.aigicrm.module.file.service.FileGrantService",
            "com.aigicrm.module.file.service.FileService",
            "com.aigicrm.module.file.service.FolderAclService",
            "com.aigicrm.module.file.service.FolderPermissionService",
            "com.aigicrm.module.file.service.FolderTypeDefaultService",
            "com.aigicrm.module.incentive.service.IncentiveService",
            "com.aigicrm.module.reminder.service.ReminderService",
            "com.aigicrm.module.score.service.ScoreService",
            "com.aigicrm.module.sea.controller.SeaController",
            "com.aigicrm.module.sea.service.SeaConfigService",
            "com.aigicrm.module.sea.service.SeaService",
            "com.aigicrm.module.statistics.service.StatisticsQueryService",
            "com.aigicrm.module.system.controller.RecycleController",
            "com.aigicrm.module.system.controller.SysConfigController",
            "com.aigicrm.module.system.controller.SysUserController",
            "com.aigicrm.module.system.service.OrgAdminService",
            "com.aigicrm.module.system.service.PermCenterService",
            "com.aigicrm.module.system.service.PermReconcileService",
            "com.aigicrm.module.system.service.PermissionMetadataService",
            "com.aigicrm.module.system.service.RecycleExportService",
            "com.aigicrm.module.system.service.RecycleService",
            "com.aigicrm.module.system.service.RolePermScopeService",
            "com.aigicrm.module.system.service.SysDictService",
            "com.aigicrm.module.system.service.SysGroupService",
            "com.aigicrm.module.system.service.SysRoleService",
            "com.aigicrm.module.system.service.SysUserService",
            "com.aigicrm.module.system.service.LifecycleService",
            "com.aigicrm.module.task.service.TaskService",
            "com.aigicrm.security.JwtAuthFilter",
            "com.aigicrm.security.JwtTokenProvider",
            "com.aigicrm.security.SecurityAuditService",
            "com.aigicrm.task.CustomerExpireTask"
    };

    @Test
    void allP05TouchedClassesInitialize() {
        for (String name : CLASSES) {
            assertDoesNotThrow(() -> Class.forName(name, true, P05ClassLoadSmokeTest.class.getClassLoader()), name);
        }
    }
}
