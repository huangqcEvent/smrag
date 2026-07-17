export function createPendingChatMessage(question, sessionId, createTime = new Date().toISOString()) {
  return {
    question,
    answer: '',
    routeType: '',
    references: [],
    citations: [],
    sessionId,
    createTime,
    phase: 'ANALYZING',
    state: 'pending',
    error: ''
  }
}

const updateMetadata = (message, event) => {
  if (event.sessionId) message.sessionId = event.sessionId
  if (event.routeType) message.routeType = event.routeType
}

const updateCitations = (message, event) => {
  if (Array.isArray(event.citations)) message.citations = event.citations
  if (Array.isArray(event.references)) {
    message.references = event.references
  } else if (Array.isArray(event.citations)) {
    message.references = [
      ...new Set(event.citations.map(citation => citation.documentName).filter(Boolean))
    ]
  }
}

export function applyChatStreamEvent(message, event) {
  if (['done', 'error', 'stopped'].includes(message.state)) return message
  switch (event.type) {
    case 'status':
      if (event.phase) message.phase = event.phase
      break
    case 'metadata':
      updateMetadata(message, event)
      break
    case 'delta':
      message.answer += typeof event.content === 'string' ? event.content : ''
      message.state = 'streaming'
      break
    case 'citations':
      updateCitations(message, event)
      break
    case 'done':
      updateMetadata(message, event)
      updateCitations(message, event)
      message.phase = 'DONE'
      message.state = 'done'
      message.error = ''
      break
    case 'error':
      message.phase = 'ERROR'
      message.state = 'error'
      message.error = event.message || '生成回答失败'
      break
    case 'stopped':
      message.phase = 'STOPPED'
      message.state = 'stopped'
      message.error = ''
      break
  }
  return message
}
