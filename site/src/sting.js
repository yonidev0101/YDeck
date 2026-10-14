// The logo sting "The deal" (store/brand/sting/sting.html), as a reusable canvas renderer for the site:
// eight cards riffled and dealt into the two 2x2 grids, the Y grows from where the stack stood, the last card
// flips to mint, and (optionally) "YDeck" rises under the mark. Same shapes and timings as the film.
window.YDeckSting = (function () {
  var WHITE = '#FFFFFF', MINT = '#5EF2C2';
  function clamp(v) { return Math.max(0, Math.min(1, v)); }
  function seg(t, a, d) { return clamp((t - a) / d); }
  function expoOut(p) { return p >= 1 ? 1 : 1 - Math.pow(2, -10 * p); }
  function p3out(p) { return 1 - Math.pow(1 - p, 3); }
  function lerp(a, b, k) { return a + (b - a) * k; }
  var Y = [
    'M296.4,364.4L113.1,181.3Q95.4,163.6 113.1,145.9L144.9,114.1Q162.6,96.4 180.3,114.1L363.6,297.4Z',
    'M262,513L262,330C262,310 276,295.8 290,281.8L457.7,114.1Q475.4,96.4 493.1,114.1L524.9,145.9Q542.6,163.6 524.9,181.3L376,330.2L376,513Q376,545 344,545L294,545Q262,545 262,513Z',
    'M262,455L376,341L376,513Q376,545 344,545L294,545Q262,545 262,513Z'
  ].map(function (d) { return new Path2D(d); });
  var T = 66, R = 16;
  var SLOTS = [[150, 312], [422, 312], [150, 394], [422, 394], [68, 312], [504, 394], [68, 394], [504, 312]];
  var MINT_SLOT = 7, DECK = [286, 446], MCX = 319, MCY = 320;

  function rr(x, px, py, w, h, r) {
    x.beginPath(); x.moveTo(px + r, py); x.arcTo(px + w, py, px + w, py + h, r); x.arcTo(px + w, py + h, px, py + h, r);
    x.arcTo(px, py + h, px, py, r); x.arcTo(px, py, px + w, py, r); x.closePath();
  }
  function card(x, ground, px, py, rot, sx, color, edge) {
    x.save(); x.translate(px + T / 2, py + T / 2); x.rotate(rot); x.scale(sx, 1);
    rr(x, -T / 2, -T / 2, T, T, R); x.fillStyle = color; x.fill();
    if (edge) { x.lineWidth = 3; x.strokeStyle = ground; x.stroke(); }
    x.restore();
  }

  /** Draws time t on ctx: the mark centred at (cx, cy), k = scale of the mark's units, ground = background. */
  function draw(x, t, o) {
    var ground = o.ground;
    x.save(); x.setTransform(1, 0, 0, 1, 0, 0);
    if (o.fill !== false) { x.fillStyle = ground; x.fillRect(0, 0, x.canvas.width, x.canvas.height); }
    else x.clearRect(0, 0, x.canvas.width, x.canvas.height);
    x.translate(o.cx, o.cy); x.scale(o.k, o.k); x.translate(-MCX, -MCY);

    var appear = p3out(seg(t, 0, 0.3)), riffle = Math.sin(Math.PI * seg(t, 0.3, 0.3)), order = [];
    SLOTS.forEach(function (s, i) {
      var k = seg(t, 0.62 + i * 0.075, 0.42), e = expoOut(k);
      var stackY = DECK[1] - i * 3 - riffle * i * 4.5, stackX = DECK[0] + riffle * (i % 2 ? 1 : -1) * i * 1.6;
      var px = lerp(stackX, s[0], e), py = lerp(stackY, s[1], e) - Math.sin(Math.PI * k) * 46;
      var side = s[0] < MCX ? -1 : 1, rot = (1 - e) * side * 0.32 * Math.sin(Math.PI * Math.min(1, k * 1.4));
      var sx = 1, color = WHITE;
      if (i === MINT_SLOT) {
        var fk = seg(t, 1.70, 0.26); sx = Math.abs(Math.cos(Math.PI * p3out(fk))); if (fk >= 0.5) color = MINT;
        sx *= 1 - 0.06 * Math.sin(Math.PI * seg(t, 1.96, 0.18));
      }
      order.push({ px: px, py: py, rot: rot, sx: sx, color: color, flying: k > 0 && k < 1, landed: k >= 1 });
    });
    x.globalAlpha = appear;
    var pre = Math.max(0, 1 - appear) * 30;
    order.filter(function (c) { return !c.flying && !c.landed; }).forEach(function (c) { card(x, ground, c.px, c.py + pre, c.rot, c.sx, c.color, true); });
    order.filter(function (c) { return c.landed; }).forEach(function (c) { card(x, ground, c.px, c.py, 0, c.sx, c.color, false); });
    order.filter(function (c) { return c.flying; }).forEach(function (c) { card(x, ground, c.px, c.py, c.rot, c.sx, c.color, true); });
    x.globalAlpha = 1;

    var stem = p3out(seg(t, 1.20, 0.30)), arms = expoOut(seg(t, 1.40, 0.55));
    if (stem > 0) {
      var regions = [], top = lerp(548, 300, stem), r0 = new Path2D();
      r0.rect(250, top, 140, 548 - top); regions.push(r0);
      var L = 300 * arms;
      if (arms > 0) [-1, 1].forEach(function (dir) {
        var ux = dir / Math.SQRT2, uy = -1 / Math.SQRT2, nx = -uy, ny = ux, w = 110;
        var a = [319 - ux * 40, 318 - uy * 40], b = [319 + ux * L, 318 + uy * L], r = new Path2D();
        r.moveTo(a[0] + nx * w, a[1] + ny * w); r.lineTo(b[0] + nx * w, b[1] + ny * w);
        r.lineTo(b[0] - nx * w, b[1] - ny * w); r.lineTo(a[0] - nx * w, a[1] - ny * w); r.closePath();
        regions.push(r);
      });
      regions.forEach(function (r) { x.save(); x.clip(r); x.fillStyle = WHITE; Y.forEach(function (p) { x.fill(p); }); x.restore(); });
    }
    x.restore();

    if (o.name) {
      var nk = p3out(seg(t, 1.95, 0.6));
      if (nk > 0) {
        var size = o.nameSize, base = o.nameY + size * 0.8;
        x.save();
        x.font = '600 ' + size + 'px "IBM Plex Sans Hebrew", system-ui, sans-serif';
        if ('letterSpacing' in x) x.letterSpacing = (-0.03 * size) + 'px';
        x.textAlign = 'center'; x.textBaseline = 'alphabetic';
        x.beginPath(); x.rect(0, base - size * 1.1, x.canvas.width, size * 1.35); x.clip();
        x.fillStyle = WHITE; x.fillText('YDeck', o.cx, base + (1 - nk) * size * 0.9);
        x.restore();
      }
    }
  }

  /** Plays on a canvas sized to its box (device pixels); layout(w, h) returns draw options. Resolves at `end`. */
  function play(canvas, layout, end, onFrame) {
    var dpr = Math.min(window.devicePixelRatio || 1, 2), x = canvas.getContext('2d');
    function size() { canvas.width = Math.round(canvas.clientWidth * dpr); canvas.height = Math.round(canvas.clientHeight * dpr); }
    size();
    return new Promise(function (done) {
      var t0 = null, stopped = false;
      function frame(now) {
        if (stopped) return;
        if (t0 === null) t0 = now;
        var t = (now - t0) / 1000;
        draw(x, end <= 0 ? 3 : Math.min(t, end), layout(canvas.width, canvas.height, dpr));
        if (onFrame) onFrame(t);
        if (t >= end) { stopped = true; return done(); }
        requestAnimationFrame(frame);
      }
      requestAnimationFrame(frame);
      canvas._stop = function () { if (stopped) return; stopped = true; draw(x, end, layout(canvas.width, canvas.height, dpr)); done(); };
    });
  }

  return { draw: draw, play: play };
})();
