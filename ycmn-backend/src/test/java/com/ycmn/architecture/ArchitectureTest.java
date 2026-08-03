package com.ycmn.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.EvaluationResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分层架构约束
 *
 * 【需求】防止分层腐化：Controller 直查 Mapper / 直用 Entity 等越层访问
 * 【方法】ArchUnit 静态依赖分析，随 mvn test 执行
 * 【业务规则】controller -> service -> mapper/entity；禁止跳层；
 *             已知技术债见 docs/TECH_DEBT.md，重构一个移除一个
 */
public class ArchitectureTest {

    static final Set<String> TECH_DEBT = Set.of(
            "AuthController", "ApprovalController", "ApprovalConfigController",
            "CustomerController", "FileController", "IncentiveController",
            "ReminderController", "ScoreController", "SeaController",
            "SysConfigController", "SysDeptController", "SysDictController",
            "SysGroupController", "SysLegalEntityController", "SysLogController",
            "SysMenuController", "SysOrgController", "SysPermissionRuleController",
            "SysPositionController", "SysRoleController", "SysUserController");

    private static List<String> newViolations(EvaluationResult result) {
        return result.getFailureReport().getDetails().stream()
                .filter(detail -> TECH_DEBT.stream().noneMatch(detail::contains))
                .toList();
    }

    @Test
    void controller_must_not_depend_on_mapper() {
        JavaClasses classes = new ClassFileImporter().importPackages("com.ycmn");
        EvaluationResult result = noClasses()
                .that().resideInAPackage("..controller..")
                .should().dependOnClassesThat().resideInAPackage("..mapper..")
                .because("分层约束：Controller 不得直查 Mapper（技术债清单见 docs/TECH_DEBT.md）")
                .evaluate(classes);
        List<String> violations = newViolations(result);
        assertTrue(violations.isEmpty(),
                () -> "新增违规（非技术债清单）：\n" + String.join("\n", violations));
    }

    @Test
    void controller_must_not_depend_on_entity() {
        JavaClasses classes = new ClassFileImporter().importPackages("com.ycmn");
        EvaluationResult result = noClasses()
                .that().resideInAPackage("..controller..")
                .should().dependOnClassesThat().resideInAPackage("..entity..")
                .because("分层约束：Controller 不得直用 Entity（技术债清单见 docs/TECH_DEBT.md）")
                .evaluate(classes);
        List<String> violations = newViolations(result);
        assertTrue(violations.isEmpty(),
                () -> "新增违规（非技术债清单）：\n" + String.join("\n", violations));
    }

    @Test
    void service_must_not_depend_on_controller() {
        JavaClasses classes = new ClassFileImporter().importPackages("com.ycmn");
        EvaluationResult result = noClasses()
                .that().resideInAPackage("..service..")
                .should().dependOnClassesThat().resideInAPackage("..controller..")
                .because("分层约束：Service 不得反向依赖 Controller")
                .evaluate(classes);
        assertTrue(result.getFailureReport().getDetails().isEmpty(),
                () -> "Service 依赖 Controller 违规：\n"
                        + String.join("\n", result.getFailureReport().getDetails()));
    }
}
