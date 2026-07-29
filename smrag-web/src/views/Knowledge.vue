<template>
  <section class="knowledge-page">
    <header class="page-header">
      <div>
        <h1>{{ copy.pageTitle }}</h1>
        <p>{{ copy.pageDescription }}</p>
      </div>
      <el-button v-if="authStore.isAdmin" type="primary" @click="startCreate">
        <el-icon><Plus /></el-icon>
        {{ copy.createBase }}
      </el-button>
    </header>

    <div class="workspace">
      <aside class="base-navigation" :aria-label="copy.baseListAriaLabel">
        <div class="navigation-search">
          <el-input
            v-model="baseSearchKeyword"
            clearable
            :placeholder="copy.baseSearchPlaceholder"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
        </div>

        <div class="base-list">
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
              <small>{{ `${base.documentCount ?? 0} ${copy.documentCountSuffix}` }}</small>
            </span>
            <span v-if="base.status === 'DISABLED'" class="disabled-label">{{ copy.disabled }}</span>
          </button>

          <div v-if="visibleBases.length === 0" class="navigation-empty">
            <el-icon><Collection /></el-icon>
            <strong>{{ navigationEmptyTitle }}</strong>
            <span>{{ navigationEmptyDescription }}</span>
          </div>
        </div>
      </aside>

      <main class="base-content">
        <section v-if="editorMode" class="base-editor" aria-labelledby="editor-title">
          <div class="section-title">
            <div>
              <span class="eyebrow">{{ editorMode === 'create' ? copy.createEyebrow : copy.editEyebrow }}</span>
              <h2 id="editor-title">{{ editorTitle }}</h2>
            </div>
            <el-button text @click="editorMode = ''">{{ copy.collapse }}</el-button>
          </div>
          <div class="editor-grid">
            <label>
              <span>{{ copy.baseNameLabel }}</span>
              <el-input v-model="baseForm.name" maxlength="128" />
            </label>
            <label v-if="editorMode === 'create'">
              <span>{{ copy.baseCodeLabel }}</span>
              <el-input v-model="baseForm.code" maxlength="64" placeholder="research-materials" />
            </label>
            <label class="full-row">
              <span>{{ copy.baseDescriptionLabel }}</span>
              <el-input
                v-model="baseForm.description"
                type="textarea"
                :rows="2"
                maxlength="500"
              />
            </label>
            <fieldset class="full-row">
              <legend>{{ copy.baseRolesLegend }}</legend>
              <el-checkbox-group v-model="baseForm.roleIds" class="role-options">
                <el-checkbox
                  v-for="role in configurableRoles"
                  :key="role.id"
                  :value="role.id"
                >
                  {{ `${role.roleName}${copy.fullWidthLeftParen}${role.roleCode}${copy.fullWidthRightParen}` }}
                </el-checkbox>
              </el-checkbox-group>
            </fieldset>
          </div>
          <div class="editor-actions">
            <el-button type="primary" :loading="savingBase" @click="saveBase">
              {{ editorMode === 'create' ? copy.createBase : copy.saveSettings }}
            </el-button>
            <template v-if="editorMode === 'edit' && selectedBase">
              <el-button
                :type="selectedBase.status === 'ACTIVE' ? 'warning' : 'success'"
                plain
                @click="toggleStatus"
              >
                {{ selectedBase.status === 'ACTIVE' ? copy.disableBase : copy.enableBase }}
              </el-button>
              <el-button
                type="danger"
                plain
                :disabled="selectedBase.documentCount > 0"
                @click="removeSelectedBase"
              >
                {{ copy.deleteBase }}
              </el-button>
              <span v-if="selectedBase.documentCount > 0" class="delete-hint">
                {{ copy.deleteBaseHint }}
              </span>
            </template>
          </div>
        </section>

        <template v-else-if="selectedBase">
          <header class="base-header">
            <div>
              <div class="title-line">
                <h2>{{ selectedBase.name }}</h2>
                <el-tag v-if="selectedBase.status === 'DISABLED'" type="warning">
                  {{ copy.disabledStatus }}
                </el-tag>
              </div>
              <p>{{ selectedBase.description || copy.noDescription }}</p>
              <div class="base-roles">
                <span>{{ copy.baseRolesTitle }}</span>
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
                :aria-label="copy.refreshDocuments"
                @click="refreshDocuments"
              >
                <el-icon><Refresh /></el-icon>
                {{ copy.refresh }}
              </el-button>
              <el-button v-if="authStore.isAdmin" @click="startEdit">{{ copy.baseSettings }}</el-button>
            </div>
          </header>

          <section
            v-if="authStore.isAdmin"
            class="upload-strip"
            :class="{ disabled: selectedBase.status !== 'ACTIVE' }"
          >
            <div>
              <strong>{{ copy.uploadSourceTitle }}</strong>
              <span v-if="selectedBase.status === 'ACTIVE'">
                {{ `${copy.uploadSourceDescription} ${selectedChunkingStrategyDescription}` }}
              </span>
              <span v-else>{{ copy.uploadDisabledDescription }}</span>
            </div>
            <div class="upload-controls">
              <el-select
                v-model="uploadChunkingStrategy"
                class="chunking-strategy-select"
                :aria-label="copy.chunkingStrategyPlaceholder"
                :placeholder="copy.chunkingStrategyPlaceholder"
                :disabled="selectedBase.status !== 'ACTIVE' || uploading"
              >
                <el-option
                  v-for="strategy in DOCUMENT_CHUNKING_STRATEGIES"
                  :key="strategy.value"
                  :label="strategy.label"
                  :value="strategy.value"
                />
              </el-select>
              <el-select
                v-model="uploadRoleCodes"
                multiple
                collapse-tags
                :placeholder="copy.documentRolesPlaceholder"
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
                    {{ selectedFile?.name || copy.chooseFile }}
                  </el-button>
                </template>
              </el-upload>
              <el-button
                type="primary"
                :loading="uploading"
                :disabled="!selectedFile || uploadRoleCodes.length === 0 || selectedBase.status !== 'ACTIVE'"
                @click="handleUpload"
              >
                {{ copy.upload }}
              </el-button>
            </div>
          </section>

          <section class="documents-section" :aria-labelledby="copy.documentsTitleId">
            <div class="section-title">
              <div>
                <span class="eyebrow">{{ copy.contentEyebrow }}</span>
                <h2 :id="copy.documentsTitleId">{{ copy.documentsTitle }}</h2>
              </div>
              <span>{{ documentCountLabel }}</span>
            </div>

            <div class="document-toolbar">
              <el-input
                v-model="documentKeyword"
                clearable
                :placeholder="copy.documentSearchPlaceholder"
                @keyup.enter="applyDocumentFilters"
              >
                <template #prefix>
                  <el-icon><Search /></el-icon>
                </template>
              </el-input>
              <el-select
                v-model="documentStatus"
                clearable
                :placeholder="copy.documentStatusPlaceholder"
              >
                <el-option
                  v-for="option in documentStatusOptions"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                />
              </el-select>
              <el-button type="primary" @click="applyDocumentFilters">{{ copy.filter }}</el-button>
              <el-button @click="resetDocumentFilters">{{ copy.reset }}</el-button>
            </div>

            <div
              ref="documentsScrollPanel"
              class="documents-scroll-panel"
              @scroll.passive="handleDocumentsScroll"
            >
              <el-table
                :data="documents"
                v-loading="loading"
                :empty-text="copy.documentsEmptyText"
              >
                <el-table-column :label="copy.documentColumn" min-width="240">
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
                        <small>{{ `#${row.id} - ${row.fileType}` }}</small>
                      </span>
                    </button>
                  </template>
                </el-table-column>
                <el-table-column :label="copy.statusColumn" width="120">
                  <template #default="{ row }">
                    <el-tag :type="statusType(row.status)" effect="plain">
                      {{ statusLabel(row.status) }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column :label="copy.chunkingStrategyColumn" width="130">
                  <template #default="{ row }">
                    <el-tag size="small" effect="plain">
                      {{ chunkingStrategyLabel(row.chunkingStrategy) }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column :label="copy.permissionsColumn" min-width="220">
                  <template #default="{ row }">
                    <div class="permission-cell">
                      <el-tag :type="row.accessible ? 'success' : 'info'" size="small">
                        {{ row.accessible ? copy.readable : copy.notReadable }}
                      </el-tag>
                      <span>
                        {{ filterConfigurableAccessRoles(row.allowedRoles)
                          .map(role => role.roleName).join(copy.listSeparator) || copy.unconfigured }}
                      </span>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column
                  v-if="authStore.isAdmin"
                  :label="copy.actionsColumn"
                  width="230"
                  fixed="right"
                >
                  <template #default="{ row }">
                    <div class="row-actions">
                      <el-button text @click="startPermissionEdit(row)">{{ copy.permissionsAction }}</el-button>
                      <el-button text @click="startMove(row)">{{ copy.moveAction }}</el-button>
                      <el-button text type="danger" @click="removeDocument(row)">{{ copy.deleteAction }}</el-button>
                    </div>
                  </template>
                </el-table-column>
              </el-table>

              <div
                v-if="loadingMoreDocuments || documentPage.hasMore || documents.length > 0"
                class="documents-scroll-footer"
              >
                <span v-if="loadingMoreDocuments">{{ copy.loadingMoreDocuments }}</span>
                <span v-else-if="documentPage.hasMore">{{ copy.scrollForMore }}</span>
                <span v-else-if="documents.length > 0">
                  {{ `${copy.loadedAllPrefix}${documentPage.total}${copy.documentCountSuffix}` }}
                </span>
              </div>
            </div>
          </section>
        </template>

        <section v-else class="content-empty">
          <el-icon><Collection /></el-icon>
          <h2>{{ authStore.isAdmin ? copy.createFirstBase : copy.noAccessibleBases }}</h2>
          <p>
            {{ authStore.isAdmin
              ? copy.createFirstBaseDescription
              : copy.noAccessibleBasesDescription }}
          </p>
          <el-button v-if="authStore.isAdmin" type="primary" @click="startCreate">
            {{ copy.createBase }}
          </el-button>
        </section>
      </main>
    </div>

    <el-dialog v-model="permissionDialog" :title="copy.editDocumentPermissions" width="460px">
      <p class="dialog-note">{{ copy.editDocumentPermissionsHint }}</p>
      <el-checkbox-group v-model="editingRoleCodes" class="dialog-roles">
        <el-checkbox
          v-for="role in selectedBase?.roles || []"
          :key="role.id"
          :value="role.roleCode"
        >
          {{ `${role.roleName}${copy.fullWidthLeftParen}${role.roleCode}${copy.fullWidthRightParen}` }}
        </el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="permissionDialog = false">{{ copy.cancel }}</el-button>
        <el-button type="primary" @click="saveDocumentPermissions">{{ copy.save }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="moveDialog" :title="copy.moveDocumentTitle" width="460px">
      <p class="dialog-note">{{ copy.moveDocumentHint }}</p>
      <el-select v-model="moveTargetCode" :placeholder="copy.moveDocumentPlaceholder" style="width: 100%">
        <el-option
          v-for="base in moveTargets"
          :key="base.id"
          :label="base.name"
          :value="base.code"
        />
      </el-select>
      <template #footer>
        <el-button @click="moveDialog = false">{{ copy.cancel }}</el-button>
        <el-button type="primary" :disabled="!moveTargetCode" @click="confirmMove">
          {{ copy.confirmMove }}
        </el-button>
      </template>
    </el-dialog>

    <DocumentViewer :document="viewerDocument" @close="viewerDocument = null" />
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { Collection, Document, Plus, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import DocumentViewer from '@/components/DocumentViewer.vue'
import { getRoles } from '@/api/role'
import { useAuthStore } from '@/stores/auth'
import { useKnowledgeStore } from '@/stores/knowledge'
import { filterConfigurableAccessRoles } from '@/constants/knowledgeBase'
import {
  DEFAULT_CHUNKING_STRATEGY,
  DOCUMENT_CHUNKING_STRATEGIES,
  chunkingStrategyDescription,
  chunkingStrategyLabel
} from '@/constants/documentChunking'
import { notifyError } from '@/utils/errorFeedback'

const copy = {
  pageTitle: '\u77e5\u8bc6\u5e93',
  pageDescription: '\u6309\u4e1a\u52a1\u8303\u56f4\u7ec4\u7ec7\u6587\u6863\uff0c\u5e76\u7528\u73b0\u6709\u89d2\u8272\u63a7\u5236\u8bbf\u95ee\u3002',
  createBase: '\u65b0\u5efa\u77e5\u8bc6\u5e93',
  baseListAriaLabel: '\u77e5\u8bc6\u5e93\u5217\u8868',
  baseSearchPlaceholder: '\u6309\u540d\u79f0\u6216\u7f16\u7801\u7b5b\u9009\u77e5\u8bc6\u5e93',
  documentCountSuffix: '\u4efd\u6587\u6863',
  disabled: '\u505c\u7528',
  createEyebrow: '\u521b\u5efa',
  editEyebrow: '\u8bbe\u7f6e',
  collapse: '\u6536\u8d77',
  baseNameLabel: '\u540d\u79f0',
  baseCodeLabel: '\u7f16\u7801\uff0c\u521b\u5efa\u540e\u4e0d\u53ef\u4fee\u6539',
  baseDescriptionLabel: '\u8bf4\u660e',
  baseRolesLegend: '\u5141\u8bb8\u8bbf\u95ee\u7684\u89d2\u8272',
  fullWidthLeftParen: '\uff08',
  fullWidthRightParen: '\uff09',
  saveSettings: '\u4fdd\u5b58\u8bbe\u7f6e',
  disableBase: '\u505c\u7528\u77e5\u8bc6\u5e93',
  enableBase: '\u542f\u7528\u77e5\u8bc6\u5e93',
  deleteBase: '\u5220\u9664\u77e5\u8bc6\u5e93',
  deleteBaseHint: '\u8bf7\u5148\u8fc1\u79fb\u6216\u5220\u9664\u5e93\u5185\u6587\u6863',
  disabledStatus: '\u5df2\u505c\u7528',
  noDescription: '\u6682\u65e0\u8bf4\u660e',
  baseRolesTitle: '\u5e93\u6743\u9650',
  refreshDocuments: '\u5237\u65b0\u6587\u6863',
  refresh: '\u5237\u65b0',
  baseSettings: '\u77e5\u8bc6\u5e93\u8bbe\u7f6e',
  uploadSourceTitle: '\u4e0a\u4f20\u539f\u59cb\u6587\u6863',
  uploadSourceDescription: 'PDF\u3001Word \u6216 TXT\uff0c\u89e3\u6790\u540e\u81ea\u52a8\u8fdb\u5165\u6388\u6743\u68c0\u7d22\u3002',
  uploadDisabledDescription: '\u77e5\u8bc6\u5e93\u5df2\u505c\u7528\uff0c\u542f\u7528\u540e\u624d\u80fd\u4e0a\u4f20\u3002',
  documentRolesPlaceholder: '\u6587\u6863\u89d2\u8272',
  chunkingStrategyPlaceholder: '\u5207\u5757\u65b9\u5f0f',
  chooseFile: '\u9009\u62e9\u6587\u4ef6',
  upload: '\u4e0a\u4f20',
  contentEyebrow: '\u5185\u5bb9',
  documentsTitle: '\u6587\u6863',
  documentsTitleId: 'documents-title',
  documentSearchPlaceholder: '\u6309\u6587\u6863\u540d\u79f0\u7b5b\u9009',
  documentStatusPlaceholder: '\u5168\u90e8\u72b6\u6001',
  filter: '\u7b5b\u9009',
  reset: '\u91cd\u7f6e',
  documentsEmptyText: '\u8fd9\u4e2a\u77e5\u8bc6\u5e93\u8fd8\u6ca1\u6709\u6587\u6863',
  documentColumn: '\u6587\u6863',
  statusColumn: '\u72b6\u6001',
  chunkingStrategyColumn: '\u5207\u5757\u65b9\u5f0f',
  permissionsColumn: '\u6587\u6863\u6743\u9650',
  readable: '\u53ef\u9605\u8bfb',
  notReadable: '\u65e0\u9605\u8bfb\u6743\u9650',
  unconfigured: '\u672a\u914d\u7f6e',
  actionsColumn: '\u64cd\u4f5c',
  permissionsAction: '\u6743\u9650',
  moveAction: '\u8fc1\u79fb',
  deleteAction: '\u5220\u9664',
  loadingMoreDocuments: '\u6b63\u5728\u52a0\u8f7d\u66f4\u591a\u6587\u6863\u2026',
  scrollForMore: '\u7ee7\u7eed\u4e0b\u6ed1\u52a0\u8f7d\u66f4\u591a',
  loadedAllPrefix: '\u5df2\u52a0\u8f7d\u5168\u90e8 ',
  createFirstBase: '\u521b\u5efa\u7b2c\u4e00\u4e2a\u77e5\u8bc6\u5e93',
  noAccessibleBases: '\u6682\u65e0\u53ef\u8bbf\u95ee\u7684\u77e5\u8bc6\u5e93',
  createFirstBaseDescription: '\u77e5\u8bc6\u5e93\u521b\u5efa\u540e\uff0c\u518d\u6309\u89d2\u8272\u4e0a\u4f20\u548c\u7ba1\u7406\u6587\u6863\u3002',
  noAccessibleBasesDescription: '\u4f60\u7684\u5f53\u524d\u89d2\u8272\u8fd8\u6ca1\u6709\u88ab\u6388\u6743\u5230\u4efb\u4f55\u77e5\u8bc6\u5e93\u3002',
  editDocumentPermissions: '\u8c03\u6574\u6587\u6863\u6743\u9650',
  editDocumentPermissionsHint: '\u6587\u6863\u89d2\u8272\u53ea\u80fd\u4ece\u5f53\u524d\u77e5\u8bc6\u5e93\u5df2\u6388\u6743\u7684\u89d2\u8272\u4e2d\u9009\u62e9\u3002',
  moveDocumentTitle: '\u8fc1\u79fb\u6587\u6863',
  moveDocumentHint: '\u53ea\u663e\u793a\u5df2\u542f\u7528\u7684\u76ee\u6807\u77e5\u8bc6\u5e93\u3002\u76ee\u6807\u5e93\u5fc5\u987b\u5305\u542b\u8fd9\u4efd\u6587\u6863\u7684\u5168\u90e8\u89d2\u8272\u3002',
  moveDocumentPlaceholder: '\u9009\u62e9\u76ee\u6807\u77e5\u8bc6\u5e93',
  confirmMove: '\u786e\u8ba4\u8fc1\u79fb',
  cancel: '\u53d6\u6d88',
  save: '\u4fdd\u5b58',
  listSeparator: '\u3001',
  noMatchedBases: '\u6ca1\u6709\u5339\u914d\u7684\u77e5\u8bc6\u5e93',
  noMatchedBasesDescription: '\u6362\u4e2a\u5173\u952e\u8bcd\u8bd5\u8bd5\uff0c\u652f\u6301\u6309\u540d\u79f0\u6216\u7f16\u7801\u7b5b\u9009\u3002',
  emptyBases: '\u8fd8\u6ca1\u6709\u77e5\u8bc6\u5e93',
  emptyBasesAdminDescription: '\u521b\u5efa\u7b2c\u4e00\u4e2a\u77e5\u8bc6\u5e93\u540e\u5373\u53ef\u4e0a\u4f20\u6587\u6863\u3002',
  emptyBasesUserDescription: '\u8bf7\u8054\u7cfb\u7ba1\u7406\u5458\u4e3a\u4f60\u7684\u89d2\u8272\u6388\u6743\u3002',
  createBaseTitle: '\u65b0\u5efa\u77e5\u8bc6\u5e93',
  editBasePrefix: '\u8bbe\u7f6e',
  baseSaveValidation: '\u8bf7\u586b\u5199\u540d\u79f0\u5e76\u81f3\u5c11\u9009\u62e9\u4e00\u4e2a\u89d2\u8272',
  baseCreated: '\u77e5\u8bc6\u5e93\u5df2\u521b\u5efa',
  baseMissingAfterRefresh: '\u77e5\u8bc6\u5e93\u5df2\u4e0d\u5b58\u5728\uff0c\u8bf7\u5237\u65b0\u540e\u91cd\u8bd5',
  baseSaved: '\u77e5\u8bc6\u5e93\u8bbe\u7f6e\u5df2\u4fdd\u5b58',
  baseEnabled: '\u77e5\u8bc6\u5e93\u5df2\u542f\u7528',
  baseDisabled: '\u77e5\u8bc6\u5e93\u5df2\u505c\u7528',
  deleteBaseConfirmTitle: '\u5220\u9664\u77e5\u8bc6\u5e93',
  deleteBaseConfirmButton: '\u5220\u9664',
  baseDeleted: '\u77e5\u8bc6\u5e93\u5df2\u5220\u9664',
  uploadSuccess: '\u6587\u6863\u5df2\u4e0a\u4f20\uff0c\u6b63\u5728\u89e3\u6790',
  documentPermissionValidation: '\u6587\u6863\u81f3\u5c11\u9700\u8981\u4e00\u4e2a\u53ef\u8bfb\u89d2\u8272',
  documentPermissionSaved: '\u6587\u6863\u6743\u9650\u5df2\u66f4\u65b0',
  documentMoved: '\u6587\u6863\u5df2\u8fc1\u79fb',
  deleteDocumentTitle: '\u5220\u9664\u6587\u6863',
  documentDeleted: '\u6587\u6863\u5df2\u5220\u9664',
  pending: '\u5f85\u5904\u7406',
  processing: '\u5904\u7406\u4e2d',
  completed: '\u5df2\u5b8c\u6210',
  failed: '\u5931\u8d25',
  deleting: '\u5220\u9664\u4e2d',
  pageLoadFailed: '\u77e5\u8bc6\u5e93\u52a0\u8f7d\u5931\u8d25'
}

const authStore = useAuthStore()
const knowledgeStore = useKnowledgeStore()
const {
  documents,
  loading,
  loadingMoreDocuments,
  knowledgeBases,
  adminKnowledgeBases,
  knowledgeBaseCode,
  documentFilters,
  documentPage
} = storeToRefs(knowledgeStore)

const roles = ref([])
const editorMode = ref('')
const savingBase = ref(false)
const baseSearchKeyword = ref('')
const documentKeyword = ref(documentFilters.value.keyword)
const documentStatus = ref(documentFilters.value.status)
const documentsScrollPanel = ref(null)
const selectedFile = ref(null)
const uploadRoleCodes = ref([])
const uploadChunkingStrategy = ref(DEFAULT_CHUNKING_STRATEGY)
const uploading = ref(false)
const viewerDocument = ref(null)
const permissionDialog = ref(false)
const editingDocument = ref(null)
const editingRoleCodes = ref([])
const moveDialog = ref(false)
const movingDocument = ref(null)
const moveTargetCode = ref('')
const baseForm = reactive({ code: '', name: '', description: '', roleIds: [] })

const documentStatusOptions = [
  { label: copy.pending, value: 'PENDING' },
  { label: copy.processing, value: 'PROCESSING' },
  { label: copy.completed, value: 'COMPLETED' },
  { label: copy.failed, value: 'FAILED' },
  { label: copy.deleting, value: 'DELETING' }
]

const configurableRoles = computed(() =>
  filterConfigurableAccessRoles(roles.value)
)

const rawVisibleBases = computed(() =>
  authStore.isAdmin ? adminKnowledgeBases.value : knowledgeBases.value
)

const visibleBases = computed(() => {
  const keyword = baseSearchKeyword.value.trim().toLowerCase()
  if (!keyword) return rawVisibleBases.value
  return rawVisibleBases.value.filter(base =>
    [base.name, base.code, base.description]
      .filter(value => typeof value === 'string')
      .some(value => value.toLowerCase().includes(keyword))
  )
})

const selectedBase = computed(() =>
  rawVisibleBases.value.find(base => base.code === knowledgeBaseCode.value) || null
)

const editorTitle = computed(() =>
  editorMode.value === 'create'
    ? copy.createBaseTitle
    : `${copy.editBasePrefix} ${selectedBase.value?.name || ''}`
)

const moveTargets = computed(() =>
  adminKnowledgeBases.value.filter(base =>
    base.status === 'ACTIVE' && base.code !== knowledgeBaseCode.value
  )
)

const documentCountLabel = computed(() =>
  documentPage.value.total > 0
    ? `${documents.value.length} / ${documentPage.value.total} ${copy.documentCountSuffix}`
    : `${documents.value.length} ${copy.documentCountSuffix}`
)

const selectedChunkingStrategyDescription = computed(() =>
  chunkingStrategyDescription(uploadChunkingStrategy.value)
)

const navigationEmptyTitle = computed(() =>
  baseSearchKeyword.value.trim() ? copy.noMatchedBases : copy.emptyBases
)

const navigationEmptyDescription = computed(() => {
  if (baseSearchKeyword.value.trim()) {
    return copy.noMatchedBasesDescription
  }
  return authStore.isAdmin
    ? copy.emptyBasesAdminDescription
    : copy.emptyBasesUserDescription
})

watch(selectedBase, base => {
  uploadRoleCodes.value = (base?.roles || []).map(role => role.roleCode)
}, { immediate: true })

watch(documentFilters, filters => {
  documentKeyword.value = filters.keyword
  documentStatus.value = filters.status
}, { deep: true })

onMounted(async () => {
  try {
    const requests = [knowledgeStore.fetchKnowledgeBases()]
    if (authStore.isAdmin) {
      requests.push(knowledgeStore.fetchAdminKnowledgeBases(), getRoles())
    }
    const results = await Promise.all(requests)
    if (authStore.isAdmin) roles.value = results.at(-1) || []
    if (knowledgeBaseCode.value) await knowledgeStore.refreshDocuments()
  } catch (error) {
    notifyError(error, copy.pageLoadFailed)
  }
})

const resetDocumentScroll = () => {
  documentsScrollPanel.value?.scrollTo?.({ top: 0 })
}

const selectBase = async code => {
  editorMode.value = ''
  resetDocumentScroll()
  knowledgeBaseCode.value = code
  await knowledgeStore.refreshDocuments(code)
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
    ElMessage.warning(copy.baseSaveValidation)
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
      ElMessage.success(copy.baseCreated)
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
        throw new Error(copy.baseMissingAfterRefresh)
      }
      await knowledgeStore.updateBaseRoles(editedBaseId, {
        roleIds: baseForm.roleIds,
        version: refreshed.version
      })
      knowledgeBaseCode.value = editedBaseCode
      ElMessage.success(copy.baseSaved)
    }
    editorMode.value = ''
    if (knowledgeBaseCode.value) await knowledgeStore.refreshDocuments()
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
  ElMessage.success(next === 'ACTIVE' ? copy.baseEnabled : copy.baseDisabled)
  editorMode.value = ''
}

const removeSelectedBase = async () => {
  await ElMessageBox.confirm(
    `${copy.deleteBase}${copy.fullWidthLeftParen}${selectedBase.value.name}${copy.fullWidthRightParen}\uff1f`,
    copy.deleteBaseConfirmTitle,
    {
      type: 'warning',
      confirmButtonText: copy.deleteBaseConfirmButton,
      cancelButtonText: copy.cancel
    }
  )
  await knowledgeStore.removeBase(selectedBase.value.id)
  editorMode.value = ''
  ElMessage.success(copy.baseDeleted)
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
      uploadRoleCodes.value,
      uploadChunkingStrategy.value
    )
    selectedFile.value = null
    ElMessage.success(copy.uploadSuccess)
  } finally {
    uploading.value = false
  }
}

const applyDocumentFilters = async () => {
  resetDocumentScroll()
  await knowledgeStore.setDocumentFilters({
    keyword: documentKeyword.value,
    status: documentStatus.value
  })
}

const resetDocumentFilters = async () => {
  documentKeyword.value = ''
  documentStatus.value = ''
  resetDocumentScroll()
  await knowledgeStore.setDocumentFilters({ keyword: '', status: '' })
}

const handleDocumentsScroll = async event => {
  const target = event.target
  if (!target) return
  const distanceToBottom =
    target.scrollHeight - target.scrollTop - target.clientHeight
  if (distanceToBottom <= 80) {
    await knowledgeStore.loadMoreDocuments()
  }
}

const refreshDocuments = async () => {
  resetDocumentScroll()
  await knowledgeStore.refreshDocuments()
}

const startPermissionEdit = document => {
  editingDocument.value = document
  editingRoleCodes.value = filterConfigurableAccessRoles(document.allowedRoles)
    .map(role => role.roleCode)
  permissionDialog.value = true
}

const saveDocumentPermissions = async () => {
  if (editingRoleCodes.value.length === 0) {
    ElMessage.warning(copy.documentPermissionValidation)
    return
  }
  await knowledgeStore.updatePermissions(editingDocument.value.id, editingRoleCodes.value)
  permissionDialog.value = false
  ElMessage.success(copy.documentPermissionSaved)
}

const startMove = document => {
  movingDocument.value = document
  moveTargetCode.value = ''
  moveDialog.value = true
}

const confirmMove = async () => {
  await knowledgeStore.transferDocument(movingDocument.value.id, moveTargetCode.value)
  moveDialog.value = false
  ElMessage.success(copy.documentMoved)
}

const removeDocument = async document => {
  await ElMessageBox.confirm(
    `${copy.deleteAction}${copy.fullWidthLeftParen}${document.name}${copy.fullWidthRightParen}\uff1f`,
    copy.deleteDocumentTitle,
    {
      type: 'warning',
      confirmButtonText: copy.deleteBaseConfirmButton,
      cancelButtonText: copy.cancel
    }
  )
  await knowledgeStore.remove(document.id)
  ElMessage.success(copy.documentDeleted)
}

const statusLabel = status => ({
  PENDING: copy.pending,
  PROCESSING: copy.processing,
  COMPLETED: copy.completed,
  FAILED: copy.failed,
  DELETING: copy.deleting
}[status] || status)

const statusType = status => ({
  COMPLETED: 'success',
  FAILED: 'danger',
  PROCESSING: 'warning'
}[status] || 'info')
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
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  gap: 12px;
  padding: 14px 10px;
  border-right: 1px solid var(--border);
  background: var(--surface);
  overflow: hidden;
}

.navigation-search {
  padding: 0 2px;
}

.base-list {
  min-height: 0;
  overflow-y: auto;
  display: grid;
  gap: 6px;
  padding-right: 2px;
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
.delete-hint,
.documents-scroll-footer {
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
.upload-controls .chunking-strategy-select { width: 150px; }

.documents-section {
  margin-top: 28px;
  display: grid;
  gap: 12px;
}

.section-title h2 { font-size: 1.05rem; }
.section-title > span { color: var(--text-muted); font-size: 0.8rem; }

.document-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.document-toolbar .el-input {
  width: min(340px, 100%);
}

.document-toolbar .el-select {
  width: 160px;
}

.documents-scroll-panel {
  overflow: auto;
  max-height: min(62vh, 720px);
  border: 1px solid var(--border);
  border-radius: 12px;
  background: var(--surface);
}

.documents-scroll-footer {
  display: flex;
  justify-content: center;
  padding: 12px 16px;
  border-top: 1px solid var(--border);
}

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
    border-right: 0;
    border-bottom: 1px solid var(--border);
  }
  .base-list {
    display: flex;
    overflow-x: auto;
    overflow-y: hidden;
    padding-bottom: 4px;
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
  .document-toolbar .el-input,
  .document-toolbar .el-select {
    width: 100%;
  }
  .upload-controls .el-select {
    width: 100%;
  }
}
</style>
