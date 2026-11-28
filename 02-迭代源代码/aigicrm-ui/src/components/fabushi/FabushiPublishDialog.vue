<template>
  <!--
    【2026-11-26 按 PC 原型 v8】法布施发布器 —— 原型 P6 每日诵读 / P7 每日随施 / P8 再读一本 三个弹窗。
    这次只重做外观（版式、组件、配色、字阶）：props / 事件 / 校验 / 提交载荷 / 录音与上传链路都没动。
    与原型有意不同的地方：
      · 类型切换（每日诵读 / 每日随施 / 再读一本）是现有功能，保留成标题下面一行很轻的分段开关（随施弹窗本来就不显示）；
      · 原型里只画了标注的「位置 / 谁可以看 / 文件归档」不做成可点的工具（后端没有这些能力），已有的事实写进提示；
      · 原型里编出来的数字（已录 2 段 · 42 秒、第 3 页、段落原文）不显示，只显示真实状态。
    图标容器写成 <ElIcon>（从 element-plus 显式引入）而不是靠自动引入的 <el-icon>：
    FabushiPublishDialog.spec.js 挂载本组件时没有给 el-icon 准备桩，自动引入的标签会报 “Failed to resolve component”。
  -->
  <el-dialog
    :model-value="modelValue"
    :title="dialogTitle"
    :width="dialogWidth"
    class="fx-dialog fx-pub-dialog"
    :close-on-click-modal="false"
    @update:model-value="(v) => emit('update:modelValue', v)"
    @closed="onDialogClosed"
  >
    <template #header>
      <div class="pub-head">
        <span class="el-dialog__title">{{ dialogTitle }}</span>
        <span class="pub-head-sub">{{ dialogSubtitle }}</span>
        <span class="pub-head-end" aria-hidden="true" />
      </div>
    </template>

    <div class="pub-body">
      <!-- 类型切换：现有功能（原型里没有），做成很轻的一行 -->
      <div v-if="form.scopeType !== 'suishi'" class="pub-type">
        <div class="seg">
          <button
            v-for="tab in TYPE_TABS"
            :key="tab.value"
            type="button"
            class="seg-item"
            :class="{ active: form.scopeType === tab.value }"
            :aria-pressed="form.scopeType === tab.value"
            @click="switchType(tab.value)"
          >{{ tab.label }}</button>
        </div>
      </div>

      <!-- ============ P6 每日诵读：诵读方式 ============ -->
      <template v-if="form.scopeType === 'songdu'">
        <div class="frm-label">
          <span class="frm-name">诵读方式</span><span class="frm-req">*</span>
          <span class="frm-note">两通道二选一</span>
        </div>
        <div class="seg">
          <button type="button" class="seg-item" :class="{ active: form.channel === 'A' }" :aria-pressed="form.channel === 'A'" @click="form.channel = 'A'">A · 直接传（文字 / 图片 + 语音）</button>
          <button type="button" class="seg-item" :class="{ active: form.channel === 'B' }" :aria-pressed="form.channel === 'B'" @click="form.channel = 'B'">B · 选书选段（从《再读一本》内容中选）</button>
        </div>
      </template>

      <!-- 选书（P6 的 B 通道 / P8 的 ❶）：类别 → 书目 → 版本 -->
      <template v-if="bookPickerShown">
        <div class="frm-label">
          <span class="frm-name">{{ form.scopeType === 'zaidu' ? '① 选择具体书（类别 → 书目 → 书名 / 作者 / 版本）' : '选择具体书（类别 → 书目 → 版本）' }}</span><span class="frm-req">*</span>
        </div>
        <div class="triple">
          <el-select v-model="form.categoryCode" class="pub-select" clearable placeholder="类别" @change="onCategoryChange">
            <el-option v-for="c in categoryOptions" :key="c.value" :label="c.label" :value="c.value" />
          </el-select>
          <el-select v-model="form.bookTitle" class="pub-select" clearable filterable placeholder="书目" @change="onTitleChange">
            <el-option v-for="t in titleOptions" :key="t" :label="t" :value="t" />
          </el-select>
          <el-select v-model="form.bookId" class="pub-select" clearable filterable placeholder="版本">
            <el-option v-for="b in editionOptions" :key="b.id" :label="editionOptionLabel(b)" :value="b.id" />
          </el-select>
        </div>
        <div v-if="versionChips.length" class="bcard-tags">
          <span class="ver-label">版本切换：</span>
          <button v-for="b in versionChips" :key="b.id" type="button" class="ver-chip" :class="{ active: form.bookId === b.id }" :aria-pressed="form.bookId === b.id" @click="pickVersion(b)">{{ versionChipLabel(b) }}</button>
        </div>
        <!-- 【FABUSHI-DELTA-20261128 R3/S4】打开这本书：对照着书诵读（新标签页打开，不打断弹窗里的输入） -->
        <button v-if="form.bookId" type="button" class="open-book-link" @click="openBookReader">
          <ElIcon><Reading /></ElIcon>打开这本书（对照诵读）
        </button>
      </template>

      <!-- ============ P6 · B 通道：具体段落 ============ -->
      <template v-if="form.scopeType === 'songdu' && form.channel === 'B'">
        <div class="frm-label"><span class="frm-name">具体段落（章节）</span><span class="frm-req">*</span></div>
        <el-select v-model="form.sectionId" class="pub-select pub-select--full" filterable clearable placeholder="选择章节（按 部 / 章 / 节 层级）">
          <el-option v-for="s in sectionOptions" :key="s.id" :label="s.path" :value="s.id" />
        </el-select>
        <p v-if="form.bookId && !sectionOptions.length" class="frm-hint warn">该书还没有章节结构，先切到「再读一本」用自录章节建立，或改选其他书。</p>
      </template>

      <!-- ============ P8 再读一本：❷ 确定章节 ============ -->
      <template v-if="form.scopeType === 'zaidu'">
        <div class="frm-label"><span class="frm-name">② 确定章节</span><span class="frm-req">*</span></div>
        <div class="seg">
          <button type="button" class="seg-item" :class="{ active: form.sectionMode === 'pick' }" :aria-pressed="form.sectionMode === 'pick'" @click="form.sectionMode = 'pick'">选择已有章节</button>
          <button type="button" class="seg-item" :class="{ active: form.sectionMode === 'manual' }" :aria-pressed="form.sectionMode === 'manual'" @click="form.sectionMode = 'manual'">自我录入章节</button>
        </div>
        <template v-if="form.sectionMode === 'pick'">
          <el-select v-model="form.sectionId" class="pub-select pub-select--full seg-after" filterable clearable placeholder="选择章节（按 部 / 章 / 节 层级）">
            <el-option v-for="s in sectionOptions" :key="s.id" :label="s.path" :value="s.id" />
          </el-select>
          <p v-if="form.bookId && !sectionOptions.length" class="frm-hint warn">该书还没有章节结构，请切到「自我录入章节」。</p>
        </template>
        <el-input v-else v-model="form.manualSection" class="pub-select pub-select--full seg-after" maxlength="100" placeholder="例如：第八章 · 上善若水" aria-label="章节名称" />
      </template>

      <!-- ============ P6 每日诵读：素材、图片、语音 ============ -->
      <template v-if="form.scopeType === 'songdu'">
        <div class="frm-label">
          <span class="frm-name">诵读素材（文字或图片）</span>
          <span class="frm-note">标题、正文、图片、语音至少填一项</span>
          <span class="spacer" />
          <el-input v-model="form.title" class="frm-title" maxlength="120" placeholder="给这段诵读起个标题（可选）" aria-label="标题（可选）" />
        </div>
        <el-input v-model="form.content" type="textarea" class="frm-textarea" maxlength="2000" show-word-limit placeholder="今天诵读的原文 / 你的批注…" aria-label="正文" />

        <div class="frm-label">
          <span class="frm-name">或上传图片（拍纸页）</span>
          <span class="frm-note">1-{{ imageLimit }} 张 · 可裁剪标注</span>
        </div>
        <div class="att-row">
          <div v-for="(img, i) in images" :key="img.key" class="att-tile picked" :class="{ current: images.length > 1 && editIndex === i }">
            <button type="button" class="cell-pick" :tabindex="images.length > 1 ? 0 : -1" :aria-pressed="editIndex === i" :aria-label="`已选图片 ${i + 1}${images.length > 1 ? '，点选后对它裁剪或标注' : ''}`" @click="currentImage = i">
              <img :src="img.previewUrl" :alt="img.name" />
              <span class="att-cap">{{ i === 0 ? `已选 ${images.length} 张` : `第 ${i + 1} 张` }}</span>
            </button>
            <button type="button" class="cell-x" :aria-label="`移除图片 ${i + 1}`" title="移除" @click="removeAttachment(attachments.indexOf(img))"><ElIcon><Close /></ElIcon></button>
          </div>
          <button v-if="images.length < imageLimit" type="button" class="att-tile add" @click="pickFile('image', false)">
            <ElIcon><Plus /></ElIcon>＋ 添加图片
          </button>
        </div>
        <!-- 现有的图片编辑只有裁剪和红框标注：原型里的“划线 / 圈”没有做，不放 -->
        <div class="edit-tools">
          <button type="button" class="edit-tool" :disabled="!editTarget" @click="openEditor(editIndex, 'crop')">裁剪</button>
          <button type="button" class="edit-tool" :disabled="!editTarget" title="在图上拖拽画红框" @click="openEditor(editIndex, 'mark')">框选标注</button>
          <span v-if="images.length > 1" class="edit-tools-label">对第 {{ editIndex + 1 }} 张操作，点图片切换</span>
        </div>

        <div class="frm-label">
          <span class="frm-name">诵读语音（朗读 / 背诵上面的内容）</span>
          <span class="frm-note">1-{{ MAX_AUDIO }} 段 × 每段 ≤{{ MAX_AUDIO_SECONDS }} 秒</span>
        </div>
        <div class="audio-seg" :class="{ live: rec.active }">
          <span class="recite-wave" aria-hidden="true"><i v-for="h in WAVE" :key="h" :style="{ height: h + 'px' }" /></span>
          <span class="audio-seg-text">{{ audioSummary }}</span>
          <span class="spacer" />
          <el-button size="small" @click="rec.active ? stopRec() : startRec()">{{ rec.active ? '停止录制' : '＋ 录一段' }}</el-button>
          <el-button size="small" :disabled="!canPreview" @click="togglePreview">{{ previewing ? '停止' : '试听' }}</el-button>
          <el-button size="small" @click="pickFile('audio', false)">上传录音</el-button>
        </div>
        <div v-if="rec.url || audioAttachments.length" ref="audioListRef" class="audio-block">
          <div v-if="rec.url" class="audio-pending">
            <audio ref="pendingAudioRef" :src="rec.url" controls class="audio-el" />
            <span class="audio-name">刚录的一段<template v-if="rec.seconds"> · {{ rec.seconds }} 秒</template>（还没保存）</span>
            <el-button size="small" type="primary" @click="saveRec">保存语音</el-button>
            <el-button size="small" @click="clearRec">重录</el-button>
          </div>
          <FabushiAudioList :items="audioAttachments" @remove="(item) => removeAttachment(attachments.indexOf(item))" />
        </div>
        <!-- 两行说明（原型的位置）：语音怎么用、当前通道是什么意思 -->
        <div class="frm-foot">
          <p>录完先点“保存语音”；保存后可逐段试听，不满意删掉重录。</p>
          <p>{{ form.channel === 'A' ? CHANNEL_HINT.A : CHANNEL_HINT.B }}</p>
        </div>
      </template>

      <!-- ============ P7 每日随施：像发朋友圈一样简单 ============ -->
      <template v-else-if="form.scopeType === 'suishi'">
        <el-input v-model="form.content" type="textarea" class="frm-textarea frm-textarea--tall" maxlength="2000" show-word-limit placeholder="写点什么…（纯文字也能发，不设标题、不关联书目）" aria-label="这一刻的想法" />
        <div class="composer-grid">
          <div v-for="(img, idx) in images" :key="img.key" class="composer-cell img" :class="{ current: images.length > 1 && editIndex === idx }">
            <button type="button" class="cell-pick" :tabindex="images.length > 1 ? 0 : -1" :aria-pressed="editIndex === idx" :aria-label="`图片 ${idx + 1}${images.length > 1 ? '，点选后对它裁剪或标注' : ''}`" @click="currentImage = idx">
              <img :src="img.previewUrl" :alt="img.name" />
            </button>
            <button type="button" class="cell-x" :aria-label="`移除图片 ${idx + 1}`" title="移除" @click="removeAttachment(attachments.indexOf(img))"><ElIcon><Close /></ElIcon></button>
            <button type="button" class="cell-zoom" aria-label="放大" title="放大" @click="openViewer(idx)"><ElIcon><ZoomIn /></ElIcon></button>
          </div>
          <button v-if="images.length < imageLimit" type="button" class="composer-cell add add-first" @click="pickFile('image', false)"><span class="add-plus">＋</span><span class="add-count">{{ images.length }}/{{ imageLimit }}</span></button>
          <button v-for="n in emptyImageSlots" :key="'e' + n" type="button" class="composer-cell add" aria-label="添加图片" @click="pickFile('image', false)">＋</button>
        </div>
        <!-- 原型这一行是“裁剪 / 标注 / 放大”各一个；多张图时点哪张就对哪张做 -->
        <div class="edit-tools">
          <span class="edit-tools-label">已选图片可：</span>
          <button type="button" class="edit-tool" :disabled="!editTarget" @click="openEditor(editIndex, 'crop')">裁剪</button>
          <button type="button" class="edit-tool" :disabled="!editTarget" title="在图上拖拽画红框" @click="openEditor(editIndex, 'mark')">标注</button>
          <button type="button" class="edit-tool" :disabled="!editTarget" @click="openViewer(editIndex)">放大</button>
          <span v-if="images.length > 1" class="edit-tools-label">对第 {{ editIndex + 1 }} 张操作，点图片切换</span>
        </div>
        <!-- 工具格子只放现有功能真有的。原型里的“位置”“谁可以看”“文件归档”后端没有对应能力，不做成可点的格子；
             其中已有的两条事实（全员可见、附件归档到文件中心）写在下面的提示里。 -->
        <div class="pub-tools">
          <button type="button" class="pub-tool" @click="pickFile('image', false)"><span class="pt-ico"><ElIcon><Picture /></ElIcon></span>图片</button>
          <button type="button" class="pub-tool" @click="pickFile('image', true)"><span class="pt-ico"><ElIcon><Camera /></ElIcon></span>拍摄</button>
          <button type="button" class="pub-tool" @click="pickFile('video', false)"><span class="pt-ico"><ElIcon><VideoPlay /></ElIcon></span>视频</button>
          <button type="button" class="pub-tool" :class="{ on: rec.active }" @click="rec.active ? stopRec() : startRec()"><span class="pt-ico"><ElIcon><Microphone /></ElIcon></span>{{ rec.active ? '停止' : '说话' }}</button>
          <button type="button" class="pub-tool" @click="pickFile('file', false)"><span class="pt-ico"><ElIcon><Folder /></ElIcon></span>文件</button>
        </div>
        <div v-if="rec.url || audioAttachments.length" ref="audioListRef" class="audio-block">
          <div v-if="rec.url" class="audio-pending">
            <audio ref="pendingAudioRef" :src="rec.url" controls class="audio-el" />
            <span class="audio-name">刚录的一段<template v-if="rec.seconds"> · {{ rec.seconds }} 秒</template>（还没保存）</span>
            <el-button size="small" type="primary" @click="saveRec">保存语音</el-button>
            <el-button size="small" @click="clearRec">重录</el-button>
          </div>
          <FabushiAudioList :items="audioAttachments" @remove="(item) => removeAttachment(attachments.indexOf(item))" />
        </div>
        <div v-if="nonImages.length" class="file-chips">
          <span v-for="f in nonImages" :key="f.key" class="file-chip"><ElIcon><Files /></ElIcon><span class="file-name">{{ f.name }}</span><button type="button" class="chip-x" aria-label="移除" @click="removeAttachment(attachments.indexOf(f))"><ElIcon><Close /></ElIcon></button></span>
        </div>
        <p class="frm-foot">不关联书目 · 不设标题 · 纯文字也能发；正能量、书的 1-5 页、心得、照片都行。</p>
        <!-- 说明只写后端真做的那样：附件必须是传到文件中心「法布施」空间的文件；信息流全员可见；
             内容下架或书目停用后，附件在文件中心里的可见范围回落到上传人
             （后端 FolderPermissionService.isFabushiCommunityFile：只有已发布内容的附件、已通过书目的 PDF 才算社区文件）。
             最后一句说的是附件，不是信息流里的那条内容。原型那句里夹着的权限码不照抄。 -->
        <p class="pub-archive"><ElIcon><Paperclip /></ElIcon><span>附件保存在 <b>文件中心 › 法布施</b> 空间；社区内容对全员可见（暂不支持「部分可见 / 不给谁看」）；内容下架或书目停用后，附件在文件中心里的可见范围回落到上传人。</span></p>
      </template>

      <!-- ============ P8 再读一本：❸ 上传内容 ============ -->
      <template v-if="form.scopeType === 'zaidu'">
        <div class="frm-label">
          <span class="frm-name">③ 上传内容</span><span class="frm-req">*</span>
          <span class="frm-note">文字、书页、语音至少一项</span>
        </div>
        <el-input v-model="form.content" type="textarea" class="frm-textarea" maxlength="2000" show-word-limit placeholder="这一章读完的想法 / 摘抄 / 批注…" aria-label="上传内容" />
        <div class="att-row att-entry">
          <button type="button" class="att-tile" @click="pickFile('image', false)"><ElIcon><Document /></ElIcon><span>书页图片</span><span class="att-count">{{ images.length }}/{{ imageLimit }}</span></button>
          <button type="button" class="att-tile" :class="{ on: rec.active }" @click="rec.active ? stopRec() : startRec()"><ElIcon><Microphone /></ElIcon><span>{{ rec.active ? '停止' : '诵读语音' }}</span><span class="att-count">{{ audioAttachments.length }}/{{ MAX_AUDIO }} 段</span></button>
          <button type="button" class="att-tile" @click="pickFile('file', false)"><ElIcon><Folder /></ElIcon><span>文件 / 视频</span><span class="att-count">{{ nonImages.length }} 个</span></button>
        </div>
        <div v-if="images.length" class="composer-grid att-thumbs">
          <div v-for="(img, idx) in images" :key="img.key" class="composer-cell img">
            <img :src="img.previewUrl" :alt="img.name" />
            <button type="button" class="cell-x" :aria-label="`移除图片 ${idx + 1}`" title="移除" @click="removeAttachment(attachments.indexOf(img))"><ElIcon><Close /></ElIcon></button>
          </div>
          <button v-if="images.length < imageLimit" type="button" class="composer-cell add add-first" @click="pickFile('image', false)"><span class="add-plus">＋</span><span class="add-count">{{ images.length }}/{{ imageLimit }}</span></button>
        </div>
        <div v-if="rec.url || audioAttachments.length" ref="audioListRef" class="audio-block">
          <div v-if="rec.url" class="audio-pending">
            <audio ref="pendingAudioRef" :src="rec.url" controls class="audio-el" />
            <span class="audio-name">刚录的一段<template v-if="rec.seconds"> · {{ rec.seconds }} 秒</template>（还没保存）</span>
            <el-button size="small" type="primary" @click="saveRec">保存语音</el-button>
            <el-button size="small" @click="clearRec">重录</el-button>
          </div>
          <FabushiAudioList :items="audioAttachments" @remove="(item) => removeAttachment(attachments.indexOf(item))" />
        </div>
        <div v-if="nonImages.length" class="file-chips">
          <span v-for="f in nonImages" :key="f.key" class="file-chip"><ElIcon><Files /></ElIcon><span class="file-name">{{ f.name }}</span><button type="button" class="chip-x" aria-label="移除" @click="removeAttachment(attachments.indexOf(f))"><ElIcon><Close /></ElIcon></button></span>
        </div>
        <p class="frm-foot">同一本书的章节日日积累 → 「按书浏览」按书聚合展示；精读完成后整本记录沉淀在我的觉醒。</p>
      </template>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="emit('update:modelValue', false)">取消</el-button>
        <el-button :type="form.scopeType === 'zaidu' ? 'success' : 'primary'" :loading="submitting" @click="submit">{{ submitLabel }}</el-button>
      </div>
    </template>

    <!-- 统一文件选择器：capture=true 走相机 -->
    <input ref="fileInputRef" type="file" style="display:none" @change="onFileChosen" />

    <!-- 图片编辑：裁剪 / 标注（Canvas，不伪造按钮） -->
    <el-dialog v-model="editor.visible" title="编辑图片" width="720px" append-to-body class="fx-dialog fx-pub-dialog" @closed="resetEditor">
      <div class="editor-stage" @mousedown="onEditorDown" @mousemove="onEditorMove" @mouseup="onEditorUp" @mouseleave="onEditorUp">
        <canvas ref="editorCanvasRef" />
      </div>
      <div class="edit-tools" style="margin-top:10px">
        <span class="edit-tools-label">模式：</span>
        <button type="button" class="edit-tool" :class="{ on: editor.mode === 'crop' }" @click="setEditorMode('crop')">裁剪</button>
        <button type="button" class="edit-tool" :class="{ on: editor.mode === 'mark' }" @click="setEditorMode('mark')">标注</button>
        <span class="edit-hint">{{ editor.mode === 'crop' ? '拖拽选择裁剪区域，点“应用”' : '拖拽画红框，点“应用”' }}</span>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="editor.visible = false">取消</el-button>
          <el-button type="primary" :loading="editor.saving" @click="applyEditor">应用</el-button>
        </div>
      </template>
    </el-dialog>

    <el-image-viewer v-if="viewer.visible" :url-list="images.map(i => i.previewUrl)" :initial-index="viewer.index" @close="viewer.visible = false" />
  </el-dialog>
</template>

<script setup>
/**
 * 发布器（P6/P7/P8 三屏合一）。
 *
 * 设计原则（沿用本仓库的「不做假功能」）：
 * - 每个按钮都接到真实接口；后端没有的能力（位置、谁可以看）不做占位按钮，只在提示里说明。
 * - 章节「自我录入」走后端 `/sections/self`（fabushi:create 权限 + 同名幂等），不是只在前端拼字符串。
 * - 录音用 MediaRecorder 真录，上传走 `/file/upload`（scope=fabushi），与图片同一条链路。
 *
 * 【2026-11-26 按 PC 原型 v8 重做外观】只动了模板与样式；脚本里新增的都是展示用的东西
 * （弹窗宽度 / 副标题、“版本切换”一行列哪些书、图片编辑针对哪一张、语音行上的文字与“试听”），
 * 校验、提交载荷、录音与上传链路原样未动。
 */
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
// 图标容器写成 <ElIcon>（从 element-plus 显式引入）而不是靠自动引入的 <el-icon>：
// FabushiPublishDialog.spec.js 挂载本组件时没有给 el-icon 准备桩，自动引入的标签会报 “Failed to resolve component”。
import { ElIcon, ElImageViewer, ElMessage } from 'element-plus'
import { Camera, Close, Document, Files, Folder, Microphone, Paperclip, Picture, Plus, Reading, VideoPlay, ZoomIn } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import request from '@/utils/request'
import { addFabushiAttachment, createFabushiPost, getFabushiBooks, getFabushiSections } from '@/api/fabushi'
import FabushiAudioList from './FabushiAudioList.vue'
import '@/assets/fabushi-v8.css'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  defaultScope: { type: String, default: 'songdu' },
  presetBookId: { type: [Number, String], default: null },
})
const emit = defineEmits(['update:modelValue', 'published'])

// 【FABUSHI-DELTA-20261128 R3/S4】打开这本书：新标签页进阅读器，对照诵读
const router = useRouter()
const openBookReader = () => {
  if (!form.bookId) return
  const href = router.resolve({ name: 'FabushiRead', params: { bookId: String(form.bookId) } }).href
  window.open(href, '_blank')
}

const TYPE_TABS = [
  { value: 'songdu', label: '每日诵读' },
  { value: 'suishi', label: '每日随施' },
  { value: 'zaidu', label: '再读一本' },
]

const form = reactive({
  scopeType: 'songdu',
  channel: 'A',
  bookId: null,
  sectionId: null,
  sectionMode: 'pick',
  manualSection: '',
  title: '',
  content: '',
  categoryCode: null,
  bookTitle: null,
})

const submitting = ref(false)
const books = ref([])
const sections = ref([])
const attachments = ref([])
const fileInputRef = ref(null)
const pendingPick = reactive({ type: 'image', capture: false })
let seq = 0

const dialogTitle = computed(() => {
  const t = TYPE_TABS.find((x) => x.value === form.scopeType)
  return `发布 · ${t ? t.label : '内容'}`
})
const submitLabel = computed(() => {
  if (form.scopeType === 'songdu') return '发布每日诵读'
  if (form.scopeType === 'suishi') return '发表'
  return '发布再读一本'
})
/* ---------- 展示：弹窗宽度、副标题、通道说明（照原型） ---------- */
/** 原型里随施弹窗 560px，其余 660px */
const dialogWidth = computed(() => (form.scopeType === 'suishi' ? '560px' : '660px'))
/** 原型 .modal-head 里跟在标题后面的那半句 */
const DIALOG_SUBTITLE = {
  songdu: '人人 · 天天 · 小而短地读经典（固定时点推荐 晨 08:00）',
  suishi: '随手施 · 像发朋友圈一样简单',
  zaidu: '按章节日日上传 · 整本精读记录',
}
const dialogSubtitle = computed(() => DIALOG_SUBTITLE[form.scopeType] || '')
/** 两个通道各是什么意思（写在弹窗底部的说明里） */
const CHANNEL_HINT = {
  A: 'A 通道：先选书 → 再写 / 传今天要读的文字或图片 → 对着它朗读背诵；内容挂在这本书下，不指定章节。',
  B: 'B 通道：三级选书 → 具体段落 → 诵读，内容会挂到该书的章节下。',
}
/** 语音行左侧那一小段波形（固定的装饰，不代表真实波形） */
const WAVE = [6, 12, 8, 14, 10, 15, 7]

/* ---------------- 三级选书：类别 → 书目 → 版本 ---------------- */
const categoryOptions = computed(() => {
  const seen = new Map()
  books.value.forEach((b) => {
    if (!b.categoryCode) return
    if (!seen.has(b.categoryCode)) seen.set(b.categoryCode, b.categoryName || b.categoryCode)
  })
  return [...seen.entries()].map(([value, label]) => ({ value, label }))
})
const titleOptions = computed(() => {
  const set = new Set()
  books.value.filter((b) => !form.categoryCode || b.categoryCode === form.categoryCode).forEach((b) => b.title && set.add(b.title))
  return [...set]
})
const editionOptions = computed(() => books.value.filter((b) => {
  if (form.categoryCode && b.categoryCode !== form.categoryCode) return false
  if (form.bookTitle && b.title !== form.bookTitle) return false
  return true
}))
const editionLabel = (b) => [b.author, b.publisher, b.editionType, b.pages ? `${b.pages} 页` : ''].filter(Boolean).join(' · ') || '默认版本'
/** 第三个下拉（版本）的选项文字：还没选定书目时前面带上书名 —— 否则没有版本信息的书全都显示成“默认版本”，分不清是哪本 */
const editionOptionLabel = (b) => {
  const meta = editionLabel(b)
  if (form.bookTitle) return meta
  return meta === '默认版本' ? `《${b.title}》` : `《${b.title}》 · ${meta}`
}
/** 要不要出“选择具体书”：每日诵读的两个通道、再读一本都要。
 *  【2026-11-26 审后修正】A 通道原来不出选书，但后端对每日诵读一律要求关联已审核书目（没有书会 400），
 *  小程序端的 A 通道也是先选书 —— 改版前从“＋每日诵读”直接打开、停在 A 通道的人根本发不出去。 */
const bookPickerShown = computed(() => form.scopeType === 'zaidu' || form.scopeType === 'songdu')

const onCategoryChange = () => { form.bookTitle = null; form.bookId = null; form.sectionId = null }
const onTitleChange = () => {
  form.sectionId = null
  const first = editionOptions.value[0]
  form.bookId = first ? first.id : null
}
/* “版本切换”一行（原型 P8）：只列同一本书的各个版本。
   没选定书目时不出这一行 —— 那时 editionOptions 是全部的书，把它们当“版本”摆出来是误导（版本还是在第三个下拉里选）。 */
const currentTitle = computed(() => {
  const preset = books.value.find((b) => b.id === form.bookId)
  return form.bookTitle || (preset && preset.title) || ''
})
const versionChips = computed(() => {
  if (!currentTitle.value) return []
  const list = books.value.filter((b) => b.title === currentTitle.value && (!form.categoryCode || b.categoryCode === form.categoryCode))
  return list.length > 1 ? list : []
})
const versionChipLabel = (b) => b.publisher || b.editionType || ('版本 ' + b.id)
const pickVersion = (b) => {
  form.categoryCode = b.categoryCode || form.categoryCode
  form.bookTitle = b.title
  form.bookId = b.id
}

/* ---------------- 章节树 ---------------- */
const LEVEL_LABEL = { part: '部', chapter: '章', section: '节', preface: '序' }
const sectionOptions = computed(() => {
  const out = []
  const walk = (nodes, prefix) => {
    ;(nodes || []).forEach((n) => {
      const label = `${LEVEL_LABEL[n.levelCode] || ''}${n.title}`
      const path = prefix ? `${prefix} / ${label}` : label
      out.push({ id: n.id, path })
      if (n.children && n.children.length) walk(n.children, path)
    })
  }
  walk(sections.value, '')
  return out
})

const loadSections = async (bookId) => {
  sections.value = []
  if (!bookId) return
  try {
    const { data } = await getFabushiSections(bookId)
    sections.value = (data && data.sections) || []
  } catch (e) {
    sections.value = []
  }
}
watch(() => form.bookId, (id) => { form.sectionId = null; loadSections(id) })

/* ---------------- 录音（MediaRecorder） ---------------- */
const rec = reactive({ active: false, seconds: 0, url: '', file: null })
let mediaRecorder = null
let chunks = []
let timer = null

const startRec = async () => {
  if (rec.active) return
  if (audioAttachments.value.length >= MAX_AUDIO) {
    ElMessage.warning(`语音最多 ${MAX_AUDIO} 段，请先删除不用的段`)
    return
  }
  if (!window.isSecureContext || !navigator.mediaDevices || !window.MediaRecorder) {
    ElMessage.warning('当前是 HTTP 或浏览器不支持麦克风；已切换为上传录音，请用手机录音后选择文件，或改用 HTTPS 打开')
    pickFile('audio', false)
    return
  }
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    chunks = []
    mediaRecorder = new MediaRecorder(stream)
    mediaRecorder.ondataavailable = (e) => { if (e.data && e.data.size) chunks.push(e.data) }
    mediaRecorder.onstop = () => {
      const type = mediaRecorder.mimeType || 'audio/webm'
      const blob = new Blob(chunks, { type })
      const ext = type.includes('mp4') ? 'm4a' : type.includes('ogg') ? 'ogg' : 'webm'
      rec.file = new File([blob], `rec-${Date.now()}.${ext}`, { type })
      if (rec.url) URL.revokeObjectURL(rec.url)
      rec.url = URL.createObjectURL(blob)
      stream.getTracks().forEach((t) => t.stop())
    }
    mediaRecorder.start()
    rec.active = true
    rec.seconds = 0
    timer = setInterval(() => {
      rec.seconds += 1
      // 【P6】后端单段上限 60s，前端到点自动停，避免录完才被 400 打回、白录一遍
      if (rec.seconds >= MAX_AUDIO_SECONDS) {
        stopRec()
        ElMessage.info(`单段语音上限 ${MAX_AUDIO_SECONDS} 秒，已自动停止`)
      }
    }, 1000)
  } catch (e) {
    ElMessage.warning('无法访问麦克风，请检查浏览器权限')
  }
}
const stopRec = () => {
  if (!rec.active) return
  if (timer) { clearInterval(timer); timer = null }
  try { mediaRecorder && mediaRecorder.stop() } catch (e) { /* 已停止 */ }
  rec.active = false
}
const clearRec = () => {
  if (rec.url) URL.revokeObjectURL(rec.url)
  rec.url = ''
  rec.file = null
  rec.seconds = 0
}
const saveRec = async () => {
  if (!rec.file) return
  const item = await uploadOne(rec.file, 'audio')
  if (item) { attachments.value.push(item); clearRec(); ElMessage.success('语音已上传') }
}

/* ---------------- 附件上传 ---------------- */
const pickFile = (type, capture) => {
  pendingPick.type = type
  pendingPick.capture = capture
  const el = fileInputRef.value
  if (!el) return
  el.accept = type === 'image' ? 'image/*' : type === 'video' ? 'video/*' : type === 'audio' ? 'audio/*' : ''
  if (capture) el.setAttribute('capture', 'environment')
  else el.removeAttribute('capture')
  el.value = ''
  el.click()
}

const guessType = (file) => (file.type.startsWith('image/') ? 'image' : file.type.startsWith('audio/') ? 'audio' : file.type.startsWith('video/') ? 'video' : 'file')

const uploadOne = async (file, fileType) => {
  const fd = new FormData()
  fd.append('file', file)
  fd.append('scope', 'fabushi')
  const res = await request.post('/file/upload', fd)
  const id = res.data && res.data.id
  if (!id) return null
  seq += 1
  return {
    key: `a${seq}`,
    fileId: id,
    name: file.name,
    fileType,
    // 【P6 2026-11-23】音/视频也给本地 blob URL：保存后仍可试听/预览，不必回源下载
    previewUrl: ['image', 'audio', 'video'].includes(fileType) ? URL.createObjectURL(file) : '',
  }
}

const imageLimit = computed(() => (form.scopeType === 'suishi' ? 9 : 5))
// 九宫格露出几格：至少两行 6 格（原型 P7 的样子），图多了往上加，最多 9 格
const gridCells = computed(() => (images.value.length >= imageLimit.value ? 9 : Math.min(9, Math.max(6, images.value.length + 1))))
const emptyImageSlots = computed(() => Math.max(0, gridCells.value - images.value.length - (images.value.length < imageLimit.value ? 1 : 0)))
const images = computed(() => attachments.value.filter((a) => a.fileType === 'image'))
// 语音有独立可播放列表（FabushiAudioList），文件胶囊只列其余非图片附件，避免同一段出现两次
const nonImages = computed(() => attachments.value.filter((a) => a.fileType !== 'image' && a.fileType !== 'audio'))
// 【P6 2026-11-23】原型「语音 1-10 段 × ≤60s」：已保存段可逐条试听/删除，超限与超时前端先挡
const MAX_AUDIO = 10
const MAX_AUDIO_SECONDS = 60

/* 图片编辑针对哪一张（P6 / P7 的裁剪、标注、放大）：默认第一张；有多张时点哪张就对哪张做 */
const currentImage = ref(0)
watch(images, (list) => { if (currentImage.value > list.length - 1) currentImage.value = Math.max(0, list.length - 1) })
const editIndex = computed(() => (images.value[currentImage.value] ? currentImage.value : 0))
const editTarget = computed(() => images.value[editIndex.value] || null)

/* ---------------- 图片编辑：裁剪 / 标注（Canvas） ---------------- */
const editorCanvasRef = ref(null)
const editor = reactive({ visible: false, index: -1, mode: 'crop', saving: false })
const viewer = reactive({ visible: false, index: 0 })
let editorImage = null
let editorScale = 1
let dragStart = null
let dragEnd = null
let editorMarks = []
let cropRect = null

const openViewer = (idx) => { viewer.index = idx; viewer.visible = true }
const setEditorMode = (mode) => { editor.mode = mode; dragStart = null; dragEnd = null; cropRect = null; drawEditor() }
const resetEditor = () => {
  editorImage = null; dragStart = null; dragEnd = null; editorMarks = []; cropRect = null
  editor.index = -1; editor.saving = false
}
const openEditor = (idx, mode) => {
  const item = images.value[idx]
  if (!item) return
  const img = new Image()
  img.onload = () => {
    editorImage = img
    editor.index = idx
    editor.mode = mode || 'crop'
    dragStart = null; dragEnd = null; editorMarks = []; cropRect = null
    editor.visible = true
    nextTick(() => {
      const canvas = editorCanvasRef.value
      if (!canvas) return
      const maxW = 640; const maxH = 420
      const scale = Math.min(1, maxW / img.naturalWidth, maxH / img.naturalHeight)
      editorScale = scale
      canvas.width = Math.max(1, Math.round(img.naturalWidth * scale))
      canvas.height = Math.max(1, Math.round(img.naturalHeight * scale))
      drawEditor()
    })
  }
  img.onerror = () => ElMessage.error('图片加载失败')
  img.src = item.previewUrl
}

const drawEditor = () => {
  const canvas = editorCanvasRef.value
  if (!canvas || !editorImage) return
  const ctx = canvas.getContext('2d')
  ctx.clearRect(0, 0, canvas.width, canvas.height)
  ctx.drawImage(editorImage, 0, 0, canvas.width, canvas.height)
  ctx.save()
  ctx.strokeStyle = '#e6a23c'
  ctx.lineWidth = 2
  ctx.setLineDash([6, 4])
  if (dragStart && dragEnd) {
    ctx.strokeRect(dragStart.x, dragStart.y, dragEnd.x - dragStart.x, dragEnd.y - dragStart.y)
  } else if (cropRect && editor.mode === 'crop') {
    ctx.strokeRect(cropRect.x, cropRect.y, cropRect.w, cropRect.h)
  }
  ctx.setLineDash([])
  ctx.strokeStyle = '#f56c6c'
  ctx.lineWidth = 3
  editorMarks.forEach((m) => ctx.strokeRect(m.x, m.y, m.w, m.h))
  ctx.restore()
}

const editorPoint = (e) => {
  const canvas = editorCanvasRef.value
  const rect = canvas.getBoundingClientRect()
  return {
    x: (e.clientX - rect.left) * canvas.width / rect.width,
    y: (e.clientY - rect.top) * canvas.height / rect.height,
  }
}
const onEditorDown = (e) => {
  if (editor.mode !== 'crop' && editor.mode !== 'mark') return
  if (editor.mode === 'crop') cropRect = null
  dragStart = editorPoint(e); dragEnd = { ...dragStart }; drawEditor()
}
const onEditorMove = (e) => {
  if (!dragStart) return
  dragEnd = editorPoint(e); drawEditor()
}
const onEditorUp = () => {
  if (!dragStart || !dragEnd) return
  const x = Math.min(dragStart.x, dragEnd.x)
  const y = Math.min(dragStart.y, dragEnd.y)
  const w = Math.abs(dragEnd.x - dragStart.x)
  const h = Math.abs(dragEnd.y - dragStart.y)
  if (w > 4 && h > 4) {
    if (editor.mode === 'crop') cropRect = { x, y, w, h }
    if (editor.mode === 'mark') editorMarks.push({ x, y, w, h })
  }
  dragStart = null; dragEnd = null; drawEditor()
}

const applyEditor = async () => {
  const canvas = editorCanvasRef.value
  if (!canvas || !editorImage) return
  let out
  if (editor.mode === 'crop') {
    // 裁剪区域在 onEditorUp 保存到 cropRect
    if (!cropRect) { ElMessage.warning('请先拖拽选择裁剪区域'); return }
    const sx = cropRect.x / editorScale
    const sy = cropRect.y / editorScale
    const sw = cropRect.w / editorScale
    const sh = cropRect.h / editorScale
    if (sw < 8 || sh < 8) { ElMessage.warning('裁剪区域太小'); return }
    out = document.createElement('canvas')
    out.width = Math.max(1, Math.round(sw)); out.height = Math.max(1, Math.round(sh))
    out.getContext('2d').drawImage(editorImage, sx, sy, sw, sh, 0, 0, out.width, out.height)
  } else {
    out = document.createElement('canvas')
    out.width = canvas.width; out.height = canvas.height
    const ctx = out.getContext('2d')
    ctx.drawImage(editorImage, 0, 0, out.width, out.height)
    ctx.strokeStyle = '#f56c6c'; ctx.lineWidth = 3
    editorMarks.forEach((m) => ctx.strokeRect(m.x, m.y, m.w, m.h))
  }
  editor.saving = true
  try {
    const blob = await new Promise((resolve) => out.toBlob(resolve, 'image/png'))
    if (!blob) throw new Error('导出图片失败')
    const file = new File([blob], `edited-${Date.now()}.png`, { type: 'image/png' })
    const item = await uploadOne(file, 'image')
    if (!item) throw new Error('上传失败')
    const old = images.value[editor.index]
    const oldIndex = attachments.value.indexOf(old)
    if (oldIndex >= 0) attachments.value.splice(oldIndex, 1, item)
    else attachments.value.push(item)
    if (old && old.previewUrl) URL.revokeObjectURL(old.previewUrl)
    editor.visible = false
    ElMessage.success('图片已更新')
  } catch (e) {
    ElMessage.error(e.message || '图片编辑失败')
  } finally {
    editor.saving = false
  }
}

const audioAttachments = computed(() => attachments.value.filter((a) => a.fileType === 'audio'))

/* ---------------- 展示：语音行上的文字与“试听”（P6） ---------------- */
/** 只报真实状态：每段多长要放过了才知道，所以不编“42 秒 / 58 秒”这样的数 */
const audioSummary = computed(() => {
  if (rec.active) return `录制中 ${rec.seconds} 秒 / ${MAX_AUDIO_SECONDS} 秒`
  const saved = audioAttachments.value.length
  if (!saved && !rec.url) return `还没有语音（最多 ${MAX_AUDIO} 段）`
  const parts = []
  if (saved) parts.push(`已录 ${saved} 段`)
  if (rec.url) parts.push(`另有一段待保存${rec.seconds ? `（${rec.seconds} 秒）` : ''}`)
  return parts.join('，')
})
/* “试听”= 按一下播放最新的一段（刚录、还没保存的优先），再按一下暂停。
   直接用那一段自己的播放器（不另开音频实例），进度就显示在它自己的控件上。 */
const pendingAudioRef = ref(null)
const audioListRef = ref(null)
const previewing = ref(false)
const canPreview = computed(() => Boolean(rec.url) || audioAttachments.value.length > 0)
const previewEl = () => {
  const pending = pendingAudioRef.value
  if (rec.url && pending) return pending
  const root = audioListRef.value
  if (!root) return null
  const list = root.querySelectorAll('audio')
  return list.length ? list[list.length - 1] : null
}
const togglePreview = () => {
  const el = previewEl()
  if (!el) return
  // 再按一下 = 停；开始放之前也先把别的段停掉，一次只放一段
  if (previewing.value) { stopAudio(); return }
  stopAudio()
  el.onpause = () => { previewing.value = false }
  previewing.value = true
  try {
    const playing = el.play()
    // play() 的应答要等真有数据可放才回来：放不了（比如格式不支持）就把键复原，不能停在“停止”上
    if (playing && playing.catch) {
      playing.catch((e) => {
        previewing.value = false
        if (e && e.name === 'NotSupportedError') ElMessage.warning('这段语音当前浏览器放不了，可以删掉重录或换一段')
      })
    }
  } catch (e) {
    previewing.value = false
  }
}
/** 把弹窗里还在放的声音停掉，“试听”的键也跟着复原（关窗、切走、再按一下都用它） */
const stopAudio = () => {
  previewing.value = false
  const root = audioListRef.value
  if (!root) return
  root.querySelectorAll('audio').forEach((el) => el.pause())
}

const onFileChosen = async (event) => {
  const files = Array.from(event.target.files || [])
  event.target.value = ''
  if (!files.length) return
  const type = pendingPick.type === 'file' ? guessType(files[0]) : pendingPick.type
  const room = type === 'image' ? Math.max(0, imageLimit.value - images.value.length) : 9
  if (room <= 0) { ElMessage.warning(`图片最多 ${imageLimit.value} 张`); return }
  for (const f of files.slice(0, room)) {
    const item = await uploadOne(f, type === 'file' ? guessType(f) : type)
    if (item) attachments.value.push(item)
  }
}
const removeAttachment = (index) => {
  if (index < 0) return
  const item = attachments.value[index]
  if (item && item.previewUrl) URL.revokeObjectURL(item.previewUrl)
  attachments.value.splice(index, 1)
}

/* ---------------- 类型切换 / 重置 ---------------- */
const switchType = (value) => {
  form.scopeType = value
  form.sectionId = null
  form.manualSection = ''
  form.title = ''
  if (value === 'suishi') {
    form.bookId = null
    form.categoryCode = null
    form.bookTitle = null
  }
}

const resetAll = () => {
  clearRec()
  attachments.value.forEach((a) => a.previewUrl && URL.revokeObjectURL(a.previewUrl))
  attachments.value = []
  form.scopeType = props.defaultScope || 'songdu'
  form.channel = 'A'
  form.bookId = props.presetBookId ? Number(props.presetBookId) : null
  form.sectionId = null
  form.sectionMode = 'pick'
  form.manualSection = ''
  form.title = ''
  form.content = ''
  form.categoryCode = null
  form.bookTitle = null
}
/** 弹窗关上：先把还在放的声音停掉，再做原有的重置 */
const onDialogClosed = () => { stopAudio(); resetAll() }

/* ---------------- 提交 ---------------- */
const submit = async () => {
  const isSuishi = form.scopeType === 'suishi'
  const isZaidu = form.scopeType === 'zaidu'
  const isSongdu = form.scopeType === 'songdu'

  if (isSuishi) {
    if (!form.content.trim() && !attachments.value.length) { ElMessage.warning('随施写点内容吧（一句话也行）'); return }
  } else {
    if (!form.bookId) { ElMessage.warning('请先选择具体书目'); return }
    if (isZaidu) {
      if (form.sectionMode === 'pick' && !form.sectionId) { ElMessage.warning('请选择章节'); return }
      if (form.sectionMode === 'manual' && !form.manualSection.trim()) { ElMessage.warning('请填写章节名称'); return }
      if (!form.content.trim() && !attachments.value.length) { ElMessage.warning('再读一本请写下这一章的所得，或上传书页/语音'); return }
    } else if (isSongdu && form.channel === 'B') {
      if (!form.sectionId) { ElMessage.warning('B 通道请选择具体段落'); return }
    }
    if (!form.title.trim() && !form.content.trim() && !attachments.value.length) {
      ElMessage.warning('标题、正文、附件至少填一项'); return
    }
  }

  submitting.value = true
  try {
    let sectionId = null
    if (isZaidu || (isSongdu && form.channel === 'B')) {
      if (isZaidu && form.sectionMode === 'manual') {
        const { data } = await request.post(`/fabushi/books/${form.bookId}/sections/self`, { title: form.manualSection.trim() })
        sectionId = data && data.id
      } else {
        sectionId = form.sectionId
      }
    }
    const created = await createFabushiPost({
      scopeType: form.scopeType,
      bookId: isSuishi ? null : form.bookId,
      title: isSuishi ? null : (form.title.trim() || null),
      content: form.content.trim() || null,
      sectionId,
    })
    const postId = created.data && created.data.id
    for (const f of attachments.value) {
      await addFabushiAttachment(postId, {
        fileId: f.fileId,
        fileType: f.fileType,
        durationSeconds: f.fileType === 'audio' ? (rec.seconds || null) : null,
      })
    }
    ElMessage.success('发布成功')
    emit('published', postId)
    emit('update:modelValue', false)
  } catch (e) {
    // request 拦截器已弹错，这里不重复提示
  } finally {
    submitting.value = false
  }
}

watch(() => props.modelValue, async (visible) => {
  if (!visible) return
  resetAll()
  if (!books.value.length) {
    try {
      const { data } = await getFabushiBooks({ status: 'approved', current: 1, size: 500 })
      books.value = (data && data.records) || []
    } catch (e) {
      books.value = []
    }
  }
  if (form.bookId) await loadSections(form.bookId)
})

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
  try { mediaRecorder && mediaRecorder.stop() } catch (e) { /* ignore */ }
  stopAudio() // 弹窗开着就切走了：已经离开文档的播放器还会接着响，停掉
})
</script>

<style scoped>
/* ============================================================================
   版式照 PC 原型 v8 的“弹窗 / 表单”一段（.modal / .frm-* / .att-* / .edit-tool / .audio-seg / .pub-tool / .composer-*）。
   通用件（弹窗外壳 .fx-dialog、分段开关 .seg、版本按钮 .ver-chip）在 assets/fabushi-v8.css，这里只写发布器自己的部分。
   弹窗就渲染在页面里（没有 append-to-body），所以 .seg / .ver-chip 的样子和 Element Plus 的尺寸都从页面上继承得到。
   ============================================================================ */

/* ---------- 弹窗外壳：原型 .modal（宽度见 :width；最高 660px，标题与底部按钮固定，正文自己滚动） ---------- */
:global(.fx-pub-dialog.el-dialog) {
  --el-dialog-margin-top: 7vh;
  display: flex;
  flex-direction: column;
  max-height: min(660px, calc(100vh - 12vh));
  margin-bottom: 5vh;
}
:global(.fx-pub-dialog.el-dialog > .el-dialog__header) { flex-shrink: 0; cursor: default; }
:global(.fx-pub-dialog.el-dialog > .el-dialog__footer) { flex-shrink: 0; }
:global(.fx-pub-dialog.el-dialog > .el-dialog__body) { flex: 1 1 auto; min-height: 0; overflow-y: auto; }

/* 标题行：标题 · 副标题 · （给右上角关闭键留出的位置）；标题行高照原型，整条 52px */
.pub-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.pub-head .el-dialog__title { line-height: 21px; }
.pub-head-sub { overflow: hidden; color: var(--text-placeholder); font-size: 11.5px; font-weight: 400; line-height: 16px; text-overflow: ellipsis; white-space: nowrap; }
.pub-head-end { flex-shrink: 0; width: 36px; }

/* ---------- 正文 ---------- */
/* 表单控件照原型：高 34px、圆角 8px、字 13px（底部按钮在正文外面，仍是原型的 30px / 10px） */
.pub-body { --el-component-size: 34px; --el-border-radius-base: 8px; color: var(--text-primary); font-size: 13px; line-height: 1.5; text-align: left; }
.pub-type { margin-bottom: 12px; }
/* 直接摆在正文里的分段开关（通道 / 章节方式）：占内容宽度的一块，免得行内盒子带来多余空隙 */
.pub-body > .seg, .pub-type .seg { display: flex; width: max-content; }
/* 分段项 25px 高（原型：上下各 4px + 17px 的行） */
.pub-body .seg-item { line-height: 17px; }

/* 小标题与各种说明（原型 .frm-label / .frm-req / 弹窗里的轻说明）。
   行高写成量出来的明确值：原型的字体栈里写着 PingFang SC、行高是 normal（12.5px 的字行框 17px）；
   应用的字体栈里没有它、中文走回退字体，normal 会矮 2px，所以这里不写 normal。 */
.frm-label { display: flex; align-items: center; gap: 8px; margin: 12px 0 6px; color: var(--text-secondary); font-size: 12.5px; line-height: 17px; }
.pub-body > .frm-label:first-child { margin-top: 0; }
.frm-req { color: var(--el-color-danger); }
.frm-note { color: var(--text-placeholder); font-size: 11px; font-weight: 400; line-height: 16px; }
.frm-hint { margin: 6px 0 0; color: var(--text-muted); font-size: 11.5px; line-height: 1.6; }
.frm-hint.warn { color: var(--el-color-warning); }
.frm-foot { margin: 10px 0 0; color: var(--text-muted); font-size: 11px; line-height: 1.7; }
.frm-foot p { margin: 0; }

/* 按钮照原型的 .btn / .btn.sm：高 30 / 26px，字重 400（主按钮 600）；
   文字与描边的颜色只改“普通按钮”的 —— 主按钮(蓝 / 绿)的文字、底色、描边仍由 Element Plus 按类型给 */
.dialog-footer :deep(.el-button), .pub-body :deep(.el-button) { height: 30px; padding: 0 14px; }
.dialog-footer :deep(.el-button:not(.el-button--primary):not(.el-button--success)),
.pub-body :deep(.el-button:not(.el-button--primary):not(.el-button--success)) {
  --el-button-font-weight: 400; --el-button-text-color: var(--text-secondary); --el-button-border-color: var(--border-light);
}
.dialog-footer :deep(.el-button--primary), .dialog-footer :deep(.el-button--success),
.pub-body :deep(.el-button--primary), .pub-body :deep(.el-button--success) { --el-button-font-weight: 600; }
.pub-body :deep(.el-button--small) { height: 26px; padding: 0 11px; border-radius: 8px; }
.dialog-footer :deep(.el-button + .el-button) { margin-left: 0; }

/* 下拉与输入（原型 .frm-select / .frm-input / .frm-textarea） */
.triple { display: flex; gap: 8px; }
.triple > .pub-select { flex: 1; min-width: 0; }
.triple > .pub-select:last-child { flex: 1.3; }
.pub-select--full { width: 100%; }
.seg-after { margin-top: 6px; }
.pub-body :deep(.el-select__wrapper) { min-height: 34px; font-size: 13px; }
.pub-body :deep(.el-input__inner),
.pub-body :deep(.el-textarea__inner),
.pub-body :deep(.el-select__placeholder),
.pub-body :deep(.el-select__selected) { font-size: 13px; }
.pub-body :deep(.el-input__inner),
.pub-body :deep(.el-textarea__inner),
.pub-body :deep(.el-select__selected) { color: var(--text-primary); font-family: inherit; }
.frm-textarea :deep(.el-textarea__inner) { height: 100px; min-height: 100px; padding: 10px 12px; line-height: 1.8; resize: none; }
.frm-textarea--tall :deep(.el-textarea__inner) { height: 106px; }
/* 标题是可选项：放在“诵读素材”小标题这一行的右端，不单独占一行 */
.frm-title { flex-shrink: 0; width: 260px; --el-component-size: 26px; }
.frm-title :deep(.el-input__inner) { font-size: 12.5px; }

/* 版本切换（原型 .bcard-tags + .ver-chip；按钮自己的样子在公共样式里） */
.bcard-tags { display: flex; align-items: center; flex-wrap: wrap; gap: 6px; margin-top: 6px; }
.ver-label { color: var(--text-placeholder); font-size: 10.5px; line-height: 15px; }
/* 【FABUSHI-DELTA-20261128 R3/S4】打开这本书（新标签页进阅读器，对照诵读） */
.open-book-link {
  display: inline-flex; align-items: center; gap: 4px; margin-top: 8px; padding: 2px 0;
  border: none; background: none; color: var(--fx-accent, var(--el-color-primary)); font-size: 11.5px; cursor: pointer;
}
.open-book-link:hover { text-decoration: underline; }

/* 图片与上传入口（原型 .att-row / .att-tile） */
.att-row { display: flex; gap: 10px; }
.att-tile {
  display: flex; flex: 1; flex-direction: column; align-items: center; justify-content: center; gap: 6px;
  height: 74px; min-width: 0; padding: 0; border: 1px dashed var(--border-light); border-radius: 8px;
  background: var(--fx-table-head); color: var(--text-placeholder); font-size: 11.5px; line-height: 16px; font-family: inherit;
}
button.att-tile { cursor: pointer; }
button.att-tile:hover { border-color: var(--primary-color); color: var(--primary-color); }
.att-tile.on { border-style: solid; border-color: var(--el-color-danger); color: var(--el-color-danger); }
.att-tile :deep(.el-icon) { font-size: 18px; }
.att-tile .att-count { font-size: 11px; line-height: 1.2; }
/* 已选图片：小块里就是这张图，底下一条写“已选 N 张 / 第 N 张” */
.att-tile.picked { position: relative; overflow: hidden; border-style: solid; border-color: var(--primary-color); }
.att-tile.picked.current, .composer-cell.img.current { outline: 2px solid var(--el-color-primary-light-5); outline-offset: 1px; }
.cell-pick { position: absolute; inset: 0; width: 100%; height: 100%; padding: 0; border: 0; background: none; cursor: pointer; font-family: inherit; }
.att-cap {
  position: absolute; right: 0; bottom: 0; left: 0; padding: 2px 6px;
  background: linear-gradient(180deg, transparent, rgba(0, 0, 0, .55)); color: #fff; font-size: 10.5px; line-height: 1.4; text-align: center; pointer-events: none;
}
/* 图上的小键：右上移除、左下放大 */
.cell-x, .cell-zoom, .chip-x {
  display: inline-flex; align-items: center; justify-content: center; padding: 0; border: 0; background: transparent; color: inherit; cursor: pointer;
}
.cell-x, .cell-zoom { position: absolute; z-index: 1; width: 20px; height: 20px; border-radius: 50%; background: rgba(0, 0, 0, .5); color: #fff; }
.cell-x { top: 4px; right: 4px; }
.cell-zoom { bottom: 4px; left: 4px; border-radius: 4px; }
.cell-x :deep(.el-icon), .cell-zoom :deep(.el-icon) { font-size: 12px; }
.cell-x:hover { background: var(--el-color-danger); }

/* 图片编辑工具（原型 .edit-tools / .edit-tool） */
.edit-tools { display: flex; align-items: center; gap: 8px; margin-top: 8px; }
.edit-tools-label { color: var(--text-placeholder); font-size: 10.5px; line-height: 15px; }
.edit-tool {
  display: inline-flex; align-items: center; gap: 5px; height: 26px; padding: 0 10px; border: 1px solid var(--border-light);
  border-radius: var(--radius-pill); background: #fff; color: var(--text-secondary); font-size: 11.5px; font-family: inherit; cursor: pointer;
}
.edit-tool:hover:not(:disabled), .edit-tool.on { border-color: var(--primary-color); color: var(--primary-color); }
.edit-tool:disabled { opacity: .5; cursor: not-allowed; }
.edit-hint { margin-left: 4px; color: var(--text-muted); font-size: 11.5px; }

/* 语音行（原型 .audio-seg / .recite-wave） */
.audio-seg {
  display: flex; align-items: center; gap: 10px; padding: 9px 12px; border: 1px solid var(--border-light); border-radius: 8px;
  background: var(--fx-table-head); color: var(--text-secondary); font-size: 12px; line-height: 17px; --el-border-radius-base: 8px;
}
.audio-seg :deep(.el-button + .el-button) { margin-left: 0; }
.audio-seg-text { white-space: nowrap; }
.audio-seg.live .audio-seg-text { color: var(--el-color-danger); font-weight: 600; }
.recite-wave { display: flex; align-items: flex-end; gap: 2px; width: 34px; height: 16px; flex-shrink: 0; }
.recite-wave i { width: 3px; border-radius: 2px; background: var(--primary-color); opacity: .7; transform-origin: bottom; }
.audio-seg.live .recite-wave i { background: var(--el-color-danger); animation: pub-wave .9s ease-in-out infinite alternate; }
.audio-seg.live .recite-wave i:nth-child(2n) { animation-delay: .3s; }
.audio-seg.live .recite-wave i:nth-child(3n) { animation-delay: .6s; }
@keyframes pub-wave { from { transform: scaleY(.4); } to { transform: scaleY(1); } }
@media (prefers-reduced-motion: reduce) { .audio-seg.live .recite-wave i { animation: none; } }
/* 刚录的一段（还没保存）与已存语音列表 */
.audio-block { margin-top: 8px; }
.audio-pending { display: flex; align-items: center; gap: 8px; padding: 5px 8px; border: 1px solid var(--border-light); border-radius: var(--radius-sm); background: var(--el-fill-color-lighter); }
.audio-pending :deep(.el-button + .el-button) { margin-left: 0; }
.audio-el { flex: 1; height: 30px; min-width: 0; }
.audio-name { flex-shrink: 0; color: var(--text-muted); font-size: 10.5px; line-height: 15px; }

/* 视频 / 文件的胶囊 */
.file-chips { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 8px; }
.file-chip {
  display: inline-flex; align-items: center; gap: 4px; max-width: 100%; padding: 2px 4px 2px 8px;
  border: 1px solid var(--border-light); border-radius: var(--radius-pill); background: var(--fx-pill-gray-bg); color: var(--text-secondary); font-size: 11px; line-height: 16px;
}
.file-chip :deep(.el-icon) { flex-shrink: 0; font-size: 12px; }
.file-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.chip-x { width: 16px; height: 16px; border-radius: 50%; color: var(--text-muted); }
.chip-x :deep(.el-icon) { font-size: 10px; }
.chip-x:hover { color: var(--el-color-danger); background: var(--el-color-danger-light-9); }

/* 九宫格（原型 .composer-grid / .composer-cell） */
.composer-grid { display: grid; grid-template-columns: repeat(3, 78px); gap: 6px; margin-top: 8px; }
.composer-cell { position: relative; height: 78px; padding: 0; border: 0; border-radius: 6px; overflow: hidden; background: var(--fx-track); font-family: inherit; }
.composer-cell .cell-pick img, .composer-cell.img > img { display: block; width: 100%; height: 100%; object-fit: cover; }
.composer-cell.add {
  display: flex; flex-direction: column; align-items: center; justify-content: center; border: 1px dashed var(--border-light);
  background: var(--fx-table-head); color: var(--text-placeholder); font-size: 24px; font-weight: 300; line-height: normal; cursor: pointer;
}
.composer-cell.add:hover { border-color: var(--primary-color); color: var(--primary-color); }
.add-count { margin-top: 3px; font-size: 10.5px; font-weight: 400; line-height: 1.3; }

/* 随施的工具格子（原型 .pub-tools / .pub-tool）：现有功能是 5 个，排成一行 */
.pub-tools { display: grid; grid-template-columns: repeat(5, 1fr); gap: 8px; margin-top: 10px; }
.pub-tool {
  display: flex; flex-direction: column; align-items: center; gap: 5px; padding: 10px 0 8px; border: 1px solid var(--border-light);
  border-radius: var(--radius-md); background: var(--fx-table-head); color: var(--text-secondary); font-size: 11px; line-height: 16px; font-family: inherit; cursor: pointer;
}
.pub-tool:hover { border-color: var(--primary-color); color: var(--primary-color); }
.pub-tool.on { border-color: var(--el-color-danger); color: var(--el-color-danger); }
.pub-tool .pt-ico { display: flex; align-items: center; justify-content: center; width: 26px; height: 26px; border-radius: 8px; background: #fff; }
.pub-tool :deep(.el-icon) { font-size: 17px; }

/* 归档提示（原型里那条淡蓝底的说明） */
.pub-archive {
  display: flex; align-items: flex-start; gap: 6px; margin: 8px 0 0; padding: 8px 12px; border-radius: 8px;
  background: var(--el-color-primary-light-9); color: var(--brand-navy); font-size: 11px; line-height: 1.7;
}
.pub-archive :deep(.el-icon) { flex-shrink: 0; margin-top: 3px; font-size: 12px; }
.pub-archive b { font-weight: 700; }

/* 图片编辑（裁剪 / 标注）弹窗：它挂在 body 下，所以这里只用全局令牌 */
.editor-stage { display: flex; justify-content: center; align-items: center; min-height: 240px; background: #111; border-radius: 8px; overflow: hidden; }
.editor-stage canvas { display: block; max-width: 100%; cursor: crosshair; }
</style>
