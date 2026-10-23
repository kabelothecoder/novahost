/* <voice-ring> (bar ring around the mic) and <voice-wave> (horizontal waveform band).
   Ported as-is from the Home v4 design (Claude Design project, voice-fx.js).
   Attributes: state (idle|listen|cancel|answer), accent (#hex), mode (full|lite|reduced), gap ("x,r" px, wave only). */
(function () {
  if (!window.customElements || customElements.get('voice-ring')) return;
  const RM = window.matchMedia ? window.matchMedia('(prefers-reduced-motion: reduce)') : null;
  const live = new Set(); let raf = 0;
  function loop(now) {
    raf = 0; let n = 0;
    live.forEach(f => { if (f._vis && f._anim) { n++; if (!f.lite || now - f._drawn > 32) { f._drawn = now; f._draw(now / 1000); } } });
    if (n) raf = requestAnimationFrame(loop);
  }
  function kick() { if (!raf) raf = requestAnimationFrame(loop); }
  function hex(h) { h = String(h || '').trim().replace('#', ''); if (h.length === 3) h = h.split('').map(x => x + x).join(''); const n = parseInt(h, 16); return (h.length !== 6 || isNaN(n)) ? [224, 49, 63] : [(n >> 16) & 255, (n >> 8) & 255, n & 255]; }
  const css = (c, a) => 'rgba(' + c[0] + ',' + c[1] + ',' + c[2] + ',' + a + ')';
  const mixW = (c, t) => c.map(v => Math.round(v + (255 - v) * t));
  function level(t, s) {
    const syl = Math.pow(Math.max(0, Math.sin(t * 5.2 + s)), .6);
    const phrase = .55 + .45 * Math.sin(t * .9 + s * 2);
    return Math.min(1, syl * phrase * (.75 + .25 * Math.sin(t * 17 + s)) + .06);
  }
  const noise = (i, t) => .5 + .5 * Math.sin(i * 1.7 + t * 9.1) * Math.sin(i * .63 - t * 6.3);
  const ATTRS = ['state', 'accent', 'mode', 'gap'];

  class Base extends HTMLElement {
    static get observedAttributes() { return ATTRS; }
    constructor() { super(); this._p = {}; this._vis = true; this._drawn = 0; }
    connectedCallback() {
      const st = this.style; st.position = 'absolute'; st.left = '0'; st.top = '0'; st.right = '0'; st.bottom = '0'; st.display = 'block'; st.pointerEvents = 'none';
      if (!this._cv) { this._cv = document.createElement('canvas'); this._cv.style.cssText = 'position:absolute;left:0;top:0;width:100%;height:100%;display:block'; this.appendChild(this._cv); this._ctx = this._cv.getContext('2d'); }
      live.add(this);
      if (window.ResizeObserver) { this._ro = new ResizeObserver(() => this._cfg()); this._ro.observe(this); }
      if (window.IntersectionObserver) { this._io = new IntersectionObserver(es => { const v = es[es.length - 1].isIntersecting; if (v !== this._vis) { this._vis = v; if (v) kick(); } }, { rootMargin: '120px' }); this._io.observe(this); }
      this._cfg();
    }
    disconnectedCallback() { live.delete(this); this._ro && this._ro.disconnect(); this._io && this._io.disconnect(); }
    attributeChangedCallback(n, o, v) { this._p[n] = v; this._cfg(); }
    _cfg() {
      if (!this._ctx) return;
      const w = this.clientWidth, h = this.clientHeight; if (!w || !h) return;
      const dpr = Math.min(2, window.devicePixelRatio || 1);
      const bw = Math.round(w * dpr), bh = Math.round(h * dpr);
      if (this._cv.width !== bw || this._cv.height !== bh) { this._cv.width = bw; this._cv.height = bh; }
      this.W = w; this.H = h; this.dpr = bw / w;
      const p = this._p; this.rgb = hex(p.accent); this.st = p.state || 'idle';
      this.lite = p.mode === 'lite';
      this.still = p.mode === 'reduced' || !!(RM && RM.matches);
      const g = String(p.gap || '').split(',').map(Number); this.gap = g.length === 2 && g.every(isFinite) ? g : null;
      this._anim = !this.still;
      if (this._anim) kick(); else this._draw(1.3);
    }
    _begin() { const c = this._ctx; c.setTransform(this.dpr, 0, 0, this.dpr, 0, 0); c.clearRect(0, 0, this.W, this.H); return c; }
  }
  ATTRS.forEach(k => Object.defineProperty(Base.prototype, k, { configurable: true, get() { return this._p[k]; }, set(v) { const nv = v == null ? v : String(v); if (this._p[k] === nv) return; this._p[k] = nv; this._cfg(); } }));

  class VoiceRing extends Base {
    _draw(t) {
      const c = this._begin(), W = this.W, H = this.H, cx = W / 2, cy = H / 2;
      const r0 = Math.min(W, H) / 2 - 13, N = 44, st = this.st, acc = this.rgb;
      const lv = st === 'listen' ? level(t, 0) : st === 'answer' ? level(t * .9, 3) : 0;
      c.lineCap = 'round'; c.lineWidth = 2;
      if (st === 'answer' && !this.lite) { c.shadowColor = css(acc, .9); c.shadowBlur = 6; }
      for (let i = 0; i < N; i++) {
        const a = i / N * Math.PI * 2 - Math.PI / 2;
        let len, col;
        if (st === 'listen') { len = 2 + 10 * lv * (.35 + .65 * noise(i, t)); col = 'rgba(255,255,255,.92)'; }
        else if (st === 'answer') { len = 2 + 10 * lv * (.6 + .4 * noise(i * .5, t)); col = css(mixW(acc, .25), .65 + .35 * lv); }
        else if (st === 'cancel') { len = 2.5; col = 'rgba(255,128,128,.7)'; }
        else { len = 2.5 + 3.5 * (.5 + .5 * Math.sin(t * 1.6 - i * .42)); col = css(mixW(acc, .6), .55); }
        const ca = Math.cos(a), sa = Math.sin(a);
        c.strokeStyle = col; c.beginPath(); c.moveTo(cx + ca * r0, cy + sa * r0); c.lineTo(cx + ca * (r0 + len), cy + sa * (r0 + len)); c.stroke();
      }
      c.shadowBlur = 0;
    }
  }
  class VoiceWave extends Base {
    _draw(t) {
      const c = this._begin(), W = this.W, H = this.H, cy = H / 2, st = this.st, acc = this.rgb;
      const lv = st === 'listen' ? level(t, 0) : st === 'answer' ? level(t * .9, 3) : .12;
      const step = 5, half = H / 2 - 6, g = this.gap;
      c.lineCap = 'round'; c.lineWidth = 2.5;
      const base = st === 'cancel' ? 'rgba(255,128,128,' : st === 'answer' ? css(mixW(acc, .3), 1).replace(/,1\)$/, ',') : 'rgba(255,255,255,';
      for (let x = 14, i = 0; x < W - 14; x += step, i++) {
        let env = Math.min(1, (x - 8) / 40, (W - 8 - x) / 40);
        if (g) { const d = Math.abs(x - g[0]) - g[1]; if (d < 0) continue; env *= Math.min(1, d / 26); }
        const n = st === 'cancel' ? .5 : noise(i * .8, t);
        const amp = Math.max(1.2, half * env * (st === 'cancel' ? .1 : lv * (.25 + .75 * n)));
        c.strokeStyle = base + (st === 'cancel' ? .55 : .5 + .45 * env) + ')';
        c.beginPath(); c.moveTo(x, cy - amp); c.lineTo(x, cy + amp); c.stroke();
      }
    }
  }
  customElements.define('voice-ring', VoiceRing);
  customElements.define('voice-wave', class extends VoiceWave {});
})();
