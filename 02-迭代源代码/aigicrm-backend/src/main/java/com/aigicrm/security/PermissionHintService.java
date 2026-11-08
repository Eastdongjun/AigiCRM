package com.aigicrm.security;

import com.aigicrm.module.system.entity.SysMenu;
import com.aigicrm.module.system.mapper.SysMenuMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 【2026-11-08 权限报错中文化】403 提示“缺什么权限、去哪开”自助指引服务。
 *
 * 背景：@PreAuthorize 拒绝原先只回“没有访问权限”，用户/管理员无法定位是哪个功能未开。
 * 思路：启动后懒加载构建两张缓存（TTL 10 分钟，菜单/端点为低频变更数据，兜底可手动 refresh）：
 * a) 端点表：遍历 {@link RequestMappingHandlerMapping#getHandlerMethods()}，对带 @PreAuthorize 的
 *    方法（含类级）解析出权限码集合（hasAuthority('x') / hasAnyAuthority('a','b')），
 *    键=(HTTP 方法集合 + 路径模式，含 {id} 模板，Spring Framework 7 的 RequestMappingInfo#getPatternValues）
 * b) 标签表：sys_menu 的 permission → menu_name（同码多行按 id 升序取第一行；无行者标签=码本身）
 *
 * hint(request, fallback)：按 method+path 用 {@link AntPathMatcher} 匹配最佳（最具体）模式；
 * 命中→返回“缺少「标签(权限码)」…请在【系统设置 → 权限中心 → 功能与范围】开启「标签」功能”；
 * 未命中→返回 fallback 全量指引。incentive:view-* / incentive:read* 走授权表（2026-08-16 授权表
 * 替代旧权限码口径），指引段改为“权限中心 → 专项权限（激励授权）”。
 *
 * 卫生约束：任何构建/匹配异常都不得改变 403 语义——内部全捕获、退回 fallback 文案。
 */
@Slf4j
@Component
public class PermissionHintService {

    /** 兜底全量指引：端点未命中（无 @PreAuthorize 的 anyRequest 拒绝、标签/端点表异常等）时使用 */
    public static final String FALLBACK_HINT =
            "您的权限不支持此访问。如需访问此内容，请联系管理员在【系统设置 → 权限中心】检查角色功能权限与数据范围。";

    /** 消息模板：命中端点后拼装（多码用顿号并列，最多列 3 个+“等”） */
    static final String MESSAGE_TEMPLATE =
            "您的权限不支持此访问：缺少%1$s功能权限。如需访问此内容，请联系管理员在【系统设置 → 权限中心 → %2$s】开启「%3$s」功能。";

    /** 权限中心默认指引段（菜单功能权限） */
    static final String SECTION_FUNC_SCOPE = "功能与范围";
    /** 激励授权类权限码（前缀 incentive:view- 或 incentive:read，走授权表）的指引段 */
    static final String SECTION_INCENTIVE_AUTH = "专项权限（激励授权）";

    private static final long TTL_MS = 10 * 60 * 1000L;
    private static final int MAX_LISTED_CODES = 3;

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    /** 匹配 hasAuthority('x') / hasAnyAuthority('a','b', ...)，参数段整体捕获后再逐个提取引号码 */
    private static final Pattern AUTH_CALL_PATTERN =
            Pattern.compile("has(?:Any)?Authority\\s*\\(\\s*([^)]*)\\)");
    private static final Pattern QUOTED_CODE_PATTERN = Pattern.compile("'([^']+)'");

    /**
     * 【修复 2026-11-08 启动歧义】容器内存在两个 HandlerMapping 候选
     * (requestMappingHandlerMapping + actuate 的 controllerEndpointHandlerMapping),
     * Lombok @RequiredArgsConstructor 不带限定符 → NoUniqueBeanDefinition 启动失败。
     * 显式构造器 + @Qualifier 钉住 MVC 主映射。单测 mock 环境测不出, 测试服启动实测抓出。
     */
    private final RequestMappingHandlerMapping handlerMapping;
    private final SysMenuMapper menuMapper;

    public PermissionHintService(
            @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping")
            RequestMappingHandlerMapping handlerMapping,
            SysMenuMapper menuMapper) {
        this.handlerMapping = handlerMapping;
        this.menuMapper = menuMapper;
    }

    /** 快照缓存：volatile 读 + 同步构建（懒加载，TTL 过期后首个 403 请求触发重建） */
    private volatile Snapshot snapshot;

    /**
     * 生成 403 中文提示。request 为被拒请求；fallback 为未命中端点时的兜底文案（传 null 用 {@link #FALLBACK_HINT}）。
     * 线程安全；任何内部异常（含 DB/MVC 元数据读取失败）均降级返回 fallback，绝不向上抛。
     */
    public String hint(HttpServletRequest request, String fallback) {
        String safeFallback = fallback != null ? fallback : FALLBACK_HINT;
        if (request == null) {
            return safeFallback;
        }
        try {
            Snapshot snap = currentSnapshot();
            String path = stripContextPath(request);
            String method = request.getMethod();

            Endpoint best = null;
            for (Endpoint e : snap.endpoints()) {
                if (!e.methods().isEmpty() && !e.methods().contains(method)) {
                    continue;
                }
                if (!PATH_MATCHER.match(e.pattern(), path)) {
                    continue;
                }
                if (best == null
                        || PATH_MATCHER.getPatternComparator(path).compare(e.pattern(), best.pattern()) < 0) {
                    best = e;
                }
            }
            if (best == null || best.codes().isEmpty()) {
                return safeFallback;
            }
            return buildMessage(best.codes(), snap.labels());
        } catch (Exception ex) {
            // 指示性文案失败不得影响 403 响应本身
            log.warn("构建权限提示失败, 退回兜底文案 uri={}", request.getRequestURI(), ex);
            return safeFallback;
        }
    }

    /** 手动刷新（菜单/端点变更后可调用；常规场景靠 TTL 自然过期） */
    public void refresh() {
        synchronized (this) {
            snapshot = buildSnapshot();
        }
    }

    // ==================== 内部实现 ====================

    private Snapshot currentSnapshot() {
        Snapshot snap = snapshot;
        if (snap != null && System.currentTimeMillis() < snap.expireAt()) {
            return snap;
        }
        synchronized (this) {
            snap = snapshot;
            if (snap != null && System.currentTimeMillis() < snap.expireAt()) {
                return snap;
            }
            snap = buildSnapshot();
            snapshot = snap;
            return snap;
        }
    }

    private Snapshot buildSnapshot() {
        List<Endpoint> endpoints = new ArrayList<>();
        try {
            for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMapping.getHandlerMethods().entrySet()) {
                RequestMappingInfo info = entry.getKey();
                HandlerMethod hm = entry.getValue();
                LinkedHashSet<String> codes = new LinkedHashSet<>();
                // 类级 + 方法级 @PreAuthorize 合并（类级兜底、方法级收紧为常见组合）
                collectAuthorityCodes(hm.getBeanType().getAnnotation(PreAuthorize.class), codes);
                collectAuthorityCodes(hm.getMethodAnnotation(PreAuthorize.class), codes);
                if (codes.isEmpty()) {
                    continue; // isAuthenticated() 等无权限码表达式无法给出“缺什么”，跳过
                }
                Set<String> methods = new HashSet<>();
                info.getMethodsCondition().getMethods().forEach(m -> methods.add(m.name()));
                for (String pattern : info.getPatternValues()) {
                    endpoints.add(new Endpoint(methods, pattern, List.copyOf(codes)));
                }
            }
        } catch (Exception e) {
            log.warn("扫描 @PreAuthorize 端点表失败", e);
        }

        Map<String, String> labels = new HashMap<>();
        try {
            // status=1 与权限查询口径一致（selectPermissionsByUserId 同样过滤）；同码多行按 id 升序取第一行
            List<SysMenu> menus = menuMapper.selectList(new QueryWrapper<SysMenu>()
                    .isNotNull("permission").ne("permission", "").eq("status", 1)
                    .orderByAsc("id"));
            for (SysMenu m : menus) {
                String perm = m.getPermission() != null ? m.getPermission().trim() : null;
                if (perm == null || perm.isEmpty()) {
                    continue;
                }
                labels.putIfAbsent(perm, m.getMenuName());
            }
        } catch (Exception e) {
            // 标签表失败仅退化展示（标签=码本身），不影响端点命中
            log.warn("加载 sys_menu 权限标签失败, 标签退化为权限码", e);
        }

        log.info("权限提示快照构建完成: 端点 {} 个, 标签 {} 条", endpoints.size(), labels.size());
        return new Snapshot(System.currentTimeMillis() + TTL_MS, List.copyOf(endpoints), Map.copyOf(labels));
    }

    /** 从 @PreAuthorize 表达式中提取 hasAuthority/hasAnyAuthority 的引号内权限码（保持出现顺序） */
    private static void collectAuthorityCodes(PreAuthorize anno, LinkedHashSet<String> sink) {
        if (anno == null) {
            return;
        }
        Matcher callMatcher = AUTH_CALL_PATTERN.matcher(anno.value());
        while (callMatcher.find()) {
            Matcher codeMatcher = QUOTED_CODE_PATTERN.matcher(callMatcher.group(1));
            while (codeMatcher.find()) {
                String code = codeMatcher.group(1).trim();
                if (!code.isEmpty()) {
                    sink.add(code);
                }
            }
        }
    }

    /** 拼装最终提示消息（包级私有，便于单测直接校验模板行为） */
    static String buildMessage(List<String> codes, Map<String, String> labels) {
        List<String> parts = new ArrayList<>();
        List<String> shown = codes.size() > MAX_LISTED_CODES ? codes.subList(0, MAX_LISTED_CODES) : codes;
        boolean truncated = codes.size() > shown.size();
        for (String code : shown) {
            String label = labels.getOrDefault(code, code);
            // 无菜单行的码（如授权表生成的 incentive:view-*）标签=码本身，避免「code(code)」重复展示
            parts.add(label.equals(code) ? "「" + code + "」" : "「" + label + "(" + code + ")」");
        }
        String codeList = String.join("、", parts) + (truncated ? "等" : "");

        boolean incentiveAuth = codes.stream().anyMatch(PermissionHintService::isIncentiveAuthCode);
        String section = incentiveAuth ? SECTION_INCENTIVE_AUTH : SECTION_FUNC_SCOPE;

        String firstCode = codes.get(0);
        String firstLabel = labels.getOrDefault(firstCode, firstCode);
        return String.format(MESSAGE_TEMPLATE, codeList, section, firstLabel);
    }

    /** 激励授权类码：前缀 incentive:view- 或 incentive:read（2026-08-16 起走授权表，非菜单功能权限） */
    static boolean isIncentiveAuthCode(String code) {
        return code.startsWith("incentive:view-") || code.startsWith("incentive:read");
    }

    private static String stripContextPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            return path.substring(contextPath.length());
        }
        return path;
    }

    // ==================== 值对象 ====================

    /** 端点条目：methods 为空集表示不限 HTTP 方法（@RequestMapping 未指定 methods） */
    record Endpoint(Set<String> methods, String pattern, List<String> codes) {
    }

    /** 不可变快照（端点表 + 标签表 + 过期时间） */
    record Snapshot(long expireAt, List<Endpoint> endpoints, Map<String, String> labels) {
    }
}
