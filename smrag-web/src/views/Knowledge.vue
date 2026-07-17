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
      <section class="upload-band" aria-labelledby="upload-title">
        <div class="upload-copy">
          <el-icon><UploadFilled /></el-icon>
          <div>
            <h2 id="upload-title">上传文档</h2>
            <p>PDF、DOC、DOCX、TXT，单个文件不超过 20 MB</p>
          </div>
        </div>

        <div v-if="selectedFile" class="selected-file">
          <el-icon><Document /></el-icon>
          <span class="file-name">{{ selectedFile.name }}</span>
          <span>{{ formatFileSize(selectedFile.size) }}</span>
          <el-button text circle aria-label="移除已选文件" @click="clearSelectedFile">
            <el-icon><Close /></el-icon>
          </el-button>
        </div>

        <div class="upload-actions">
          <el-upload
            ref="uploadRef"
            :auto-upload="false"
            :on-change="handleFileChange"
            :show-file-list="false"
            accept=".pdf,.doc,.docx,.txt"
          >
            <el-button>
              <el-icon><FolderOpened /></el-icon>
              选择文件
            </el-button>
          </el-upload>
          <el-button
            type="primary"
            :loading="uploading"
            :disabled="!selectedFile"
            @click="handleUpload"
          >
            <el-icon v-if="!uploading"><Upload /></el-icon>
            上传
          </el-button>
        </div>
      </section>

      <section class="document-workspace" aria-labelledby="documents-title">
        <div class="section-heading">
          <div>
            <h2 id="documents-title">文档</h2>
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
                  <el-icon><Document /></el-icon>
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
            <el-table-column prop="chunkCount" label="分块" width="90" align="right">
              <template #default="{ row }">{{ row.chunkCount || 0 }}</template>
            </el-table-column>
            <el-table-column prop="createTime" label="创建时间" width="180">
              <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
            </el-table-column>
            <el-table-column label="" width="64" align="right">
              <template #default="{ row }">
                <el-tooltip content="删除文档" placement="left">
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
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import {
  Close,
  Delete,
  Document,
  FolderOpened,
  Refresh,
  Upload,
  UploadFilled
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useKnowledgeStore } from '@/stores/knowledge'
import SkeletonTable from '@/components/SkeletonTable.vue'

const knowledgeStore = useKnowledgeStore()
const uploadRef = ref()
const selectedFile = ref(null)
const uploading = ref(false)
const { documents, loading, knowledgeBaseId } = storeToRefs(knowledgeStore)
const { fetchDocuments, upload, remove } = knowledgeStore

const completedCount = computed(() => (
  documents.value.filter(document => document.status === 'COMPLETED').length
))

onMounted(fetchDocuments)

const handleFileChange = (file) => {
  selectedFile.value = file.raw
}

const clearSelectedFile = () => {
  selectedFile.value = null
  uploadRef.value?.clearFiles()
}

const handleUpload = async () => {
  if (!selectedFile.value) return
  uploading.value = true
  try {
    if (await upload(selectedFile.value)) {
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
      `删除“${document.name}”及其全部向量数据？`,
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

.upload-band {
  min-height: 86px;
  display: grid;
  grid-template-columns: minmax(220px, 1fr) minmax(0, 1fr) auto;
  align-items: center;
  gap: 20px;
  padding: 16px 18px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--surface);
  box-shadow: var(--shadow-low);
}

.upload-copy {
  display: flex;
  align-items: center;
  gap: 12px;
}

.upload-copy > .el-icon {
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  border-radius: 7px;
  background: var(--accent-soft);
  color: var(--accent);
  font-size: 1rem;
}

.upload-copy h2,
.section-heading h2 {
  margin: 0;
  font-size: 0.92rem;
  font-weight: 650;
  letter-spacing: 0;
}

.upload-copy p,
.section-heading p {
  margin: 3px 0 0;
  color: var(--text-muted);
  font-size: 0.75rem;
}

.selected-file {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-muted);
  font-size: 0.75rem;
}

.selected-file .file-name {
  min-width: 0;
  overflow: hidden;
  color: var(--text-secondary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.upload-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

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

.document-name > .el-icon {
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

  .upload-band {
    grid-template-columns: 1fr;
    gap: 14px;
  }

  .upload-actions {
    justify-content: flex-end;
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
