import {
  MAX_UPLOAD_SIZE_BYTES,
  UPLOAD_LIMIT_MEGABYTES
} from '../constants/uploadLimits.js'

const fail = (code, message) => ({ ok: false, code, message })

/**
 * 上传前的本地预校验。
 *
 * 判定顺序与后端 DocumentService#validateFile 对齐（先空、后超限），
 * 保证同一个文件在两端得到同样的结论，避免「前端放行、后端拒绝」。
 *
 * @param file 浏览器 File 对象（或等价的 { name, size }）
 * @returns {{ ok: boolean, code: 'MISSING'|'EMPTY'|'TOO_LARGE'|null, message: string }}
 */
export function validateUploadFile(file) {
  if (!file || typeof file !== 'object') {
    return fail('MISSING', '请先选择要上传的文件')
  }

  const size = Number(file.size)
  if (!Number.isFinite(size) || size <= 0) {
    return fail('EMPTY', '文件内容为空，无法解析')
  }

  if (typeof file.name === 'string' && file.name.trim() === '') {
    return fail('MISSING', '文件名为空，无法上传')
  }

  if (size > MAX_UPLOAD_SIZE_BYTES) {
    return fail(
      'TOO_LARGE',
      `文件超过 ${UPLOAD_LIMIT_MEGABYTES}MB 上限，请压缩或拆分后重试`
    )
  }

  return { ok: true, code: null, message: '' }
}
