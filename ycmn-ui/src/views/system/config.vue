<template>
  <div class="config-page">

    <div class="section-box">
      <el-tabs v-model="activeTab">
        <!-- 基本设置 -->
        <el-tab-pane label="基本设置" name="basic">
          <el-form label-width="120px" class="basic-form">
            <el-form-item label="系统名称">
              <el-input v-model="configMap['system.name']" style="width: 300px" />
            </el-form-item>
            <el-form-item label="系统副标题">
              <el-input v-model="configMap['system.subtitle']" style="width: 300px" placeholder="如：市场资源管理系统" />
            </el-form-item>
            <el-form-item label="系统Logo">
              <div style="display: flex; align-items: center; gap: 12px">
                <img v-if="configMap['system.logo']" :src="logoPreviewSrc" class="logo-preview" />
                <el-upload
                  :action="'/api/file/upload'"
                  :headers="{ Authorization: 'Bearer ' + token }"
                  :data="{ folderId: 0, scope: 'public' }"
                  :show-file-list="false"
                  accept="image/*"
                  :on-success="handleLogoUpload"
                >
                  <el-button type="primary">上传Logo</el-button>
                </el-upload>
                <el-button v-if="configMap['system.logo']" type="danger" link @click="configMap['system.logo'] = ''">清除</el-button>
              </div>
            </el-form-item>
            <el-form-item label="客户保护期">
              <el-input v-model="configMap['customer.protection.days']" style="width: 300px">
                <template #suffix>天</template>
              </el-input>
            </el-form-item>
            <el-form-item label="提醒提前">
              <el-input v-model="configMap['customer.warning.days']" style="width: 300px">
                <template #suffix>天</template>
              </el-input>
            </el-form-item>
            <el-form-item label="调整期天数">
              <el-input v-model="configMap['customer.adjust.days']" style="width: 300px">
                <template #suffix>天</template>
              </el-input>
            </el-form-item>
            <el-form-item label="开票超期天数">
              <el-input v-model="configMap['invoice.overdue.days']" style="width: 300px">
                <template #suffix>天</template>
              </el-input>
            </el-form-item>
            <el-form-item label="默认存储空间">
              <el-input :model-value="storageGb" @update:model-value="onStorageGb" style="width: 300px">
                <template #suffix>GB</template>
              </el-input>
            </el-form-item>
            <el-form-item label="编号规则">
              <el-select v-model="configMap['customer.code.rule']" style="width: 300px">
                <el-option label="大区首字母-年月-顺序号 (如 NM-202606-001)" value="region_date_seq" />
                <el-option label="大区首字母-顺序号 (如 NM-001)" value="region_seq" />
              </el-select>
            </el-form-item>
            <el-divider content-position="left">客户默认共享规则</el-divider>
            <el-form-item label="直接上级">
              <el-switch v-model="autoShare.superior" active-text="上级可见下级客户" />
            </el-form-item>
            <el-form-item label="管理员">
              <el-switch v-model="autoShare.admin" active-text="管理员可见全部客户" />
            </el-form-item>
            <el-form-item label="指定角色">
              <el-select v-model="autoShare.roles" multiple placeholder="选择角色（可多选）" style="width: 100%">
                <el-option v-for="r in allRoles" :key="r.roleCode" :label="r.roleName" :value="r.roleCode" />
              </el-select>
            </el-form-item>
            <el-form-item label="指定人员">
              <el-select v-model="autoShare.users" multiple filterable placeholder="选择人员（可多选）" style="width: 100%">
                <el-option v-for="u in allUsers" :key="u.id" :label="u.realName" :value="u.id" />
              </el-select>
            </el-form-item>
            <el-divider content-position="left">系统级数据范围</el-divider>
            <el-form-item label="上级看下属">
              <el-switch v-model="configMap['perm.superior.see.subordinate']"
                active-value="true" inactive-value="false"
                active-text="直接上级默认可见递归下属链数据（最多5层）" />
            </el-form-item>
            <el-form-item label="组织负责人">
              <el-switch v-model="configMap['perm.org.leader.see.org']"
                active-value="true" inactive-value="false"
                active-text="组织负责人默认可见本组织整棵子树全部数据" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleSave">保存设置</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- 字典管理 -->
        <el-tab-pane label="字典管理" name="dict">
          <div class="dict-layout">
            <!-- 左侧: 字典类型 -->
            <div class="dict-types">
              <div class="dict-header">
                <span><el-icon style="vertical-align:middle"><Files /></el-icon> 字典类型 <el-tag size="small" type="info" effect="plain">{{ dictTypes.length }}</el-tag></span>
                <el-button type="primary" size="small" :icon="Plus" @click="handleAddType">新增</el-button>
              </div>
              <div class="dict-type-list">
                <div
                  v-for="t in dictTypes"
                  :key="t.id"
                  class="dict-type-item"
                  :class="{ active: currentType && currentType.id === t.id }"
                  @click="handleTypeSelect(t)"
                >
                  <div class="dict-type-name">
                    <el-icon class="dict-type-ico"><Collection /></el-icon>
                    <span class="dict-type-title">{{ t.dictName }}</span>
                    <el-tag v-if="t.builtIn" size="small" type="info" effect="plain">内置</el-tag>
                  </div>
                  <div class="dict-type-code">{{ t.dictCode }}</div>
                  <div class="dict-type-acts">
                    <el-button link size="small" @click.stop="handleEditType(t)">编辑</el-button>
                    <el-button v-if="!t.builtIn" link size="small" type="danger" @click.stop="handleDeleteType(t)">删除</el-button>
                  </div>
                </div>
                <el-empty v-if="!dictTypes.length" description="暂无字典类型" :image-size="60" />
              </div>
            </div>

            <!-- 右侧: 字典数据 -->
            <div class="dict-data">
              <div class="dict-header">
                <span>
                  <el-icon style="vertical-align:middle"><PriceTag /></el-icon>
                  {{ currentType ? currentType.dictName + ' · 数据列表' : '请选择字典类型' }}
                  <el-tag v-if="currentType" size="small" type="success" effect="plain">{{ dictDataList.length }} 项</el-tag>
                </span>
                <el-button type="primary" size="small" :icon="Plus" :disabled="!currentType" @click="handleAddData">新增</el-button>
              </div>
              <el-table v-if="currentType" :data="dictDataList" size="small" stripe style="width: 100%">
                <el-table-column prop="dictLabel" label="显示标签" min-width="120" />
                <el-table-column prop="dictValue" label="数据值" min-width="120" />
                <el-table-column prop="sortOrder" label="排序" width="60" align="center" />
                <el-table-column label="状态" width="70" align="center">
                  <template #default="{ row }">
                    <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="100" align="center">
                  <template #default="{ row }">
                    <el-button link size="small" @click="handleEditData(row)">编辑</el-button>
                    <el-button link size="small" type="danger" @click="handleDeleteData(row)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <div v-else class="dict-empty-tip">
                <el-empty description="从左侧选择字典类型查看数据" :image-size="90" />
              </div>
            </div>
          </div>
        </el-tab-pane>
        <!-- 公海配置 -->
        <el-tab-pane label="审批配置" name="approvalConfig"><ApprovalConfig /></el-tab-pane>
        <el-tab-pane label="公海配置" name="sea">
          <div style="display:flex;align-items:center;gap:12px;margin-bottom:16px">
            <span style="font-size:15px;font-weight:600">公海池列表</span>
            <el-tag size="small" type="info">{{ seaPools.length }} 个池</el-tag>
            <el-button type="primary" size="small" :icon="Plus" @click="handleAddPool">新增公海池</el-button>
          </div>
          <el-row :gutter="16">
            <el-col v-for="pool in seaPools" :key="pool.id" :span="8" style="margin-bottom:16px">
              <el-card shadow="hover" :body-style="{ padding: '16px' }">
                <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:10px">
                  <span style="font-weight:600;font-size:14px">{{ pool.poolName }}</span>
                  <el-tag :type="pool.status===1?'success':'info'" size="small">{{ pool.status===1?'启用':'禁用' }}</el-tag>
                </div>
                <div style="color:#909399;font-size:12px;line-height:1.8">
                  <div><el-icon style="vertical-align:middle"><OfficeBuilding /></el-icon> {{ pool.regionNames || '未设置' }}</div>
                  <div><el-icon style="vertical-align:middle"><View /></el-icon> {{ pool.visibleNames || '未设置' }}</div>
                  <div><el-icon style="vertical-align:middle"><Sort /></el-icon> 排序: {{ pool.sortOrder }}</div>
                </div>
                <div style="margin-top:12px;display:flex;gap:8px">
                  <el-button size="small" @click="handleEditPool(pool)">编辑</el-button>
                  <el-button size="small" type="danger" @click="handleDeletePool(pool)">删除</el-button>
                </div>
              </el-card>
            </el-col>
          </el-row>
          <el-empty v-if="seaLoadError" description="无权限查看公海配置（缺少 sea:config:view 权限）或加载失败" />
          <el-empty v-else-if="!seaPools.length" description="暂无公海池，点击上方按钮创建" />
        </el-tab-pane>
        <!-- 激励类别与权限 -->
        <el-tab-pane label="激励类别与权限" name="incentiveCategory">
          <!-- 激励类别管理：一级卡片 + 二级子项 -->
          <div class="incentive-card">
            <div class="dict-header">
              <span>激励类别管理</span>
              <el-button type="primary" size="small" :icon="Plus" @click="openCategoryDialog()">新增类别</el-button>
            </div>
            <div v-if="catLoadError" class="cat-empty" style="color:#f56c6c">无权限查看激励类别（缺少 incentive:view 权限）或加载失败</div>
            <div v-else-if="!categoryTree.length" class="cat-empty">暂无类别，点击"新增类别"创建</div>
            <div v-for="top in categoryTree" :key="top.id" class="cat-group">
              <div class="cat-group-head" @click="toggleCategory(top.id)">
                <span class="cat-group-name">{{ top.categoryName }}</span>
                <span class="cat-group-code">{{ top.categoryCode }}</span>
                <el-tag :type="top.status === 1 ? 'success' : 'danger'" size="small">{{ top.status === 1 ? '启用' : '禁用' }}</el-tag>
                <span class="cat-group-count">{{ (top.children || []).length }} 个子项</span>
                <span class="cat-arrow" :class="{ expanded: !collapsed.has(top.id) }">▾</span>
                <div class="cat-group-actions" @click.stop>
                  <el-button link type="primary" size="small" @click="openCategoryDialog(top)">编辑</el-button>
                  <el-button link type="primary" size="small" @click="openCategoryDialog(null, top.id)">子项</el-button>
                  <el-button link type="danger" size="small" @click="handleDeleteCategory(top)">删除</el-button>
                </div>
              </div>
              <div v-show="!collapsed.has(top.id)" class="cat-group-body">
                <el-table v-if="top.children && top.children.length" :data="top.children" stripe size="small" style="width:100%">
                  <el-table-column prop="categoryName" label="类别名称" min-width="160" />
                  <el-table-column prop="categoryCode" label="编码" width="130" />
                  <el-table-column label="分值" width="60" align="center">
                    <template #default="{ row }">
                      <span v-if="row.score > 0">{{ row.score }}分</span>
                      <span v-else style="color:#c0c4cc">—</span>
                    </template>
                  </el-table-column>
                  <el-table-column prop="sortOrder" label="排序" width="60" align="center" />
                  <el-table-column label="状态" width="70" align="center">
                    <template #default="{ row }">
                      <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column label="操作" width="130" align="center">
                    <template #default="{ row }">
                      <div class="action-btns">
                        <el-button link type="primary" size="small" @click="openCategoryDialog(row)">编辑</el-button>
                        <el-button link type="danger" size="small" @click="handleDeleteCategory(row)">删除</el-button>
                      </div>
                    </template>
                  </el-table-column>
                </el-table>
                <div v-else class="cat-no-child">暂无子项</div>
              </div>
            </div>
          </div>

          <!-- 四项激励权限 -->
          <div class="incentive-card">
            <div class="dict-header">
              <span>【激励专项权限】授权人员 — 独立于菜单权限，按人控制编辑/删除能力</span>
              <el-button type="primary" size="small" :icon="Plus" @click="showPermDialog=true">添加</el-button>
            </div>
            <el-table :data="permList" size="small" style="width:100%">
            <el-table-column type="index" label="序号" width="55" align="center" />
            <el-table-column prop="username" label="账号" min-width="90" />
            <el-table-column prop="realName" label="姓名" min-width="80" />
            <el-table-column prop="officeName" label="大区" min-width="120" show-overflow-tooltip />
            <el-table-column prop="roleName" label="岗位" min-width="80" />
            <el-table-column label="编辑" width="60" align="center">
              <template #default="{row}"><el-tag :type="row.can_edit?'success':'info'" size="small">{{row.can_edit?'是':'否'}}</el-tag></template>
            </el-table-column>
            <el-table-column label="删除" width="55" align="center">
              <template #default="{row}"><el-tag :type="row.can_delete?'success':'info'" size="small">{{row.can_delete?'是':'否'}}</el-tag></template>
            </el-table-column>
            <el-table-column label="操作" width="65" align="center">
              <template #default="{row}"><el-button link type="danger" size="small" @click="revokePerm(row.id)">撤销</el-button></template>
            </el-table-column>
          </el-table>
          </div>
        </el-tab-pane>

        <!-- 权限体系总览 -->
        <el-tab-pane label="权限体系" name="permOverview">
          <el-alert type="info" :closable="false" style="margin-bottom:16px">
            <template #title>系统包含5种权限：菜单权限（能进哪个页面）、数据权限（能看到多少数据）、操作权限（能改/删数据）、组织管理权限（能管哪个组织的人）、激励专项权限（能编辑/删除激励）</template>
          </el-alert>
          
          <el-row :gutter="16">
            <el-col :span="12" v-for="item in permOverview" :key="item.title" style="margin-bottom:16px">
              <el-card shadow="hover">
                <template #header>
                  <div style="display:flex;justify-content:space-between;align-items:center">
                    <span style="font-weight:600">{{ item.icon }} {{ item.title }}</span>
                    <el-tag :type="item.tagType" size="small">{{ item.tag }}</el-tag>
                  </div>
                </template>
                <div style="font-size:13px;color:#606266;line-height:1.8;min-height:80px">{{ item.desc }}</div>
                <div style="margin-top:12px">
                  <span style="font-size:12px;color:#909399">配置入口：</span>
                  <el-button v-for="btn in item.buttons" :key="btn.label" size="small" :type="btn.type || 'primary'" link @click="btn.tab ? activeTab=btn.tab : router.push(btn.href)">{{ btn.label }}</el-button>
                </div>
              </el-card>
            </el-col>
          </el-row>
        </el-tab-pane>

        <!-- 权限矩阵 -->
        <el-tab-pane label="权限矩阵" name="perm">
          <div style="display:flex;align-items:center;gap:8px;margin-bottom:12px">
            <span style="font-size:13px;color:#666">【菜单权限】按角色分配：✓ 表示该角色能访问此页面/功能</span>
            <el-button size="small" @click="loadPermData">刷新</el-button>
          </div>
          <el-empty v-if="permLoadError" description="无权限查看权限矩阵（缺少 system:menu:view / system:role:view 权限）或加载失败" />
          <div v-else class="perm-table-wrap">
            <table class="perm-table">
              <thead>
                <tr>
                  <th class="perm-module">模块</th>
                  <th class="perm-item-col">权限项</th>
                  <th v-for="r in allRoles" :key="r.id" class="perm-role-col">{{ r.roleName }}</th>
                </tr>
              </thead>
              <tbody>
                <template v-for="mod in permModules" :key="mod.name">
                  <tr v-for="(item, idx) in mod.items" :key="item.id" :class="{ 'first-in-group': idx === 0 }">
                    <td v-if="idx === 0" :rowspan="mod.items.length" class="perm-module perm-module-name">{{ mod.name }}</td>
                    <td class="perm-item-col">{{ item.label }}</td>
                    <td v-for="r in allRoles" :key="r.id" class="perm-role-col" @click="togglePerm(r, item)">
                      <span v-if="hasPerm(r, item)" class="perm-yes">✓</span>
                      <span v-else class="perm-no">—</span>
                    </td>
                  </tr>
                </template>
              </tbody>
            </table>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- 四项激励权限弹窗 -->
    <el-dialog v-model="showPermDialog" title="添加权限" width="420px">
      <el-form label-width="70px">
        <el-form-item label="选择人员">
          <el-select v-model="permForm.userId" placeholder="请选择" filterable style="width:100%">
            <el-option v-for="u in allUsers" :key="u.id" :label="u.realName+' ('+u.username+')'" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="编辑权限"><el-switch v-model="permForm.canEdit" /></el-form-item>
        <el-form-item label="删除权限"><el-switch v-model="permForm.canDelete" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showPermDialog=false">取消</el-button>
        <el-button type="primary" @click="grantPerm">确定</el-button>
      </template>
    </el-dialog>

    <!-- 公海池编辑弹窗 -->
    <el-dialog v-model="poolDialogVisible" :title="poolForm.id ? '编辑公海池' : '新增公海池'" width="700px" destroy-on-close top="5vh">
      <el-form :model="poolForm" label-width="100px" label-position="right">
        <el-form-item label="公海池名称" required>
          <el-input v-model="poolForm.poolName" placeholder="如：内蒙办公海" maxlength="30" show-word-limit />
        </el-form-item>

        <el-divider content-position="left">
          <el-icon><View /></el-icon> 可见范围
          <el-tag size="small" type="warning" style="margin-left:8px">{{ poolForm.visibleKeys.length }} 项已选</el-tag>
        </el-divider>

        <el-row :gutter="16" style="margin-bottom:8px">
          <el-col :span="14">
            <div class="tree-card">
              <div class="tree-card-header">
                <el-icon><OfficeBuilding /></el-icon> 组织架构
              </div>
              <el-tree
                ref="seaTreeRef"
                :data="orgTree"
                show-checkbox
                node-key="id"
                :props="{ label: 'label', children: 'children', disabled: 'disabled' }"
                default-expand-all
                check-strictly
                @check="onSeaTreeCheck"
                style="max-height:360px;overflow:auto;padding:8px"
              />
              <div class="tree-card-tip">总部不可选；大区/办事处/部门/人员均可选</div>
            </div>
          </el-col>
          <el-col :span="10">
            <div class="tree-card">
              <div class="tree-card-header">
                <el-icon><Select /></el-icon> 已选范围
                <el-button link size="small" @click="clearSeaSelection" style="margin-left:auto">清空</el-button>
              </div>
              <div style="max-height:360px;overflow:auto;padding:8px">
                <el-tag
                  v-for="item in selectedSeaNodes"
                  :key="item.key"
                  :type="item.type"
                  closable
                  size="small"
                  @close="removeSeaNode(item)"
                  style="margin:2px;display:block;text-align:left"
                >
                  <el-icon style="vertical-align:middle"><component :is="item.key.startsWith('d_') ? 'OfficeBuilding' : 'User'" /></el-icon>
                  {{ item.label }}
                </el-tag>
                <el-empty v-if="!selectedSeaNodes.length" description="未选择" :image-size="40" />
              </div>
            </div>
          </el-col>
        </el-row>

        <el-divider content-position="left"><el-icon><Setting /></el-icon> 其他设置</el-divider>
        <el-form-item label="排序">
          <el-input-number v-model="poolForm.sortOrder" :min="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="poolDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitPool">确定</el-button>
      </template>
    </el-dialog>

    <!-- 字典类型编辑弹窗 -->
    <el-dialog v-model="typeDialogVisible" :title="typeForm.id ? '编辑字典类型' : '新增字典类型'" width="420px">
      <el-form :model="typeForm" label-width="80px">
        <el-form-item label="名称">
          <el-input v-model="typeForm.dictName" placeholder="如：行业类型" />
        </el-form-item>
        <el-form-item label="编码">
          <el-input v-model="typeForm.dictCode" placeholder="如：industry" :disabled="!!typeForm.id" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="typeForm.remark" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="typeDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitType">确定</el-button>
      </template>
    </el-dialog>

    <!-- 字典数据编辑弹窗 -->
    <el-dialog v-model="dataDialogVisible" :title="dataForm.id ? '编辑字典数据' : '新增字典数据'" width="420px">
      <el-form :model="dataForm" label-width="80px">
        <el-form-item label="标签">
          <el-input v-model="dataForm.dictLabel" placeholder="显示名称" />
        </el-form-item>
        <el-form-item label="数据值">
          <el-input v-model="dataForm.dictValue" placeholder="实际存储值" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="dataForm.sortOrder" :min="0" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="dataForm.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dataDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitData">确定</el-button>
      </template>
    </el-dialog>
    <!-- 激励类别编辑弹窗 -->
    <el-dialog v-model="categoryDialogVisible" :title="categoryForm.id ? '编辑类别' : '新增类别'" width="500px">
      <el-form :model="categoryForm" label-width="80px">
        <el-form-item label="上级类别">
          <el-tree-select
            v-model="categoryForm.parentId"
            :data="categoryParentOptions"
            :props="{ label: 'categoryName', value: 'id', children: 'children' }"
            check-strictly
            placeholder="无（作为顶级类别）"
            style="width:100%"
            clearable
          />
        </el-form-item>
        <el-form-item label="类别名称">
          <el-input v-model="categoryForm.categoryName" placeholder="如：外出参观、涂层类" />
        </el-form-item>
        <el-form-item label="类别编码">
          <el-input v-model="categoryForm.categoryCode" placeholder="如：visit、trial_coating" />
        </el-form-item>
        <el-form-item label="分值">
          <el-input-number v-model="categoryForm.score" :min="0" :max="100" />
          <span style="color:#999;font-size:12px;margin-left:8px">顶级类别分值为0，子项设置实际分值</span>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="categoryForm.sortOrder" :min="0" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="categoryForm.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="禁用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="categoryDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCategory">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import ApprovalConfig from '@/views/system/approvalConfig.vue'
import { ref, reactive, computed, onMounted, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Files, Collection, PriceTag } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import {
  getConfigList, updateConfigs,
  getDictTypes, createDictType, updateDictType, deleteDictType,
  getDictDataByTypeId, createDictData, updateDictData, deleteDictData,
  getRoleList, getMenuTree, updateRole
} from '@/api/system'
import { getSeaConfigs, createSeaConfig, updateSeaConfig, deleteSeaConfig, getOrgTree } from '@/api/sea'
import { getCategoryTree, createCategory, updateCategory, deleteCategory } from '@/api/incentive'
import request from '@/utils/request'

const userStore = useUserStore()
const activeTab = ref('basic')

// ===== 权限矩阵 =====

const permOverview = [
  {
    icon: '📋', title: '菜单权限',
    tag: '角色管理 → 权限',
    tagType: 'primary',
    desc: '控制用户能看到哪些页面和功能。分配时勾选菜单树即可，共 39 个权限点。',
    buttons: [{ label: '前往配置 →', tab: 'perm' }]
  },
  {
    icon: '📊', title: '数据权限',
    tag: '角色管理 → 编辑',
    tagType: 'warning',
    desc: '控制用户能看到多少条数据。7级：全部 → 本组织及下级 → 仅本组织 → 本部门及下级 → 仅本部门 → 仅本人。',
    buttons: [{ label: '前往配置 →', tab: 'perm' }]
  },
  {
    icon: '✏️', title: '操作权限',
    tag: '系统内置',
    tagType: 'success',
    desc: '内置规则：每个人只能编辑/删除自己的客户和激励记录，管理员例外。无需手动配置。',
    buttons: []
  },
  {
    icon: '🏢', title: '组织管理权限',
    tag: '组织管理 → 管理',
    tagType: 'danger',
    desc: '指定组织的管理员，能管理该组织及下级的所有用户。当前已配置 1 个组织管理员。',
    buttons: [{ label: '前往配置 →', type: 'warning', href: '/system/org' }]
  },
  {
    icon: '⭐', title: '激励专项权限',
    tag: '激励类别与权限',
    tagType: 'warning',
    desc: '独立于菜单权限，按人配置能否编辑/删除激励记录。有权限的人能看全部数据。',
    buttons: [{ label: '前往配置 →', type: 'warning', tab: 'incentiveCategory' }]
  }
]
const allRoles = ref([])
const menuTree = ref([])
const rolePermMap = ref({}) // roleId -> Set of menuIds

const permModules = computed(() => {
  const groups = [
    { name: '工作台', items: [] },
    { name: '客户管理', items: [] },
    { name: '文件管理', items: [] },
    { name: '四项激励', items: [] },
    { name: '提醒管理', items: [] },
    { name: '公海管理', items: [] },
    { name: '审批管理', items: [] },
    { name: '统计分析', items: [] },
    { name: '学分管理', items: [] },
    { name: '核心客户指标', items: [] },
    { name: '系统管理', items: [] },
  ]
  const labelMap = {
    'dashboard': '工作台', 'customer': '客户管理', 'file': '文件管理', 'incentive': '四项激励',
    'reminder': '提醒管理', 'sea': '公海管理', 'approval': '审批管理', 'statistics': '统计分析',
    'score': '学分管理', 'indicator': '核心客户指标', 'system': '系统管理',
  }
  const flatten = (nodes) => {
    for (const n of nodes) {
      if (n.permission) {
        const prefix = n.permission.split(':')[0]
        const group = groups.find(g => {
          for (const [k, v] of Object.entries(labelMap)) {
            if (prefix === k) return g.name === v
          }
          return false
        })
        const target = group || groups[groups.length - 1]
        target.items.push({ id: n.id, label: n.menuName, permission: n.permission })
      }
      if (n.children) flatten(n.children)
    }
  }
  flatten(menuTree.value)
  return groups.filter(g => g.items.length > 0)
})

const hasPerm = (role, item) => {
  const set = rolePermMap.value[role.id]
  return set ? set.has(item.id) : false
}

const togglePerm = async (role, item) => {
  const set = rolePermMap.value[role.id]
  if (!set) return
  const had = set.has(item.id)
  if (had) {
    set.delete(item.id)
  } else {
    set.add(item.id)
  }
  try {
    await updateRole(role.id, { menuIds: [...set] })
  } catch (e) {
    // 保存失败:回滚本地勾选状态,避免"界面已改、数据库没变"的假成功
    if (had) set.add(item.id); else set.delete(item.id)
    ElMessage.error('权限保存失败,已还原勾选')
  }
}

const loadPermData = async () => {
  permLoadError.value = false
  try { const res = await getRoleList(); allRoles.value = res.data || [] } catch { permLoadError.value = true }
  try { const res = await getMenuTree(); menuTree.value = res.data || [] } catch { permLoadError.value = true }
  // 加载每个角色的菜单权限
  for (const role of allRoles.value) {
    try {
      const r = await request.get(`/system/roles/${role.id}`)
      rolePermMap.value[role.id] = new Set(r.data?.menuIds || [])
    } catch {}
  }
}

// 四项激励权限
const permList = ref([])
const showPermDialog = ref(false)
const permForm = reactive({ userId: null, canEdit: true, canDelete: true })
const allUsers = ref([])
const loadPermList = async () => {
  try { const res = await request.get('/incentive/permission/list'); permList.value = res.data || [] } catch(e) {}
}
const loadAllUsers = async () => {
  try { const res = await request.get('/system/users', { params: { current:1, size:500 }}); allUsers.value = res.data?.records || [] } catch(e) {}
}
const grantPerm = async () => {
  if (!permForm.userId) { ElMessage.warning('请选择人员'); return }
  try {
    await request.post('/incentive/permission/grant', permForm)
    ElMessage.success('授权成功'); showPermDialog.value = false; loadPermList()
  } catch(e) { ElMessage.error('授权失败') }
}
const revokePerm = async (id) => {
  try { await ElMessageBox.confirm('确认撤销该权限？','提示',{type:'warning'}); await request.delete('/incentive/permission/'+id); ElMessage.success('已撤销'); loadPermList() } catch(e) {}
}
const token = localStorage.getItem('token')

// ===== 基本设置 =====
const configs = ref([])
const router = useRouter()
const configMap = reactive({})
const autoShare = reactive({ superior: true, admin: true, roles: [], users: [] })
// Tab 加载失败标志:区分"无权限/加载失败"与"暂无数据",避免静默空白
const permLoadError = ref(false)
const seaLoadError = ref(false)
const catLoadError = ref(false)
const loadConfigs = async () => {
  try {
    const res = await getConfigList()
    configs.value = res.data || []
    configs.value.forEach(c => { configMap[c.configKey] = c.configValue })
    // 系统级数据范围开关:DB 无行时默认开启(与后端 getBooleanValue 缺省 true 一致),首存即持久化
    configMap['perm.superior.see.subordinate'] = configMap['perm.superior.see.subordinate'] ?? 'true'
    configMap['perm.org.leader.see.org'] = configMap['perm.org.leader.see.org'] ?? 'true'
    // 客户默认共享规则:从 customer.auto_share(JSON 字符串)回填到 autoShare
    const asRaw = configMap['customer.auto_share']
    if (asRaw) {
      try {
        const parsed = JSON.parse(asRaw)
        autoShare.superior = !!parsed.superior
        autoShare.admin = !!parsed.admin
        autoShare.roles = parsed.roles || []
        autoShare.users = parsed.users || []
      } catch (e) { /* 历史脏值忽略,使用默认 */ }
    }
  } catch (e) {}
}

// Logo 预览统一走匿名可读的代理端点:配置值为路径式(如 /api/file/download/public/1/logo.jpg)
// 或 by-id 格式时,后端 /api/system/configs/logo 会解析 fileKey 并读磁盘返回,避免 404/403
// logoVersion 在保存完成后自增,强制 img 重新加载(上传时 configMap 先更新而 DB 仍是旧值,存在竞态)
const logoVersion = ref(0)
const logoPreviewSrc = computed(() => configMap['system.logo'] ? '/api/system/configs/logo?t=' + Date.now() + '-' + logoVersion.value : '')

const handleLogoUpload = async (res) => {
  if (res.code === 200 && res.data) {
    configMap['system.logo'] = res.data.ossUrl || res.data.url || ''
    ElMessage.success('Logo上传成功，正在保存...')
    // REV-20260802-02: 保存失败时回滚预览并提示, 不再无条件自增版本号
    const saved = await handleSave()
    if (saved) {
      logoVersion.value++ // 保存成功后强制预览重新加载新图
    } else {
      configMap['system.logo'] = ''
      ElMessage.error('Logo保存失败，请重试')
    }
  } else {
    ElMessage.error('上传失败')
  }
}

// 默认存储空间:配置存字节,UI 以 GB 展示/输入(1024^3 换算)
const storageGb = computed(() => {
  const v = Number(configMap['user.default.storage']) || 0
  return v > 0 ? String(Math.round(v / 1073741824 * 10) / 10) : '10'
})
const onStorageGb = (v) => {
  const gb = Number(v)
  if (!isNaN(gb) && gb > 0) configMap['user.default.storage'] = String(Math.round(gb * 1073741824))
}
const handleSave = async () => {
  try {
    // 客户默认共享规则:把 autoShare 序列化写回 configMap,随保存一并提交(否则该配置永不更新)
    configMap['customer.auto_share'] = JSON.stringify({
      superior: autoShare.superior,
      admin: autoShare.admin,
      roles: autoShare.roles || [],
      users: autoShare.users || []
    })
    // 以 configMap 为准提交所有键(含 DB 中尚不存在的键),后端按 configKey upsert
    const metaByKey = {}
    configs.value.forEach(c => { metaByKey[c.configKey] = c })
    const updated = Object.keys(configMap).map(key => ({
      id: metaByKey[key]?.id ?? null,
      configKey: key,
      configValue: configMap[key],
      description: metaByKey[key]?.description ?? null
    }))
    await updateConfigs(updated)
    ElMessage.success('设置已保存')
    await loadConfigs()
    userStore.loadSystemConfig()
    return true
  } catch (e) {
    ElMessage.error('保存失败，请重试')
    return false
  }
}

// ===== 字典类型 =====
const dictTypes = ref([])
const currentType = ref(null)
const typeDialogVisible = ref(false)
const typeForm = reactive({ id: null, dictName: '', dictCode: '', remark: '' })

const loadDictTypes = async () => {
  try {
    const res = await getDictTypes()
    dictTypes.value = res.data || []
  } catch (e) {}
}

const handleTypeSelect = (row) => {
  currentType.value = row
  if (row) loadDictData(row.id)
}

const handleAddType = () => {
  Object.assign(typeForm, { id: null, dictName: '', dictCode: '', remark: '' })
  typeDialogVisible.value = true
}

const handleEditType = (row) => {
  Object.assign(typeForm, { id: row.id, dictName: row.dictName, dictCode: row.dictCode, remark: row.remark || '' })
  typeDialogVisible.value = true
}

const handleDeleteType = async (row) => {
  await ElMessageBox.confirm(`确定删除字典类型「${row.dictName}」及其所有数据？`, '提示', { type: 'warning' })
  await deleteDictType(row.id)
  ElMessage.success('删除成功')
  loadDictTypes()
  if (currentType.value?.id === row.id) {
    currentType.value = null
    dictDataList.value = []
  }
}

const submitType = async () => {
  try {
    if (typeForm.id) {
      await updateDictType(typeForm.id, typeForm)
    } else {
      await createDictType(typeForm)
    }
    ElMessage.success('保存成功')
    typeDialogVisible.value = false
    loadDictTypes()
  } catch (e) {}
}

// ===== 字典数据 =====
const dictDataList = ref([])
const dataDialogVisible = ref(false)
const dataForm = reactive({ id: null, dictTypeId: null, dictLabel: '', dictValue: '', sortOrder: 0, status: 1 })

const loadDictData = async (typeId) => {
  try {
    const res = await getDictDataByTypeId(typeId)
    dictDataList.value = res.data || []
  } catch (e) {}
}

const handleAddData = () => {
  Object.assign(dataForm, { id: null, dictTypeId: currentType.value.id, dictLabel: '', dictValue: '', sortOrder: 0, status: 1 })
  dataDialogVisible.value = true
}

const handleEditData = (row) => {
  Object.assign(dataForm, { id: row.id, dictTypeId: row.dictTypeId, dictLabel: row.dictLabel, dictValue: row.dictValue, sortOrder: row.sortOrder, status: row.status })
  dataDialogVisible.value = true
}

const handleDeleteData = async (row) => {
  await ElMessageBox.confirm(`确定删除「${row.dictLabel}」？`, '提示', { type: 'warning' })
  await deleteDictData(row.id)
  ElMessage.success('删除成功')
  loadDictData(currentType.value.id)
}

const submitData = async () => {
  try {
    if (dataForm.id) {
      await updateDictData(dataForm.id, dataForm)
    } else {
      await createDictData(dataForm)
    }
    ElMessage.success('保存成功')
    dataDialogVisible.value = false
    loadDictData(currentType.value.id)
  } catch (e) {}
}

// ===== 公海池配置 =====
const seaPools = ref([])
const orgTree = ref([])
const poolDialogVisible = ref(false)
const poolForm = reactive({ id: null, poolName: '', visibleKeys: [], sortOrder: 0 })

const loadSeaPools = async () => {
  seaLoadError.value = false
  try {
    const res = await getSeaConfigs()
    seaPools.value = res.data || []
  } catch (e) { seaLoadError.value = true }
}

const loadOrgTree = async () => {
  try {
    const res = await getOrgTree()
    orgTree.value = res.data || []
  } catch (e) {}
}

// 由 visibleDeptIds/visibleUserIds 还原树节点 key (d_<id> / u_<id>)
const buildVisibleKeys = (deptIds, userIds) => {
  const keys = []
  if (deptIds) deptIds.split(',').filter(Boolean).forEach(id => keys.push('d_' + id.trim()))
  if (userIds) userIds.split(',').filter(Boolean).forEach(id => keys.push('u_' + id.trim()))
  return keys
}

const seaTreeRef = ref(null)
// 扁平化组织树用于 key → 标签映射
const flattenOrgTree = (nodes, out = []) => {
  for (const n of nodes) { out.push(n); if (n.children) flattenOrgTree(n.children, out) }
  return out
}
// 已选范围标签（同步树勾选态）
const selectedSeaNodes = computed(() => {
  const all = flattenOrgTree(orgTree.value)
  return (poolForm.visibleKeys || []).map(k => {
    const n = all.find(x => String(x.id) === k)
    if (n) return { key: k, label: n.label, type: String(k).startsWith('d_') ? 'warning' : 'primary' }
    return { key: k, label: k, type: 'info' }
  })
})
// 树勾选变化 → 同步 visibleKeys
const onSeaTreeCheck = () => {
  if (!seaTreeRef.value) return
  poolForm.visibleKeys = seaTreeRef.value.getCheckedKeys().map(String)
}
const removeSeaNode = (item) => {
  poolForm.visibleKeys = (poolForm.visibleKeys || []).filter(k => k !== item.key)
  if (seaTreeRef.value) seaTreeRef.value.setCheckedKeys(poolForm.visibleKeys)
}
const clearSeaSelection = () => {
  poolForm.visibleKeys = []
  if (seaTreeRef.value) seaTreeRef.value.setCheckedKeys([])
}

const handleAddPool = () => {
  Object.assign(poolForm, { id: null, poolName: '', visibleKeys: [], sortOrder: 0 })
  poolDialogVisible.value = true
}

const handleEditPool = (row) => {
  Object.assign(poolForm, {
    id: row.id,
    poolName: row.poolName,
    visibleKeys: buildVisibleKeys(row.visibleDeptIds, row.visibleUserIds),
    sortOrder: row.sortOrder || 0
  })
  poolDialogVisible.value = true
  nextTick(() => { if (seaTreeRef.value) seaTreeRef.value.setCheckedKeys(poolForm.visibleKeys) })
}

const handleDeletePool = async (row) => {
  await ElMessageBox.confirm(`确定删除公海池「${row.poolName}」？`, '提示', { type: 'warning' })
  await deleteSeaConfig(row.id)
  ElMessage.success('删除成功')
  loadSeaPools()
}

const submitPool = async () => {
  if (!poolForm.poolName) { ElMessage.warning('请输入公海池名称'); return }
  if (!poolForm.visibleKeys.length) { ElMessage.warning('请选择可见部门/人员'); return }
  try {
    // 拆分树节点 key 为可见部门ID / 可见人员ID
    const deptIds = poolForm.visibleKeys.filter(k => k.startsWith('d_')).map(k => k.slice(2))
    const userIds = poolForm.visibleKeys.filter(k => k.startsWith('u_')).map(k => k.slice(2))
    const payload = {
      poolName: poolForm.poolName,
      visibleDeptIds: deptIds.join(','),
      visibleUserIds: userIds.join(','),
      sortOrder: poolForm.sortOrder
    }
    if (poolForm.id) {
      await updateSeaConfig(poolForm.id, payload)
    } else {
      await createSeaConfig(payload)
    }
    ElMessage.success('保存成功')
    poolDialogVisible.value = false
    loadSeaPools()
  } catch (e) {}
}

// ===== 激励类别 =====
const categoryTree = ref([])
const collapsed = ref(new Set())
const categoryDialogVisible = ref(false)
const categoryForm = reactive({ id: null, parentId: 0, categoryName: '', categoryCode: '', score: 0, sortOrder: 0, status: 1 })

const categoryParentOptions = computed(() =>
  categoryTree.value.map(c => ({ id: c.id, categoryName: c.categoryName }))
)

const toggleCategory = (id) => {
  const s = new Set(collapsed.value)
  s.has(id) ? s.delete(id) : s.add(id)
  collapsed.value = s
}

const loadCategoryTree = async () => {
  catLoadError.value = false
  try {
    const res = await getCategoryTree()
    categoryTree.value = res.data || []
  } catch (e) { catLoadError.value = true }
}

const openCategoryDialog = (row, parentId) => {
  if (row) {
    Object.assign(categoryForm, { id: row.id, parentId: row.parentId, categoryName: row.categoryName, categoryCode: row.categoryCode, score: row.score, sortOrder: row.sortOrder, status: row.status })
  } else {
    Object.assign(categoryForm, { id: null, parentId: parentId || 0, categoryName: '', categoryCode: '', score: parentId ? 1 : 0, sortOrder: 0, status: 1 })
  }
  categoryDialogVisible.value = true
}

const submitCategory = async () => {
  if (!categoryForm.categoryName.trim()) { ElMessage.warning('请输入类别名称'); return }
  try {
    if (categoryForm.id) {
      await updateCategory(categoryForm.id, categoryForm)
    } else {
      await createCategory(categoryForm)
    }
    ElMessage.success('保存成功')
    categoryDialogVisible.value = false
    loadCategoryTree()
  } catch (e) {}
}

const handleDeleteCategory = async (row) => {
  const hasChildren = row.children && row.children.length > 0
  if (hasChildren) {
    ElMessage.warning(`"${row.categoryName}" 下有 ${row.children.length} 个子项，请先删除子项`)
    return
  }
  const msg = `确认删除 "${row.categoryName}"？`
  try {
    await ElMessageBox.confirm(msg, '删除确认', { type: 'warning' })
    await deleteCategory(row.id)
    ElMessage.success('删除成功')
    loadCategoryTree()
  } catch (e) {}
}

watch(activeTab, (tab) => { if (tab === 'perm') loadPermData() })

onMounted(() => {
  loadConfigs()
  loadPermList()
  loadAllUsers()
  loadDictTypes()
  loadSeaPools()
  loadOrgTree()
  loadCategoryTree()
  // 【修复 20260805】基本设置页"指定角色"下拉依赖 allRoles，此前仅在切到"权限矩阵"Tab 时才加载
  // （watch activeTab），导致基本设置打开时角色下拉为空。挂载即加载，与权限 Tab 共用数据源。
  try { getRoleList().then(res => { allRoles.value = res.data || [] }) } catch {}
})
</script>

<style scoped>
.config-page { height: calc(100vh - 80px); display: flex; flex-direction: column }
.section-box { background: #fff; border: 1px solid #e4e7ed; border-radius: 10px; padding: 20px 24px; flex: 1; overflow: hidden; display: flex; flex-direction: column }
.section-box :deep(.el-tabs) { flex: 1; display: flex; flex-direction: column }
.section-box :deep(.el-tabs__content) { flex: 1; overflow: auto }
.page-header { margin-bottom: 16px; }
.page-header h2 { font-size: 20px; }

.basic-form {
  max-width: 520px;
  padding: 20px 0;
}
.logo-preview {
  width: 48px;
  height: 48px;
  border-radius: 8px;
  object-fit: contain;
  border: 1px solid #ebeef5;
  background: #fafafa;
}

.dict-layout {
  display: flex;
  gap: 16px;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}
.dict-types {
  width: 380px;
  flex-shrink: 0;
  background: #fff;
  border-radius: 10px;
  padding: 14px;
  border: 1px solid #e4e7ed;
  box-shadow: 0 1px 3px rgba(0,0,0,.04);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.dict-data {
  flex: 1;
  min-width: 0;
  background: #fff;
  border-radius: 10px;
  padding: 14px;
  border: 1px solid #e4e7ed;
  box-shadow: 0 1px 3px rgba(0,0,0,.04);
  overflow: auto;
}
.dict-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid #f0f2f5;
  font-weight: 600;
  font-size: 14px;
  color: #1f2329;
}
.dict-type-list { flex: 1; min-height: 0; overflow: auto; display: flex; flex-direction: column; gap: 8px; padding-right: 2px }
.dict-type-item {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 10px 12px;
  cursor: pointer;
  transition: all .18s;
  background: #fafbfc;
}
.dict-type-item:hover { border-color: #b3d3f8; background: #f5f9ff }
.dict-type-item.active {
  border-color: #409eff;
  background: #ecf5ff;
  box-shadow: 0 0 0 1px #409eff inset;
}
.dict-type-name { display: flex; align-items: center; gap: 6px; font-size: 13px; font-weight: 600; color: #1f2329 }
.dict-type-ico { color: #409eff; font-size: 15px }
.dict-type-item.active .dict-type-ico { color: #1f6fd6 }
.dict-type-title { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap }
.dict-type-code { font-size: 11px; color: #a8abb2; font-family: ui-monospace, SFMono-Regular, Menlo, monospace; margin: 3px 0 4px 21px }
.dict-type-acts { margin-left: 21px; display: flex; gap: 2px; opacity: 0; transition: opacity .15s }
.dict-type-item:hover .dict-type-acts, .dict-type-item.active .dict-type-acts { opacity: 1 }
.dict-empty-tip { display: flex; align-items: center; justify-content: center; height: 100%; min-height: 260px }
.form-tip {
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
  margin-top: 4px;
}

.incentive-card {
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 10px;
  padding: 16px 20px;
  margin-bottom: 14px;
}
.incentive-card .dict-header {
  padding-bottom: 12px;
  margin-bottom: 8px;
}
.cat-empty {
  color: #909399;
  font-size: 13px;
  padding: 24px 0;
  text-align: center;
}
.cat-group {
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  margin-bottom: 10px;
  overflow: hidden;
}
.cat-group-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  background: #f5f7fa;
  cursor: pointer;
  user-select: none;
}
.cat-group-name {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}
.cat-group-code {
  font-size: 12px;
  color: #909399;
}
.cat-group-count {
  font-size: 12px;
  color: #909399;
}
.cat-arrow {
  margin-left: auto;
  font-size: 12px;
  color: #909399;
  transition: transform 0.2s;
}
.cat-arrow.expanded {
  transform: rotate(180deg);
}
.cat-group-actions {
  display: flex;
  gap: 2px;
}
.cat-group-body {
  padding: 4px 0;
}
.cat-no-child {
  color: #c0c4cc;
  font-size: 13px;
  text-align: center;
  padding: 16px 0;
}

.action-btns {
  display: flex;
  justify-content: center;
  gap: 2px;
}
.action-btns .el-button {
  min-width: 44px;
}

/* 权限矩阵 */
.perm-table-wrap { overflow-x: auto; max-height: calc(100vh - 280px); overflow-y: auto }
.perm-table { width: 100%; border-collapse: collapse; font-size: 13px }
.perm-table th { position: sticky; top: 0; background: #f5f7fa; padding: 10px 12px; border-bottom: 2px solid #e4e7ed; text-align: center; font-weight: 600; color: #333; z-index: 1 }
.perm-table td { padding: 6px 12px; border-bottom: 1px solid #f0f0f0; text-align: center }
.perm-module { background: #fafbfc; font-weight: 600; color: #1f2329 }
.perm-module-name { text-align: left !important }
.perm-item-col { text-align: left !important; color: #555; min-width: 100px }
.perm-role-col { width: 70px; cursor: pointer; user-select: none }
.perm-role-col:hover { background: #f0f5ff }
.perm-yes { color: #409eff; font-weight: 700; font-size: 16px }
.perm-no { color: #ddd }
.first-in-group td { border-top: 2px solid #e8eaed }

/* 公海池可见范围树 */
.tree-card { border: 1px solid #e4e7ed; border-radius: 8px; padding: 10px 12px; background: #fafbfc }
.tree-card-header { display: flex; align-items: center; gap: 5px; font-size: 13px; font-weight: 600; color: #4e5969; margin-bottom: 6px }
.tree-card-tip { font-size: 11px; color: #909399; margin-top: 6px; line-height: 1.5 }
</style>
