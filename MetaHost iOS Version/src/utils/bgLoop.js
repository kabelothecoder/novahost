/**
 * Nova Edge — Animated Background Canvas Loop Generator
 * Generates a dark animated mesh gradient, captured as a looping video
 * via MediaRecorder and saved to IndexedDB / Cache Storage as bg_loop.mp4
 */
export function generateBgLoop(canvas) {
  const ctx = canvas.getContext('2d')
  canvas.width = 390   // iPhone 14 Pro width
  canvas.height = 844  // iPhone 14 Pro height

  let frame = 0
  const orbs = Array.from({ length: 6 }, (_, i) => ({
    x: Math.random() * canvas.width,
    y: Math.random() * canvas.height,
    r: 80 + Math.random() * 140,
    vx: (Math.random() - 0.5) * 0.5,
    vy: (Math.random() - 0.5) * 0.5,
    hue: i % 2 === 0 ? 195 : 265,  // cyan : violet
    phase: Math.random() * Math.PI * 2
  }))

  function drawFrame(time) {
    // Deep space background
    ctx.fillStyle = '#050510'
    ctx.fillRect(0, 0, canvas.width, canvas.height)

    // Animated mesh orbs
    orbs.forEach(orb => {
      orb.phase += 0.008
      orb.x += orb.vx
      orb.y += orb.vy
      if (orb.x < -orb.r) orb.x = canvas.width + orb.r
      if (orb.x > canvas.width + orb.r) orb.x = -orb.r
      if (orb.y < -orb.r) orb.y = canvas.height + orb.r
      if (orb.y > canvas.height + orb.r) orb.y = -orb.r

      const pulse = orb.r + Math.sin(orb.phase) * 20
      const alpha = 0.12 + Math.sin(orb.phase * 0.7) * 0.05
      const grad = ctx.createRadialGradient(orb.x, orb.y, 0, orb.x, orb.y, pulse)
      grad.addColorStop(0, `hsla(${orb.hue}, 90%, 65%, ${alpha})`)
      grad.addColorStop(0.5, `hsla(${orb.hue}, 80%, 50%, ${alpha * 0.5})`)
      grad.addColorStop(1, `hsla(${orb.hue}, 70%, 40%, 0)`)
      ctx.fillStyle = grad
      ctx.beginPath()
      ctx.arc(orb.x, orb.y, pulse, 0, Math.PI * 2)
      ctx.fill()
    })

    // Subtle grid overlay
    ctx.strokeStyle = 'rgba(0,212,255,0.03)'
    ctx.lineWidth = 1
    const gridSize = 40
    for (let x = 0; x < canvas.width; x += gridSize) {
      ctx.beginPath(); ctx.moveTo(x, 0); ctx.lineTo(x, canvas.height); ctx.stroke()
    }
    for (let y = 0; y < canvas.height; y += gridSize) {
      ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(canvas.width, y); ctx.stroke()
    }

    // Scanline vignette
    const vignette = ctx.createRadialGradient(
      canvas.width / 2, canvas.height / 2, canvas.height * 0.3,
      canvas.width / 2, canvas.height / 2, canvas.height * 0.85
    )
    vignette.addColorStop(0, 'transparent')
    vignette.addColorStop(1, 'rgba(2,2,9,0.7)')
    ctx.fillStyle = vignette
    ctx.fillRect(0, 0, canvas.width, canvas.height)

    frame++
  }

  return { drawFrame, canvas }
}
