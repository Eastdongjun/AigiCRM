package com.ycmn.architecture;

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

    /** 权限码字典相对路径：测试运行工作目录为 ycmn-backend/，回溯到 04-源码/docs/permission/ */
    private static final String DICT_REL_PATH = "../docs/permission/permission-dict.md";

    /**
     * 例外白名单（类名 + 方法名 精确匹配，命中则跳过权限码校验）：
     * - AuthController：匿名/自服务端点（login/logout/forgot-password 系列）
     * - SysUserController.changePassword：自服务改密
     * - IncentiveController.updateRecord/deleteRecord：方法内守卫（admin ∨ 矩阵码 ∨ 用户级授权 ∨ 本人）
     * - FolderAclController 写方法：服务层 FolderAclService 守卫
     * - FileController 写方法：服务层 FolderPermissionService 守卫
     * - ReminderController.process：有意用 reminder:view 保护写操作（设计取舍）
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
            "SeaController.deletePool", // 2026-08-10 池级权限: 删除权限在 SeaService 内按池判定, 角色码 sea:pool:delete 不作硬门
            // 2026-08-10 池级编辑权限: 客户编辑流(update/日志/开票/指标/关系/分享/释放/联系人/回款)改为
            //   CustomerAccessService.canEdit 行级判定——池内客户按池级 PERM_EDIT(缺 customer:edit 角色也可保存),
            //   非池客户需 customer:edit 能力 + 负责人; 注解角色码不再作硬门
            "CustomerController.update",
            "CustomerController.addLog",
            "CustomerController.addInvoice",
            "CustomerController.deleteInvoice",
            "CustomerController.saveIndicator",
            "CustomerController.saveRelationship",
            "CustomerController.addShares",
            "CustomerController.removeShare",
            "CustomerController.release",
            "CustomerController.addContact",
            "CustomerController.updateContact",
            "CustomerController.deleteContact",
            "CustomerController.addPayment",
            "CustomerController.deletePayment",
            "FolderAclController.saveAcl",
            "FolderAclController.removeAcl",
            "FolderAclController.setDownloadable",
            "FileController.upload",
            "FileController.createFolder",
            "FileController.renameFolder",
            "FileController.deleteFolder",
            "FileController.deleteFile",
            "FileController.replaceFile",
            "FileController.shareFile",
            "ReminderController.process");

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

    /** 防止白名单静默腐化: CustomerController 白名单写方法须仍在方法体内调用行级守卫 assertCanEdit(见上白名单注释) */
    @Test
    void whitelistedCustomerEditMethods_mustRetainAssertCanEditGuard() {
        long whitelisted = WHITELIST.stream().filter(k -> k.startsWith("CustomerController.")).count();
        if (whitelisted == 0) {
            return; // 无该类白名单时跳过
        }
        Path src = Paths.get("src/main/java/com/ycmn/module/customer/controller/CustomerController.java")
                .toAbsolutePath().normalize();
        assertTrue(Files.exists(src), "CustomerController 源文件不存在: " + src);
        String body;
        try {
            body = Files.readString(src);
        } catch (IOException e) {
            throw new IllegalStateException("读取 CustomerController 源码失败", e);
        }
        long guards = countSubstring(body, "assertCanEdit(");
        assertTrue(guards >= whitelisted,
                () -> "白名单 CustomerController 写方法 " + whitelisted + " 个, 但源码仅 " + guards
                        + " 处 assertCanEdit( 调用——白名单方法体内行级守卫缺失, 权限将无兜底");
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
                            + "请确认从 ycmn-backend/ 运行 mvn test（相对路径 ../docs/permission/permission-dict.md）");
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

    /** 用 ArchUnit 扫描 com.ycmn 下所有 @RestController 类（限定 controller 包） */
    private static List<Class<?>> scanControllerClasses() {
        JavaClasses classes = new ClassFileImporter().importPackages("com.ycmn");
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

    private static String describe(WriteMethod wm) {
        return wm.clazz().getName() + "." + wm.method().getName() + " (" + wm.mapping() + ")";
    }
}
