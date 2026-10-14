// Sound for vanish.html (14.4 s, same beats). No music on status films; the sting brings the signature.
// Usage: node sound-vanish.cjs out.wav
const fs = require('fs');
const SR = 48000, DUR = 14.4, N = Math.round(SR * DUR);
const L = new Float32Array(N), R = new Float32Array(N);
const TAU = Math.PI * 2;
const clamp = (v, a = 0, b = 1) => Math.max(a, Math.min(b, v));
const smooth = v => { v = clamp(v); return v * v * (3 - 2 * v); };
let seed = 13; const rnd = () => ((seed = (seed * 1664525 + 1013904223) >>> 0) / 4294967296) * 2 - 1;
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
// a hollow woodblock knock, for the countdown
function knock(at, f, amp) {
  for (let i = Math.floor(at * SR); i < (at + 0.18) * SR; i++) {
    const t = i / SR - at, env = clamp(t / 0.001) * Math.exp(-t * 38);
    add(i, amp * env * (Math.sin(TAU * f * t) + 0.5 * Math.sin(TAU * f * 2.7 * t) * Math.exp(-t * 30)));
  }
}

// 0 · the title: a soft shimmer, rising (D major, not the brand's C–E signature)
[1174.66, 1479.98, 1760, 2349.32].forEach((f, i) => bell(0.25 + i * 0.07, f, 0.05, 1.2, -0.3 + i * 0.2));

// 1 · the tabs rain into the heap: a small tick and a soft landing per tab (times from vanish.html)
const HEAP = 24;
for (let i = 0; i < HEAP; i++) {
  const land = 2.15 + i * 0.085 + 0.42, pan = ((i * 37) % 11) / 10 - 0.5;
  tick(land, 0.2, pan, 1500 + ((i * 53) % 9) * 90, 0.04);
  pop(land, 240, 130, 0.07, 0.12);
}

// 2 · the cloth comes down: a long, soft fabric swish; it settles
swish(4.65, 0.75, 0.2, 0, 0, 0.08);
pop(5.3, 200, 110, 0.18, 0.3);

// 3 · one, two, three: knocks rising in pitch, the cloth twitching
knock(6.75, 620, 0.5); knock(7.25, 700, 0.5); knock(7.75, 800, 0.55);

// 4 · the pull: a sharp whoosh up and to the right, then the sparkle and a bright "ta-da" (D6 + A6)
swish(7.98, 0.4, 0.34, -0.2, 0.9, 0.45);
[2349.32, 2959.96, 3520, 2637.02, 3135.96, 3951.07].forEach((f, i) => bell(8.12 + i * 0.06, f, 0.035, 0.6, i % 2 ? 0.6 : -0.6));
bell(8.12, 1174.66, 0.15, 1.8, -0.15);
bell(8.16, 1760, 0.13, 1.8, 0.15);

// 5 · the tap: a click, and the "right account" ding (G5 + D6, as in the trick)
tick(11.3, 0.4, 0.15, 3000, 0.035);
bell(11.58, 783.99, 0.14, 1.4, -0.2);
bell(11.66, 1174.66, 0.14, 1.4, 0.2);

// 6 · the cloth sweeps back in and becomes the screen: a long rising whoosh into the sting
{ let lp = 0; for (let i = Math.floor(12.9 * SR); i < 14.25 * SR; i++) {
  const k = (i / SR - 12.9) / 1.35; lp += (rnd() - lp) * (0.02 + 0.28 * k * k);
  add(i, 0.3 * smooth(k / 0.85) * (1 - smooth((k - 0.93) / 0.07)) * lp, -0.6 + 0.6 * k); } }

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
fs.writeFileSync(process.argv[2] || 'vanish.wav', buf);
console.log('wrote', process.argv[2] || 'vanish.wav');
