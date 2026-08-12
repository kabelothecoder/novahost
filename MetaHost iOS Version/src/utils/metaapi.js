export class MetaApiMock {
  constructor() {
    this.subscribers = new Set()
    this.intervalId = null
    this.status = 'idle'
  }

  connect() {
    if (this.status === 'connected') return
    this.status = 'connecting'
    
    // Simulate connection delay
    setTimeout(() => {
      this.status = 'connected'
      this._startDataSimulation()
    }, 1500)
  }

  disconnect() {
    this.status = 'idle'
    if (this.intervalId) {
      clearInterval(this.intervalId)
      this.intervalId = null
    }
  }

  subscribe(callback) {
    this.subscribers.add(callback)
    return () => this.subscribers.delete(callback)
  }

  _startDataSimulation() {
    const pairs = ['BTCUSD', 'EURUSD', 'XAUUSD', 'ETHUSD']
    const directions = ['BULLISH', 'BEARISH']

    this.intervalId = setInterval(() => {
      if (this.status !== 'connected') return

      const pair = pairs[Math.floor(Math.random() * pairs.length)]
      const direction = directions[Math.floor(Math.random() * directions.length)]
      const price = (Math.random() * 60000 + 1.0500).toFixed(4)
      
      const payload = {
        type: 'trade_signal',
        timestamp: Date.now(),
        data: {
          asset: pair,
          direction: direction,
          price: price,
          confidence: Math.floor(Math.random() * 20 + 80), // 80-99%
          tp: (parseFloat(price) * (direction === 'BULLISH' ? 1.02 : 0.98)).toFixed(4),
          sl: (parseFloat(price) * (direction === 'BULLISH' ? 0.99 : 1.01)).toFixed(4)
        }
      }

      this._notify(payload)
    }, 4000)
  }

  _notify(data) {
    this.subscribers.forEach(cb => cb(data))
  }
}

// Export singleton
export const metaApiWS = new MetaApiMock()
