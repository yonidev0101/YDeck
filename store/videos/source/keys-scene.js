
// ==== "The keys" (built on the trick's helpers by make-keys.cjs) ====
// A project is a bunch of keys: one per service, each for a different account. You try them on the Firebase lock, one after
// another, and none opens it. Then the keys fold into the widget (each key's head becomes its tile) and one tap opens it.
// 0.2 the bunch drops in · 4.3 the lock · 5.1 three tries, three jams · 8.6 the keys become the widget
// 10.9 the tap: the lock springs open in the right account · 12.85 the keyhole grows into the indigo screen
const TAP = 10.9;
const LINES3 = [
  [0.2, 2.15, 'פרויקט אחד.', 'שמונה מפתחות.'],
  [2.35, 4.15, 'כל שירות.', 'מפתח משלו.'],
  [4.3, 5.35, 'עכשיו תפתח', 'את Firebase.'],
  [8.6, 9.95, 'יש דרך', 'אחרת.'],
  [10.1, 11.3, 'מפתח אחד.', 'לכל הפרויקט.'],
  [11.45, 12.7, 'נפתח.', 'בחשבון הנכון.'],
  [12.85, 14.05, 'בלי צרור.', 'זה YDeck.'],
];
const TRIES = [[5.1, 0], [6.2, 2], [7.3, 1]];          // [start, key]: Play, GitHub, and even the Firebase key
const JAMS = TRIES.map(([s]) => s + 0.6);
window.JAMS = JAMS;
function headline(t) {
  // the tries build one line, a word per jam; the third adds the reason
  if (t >= JAMS[0] - 0.05 && t < 8.5) {
    const n = JAMS.filter(c => t >= c).length, words = ['לא.', 'לא.', 'גם לא.'];
    $('l1').textContent = words.slice(0, Math.max(1, n)).join(' ');
    $('l2').textContent = n >= 3 ? 'חשבון לא נכון.' : '';
    const out = -p3in(seg(t, 8.2, 0.25)) * 130;
    $('l1').style.opacity = t >= JAMS[0] ? 1 : 0;
    $('l1').style.transform = `translateY(${t < 8.2 ? (1 - p3(seg(t, JAMS[0], 0.35))) * 130 : out}px)`;
    $('l2').style.transform = `translateY(${t < 8.2 ? (1 - p3(seg(t, JAMS[2] + 0.1, 0.4))) * 130 : out}px)`;
    return;
  }
  $('l1').style.opacity = 1;
  const cur = LINES3.find(([a, b]) => t >= a - 0.01 && t < b + 0.32);
  if (!cur) { $('l1').textContent = $('l2').textContent = ''; return; }
  const [a, b, one, two] = cur;
  $('l1').textContent = one; $('l2').textContent = two;
  const inn = at => (1 - p3(seg(t, at, 0.45))) * 130, out = -p3in(seg(t, b, 0.28)) * 130;
  $('l1').style.transform = `translateY(${t < b ? inn(a) : out}px)`;
  $('l2').style.transform = `translateY(${t < b ? inn(a + 0.14) : out}px)`;
}

// ---- a key: a round head with the service's icon (grey: clutter is never coloured), a shaft and teeth ----
// local coords: head centre at 0,0, the shaft points down (+y). m = morph into a widget tile (0 key → 1 tile).
const KLEN = 240, HEAD = 48;
const mix = (a, b, k) => { const p = s => [1, 3, 5].map(i => parseInt(s.slice(i, i + 2), 16)); const A = p(a), B = p(b);
  return `rgb(${A.map((v, i) => Math.round(lerp(v, B[i], k))).join(',')})`; };
function keyAt(i, px, py, rot, m = 0, alpha = 1) {
  const [n, l, c, primary] = SLOTS[i];
  x.save(); x.globalAlpha = alpha; x.translate(px, py); x.rotate(rot);
  // shaft + teeth retract as it becomes a tile
  const len = KLEN * (1 - p3(clamp(m * 1.6)));
  if (len > 4) {
    x.fillStyle = '#3A3A3F';
    rr(-10, HEAD - 6, 20, len - HEAD + 6, 6); x.fill();
    const s = len / KLEN;
    x.beginPath(); x.moveTo(10, len - 74 * s); x.lineTo(30, len - 74 * s); x.lineTo(30, len - 52 * s); x.lineTo(20, len - 52 * s);
    x.lineTo(20, len - 36 * s); x.lineTo(32, len - 36 * s); x.lineTo(32, len - 14 * s); x.lineTo(10, len); x.closePath(); x.fill();
    rr(-17, HEAD - 2, 34, 12, 4); x.fillStyle = '#4A4A50'; x.fill();
  }
  // head: circle → rounded tile
  const size = lerp(HEAD * 2, 116, m), rad = lerp(HEAD, 34, m);
  rr(-size / 2, -size / 2, size, size, rad);
  x.fillStyle = mix('#2A2A2F', primary ? '#0B0B0C' : '#F2F2F7', m); x.fill();
  if (m < 1) { x.lineWidth = 4 * (1 - m); x.strokeStyle = '#4A4A50'; x.stroke(); }
  // the hole the ring passes through
  if (m < 0.5) { x.beginPath(); x.arc(0, -HEAD + 15, 7, 0, 7); x.fillStyle = `rgba(10,10,11,${1 - m * 2})`; x.fill(); }
  const is = lerp(42, 56, m), iy = lerp(5, 0, m);
  if (m < 1) { x.globalAlpha = alpha * (1 - m); icon(n, '#8A8A90', is, 0, iy); }
  if (m > 0) { x.globalAlpha = alpha * m; icon(n, c, is, 0, iy); }
  x.restore();
}

// ---- the bunch: a big split ring, the keys hanging from its lower half ----
const RING = 185;
function ringY(t) { return lerp(-620, 690, back(seg(t, 0.3, 0.75))); }
function swing(t) {
  const d = Math.max(0, t - 1.0);
  return (t > 1.0 ? 0.22 * Math.exp(-d * 1.6) * Math.sin(d * 7.5) : 0) + 0.035 * Math.sin(t * 1.9) * seg(t, 1.5, 1);
}
function homePose(i, t) {
  const a = lerp(1.35, -1.35, i / 7) + swing(t) * (1 + 0.15 * Math.sin(i * 1.7));
  const ry = ringY(t), d = RING + HEAD - 15;
  return [540 + d * Math.sin(a), ry + d * Math.cos(a), -a];
}

// ---- the lock (Firebase · Nova) ----
function lockPose(t) {
  const up = p3(seg(t, 4.3, 0.55)), shrink = io(seg(t, 8.7, 0.9));
  const shake = JAMS.reduce((s, j) => s + Math.sin(seg(t, j, 0.45) * Math.PI * 6) * (1 - seg(t, j, 0.45)) * (t >= j ? 12 : 0), 0);
  return { x: 540 + shake, y: lerp(2200, 1470, up) + shrink * 95, s: 1 - shrink * 0.38, label: 1 - seg(t, 8.6, 0.3) };
}
const keyhole = L => [L.x, L.y - 15 * L.s];
function lock(L, open, t) {
  x.save(); x.translate(L.x, L.y); x.scale(L.s, L.s);
  const sh = mix('#5C5C61', MINT, open);
  // shackle: lifts and swings open on its left leg
  x.save(); x.translate(-70, -95 - 46 * back(open)); x.rotate(-0.22 * open); x.translate(70, 95);
  x.beginPath(); x.moveTo(-70, -80); x.lineTo(-70, -165); x.arc(0, -165, 70, Math.PI, 0); x.lineTo(70, -80);
  x.lineWidth = 28; x.lineCap = 'round'; x.strokeStyle = sh; x.stroke();
  x.restore();
  rr(-115, -95, 230, 190, 40); x.fillStyle = '#1C1C1F'; x.fill();
  x.lineWidth = 4; x.strokeStyle = mix('#3A3A3F', MINT, open); x.stroke();
  // keyhole
  x.beginPath(); x.arc(0, -15, 20, 0, 7); x.moveTo(-8, -6); x.lineTo(-14, 42); x.lineTo(14, 42); x.lineTo(8, -6); x.closePath();
  x.fillStyle = open > 0 ? mix('#0A0A0B', MINT, open) : NIGHT; x.fill();
  x.restore();
  if (L.label > 0) {
    x.save(); x.globalAlpha = L.label;
    icon('si_firebase', '#8A8A90', 34, L.x - 92, L.y + 151);
    text('Firebase · Nova', L.x - 66, L.y + 162, '500 32px Plex', '#8A8A90', 'left', 'ltr');
    x.restore();
  }
}
let NO_TILES = false;

// ---- a try: the key flies to the lock, slides in, jams, comes out and flies home ----
function tryPose(i, start, t) {
  const u = t - start, L = lockPose(t), [kx, ky] = keyhole(L);
  const fly = io(seg(u, 0, 0.35)), back2 = io(seg(u, 1.2, 0.3));
  const depth = 110 * (p3(seg(u, 0.35, 0.2)) - p3(seg(u, 1.05, 0.15)));
  const j = seg(u, 0.6, 0.45), wig = Math.sin(j * Math.PI * 3) * (1 - j) * 0.16 * (u >= 0.6 ? 1 : 0);
  const reach = KLEN - depth;                       // head centre's distance from the keyhole
  const lx = kx + reach * Math.sin(wig), ly = ky - reach * Math.cos(wig);
  const [hx, hy, hr] = homePose(i, t);
  const k = fly * (1 - back2);
  return [lerp(hx, lx, k), lerp(hy, ly, k), lerp(hr, wig, k), u > 0.3 && u < 1.25];
}

// ---- the finger, the toast-like address under the lock, and the keyhole finale ----
function keyholeShape(cx, cy, r) {
  // one winding for both parts, or the overlap cancels out
  x.beginPath(); x.arc(cx, cy, r, 0, 7); x.moveTo(cx - r * 0.4, cy + r * 0.45); x.lineTo(cx + r * 0.4, cy + r * 0.45);
  x.lineTo(cx + r * 0.7, cy + r * 2.9); x.lineTo(cx - r * 0.7, cy + r * 2.9); x.closePath();
}

window.render = (t, f = Math.round(t * 30)) => {
  x.setTransform(1, 0, 0, 1, 0, 0);
  x.fillStyle = NIGHT; x.fillRect(0, 0, 1080, 1920);
  headline(t);
  const L = lockPose(t), open = clamp(p3(seg(t, TAP + 0.25, 0.35)));
  if (t >= 4.3) lock(L, open, t);

  const morphing = t >= 8.6, built = t >= 9.95;
  // the widget's shell fades in behind the arriving tiles
  if (t >= 9.0) {
    const a = p3(seg(t, 9.0, 0.6));
    NO_TILES = !built;
    x.save(); x.globalAlpha = a; x.translate(540, 1040); x.scale(0.94 + 0.06 * a, 0.94 + 0.06 * a); x.translate(-540, -1040);
    widgetCard(1, t);
    x.restore();
    NO_TILES = false;
  }
  if (!built) {
    const active = TRIES.find(([s]) => t >= s && t < s + 1.5);
    // the ring (behind the heads it passes through: drawn first, the heads cover it, the holes show it)
    const ry = ringY(t), ra = 1 - seg(t, 8.6, 0.35);
    if (ra > 0) {
      x.save(); x.globalAlpha = ra; x.beginPath(); x.arc(540, ry, RING * (1 - 0.3 * seg(t, 8.6, 0.35)), 0, 7);
      x.lineWidth = 9; x.strokeStyle = '#5C5C61'; x.stroke();
      x.beginPath(); x.arc(540, ry, RING - 9, -2.2, -1.6); x.lineWidth = 3; x.strokeStyle = '#8A8A90'; x.stroke(); x.restore();
    }
    SLOTS.forEach((_, i) => {
      if (active && active[1] === i) return;
      let [hx, hy, hr] = homePose(i, t), m = 0;
      if (morphing) {
        m = io(seg(t, 8.65 + i * 0.05, 0.85));
        const [sx, sy] = slotPos(i);
        hx = lerp(hx, sx, m); hy = lerp(hy, sy, m); hr = lerp(hr, 0, m);
      }
      keyAt(i, hx, hy, hr, m);
      // labels arrive with the tile
      const la = seg(m, 0.75, 0.25);
      if (la > 0) { const [sx, sy] = slotPos(i); x.globalAlpha = la; text(SLOTS[i][1], sx, sy + 92, '500 24px Plex', '#3C3C43', 'center', /[א-ת]/.test(SLOTS[i][1]) ? 'rtl' : 'ltr'); x.globalAlpha = 1; }
    });
    if (active) {
      const [s, i] = active, [px, py, pr, inLock] = tryPose(i, s, t);
      x.save();
      if (inLock) { const [, ky] = keyhole(L); x.beginPath(); x.rect(0, 0, 1080, ky); x.clip(); }
      keyAt(i, px, py, pr);
      x.restore();
    }
  }

  // the tap and the open lock
  if (built && t < 13.6) {
    const [fx, fy] = slotPos(1);
    finger(fx, fy + 20, seg(t, TAP - 0.4, 0.25) * (1 - seg(t, TAP + 0.35, 0.25)), Math.sin(Math.PI * seg(t, TAP, 0.22)));
    const ring = seg(t, TAP + 0.25, 0.8);
    if (ring > 0 && ring < 1) {
      x.save(); x.globalAlpha = 1 - ring; x.translate(L.x, L.y); x.scale(L.s, L.s);
      rr(-115 - 40 * p3(ring), -95 - 40 * p3(ring), 230 + 80 * p3(ring), 190 + 80 * p3(ring), 40 + 20 * p3(ring));
      x.lineWidth = 6; x.strokeStyle = MINT; x.stroke(); x.restore();
    }
    const ea = p3(seg(t, TAP + 0.45, 0.4));
    if (ea > 0) { x.save(); x.globalAlpha = ea; text('dana@nova.dev', 540, L.y + 118 + (1 - ea) * 20, '500 34px Mono', MINT, 'center', 'ltr'); x.restore(); }
  }

  // the finale: light through the keyhole grows into the whole indigo screen
  if (t >= 13.1) {
    const k = seg(t, 13.1, 1.1), [kx, ky] = keyhole(L);
    if (k < 1) { keyholeShape(lerp(kx, 540, io(k)), lerp(ky, 900, io(k)), lerp(20 * L.s, 1500, p3in(k))); x.fillStyle = INDIGO; x.fill(); }
    else { x.fillStyle = INDIGO; x.fillRect(0, 0, 1080, 1920); }
  }
  grain(f);
};
window.DURATION = 14.4;
