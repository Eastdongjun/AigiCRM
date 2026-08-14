# 功能受理

## 用户目标

系统设置→审批设置→"客户捞取"流程（flow_type 为乱码"????"，用户自加的测试数据）删除掉。

## 当前行为

- biz_approval_config 两条：id=1 flow_name="客户捞取" flow_type="????"（乱码，用户添加的测试数据）、id=2 flow_name="捞取客户审批" flow_type="claim"（正常业务配置）
- 引用检查：biz_approval_flow 仅 claim/transfer/extend/storage 四种，无记录引用乱码 flow_type → 删除无关联影响

## 范围

- 删除 biz_approval_config id=1（单条 DELETE，含 flow_name 条件防止误删）；浏览器验证审批配置页

## 不在范围

- id=2"捞取客户审批"保留；生产不动

## 来源与事实

- 用户输入："系统设置-审批设置 客户捞取 ???? 这个是我加的删除掉"
- 数据库查询确认（测试服 shared-mysql）

## 约束

- 测试服 111.229.37.220；删除条件 WHERE id=1 AND flow_name='客户捞取'

## 初始风险

- 极低：已确认无引用，可恢复（DELETE 前已核对，无备份留存——如需恢复可重建）
