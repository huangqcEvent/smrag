import { ElMessage } from 'element-plus'
import {
  isSilentError,
  toUserErrorMessage
} from './safeError.js'

const isObject = value =>
  value !== null && (typeof value === 'object' || typeof value === 'function')

export function createErrorNotifier(showError) {
  const notifiedErrors = new WeakSet()

  return (error, fallback) => {
    if (isSilentError(error)) return ''
    if (isObject(error) && notifiedErrors.has(error)) return ''

    const message = toUserErrorMessage(error, fallback)
    if (!message) return ''
    if (isObject(error)) notifiedErrors.add(error)
    showError(message)
    return message
  }
}

export const notifyError = createErrorNotifier(message => {
  ElMessage.error(message)
})
