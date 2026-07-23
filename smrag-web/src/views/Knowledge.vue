<template>
  <section class="knowledge-page">
    <header class="page-toolbar">
      <div>
        <h1>知识库</h1>
        <p>{{ documents.length }} 份文档</p>
      </div>
      <div class="toolbar-actions">
        <label for="knowledge-base">知识库</label>
        <el-input
          id="knowledge-base"
          v-model="knowledgeBaseId"
          size="small"
          maxlength="64"
          aria-label="知识库 ID"
          @change="fetchDocuments"
        />
        <el-tooltip content="刷新文档列表" placement="bottom">
          <el-button
            text
            circle
            aria-label="刷新文档列表"
            :loading="loading"
            @click="fetchDocuments"
          >
            <el-icon v-if="!loading"><Refresh /></el-icon>
          </el-button>
        </el-tooltip>
      </div>
    </header>

    <div class="knowledge-content">
      <!-- 上传区域 -->
      <section v-if="authStore.isAdmin" class="upload-section" aria-labelledby="upload-title">
        <div class="upload-header">
          <div class="upload-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
              <polyline points="17 8 12 3 7 8"/>
              <line x1="12" y1="3" x2="12" y2="15"/>
            </svg>
          </div>
          <div class="upload-title-group">
            <h2 id="upload-title">上传文档</h2>
            <p>支持 PDF、DOC、DOCX、TXT 格式，单个文件不超过 20 MB</p>
          </div>
        </div>

        <!-- 已选文件 -->
        <div v-if="selectedFile" class="selected-file-card">
          <div class="file-info">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
              <polyline points="14 2 14 8 20 8"/>
            </svg>
            <div class="file-meta">
              <span class="file-name">{{ selectedFile.name }}</span>
              <span class="file-size">{{ formatFileSize(selectedFile.size) }}</span>
            </div>
          </div>
          <button class="remove-file-btn" @click="clearSelectedFile">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"/>
              <line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </button>
        </div>

        <!-- 权限选择 -->
        <div class="permission-section">
          <label class="permission-label">访问权限</label>
          <div class="role-checkbox-group">
            <label class="role-checkbox">
              <input type="checkbox" v-model="selectedRoles" value="ADMIN" />
              <span class="checkbox-custom admin"></span>
              <span class="role-name">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
                </svg>
                管理员
              </span>
            </label>
            <label class="role-checkbox">
              <input type="checkbox" v-model="selectedRoles" value="USER" />
              <span class="checkbox-custom user"></span>
              <span class="role-name">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
                  <circle cx="12" cy="7" r="4"/>
                </svg>
                普通用户
              </span>
            </label>
            <label
              v-for="role in customRoles"
              :key="role.roleCode"
              class="role-checkbox"
            >
              <input type="checkbox" v-model="selectedRoles" :value="role.roleCode" />
              <span class="checkbox-custom user"></span>
              <span class="role-name">{{ role.roleName }}</span>
            </label>
          </div>
          <p class="permission-hint">选择可以访问该文档的角色，至少选择一个</p>
        </div>

        <!-- 上传操作 -->
        <div class="upload-actions">
          <el-upload
            ref="uploadRef"
            :auto-upload="false"
            :on-change="handleFileChange"
            :show-file-list="false"
            accept=".pdf,.doc,.docx,.txt"
          >
            <template #trigger>
              <button type="button" class="select-file-btn">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/>
                </svg>
                选择文件
              </button>
            </template>
          </el-upload>
          <button
            class="upload-btn"
            :disabled="!selectedFile || selectedRoles.length === 0"
            :loading="uploading"
            @click="handleUpload"
          >
            <svg v-if="!uploading" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
              <polyline points="17 8 12 3 7 8"/>
              <line x1="12" y1="3" x2="12" y2="15"/>
            </svg>
            {{ uploading ? '上传中...' : '开始上传' }}
          </button>
        </div>
      </section>

      <!-- 文档列表 -->
      <section class="document-workspace" aria-labelledby="documents-title">
        <div class="section-heading">
          <div>
            <h2 id="documents-title">文档列表</h2>
            <p>处理完成的内容可用于检索与引用</p>
          </div>
          <span class="status-legend">
            <span class="status-dot completed"></span>
            {{ completedCount }} 已完成
          </span>
        </div>

        <div class="table-scroll">
          <SkeletonTable
            v-if="loading && documents.length === 0"
            :rows="6"
          />
          <el-table
            v-else
            :data="documents"
            v-loading="loading && documents.length > 0"
            class="documents-table"
            empty-text="暂无文档"
          >
            <el-table-column prop="name" label="文档" min-width="240">
              <template #default="{ row }">
                <div class="document-name">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                    <polyline points="14 2 14 8 20 8"/>
                  </svg>
                  <div>
                    <strong>{{ row.name }}</strong>
                    <span>#{{ row.id }}</span>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="fileType" label="类型" width="90" />
            <el-table-column prop="status" label="状态" width="130">
              <template #default="{ row }">
                <span class="document-status" :class="statusClass(row.status)">
                  <span class="status-dot"></span>
                  {{ statusLabel(row.status) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="访问权限" min-width="210">
              <template #default="{ row }">
                <div class="document-access">
                  <el-tag :type="row.accessible ? 'success' : 'info'" size="small">
                    {{ row.accessible ? '可访问' : '无权限' }}
                  </el-tag>
                  <div class="allowed-roles">
                    <el-tag
                      v-for="role in row.allowedRoles || []"
                      :key="role.roleCode"
                      size="small"
                      effect="plain"
                    >
                      {{ role.roleName }}
                    </el-tag>
                    <span v-if="!row.allowedRoles?.length" class="empty-roles">未配置角色</span>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="chunkCount" label="分块" width="90" align="right">
              <template #default="{ row }">{{ row.chunkCount || 0 }}</template>
            </el-table-column>
            <el-table-column prop="createTime" label="创建时间" width="180">
              <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="144" align="right">
              <template #default="{ row }">
                <el-tooltip :content="row.accessible ? '查看原文档' : '你没有查看该文档的权限'" placement="left">
                  <el-button
                    text
                    circle
                    :disabled="!row.accessible"
                    :aria-label="row.accessible ? '查看原文档' : '无权查看原文档'"
                    @click="viewerDocument = row"
                  >
                    <el-icon><View /></el-icon>
                  </el-button>
                </el-tooltip>
                <el-tooltip v-if="authStore.isAdmin" content="编辑权限" placement="left">
                  <el-button text circle aria-label="编辑文档权限" @click="openPermissionDialog(row)">
                    <el-icon><Edit /></el-icon>
                  </el-button>
                </el-tooltip>
                <el-tooltip v-if="authStore.isAdmin" content="删除文档" placement="left">
                  <el-button
                    text
                    circle
                    class="delete-button"
                    aria-label="删除文档"
                    @click="handleDelete(row)"
                  >
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </el-tooltip>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </section>
    </div>

    <el-dialog v-model="permissionDialogVisible" title="编辑文档权限" width="440px">
      <p class="dialog-document-name">{{ editingDocument?.name }}</p>
      <el-checkbox-group v-model="editingRoleCodes" class="dialog-role-list">
        <el-checkbox v-for="role in roles" :key="role.roleCode" :value="role.roleCode">
          {{ role.roleName }}（{{ role.roleCode }}）
        </el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="permissionDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingPermissions" @click="savePermissions">保存</el-button>
      </template>
    </el-dialog>
    <DocumentViewer :document="viewerDocument" @close="viewerDocument = null" />
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { Delete, Edit, Refresh, View } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useKnowledgeStore } from '@/stores/knowledge'
import { useAuthStore } from '@/stores/auth'
import SkeletonTable from '@/components/SkeletonTable.vue'
import DocumentViewer from '@/components/DocumentViewer.vue'
import { getRoles } from '@/api/role'

const knowledgeStore = useKnowledgeStore()
const authStore = useAuthStore()
const uploadRef = ref()
const selectedFile = ref(null)
const uploading = ref(false)
const selectedRoles = ref([])
const roles = ref([])
const permissionDialogVisible = ref(false)
const editingDocument = ref(null)
const editingRoleCodes = ref([])
const savingPermissions = ref(false)
const viewerDocument = ref(null)
const { documents, loading, knowledgeBaseId } = storeToRefs(knowledgeStore)
const { fetchDocuments, upload, remove, updatePermissions } = knowledgeStore

const customRoles = computed(() => roles.value.filter(role => !['ADMIN', 'USER'].includes(role.roleCode)))

const completedCount = computed(() => (
  documents.value.filter(document => document.status === 'COMPLETED').length
))

onMounted(async () => {
  await fetchDocuments()
  if (authStore.isAdmin) {
    roles.value = await getRoles()
    selectedRoles.value = roles.value.map(role => role.roleCode)
  }
})

const openPermissionDialog = (document) => {
  editingDocument.value = document
  editingRoleCodes.value = (document.allowedRoles || []).map(role => role.roleCode)
  permissionDialogVisible.value = true
}

const savePermissions = async () => {
  if (!editingDocument.value || editingRoleCodes.value.length === 0) {
    ElMessage.error('请选择至少一个访问角色')
    return
  }
  savingPermissions.value = true
  try {
    await updatePermissions(editingDocument.value.id, editingRoleCodes.value)
    permissionDialogVisible.value = false
    ElMessage.success('文档权限已更新')
  } finally {
    savingPermissions.value = false
  }
}

const handleFileChange = (file) => {
  selectedFile.value = file.raw
}

const clearSelectedFile = () => {
  selectedFile.value = null
  uploadRef.value?.clearFiles()
}

const handleUpload = async () => {
  if (!selectedFile.value) return
  if (selectedRoles.value.length === 0) {
    ElMessage.error('请选择至少一个访问角色')
    return
  }

  uploading.value = true
  try {
    if (await upload(selectedFile.value, knowledgeBaseId.value, selectedRoles.value)) {
      ElMessage.success('文档已提交处理')
      clearSelectedFile()
    }
  } finally {
    uploading.value = false
  }
}

const handleDelete = async (document) => {
  try {
    await ElMessageBox.confirm(
      `删除"${document.name}"及其全部向量数据？`,
      '删除文档',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    if (await remove(document.id)) {
      ElMessage.success('文档已删除')
    }
  } catch {
    // User cancelled the destructive action.
  }
}

const formatFileSize = (bytes) => {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

const formatTime = (time) => time
  ? new Date(time).toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit'
    })
  : ''

const statusLabel = (status) => ({
  PENDING: '等待处理',
  PROCESSING: '处理中',
  COMPLETED: '已完成',
  FAILED: '处理失败',
  DELETING: '删除中'
}[status] || status)

const statusClass = (status) => `status-${String(status || 'PENDING').toLowerCase()}`
</script>

<style scoped>
.knowledge-page {
  height: 100%;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  background: var(--bg-primary);
}

.page-toolbar {
  min-height: 76px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 14px clamp(20px, 4vw, 48px);
  border-bottom: 1px solid var(--border);
  background: color-mix(in srgb, var(--surface) 88%, transparent);
  backdrop-filter: blur(18px) saturate(150%);
  z-index: 2;
}

.page-toolbar h1 {
  margin: 0;
  font-size: 1.25rem;
  line-height: 1.25;
  font-weight: 650;
  letter-spacing: 0;
}

.page-toolbar p {
  margin: 3px 0 0;
  color: var(--text-muted);
  font-size: 0.78rem;
}

.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-muted);
  font-size: 0.75rem;
}

.toolbar-actions .el-input {
  width: 140px;
}

.knowledge-content {
  width: min(100%, 1120px);
  margin: 0 auto;
  padding: 24px clamp(16px, 4vw, 36px) 40px;
  overflow-y: auto;
}

/* 上传区域 */
.upload-section {
  padding: 24px;
  border: 2px dashed var(--border);
  border-radius: 16px;
  background: var(--surface);
}

.upload-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
}

.upload-icon {
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.1) 0%, rgba(118, 75, 162, 0.1) 100%);
  color: #667eea;
}

.upload-icon svg {
  width: 24px;
  height: 24px;
}

.upload-title-group h2 {
  margin: 0;
  font-size: 1.125rem;
  font-weight: 600;
  color: var(--text-primary);
}

.upload-title-group p {
  margin: 4px 0 0;
  font-size: 0.8125rem;
  color: var(--text-muted);
}

/* 已选文件 */
.selected-file-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  margin-bottom: 20px;
  border-radius: 12px;
  background: color-mix(in srgb, var(--accent-soft) 30%, transparent);
  border: 1px solid color-mix(in srgb, var(--accent) 20%, transparent);
}

.file-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.file-info svg {
  width: 24px;
  height: 24px;
  color: var(--accent);
}

.file-meta {
  display: flex;
  flex-direction: column;
}

.file-name {
  font-weight: 500;
  color: var(--text-primary);
}

.file-size {
  font-size: 0.75rem;
  color: var(--text-muted);
}

.remove-file-btn {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  transition: all 0.2s;
}

.remove-file-btn:hover {
  background: var(--border);
  color: var(--danger);
}

.remove-file-btn svg {
  width: 18px;
  height: 18px;
}

/* 权限选择 */
.permission-section {
  margin-bottom: 20px;
}

.permission-label {
  display: block;
  margin-bottom: 12px;
  font-size: 0.875rem;
  font-weight: 500;
  color: var(--text-secondary);
}

.role-checkbox-group {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}

.document-access,
.allowed-roles {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.empty-roles,
.dialog-document-name {
  color: var(--text-muted);
  font-size: 0.78rem;
}

.dialog-role-list {
  display: grid;
  gap: 10px;
  margin-top: 16px;
}

.role-checkbox {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 20px;
  border: 2px solid var(--border);
  border-radius: 12px;
  background: var(--bg-primary);
  cursor: pointer;
  transition: all 0.2s;
}

.role-checkbox:hover {
  border-color: var(--text-muted);
}

.role-checkbox input {
  display: none;
}

.checkbox-custom {
  width: 20px;
  height: 20px;
  border: 2px solid var(--border);
  border-radius: 4px;
  position: relative;
}

.role-checkbox input:checked + .checkbox-custom::after {
  content: '';
  position: absolute;
  top: 2px;
  left: 6px;
  width: 5px;
  height: 10px;
  border: solid white;
  border-width: 0 2px 2px 0;
  transform: rotate(45deg);
}

.role-checkbox input:checked + .checkbox-custom.admin {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-color: transparent;
}

.role-checkbox input:checked + .checkbox-custom.user {
  background: #10b981;
  border-color: transparent;
}

.role-name {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 0.875rem;
  font-weight: 500;
  color: var(--text-primary);
}

.role-name svg {
  width: 18px;
  height: 18px;
  color: var(--text-muted);
}

.permission-hint {
  margin: 8px 0 0;
  font-size: 0.75rem;
  color: var(--text-muted);
}

/* 上传操作 */
.upload-actions {
  display: flex;
  gap: 12px;
}

.select-file-btn,
.upload-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 24px;
  border-radius: 10px;
  font-size: 0.9375rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.select-file-btn {
  border: 2px solid var(--border);
  background: var(--bg-primary);
  color: var(--text-primary);
}

.select-file-btn:hover {
  border-color: var(--text-muted);
}

.select-file-btn svg,
.upload-btn svg {
  width: 18px;
  height: 18px;
}

.upload-btn {
  border: none;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.upload-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

.upload-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* 文档列表 */
.document-workspace {
  margin-top: 24px;
}

.section-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
}

.section-heading h2 {
  margin: 0;
  font-size: 0.92rem;
  font-weight: 650;
  letter-spacing: 0;
}

.section-heading p {
  margin: 3px 0 0;
  color: var(--text-muted);
  font-size: 0.75rem;
}

.status-legend {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--text-muted);
  font-size: 0.75rem;
}

.table-scroll {
  overflow-x: auto;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--surface);
  box-shadow: var(--shadow-low);
}

.documents-table {
  min-width: 780px;
}

.document-name {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.document-name svg {
  width: 20px;
  height: 20px;
  flex-shrink: 0;
  color: var(--accent);
}

.document-name div {
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.document-name strong {
  overflow: hidden;
  color: var(--text-primary);
  font-size: 0.82rem;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.document-name span {
  color: var(--text-muted);
  font-size: 0.68rem;
}

.document-status {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: var(--text-secondary);
  font-size: 0.76rem;
}

.status-dot {
  width: 7px;
  height: 7px;
  flex-shrink: 0;
  border-radius: 50%;
  background: var(--text-muted);
}

.status-completed .status-dot,
.status-dot.completed { background: var(--success); }
.status-processing .status-dot { background: var(--info); }
.status-failed .status-dot { background: var(--danger); }
.status-deleting .status-dot { background: var(--warning); }

.delete-button {
  color: var(--text-muted);
}

.delete-button:hover {
  color: var(--danger);
}

@media (max-width: 780px) {
  .page-toolbar {
    align-items: flex-start;
    flex-direction: column;
    gap: 10px;
  }

  .toolbar-actions {
    width: 100%;
  }

  .toolbar-actions .el-input {
    flex: 1;
    width: auto;
  }

  .role-checkbox-group {
    flex-direction: column;
  }

  .upload-actions {
    flex-direction: column;
  }
}

@media (max-width: 430px) {
  .knowledge-content {
    padding-top: 16px;
  }

  .section-heading {
    align-items: flex-start;
  }

  .status-legend {
    white-space: nowrap;
  }
}

@media (prefers-reduced-transparency: reduce) {
  .page-toolbar {
    background: var(--surface);
    backdrop-filter: none;
  }
}
</style>
