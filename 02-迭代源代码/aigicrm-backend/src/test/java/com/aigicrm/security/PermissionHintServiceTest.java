package com.aigicrm.security;

import com.aigicrm.module.system.entity.SysMenu;
import com.aigicrm.module.system.mapper.SysMenuMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 【2026-11-08 权限报错中文化】PermissionHintService 单测。
 * 覆盖：标签映射/消息拼装/AntPathMatcher 匹配 {id} 模板/无命中 fallback/
 * 多码顿号并列截断/激励授权特殊指引段/类级+方法级注解合并/HTTP 方法过滤。
 * （RequestMappingInfo 用 Spring Framework 7 的 org.springframework.web.servlet.mvc.method 包，
 * 与 RequestMappingHandlerMapping#getHandlerMethods() 返回类型一致。）
 */
class PermissionHintServiceTest {

    private RequestMappingHandlerMapping handlerMapping;
    private SysMenuMapper menuMapper;
    private PermissionHintService svc;

    @BeforeEach
    void setUp() {
        handlerMapping = mock(RequestMappingHandlerMapping.class);
        menuMapper = mock(SysMenuMapper.class);
        when(menuMapper.selectList(any())).thenReturn(List.of(
                menu(11L, "客户管理", "customer:view"),
                menu(21L, "统计分析", "statistics:view"),
                menu(22L, "首页看板", "dashboard:view"),
                // 同码多行：按 id 升序取第一行 → "客户管理"（13 行不生效）
                menu(13L, "客户详情", "customer:view")));
        svc = new PermissionHintService(handlerMapping, menuMapper);
    }

    // ==================== 端点注册桩 ====================

    /** 模拟一个带 @PreAuthorize 的控制器（类级 + 方法级） */
    static class DemoController {
        @PreAuthorize("hasAuthority('customer:view')")
        public void list() {
        }

        @PreAuthorize("hasAnyAuthority('statistics:view','dashboard:view')")
        public void stats() {
        }

        @PreAuthorize("hasAuthority('incentive:view-2026')")
        public void incentiveAuth() {
        }

        @PreAuthorize("hasAnyAuthority('a:one','a:two','a:three','a:four','a:five')")
        public void manyCodes() {
        }

        public void noAnno() {
        }
    }

    @PreAuthorize("hasAuthority('sea:view')")
    static class ClassLevelController {
        public void anything() {
        }
    }

    private static void register(Map<RequestMappingInfo, HandlerMethod> map,
                                 Object bean, String methodName, String... patterns) throws Exception {
        register(map, bean, methodName, HttpMethod.GET, patterns);
    }

    private static void register(Map<RequestMappingInfo, HandlerMethod> map,
                                 Object bean, String methodName, HttpMethod httpMethod, String... patterns)
            throws Exception {
        Method m = bean.getClass().getMethod(methodName);
        RequestMappingInfo.Builder builder = RequestMappingInfo.paths(patterns);
        if (httpMethod != null) {
            builder.methods(org.springframework.web.bind.annotation.RequestMethod.valueOf(httpMethod.name()));
        }
        map.put(builder.build(), new HandlerMethod(bean, m));
    }

    // ==================== hint：端点匹配与文案 ====================

    @Test
    void hint_matchesIdTemplateAndBuildsLabelledMessage() throws Exception {
        Map<RequestMappingInfo, HandlerMethod> handlers = new LinkedHashMap<>();
        register(handlers, new DemoController(), "list", "/api/customer/{id}");
        when(handlerMapping.getHandlerMethods()).thenReturn(handlers);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/app/api/customer/123");
        req.setContextPath("/app"); // 上下文路径须被剥掉再匹配
        String msg = svc.hint(req, null);

        assertEquals("您的权限不支持此访问：缺少「客户管理(customer:view)」功能权限。"
                + "如需访问此内容，请联系管理员在【系统设置 → 权限中心 → 功能与范围】开启「客户管理」功能。", msg);
    }

    @Test
    void hint_multiCodesJoinedWithDunAndTruncatedToThree() throws Exception {
        Map<RequestMappingInfo, HandlerMethod> handlers = new LinkedHashMap<>();
        register(handlers, new DemoController(), "manyCodes", "/api/many");
        when(handlerMapping.getHandlerMethods()).thenReturn(handlers);

        // a:one..a:five 无菜单行 → 标签=码本身（不出现「code(code)」重复）
        String msg = svc.hint(new MockHttpServletRequest("GET", "/api/many"), null);
        assertTrue(msg.contains("缺少「a:one」、「a:two」、「a:three」等功能权限"), msg);
        assertFalse(msg.contains("a:four"), "第 4 个码不列出: " + msg);
        assertFalse(msg.contains("(a:one)"), "无标签行不得重复展示码: " + msg);
    }

    @Test
    void hint_incentiveAuthCodeUsesSpecialSection() throws Exception {
        Map<RequestMappingInfo, HandlerMethod> handlers = new LinkedHashMap<>();
        register(handlers, new DemoController(), "incentiveAuth", "/api/incentive/records");
        when(handlerMapping.getHandlerMethods()).thenReturn(handlers);

        String msg = svc.hint(new MockHttpServletRequest("GET", "/api/incentive/records"), null);
        assertTrue(msg.contains("【系统设置 → 权限中心 → 专项权限（激励授权）】"), msg);
        assertFalse(msg.contains("功能与范围"), "激励授权类不得指向功能与范围: " + msg);
    }

    @Test
    void hint_classLevelPreAuthorizeMerged() throws Exception {
        Map<RequestMappingInfo, HandlerMethod> handlers = new LinkedHashMap<>();
        register(handlers, new ClassLevelController(), "anything", "/api/sea/list");
        when(handlerMapping.getHandlerMethods()).thenReturn(handlers);

        String msg = svc.hint(new MockHttpServletRequest("GET", "/api/sea/list"), null);
        assertTrue(msg.contains("sea:view"), msg);
    }

    @Test
    void hint_httpMethodMismatchFallsBack() throws Exception {
        Map<RequestMappingInfo, HandlerMethod> handlers = new LinkedHashMap<>();
        register(handlers, new DemoController(), "list", HttpMethod.POST, "/api/customer/{id}");
        when(handlerMapping.getHandlerMethods()).thenReturn(handlers);

        assertEquals(PermissionHintService.FALLBACK_HINT,
                svc.hint(new MockHttpServletRequest("GET", "/api/customer/1"), null));
    }

    @Test
    void hint_noMethodConditionMatchesAnyVerb() throws Exception {
        Map<RequestMappingInfo, HandlerMethod> handlers = new LinkedHashMap<>();
        // httpMethod=null → @RequestMapping 未限定 methods，任意 HTTP 方法均可命中
        register(handlers, new DemoController(), "list", (HttpMethod) null, "/api/customer/all");
        when(handlerMapping.getHandlerMethods()).thenReturn(handlers);

        assertTrue(svc.hint(new MockHttpServletRequest("DELETE", "/api/customer/all"), null)
                .contains("customer:view"));
    }

    @Test
    void hint_noMatchingPathReturnsFallback() throws Exception {
        Map<RequestMappingInfo, HandlerMethod> handlers = new LinkedHashMap<>();
        register(handlers, new DemoController(), "list", "/api/customer/{id}");
        when(handlerMapping.getHandlerMethods()).thenReturn(handlers);

        assertEquals(PermissionHintService.FALLBACK_HINT,
                svc.hint(new MockHttpServletRequest("GET", "/api/unknown/thing"), null));
    }

    @Test
    void hint_endpointWithoutPreAuthorizeSkipped() throws Exception {
        Map<RequestMappingInfo, HandlerMethod> handlers = new LinkedHashMap<>();
        register(handlers, new DemoController(), "noAnno", "/api/open/data");
        when(handlerMapping.getHandlerMethods()).thenReturn(handlers);

        // 无权限码端点（isAuthenticated() 等）无法给出“缺什么”，退回 fallback
        assertEquals(PermissionHintService.FALLBACK_HINT,
                svc.hint(new MockHttpServletRequest("GET", "/api/open/data"), null));
    }

    @Test
    void hint_mostSpecificPatternWinsOverWildcard() throws Exception {
        Map<RequestMappingInfo, HandlerMethod> handlers = new LinkedHashMap<>();
        register(handlers, new DemoController(), "stats", "/api/customer/**");
        register(handlers, new DemoController(), "list", "/api/customer/{id}");
        when(handlerMapping.getHandlerMethods()).thenReturn(handlers);

        // /api/customer/{id} 比 /api/customer/** 更具体，应胜出（AntPatternComparator）
        String msg = svc.hint(new MockHttpServletRequest("GET", "/api/customer/9"), null);
        assertTrue(msg.contains("customer:view"), msg);
        assertFalse(msg.contains("statistics:view"), "宽匹配端点的码不得混入: " + msg);
    }

    @Test
    void hint_nullRequestReturnsFallback() {
        assertEquals(PermissionHintService.FALLBACK_HINT, svc.hint(null, null));
    }

    @Test
    void hint_handlerMappingFailureFallsBackWithoutThrowing() {
        when(handlerMapping.getHandlerMethods()).thenThrow(new IllegalStateException("mvc not ready"));
        assertEquals(PermissionHintService.FALLBACK_HINT,
                svc.hint(new MockHttpServletRequest("GET", "/api/customer/1"), null));
    }

    // ==================== 标签表 ====================

    @Test
    void labelMap_firstRowWinsForDuplicatedPermission() throws Exception {
        Map<RequestMappingInfo, HandlerMethod> handlers = new LinkedHashMap<>();
        register(handlers, new DemoController(), "list", "/api/customer/{id}");
        when(handlerMapping.getHandlerMethods()).thenReturn(handlers);

        // seed 中 customer:view 有两行(id=11 客户管理 / id=13 客户详情)，须取 id 靠前的"客户管理"
        String msg = svc.hint(new MockHttpServletRequest("GET", "/api/customer/1"), null);
        assertTrue(msg.contains("「客户管理(customer:view)」"), msg);
        assertFalse(msg.contains("客户详情"), msg);
    }

    // ==================== buildMessage / 码提取（纯函数级） ====================

    @Test
    void buildMessage_exactTemplateForSingleLabelledCode() {
        String msg = PermissionHintService.buildMessage(
                List.of("customer:view"), Map.of("customer:view", "客户列表"));
        assertEquals("您的权限不支持此访问：缺少「客户列表(customer:view)」功能权限。"
                + "如需访问此内容，请联系管理员在【系统设置 → 权限中心 → 功能与范围】开启「客户列表」功能。", msg);
    }

    @Test
    void buildMessage_incentiveReadPrefixTreatedAsIncentiveAuth() {
        assertTrue(PermissionHintService.isIncentiveAuthCode("incentive:read-stats"));
        assertTrue(PermissionHintService.isIncentiveAuthCode("incentive:read-ranking:view"));
        assertTrue(PermissionHintService.isIncentiveAuthCode("incentive:view-7"));
        // 普通激励功能码（无 -view- 后缀/非 read 前缀）不属授权表口径
        assertFalse(PermissionHintService.isIncentiveAuthCode("incentive:view"));
        assertFalse(PermissionHintService.isIncentiveAuthCode("incentive:edit"));
    }

    @Test
    void buildMessage_mixedCodesUseIncentiveSectionWhenAnyIncentiveAuthCode() {
        String msg = PermissionHintService.buildMessage(
                List.of("customer:view", "incentive:view-7"), Map.of("customer:view", "客户管理"));
        assertTrue(msg.contains("专项权限（激励授权）"), msg);
    }

    @Test
    void buildMessage_exactlyThreeCodesNoTruncationMark() {
        String msg = PermissionHintService.buildMessage(
                List.of("a", "b", "c"), Map.of());
        assertTrue(msg.contains("「a」、「b」、「c」功能权限"), msg);
        assertFalse(msg.contains("等"), msg);
    }

    // ==================== 工具 ====================

    private static SysMenu menu(Long id, String name, String permission) {
        SysMenu m = new SysMenu();
        m.setId(id);
        m.setMenuName(name);
        m.setPermission(permission);
        m.setStatus(1);
        return m;
    }
}
