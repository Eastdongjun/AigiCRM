import { describe, expect, it } from 'vitest'
import { CUSTOMER_STATUS_MAP, customerStatusTagType, customerStatusText } from './customer-status'

describe('customer-status', () => {
  it('maps known status codes to Chinese labels and tag types', () => {
    expect(customerStatusText('normal')).toBe('正常')
    expect(customerStatusText('warning')).toBe('预警')
    expect(customerStatusText('expired')).toBe('超期')
    expect(customerStatusText('sea')).toBe('公海')
    expect(customerStatusTagType('expired')).toBe('danger')
  })

  it('falls back for unknown or empty status without throwing', () => {
    expect(customerStatusText('whatever')).toBe('—')
    expect(customerStatusText(null)).toBe('—')
    expect(customerStatusTagType('whatever')).toBe('info')
    expect(customerStatusTagType(undefined)).toBe('info')
  })

  it('keeps the dictionary stable for the four business statuses', () => {
    expect(Object.keys(CUSTOMER_STATUS_MAP).sort()).toEqual(['expired', 'normal', 'sea', 'warning'])
  })
})
