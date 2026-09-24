# 财务记录(开票/回款)

> 来源锚点:`CustomerController` 财务端点、`BizInvoice/BizPayment` 实体、`CustomerService` 保护期公式。核实日期 2026-09-24。

## 1. 数据模型

| 实体 | 关键字段 | 说明 |
| --- | --- | --- |
| 开票 `biz_invoice` | invoice_date/invoice_number/invoice_type/invoice_company/amount/created_by | 一客户多票 |
| 回款 `biz_payment` | (同构:日期/金额/经手) | 与开票分列 |

财务记录挂在客户详情财务页签,PC 与小程序同源。

## 2. 权限

- 读:`customer:view` + `finance:view`(无 finance:view 时页签返回空,不报错)。
- 增删:`finance:manage` **或**(`customer:edit` + `finance:view`)——V2026.09.23.4 起 finance:manage 为可勾选专职授权码,两类持有者都可操作。

## 3. 与保护期的联动(核心业务规则)

```
最近开票日(last_invoice_date)= MAX(该客户全部有效开票的 invoice_date)
到期日 = COALESCE(最近开票日, 注册日) + 360 + 累计已批延期
```

- **新增开票 → 最近开票日前移 → 到期日顺延**(保护期随最新开票滚动);
- **删除开票 → 回算**最近开票日(可能变早),到期日随之下调;**已批延期不收回**(累计延期独立于开票,见 [customer-lifecycle.md](customer-lifecycle.md) §3)。
- 开票日因此成为保护期的核心输入——这也是"注册日/最近开票日录入后仅总监+行政+admin 可改"(23.2)的原因。

## 4. 与提醒的联动

- 从未开票 → `invoice_never` 提醒;最近开票距今 >365 天 → `invoice_overdue` 提醒(见 [reminder.md](reminder.md) §1)。

## 5. 待核实(未逐行验证,引用前先查代码)

- 回款(payment)是否有独立提醒/统计口径;
- 开票金额是否参与核心客户判定(六项指标的开票/回款 ≥100 万阈值来自统计侧,联动细节在 statistics 模块)。
