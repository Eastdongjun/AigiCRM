import { describe, expect, it } from 'vitest'
import {
  NO_REMINDER_ID,
  buildReminderDetailLocation,
  customerRegion,
  customerStatusLabel,
  daysUntil,
  normalizeCustomerBrief,
  remainingDaysTagType,
  remainingDaysText,
  reminderStatusTagType,
  reminderStatusText,
  reminderTimingText,
  reminderTypeLabel,
  rowReminderId,
  rowReminderType
} from './reminder-detail'

describe('reminder-detail', () => {
  it('reads reminder id from either reminderId (share/invoice rows) or id (expiring rows)', () => {
    expect(rowReminderId({ reminderId: 541 })).toBe('541')
    expect(rowReminderId({ id: 542 })).toBe('542')
    expect(rowReminderId({ reminderId: null, id: 543 })).toBe('543')
    expect(rowReminderId({})).toBeNull()
    expect(rowReminderId(null)).toBeNull()
  })

  it('builds a routable detail location carrying customer and type', () => {
    expect(buildReminderDetailLocation({
      reminderId: 541, customerId: 295, reminderType: 'expiring'
    })).toEqual({
      name: 'ReminderDetail',
      params: { id: '541' },
      query: { customerId: '295', type: 'expiring' }
    })
  })

  it('uses the placeholder id when today reminder is not materialized', () => {
    const location = buildReminderDetailLocation({ customerId: 295, _type: 'expiring' })
    expect(location.params.id).toBe(NO_REMINDER_ID)
    expect(location.query).toEqual({ customerId: '295', type: 'expiring' })
  })

  it('falls back to the row group type when reminderType is absent', () => {
    expect(rowReminderType({ reminderType: 'share_to' })).toBe('share_to')
    expect(rowReminderType({ _type: 'invoice', _invoiceType: 'invoice_never' })).toBe('invoice_never')
    expect(rowReminderType({ _type: 'share' })).toBe('share')
    expect(rowReminderType({})).toBeNull()
  })

  it('labels reminder types and unknown types', () => {
    expect(reminderTypeLabel('expiring')).toBe('到期提醒')
    expect(reminderTypeLabel('invoice_overdue')).toBe('开票超期提醒')
    expect(reminderTypeLabel('share_owner')).toBe('你的客户被分享')
    expect(reminderTypeLabel('mystery')).toBe('mystery')
    expect(reminderTypeLabel(null)).toBe('提醒')
  })

  it('maps reminder status to text and tag type', () => {
    expect(reminderStatusText('pending')).toBe('待处理')
    expect(reminderStatusText('processed')).toBe('已处理')
    expect(reminderStatusText(null)).toBe('—')
    expect(reminderStatusTagType('pending')).toBe('warning')
    expect(reminderStatusTagType('processed')).toBe('success')
  })

  it('computes remaining days and tones around the expiry date', () => {
    const today = new Date('2026-09-13T10:00:00')
    expect(daysUntil('2026-09-18', today)).toBe(5)
    expect(daysUntil('2026-09-13', today)).toBe(0)
    expect(daysUntil('2026-08-14', today)).toBe(-30)
    expect(daysUntil(null, today)).toBeNull()
    expect(daysUntil('not-a-date', today)).toBeNull()
  })

  it('describes expiry urgency consistently', () => {
    expect(remainingDaysText(-30)).toBe('已过期 30 天')
    expect(remainingDaysText(3)).toBe('即将逾期')
    expect(remainingDaysText(20)).toBe('临期提醒')
    expect(remainingDaysText(60)).toBe('关注')
    expect(remainingDaysText(200)).toBe('正常')
    expect(remainingDaysText(null)).toBe('—')
    expect(remainingDaysTagType(-30)).toBe('danger')
    expect(remainingDaysTagType(20)).toBe('warning')
    expect(remainingDaysTagType(60)).toBe('info')
    expect(remainingDaysTagType(200)).toBe('success')
  })

  it('labels customer status with the shared dictionary', () => {
    expect(customerStatusLabel('warning')).toBe('预警')
    expect(customerStatusLabel('sea')).toBe('公海')
    expect(customerStatusLabel('weird')).toBe('weird')
    expect(customerStatusLabel(null)).toBe('—')
  })

  it('joins province/city/district into a region label', () => {
    expect(customerRegion({ province: '山西省', city: '临汾市', district: '尧都区' })).toBe('山西省临汾市尧都区')
    expect(customerRegion({ province: '山西省' })).toBe('山西省')
    expect(customerRegion({ region: '山西省临汾市' })).toBe('山西省临汾市')
    expect(customerRegion(null)).toBeNull()
  })

  it('normalizes both detail-API and customer-entity shapes', () => {
    expect(normalizeCustomerBrief({
      id: 301, customerCode: 'C0301', customerName: '山西平阳重工', status: 'warning',
      industry: '煤机', region: '山西省临汾市', expireDate: '2026-10-01', lastInvoiceDate: '2026-06-01',
      ownerId: 7, ownerName: '张销售'
    })).toEqual({
      id: 301, customerCode: 'C0301', customerName: '山西平阳重工', status: 'warning',
      industry: '煤机', region: '山西省临汾市', expireDate: '2026-10-01', lastInvoiceDate: '2026-06-01',
      ownerId: 7, ownerName: '张销售'
    })
    const fromEntity = normalizeCustomerBrief({ id: 8, province: '山西省', city: '太原市' })
    expect(fromEntity.region).toBe('山西省太原市')
    expect(fromEntity.customerName).toBeNull()
    expect(normalizeCustomerBrief(null)).toBeNull()
  })

  it('describes reminder timing per reminder type', () => {
    expect(reminderTimingText({ reminderType: 'invoice_overdue', overdueDays: 12 }))
      .toBe('已超期 12 天未开票')
    expect(reminderTimingText({ reminderType: 'invoice_never', overdueDays: 0 })).toBe('待首次开票')
    expect(reminderTimingText({ reminderType: 'share_to' })).toBe('客户分享提醒')
    // 到期提醒按客户 expireDate 现算（不依赖固定"今天"）
    expect(reminderTimingText({ reminderType: 'expiring' }, { expireDate: '2099-01-01' }))
      .toMatch(/^剩余 \d+ 天到期$/)
    expect(reminderTimingText({ reminderType: 'expiring' }, { expireDate: '2000-01-01' }))
      .toMatch(/^已过期 \d+ 天$/)
    expect(reminderTimingText({ reminderType: 'expiring', overdueDays: 3 }, {}))
      .toBe('剩余 3 天到期')
    expect(reminderTimingText({ reminderType: 'expiring' }, {})).toBe('—')
  })
})
