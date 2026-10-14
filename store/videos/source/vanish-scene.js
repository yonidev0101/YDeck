
// ==== "Magic #2: the vanishing" (built on the trick's helpers by make-vanish.cjs) ====
// 0.2 title · 2.05 tabs rain into a heap · 4.65 the cloth comes down · 6.65 one, two, three · 8.0 the pull: one widget
// 10.75 a tap opens Firebase in the right account · 12.65 "magic? no. YDeck." · 13.0 the cloth returns and covers the screen
const TAP = 11.3;
const LINES2 = [
  [0.2, 1.9, 'קסם מספר 2.', 'ההיעלמות.'],
  [2.05, 4.5, 'אלה כל הטאבים', 'של פרויקט אחד.'],
  [4.65, 6.5, 'עכשיו שימו לב.', 'לא למצמץ.'],
  [8.3, 10.6, 'כל הטאבים.', 'בווידג׳ט אחד.'],
  [10.75, 12.5, 'וכל אחד נפתח', 'בחשבון הנכון.'],
  [12.65, 14.05, 'קסם?', 'לא. YDeck.'],
];
const COUNTS = [6.75, 7.25, 7.75];
function headline(t) {
  // the countdown builds one line word by word
  if (t >= 6.6 && t < 8.25) {
    const n = COUNTS.filter(c => t >= c).length, words = ['אחת.', 'שתיים.', 'שלוש.'];
    $('l1').textContent = words.slice(0, Math.max(1, n)).join(' ');
    $('l2').textContent = '';
    const out = -p3in(seg(t, 7.95, 0.25)) * 130;
    $('l1').style.transform = `translateY(${t < 7.95 ? (1 - p3(seg(t, 6.6, 0.35))) * 130 : out}px)`;
    $('l1').style.opacity = n === 0 ? seg(t, 6.6, 0.2) * 0 + (t >= 6.75 ? 1 : 0) : 1;
    return;
  }
  $('l1').style.opacity = 1;
  const cur = LINES2.find(([a, b]) => t >= a - 0.01 && t < b + 0.32);
  if (!cur) { $('l1').textContent = $('l2').textContent = ''; return; }
  const [a, b, one, two] = cur;
  $('l1').textContent = one; $('l2').textContent = two;
  const inn = at => (1 - p3(seg(t, at, 0.45))) * 130, out = -p3in(seg(t, b, 0.28)) * 130;
  $('l1').style.transform = `translateY(${t < b ? inn(a) : out}px)`;
  $('l2').style.transform = `translateY(${t < b ? inn(a + 0.14) : out}px)`;
}

// ---- the tabs: they rain into a heap (always grey: clutter is never coloured) ----
const TITLES = ['Firebase', 'Vercel', 'Play Console', 'Gmail', 'GitHub', 'Sentry', 'Analytics', 'Supabase', 'Firebase (2)', 'Docs',
  'Stripe', 'Cloudflare', 'Play Console', 'Firebase', 'Vercel · team', 'Search Console', 'GCP', 'Gmail (עבודה)', 'Figma', 'Notion', 'Firebase (3)', 'Drive', 'Mixpanel', 'Sentry'];
let rs = 42; const rnd = () => ((rs = (rs * 1664525 + 1013904223) >>> 0) / 4294967296);
const HEAP = TITLES.map((title, i) => {
  const a = rnd() * Math.PI * 2, r = Math.sqrt(rnd());
  return { title, at: 2.15 + i * 0.085, x: 540 + Math.cos(a) * r * 270, y: 1130 + Math.sin(a) * r * 210 - i * 3,
    rot: (rnd() - 0.5) * 0.7, from: (rnd() - 0.5) * 300, w: 230 + rnd() * 90 };
});
window.HEAP_TIMES = HEAP.map(h => h.at + 0.42);
function tab(h, px, py, rot, alpha) {
  x.save(); x.globalAlpha = alpha; x.translate(px, py); x.rotate(rot); x.scale(1.15, 1.15);
  rr(-h.w / 2, -34, h.w, 68, 20); x.fillStyle = '#1C1C1F'; x.fill(); x.lineWidth = 2; x.strokeStyle = '#3A3A3F'; x.stroke();
  x.beginPath(); x.arc(h.w / 2 - 34, 0, 11, 0, 7); x.fillStyle = '#5C5C61'; x.fill();
  text(h.title, h.w / 2 - 58, 9, '400 26px Plex', '#A1A1A8', 'right', 'rtl');
  x.beginPath(); x.moveTo(-h.w / 2 + 24, -8); x.lineTo(-h.w / 2 + 40, 8); x.moveTo(-h.w / 2 + 40, -8); x.lineTo(-h.w / 2 + 24, 8);
  x.lineWidth = 3; x.strokeStyle = '#5C5C61'; x.lineCap = 'round'; x.stroke();
  x.restore();
}
function heap(t) {
  if (t > 5.35) return;   // hidden under the cloth from here on (and gone when it lifts)
  HEAP.forEach(h => {
    const k = seg(t, h.at, 0.42);
    if (k <= 0) return;
    // falls in with gravity, lands with a little settle
    const fall = k < 1 ? p3in(k) : 1, settle = Math.sin(Math.PI * seg(t, h.at + 0.42, 0.18)) * 10;
    const px = lerp(h.x + h.from, h.x, fall), py = lerp(-120, h.y, fall) - settle;
    tab(h, px, py, lerp(h.rot * 3, h.rot, fall), 1);
  });
}

// ---- the cloth: an indigo silk with a domed top, soft folds and a wavy hem ----
function cloth(cx, cy, s, rot, squash, t) {
  x.save(); x.translate(cx, cy); x.rotate(rot); x.scale(s, s * squash);
  // draped over the pile: a low rounded crown, shoulders, then the fabric flares out to a wide wavy hem
  const top = -250, bottom = 360, HW = 500;
  const hem = u => bottom + Math.sin(u * 11 + t * 3.5) * 14 + Math.sin(u * 5.1 + 1) * 12;
  x.beginPath();
  x.moveTo(-HW, hem(0));
  x.bezierCurveTo(-HW + 70, 220, -400, 0, -360, -110);
  x.bezierCurveTo(-320, -215, -180, top, 0, top);
  x.bezierCurveTo(180, top, 320, -215, 360, -110);
  x.bezierCurveTo(400, 0, HW - 70, 220, HW, hem(1));
  for (let u = 1; u >= 0; u -= 0.02) x.lineTo(-HW + 2 * HW * u, hem(u));
  x.closePath();
  x.fillStyle = INDIGO; x.fill();
  // folds fan out from the crown to the hem
  x.save(); x.clip();
  [-0.78, -0.45, -0.12, 0.2, 0.52, 0.82].forEach((u, i) => {
    const hx = u * HW, cx0 = u * 150;
    x.beginPath(); x.moveTo(cx0, top + 70); x.bezierCurveTo(cx0 * 1.4, -40, hx * 0.85, 160, hx + (i % 2 ? 18 : -18), bottom + 40);
    x.lineWidth = 30; x.strokeStyle = 'rgba(20, 14, 120, .2)'; x.stroke();
    x.lineWidth = 5; x.strokeStyle = 'rgba(255, 255, 255, .07)'; x.stroke();
  });
  x.restore();
  // a small mark sewn on, like a label
  // the label only belongs to the cloth-sized cloth; it fades as the cloth grows to fill the screen
  const lab = 1 - clamp((s - 1.15) / 0.35);
  if (lab > 0) { x.globalAlpha = lab; markAt(0, -60, 0.24, 'rgba(255,255,255,.9)', MINT); x.globalAlpha = 1; }
  x.restore();
}
function clothState(t) {
  // down 4.7–5.35 · twitch on each count · pulled away up-right 8.0–8.45
  const down = expo(seg(t, 4.7, 0.65));
  const tw = COUNTS.reduce((acc, c) => acc + Math.sin(Math.PI * seg(t, c, 0.22)) * (t >= c ? 1 : 0), 0);
  const pull = io(seg(t, 8.0, 0.45));
  return { on: t >= 4.7 && t < 8.5, cx: 540 + pull * 1100, cy: lerp(-700, 1090, down) - pull * 1500, s: 1 + pull * 0.4,
    rot: pull * 0.9 + tw * 0.015, squash: 1 - tw * 0.035 };
}

// ---- sparkles when the cloth leaves ----
function sparkle(px, py, r, a) {
  x.save(); x.globalAlpha = a; x.translate(px, py); x.fillStyle = MINT;
  x.beginPath(); x.moveTo(0, -r); x.quadraticCurveTo(0, 0, r, 0); x.quadraticCurveTo(0, 0, 0, r); x.quadraticCurveTo(0, 0, -r, 0); x.quadraticCurveTo(0, 0, 0, -r); x.fill();
  x.restore();
}
const SPARKS = [[170, 760, 26, 0], [930, 820, 20, 0.06], [880, 1380, 30, 0.1], [200, 1330, 18, 0.14], [540, 690, 16, 0.18], [980, 1080, 14, 0.22]];

// ---- toast under the widget ----
function toast(t) {
  const k = p3(seg(t, TAP + 0.25, 0.4)) * (1 - seg(t, 12.45, 0.3));
  if (k <= 0) return;
  x.save(); x.globalAlpha = k;
  const y = 1440 + (1 - k) * 30, w = 760;
  rr(540 - w / 2, y, w, 96, 48); x.fillStyle = 'rgba(244,244,245,.07)'; x.fill(); x.lineWidth = 2; x.strokeStyle = '#26262A'; x.stroke();
  x.beginPath(); x.arc(540 + w / 2 - 50, y + 48, 9, 0, 7); x.fillStyle = MINT; x.fill();
  text('Firebase נפתח בחשבון', 540 + w / 2 - 78, y + 58, '500 30px Plex', ON, 'right', 'rtl');
  text('dana@nova.dev', 540 - w / 2 + 44, y + 58, '500 30px Mono', MINT, 'left', 'ltr');
  x.restore();
}

window.render = (t, f = Math.round(t * 30)) => {
  x.setTransform(1, 0, 0, 1, 0, 0);
  x.fillStyle = NIGHT; x.fillRect(0, 0, 1080, 1920);
  headline(t);
  heap(t);

  // the widget waits under the cloth; it shows only once the cloth is pulled
  if (t >= 8.05 && t < 13.6) {
    const a = p3(seg(t, 8.05, 0.35));
    x.save(); x.globalAlpha = a;
    x.translate(540, 1040); x.scale(0.94 + 0.06 * a, 0.94 + 0.06 * a); x.translate(-540, -1040);
    widgetCard(1, t);
    x.restore();
    SPARKS.forEach(([px, py, r, d]) => { const k = seg(t, 8.12 + d, 0.6); if (k > 0 && k < 1) sparkle(px, py, r * Math.sin(Math.PI * k), 1); });
    const [fx, fy] = slotPos(1);
    finger(fx, fy + 20, seg(t, TAP - 0.4, 0.25) * (1 - seg(t, TAP + 0.35, 0.25)), Math.sin(Math.PI * seg(t, TAP, 0.22)));
    toast(t);
  }

  const c = clothState(t);
  if (c.on) cloth(c.cx, c.cy, c.s, c.rot, c.squash, t);

  // the finale: the cloth sweeps back in from the top left and becomes the whole indigo screen
  if (t >= 12.95) {
    const k = io(seg(t, 12.95, 1.25));
    if (k < 1) cloth(lerp(-500, 540, k), lerp(-900, 1150, k), lerp(1, 4.2, k), lerp(-0.8, 0, k), 1, t);
    else { x.fillStyle = INDIGO; x.fillRect(0, 0, 1080, 1920); }
  }
  grain(f);
};
window.DURATION = 14.4;
