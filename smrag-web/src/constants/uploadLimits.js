/**
 * 上传限制（前端侧口径）。
 *
 * 上限必须与后端保持一致，对应位置：
 * - smrag-core/src/main/resources/application.yml 的 smrag.rag.max-upload-size-bytes
 * - smrag-core/src/main/java/com/hqc/smrag/config/RagProperties.java 的 maxUploadSizeBytes
 *
 * 只允许在这里定义一个上限字面量；其它地方一律引用本常量。
 * 前端校验只是体验优化，不是安全边界：后端 DocumentService#validateFile 仍会独立拒绝。
 */
export const MAX_UPLOAD_SIZE_BYTES = 20 * 1024 * 1024

/** 提示文案用的兆字节数，避免常量改了而文案没改 */
export const UPLOAD_LIMIT_MEGABYTES = MAX_UPLOAD_SIZE_BYTES / (1024 * 1024)
