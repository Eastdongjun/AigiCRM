import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { ref } from 'vue'
import ElementPlus from 'element-plus'
import InfoTab from './InfoTab.vue'
import { CustomerDetailKey } from '@/composables/useCustomerDetail'

/**
 * 客户详情「基础信息 / 归属信息」展示字段断言（2026-09-13）
 * 背景：客户编号、开票名称、所属部门此前只在客户列表有列，详情页看不到；
 * 这里把"详情必须展示这三项"固化成回归断言，避免再次漏展示。
 */
function mountInfoTab(customer) {
  return mount(InfoTab, {
    global: {
      plugins: [ElementPlus],
      provide: {
        [CustomerDetailKey]: {
          customer: ref(customer),
          readonly: ref(false),
          contacts: ref([]),
          editingContact: ref(null),
          contactForm: ref({ name: '', position: '', phone: '', remark: '' }),
          showContactDialog: ref(false),
          canViewContacts: ref(false),
          openEditContact: () => {},
          handleSaveContact: () => {},
          handleDeleteContact: () => {},
          relationshipOptions: ref([]),
          indicator: ref(null),
          selectRelationship: () => {},
          expireLevel: ref('blue'),
          statusLabel: ref('正常'),
          handleExtend: () => {},
        },
      },
    },
  })
}

describe('客户详情 InfoTab 展示字段', () => {
  it('基础信息展示客户编号与开票名称', () => {
    const wrapper = mountInfoTab({
      customerCode: 'NM-202609-001',
      invoiceName: '陕煤榆林化学有限公司',
      industry: '煤矿',
      devType: '成熟创新',
      capacity: '500',
      hasTerminal: 1,
    })
    const text = wrapper.text()
    expect(text).toContain('客户编号')
    expect(text).toContain('NM-202609-001')
    expect(text).toContain('开票名称')
    expect(text).toContain('陕煤榆林化学有限公司')
  })

  it('归属信息展示所属部门（列表有「部门」列，详情也必须有）', () => {
    const wrapper = mountInfoTab({
      regionName: '内蒙古大区',
      officeName: '内蒙办',
      deptName: '内蒙办销售部',
      ownerName: '王磊磊',
      managerName: '张上级',
    })
    const text = wrapper.text()
    expect(text).toContain('所属部门')
    expect(text).toContain('内蒙办销售部')
  })

  it('三项字段为空时回退占位符「—」而不是显示空白', () => {
    const wrapper = mountInfoTab({
      customerCode: null, invoiceName: null, deptName: null,
      industry: null, devType: null, capacity: null, hasTerminal: null,
    })
    const text = wrapper.text()
    expect(text).toContain('客户编号')
    expect(text).toContain('开票名称')
    expect(text).toContain('所属部门')
    expect(text).toContain('—')
  })
})
