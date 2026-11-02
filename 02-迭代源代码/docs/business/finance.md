# 财务记录(开票/回款)

> 来源锚点:`CustomerController` 财务端点、`BizInvoice/BizPayment/BizCustomerStageAmount` 实体、`CustomerService` 保护期公式。核实日期 2026-10-18(2026-10-18 延期规则收敛 + 公海删除守卫)。

## 1. 数据模型

> **【裁决 2026-10-10】开票回款改阶段总额制(现行主口径)**:录入载体为阶段行
> `biz_customer_stage_amount`(唯一键 customer_id+period_year+period_type,半年/全年一行两金额——
> 开票额+回款额,万元,FOR UPDATE 行锁 upsert);存量逐笔票/款按年聚合回填 FY 行(V2026.10.10.1)。
> **逐笔发票/回款录入入口已下线改只读历史**(FinanceTab 历史明细只读折叠区),逐笔写端点
> (add/void/confirm/delete)保留但无 UI 消费——confirm 对留作批量导入预留(裁决 2026-10-09),
> add/void/delete 为悬空能力,纠错一律走阶段行编辑覆盖。**核心客户三态判定的金额源=阶段总额
> 年度口径**(纯金额门槛,五项评分不再参与,见 StatisticsQueryService 注释)。
> 阶段总额表已入租户白名单(体检 H4 2026-10-12: insert 自动补租户/查询自动隔离)。

| 实体 | 关键字段 | 说明 |
| --- | --- | --- |
| 阶段总额 `biz_customer_stage_amount`(现行录入) | period_year/period_type(H1/H2/FY)/invoice_amount/payment_amount | 半年/全年一行两金额,upsert 覆盖式录入 |
| 开票 `biz_invoice`(历史只读) | invoice_date/invoice_number/invoice_type/invoice_company/amount/created_by | 一客户多票;存量+导入承接,录入入口已下线 |
| 回款 `biz_payment`(历史只读) | (同构:日期/金额/经手) | 与开票分列;存量+导入承接,录入入口已下线 |

财务记录挂在客户详情财务页签,PC 与小程序同源(小程序阶段数据为只读历史口径)。

## 2. 权限

- 读:`customer:view` + `finance:view`(无 finance:view 时页签返回空,不报错)。
- 增删:`finance:manage` **或**(`customer:edit` + `finance:view`)——V2026.09.23.4 起 finance:manage 为可勾选专职授权码,两类持有者都可操作。
  > ✅【E1 闭环 2026-10-21】阶段数据保存端点(POST stage-amounts)已由 assertCanEdit 改为与发票/回款端点
  > 同口径的 assertCanManageFinance:仅持 finance:manage 的专职授权码用户可保存(不再要求 customer:edit),
  > 上方"两类持有者都可操作"的承诺对全部财务写端点一致成立。2026-10-12 登记的已知缺口就此关闭。

## 3. 与保护期的联动(核心业务规则)

```
最近开票日(last_invoice_date)= MAX(该客户全部非作废开票的 invoice_date)
到期日 = MAX(最近开票日, 注册日) + 360 + 累计已批延期
```

> **【公式演进 2026-10-09 MAX 基准 → 2026-11-02 三线口径】到期日=MAX(建档+365, 开票+365, 捞取+180)+累计延期**
> (口径批 2026-11-02 裁决A/B:一年=365 天、捞取后短保护 180 天,详见 [customer-lifecycle.md](customer-lifecycle.md) §3)。
> 2026-10-09 的 MAX(开票日,注册日) 单基准为三线口径的前身——其防回归语义(老票/补录旧票不得把
> 基准拉回过去,legacy 2015/2024 年老票不再触发"捞取→掉海→再捞"死循环)由三线 MAX 完整继承:
> 老票的开票线永远早于捞取线/建档线,不会拖垮保护期;捞取的保护期起点承诺由捞取线稳定保持。

> **【裁决 2026-09-24 → 2026-11-02 翻案】最近开票日=推导字段+人工维护通道**:以最后一次开发票的
> 日期为准,自动推导"只前进";**持 `customer:invoice-date:edit` 权限码者(后台角色管理勾选,目标=
> 行政/销售总监/系统管理员,admin 免勾)可人工直改**——人工设定=新基准语义(清零已批延期+按三线公式
> 重算),与"只前进"共存,不再存在被开票重算静默覆盖的问题(旧裁决砍人工通道的根因)。客户创建时
> 仍不接受表单直带;纠错另一路径=财务页发票补录/作废,全程留痕。
> **发票/回款可删但必留痕(裁决当日二次更新)**:任何状态均可删除,**必填删除原因**;删除时写 sys_log
> 审计快照(票号/日期/金额/状态/原因/操作人),数据行 @TableLogic 逻辑删保留可回查;保护期自动回算。
> 更正常规口径仍推荐"作废"(voided 不进统计与保护期)。V2026.09.24.1 已为 258 个 legacy 客户回填
> "历史导入"票承接旧手工日期,切换零行为变化。
>
> **【裁决 2026-10-09】台账状态机=录入直落 confirmed**:手动录入开票/回款不传 status,落库取列默认
> 'confirmed'(V2026.09.19.7 建列时给存量的默认)——录入即生效,信任录入人;**pending(待核对)分支
> 全代码链无产生路径**(查证:生产 282 票+5 款全部 confirmed 零 pending),「确认」按钮与
> confirmInvoice/confirmPayment 接口为不可达死分支,**留作未来批量导入场景预留,不删不改**;
> 三态(confirmed/voided + 预留 pending)语义维持。

> **【延期规则收敛 2026-10-18·审计 biz-0,生效 2026-10-18】** 新开票/阶段总额只在**把最近开票日
> 向前推进(新值晚于旧值)**时清零 `approved_extend_days`,并重算到期日一次:新基准上的 360 天
> 保护期已覆盖旧基准批出的延期,继续叠加会双重延长。**补录早于(或等于)当前最近开票日的旧票
> 不清零、不回退基准、不重算**——旧实现 `addInvoice` 无条件清零,补录历史票会误伤已批延期。
> 逐笔 `addInvoice` 与阶段总额 `saveStageAmount` 共用 `CustomerService.advanceLastInvoiceDate` 单一逻辑。
> 删票/作废仍走回算(最近开票日可能变早),**不收回**已批延期(2026-09-19 议题一裁决维持)。

- **新增开票/阶段开票额 → 最近开票日前移 → 到期日顺延**(保护期随最新开票滚动);
  若前移,同一次落库内清零已批延期(见上条 2026-10-18 规则);
- **补录旧票(日期早于当前最近开票日)→ 基准与延期均不动**(仅入台账,不驱动保护期);
- **作废/删除开票 → 回算**最近开票日(可能变早),到期日随之下调;已批延期不收回。
- 开票日因此成为保护期的核心输入——纯推导模型下无需"注册日/开票日录入权限",注册日仍为受控字段(customer:date:edit:总监 0/行政 05/admin)。

## 4. 与提醒的联动

- 从未开票 → `invoice_never` 提醒(**建客超过宽限期才触发**:系统设置 `invoice.never.grace.days` 默认 30 天,0=无宽限;裁决 2026-09-24 P1-018,补记于 2026-10-08);最近开票距今 >365 天 → `invoice_overdue` 提醒(见 [reminder.md](reminder.md) §1)。

## 5. 已核实(原"待核实"段, 2026-10-12 收口)

- 回款(payment):无独立提醒;统计口径与开票合并进阶段行两金额。
- 开票金额参与核心客户判定:**【裁决 2026-10-10 已定案】判定金额源=阶段总额年度口径**
  (纯金额三态:真核心/争核心按年度门槛,五项评分不再参与),联动实现见 StatisticsQueryService
  与 CustomerService.computeCoreType;判定详情见 [customer-lifecycle.md](customer-lifecycle.md)。
