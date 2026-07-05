<template>
  <div class="knowledge-page">
    <div class="knowledge-container">
      <!-- 页面标题 -->
      <div class="page-header">
        <div class="header-info">
          <h2 class="page-title glow-text">知识库管理</h2>
          <p class="page-subtitle">上传和管理私募基金相关文档资料</p>
        </div>
      </div>

      <!-- 上传区域 -->
      <div class="upload-section card">
        <div class="upload-header">
          <div class="upload-icon">
            <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
              <polyline points="17 8 12 3 7 8"/>
              <line x1="12" y1="3" x2="12" y2="15"/>
            </svg>
          </div>
          <h3 class="upload-title">上传文档</h3>
          <p class="upload-description">支持 PDF、DOC、DOCX、TXT 格式的文档上传</p>
        </div>

        <div class="upload-actions">
          <el-upload
            ref="uploadRef"
            :auto-upload="false"
            :on-change="handleFileChange"
            :show-file-list="false"
            accept=".pdf,.doc,.docx,.txt"
            class="upload-input"
          >
            <el-button type="default" size="large" class="file-btn">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                <polyline points="14 2 14 8 20 8"/>
              </svg>
              选择文件
            </el-button>
          </el-upload>

          <el-button
            type="primary"
            size="large"
            @click="handleUpload"
            :loading="uploading"
            :disabled="!selectedFile"
            class="upload-btn"
          >
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
              <polyline points="17 8 12 3 7 8"/>
              <line x1="12" y1="3" x2="12" y2="15"/>
            </svg>
            开始上传
          </el-button>
        </div>

        <div v-if="selectedFile" class="file-preview">
          <div class="file-icon">
            <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
              <polyline points="14 2 14 8 20 8"/>
              <line x1="16" y1="13" x2="8" y2="13"/>
              <line x1="16" y1="17" x2="8" y2="17"/>
            </svg>
          </div>
          <div class="file-info">
            <div class="file-name">{{ selectedFile.name }}</div>
            <div class="file-size">{{ formatFileSize(selectedFile.size) }}</div>
          </div>
        </div>
      </div>

      <!-- 文档列表 -->
      <div class="documents-section card">
        <div class="section-header">
          <h3 class="section-title">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/>
            </svg>
            文档列表
          </h3>
          <el-button size="small" @click="fetchDocuments" class="refresh-btn">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="23 4 23 10 17 10"/>
              <polyline points="1 20 1 14 7 14"/>
              <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/>
            </svg>
            刷新
          </el-button>
        </div>

        <el-table :data="documents" v-loading="loading" class="documents-table">
          <el-table-column prop="id" label="ID" width="80" align="center" />
          <el-table-column prop="name" label="文档名称" min-width="200">
            <template #default="{ row }">
              <div class="doc-name">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                  <polyline points="14 2 14 8 20 8"/>
                </svg>
                {{ row.name }}
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="fileType" label="类型" width="100" align="center">
            <template #default="{ row }">
              <el-tag size="small">{{ row.fileType }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="120" align="center">
            <template #default="{ row }">
              <div class="status-badge" :class="getStatusClass(row.status)">
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <circle cx="12" cy="12" r="10"/>
                  <line x1="12" y1="8" x2="12" y2="12"/>
                  <line x1="12" y1="16" x2="12.01" y2="16"/>
                </svg>
                {{ row.status }}
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="chunkCount" label="分块数" width="100" align="center">
            <template #default="{ row }">
              <div class="chunk-count">{{ row.chunkCount || 0 }}</div>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="创建时间" width="180" align="center">
            <template #default="{ row }">
              {{ formatTime(row.createTime) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120" align="center">
            <template #default="{ row }">
              <el-button
                type="danger"
                size="small"
                @click="handleDelete(row.id)"
                class="delete-btn"
              >
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polyline points="3 6 5 6 21 6"/>
                  <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
                </svg>
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useKnowledgeStore } from '@/stores/knowledge'
import { ElMessage, ElMessageBox } from 'element-plus'

const knowledgeStore = useKnowledgeStore()
const uploadRef = ref()
const selectedFile = ref(null)
const uploading = ref(false)

const { documents, loading, fetchDocuments, upload, remove } = knowledgeStore

onMounted(() => {
  fetchDocuments()
})

const handleFileChange = (file) => {
  selectedFile.value = file.raw
}

const handleUpload = async () => {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择文件')
    return
  }

  uploading.value = true
  try {
    const success = await upload(selectedFile.value)
    if (success) {
      ElMessage.success('上传成功，文档正在处理中')
      selectedFile.value = null
      uploadRef.value?.clearFiles()
    } else {
      ElMessage.error('上传失败')
    }
  } finally {
    uploading.value = false
  }
}

const handleDelete = async (id) => {
  try {
    await ElMessageBox.confirm('确认删除该文档吗？删除后无法恢复', '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })

    const success = await remove(id)
    if (success) {
      ElMessage.success('删除成功')
    } else {
      ElMessage.error('删除失败')
    }
  } catch {
    // 取消删除
  }
}

const formatFileSize = (bytes) => {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(2) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(2) + ' MB'
}

const getStatusClass = (status) => {
  const classes = {
    'PENDING': 'status-pending',
    'PROCESSING': 'status-processing',
    'COMPLETED': 'status-completed',
    'FAILED': 'status-failed'
  }
  return classes[status] || 'status-pending'
}

const formatTime = (time) => {
  if (!time) return ''
  return new Date(time).toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}
</script>

<style scoped>
.knowledge-page {
  min-height: 100vh;
  background: var(--bg-primary);
  padding: 2rem;
}

.knowledge-container {
  max-width: 1200px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 2rem;
}

.page-title {
  font-family: var(--font-code);
  font-size: 2rem;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 0.5rem 0;
}

.page-subtitle {
  color: var(--text-secondary);
  font-size: 0.875rem;
  margin: 0;
}

.upload-section {
  margin-bottom: 2rem;
}

.upload-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: 2rem 0 1.5rem;
}

.upload-icon {
  color: var(--primary);
  opacity: 0.8;
  margin-bottom: 1rem;
}

.upload-title {
  font-size: 1.25rem;
  color: var(--text-primary);
  margin: 0 0 0.5rem;
}

.upload-description {
  color: var(--text-muted);
  font-size: 0.875rem;
  margin: 0;
}

.upload-actions {
  display: flex;
  justify-content: center;
  gap: 1rem;
  padding: 1.5rem;
}

.file-btn, .upload-btn {
  cursor: pointer;
}

.upload-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.file-preview {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 1rem;
  background: var(--bg-secondary);
  border-radius: var(--radius-md);
  margin: 1rem 2rem;
}

.file-icon {
  color: var(--primary);
}

.file-info {
  flex: 1;
}

.file-name {
  font-size: 0.875rem;
  color: var(--text-primary);
  margin-bottom: 0.25rem;
}

.file-size {
  font-size: 0.75rem;
  color: var(--text-muted);
}

.documents-section {
  overflow: hidden;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1.5rem;
  border-bottom: 1px solid var(--border);
}

.section-title {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 1rem;
  color: var(--text-primary);
  margin: 0;
}

.section-title svg {
  color: var(--primary);
}

.refresh-btn {
  cursor: pointer;
}

.documents-table {
  border-top: none;
}

.doc-name {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  color: var(--text-primary);
}

.doc-name svg {
  color: var(--text-muted);
}

.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
  padding: 0.25rem 0.75rem;
  border-radius: var(--radius-md);
  font-size: 0.75rem;
  font-weight: 500;
}

.status-pending {
  background: rgba(100, 116, 139, 0.15);
  color: var(--text-muted);
}

.status-processing {
  background: rgba(245, 158, 11, 0.15);
  color: var(--primary);
}

.status-completed {
  background: rgba(16, 185, 129, 0.15);
  color: #10B981;
}

.status-failed {
  background: rgba(239, 68, 68, 0.15);
  color: #EF4444;
}

.chunk-count {
  font-family: var(--font-code);
  color: var(--cta);
  font-weight: 500;
}

.delete-btn {
  cursor: pointer;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .knowledge-page {
    padding: 1rem;
  }

  .page-title {
    font-size: 1.5rem;
  }

  .upload-actions {
    flex-direction: column;
  }

  .documents-table {
    font-size: 0.875rem;
  }
}

@media (max-width: 375px) {
  .upload-header {
    padding: 1rem 0;
  }

  .upload-icon {
    width: 36px;
    height: 36px;
  }
}
</style>