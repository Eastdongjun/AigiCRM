-- 激励记录"具体产品"字段（产品试点专属，可选文本）
-- 执行：mysql -uroot -pXXXX --default-character-set=utf8mb4 ycmn < specific-product-column.sql
ALTER TABLE biz_incentive_record
  ADD COLUMN specific_product varchar(200) NULL AFTER related_products;
