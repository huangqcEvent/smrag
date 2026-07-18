<template>
  <div class="users-page">
    <!-- 页面头部 -->
    <header class="page-header">
      <div class="header-content">
        <h1 class="page-title">用户管理</h1>
        <p class="page-subtitle">管理系统用户、角色权限和账户状态</p>
      </div>
      <button class="add-user-btn" @click="showAddDialog">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <line x1="12" y1="5" x2="12" y2="19"/>
          <line x1="5" y1="12" x2="19" y2="12"/>
        </svg>
        添加用户
      </button>
    </header>

    <!-- 统计卡片 -->
    <div class="stats-grid">
      <div class="stat-card">
        <div class="stat-icon users">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
            <circle cx="9" cy="7" r="4"/>
            <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
            <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
          </svg>
        </div>
        <div class="stat-info">
          <span class="stat-value">{{ users.length }}</span>
          <span class="stat-label">总用户</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon admins">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
          </svg>
        </div>
        <div class="stat-info">
          <span class="stat-value">{{ adminCount }}</span>
          <span class="stat-label">管理员</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon active">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
            <polyline points="22 4 12 14.01 9 11.01"/>
          </svg>
        </div>
        <div class="stat-info">
          <span class="stat-value">{{ activeCount }}</span>
          <span class="stat-label">活跃用户</span>
        </div>
      </div>
    </div>

    <section class="roles-panel">
      <div class="roles-panel-header">
        <div>
          <h2>角色管理</h2>
          <p>自定义角色用于配置文档读取权限</p>
        </div>
        <button class="add-user-btn" @click="handleCreateRole">新增角色</button>
      </div>
      <div class="roles-list">
        <div v-for="role in roles" :key="role.id" class="role-item">
          <div>
            <strong>{{ role.roleName }}</strong>
            <span>{{ role.roleCode }}</span>
            <p>{{ role.description || '暂无描述' }}</p>
          </div>
          <div v-if="role.roleCode !== 'ADMIN'" class="actions">
            <button class="action-btn" title="编辑角色" @click="handleEditRole(role)">编辑</button>
            <button class="action-btn danger" title="删除角色" @click="handleDeleteRole(role)">删除</button>
          </div>
          <el-tag v-else size="small" type="info">系统角色</el-tag>
        </div>
      </div>
    </section>

    <!-- 用户列表 -->
    <div class="table-container">
      <div class="table-wrapper">
        <table class="users-table">
          <thead>
            <tr>
              <th>用户信息</th>
              <th>角色</th>
              <th>状态</th>
              <th>最后登录</th>
              <th>创建时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody v-loading="loading">
            <tr v-for="user in users" :key="user.id">
              <td>
                <div class="user-info">
                  <div class="user-avatar" :class="user.roles?.[0]?.toLowerCase()">
                    {{ user.username?.charAt(0)?.toUpperCase() }}
                  </div>
                  <div class="user-details">
                    <span class="user-name">{{ user.username }}</span>
                    <span class="user-nickname">{{ user.nickname || '-' }}</span>
                  </div>
                </div>
              </td>
              <td>
                <span class="role-badge" :class="user.roles?.[0]?.toLowerCase()">
                  {{ getRoleName(user.roles?.[0]) }}
                </span>
              </td>
              <td>
                <span class="status-badge" :class="user.status?.toLowerCase()">
                  <span class="status-dot"></span>
                  {{ user.status === 'ACTIVE' ? '正常' : '禁用' }}
                </span>
              </td>
              <td class="time-cell">{{ formatTime(user.lastLoginTime) }}</td>
              <td class="time-cell">{{ formatTime(user.createdAt) }}</td>
              <td>
                <div class="actions">
                  <button class="action-btn" title="修改角色" @click="showRoleDialog(user)">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
                      <circle cx="12" cy="7" r="4"/>
                    </svg>
                  </button>
                  <button class="action-btn" title="重置密码" @click="showPasswordDialog(user)">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <rect x="3" y="11" width="18" height="11" rx="2" ry="2"/>
                      <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
                    </svg>
                  </button>
                  <button
                    v-if="user.status === 'ACTIVE'"
                    class="action-btn danger"
                    title="禁用用户"
                    @click="handleDisable(user)"
                  >
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <circle cx="12" cy="12" r="10"/>
                      <line x1="4.93" y1="4.93" x2="19.07" y2="19.07"/>
                    </svg>
                  </button>
                  <button
                    v-else
                    class="action-btn success"
                    title="启用用户"
                    @click="handleEnable(user)"
                  >
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
                      <polyline points="22 4 12 14.01 9 11.01"/>
                    </svg>
                  </button>
                </div>
              </td>
            </tr>
            <tr v-if="!loading && users.length === 0">
              <td colspan="6" class="empty-cell">暂无用户数据</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- 添加用户对话框 -->
    <div v-if="addDialogVisible" class="modal-overlay" @click.self="addDialogVisible = false">
      <div class="modal">
        <div class="modal-header">
          <h3>添加用户</h3>
          <button class="modal-close" @click="addDialogVisible = false">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"/>
              <line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </button>
        </div>
        <form class="modal-body" @submit.prevent="handleAdd">
          <div class="form-group">
            <label>用户名</label>
            <input v-model="addForm.username" type="text" placeholder="请输入用户名" required />
          </div>
          <div class="form-group">
            <label>密码</label>
            <input v-model="addForm.password" type="password" placeholder="请输入密码" required />
          </div>
          <div class="form-group">
            <label>昵称</label>
            <input v-model="addForm.nickname" type="text" placeholder="请输入昵称" />
          </div>
          <div class="form-group">
            <label>角色</label>
            <select v-model="addForm.roleCode" class="role-dropdown" required>
              <option v-for="role in roles" :key="role.roleCode" :value="role.roleCode">
                {{ role.roleName }}（{{ role.roleCode }}）
              </option>
            </select>
            <div v-if="false" class="role-select">
              <button
                type="button"
                class="role-option"
                :class="{ active: addForm.roleCode === 'USER' }"
                @click="addForm.roleCode = 'USER'"
              >
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
                  <circle cx="12" cy="7" r="4"/>
                </svg>
                <span>普通用户</span>
              </button>
              <button
                type="button"
                class="role-option admin"
                :class="{ active: addForm.roleCode === 'ADMIN' }"
                @click="addForm.roleCode = 'ADMIN'"
              >
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
                </svg>
                <span>管理员</span>
              </button>
            </div>
          </div>
          <div class="modal-actions">
            <button type="button" class="btn-secondary" @click="addDialogVisible = false">取消</button>
            <button type="submit" class="btn-primary" :disabled="submitLoading">
              {{ submitLoading ? '处理中...' : '确定' }}
            </button>
          </div>
        </form>
      </div>
    </div>

    <!-- 修改角色对话框 -->
    <div v-if="roleDialogVisible" class="modal-overlay" @click.self="roleDialogVisible = false">
      <div class="modal">
        <div class="modal-header">
          <h3>修改用户角色</h3>
          <button class="modal-close" @click="roleDialogVisible = false">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"/>
              <line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </button>
        </div>
        <div class="modal-body">
          <div class="form-group">
            <label>当前用户</label>
            <div class="user-info-inline">
              <span class="user-avatar small">{{ currentUser?.username?.charAt(0)?.toUpperCase() }}</span>
              <span>{{ currentUser?.username }}</span>
            </div>
          </div>
          <div class="form-group">
            <label>选择角色</label>
            <select v-model="roleForm.roleCode" class="role-dropdown" required>
              <option v-for="role in roles" :key="role.roleCode" :value="role.roleCode">
                {{ role.roleName }}（{{ role.roleCode }}）
              </option>
            </select>
            <div v-if="false" class="role-select">
              <button
                type="button"
                class="role-option"
                :class="{ active: roleForm.roleCode === 'USER' }"
                @click="roleForm.roleCode = 'USER'"
              >
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
                  <circle cx="12" cy="7" r="4"/>
                </svg>
                <span>普通用户</span>
              </button>
              <button
                type="button"
                class="role-option admin"
                :class="{ active: roleForm.roleCode === 'ADMIN' }"
                @click="roleForm.roleCode = 'ADMIN'"
              >
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
                </svg>
                <span>管理员</span>
              </button>
            </div>
          </div>
          <div class="modal-actions">
            <button type="button" class="btn-secondary" @click="roleDialogVisible = false">取消</button>
            <button type="button" class="btn-primary" :disabled="submitLoading" @click="handleUpdateRole">
              {{ submitLoading ? '处理中...' : '确定' }}
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- 重置密码对话框 -->
    <div v-if="passwordDialogVisible" class="modal-overlay" @click.self="passwordDialogVisible = false">
      <div class="modal">
        <div class="modal-header">
          <h3>重置密码</h3>
          <button class="modal-close" @click="passwordDialogVisible = false">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"/>
              <line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </button>
        </div>
        <form class="modal-body" @submit.prevent="handleResetPassword">
          <div class="form-group">
            <label>当前用户</label>
            <div class="user-info-inline">
              <span class="user-avatar small">{{ currentUser?.username?.charAt(0)?.toUpperCase() }}</span>
              <span>{{ currentUser?.username }}</span>
            </div>
          </div>
          <div class="form-group">
            <label>新密码</label>
            <input v-model="passwordForm.newPassword" type="password" placeholder="请输入新密码" required />
          </div>
          <div class="modal-actions">
            <button type="button" class="btn-secondary" @click="passwordDialogVisible = false">取消</button>
            <button type="submit" class="btn-primary" :disabled="submitLoading">
              {{ submitLoading ? '处理中...' : '确定' }}
            </button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getUserList, addUser, updateUserRole, resetPassword, disableUser, enableUser } from '@/api/user'
import { createRole, deleteRole, getRoles, updateRole } from '@/api/role'

const loading = ref(false)
const submitLoading = ref(false)
const users = ref([])
const roles = ref([])

// 统计数据
const adminCount = computed(() => users.value.filter(u => u.roles?.[0] === 'ADMIN').length)
const activeCount = computed(() => users.value.filter(u => u.status === 'ACTIVE').length)

// 添加用户
const addDialogVisible = ref(false)
const addForm = reactive({
  username: '',
  password: '',
  nickname: '',
  roleCode: 'USER'
})

// 修改角色
const roleDialogVisible = ref(false)
const currentUser = ref(null)
const roleForm = reactive({
  roleCode: ''
})

// 重置密码
const passwordDialogVisible = ref(false)
const passwordForm = reactive({
  newPassword: ''
})

// 加载用户列表
async function loadUsers() {
  loading.value = true
  try {
    users.value = await getUserList()
  } catch (error) {
    ElMessage.error('加载用户列表失败')
  } finally {
    loading.value = false
  }
}

async function loadRoles() {
  roles.value = await getRoles()
}

const getRoleName = roleCode => roles.value.find(role => role.roleCode === roleCode)?.roleName || roleCode || '-'

async function handleCreateRole() {
  try {
    const { value: roleCode } = await ElMessageBox.prompt('请输入角色编码（字母开头）', '新增角色', {
      inputPattern: /^[A-Za-z][A-Za-z0-9_]{1,49}$/,
      inputErrorMessage: '请输入 2-50 位字母、数字或下划线'
    })
    const { value: roleName } = await ElMessageBox.prompt('请输入角色名称', '新增角色')
    await createRole({ roleCode, roleName, description: '' })
    await loadRoles()
    ElMessage.success('角色已新增')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error.message || '新增角色失败')
  }
}

async function handleEditRole(role) {
  try {
    const { value: roleName } = await ElMessageBox.prompt('请输入新的角色名称', '编辑角色', {
      inputValue: role.roleName
    })
    await updateRole(role.id, { roleName, description: role.description || '' })
    await loadRoles()
    ElMessage.success('角色已更新')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error.message || '更新角色失败')
  }
}

async function handleDeleteRole(role) {
  try {
    await ElMessageBox.confirm(`确定删除角色“${role.roleName}”吗？`, '删除角色', { type: 'warning' })
    await deleteRole(role.id)
    await loadRoles()
    ElMessage.success('角色已删除')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error.message || '删除角色失败')
  }
}

// 显示添加用户对话框
function showAddDialog() {
  addForm.username = ''
  addForm.password = ''
  addForm.nickname = ''
  addForm.roleCode = 'USER'
  addDialogVisible.value = true
}

// 添加用户
async function handleAdd() {
  if (!addForm.username || !addForm.password) {
    ElMessage.error('请填写必填项')
    return
  }

  submitLoading.value = true
  try {
    await addUser(addForm)
    ElMessage.success('添加用户成功')
    addDialogVisible.value = false
    loadUsers()
  } catch (error) {
    ElMessage.error(error.message || '添加用户失败')
  } finally {
    submitLoading.value = false
  }
}

// 显示修改角色对话框
function showRoleDialog(user) {
  currentUser.value = user
  roleForm.roleCode = user.roles?.[0] || 'USER'
  roleDialogVisible.value = true
}

// 修改角色
async function handleUpdateRole() {
  submitLoading.value = true
  try {
    await updateUserRole(currentUser.value.id, roleForm.roleCode)
    ElMessage.success('修改角色成功')
    roleDialogVisible.value = false
    loadUsers()
  } catch (error) {
    ElMessage.error(error.message || '修改角色失败')
  } finally {
    submitLoading.value = false
  }
}

// 显示重置密码对话框
function showPasswordDialog(user) {
  currentUser.value = user
  passwordForm.newPassword = ''
  passwordDialogVisible.value = true
}

// 重置密码
async function handleResetPassword() {
  if (!passwordForm.newPassword) {
    ElMessage.error('请输入新密码')
    return
  }

  submitLoading.value = true
  try {
    await resetPassword(currentUser.value.id, passwordForm.newPassword)
    ElMessage.success('重置密码成功')
    passwordDialogVisible.value = false
  } catch (error) {
    ElMessage.error(error.message || '重置密码失败')
  } finally {
    submitLoading.value = false
  }
}

// 禁用用户
async function handleDisable(user) {
  try {
    await ElMessageBox.confirm('确定要禁用该用户吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await disableUser(user.id)
    ElMessage.success('禁用成功')
    loadUsers()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '禁用失败')
    }
  }
}

// 启用用户
async function handleEnable(user) {
  try {
    await enableUser(user.id)
    ElMessage.success('启用成功')
    loadUsers()
  } catch (error) {
    ElMessage.error(error.message || '启用失败')
  }
}

// 格式化时间
function formatTime(time) {
  if (!time) return '-'
  return new Date(time).toLocaleString('zh-CN')
}

onMounted(() => {
  loadUsers()
  loadRoles()
})
</script>

<style scoped>
.users-page {
  min-height: 100%;
  padding: 32px;
  background: #f8fafc;
}

/* 页面头部 */
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 32px;
}

.header-content {
  flex: 1;
}

.page-title {
  font-size: 1.75rem;
  font-weight: 700;
  color: #0f172a;
  margin: 0 0 8px;
}

.page-subtitle {
  font-size: 0.9375rem;
  color: #64748b;
  margin: 0;
}

.add-user-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 24px;
  border: none;
  border-radius: 12px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  font-size: 0.9375rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
}

.add-user-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 20px -5px rgba(102, 126, 234, 0.4);
}

.add-user-btn svg {
  width: 20px;
  height: 20px;
}

/* 统计卡片 */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 20px;
  margin-bottom: 32px;
}

.roles-panel {
  padding: 24px;
  margin-bottom: 32px;
  background: white;
  border-radius: 16px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.roles-panel-header,
.role-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.roles-panel-header h2,
.role-item p {
  margin: 0;
}

.roles-panel-header p,
.role-item span,
.role-item p {
  color: #64748b;
  font-size: 0.8rem;
}

.roles-list {
  display: grid;
  gap: 10px;
  margin-top: 20px;
}

.role-item {
  padding: 14px 16px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
}

.role-item strong,
.role-item span {
  margin-right: 8px;
}

.role-dropdown {
  width: 100%;
  padding: 11px 12px;
  border: 1px solid #dbe2ea;
  border-radius: 9px;
  background: white;
  color: #0f172a;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 24px;
  background: white;
  border-radius: 16px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.stat-icon {
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
}

.stat-icon svg {
  width: 24px;
  height: 24px;
}

.stat-icon.users {
  background: #dbeafe;
  color: #3b82f6;
}

.stat-icon.admins {
  background: #fef3c7;
  color: #f59e0b;
}

.stat-icon.active {
  background: #d1fae5;
  color: #10b981;
}

.stat-info {
  display: flex;
  flex-direction: column;
}

.stat-value {
  font-size: 1.5rem;
  font-weight: 700;
  color: #0f172a;
}

.stat-label {
  font-size: 0.875rem;
  color: #64748b;
}

/* 表格容器 */
.table-container {
  background: white;
  border-radius: 16px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  overflow: hidden;
}

.table-wrapper {
  overflow-x: auto;
}

.users-table {
  width: 100%;
  border-collapse: collapse;
}

.users-table th {
  padding: 16px 20px;
  text-align: left;
  font-size: 0.75rem;
  font-weight: 600;
  color: #64748b;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  background: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
}

.users-table td {
  padding: 16px 20px;
  border-bottom: 1px solid #f1f5f9;
}

.users-table tbody tr:hover {
  background: #f8fafc;
}

/* 用户信息 */
.user-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-avatar {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  background: #e2e8f0;
  color: #64748b;
  font-weight: 600;
  font-size: 1rem;
}

.user-avatar.admin {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.user-avatar.small {
  width: 32px;
  height: 32px;
  font-size: 0.875rem;
}

.user-details {
  display: flex;
  flex-direction: column;
}

.user-name {
  font-weight: 600;
  color: #0f172a;
}

.user-nickname {
  font-size: 0.8125rem;
  color: #94a3b8;
}

.user-info-inline {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #0f172a;
}

/* 角色标签 */
.role-badge {
  display: inline-flex;
  align-items: center;
  padding: 6px 12px;
  border-radius: 20px;
  font-size: 0.8125rem;
  font-weight: 500;
  background: #f1f5f9;
  color: #64748b;
}

.role-badge.admin {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

/* 状态标签 */
.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 20px;
  font-size: 0.8125rem;
  font-weight: 500;
  background: #d1fae5;
  color: #059669;
}

.status-badge.disabled {
  background: #fee2e2;
  color: #dc2626;
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

.time-cell {
  font-size: 0.875rem;
  color: #64748b;
}

/* 操作按钮 */
.actions {
  display: flex;
  gap: 8px;
}

.action-btn {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 8px;
  background: #f1f5f9;
  color: #64748b;
  cursor: pointer;
  transition: all 0.2s ease;
}

.action-btn:hover {
  background: #e2e8f0;
  color: #0f172a;
}

.action-btn.danger:hover {
  background: #fee2e2;
  color: #dc2626;
}

.action-btn.success:hover {
  background: #d1fae5;
  color: #059669;
}

.action-btn svg {
  width: 18px;
  height: 18px;
}

.empty-cell {
  text-align: center;
  color: #94a3b8;
  padding: 48px !important;
}

/* 模态框 */
.modal-overlay {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(15, 23, 42, 0.5);
  backdrop-filter: blur(4px);
  z-index: 100;
}

.modal {
  width: 100%;
  max-width: 480px;
  background: white;
  border-radius: 20px;
  overflow: hidden;
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.25);
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24px;
  border-bottom: 1px solid #e2e8f0;
}

.modal-header h3 {
  margin: 0;
  font-size: 1.125rem;
  font-weight: 600;
  color: #0f172a;
}

.modal-close {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: #64748b;
  cursor: pointer;
  transition: all 0.2s;
}

.modal-close:hover {
  background: #f1f5f9;
  color: #0f172a;
}

.modal-close svg {
  width: 20px;
  height: 20px;
}

.modal-body {
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.form-group label {
  font-size: 0.875rem;
  font-weight: 500;
  color: #374151;
}

.form-group input {
  height: 48px;
  padding: 0 16px;
  border: 2px solid #e2e8f0;
  border-radius: 10px;
  font-size: 0.9375rem;
  color: #0f172a;
  transition: all 0.2s;
}

.form-group input:focus {
  outline: none;
  border-color: #667eea;
  box-shadow: 0 0 0 4px rgba(102, 126, 234, 0.1);
}

.form-group input::placeholder {
  color: #94a3b8;
}

/* 角色选择 */
.role-select {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.role-option {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 20px;
  border: 2px solid #e2e8f0;
  border-radius: 12px;
  background: white;
  color: #64748b;
  cursor: pointer;
  transition: all 0.2s;
}

.role-option:hover {
  border-color: #cbd5e1;
}

.role-option.active {
  border-color: #667eea;
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.1) 0%, rgba(118, 75, 162, 0.1) 100%);
  color: #667eea;
}

.role-option.admin.active {
  border-color: #764ba2;
  background: linear-gradient(135deg, rgba(118, 75, 162, 0.1) 0%, rgba(102, 126, 234, 0.1) 100%);
  color: #764ba2;
}

.role-option svg {
  width: 28px;
  height: 28px;
}

.role-option span {
  font-size: 0.875rem;
  font-weight: 500;
}

/* 模态框按钮 */
.modal-actions {
  display: flex;
  gap: 12px;
  margin-top: 8px;
}

.btn-secondary,
.btn-primary {
  flex: 1;
  height: 48px;
  border: none;
  border-radius: 10px;
  font-size: 0.9375rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-secondary {
  background: #f1f5f9;
  color: #64748b;
}

.btn-secondary:hover {
  background: #e2e8f0;
}

.btn-primary {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.btn-primary:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

.btn-primary:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

/* 响应式 */
@media (max-width: 768px) {
  .users-page {
    padding: 20px;
  }

  .page-header {
    flex-direction: column;
    gap: 20px;
  }

  .add-user-btn {
    width: 100%;
    justify-content: center;
  }

  .stats-grid {
    grid-template-columns: 1fr;
  }

  .users-table th,
  .users-table td {
    padding: 12px;
  }
}
</style>
