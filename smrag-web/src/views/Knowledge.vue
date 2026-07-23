<template>
  <section class="knowledge-page">
    <header class="page-header">
      <div>
        <h1>知识库</h1>
        <p>按业务范围组织文档，并用现有角色控制访问。</p>
      </div>
      <el-button v-if="authStore.isAdmin" type="primary" @click="startCreate">
        <el-icon><Plus /></el-icon>
        新建知识库
      </el-button>
    </header>

    <div class="workspace">
      <aside class="base-navigation" aria-label="知识库列表">
        <button
          v-for="base in visibleBases"
          :key="base.id"
          type="button"
          :class="['base-item', { active: base.code === knowledgeBaseCode }]"
          @click="selectBase(base.code)"
        >
          <span class="base-mark">{{ base.name.slice(0, 1) }}</span>
          <span class="base-copy">
            <strong>{{ base.name }}</strong>
            <small>{{ base.documentCount ?? 0 }} 份文档</small>
          </span>
          <span v-if="base.status === 'DISABLED'" class="disabled-label">停用</span>
        </button>

        <div v-if="visibleBases.length === 0" class="navigation-empty">
          <el-icon><Collection /></el-icon>
          <strong>还没有知识库</strong>
          <span v-if="authStore.isAdmin">创建第一个知识库后即可上传文档。</span>
          <span v-else>请联系管理员为你的角色授权。</span>
        </div>
      </aside>

      <main class="base-content">
        <section v-if="editorMode" class="base-editor" aria-labelledby="editor-title">
          <div class="section-title">
            <div>
              <span class="eyebrow">{{ editorMode === 'create' ? '创建' : '设置' }}</span>
              <h2 id="editor-title">
                {{ editorMode === 'create' ? '新建知识库' : `设置 ${selectedBase?.name}` }}
              </h2>
            </div>
            <el-button text @click="editorMode = ''">收起</el-button>
          </div>
          <div class="editor-grid">
            <label>
              <span>名称</span>
              <el-input v-model="baseForm.name" maxlength="128" />
            </label>
            <label v-if="editorMode === 'create'">
              <span>编码，创建后不可修改</span>
              <el-input v-model="baseForm.code" maxlength="64" placeholder="research-materials" />
            </label>
            <label class="full-row">
              <span>说明</span>
              <el-input
                v-model="baseForm.description"
                type="textarea"
                :rows="2"
                maxlength="500"
              />
            </label>
            <fieldset class="full-row">
              <legend>允许访问的角色</legend>
              <el-checkbox-group v-model="baseForm.roleIds" class="role-options">
                <el-checkbox
                  v-for="role in configurableRoles"
                  :key="role.id"
                  :value="role.id"
                >
                  {{ role.roleName }}（{{ role.roleCode }}）
                </el-checkbox>
              </el-checkbox-group>
            </fieldset>
          </div>
          <div class="editor-actions">
            <el-button type="primary" :loading="savingBase" @click="saveBase">
              {{ editorMode === 'create' ? '创建知识库' : '保存设置' }}
            </el-button>
            <template v-if="editorMode === 'edit' && selectedBase">
              <el-button
                :type="selectedBase.status === 'ACTIVE' ? 'warning' : 'success'"
                plain
                @click="toggleStatus"
              >
                {{ selectedBase.status === 'ACTIVE' ? '停用知识库' : '启用知识库' }}
              </el-button>
              <el-button
                type="danger"
                plain
                :disabled="selectedBase.documentCount > 0"
                @click="removeSelectedBase"
              >
                删除知识库
              </el-button>
              <span v-if="selectedBase.documentCount > 0" class="delete-hint">
                请先迁移或删除库内文档
              </span>
            </template>
          </div>
        </section>

        <template v-else-if="selectedBase">
          <header class="base-header">
            <div>
              <div class="title-line">
                <h2>{{ selectedBase.name }}</h2>
                <el-tag v-if="selectedBase.status === 'DISABLED'" type="warning">已停用</el-tag>
              </div>
              <p>{{ selectedBase.description || '暂无说明' }}</p>
              <div class="base-roles">
                <span>库权限</span>
                <el-tag
                  v-for="role in selectedBase.roles || []"
                  :key="role.id"
                  size="small"
                  effect="plain"
                >
                  {{ role.roleName }}
                </el-tag>
              </div>
            </div>
            <div class="header-actions">
              <el-button
                text
                :loading="loading"
                aria-label="刷新文档"
                @click="fetchDocuments()"
              >
                <el-icon><Refresh /></el-icon>
                刷新
              </el-button>
              <el-button v-if="authStore.isAdmin" @click="startEdit">知识库设置</el-button>
            </div>
          </header>

          <section
            v-if="authStore.isAdmin"
            class="upload-strip"
            :class="{ disabled: selectedBase.status !== 'ACTIVE' }"
          >
            <div>
              <strong>上传原始文档</strong>
              <span v-if="selectedBase.status === 'ACTIVE'">
                PDF、Word 或 TXT，解析后自动进入授权检索
              </span>
              <span v-else>知识库已停用，启用后才能上传。</span>
            </div>
            <div class="upload-controls">
              <el-select
                v-model="uploadRoleCodes"
                multiple
                collapse-tags
                placeholder="文档角色"
                :disabled="selectedBase.status !== 'ACTIVE'"
              >
                <el-option
                  v-for="role in selectedBase.roles || []"
                  :key="role.id"
                  :label="role.roleName"
                  :value="role.roleCode"
                />
              </el-select>
              <el-upload
                :auto-upload="false"
                :show-file-list="false"
                :on-change="handleFileChange"
                accept=".pdf,.doc,.docx,.txt"
                :disabled="selectedBase.status !== 'ACTIVE'"
              >
                <template #trigger>
                  <el-button :disabled="selectedBase.status !== 'ACTIVE'">
                    {{ selectedFile?.name || '选择文件' }}
                  </el-button>
                </template>
              </el-upload>
              <el-button
                type="primary"
                :loading="uploading"
                :disabled="!selectedFile || uploadRoleCodes.length === 0 || selectedBase.status !== 'ACTIVE'"
                @click="handleUpload"
              >
                上传
              </el-button>
            </div>
          </section>

          <section class="documents-section" aria-labelledby="documents-title">
            <div class="section-title">
              <div>
                <span class="eyebrow">内容</span>
                <h2 id="documents-title">文档</h2>
              </div>
              <span>{{ documents.length }} 份</span>
            </div>
            <el-table
              :data="documents"
              v-loading="loading"
              empty-text="这个知识库还没有文档"
            >
              <el-table-column label="文档" min-width="240">
                <template #default="{ row }">
                  <button
                    class="document-link"
                    type="button"
                    :disabled="!row.accessible"
                    @click="viewerDocument = row"
                  >
                    <el-icon><Document /></el-icon>
                    <span>
                      <strong>{{ row.name }}</strong>
                      <small>#{{ row.id }} · {{ row.fileType }}</small>
                    </span>
                  </button>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="120">
                <template #default="{ row }">
                  <el-tag :type="statusType(row.status)" effect="plain">
                    {{ statusLabel(row.status) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="文档权限" min-width="220">
                <template #default="{ row }">
                  <div class="permission-cell">
                    <el-tag :type="row.accessible ? 'success' : 'info'" size="small">
                      {{ row.accessible ? '可阅读' : '无阅读权限' }}
                    </el-tag>
                    <span>
                      {{ filterConfigurableAccessRoles(row.allowedRoles)
                        .map(role => role.roleName).join('、') || '未配置' }}
                    </span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column v-if="authStore.isAdmin" label="操作" width="230" fixed="right">
                <template #default="{ row }">
                  <div class="row-actions">
                    <el-button text @click="startPermissionEdit(row)">权限</el-button>
                    <el-button text @click="startMove(row)">迁移</el-button>
                    <el-button text type="danger" @click="removeDocument(row)">删除</el-button>
                  </div>
                </template>
              </el-table-column>
            </el-table>
          </section>
        </template>

        <section v-else class="content-empty">
          <el-icon><Collection /></el-icon>
          <h2>{{ authStore.isAdmin ? '创建第一个知识库' : '暂无可访问的知识库' }}</h2>
          <p>
            {{ authStore.isAdmin
              ? '知识库创建后，再按角色上传和管理文档。'
              : '你的当前角色还没有被授权到任何知识库。' }}
          </p>
          <el-button v-if="authStore.isAdmin" type="primary" @click="startCreate">
            新建知识库
          </el-button>
        </section>
      </main>
    </div>

    <el-dialog v-model="permissionDialog" title="调整文档权限" width="460px">
      <p class="dialog-note">文档角色只能从当前知识库已授权的角色中选择。</p>
      <el-checkbox-group v-model="editingRoleCodes" class="dialog-roles">
        <el-checkbox
          v-for="role in selectedBase?.roles || []"
          :key="role.id"
          :value="role.roleCode"
        >
          {{ role.roleName }}（{{ role.roleCode }}）
        </el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="permissionDialog = false">取消</el-button>
        <el-button type="primary" @click="saveDocumentPermissions">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="moveDialog" title="迁移文档" width="460px">
      <p class="dialog-note">
        只显示已启用的目标知识库。目标库必须包含这份文档的全部角色。
      </p>
      <el-select v-model="moveTargetCode" placeholder="选择目标知识库" style="width: 100%">
        <el-option
          v-for="base in moveTargets"
          :key="base.id"
          :label="base.name"
          :value="base.code"
        />
      </el-select>
      <template #footer>
        <el-button @click="moveDialog = false">取消</el-button>
        <el-button type="primary" :disabled="!moveTargetCode" @click="confirmMove">
          确认迁移
        </el-button>
      </template>
    </el-dialog>

    <DocumentViewer :document="viewerDocument" @close="viewerDocument = null" />
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { Collection, Document, Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import DocumentViewer from '@/components/DocumentViewer.vue'
import { getRoles } from '@/api/role'
import { useAuthStore } from '@/stores/auth'
import { useKnowledgeStore } from '@/stores/knowledge'
import { filterConfigurableAccessRoles } from '@/constants/knowledgeBase'

const authStore = useAuthStore()
const knowledgeStore = useKnowledgeStore()
const {
  documents,
  loading,
  knowledgeBases,
  adminKnowledgeBases,
  knowledgeBaseCode
} = storeToRefs(knowledgeStore)

const roles = ref([])
const editorMode = ref('')
const savingBase = ref(false)
const selectedFile = ref(null)
const uploadRoleCodes = ref([])
const uploading = ref(false)
const viewerDocument = ref(null)
const permissionDialog = ref(false)
const editingDocument = ref(null)
const editingRoleCodes = ref([])
const moveDialog = ref(false)
const movingDocument = ref(null)
const moveTargetCode = ref('')
const baseForm = reactive({ code: '', name: '', description: '', roleIds: [] })
const configurableRoles = computed(() =>
  filterConfigurableAccessRoles(roles.value)
)

const visibleBases = computed(() =>
  authStore.isAdmin ? adminKnowledgeBases.value : knowledgeBases.value
)
const selectedBase = computed(() =>
  visibleBases.value.find(base => base.code === knowledgeBaseCode.value) || null
)
const moveTargets = computed(() =>
  adminKnowledgeBases.value.filter(base =>
    base.status === 'ACTIVE' && base.code !== knowledgeBaseCode.value
  )
)

watch(selectedBase, base => {
  uploadRoleCodes.value = (base?.roles || []).map(role => role.roleCode)
})

onMounted(async () => {
  try {
    const requests = [knowledgeStore.fetchKnowledgeBases()]
    if (authStore.isAdmin) {
      requests.push(knowledgeStore.fetchAdminKnowledgeBases(), getRoles())
    }
    const results = await Promise.all(requests)
    if (authStore.isAdmin) roles.value = results.at(-1) || []
    if (knowledgeBaseCode.value) await knowledgeStore.fetchDocuments()
  } catch {
    ElMessage.error('知识库加载失败')
  }
})

const selectBase = async code => {
  editorMode.value = ''
  knowledgeBaseCode.value = code
  await knowledgeStore.fetchDocuments(code)
}

const resetForm = () => Object.assign(baseForm, {
  code: '',
  name: '',
  description: '',
  roleIds: []
})

const startCreate = async () => {
  if (!roles.value.length) roles.value = await getRoles()
  resetForm()
  editorMode.value = 'create'
}

const startEdit = async () => {
  if (!roles.value.length) roles.value = await getRoles()
  Object.assign(baseForm, {
    code: selectedBase.value.code,
    name: selectedBase.value.name,
    description: selectedBase.value.description || '',
    roleIds: (selectedBase.value.roles || []).map(role => role.id)
  })
  editorMode.value = 'edit'
}

const saveBase = async () => {
  if (!baseForm.name.trim() || baseForm.roleIds.length === 0) {
    ElMessage.warning('请填写名称并至少选择一个角色')
    return
  }
  savingBase.value = true
  try {
    if (editorMode.value === 'create') {
      await knowledgeStore.createBase({
        code: baseForm.code.trim().toLowerCase(),
        name: baseForm.name.trim(),
        description: baseForm.description.trim(),
        roleIds: baseForm.roleIds
      })
      knowledgeBaseCode.value =
        adminKnowledgeBases.value.find(base => base.code === baseForm.code.trim().toLowerCase())?.code
        || knowledgeBaseCode.value
      ElMessage.success('知识库已创建')
    } else {
      const editedBaseId = selectedBase.value.id
      const editedBaseCode = selectedBase.value.code
      const editedBaseVersion = selectedBase.value.version
      await knowledgeStore.updateBase(editedBaseId, {
        name: baseForm.name.trim(),
        description: baseForm.description.trim(),
        version: editedBaseVersion
      })
      const refreshed = adminKnowledgeBases.value.find(base => base.id === editedBaseId)
      if (!refreshed) {
        throw new Error('知识库已不存在，请刷新后重试')
      }
      await knowledgeStore.updateBaseRoles(editedBaseId, {
        roleIds: baseForm.roleIds,
        version: refreshed.version
      })
      knowledgeBaseCode.value = editedBaseCode
      ElMessage.success('知识库设置已保存')
    }
    editorMode.value = ''
    if (knowledgeBaseCode.value) await knowledgeStore.fetchDocuments()
  } finally {
    savingBase.value = false
  }
}

const toggleStatus = async () => {
  const next = selectedBase.value.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  await knowledgeStore.updateBaseStatus(selectedBase.value.id, {
    status: next,
    version: selectedBase.value.version
  })
  ElMessage.success(next === 'ACTIVE' ? '知识库已启用' : '知识库已停用')
  editorMode.value = ''
}

const removeSelectedBase = async () => {
  await ElMessageBox.confirm(
    `确认删除空知识库“${selectedBase.value.name}”？`,
    '删除知识库',
    { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
  )
  await knowledgeStore.removeBase(selectedBase.value.id)
  editorMode.value = ''
  ElMessage.success('知识库已删除')
}

const handleFileChange = uploadFile => {
  selectedFile.value = uploadFile.raw
}

const handleUpload = async () => {
  uploading.value = true
  try {
    await knowledgeStore.upload(
      selectedFile.value,
      selectedBase.value.code,
      uploadRoleCodes.value
    )
    selectedFile.value = null
    ElMessage.success('文档已上传，正在解析')
  } finally {
    uploading.value = false
  }
}

const startPermissionEdit = document => {
  editingDocument.value = document
  editingRoleCodes.value = filterConfigurableAccessRoles(document.allowedRoles)
    .map(role => role.roleCode)
  permissionDialog.value = true
}

const saveDocumentPermissions = async () => {
  if (editingRoleCodes.value.length === 0) {
    ElMessage.warning('文档至少需要一个可读角色')
    return
  }
  await knowledgeStore.updatePermissions(editingDocument.value.id, editingRoleCodes.value)
  permissionDialog.value = false
  ElMessage.success('文档权限已更新')
}

const startMove = document => {
  movingDocument.value = document
  moveTargetCode.value = ''
  moveDialog.value = true
}

const confirmMove = async () => {
  await knowledgeStore.transferDocument(movingDocument.value.id, moveTargetCode.value)
  moveDialog.value = false
  ElMessage.success('文档已迁移')
}

const removeDocument = async document => {
  await ElMessageBox.confirm(
    `确认删除“${document.name}”及其向量索引？`,
    '删除文档',
    { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
  )
  await knowledgeStore.remove(document.id)
  ElMessage.success('文档已删除')
}

const statusLabel = status => ({
  PENDING: '待处理',
  PROCESSING: '处理中',
  COMPLETED: '已完成',
  FAILED: '失败',
  DELETING: '删除中'
}[status] || status)
const statusType = status => ({
  COMPLETED: 'success',
  FAILED: 'danger',
  PROCESSING: 'warning'
}[status] || 'info')
const fetchDocuments = () => knowledgeStore.fetchDocuments()
</script>

<style scoped>
.knowledge-page {
  min-height: 100%;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  background: var(--bg-primary);
}

.page-header {
  min-height: 84px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 16px clamp(20px, 4vw, 48px);
  border-bottom: 1px solid var(--border);
  background: var(--surface);
}

.page-header h1,
.base-header h2,
.section-title h2,
.content-empty h2 {
  margin: 0;
  color: var(--text-primary);
}

.page-header h1 { font-size: 1.3rem; }
.page-header p,
.base-header p,
.content-empty p {
  margin: 4px 0 0;
  color: var(--text-muted);
  font-size: 0.82rem;
}

.workspace {
  min-height: 0;
  display: grid;
  grid-template-columns: 252px minmax(0, 1fr);
}

.base-navigation {
  min-height: 0;
  overflow-y: auto;
  padding: 14px 10px;
  border-right: 1px solid var(--border);
  background: var(--surface);
}

.base-item {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  color: var(--text-primary);
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.base-item:hover { background: var(--surface-raised); }
.base-item.active {
  border-color: var(--border-strong);
  background: var(--accent-soft);
}

.base-mark {
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  border-radius: 7px;
  background: var(--surface-raised);
  color: var(--accent);
  font-weight: 700;
}

.base-copy {
  min-width: 0;
  display: flex;
  flex: 1;
  flex-direction: column;
}

.base-copy strong {
  overflow: hidden;
  font-size: 0.86rem;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.base-copy small,
.disabled-label {
  color: var(--text-muted);
  font-size: 0.7rem;
}

.disabled-label { color: var(--el-color-warning); }

.navigation-empty,
.content-empty {
  display: grid;
  justify-items: center;
  gap: 8px;
  color: var(--text-muted);
  text-align: center;
}

.navigation-empty {
  padding: 42px 14px;
  font-size: 0.78rem;
}

.navigation-empty .el-icon,
.content-empty .el-icon {
  color: var(--accent);
  font-size: 1.8rem;
}

.base-content {
  min-width: 0;
  min-height: 0;
  overflow: auto;
  padding: clamp(20px, 3vw, 40px);
}

.content-empty {
  min-height: 60vh;
  place-content: center;
}

.base-header,
.section-title,
.upload-strip,
.editor-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}

.base-header {
  padding-bottom: 22px;
  border-bottom: 1px solid var(--border);
}

.title-line,
.header-actions,
.base-roles,
.row-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.base-roles {
  flex-wrap: wrap;
  margin-top: 12px;
  color: var(--text-muted);
  font-size: 0.75rem;
}

.upload-strip {
  margin: 24px 0 34px;
  padding: 16px 0;
  border-top: 1px solid var(--border);
  border-bottom: 1px solid var(--border);
}

.upload-strip > div:first-child {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.upload-strip span,
.dialog-note,
.delete-hint {
  color: var(--text-muted);
  font-size: 0.76rem;
}

.upload-strip.disabled { opacity: 0.68; }

.upload-controls {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

.upload-controls .el-select { width: 190px; }

.documents-section { margin-top: 28px; }
.section-title { margin-bottom: 12px; }
.section-title h2 { font-size: 1.05rem; }
.section-title > span { color: var(--text-muted); font-size: 0.8rem; }

.eyebrow {
  color: var(--accent);
  font-size: 0.68rem;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.document-link {
  max-width: 100%;
  display: inline-flex;
  align-items: center;
  gap: 9px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--text-primary);
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.document-link:disabled {
  color: var(--text-muted);
  cursor: not-allowed;
}

.document-link > span {
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.document-link strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.document-link small,
.permission-cell span {
  color: var(--text-muted);
  font-size: 0.72rem;
}

.permission-cell {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 7px;
}

.base-editor {
  max-width: 820px;
}

.editor-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 18px;
  margin-top: 24px;
}

.editor-grid label {
  display: grid;
  gap: 7px;
  color: var(--text-secondary);
  font-size: 0.8rem;
}

.full-row { grid-column: 1 / -1; }

.editor-grid fieldset {
  margin: 0;
  padding: 14px;
  border: 1px solid var(--border);
  border-radius: 8px;
}

.editor-grid legend {
  padding: 0 6px;
  color: var(--text-secondary);
  font-size: 0.8rem;
}

.role-options,
.dialog-roles {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px;
}

.editor-actions {
  justify-content: flex-start;
  margin-top: 22px;
}

.dialog-note { margin: 0 0 16px; }

@media (max-width: 800px) {
  .workspace { grid-template-columns: 1fr; }
  .base-navigation {
    display: flex;
    gap: 6px;
    overflow-x: auto;
    border-right: 0;
    border-bottom: 1px solid var(--border);
  }
  .base-item { min-width: 180px; }
  .navigation-empty { min-width: 100%; padding: 20px; }
  .base-header,
  .upload-strip { align-items: flex-start; flex-direction: column; }
  .upload-controls { width: 100%; justify-content: flex-start; flex-wrap: wrap; }
}

@media (max-width: 560px) {
  .page-header p { display: none; }
  .base-content { padding: 18px 14px; }
  .editor-grid { grid-template-columns: 1fr; }
  .full-row { grid-column: auto; }
  .role-options,
  .dialog-roles { grid-template-columns: 1fr; }
}
</style>
