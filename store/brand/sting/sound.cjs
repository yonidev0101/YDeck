// YDeck sting sound, "The deal": synced to sting.html (same times). Usage: node sound.cjs out.wav
// riffle → eight dealt cards (ticks panned to the side each lands on) → the stem rises and forks in stereo
// → the last card flips → the YDeck signature: C6 then E6 over a soft octave, with a low pop.
const fs = require('fs');
const SR = 48000, DUR = 3.6, N = Math.round(SR * DUR);
const L = new Float32Array(N), R = new Float32Array(N);
const TAU = Math.PI * 2;
const clamp = (v, a = 0, b = 1) => Math.max(a, Math.min(b, v));
const smooth = v => { v = clamp(v); return v * v * (3 - 2 * v); };
let seed = 11; const rnd = () => ((seed = (seed * 1664525 + 1013904223) >>> 0) / 4294967296) * 2 - 1;
const add = (i, v, pan = 0) => { if (i >= 0 && i < N) { L[i] += v * (1 - pan); R[i] += v * (1 + pan); } };

// a card tick: a very short band of noise (paper) plus a damped click tone
function tick(at, amp, pan, tone = 2300, len = 0.05) {
  let hp = 0, prev = 0;
  for (let i = Math.floor(at * SR); i < (at + len) * SR; i++) {
    const t = i / SR - at, env = clamp(t / 0.0015) * Math.exp(-t * 90);
    const n = rnd(); hp = 0.85 * (hp + n - prev); prev = n;      // high-passed noise
    add(i, amp * env * (0.55 * hp + 0.45 * Math.sin(TAU * tone * t)), pan);
  }
}
// a soft air movement for a card in flight
function swish(at, len, amp, pan) {
  let lp = 0;
  for (let i = Math.floor(at * SR); i < (at + len) * SR; i++) {
    const k = (i / SR - at) / len, env = Math.sin(Math.PI * k) ** 2;
    lp += (rnd() - lp) * (0.08 + 0.25 * k);
    add(i, amp * env * lp, pan * (k * 2 - 1) * 0.6 + pan * 0.4);
  }
}
function glide(at, len, f0, f1, amp, pan) {
  let ph = 0;
  for (let i = Math.floor(at * SR); i < (at + len) * SR; i++) {
    const k = (i / SR - at) / len, f = f0 + (f1 - f0) * smooth(k);
    ph += TAU * f / SR;
    add(i, amp * Math.sin(Math.PI * k) ** 1.5 * Math.sin(ph), pan);
  }
}
function pop(at, f0, f1, amp, len) {
  let ph = 0;
  for (let i = Math.floor(at * SR); i < (at + len) * SR; i++) {
    const k = (i / SR - at) / len, f = f1 + (f0 - f1) * Math.exp(-k * 6);
    ph += TAU * f / SR;
    add(i, amp * clamp(k * len / 0.005) * Math.exp(-k * 5) * Math.sin(ph));
  }
}
// a soft bell: the note, a quiet fifth partial and an octave below
function note(at, f, amp, len, pan) {
  for (let i = Math.floor(at * SR); i < (at + len) * SR; i++) {
    const t = i / SR - at, env = clamp(t / 0.008) * Math.exp(-t * 2.6);
    add(i, amp * env * (Math.sin(TAU * f * t) + 0.18 * Math.sin(TAU * f * 3 * t) * Math.exp(-t * 8) + 0.45 * Math.sin(TAU * f / 2 * t)), pan);
  }
}

// 0.00 the stack appears: one soft card on felt
tick(0.04, 0.35, 0, 1500, 0.06);

// 0.30–0.60 the riffle: quick ticks that speed up and fade, slightly alternating sides
for (let k = 0; k < 16; k++) {
  const at = 0.30 + 0.28 * Math.pow(k / 16, 0.75);
  tick(at, 0.16 * (1 - k / 22), (k % 2 ? 0.25 : -0.25), 2600 + 90 * k, 0.03);
}

// 0.62+ the deal: each card swishes out and ticks down on its side (same order and timing as sting.html)
const SIDES = [-1, 1, -1, 1, -1, 1, -1, 1];
SIDES.forEach((side, i) => {
  const start = 0.62 + i * 0.075;
  swish(start, 0.2, 0.07, side * 0.7);
  tick(start + 0.16, 0.42, side * 0.7, 1900 + (i % 4) * 140);
});

// 1.20 the stem rises; 1.40 it forks: one rising tone that splits into two, out to left and right
glide(1.20, 0.30, 330, 520, 0.11, 0);
glide(1.40, 0.42, 520, 700, 0.08, -0.85);
glide(1.40, 0.42, 520, 780, 0.08, 0.85);
{ let lp = 0; for (let i = Math.floor(1.2 * SR); i < 1.85 * SR; i++) {
  const k = (i / SR - 1.2) / 0.65; lp += (rnd() - lp) * 0.05;
  add(i, 0.10 * Math.sin(Math.PI * k) ** 2 * lp, 0); } }

// 1.70 the flip: a quick paper "fwip"; 1.83 face up: C6, 1.96 (the press): E6, with a soft low pop
swish(1.70, 0.13, 0.12, 0.6);
tick(1.83, 0.3, 0.6, 2800, 0.04);
note(1.83, 1046.5, 0.16, 1.5, 0.2);
note(1.98, 1318.5, 0.17, 1.6, 0.35);
pop(1.96, 300, 120, 0.4, 0.35);

// a little room
function room(xs, d1, d2) {
  const out = new Float32Array(N), a = Math.floor(d1 * SR), b = Math.floor(d2 * SR);
  let la = 0, lb = 0;
  for (let i = 0; i < N; i++) {
    la += ((i >= a ? out[i - a] : 0) - la) * 0.35; lb += ((i >= b ? out[i - b] : 0) - lb) * 0.35;
    out[i] = xs[i] + 0.25 * la + 0.16 * lb;
  }
  return out;
}
const WL = room(L, 0.041, 0.077), WR = room(R, 0.049, 0.087);
let peak = 0;
for (let i = 0; i < N; i++) {
  const g = 1 - smooth((i / SR - 3.3) / 0.3);
  WL[i] *= g; WR[i] *= g; peak = Math.max(peak, Math.abs(WL[i]), Math.abs(WR[i]));
}
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
const out = process.argv[2] || 'ydeck-sting.wav';
fs.writeFileSync(out, buf);
console.log('wrote', out);
