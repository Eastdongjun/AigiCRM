const incentiveRecords = [
  { id: 1, name: '山西晋能集团中阳煤业', industry: '煤矿', score: 45, isNew: true, unread: true, summary: '晋能集团高层拜访，达成120万吨供煤合作意向', fileId: '[101,102]', fileName: '现场拜访照片.jpg,合作纪要.pdf' },
  { id: 2, name: '内蒙古鄂尔多斯化工', industry: '化工', score: 32, isNew: true, unread: false, summary: '煤机设备试运行成功，产能提升15%', filePreId: '[201]', filePreName: '安装前现场照片.jpg', filePostId: '[202]', filePostName: '试运行报告.pdf' },
  { id: 3, name: '大同云冈电厂', industry: '电力', score: 18, isNew: false, unread: true, summary: '完成现场技术交流', fileTrackId: '[301]', fileTrackName: '跟踪回访记录.docx' }
]

const homeCards = [
  ['128', '我的客户'],
  ['86', '在保护期'],
  ['8', '即将到期'],
  ['5', '已超期'],
  ['2', '今日到期']
]

const approvalRecords = [
  { id: 1, customerId: 1, customerName: '山西晋能集团中阳煤业', customerIndustry: '煤矿', title: '山西晋能客户转交', type: 'customer_transfer', status: 'pending', applicant: '张三', createdAt: '2026-09-05 10:30', reason: '跨区域协同跟进，需要审批转交。' },
  { id: 2, customerId: 2, customerName: '内蒙古鄂尔多斯化工', customerIndustry: '化工', title: '鄂尔多斯化工退回公海', type: 'return_to_pool', status: 'approved', applicant: '李四', createdAt: '2026-09-04 16:12', reason: '客户阶段停滞，已完成审批。' }
]

const seaRecords = [
  { id: 1, customerId: 1, customerName: '山西晋能集团中阳煤业', customerCode: 'C-20260901', industry: '煤矿', region: '山西', ownerName: '王五', enterReason: '保护期满', enterDate: '2026-09-01', status: 'available', canClaim: true, canEdit: false, canDelete: false },
  { id: 2, customerId: 2, customerName: '内蒙古鄂尔多斯化工', customerCode: 'C-20260902', industry: '化工', region: '内蒙古', ownerName: '赵六', enterReason: '放弃客户', enterDate: '2026-09-02', status: 'pending_approval', canClaim: false, canEdit: false, canDelete: false }
]

const reminderRecords = [
  { id: 1, customerId: 1, customerName: '山西晋能集团中阳煤业', customerCode: 'C-20260901', customerStatus: 'protected', industry: '煤矿', region: '山西', type: 'expire', status: 'pending', expireDate: '2026-09-08', remainingDays: 7, lastInvoiceDate: '2026-08-01', content: '客户保护期即将到期，请及时跟进。' },
  { id: 2, customerId: 2, customerName: '内蒙古鄂尔多斯化工', customerCode: 'C-20260902', customerStatus: 'shared', industry: '化工', region: '内蒙古', type: 'share', status: 'pending', expireDate: '', remainingDays: null, lastInvoiceDate: '', content: '同事分享了客户资料，请查看。' }
]

module.exports = {
  incentiveRecords,
  homeCards,
  approvalRecords,
  seaRecords,
  reminderRecords
}
