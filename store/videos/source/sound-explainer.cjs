// Sound for explainer.html (28 s, same beats). No music on status films: UI sounds only; the sting brings the signature.
// Usage: node sound-explainer.cjs out.wav
const fs = require('fs');
const SR = 48000, DUR = 28.0, N = Math.round(SR * DUR);
const L = new Float32Array(N), R = new Float32Array(N);
const TAU = Math.PI * 2;
const clamp = (v, a = 0, b = 1) => Math.max(a, Math.min(b, v));
const smooth = v => { v = clamp(v); return v * v * (3 - 2 * v); };
let seed = 33; const rnd = () => ((seed = (seed * 1664525 + 1013904223) >>> 0) / 4294967296) * 2 - 1;
// after the developer story every event is written on the film's earlier clock and lands OFF later
const OFF = 4.0; let SHIFT = 0;
const add = (i, v, pan = 0) => { i += SHIFT; if (i >= 0 && i < N) { L[i] += v * (1 - pan); R[i] += v * (1 + pan); } };

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
function pop(at, f0, f1, amp, len, pan = 0) {
  let ph = 0;
  for (let i = Math.floor(at * SR); i < (at + len) * SR; i++) {
    const k = (i / SR - at) / len; ph += TAU * (f1 + (f0 - f1) * Math.exp(-k * 6)) / SR;
    add(i, amp * clamp(k * len / 0.005) * Math.exp(-k * 5) * Math.sin(ph), pan);
  }
}
function bell(at, f, amp, len, pan = 0) {
  for (let i = Math.floor(at * SR); i < (at + len) * SR; i++) {
    const t = i / SR - at, env = clamp(t / 0.005) * Math.exp(-t * 3.4);
    add(i, amp * env * (Math.sin(TAU * f * t) + 0.3 * Math.sin(TAU * f / 2 * t) + 0.1 * Math.sin(TAU * f * 3 * t) * Math.exp(-t * 9)), pan);
  }
}

// 0 · the developer story (explainer.html STORY): a card lands per project, its links tick in, its accounts pop
const COLPAN = [0.55, 0, -0.55];
COLPAN.forEach((pan, c) => {
  pop(0.45 + c * 0.18 + 0.12, 380, 200, 0.2, 0.22, pan); tick(0.45 + c * 0.18 + 0.12, 0.14, pan, 1400, 0.04);
  for (let i = 0; i < 5; i++) tick(2.3 + c * 0.12 + i * 0.13 + 0.05, 0.11, pan, 2100 + i * 90, 0.03);
  for (let i = 0; i < 2; i++) { const at = 4.45 + c * 0.15 + i * 0.22 + 0.1; pop(at, 640, 330, 0.12, 0.12, pan); }
});
SHIFT = Math.round(OFF * SR);

// 1 · they are pulled in; the phone rises; the widget lands with a soft shimmer (A major, not the brand's C–E)
swish(2.6, 0.6, 0.16, 0, 0, 0.35);
swish(2.95, 0.8, 0.12, 0, 0, 0.1);
pop(3.6, 520, 260, 0.22, 0.3);
[880, 1108.73, 1318.51, 1760].forEach((f, i) => bell(3.65 + i * 0.07, f, 0.045, 1.2, -0.3 + i * 0.2));

// 2 · the marks light up in turn, rising
for (let i = 0; i < 8; i++) tick(5.95 + i * 0.2, 0.16, (i % 4) / 3 * 0.8 - 0.4, 2000 + i * 120, 0.035);

// 3 · the taps: Firebase, then Vercel; each answer gets the "right account" ding (G5 + D6, as in the other films)
tick(10.4, 0.4, 0.1, 3000, 0.035); bell(10.62, 783.99, 0.13, 1.3, -0.2); bell(10.7, 1174.66, 0.13, 1.3, 0.2);
tick(11.95, 0.4, -0.2, 3000, 0.035); bell(12.17, 783.99, 0.1, 1.1, -0.2); bell(12.25, 1174.66, 0.1, 1.1, 0.2);

// 4 · the page dots: a click and a soft slide into the next project's colour
for (const at of [14.3, 15.4]) { tick(at, 0.35, -0.5, 2700, 0.035); swish(at + 0.03, 0.42, 0.07, -0.5, 0.5); pop(at + 0.4, 420, 300, 0.1, 0.2); }

// 5 · the widget folds to one row
swish(17.8, 0.7, 0.1, 0, 0, 0.15); pop(18.55, 300, 170, 0.2, 0.25); tick(18.55, 0.15, 0, 1500, 0.04);

// 6 · the phone leaves; the mark; the mint tile opens into indigo and the sting
swish(20.35, 0.65, 0.1, 0, 0, 0.1);
pop(20.95, 460, 230, 0.2, 0.3);
[1174.66, 1479.98, 1760].forEach((f, i) => bell(21.0 + i * 0.08, f, 0.04, 1.4, -0.2 + i * 0.2));
{ let lp = 0; for (let i = Math.floor(22.85 * SR); i < 23.9 * SR; i++) {
  const k = (i / SR - 22.85) / 1.05; lp += (rnd() - lp) * (0.02 + 0.28 * k * k);
  add(i, 0.28 * smooth(k / 0.85) * (1 - smooth((k - 0.93) / 0.07)) * lp); } }

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
fs.writeFileSync(process.argv[2] || 'explainer.wav', buf);
console.log('wrote', process.argv[2] || 'explainer.wav');
