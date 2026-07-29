export const DEFAULT_CHUNKING_STRATEGY = 'TOKEN'

export const DOCUMENT_CHUNKING_STRATEGIES = Object.freeze([
  Object.freeze({
    value: 'TOKEN',
    label: 'Token 切块',
    description: '按 Token 控制块大小，兼容原有处理方式。'
  }),
  Object.freeze({
    value: 'RECURSIVE',
    label: '递归切块',
    description: '优先保留段落和句子，并包含少量上下文重叠。'
  })
])

const strategyByValue = Object.fromEntries(
  DOCUMENT_CHUNKING_STRATEGIES.map(strategy => [strategy.value, strategy])
)

export const chunkingStrategyLabel = value =>
  strategyByValue[value]?.label ?? '未知策略'

export const chunkingStrategyDescription = value =>
  strategyByValue[value]?.description ?? '切块方式信息不可用。'
