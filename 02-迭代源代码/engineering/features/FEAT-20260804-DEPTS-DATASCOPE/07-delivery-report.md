# 交付报告

## 结果
/api/system/depts 越权修复:非管理员(销售员)仅可见本人部门及祖先链,与 self 数据范围一致;
激励查询页部门筛选功能不受影响。admin 全量不受限。

## 验证证据
- 生产实测:TY101 → 3 部门(公司→山西大区→太原办销售部);admin → 10 部门;激励接口 200
- mvn test 34/34;gate 报告见 evidence/

## 部署状态
- 生产已部署(2026-08-04 00:35,deploy-backend.sh,健康检查通过)

## 回滚
- 旧 jar(NAS 备份)+ rollback.sh;回滚后恢复全量可见(旧行为)

## 残余风险
- dept_id 为空的非管理员 → 空列表(前端容错);数据范围按本人部门上溯,不含下级部门

## 交付范围
- REQ-001 完成;AC-001/002/003 全部 PASS

## 变更清单
- aigicrm-backend/src/main/java/com/aigicrm/module/system/service/SysDeptService.java(scopeFilter + tree/list 接入)

## 安装与启动
- 无新依赖;jar 构建后 deploy-backend.sh 部署(已执行)

## 部署状态与步骤
- 生产已部署(2026-08-04 00:35,deploy-backend.sh,健康检查通过,commit 51caa19)

## 回滚与恢复
- 回滚旧 jar(NAS 备份 aigicrm-backend-jar-20260804-002106.jar)+ rollback.sh;回滚后恢复全量可见(旧行为)

## 已知限制与残余风险
- dept_id 为空的非管理员 → 空部门列表(前端容错);数据范围仅上溯祖先链,不含下级部门

## 版本与追踪
- Feature: FEAT-20260804-DEPTS-DATASCOPE;commit 51caa19;部署记录 deploy.log
