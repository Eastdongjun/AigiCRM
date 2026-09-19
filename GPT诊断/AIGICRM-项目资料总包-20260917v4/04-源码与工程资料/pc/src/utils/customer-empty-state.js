export function customerEmptyText(records) {
  return records.length
    ? '暂无符合筛选条件的客户'
    : '当前数据范围内暂无可见客户，请联系管理员检查数据范围'
}
