# DeepSeek Harness 全量只读扫描(四审)逐项裁定(2026-09-22)

> 审查方: 本地 DeepSeek headless harness(独立外审第四轮, 与 GPT 三轮/内审两轮互补); 交接见 runs/20260922-230626-deepseek-harness-scan。
> 裁定基线: d983702d; 修复后以本文件提交为准。纪律: 属实先实测核验再修; 全部裁定带证据/处置。

## 一、P0(2 项,均属实,本轮修复)

| 编号 | 内容 | 裁定与处置 |
|---|---|---|
| P0-1 | 用户更新/删除/重置口令接口缺对象级边界, 非 orgAdmin 调用方守卫短路(`isOrgAdmin()&&!canManageUser` 对非 orgAdmin 恒假跳过) | **属实-阻断级-已修**。实测: 行政角色(TY200/TY100/NM100 持 system:user:edit 无 org:admin)修复前可 `PUT /api/system/users/1` 篡改/禁用 admin。修复=三处守卫反转为 `!isAdmin() && !canManageUser`(update/delete/reset-password); 行为级实证: TY200 攻击 admin → 400"无权操作该用户", admin 数据未动; reset-password 另有服务层 H1 双闸 |
| P0-2 | deploy-db.sh 迁移前备份失败可被旧备份掩盖(远端分号序列吞 mysqldump 错码, ls 假绿) | **属实-已修**。改为: 本次备份文件名先定 → mysqldump 显式判错 → 非空校验, 失败硬退出 |

## 二、P1(6+1 项: 3 本轮修复, 3 属实列队列, 1 部分)

| 编号 | 内容 | 裁定与处置 |
|---|---|---|
| P1-审批tab | 未知/空 tab 落入无过滤分支, `?tab=x` 越过个人范围看全集团 | **属实-已修**: else 分支按最窄口径(非 admin 套 applyDataScope, 与 all 同闸) |
| P1-登录IP计数 | 成功登录 delete(ipFailKey), 穿插正确账号可清爆破计数 | **属实-已修**: 只清用户名键; IP 键保留至 TTL(结构测试断言同步到新契约) |
| P1-激励update | update 提供文件槽不验归属, 伪造文件 ID 保分 | **属实-已修**: fileTouched 时对合并后记录复用 create 同款 uploaderId 归属核对 |
| P1-提权链 | dept/position/config/role edit 仅功能码校验缺对象边界 | **属实-列高优队列**: 实测四码由 系统管理员+销售总监 持有(NO001 同样可及); 修复面大(四处对象边界各需测试), 定上线后首批; **部署时增加生产持权核查点**(若生产仅 admin 持有则暴露为理论级) |
| P1-转移/延期提交 | submitApproval 前无客户 canView 校验 | 属实-队列: 同集团内横向构造, 影响为审批记录创建; 下一批与审批撤回/超时合并修 |
| P1-public下载 | /download/public/** 不查 biz_file/下载开关 | 属实-队列(产品权衡): 改语义影响既有公开链路, 需产品确认后连同文件夹下载开关统一收口 |
| (半)lookup 存在性 | 09 号已裁定维持(限流+审计缓解) | 维持原裁定 |

## 三、P2/脚本/P3 批量裁定

- **属实-已知重合**(内审/GPT 已登记): TENANT 默认 false(prod 档 fail-closed 自检+compose 显式 true 双闸)/FormDraft 并发/客户状态字段透传/FileService 无台账(Outbox 已列产品)/配额仅 private/版本号并发/inline 历史/对账 OSS/统计 canViewShared 口径/Sea N+1+并发 500/size 未上限/KEYS/匿名 public config/前端 isAdmin 口径——分别对应 07/09 号文档队列, 无新增事实。
- **属实-新增采纳(脚本组, 下一批)**: deploy-frontend.sh 备份失败掩盖+rm -rf/deploy-backend.sh tail -1 掩盖构建失败/rollback.sh 缺 prod pin/deploy-db-test.sh 密码回退/check-permission-data.sh 远程拼接/auto-pipeline.sh 弱 JWT 默认/deploy-lib.sh sshpass/verify-delivery.sh 写死容器名/package-source.sh grep -c 与 set -e 死分支/verify-perm-matrix.sh 临时口令硬编码(测试夹具, 低)。
- **P3**: SysLog 日期 500/UserDetails NPE/KEYS 已知。**残余**: cleanroom .env 在源码树(gitignore 已挡, README 已声明一次性; 保持不入库纪律即可)。

## 四、本轮修复门禁

全量 **580/580**(含契约更新)+ 测试服部署(health UP)矩阵 **21/21** + Sonar **0** + 行为级攻击实证(P0-1 修复前后对照)。
教训入档: ①短路守卫反模式(`if(isX() && !can(x))` 对非 X 全放行)全库已扫无残留; ②矩阵夹具须绑定父链菜单且每权限一行(menus 树不变量); ③UATNOF 验收账号已恢复可用。

## 五、上线影响

P0 双修后, DeepSeek 四审无剩余阻断项; 提权链 P1 为唯一高优开放(缓解: 单一内部账号持权+部署时生产持权核查)。上线判定回到既有门槛: 真机 13 旅程。
