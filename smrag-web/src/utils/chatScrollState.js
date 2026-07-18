export const shouldAutoScroll = isNearBottom => isNearBottom

export const resetScrollAfterHistoryLoad = () => ({
  isNearBottom: true,
  showScrollButton: false
})
