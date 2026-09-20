<template>
  <div>
    <div class="info-grid">
      <div class="info-card">
        <div class="info-card-head"><span class="info-card-icon c-blue"><el-icon><InfoFilled /></el-icon></span><span>基础信息</span></div>
        <div class="info-rows">
          <!-- 【2026-09-13】补展示：客户编号与开票名称此前只在列表有列，详情页看不到 -->
          <div class="info-row"><span class="lbl">客户编号</span><span class="val">{{ customer.customerCode || '—' }}</span></div>
          <div class="info-row"><span class="lbl">开票名称</span><span class="val">{{ customer.invoiceName || '—' }}</span></div>
          <div class="info-row"><span class="lbl">行业</span><span class="val">{{ customer.industry || '—' }}</span></div>
          <div class="info-row"><span class="lbl">开发类型</span><span class="val">{{ customer.devType || '—' }}</span></div>
          <div class="info-row"><span class="lbl">产能(万吨)</span><span class="val">{{ customer.capacity || '—' }}</span></div>
          <div class="info-row"><span class="lbl">终端</span><span class="val">{{ terminalLabel(customer.hasTerminal) }}</span></div>
        </div>
      </div>
      <div class="info-card">
        <div class="info-card-head"><span class="info-card-icon c-orange"><el-icon><MapLocation /></el-icon></span><span>归属信息</span></div>
        <div class="info-rows">
          <div class="info-row"><span class="lbl">所属大区</span><span class="val">{{ customer.regionName || '—' }}</span></div>
          <div class="info-row"><span class="lbl">所属办事处</span><span class="val">{{ customer.officeName || '—' }}</span></div>
          <!-- 【2026-09-13】所属部门：列表有「部门」列，详情此前没有（该部门由第一负责人部门自动确定） -->
          <div class="info-row"><span class="lbl">所属部门</span><span class="val">{{ customer.deptName || '—' }}</span></div>
          <div class="info-row"><span class="lbl">第一负责人</span><span class="val">{{ customer.ownerName || '—' }}</span></div>
          <div class="info-row"><span class="lbl">管理上级</span><span class="val">{{ customer.managerName || '—' }}</span></div>
        </div>
      </div>
      <div class="info-card">
        <div class="info-card-head"><span class="info-card-icon c-purple"><el-icon><OfficeBuilding /></el-icon></span><span>组织信息</span></div>
        <div class="info-rows">
          <div class="info-row"><span class="lbl">所属集团</span><span class="val">{{ customer.groupName || '—' }}</span></div>
          <div class="info-row"><span class="lbl">所属分公司</span><span class="val">{{ customer.branchCompany || '—' }}</span></div>
          <div class="info-row"><span class="lbl">所属单位</span><span class="val">{{ customer.unitName || '—' }}</span></div>
        </div>
      </div>
      <div class="info-card">
        <div class="info-card-head"><span class="info-card-icon c-teal"><el-icon><Location /></el-icon></span><span>地址信息</span></div>
        <div class="info-rows">
          <div class="info-row"><span class="lbl">省市县</span><span class="val">{{ customer.province }}{{ customer.city }}{{ customer.district || '—' }}</span></div>
          <div class="info-row"><span class="lbl">厂址</span><span class="val">{{ customer.address || '—' }}</span></div>
          <div class="info-row"><span class="lbl">保护期</span><span class="val"><span :class="'protect-badge badge-'+expireLevel">{{ statusLabel }}</span></span></div>
        </div>
      </div>
    </div>

    <div class="protect-bar">
      <div class="protect-dates">
        <div class="protect-date-item"><span class="pd-label">注册日</span><span class="pd-value">{{ customer.registerDate }}</span></div>
        <div class="protect-divider"><el-icon><ArrowRight /></el-icon></div>
        <div class="protect-date-item"><span class="pd-label">最近开票</span><span class="pd-value">{{ customer.lastInvoiceDate || '—' }}</span></div>
        <div class="protect-divider"><el-icon><ArrowRight /></el-icon></div>
        <div class="protect-date-item"><span class="pd-label">到期日 (360天)</span><span class="pd-value" :class="'exp-'+expireLevel">{{ customer.expireDate }}</span></div>
      </div>
      <div class="protect-actions">
        <el-button v-if="!readonly" size="small" type="warning" @click="handleExtend"><el-icon><Calendar /></el-icon> 申请延期</el-button>
        <!-- 【PR-09】客户转移: 提交审批, 通过后负责人/部门/转移日由后端落库(接收人校验/集团边界在审批执行侧) -->
        <el-button v-if="!readonly" size="small" type="primary" @click="openTransfer"><el-icon><Position /></el-icon> 转移客户</el-button>
      </div>
    </div>

    <!-- 【2026-09-13】客户备注：此前新增/编辑可填（库内 374/596 有值），但列表与详情都没有展示位 -->
    <div class="remark-card">
      <div class="remark-title"><el-icon><Document /></el-icon> 备注</div>
      <div class="remark-body">{{ customer.remark || '—' }}</div>
    </div>

    <!-- 【PR-09】客户转移弹窗: 接收人候选=可分享用户列表, 排除当前负责人 -->
    <el-dialog v-model="showTransferDialog" title="转移客户" width="420px" :close-on-click-modal="false">
      <el-form label-width="80px">
        <el-form-item label="客户">{{ customer.customerName }}</el-form-item>
        <el-form-item label="接收人">
          <el-select v-model="transferUserId" filterable placeholder="选择新负责人" style="width:100%">
            <el-option v-for="u in transferCandidates" :key="u.id" :label="u.realName || u.username" :value="u.id" />
          </el-select>
        </el-form-item>
        <div class="transfer-hint">提交后进入审批;审批通过后客户负责人与归属部门同步变更(保护期规则不变)。</div>
      </el-form>
      <template #footer><el-button @click="showTransferDialog=false">取消</el-button><el-button type="primary" @click="confirmTransfer">提交转移审批</el-button></template>
    </el-dialog>

    <div v-if="canViewContacts" class="contact-card">
      <div class="contact-head">
        <span class="contact-title"><el-icon><User /></el-icon> 联系人</span>
        <el-button v-if="!readonly" size="small" type="primary" @click="editingContact = null; Object.assign(contactForm, { name: '', position: '', phone: '', remark: '' }); showContactDialog = true"><el-icon><Plus /></el-icon> 添加</el-button>
      </div>
      <div v-if="contacts.length" class="contact-grid">
        <div v-for="c in contacts" :key="c.id" class="contact-item">
          <div class="contact-avatar">{{ c.name?.charAt(0) }}</div>
          <div class="contact-info">
            <div class="contact-name">{{ c.name }}</div>
            <div class="contact-sub">{{ c.position || '—' }} · {{ c.phone || '—' }}</div>
          </div>
          <div v-if="!readonly" class="contact-acts">
            <el-button link size="small" @click="openEditContact(c)"><el-icon><Edit /></el-icon></el-button>
            <el-button link size="small" type="danger" @click="handleDeleteContact(c)"><el-icon><Delete /></el-icon></el-button>
          </div>
        </div>
      </div>
      <div v-else class="empty-text">暂无联系人</div>
    </div>

    <div class="relation-card">
      <div class="relation-title"><el-icon color="#f56c6c"><StarFilled /></el-icon> 客情 · 商务关系深度</div>
      <div class="relation-options">
        <div v-for="opt in relationshipOptions" :key="opt.value"
          class="relation-opt" :class="{on: (indicator?.relationshipScore || 0) == opt.value}"
          @click="!readonly && selectRelationship(parseInt(opt.value))">
          <span class="rel-icon">{{ opt.icon }}</span><span>{{ opt.label }} · {{ opt.value }}分</span>
        </div>
      </div>
    </div>

    <el-dialog v-model="showContactDialog" :title="editingContact ? '编辑联系人' : '添加联系人'" width="440px" :close-on-click-modal="false">
      <el-form :model="contactForm" label-width="50px">
        <el-form-item label="姓名"><el-input v-model="contactForm.name" /></el-form-item>
        <el-form-item label="职位"><el-input v-model="contactForm.position" /></el-form-item>
        <el-form-item label="电话"><el-input v-model="contactForm.phone" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="contactForm.remark" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showContactDialog=false">取消</el-button><el-button type="primary" @click="handleSaveContact">确认</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { injectCustomerDetail } from '@/composables/useCustomerDetail'
import { terminalLabel } from '@/utils/customer-fields'

const {
  customer, readonly, contacts, editingContact, contactForm, showContactDialog, canViewContacts,
  openEditContact, handleSaveContact, handleDeleteContact, relationshipOptions,
  indicator, selectRelationship, expireLevel, statusLabel, handleExtend,
  openTransfer, confirmTransfer, showTransferDialog, transferUserId, allUsers,
} = injectCustomerDetail()

// 转移候选 = 可选用户列表排除当前负责人
const transferCandidates = computed(() =>
  (allUsers.value || []).filter((u) => !customer.value || String(u.id) !== String(customer.value.ownerId)))
</script>

<style scoped>
.protect-actions { display:flex; gap:8px; flex-shrink:0 }
.transfer-hint { color:#909399; font-size:12px; padding:2px 0 6px 80px }
.info-grid { display:grid; grid-template-columns:repeat(2,1fr); gap:14px; margin-bottom:16px }
.info-card { background:#fafbfc; border-radius:10px; padding:18px 20px; border:1px solid #f0f1f3 }
.info-card-head { display:flex; align-items:center; gap:8px; font-size:14px; font-weight:600; color:#333; margin-bottom:14px }
.info-card-icon { display:flex; align-items:center; justify-content:center; width:28px; height:28px; border-radius:7px; font-size:14px }
.c-blue { background:#ecf5ff; color:#409eff }.c-orange { background:#fdf6ec; color:#e6a23c }
.c-purple { background:#f5f0ff; color:#9c27b0 }.c-teal { background:#e6fffb; color:#13c2c2 }
.info-rows { display:flex; flex-direction:column; gap:0 }
.info-row { display:flex; padding:10px 0; border-bottom:1px solid #f5f5f5 }
.info-row:last-child { border-bottom:none }
.lbl { width:85px; color:#909399; font-size:13px; flex-shrink:0 }
.val { color:#1f2329; font-size:14px; font-weight:500 }
.protect-badge { font-size:12px; padding:2px 8px; border-radius:4px; font-weight:600 }
.badge-blue { color:#67c23a; background:#f0f9eb }.badge-orange { color:#e6a23c; background:#fdf6ec }.badge-red { color:#f56c6c; background:#fef0f0 }
.protect-bar { display:flex; align-items:center; justify-content:space-between; background:#fafbfc; border-radius:10px; padding:16px 24px; margin-bottom:16px; border:1px solid #f0f1f3 }
.protect-dates { display:flex; align-items:center; gap:20px }
.protect-date-item { text-align:center }.pd-label { font-size:12px; color:#909399 }.pd-value { font-size:16px; font-weight:600; color:#333 }
.pd-value.exp-red { color:#f56c6c }.pd-value.exp-orange { color:#e6a23c }
.protect-divider { color:#c0c4cc; font-size:18px }
.remark-card { background:#fafbfc; border-radius:10px; padding:16px 20px; margin-bottom:16px; border:1px solid #f0f1f3 }
.remark-title { display:flex; align-items:center; gap:6px; font-size:14px; font-weight:600; color:#333; margin-bottom:10px }
.remark-body { font-size:13px; color:#4e5969; white-space:pre-wrap; word-break:break-all; line-height:1.6 }
.contact-card { background:#fafbfc; border-radius:10px; padding:18px 20px; margin-bottom:16px; border:1px solid #f0f1f3 }
.contact-head { display:flex; justify-content:space-between; align-items:center; margin-bottom:14px }
.contact-title { display:flex; align-items:center; gap:6px; font-size:14px; font-weight:600; color:#333 }
.contact-grid { display:grid; grid-template-columns:repeat(2,1fr); gap:10px }
.contact-item { display:flex; align-items:center; gap:12px; padding:12px; background:#fff; border-radius:8px; border:1px solid #f0f1f3 }
.contact-avatar { width:36px; height:36px; border-radius:50%; background:#ecf5ff; color:#409eff; display:flex; align-items:center; justify-content:center; font-size:14px; font-weight:700 }
.contact-info { flex:1 }.contact-name { font-size:14px; font-weight:500; color:#333 }.contact-sub { font-size:12px; color:#909399; margin-top:2px }
.contact-acts { display:flex }
.relation-card { background:#fafbfc; border-radius:10px; padding:18px 20px; margin-bottom:16px; border:1px solid #f0f1f3 }
.relation-title { display:flex; align-items:center; gap:6px; font-size:14px; font-weight:600; color:#333; margin-bottom:12px }
.relation-options { display:grid; grid-template-columns:repeat(4,1fr); gap:10px }
.relation-opt { display:flex; align-items:center; justify-content:center; gap:6px; padding:14px 8px; border:2px solid #e8e8e8; border-radius:8px; cursor:pointer; transition:all .15s; font-size:13px; color:#666; background:#fff }
.relation-opt:hover { border-color:#409eff; color:#409eff }
.relation-opt.on { border-color:#409eff; background:#ecf5ff; color:#409eff; font-weight:600 }
.empty-text { color:#c0c4cc; text-align:center; padding:24px 0; font-size:13px }
</style>
