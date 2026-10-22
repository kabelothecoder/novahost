/* <falling-fx> — calm falling-particle layer for NovaHost home screens.
   Ported as-is from the Home v4 design (Claude Design project, falling-fx.js).
   Attributes: fx (currency|coins|gems|pairs|mix|off), intensity (subtle|normal|rich),
   accent (#hex), mode (full|lite|reduced|static), clear ("x,y,r;x,y,r" keep-out zones in px),
   res (canvas resolution multiplier), scale (shape size multiplier), density, seed. */
(function () {
  if (!window.customElements || customElements.get('falling-fx')) return;

  const GOLD = [226, 180, 76];
  const CACHE = new Map();
  const S = 64;
  const SANS = 'Geist, "SF Pro Display", system-ui, sans-serif';
  const MONO = '"Geist Mono", ui-monospace, Menlo, monospace';
  const FILTER_OK = (function () { try { const c = document.createElement('canvas').getContext('2d'); return !!c && typeof c.filter === 'string'; } catch (e) { return false; } })();
  const RM = window.matchMedia ? window.matchMedia('(prefers-reduced-motion: reduce)') : null;

  const clamp = (v, a, b) => v < a ? a : v > b ? b : v;
  const mixc = (c, d, t) => [0, 1, 2].map(i => Math.round(c[i] + (d[i] - c[i]) * t));
  const tint = (c, t) => mixc(c, [255, 255, 255], t);
  const shade = (c, t) => mixc(c, [0, 0, 0], t);
  const css = (c, a) => 'rgba(' + c[0] + ',' + c[1] + ',' + c[2] + ',' + (a == null ? 1 : a) + ')';
  function hex(h) { h = String(h || '').trim().replace('#', ''); if (h.length === 3) h = h.split('').map(x => x + x).join(''); const n = parseInt(h, 16); return (h.length !== 6 || isNaN(n)) ? [224, 49, 63] : [(n >> 16) & 255, (n >> 8) & 255, n & 255]; }
  function rng(seed) { let a = (seed >>> 0) || 1; return function () { a = (a + 0x6D2B79F5) | 0; let t = Math.imul(a ^ (a >>> 15), 1 | a); t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t; return ((t ^ (t >>> 14)) >>> 0) / 4294967296; }; }

  function sprite(key, w, h, o, draw) {
    let s = CACHE.get(key); if (s) return s;
    const blur = o.blur || 0;
    const pad = Math.ceil(Math.max(w, h) * (o.glow ? .34 : .08) + blur * 2.5);
    const cv = document.createElement('canvas');
    cv.width = Math.ceil(w + pad * 2); cv.height = Math.ceil(h + pad * 2);
    const c = cv.getContext('2d'); c.translate(pad, pad);
    if (blur && FILTER_OK) c.filter = 'blur(' + blur + 'px)';
    if (o.glow) { c.shadowColor = o.glow; c.shadowBlur = Math.max(w, h) * .3; }
    draw(c, w, h);
    s = { cv: cv, w: w };
    CACHE.set(key, s);
    if (CACHE.size > 900) CACHE.delete(CACHE.keys().next().value);
    return s;
  }
  function blit(ctx, s, D) { const k = D / s.w, W = s.cv.width * k, H = s.cv.height * k; ctx.drawImage(s.cv, -W / 2, -H / 2, W, H); }
  function circle(c, x, y, r) { c.beginPath(); c.arc(x, y, r, 0, Math.PI * 2); }
  function poly(c, pts, w, h) { c.beginPath(); pts.forEach((p, i) => i ? c.lineTo(p[0] * w, p[1] * h) : c.moveTo(p[0] * w, p[1] * h)); c.closePath(); }
  function rrect(c, x, y, w, h, r) { c.beginPath(); c.moveTo(x + r, y); c.arcTo(x + w, y, x + w, y + h, r); c.arcTo(x + w, y + h, x, y + h, r); c.arcTo(x, y + h, x, y, r); c.arcTo(x, y, x + w, y, r); c.closePath(); }

  function sGlyph(ch, rgb, lite, blur) {
    return sprite('g|' + ch + '|' + rgb + '|' + lite + '|' + blur, S, S, { blur, glow: lite ? null : css(rgb, .6) }, (c, w, h) => {
      c.font = '600 ' + Math.round(h * .9) + 'px ' + SANS; c.textAlign = 'center'; c.textBaseline = 'middle';
      if (lite) c.fillStyle = css(rgb);
      else { const g = c.createLinearGradient(0, h * .1, 0, h * .92); g.addColorStop(0, css(tint(rgb, .72))); g.addColorStop(.45, css(tint(rgb, .22))); g.addColorStop(1, css(shade(rgb, .18))); c.fillStyle = g; }
      c.fillText(ch, w / 2, h * .54);
    });
  }
  function sCoin(sym, lite, blur, glowRgb) {
    return sprite('c|' + sym + '|' + lite + '|' + blur + '|' + glowRgb, S, S, { blur, glow: lite ? null : css(glowRgb, .5) }, (c, w) => {
      const r = w / 2;
      c.textAlign = 'center'; c.textBaseline = 'middle';
      if (lite) {
        c.fillStyle = '#D7A63D'; circle(c, r, r, r); c.fill();
        c.strokeStyle = '#A57B22'; c.lineWidth = r * .12; circle(c, r, r, r * .84); c.stroke();
        c.fillStyle = '#8F661A';
      } else {
        const g = c.createRadialGradient(r * .62, r * .55, r * .08, r, r, r);
        g.addColorStop(0, '#FFF3C8'); g.addColorStop(.38, '#F4CB5C'); g.addColorStop(.78, '#C8972C'); g.addColorStop(1, '#85621A');
        c.fillStyle = g; circle(c, r, r, r); c.fill(); c.shadowBlur = 0;
        c.strokeStyle = 'rgba(122,86,20,.85)'; c.lineWidth = r * .09; circle(c, r, r, r * .86); c.stroke();
        c.strokeStyle = 'rgba(255,238,178,.55)'; c.lineWidth = r * .035; circle(c, r, r, r * .74); c.stroke();
        c.font = '700 ' + Math.round(r * 1.02) + 'px ' + SANS;
        c.fillStyle = 'rgba(255,240,190,.55)'; c.fillText(sym, r - r * .03, r - r * .02);
        c.fillStyle = 'rgba(132,92,22,.92)';
      }
      c.font = '700 ' + Math.round(r * 1.02) + 'px ' + SANS; c.fillText(sym, r, r + r * .04);
    });
  }
  function sEdge(blur) {
    return sprite('e|' + blur, S, S, { blur }, (c, w) => { const r = w / 2; const g = c.createLinearGradient(0, 0, w, 0); g.addColorStop(0, '#5E430F'); g.addColorStop(.5, '#B68A2B'); g.addColorStop(1, '#5E430F'); c.fillStyle = g; circle(c, r, r, r); c.fill(); });
  }
  function sShine() {
    return sprite('shine', S, S, {}, (c, w) => { const r = w / 2; const g = c.createRadialGradient(r * .7, r * .62, 0, r, r, r); g.addColorStop(0, 'rgba(255,255,240,.95)'); g.addColorStop(.35, 'rgba(255,248,215,.35)'); g.addColorStop(1, 'rgba(255,248,215,0)'); c.fillStyle = g; circle(c, r, r, r); c.fill(); });
  }
  function sNote(acc, lite, blur) {
    const w = S * 1.5, h = S * .7;
    return sprite('n|' + acc + '|' + lite + '|' + blur, w, h, { blur, glow: lite ? null : 'rgba(232,192,92,.45)' }, (c) => {
      if (lite) { c.fillStyle = '#E9D396'; rrect(c, 0, 0, w, h, h * .1); c.fill(); c.fillStyle = css(acc); c.beginPath(); c.ellipse(w * .5, h * .5, h * .28, h * .31, 0, 0, Math.PI * 2); c.fill(); return; }
      const g = c.createLinearGradient(0, 0, w, h); g.addColorStop(0, '#FAEDC4'); g.addColorStop(.55, '#E8CD86'); g.addColorStop(1, '#CDA651');
      c.fillStyle = g; rrect(c, 0, 0, w, h, h * .1); c.fill(); c.shadowBlur = 0;
      c.strokeStyle = 'rgba(126,92,30,.5)'; c.lineWidth = h * .04; rrect(c, h * .1, h * .1, w - h * .2, h * .8, h * .06); c.stroke();
      c.strokeStyle = 'rgba(126,92,30,.22)'; c.lineWidth = h * .025;
      for (let i = 0; i < 5; i++) { const x = w * .16 + i * h * .07; c.beginPath(); c.moveTo(x, h * .24); c.lineTo(x, h * .76); c.stroke(); c.beginPath(); c.moveTo(w - x, h * .24); c.lineTo(w - x, h * .76); c.stroke(); }
      const og = c.createRadialGradient(w * .47, h * .42, 0, w * .5, h * .5, h * .36); og.addColorStop(0, css(tint(acc, .45))); og.addColorStop(1, css(acc));
      c.fillStyle = og; c.beginPath(); c.ellipse(w * .5, h * .5, h * .28, h * .31, 0, 0, Math.PI * 2); c.fill();
      c.strokeStyle = css(shade(acc, .35), .8); c.lineWidth = h * .03; c.stroke();
    });
  }
  function sBar(lite, blur) {
    const w = S * 1.25, h = S * .62;
    return sprite('b|' + lite + '|' + blur, w, h, { blur, glow: lite ? null : 'rgba(240,190,80,.5)' }, (c) => {
      const front = [[.03, .94], [.97, .94], [.85, .44], [.15, .44]], top = [[.15, .44], [.85, .44], [.75, .1], [.25, .1]];
      if (lite) { c.fillStyle = '#C8962F'; poly(c, front, w, h); c.fill(); c.fillStyle = '#EAC560'; poly(c, top, w, h); c.fill(); return; }
      let g = c.createLinearGradient(0, h * .44, 0, h * .94); g.addColorStop(0, '#E9B84F'); g.addColorStop(1, '#94681A'); c.fillStyle = g; poly(c, front, w, h); c.fill(); c.shadowBlur = 0;
      g = c.createLinearGradient(0, h * .1, w, h * .44); g.addColorStop(0, '#FFF2C0'); g.addColorStop(.6, '#F1C962'); g.addColorStop(1, '#D9A841'); c.fillStyle = g; poly(c, top, w, h); c.fill();
      c.strokeStyle = 'rgba(255,248,214,.85)'; c.lineWidth = h * .035; c.beginPath(); c.moveTo(w * .15, h * .44); c.lineTo(w * .85, h * .44); c.stroke();
      c.fillStyle = 'rgba(168,120,34,.45)'; rrect(c, w * .38, h * .2, w * .24, h * .14, h * .03); c.fill();
    });
  }
  const GT1 = [.3, .1], GT2 = [.7, .1], GL = [.05, .36], GR = [.95, .36], GM1 = [.37, .36], GM2 = [.63, .36], GB = [.5, .95];
  const FACETS = [[[GL, GT1, GM1], 1.45], [[GT1, GT2, GM2, GM1], 1.75], [[GT2, GR, GM2], 1.1], [[GL, GM1, GB], .8], [[GM1, GM2, GB], 1.15], [[GM2, GR, GB], .55]];
  function sGem(rgb, lite, blur) {
    const w = S * .95, h = S * .82;
    return sprite('m|' + rgb + '|' + lite + '|' + blur, w, h, { blur, glow: lite ? null : css(rgb, .6) }, (c) => {
      if (lite) { c.fillStyle = css(rgb); poly(c, [GT1, GT2, GR, GB, GL], w, h); c.fill(); c.fillStyle = css(tint(rgb, .35)); poly(c, [GT1, GT2, GM2, GM1], w, h); c.fill(); return; }
      c.fillStyle = css(rgb); poly(c, [GT1, GT2, GR, GB, GL], w, h); c.fill(); c.shadowBlur = 0;
      FACETS.forEach(([pts, k]) => { c.fillStyle = css(k >= 1 ? tint(rgb, (k - 1) * .9) : shade(rgb, (1 - k) * .9)); poly(c, pts, w, h); c.fill(); });
      c.strokeStyle = 'rgba(255,255,255,.4)'; c.lineWidth = Math.max(.6, h * .018); FACETS.forEach(([pts]) => { poly(c, pts, w, h); c.stroke(); });
    });
  }
  function sSpark(rgb) {
    return sprite('s|' + rgb, S, S, {}, (c, w) => {
      const r = w / 2;
      const g = c.createRadialGradient(r, r, 0, r, r, r * .5); g.addColorStop(0, 'rgba(255,255,255,.9)'); g.addColorStop(1, css(tint(rgb, .5), 0)); c.fillStyle = g; circle(c, r, r, r * .5); c.fill();
      c.fillStyle = 'rgba(255,255,255,.95)';
      c.beginPath(); c.moveTo(r, 0); c.quadraticCurveTo(r, r, w, r); c.quadraticCurveTo(r, r, r, w); c.quadraticCurveTo(r, r, 0, r); c.quadraticCurveTo(r, r, r, 0); c.fill();
    });
  }
  function sChar(ch, rgb, head, lite, blur) {
    const w = S * .64, h = S;
    return sprite('p|' + ch + '|' + rgb + '|' + head + '|' + lite + '|' + blur, w, h, { blur, glow: lite ? null : css(rgb, head ? .9 : .45) }, (c) => {
      c.font = (head ? '700 ' : '600 ') + Math.round(h * .78) + 'px ' + MONO; c.textAlign = 'center'; c.textBaseline = 'middle';
      c.fillStyle = css(head ? tint(rgb, lite ? .4 : .72) : rgb); c.fillText(ch, w / 2, h * .52);
    });
  }

  const LAYERS = [
    { size: .58, v: [10, 14], a: .5, blur: 0 },
    { size: .9, v: [16, 22], a: .82, blur: 0 },
    { size: 1.45, v: [26, 33], a: .6, blur: 5 }
  ];
  const BASE = { glyph: 26, coin: 22, note: 40, bar: 30, gem: 22, pair: 13 };
  const STYLES = {
    currency: { n: 14, k: { glyph: 1 } },
    coins: { n: 11, k: { coin: .66, note: .34 } },
    gems: { n: 12, k: { bar: .42, gem: .58 } },
    pairs: { n: 7, k: { pair: 1 } },
    mix: { n: 13, k: { glyph: .3, coin: .16, note: .08, bar: .1, gem: .16, pair: .2 } }
  };
  const INT = { subtle: .6, normal: 1, rich: 1.6 };
  const SYMS = ['R', '$', '€', '£', '¥'];
  const PAIRS = ['XAUUSD', 'NAS100', 'EURUSD', 'US30'];

  const live = new Set(); let raf = 0;
  function loop(now) {
    raf = 0; let n = 0;
    live.forEach(f => { if (f._vis && f._anim) n++; });
    const busy = n > 4;
    live.forEach(f => {
      if (!f._vis || !f._anim) return;
      const iv = (busy || f.lite) ? 32 : 0;
      if (now - f._drawn >= iv) { f._drawn = now; f._frame(now / 1000, false); }
    });
    if (n) raf = requestAnimationFrame(loop);
  }
  function kick() { if (!raf) raf = requestAnimationFrame(loop); }
  function refreshFonts() { CACHE.clear(); live.forEach(f => { if (!f._anim) f._config(); }); }
  if (document.fonts) {
    try { document.fonts.addEventListener('loadingdone', refreshFonts); } catch (e) {}
    try { Promise.all([document.fonts.load('600 40px Geist'), document.fonts.load('700 40px Geist'), document.fonts.load('600 40px "Geist Mono"')]).then(refreshFonts, () => {}); } catch (e) {}
  }

  const ATTRS = ['fx', 'intensity', 'accent', 'mode', 'clear', 'res', 'scale', 'density', 'seed'];
  class FallingFx extends HTMLElement {
    static get observedAttributes() { return ATTRS; }
    constructor() { super(); this._p = {}; this.parts = []; this._vis = true; this._anim = false; this._drawn = 0; this._last = 0; this._t = 0; this.zones = []; }
    connectedCallback() {
      const st = this.style; st.position = 'absolute'; st.left = '0'; st.top = '0'; st.right = '0'; st.bottom = '0'; st.display = 'block'; st.pointerEvents = 'none'; st.overflow = 'hidden';
      if (!this._cv) {
        this._cv = document.createElement('canvas');
        this._cv.style.cssText = 'position:absolute;left:0;top:0;width:100%;height:100%;display:block';
        this.appendChild(this._cv); this._ctx = this._cv.getContext('2d');
      }
      live.add(this);
      if (window.ResizeObserver) { this._ro = new ResizeObserver(() => this._config()); this._ro.observe(this); }
      if (window.IntersectionObserver) {
        this._io = new IntersectionObserver(es => { const v = es[es.length - 1].isIntersecting; if (v !== this._vis) { this._vis = v; this._last = 0; if (v) kick(); } }, { rootMargin: '160px' });
        this._io.observe(this);
      }
      if (RM) { this._rm = () => this._config(); try { RM.addEventListener('change', this._rm); } catch (e) { RM.addListener && RM.addListener(this._rm); } }
      this._config();
    }
    disconnectedCallback() {
      live.delete(this);
      this._ro && this._ro.disconnect(); this._io && this._io.disconnect();
      if (RM && this._rm) { try { RM.removeEventListener('change', this._rm); } catch (e) { RM.removeListener && RM.removeListener(this._rm); } }
    }
    attributeChangedCallback(n, o, v) { this._p[n] = v; this._config(); }

    _config() {
      if (!this._ctx) return;
      const p = this._p, w = this.clientWidth, h = this.clientHeight;
      if (!w || !h) return;
      const dpr = Math.min(1.5, window.devicePixelRatio || 1) * (parseFloat(p.res) || 1);
      const bw = Math.max(1, Math.round(w * dpr)), bh = Math.max(1, Math.round(h * dpr));
      if (this._cv.width !== bw || this._cv.height !== bh) { this._cv.width = bw; this._cv.height = bh; }
      this.W = w; this.H = h; this.dpr = bw / w;
      const mode = p.mode || 'full';
      this.lite = mode === 'lite';
      this.still = mode === 'reduced' || mode === 'static' || !!(RM && RM.matches);
      this.rgb = hex(p.accent);
      this._sc = parseFloat(p.scale) || 1;
      this.zones = String(p.clear || '').split(';').map(s => s.split(',').map(Number)).filter(z => z.length === 3 && z.every(isFinite));
      const fx = STYLES[p.fx] ? p.fx : (p.fx === 'off' ? 'off' : 'mix');
      const sig = [fx, p.intensity, this.lite, p.scale, p.density, p.seed, Math.round(w / 20), Math.round(h / 20)].join('|');
      if (sig !== this._sig) { this._sig = sig; this._build(fx); }
      this._anim = !this.still && this.parts.length > 0;
      if (this._anim) kick(); else this._frame(this._t, true);
    }
    _build(fx) {
      const p = this._p; this.parts = [];
      if (fx === 'off') return;
      const st = STYLES[fx];
      const R = this.r = rng(((parseInt(p.seed, 10) || 7) * 2654435761) >>> 0);
      const area = (this.W * this.H) / (390 * 844) / (this._sc * this._sc);
      const n = Math.max(4, Math.round(st.n * (INT[p.intensity] || 1) * area * (parseFloat(p.density) || 1) * (this.lite ? .55 : 1)));
      const ks = Object.keys(st.k), kinds = [];
      ks.forEach(k => { const c = Math.round(st.k[k] * n); for (let i = 0; i < c; i++) kinds.push(k); });
      while (kinds.length < n) kinds.push(ks[Math.floor(R() * ks.length)]);
      kinds.length = n;
      const shuf = a => { for (let i = a.length - 1; i > 0; i--) { const j = Math.floor(R() * (i + 1)); const t = a[i]; a[i] = a[j]; a[j] = t; } return a; };
      shuf(kinds);
      const slots = shuf(kinds.map((_, i) => i));
      kinds.forEach((k, i) => {
        const r = R();
        const q = { kind: k, slot: slots[i], n: n, layer: r < .45 ? 0 : r < .8 ? 1 : 2 };
        this._spawn(q, true); this.parts.push(q);
      });
      this.parts.sort((a, b) => a.layer - b.layer);
    }
    _spawn(q, init) {
      const R = this.r, L = LAYERS[q.layer], sc = this._sc, W = this.W, H = this.H;
      q.size = BASE[q.kind] * L.size * sc * (.85 + R() * .3);
      q.vy = (L.v[0] + R() * (L.v[1] - L.v[0])) * (.55 + .45 * sc) * (q.kind === 'note' ? .72 : q.kind === 'pair' ? 1.1 : 1);
      q.x0 = ((q.slot + .15 + R() * .7) / q.n) * W;
      q.amp = q.kind === 'pair' ? 0 : (4 + R() * 9) * sc * (q.kind === 'note' ? 2.2 : 1);
      q.fq = (.08 + R() * .14) * Math.PI * 2; q.ph = R() * 6.283;
      q.rot = (R() - .5) * .8; q.vr = (R() - .5) * .5;
      q.spin = R() * 6.283; q.vs = (.6 + R() * .9) * (R() < .5 ? -1 : 1);
      q.gold = (q.kind === 'coin' || q.kind === 'bar' || q.kind === 'note') ? true : R() < (q.kind === 'glyph' ? .55 : q.kind === 'pair' ? .35 : 0);
      q.glowAcc = R() < .5;
      q.crystal = q.kind === 'gem' && R() < .4;
      q.sym = SYMS[Math.floor(R() * SYMS.length)];
      if (q.kind === 'pair') { q.text = PAIRS[Math.floor(R() * PAIRS.length)]; q.step = q.size * 1.18; q.ext = q.text.length * q.step; }
      else q.ext = q.size;
      q.y = init ? -q.size + R() * (H + q.size) : -q.size - R() * H * .25;
      q.spark = 1 + R() * 4;
    }
    _fade(x, y, s) {
      let f = clamp((y + s) / 70, 0, 1) * clamp((this.H - y) / 90, 0, 1);
      const Z = this.zones;
      for (let i = 0; i < Z.length; i++) {
        const z = Z[i], d = Math.hypot(x - z[0], y - z[1]) - z[2], ramp = z[2] * .7;
        if (d < s * .5 + ramp) { const u = clamp((d - s * .5) / ramp, 0, 1); f *= u * u * (3 - 2 * u); }
      }
      return f;
    }
    _frame(t, still) {
      const ctx = this._ctx; if (!ctx || !this.W) return;
      const dt = still ? 0 : (this._last ? Math.min(.06, t - this._last) : 0);
      if (!still) this._last = t;
      this._t = t;
      const W = this.W, H = this.H, lite = this.lite, acc = this.rgb, crystal = tint(acc, .78);
      ctx.setTransform(this.dpr, 0, 0, this.dpr, 0, 0); ctx.clearRect(0, 0, W, H);
      for (const q of this.parts) {
        q.y += q.vy * dt; q.rot += q.vr * dt; q.spin += q.vs * dt;
        if ((q.kind === 'pair' ? q.y - q.ext : q.y - q.size) > H + 10) { this._spawn(q, false); continue; }
        const L = LAYERS[q.layer], blur = lite ? 0 : L.blur, base = L.a * (lite ? .9 : 1);
        const x = q.x0 + Math.sin(t * q.fq + q.ph) * q.amp;
        if (q.kind === 'pair') { this._pair(ctx, q, x, base, blur, acc); continue; }
        const a = base * this._fade(x, q.y, q.size); if (a < .01) continue;
        const col = q.gold ? GOLD : acc;
        ctx.save(); ctx.translate(x, q.y); ctx.globalAlpha = a;
        if (q.kind === 'glyph') {
          ctx.rotate(Math.sin(t * .35 + q.ph) * .22); blit(ctx, sGlyph(q.sym, col, lite, blur), q.size);
        } else if (q.kind === 'coin') {
          const cs = Math.cos(q.spin), sn = Math.sin(q.spin), sx = Math.max(.1, Math.abs(cs));
          ctx.rotate(q.rot * .5);
          if (!lite) { ctx.save(); ctx.translate((sn > 0 ? 1 : -1) * q.size * .09 * Math.abs(sn), 0); ctx.scale(sx, 1); blit(ctx, sEdge(blur), q.size); ctx.restore(); }
          ctx.scale(sx, 1); blit(ctx, sCoin(q.sym, lite, blur, q.glowAcc ? acc : GOLD), q.size);
          if (!lite && cs > 0) { ctx.globalAlpha = a * Math.pow(cs, 6) * .8; blit(ctx, sShine(), q.size * .9); }
        } else if (q.kind === 'note') {
          ctx.rotate(q.rot + Math.sin(t * .9 + q.ph) * .35);
          ctx.transform(1, 0, Math.cos(t * .7 + q.ph * 1.3) * .3, 1, 0, 0);
          ctx.scale(1, .28 + .72 * Math.abs(Math.cos(t * .8 + q.ph)));
          blit(ctx, sNote(acc, lite, blur), q.size);
        } else if (q.kind === 'bar') {
          ctx.rotate(q.rot); ctx.scale(1, .5 + .5 * Math.abs(Math.cos(q.spin * .5))); blit(ctx, sBar(lite, blur), q.size);
        } else if (q.kind === 'gem') {
          const gc = q.crystal ? crystal : acc;
          ctx.rotate(Math.sin(t * .4 + q.ph) * .3);
          ctx.save(); ctx.scale(.45 + .55 * Math.abs(Math.cos(q.spin * .45)), 1); blit(ctx, sGem(gc, lite, blur), q.size); ctx.restore();
          if (!lite && !still) {
            q.spark -= dt;
            if (q.spark < 0) {
              const u = -q.spark / .75;
              if (u >= 1) q.spark = 2.5 + this.r() * 4;
              else { const k = Math.sin(Math.PI * u); ctx.globalAlpha = a * k; ctx.globalCompositeOperation = 'lighter'; ctx.translate(q.size * .18, -q.size * .2); blit(ctx, sSpark(gc), q.size * 1.3 * (.4 + .6 * k)); ctx.globalCompositeOperation = 'source-over'; }
            }
          }
        }
        ctx.restore();
      }
      ctx.globalAlpha = 1;
    }
    _pair(ctx, q, x, base, blur, acc) {
      const col = q.gold ? GOLD : acc, n = q.text.length, D = q.size * .64;
      for (let i = 0; i < n; i++) {
        const y = q.y - (n - 1 - i) * q.step, head = i === n - 1;
        const a = base * (.34 + .66 * Math.pow(i / (n - 1), 1.4)) * this._fade(x, y, q.size); if (a < .01) continue;
        ctx.globalAlpha = a; ctx.save(); ctx.translate(x, y); blit(ctx, sChar(q.text[i], col, head, this.lite, blur), D); ctx.restore();
      }
    }
  }
  ATTRS.forEach(k => Object.defineProperty(FallingFx.prototype, k, {
    configurable: true,
    get() { return this._p[k]; },
    set(v) { const nv = v == null ? v : String(v); if (this._p[k] === nv) return; this._p[k] = nv; this._config(); }
  }));
  customElements.define('falling-fx', FallingFx);
})();
