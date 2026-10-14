// Sound for keys.html (14.4 s, same beats). No music on status films: key and lock sounds only; the sting brings the signature.
// Usage: node sound-keys.cjs out.wav
const fs = require('fs');
const SR = 48000, DUR = 14.4, N = Math.round(SR * DUR);
const L = new Float32Array(N), R = new Float32Array(N);
const TAU = Math.PI * 2;
const clamp = (v, a = 0, b = 1) => Math.max(a, Math.min(b, v));
const smooth = v => { v = clamp(v); return v * v * (3 - 2 * v); };
let seed = 21; const rnd = () => ((seed = (seed * 1664525 + 1013904223) >>> 0) / 4294967296) * 2 - 1;
const add = (i, v, pan = 0) => { if (i >= 0 && i < N) { L[i] += v * (1 - pan); R[i] += v * (1 + pan); } };

function tick(at, amp, pan, tone = 2300, len = 0.045) {
  let hp = 0, prev = 0;
  for (let i = Math.floor(at * SR); i < (at + len) * SR; i++) {
    const t = i / SR - at, env = clamp(t / 0.0015) * Math.exp(-t * 95);
    const n = rnd(); hp = 0.85 * (hp + n - prev); prev = n;
    add(i, amp * env * (0.6 * hp + 0.4 * Math.sin(TAU * tone * t)), pan);
  }
}
function swish(at, len, amp, pan0 = 0, pan1 = pan0, bright = 0.22) {
  let lp = 0;
  for (let i = Math.floor(at * SR); i < (at + len) * SR; i++) {
    const k = (i / SR - at) / len, env = Math.sin(Math.PI * k) ** 2;
    lp += (rnd() - lp) * (0.04 + bright * k);
    add(i, amp * env * lp, pan0 + (pan1 - pan0) * k);
  }
}
function pop(at, f0, f1, amp, len) {
  let ph = 0;
  for (let i = Math.floor(at * SR); i < (at + len) * SR; i++) {
    const k = (i / SR - at) / len; ph += TAU * (f1 + (f0 - f1) * Math.exp(-k * 6)) / SR;
    add(i, amp * clamp(k * len / 0.005) * Math.exp(-k * 5) * Math.sin(ph));
  }
}
function bell(at, f, amp, len, pan = 0) {
  for (let i = Math.floor(at * SR); i < (at + len) * SR; i++) {
    const t = i / SR - at, env = clamp(t / 0.005) * Math.exp(-t * 3.4);
    add(i, amp * env * (Math.sin(TAU * f * t) + 0.3 * Math.sin(TAU * f / 2 * t) + 0.1 * Math.sin(TAU * f * 3 * t) * Math.exp(-t * 9)), pan);
  }
}
// one small metal strike: inharmonic partials, short ring
function ping(at, f, amp, pan, decay = 28) {
  const parts = [[1, 1], [2.76, 0.5], [5.4, 0.25], [8.93, 0.12]];
  for (let i = Math.floor(at * SR); i < (at + 0.35) * SR; i++) {
    const t = i / SR - at, env = clamp(t / 0.0008) * Math.exp(-t * decay);
    let v = 0; for (const [m, a] of parts) v += a * Math.sin(TAU * f * m * t) * Math.exp(-t * m * 4);
    add(i, amp * env * v, pan);
  }
}
// a bunch of keys jingling: many small strikes over a short spread
function jingle(at, spread, count, amp, pan = 0) {
  for (let k = 0; k < count; k++) {
    const dt = Math.abs(rnd()) * spread, f = 2100 + Math.abs(rnd()) * 2600;
    ping(at + dt, f, amp * (1 - 0.5 * dt / spread), clamp(pan + rnd() * 0.5, -0.9, 0.9));
  }
}
// metal sliding in metal: a narrow, bright scrape
function scrape(at, len, amp, up = true) {
  let bp = 0, lp = 0;
  for (let i = Math.floor(at * SR); i < (at + len) * SR; i++) {
    const k = (i / SR - at) / len, env = Math.sin(Math.PI * k) ** 0.7;
    const n = rnd(); lp += (n - lp) * 0.5; bp = lp - bp * 0.2;
    const ridge = 0.6 + 0.4 * Math.sin(TAU * (up ? 38 + 30 * k : 68 - 30 * k) * (i / SR));
    add(i, amp * env * bp * ridge, 0.1);
  }
}
// the jam: the key hits the pins, the lock rattles
function jam(at) {
  pop(at, 260, 120, 0.3, 0.18);
  ping(at, 1450, 0.16, 0.1, 40); tick(at, 0.3, 0.1, 1700, 0.05);
  [0.09, 0.17, 0.24].forEach((d, j) => { tick(at + d, 0.2 - j * 0.04, j % 2 ? 0.3 : -0.3, 1300 + j * 120, 0.04); ping(at + d, 1900 + j * 140, 0.06, 0, 45); });
}

// 0 · the bunch drops in, lands with a jingle and swings
swish(0.25, 0.45, 0.12, 0, 0, 0.12);
jingle(0.62, 0.22, 16, 0.07);
jingle(1.25, 0.18, 7, 0.035, -0.3); jingle(1.65, 0.18, 6, 0.028, 0.3);

// 1 · "every service, its own key": a soft shake of the bunch
jingle(2.45, 0.3, 9, 0.03);

// 2 · the lock rises and sets down
swish(4.3, 0.45, 0.08, 0, 0, 0.1);
pop(4.78, 150, 80, 0.28, 0.35); tick(4.78, 0.15, 0, 900, 0.06);

// 3 · three tries: a key comes off, flies, slides in, jams, slides out, goes home
for (const T of [5.1, 6.2, 7.3]) {
  jingle(T, 0.12, 5, 0.035, 0.2);
  swish(T + 0.04, 0.32, 0.09, 0.4, 0);
  scrape(T + 0.36, 0.2, 0.09, true);
  jam(T + 0.6);
  scrape(T + 1.05, 0.15, 0.07, false);
  jingle(T + 1.45, 0.1, 4, 0.03, 0.3);
}

// 4 · the keys fly in and become the widget: a rising whoosh, a click per tile, a soft pop when the card is whole
swish(8.6, 0.85, 0.14, -0.4, 0.4, 0.3);
jingle(8.62, 0.25, 8, 0.03);
for (let i = 0; i < 8; i++) tick(8.65 + i * 0.05 + 0.82, 0.16, (i % 4) / 3 * 0.8 - 0.4, 2400 + i * 50, 0.03);
pop(9.3, 480, 240, 0.2, 0.3);

// 5 · the tap; the lock springs open; the "right account" ding (G5 + D6, as in the trick)
tick(10.9, 0.4, 0.15, 3000, 0.035);
pop(11.15, 300, 160, 0.25, 0.16); ping(11.15, 1700, 0.14, 0, 30); tick(11.2, 0.25, 0, 2600, 0.03);
bell(11.3, 783.99, 0.14, 1.4, -0.2);
bell(11.38, 1174.66, 0.14, 1.4, 0.2);

// 6 · light through the keyhole: a long rising whoosh into the sting
{ let lp = 0; for (let i = Math.floor(13.05 * SR); i < 14.25 * SR; i++) {
  const k = (i / SR - 13.05) / 1.2; lp += (rnd() - lp) * (0.02 + 0.28 * k * k);
  add(i, 0.3 * smooth(k / 0.85) * (1 - smooth((k - 0.93) / 0.07)) * lp); } }

function room(xs, d1, d2) {
  const out = new Float32Array(N), a = Math.floor(d1 * SR), b = Math.floor(d2 * SR);
  let la = 0, lb = 0;
  for (let i = 0; i < N; i++) { la += ((i >= a ? out[i - a] : 0) - la) * 0.35; lb += ((i >= b ? out[i - b] : 0) - lb) * 0.35; out[i] = xs[i] + 0.22 * la + 0.14 * lb; }
  return out;
}
const WL = room(L, 0.041, 0.077), WR = room(R, 0.049, 0.087);
let peak = 0; for (let i = 0; i < N; i++) peak = Math.max(peak, Math.abs(WL[i]), Math.abs(WR[i]));
const norm = 0.707 / peak;
const buf = Buffer.alloc(44 + N * 4);
buf.write('RIFF', 0); buf.writeUInt32LE(36 + N * 4, 4); buf.write('WAVE', 8);
buf.write('fmt ', 12); buf.writeUInt32LE(16, 16); buf.writeUInt16LE(1, 20); buf.writeUInt16LE(2, 22);
buf.writeUInt32LE(SR, 24); buf.writeUInt32LE(SR * 4, 28); buf.writeUInt16LE(4, 32); buf.writeUInt16LE(16, 34);
buf.write('data', 36); buf.writeUInt32LE(N * 4, 40);
for (let i = 0; i < N; i++) {
  buf.writeInt16LE(Math.round(clamp(WL[i] * norm, -1, 1) * 32767), 44 + i * 4);
  buf.writeInt16LE(Math.round(clamp(WR[i] * norm, -1, 1) * 32767), 46 + i * 4);
}
fs.writeFileSync(process.argv[2] || 'keys.wav', buf);
console.log('wrote', process.argv[2] || 'keys.wav');
