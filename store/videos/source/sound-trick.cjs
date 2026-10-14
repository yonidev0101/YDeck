// Sound for trick.html (14.4 s, same beats). No music on status films: card sounds only; the sting brings the signature.
// Usage: node sound-trick.cjs out.wav
const fs = require('fs');
const SR = 48000, DUR = 14.4, N = Math.round(SR * DUR);
const L = new Float32Array(N), R = new Float32Array(N);
const TAU = Math.PI * 2;
const clamp = (v, a = 0, b = 1) => Math.max(a, Math.min(b, v));
const smooth = v => { v = clamp(v); return v * v * (3 - 2 * v); };
let seed = 9; const rnd = () => ((seed = (seed * 1664525 + 1013904223) >>> 0) / 4294967296) * 2 - 1;
const add = (i, v, pan = 0) => { if (i >= 0 && i < N) { L[i] += v * (1 - pan); R[i] += v * (1 + pan); } };

function tick(at, amp, pan, tone = 2300, len = 0.045) {
  let hp = 0, prev = 0;
  for (let i = Math.floor(at * SR); i < (at + len) * SR; i++) {
    const t = i / SR - at, env = clamp(t / 0.0015) * Math.exp(-t * 95);
    const n = rnd(); hp = 0.85 * (hp + n - prev); prev = n;
    add(i, amp * env * (0.6 * hp + 0.4 * Math.sin(TAU * tone * t)), pan);
  }
}
function swish(at, len, amp, pan0 = 0, pan1 = pan0) {
  let lp = 0;
  for (let i = Math.floor(at * SR); i < (at + len) * SR; i++) {
    const k = (i / SR - at) / len, env = Math.sin(Math.PI * k) ** 2;
    lp += (rnd() - lp) * (0.06 + 0.22 * k);
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
    const t = i / SR - at, env = clamp(t / 0.006) * Math.exp(-t * 3.2);
    add(i, amp * env * (Math.sin(TAU * f * t) + 0.35 * Math.sin(TAU * f / 2 * t) + 0.12 * Math.sin(TAU * f * 3 * t) * Math.exp(-t * 9)), pan);
  }
}
function glide(at, len, f0, f1, amp, pan = 0) {
  let ph = 0;
  for (let i = Math.floor(at * SR); i < (at + len) * SR; i++) {
    const k = (i / SR - at) / len; ph += TAU * (f0 + (f1 - f0) * smooth(k)) / SR;
    add(i, amp * Math.sin(Math.PI * k) ** 1.5 * Math.sin(ph), pan);
  }
}

// 0 · the deck lands on the table
pop(0.08, 300, 140, 0.25, 0.25); tick(0.08, 0.2, 0, 1400, 0.06);

// 1 · riffle: the halves part (two swishes, left and right), then the cards fall in, one tick each, alternating sides
swish(0.55, 0.35, 0.09, -0.7); swish(0.57, 0.35, 0.09, 0.7);
for (let i = 0; i < 7; i++) tick(1.0 + i * 0.085 + 0.12, 0.34, i % 2 ? 0.6 : -0.6, 2000 + i * 60);
for (let k = 0; k < 22; k++) tick(1.02 + k * 0.03, 0.06, (k % 2 ? 0.3 : -0.3), 3200, 0.02);   // the burr of a real riffle
pop(1.85, 260, 150, 0.22, 0.2);   // squared up

// 2 · the fan sweeps left to right, a soft tick per card
swish(2.05, 0.65, 0.1, -0.8, 0.8);
for (let i = 0; i < 7; i++) tick(2.15 + i * 0.07, 0.14, -0.8 + i * 0.27, 1800 + i * 80, 0.035);

// 3 · the pick: a click, the card slides out, flies to the middle, flips; a bell when Nova shows
tick(3.8, 0.32, 0.25, 2600, 0.04);
swish(3.85, 0.4, 0.1, 0.25, 0.1);
swish(4.55, 0.55, 0.12, 0.1, 0);
swish(5.05, 0.18, 0.14, 0); tick(5.25, 0.22, 0, 2900, 0.03);
bell(5.3, 783.99, 0.14, 1.2);

// 4 · the card opens into the widget: a soft pop, the tiles click in; the tap
pop(6.32, 480, 240, 0.25, 0.3);
for (let i = 0; i < 8; i++) tick(6.55 + i * 0.05, 0.12, (i % 4) / 3 * 0.8 - 0.4, 2400 + i * 50, 0.03);
tick(7.6, 0.4, 0.15, 3000, 0.035);

// 5 · the reel: one tick per account passing the window (same curve as trick.html), slowing to a stop
{ const START = 8.75, D = 1.85, TOTAL = 18;
  const pos = t => TOTAL * (1 - Math.pow(1 - clamp((t - START) / D), 3.2));
  let last = 0;
  for (let t = START; t <= START + D + 0.001; t += 0.0005) {
    const p = Math.floor(pos(t) + 0.5);
    if (p !== last) { tick(t, 0.3, 0, 2200 + (p % 4) * 120, 0.04); last = p; }
  }
  swish(8.6, 0.3, 0.06);
  // it lands: a two-note "ding" (G5 + D6), the answer
  bell(START + D + 0.02, 783.99, 0.15, 1.6, -0.2);
  bell(START + D + 0.1, 1174.66, 0.15, 1.6, 0.2);
}

// 6 · the card closes, flips to its back, and grows until it is the screen
swish(12.35, 0.45, 0.09);
swish(12.8, 0.2, 0.14); tick(12.98, 0.2, 0, 2600, 0.03);
{ let lp = 0; for (let i = Math.floor(13.2 * SR); i < 14.25 * SR; i++) {
  const k = (i / SR - 13.2) / 1.05; lp += (rnd() - lp) * (0.02 + 0.3 * k * k);
  add(i, 0.28 * smooth(k / 0.9) * (1 - smooth((k - 0.92) / 0.08)) * lp); } }
glide(13.25, 0.95, 180, 720, 0.09);

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
fs.writeFileSync(process.argv[2] || 'trick.wav', buf);
console.log('wrote', process.argv[2] || 'trick.wav');
