<template>
  <div class="product-page">
    <div class="product-container">
      <!-- 页面标题 -->
      <div class="page-header">
        <div class="header-info">
          <h2 class="page-title glow-text">产品管理</h2>
          <p class="page-subtitle">查看和管理私募基金产品信息</p>
        </div>
      </div>

      <!-- 搜索和筛选 -->
      <div class="search-section card">
        <div class="search-box">
          <el-input
            v-model="searchKeyword"
            placeholder="输入产品名称或代码搜索"
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

      <!-- 产品表格 -->
      <div class="products-section card">
        <div class="section-header">
          <h3 class="section-title">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="2" y="7" width="20" height="14" rx="2" ry="2"/>
              <path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16"/>
            </svg>
            产品列表
          </h3>
          <div class="product-count">共 {{ products.length }} 个产品</div>
        </div>

        <el-table :data="products" v-loading="loading" class="products-table">
          <el-table-column prop="id" label="ID" width="80" align="center" />
          <el-table-column prop="productCode" label="产品代码" width="140">
            <template #default="{ row }">
              <div class="code-badge">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polyline points="16 18 22 12 16 6"/>
                  <polyline points="8 6 2 12 8 18"/>
                </svg>
                {{ row.productCode }}
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="productName" label="产品名称" min-width="180" />
          <el-table-column prop="managerName" label="管理人" width="160">
            <template #default="{ row }">
              <div class="manager-cell">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
                  <circle cx="12" cy="7" r="4"/>
                </svg>
                {{ row.managerName || '-' }}
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="riskLevel" label="风险等级" width="120" align="center">
            <template #default="{ row }">
              <div class="risk-badge" :class="getRiskClass(row.riskLevel)">
                {{ row.riskLevel }}
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="returnRate" label="收益率" width="120" align="center">
            <template #default="{ row }">
              <div class="rate-badge" :class="{ positive: row.returnRate > 0 }">
                {{ row.returnRate ? row.returnRate.toFixed(2) + '%' : '-' }}
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="minAmount" label="最低金额" width="140" align="center">
            <template #default="{ row }">
              <div class="amount-badge">
                {{ row.minAmount ? '¥' + formatAmount(row.minAmount) : '-' }}
              </div>
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

      <!-- 产品详情对话框 -->
      <el-dialog v-model="dialogVisible" title="产品详情" width="560px" class="product-dialog">
        <div v-if="currentProduct" class="product-detail">
          <div class="detail-header">
            <div class="product-icon">
              <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <rect x="2" y="7" width="20" height="14" rx="2" ry="2"/>
                <path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16"/>
              </svg>
            </div>
            <div class="product-title">
              <h3>{{ currentProduct.productName }}</h3>
              <el-tag size="small" :class="getRiskClass(currentProduct.riskLevel)">
                {{ currentProduct.riskLevel }}
              </el-tag>
            </div>
          </div>

          <el-descriptions :column="1" border class="detail-desc">
            <el-descriptions-item label="产品代码">
              <span class="desc-value code">{{ currentProduct.productCode }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="管理人">
              <span class="desc-value">{{ currentProduct.managerName || '-' }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="风险等级">
              <div class="risk-badge" :class="getRiskClass(currentProduct.riskLevel)">
                {{ currentProduct.riskLevel }}
              </div>
            </el-descriptions-item>
            <el-descriptions-item label="收益率">
              <span class="desc-value rate" :class="{ positive: currentProduct.returnRate > 0 }">
                {{ currentProduct.returnRate ? currentProduct.returnRate.toFixed(2) + '%' : '-' }}
              </span>
            </el-descriptions-item>
            <el-descriptions-item label="投资策略">
              <span class="desc-value">{{ currentProduct.investmentStrategy || '-' }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="最低金额">
              <span class="desc-value amount">
                {{ currentProduct.minAmount ? '¥' + formatAmount(currentProduct.minAmount) : '-' }}
              </span>
            </el-descriptions-item>
          </el-descriptions>
        </div>
      </el-dialog>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useProductStore } from '@/stores/product'
import { ElMessage } from 'element-plus'

const productStore = useProductStore()
const searchKeyword = ref('')
const dialogVisible = ref(false)

const { products, currentProduct, loading, fetchProducts, search, fetchProduct } = productStore

onMounted(() => {
  fetchProducts()
})

const handleSearch = () => {
  search(searchKeyword.value)
}

const handleClearSearch = () => {
  searchKeyword.value = ''
  fetchProducts()
}

const handleRefresh = () => {
  searchKeyword.value = ''
  fetchProducts()
  ElMessage.success('数据已刷新')
}

const handleView = async (id) => {
  await fetchProduct(id)
  dialogVisible.value = true
}

const getRiskClass = (riskLevel) => {
  const classes = {
    '低风险': 'risk-low',
    '中低风险': 'risk-medium-low',
    '中风险': 'risk-medium',
    '中高风险': 'risk-medium-high',
    '高风险': 'risk-high'
  }
  return classes[riskLevel] || 'risk-medium'
}

const formatAmount = (amount) => {
  if (amount >= 10000) {
    return (amount / 10000).toFixed(2) + '万'
  }
  return amount.toFixed(2)
}
</script>

<style scoped>
.product-page {
  height: calc(100vh - 64px);
  background: var(--bg-primary);
  padding: 2rem;
  overflow-y: auto;
}

.product-container {
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

.products-section {
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

.product-count {
  font-size: 0.875rem;
  color: var(--text-muted);
}

.products-table {
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

.manager-cell {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  color: var(--text-primary);
}

.manager-cell svg {
  color: var(--text-muted);
}

.risk-badge {
  display: inline-flex;
  padding: 0.25rem 0.75rem;
  border-radius: var(--radius-md);
  font-size: 0.75rem;
  font-weight: 500;
}

.risk-low {
  background: rgba(16, 185, 129, 0.15);
  color: #10B981;
}

.risk-medium-low {
  background: rgba(59, 130, 246, 0.15);
  color: #3B82F6;
}

.risk-medium {
  background: rgba(245, 158, 11, 0.15);
  color: var(--primary);
}

.risk-medium-high {
  background: rgba(249, 115, 22, 0.15);
  color: #F97316;
}

.risk-high {
  background: rgba(239, 68, 68, 0.15);
  color: #EF4444;
}

.rate-badge {
  font-family: var(--font-code);
  font-size: 0.875rem;
  color: var(--text-muted);
}

.rate-badge.positive {
  color: #10B981;
}

.amount-badge {
  font-family: var(--font-code);
  font-size: 0.875rem;
  color: var(--text-primary);
}

.view-btn {
  cursor: pointer;
}

.product-detail {
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

.product-icon {
  color: var(--primary);
}

.product-title h3 {
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

.desc-value.rate.positive {
  color: #10B981;
}

.desc-value.amount {
  color: var(--primary);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .product-page {
    padding: 1rem;
  }

  .page-title {
    font-size: 1.5rem;
  }

  .search-box {
    flex-direction: column;
  }

  .products-table {
    font-size: 0.875rem;
  }
}
</style>