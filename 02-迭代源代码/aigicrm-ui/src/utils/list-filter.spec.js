import { describe, it, expect } from 'vitest'
import { filterCustomers } from './list-filter'

function c(id, over = {}) {
  return {
    id, customerName: '客户' + id, customerCode: 'C' + id,
    industry: '制造', status: 'normal', province: '陕西', city: '西安',
    protectionStatus: 'watch', ownerName: '张三', ownerId: 1,
    officeId: 10, orgId: 20, deptId: 30, registerDate: '2024-01-01',
    expireDate: '2026-01-01', ...over,
  }
}

// 每行组织字段有区分度，便于验证 orgIds 过滤
const data = [
  c(1),                                                    // office 10 org 20 dept 30, owner 1
  c(2, { customerName: '北京客户', status: 'expired' }),   // 同上
  c(3, { industry: '服务', ownerId: 2, ownerName: '李四',
         officeId: 11, orgId: 21, deptId: 31 }),            // 不同组织 + owner2
  c(4, { province: '山西', city: '太原', ownerId: 3, officeId: 11, orgId: 21, deptId: 31 }),
  c(5, { protectionStatus: 'danger', ownerName: '张三' }),
]

describe('filterCustomers 本地过滤', () => {
  it('空数组 / 非数组安全返回 []', () => {
    expect(filterCustomers([], '', {}, {})).toEqual([])
    expect(filterCustomers(null, '', {}, {})).toEqual([])
  })

  it('无筛选条件返回原数组', () => {
    expect(filterCustomers(data, '', {}, {})).toEqual(data)
  })

  it('search 匹配名称或编号', () => {
    const byName = filterCustomers(data, '北京', {}, {})
    expect(byName.map(x => x.id)).toEqual([2])
    const byCode = filterCustomers(data, 'c3', {}, {})
    expect(byCode.map(x => x.id)).toEqual([3])
  })

  it('filters.industry 过滤', () => {
    const r = filterCustomers(data, '', { industry: '服务' }, {})
    expect(r.map(x => x.id)).toEqual([3])
  })

  it('filters.status 过滤', () => {
    const r = filterCustomers(data, '', { status: 'expired' }, {})
    expect(r.map(x => x.id)).toEqual([2])
  })

  it('advFilter.province 包含匹配', () => {
    const r = filterCustomers(data, '', {}, { province: '山西' })
    expect(r.map(x => x.id)).toEqual([4])
  })

  it('advFilter.owner 包含匹配 ownerName', () => {
    // 张三 → id 1,2,4,5（id3 是李四）
    const r = filterCustomers(data, '', {}, { owner: '张' })
    expect(r.map(x => x.id)).toEqual([1, 2, 4, 5])
  })

  it('advFilter.regDate 闭区间', () => {
    const r = filterCustomers(data, '', {}, { regDate: ['2024-01-01', '2024-12-31'] })
    expect(r.map(x => x.id)).toEqual([1, 2, 3, 4, 5])
  })

  it('advFilter.orgIds 用 uidsSource 收集 ownerId 再过滤', () => {
    // orgIds=[10] 匹配 officeId=10 的行(id1,2,5) → 收集 ownerId={1}
    // → 过滤 ownerId=1：id1,2,5（id3 ownerId=2, id4 ownerId=3 排除）
    const r = filterCustomers(data, '', {}, { orgIds: [10] })
    expect(r.map(x => x.id)).toEqual([1, 2, 5])
  })

  it('orgIds 用 orgKey=orgId 收集', () => {
    // orgIds=[21] 匹配 orgId=21 的行(id3,4) → ownerId={2,3} → 只命中 id3,4
    const r = filterCustomers(data, '', {}, { orgIds: [21] }, { orgKeys: ['officeId', 'orgId'] })
    expect(r.map(x => x.id)).toEqual([3, 4])
  })

  it('orgIds 参数化 orgKeys=deptId（currentTabData 语义）', () => {
    // orgIds=[31] 匹配 deptId=31(id3,4) → ownerId={2,3} → 只命中 id3,4
    const r = filterCustomers(data, '', {}, { orgIds: [31] }, {
      uidsSource: data, orgKeys: ['officeId', 'deptId'],
    })
    expect(r.map(x => x.id)).toEqual([3, 4])
  })

  it('advFilter.userIds 过滤 owner', () => {
    const r = filterCustomers(data, '', {}, { userIds: [2] })
    expect(r.map(x => x.id)).toEqual([3])
  })

  it('advFilter.deptIds 过滤部门', () => {
    const r = filterCustomers(data, '', {}, { deptIds: [31] })
    expect(r.map(x => x.id)).toEqual([3, 4])
  })

  it('多层条件叠加', () => {
    // 名称含"客户" + status=normal + province=陕西
    // → id2(expired) 排除、id4(山西) 排除 → 剩 1,3,5
    const r = filterCustomers(data, '客户', { status: 'normal' }, { province: '陕西' })
    expect(r.map(x => x.id)).toEqual([1, 3, 5])
  })
})
