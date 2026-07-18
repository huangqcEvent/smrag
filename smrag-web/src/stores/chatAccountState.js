export function resetChatAccountState(state, controller) {
  controller?.abort()
  state.currentSessionId = ''
  state.chatHistory = []
  state.sessionList = []
  state.loading = false
}
