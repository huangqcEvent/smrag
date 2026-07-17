export function createSseParser(onEvent) {
  let buffer = ''
  let eventName = ''
  let dataLines = []

  const dispatch = () => {
    if (dataLines.length > 0) {
      onEvent({
        event: eventName || 'message',
        data: dataLines.join('\n')
      })
    }
    eventName = ''
    dataLines = []
  }

  const processLine = (line) => {
    if (line === '') {
      dispatch()
      return
    }
    if (line.startsWith(':')) return

    const separator = line.indexOf(':')
    const field = separator === -1 ? line : line.slice(0, separator)
    let value = separator === -1 ? '' : line.slice(separator + 1)
    if (value.startsWith(' ')) value = value.slice(1)

    if (field === 'event') eventName = value
    if (field === 'data') dataLines.push(value)
  }

  const consumeCompleteLines = () => {
    while (buffer.length > 0) {
      const lineFeed = buffer.indexOf('\n')
      const carriageReturn = buffer.indexOf('\r')
      const candidates = [lineFeed, carriageReturn].filter(index => index >= 0)
      if (candidates.length === 0) return

      const lineEnd = Math.min(...candidates)
      if (buffer[lineEnd] === '\r' && lineEnd === buffer.length - 1) return

      const separatorLength = buffer[lineEnd] === '\r' && buffer[lineEnd + 1] === '\n' ? 2 : 1
      const line = buffer.slice(0, lineEnd)
      buffer = buffer.slice(lineEnd + separatorLength)
      processLine(line)
    }
  }

  return {
    feed(chunk) {
      buffer += chunk
      consumeCompleteLines()
    },
    flush() {
      this.feed('\n\n')
      buffer = ''
    }
  }
}
