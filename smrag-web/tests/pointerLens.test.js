import test from 'node:test'
import assert from 'node:assert/strict'
import {
  canUsePointerLens,
  createPointerLensController
} from '../src/utils/pointerLens.js'

test('only enables the lens for a fine hover pointer without reduced motion', () => {
  assert.equal(canUsePointerLens({
    canHover: true,
    hasFinePointer: true,
    reducedMotion: false
  }), true)
  assert.equal(canUsePointerLens({
    canHover: false,
    hasFinePointer: true,
    reducedMotion: false
  }), false)
  assert.equal(canUsePointerLens({
    canHover: true,
    hasFinePointer: false,
    reducedMotion: false
  }), false)
  assert.equal(canUsePointerLens({
    canHover: true,
    hasFinePointer: true,
    reducedMotion: true
  }), false)
})

test('coalesces pointer moves and uses the latest coordinates', () => {
  let frame
  const positions = []
  const activeStates = []
  const controller = createPointerLensController({
    requestFrame: callback => {
      frame = callback
      return 1
    },
    cancelFrame() {},
    onPosition: (x, y) => positions.push([x, y]),
    onActive: active => activeStates.push(active)
  })

  controller.enter({ clientX: 10, clientY: 20 })
  controller.move({ clientX: 30, clientY: 40 })

  assert.deepEqual(positions, [])
  frame()
  assert.deepEqual(positions, [[30, 40]])

  controller.leave()
  assert.deepEqual(activeStates, [true, false])
})

test('cancels a pending frame when destroyed', () => {
  const cancelled = []
  const controller = createPointerLensController({
    requestFrame: () => 17,
    cancelFrame: frameId => cancelled.push(frameId),
    onPosition() {},
    onActive() {}
  })

  controller.move({ clientX: 1, clientY: 2 })
  controller.destroy()

  assert.deepEqual(cancelled, [17])
})
