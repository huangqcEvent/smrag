<template>
  <div class="manager-page">
    <div class="manager-container">
      <!-- 页面标题 -->
      <div class="page-header">
        <div class="header-info">
          <h2 class="page-title glow-text">管理人管理</h2>
          <p class="page-subtitle">查看和管理私募基金管理人信息</p>
        </div>
      </div>

      <!-- 搜索区域 -->
      <div class="search-section card">
        <div class="search-box">
          <el-input
            v-model="searchKeyword"
            placeholder="输入管理人名称或代码搜索"
            clearable
            @keyup.enter="handleSearch"
            @clear="handleClearSearch"
            size="large"
            class="search-input"
          >
            <template #prefix>
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="11" cy="11" r="8"/>
                <line x1="21" y1="21" x2="16.65" y2="16.65"/>
              </svg>
            </template>
          </el-input>
          <el-button
            type="primary"
            size="large"
            @click="handleSearch"
            class="search-btn"
          >
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="11" cy="11" r="8"/>
              <line x1="21" y1="21" x2="16.65" y2="16.65"/>
            </svg>
            搜索
          </el-button>
          <el-button
            size="large"
            @click="handleRefresh"
            class="refresh-btn"
          >
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="23 4 23 10 17 10"/>
              <polyline points="1 20 1 14 7 14"/>
              <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/>
            </svg>
            刷新
          </el-button>
        </div>
      </div>

      <!-- 管理人表格 -->
      <div class="managers-section card">
        <div class="section-header">
          <h3 class="section-title">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
              <circle cx="12" cy="7" r="4"/>
            </svg>
            管理人列表
          </h3>
          <div class="manager-count">共 {{ managers.length }} 个管理人</div>
        </div>

        <el-table :data="managers" v-loading="loading" class="managers-table">
          <el-table-column prop="id" label="ID" width="80" align="center" />
          <el-table-column prop="managerCode" label="管理人代码" width="140">
            <template #default="{ row }">
              <div class="code-badge">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polyline points="16 18 22 12 16 6"/>
                  <polyline points="8 6 2 12 8 18"/>
                </svg>
                {{ row.managerCode }}
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="managerName" label="管理人名称" min-width="160" />
          <el-table-column prop="companyName" label="公司名称" min-width="200" />
          <el-table-column prop="assetsScale" label="资产规模" width="160" align="center">
            <template #default="{ row }">
              <div class="scale-badge">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <line x1="12" y1="1" x2="12" y2="23"/>
                  <path d="M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6"/>
                </svg>
                {{ row.assetsScale ? '¥' + formatScale(row.assetsScale) + '万' : '-' }}
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="qualification" label="资质" width="160" align="center">
            <template #default="{ row }">
              <div v-if="row.qualification" class="qual-badge">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
                  <polyline points="22 4 12 14.01 9 11.01"/>
                </svg>
                {{ row.qualification }}
              </div>
              <span v-else class="no-data">-</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="140" align="center" fixed="right">
            <template #default="{ row }">
              <el-button
                type="primary"
                size="small"
                @click="handleView(row.id)"
                class="view-btn"
              >
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                  <circle cx="12" cy="12" r="3"/>
                </svg>
                查看
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 管理人详情对话框 -->
      <el-dialog v-model="dialogVisible" title="管理人详情" width="560px" class="manager-dialog">
        <div v-if="currentManager" class="manager-detail">
          <div class="detail-header">
            <div class="manager-avatar">
              <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
                <circle cx="12" cy="7" r="4"/>
              </svg>
            </div>
            <div class="manager-title">
              <h3>{{ currentManager.managerName }}</h3>
              <el-tag v-if="currentManager.qualification" size="small" type="success">
                {{ currentManager.qualification }}
              </el-tag>
            </div>
          </div>

          <el-descriptions :column="1" border class="detail-desc">
            <el-descriptions-item label="管理人代码">
              <span class="desc-value code">{{ currentManager.managerCode }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="管理人名称">
              <span class="desc-value">{{ currentManager.managerName }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="公司名称">
              <span class="desc-value">{{ currentManager.companyName || '-' }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="资产规模">
              <span class="desc-value scale">
                {{ currentManager.assetsScale ? '¥' + formatScale(currentManager.assetsScale) + '万' : '-' }}
              </span>
            </el-descriptions-item>
            <el-descriptions-item label="资质">
              <div v-if="currentManager.qualification" class="qual-badge">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
                  <polyline points="22 4 12 14.01 9 11.01"/>
                </svg>
                {{ currentManager.qualification }}
              </div>
              <span v-else class="no-data">-</span>
            </el-descriptions-item>
          </el-descriptions>
        </div>
      </el-dialog>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useManagerStore } from '@/stores/manager'
import { ElMessage } from 'element-plus'

const managerStore = useManagerStore()
const searchKeyword = ref('')
const dialogVisible = ref(false)

const { managers, currentManager, loading, fetchManagers, search, fetchManager } = managerStore

onMounted(() => {
  fetchManagers()
})

const handleSearch = () => {
  search(searchKeyword.value)
}

const handleClearSearch = () => {
  searchKeyword.value = ''
  fetchManagers()
}

const handleRefresh = () => {
  searchKeyword.value = ''
  fetchManagers()
  ElMessage.success('数据已刷新')
}

const handleView = async (id) => {
  await fetchManager(id)
  dialogVisible.value = true
}

const formatScale = (scale) => {
  return scale.toFixed(2)
}
</script>

<style scoped>
.manager-page {
  min-height: 100vh;
  background: var(--bg-primary);
  padding: 2rem;
}

.manager-container {
  max-width: 1400px;
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

.search-section {
  margin-bottom: 1.5rem;
}

.search-box {
  display: flex;
  gap: 1rem;
  padding: 1rem;
}

.search-input {
  flex: 1;
}

.search-btn, .refresh-btn {
  cursor: pointer;
}

.managers-section {
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

.manager-count {
  font-size: 0.875rem;
  color: var(--text-muted);
}

.managers-table {
  border-top: none;
}

.code-badge {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  color: var(--cta);
  font-family: var(--font-code);
  font-size: 0.875rem;
}

.scale-badge {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  color: var(--primary);
  font-family: var(--font-code);
  font-size: 0.875rem;
}

.qual-badge {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.25rem 0.75rem;
  background: rgba(16, 185, 129, 0.15);
  color: #10B981;
  border-radius: var(--radius-md);
  font-size: 0.75rem;
}

.no-data {
  color: var(--text-muted);
}

.view-btn {
  cursor: pointer;
}

.manager-detail {
  padding: 1rem;
}

.detail-header {
  display: flex;
  align-items: center;
  gap: 1.5rem;
  padding-bottom: 1.5rem;
  border-bottom: 1px solid var(--border);
  margin-bottom: 1.5rem;
}

.manager-avatar {
  color: var(--primary);
}

.manager-title h3 {
  font-size: 1.25rem;
  color: var(--text-primary);
  margin: 0 0 0.5rem;
}

.detail-desc {
  background: transparent;
}

.desc-value {
  font-family: var(--font-code);
}

.desc-value.code {
  color: var(--cta);
}

.desc-value.scale {
  color: var(--primary);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .manager-page {
    padding: 1rem;
  }

  .page-title {
    font-size: 1.5rem;
  }

  .search-box {
    flex-direction: column;
  }

  .managers-table {
    font-size: 0.875rem;
  }
}
</style>