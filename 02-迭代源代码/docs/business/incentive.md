# 激励(四项激励)与分享制

> 来源锚点:`IncentiveService`(applyShareVisibility/canViewIncentiveUser/getUserList/scoreboard/getCustomerRecords)。核实日期 2026-09-24。

## 1. 分类体系

- **激励记录四大父类(自建表 `biz_incentive_category`,parent_id=0 四大类):试点(trial) / 两谈两做(plan) / 交流拜访(exchange) / 外出参观(visit)**——排序权重即此顺序(IncentiveService 四区权重 0→3)。【更正 2026-10-08 需求对齐】原文误把客户开发类型(dev_type)字典终态记为激励分类父类;`成熟创新/突破开拓/铺垫宣传/新建改建扩建大修`(V2026.09.23.6,最后一项为完整词不可拆)是**客户开发类型字典**,仅作为激励查询的一个筛选维度,不是激励分类。
- 记录周期 period 现值口径 `2026全年`;空周期不放行等值过滤(空串≠匹配)。

## 2. 分值与佐证

- 每条记录:分类、分值 score、三阶段佐证文件槽(安装前 file_pre_id / 安装中 file_mid_id / 安署后 file_post_id)。
- **无佐证文件不计分**(fileCountOf=0 → 该条 0 分入榜)。
- 编辑他人记录的资格=授权面板 `can_edit`;本人记录编辑另有归属校验放行;update 只校验**本次新引入**的文件引用归属(防止"填他人文件 ID 保分",同时不误伤存量文件属原作者的编辑)。

## 3. 分享制可见性(激励模块独立于数据范围体系)

```
可见记录 = 本人全部记录 ∪ (他人 shared=1 且本人持"查看已分享"资格)
查看某人 = canSeeAll ∨ 是本人 ∨ (持查看分享资格 ∧ 该人存在任一 shared=1 记录)
```

- 授权面板(`biz_incentive_grant`,仅 admin 可改):`can_view_shared`(默认 **1**,全员开)/ `can_view_all` / `can_edit` / `can_delete`。
- admin 恒见全部;can_view_all/can_edit/can_delete 任一为 1 也视作全量可见(canSeeAllIncentive)。
- **人员筛选下拉口径(09-24 修正)= 上述查看语义**:本人+有 shared 记录的作者;canSeeAll → 全量。不再按数据范围过滤(原口径跨团队同事"看得到记录却选不到人")。

## 4. 排行口径(三处,注意差异)

| 排行 | 数据源 | 口径 |
| --- | --- | --- |
| 个人排行(`/incentive/stats` Tab、小程序排行中心) | `/incentive/scoreboard` | **公司级全量**(含未分享聚合分)——**裁决 2026-09-24:允许暴露**聚合分(明细仍走分享制);导出按钮 `incentive:person-rank:export` 默认仅 admin 可勾选 |
| 客户排行(激励统计页 Tab) | `/incentive/customer-records` | **分享制**(本人+他人 shared);客户名对分享记录引用的客户可见 |
| 客户排行(独立客户排行页 `/ranking/customer`、小程序排行中心) | `/statistics/customer-rankings` | 四项激励聚合=**分享制**(09-24 对齐);同页客情/六项指标排行=客户可见范围(G2 安全设计,与激励口径**有意不同**,待产品裁决是否统一) |

## 5. 阅读跟踪

- 每条记录对每个查看人独立已读态(`markRead`);未读驱动角标。
- 已读统计 `incentive:read-stats`(按集团/月);**本月激励查询阅读排行榜** `incentive:read-ranking:view`(默认仅 admin,其他角色由管理员在角色管理勾选)。

## 6. 上传

- 上传入口 `incentive:add`;佐证文件挂文件中心;客户/分类为必填。
- 小程序支持编辑/追加文件(死入口已于 09-24 激活:详情页记录卡→上传页编辑态)。
