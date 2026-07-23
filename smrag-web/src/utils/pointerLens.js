export function canUsePointerLens({
  canHover,
  hasFinePointer,
  reducedMotion
}) {
  return canHover && hasFinePointer && !reducedMotion
}

export function isMousePointer(event) {
  return event?.pointerType === 'mouse'
}

export function toLocalPointerPosition({ clientX, clientY }, { left, top }) {
  return {
    x: clientX - left,
    y: clientY - top
  }
}

export function createPointerLensController({
  requestFrame = callback => globalThis.requestAnimationFrame(callback),
  cancelFrame = frameId => globalThis.cancelAnimationFrame(frameId),
  onPosition,
  onActive
}) {
  let frameId = null
  let pendingPosition = null

  const flush = () => {
    frameId = null
    if (!pendingPosition) return
    const { x, y } = pendingPosition
    pendingPosition = null
    onPosition(x, y)
  }

  const schedule = event => {
    pendingPosition = {
      x: event.clientX,
      y: event.clientY
    }
    if (frameId == null) {
      frameId = requestFrame(flush)
    }
  }

  return {
    enter(event) {
      onActive(true)
      schedule(event)
    },
    move: schedule,
    leave() {
      onActive(false)
    },
    destroy() {
      if (frameId != null) cancelFrame(frameId)
      frameId = null
      pendingPosition = null
    }
  }
}
