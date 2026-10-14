// Sound for film.html (14 s, same beats). No music on status films (brand book): soft effects only; the sting brings the signature.
// Usage: node sound.cjs out.wav
const fs = require('fs');
const SR = 48000, DUR = 14.0, N = Math.round(SR * DUR);
const L = new Float32Array(N), R = new Float32Array(N);
const TAU = Math.PI * 2;
const clamp = (v, a = 0, b = 1) => Math.max(a, Math.min(b, v));
const smooth = v => { v = clamp(v); return v * v * (3 - 2 * v); };
let seed = 5; const rnd = () => ((seed = (seed * 1664525 + 1013904223) >>> 0) / 4294967296) * 2 - 1;
const add = (i, v, pan = 0) => { if (i >= 0 && i < N) { L[i] += v * (1 - pan); R[i] += v * (1 + pan); } };

function tick(at, amp, pan, tone = 2300, len = 0.045) {
  let hp = 0, prev = 0;
  for (let i = Math.floor(at * SR); i < (at + len) * SR; i++) {
    const t = i / SR - at, env = clamp(t / 0.0015) * Math.exp(-t * 95);
    const n = rnd(); hp = 0.85 * (hp + n - prev); prev = n;
    add(i, amp * env * (0.5 * hp + 0.5 * Math.sin(TAU * tone * t)), pan);
  }
}
function swish(at, len, amp, pan = 0) {
  let lp = 0;
  for (let i = Math.floor(at * SR); i < (at + len) * SR; i++) {
    const k = (i / SR - at) / len, env = Math.sin(Math.PI * k) ** 2;
    lp += (rnd() - lp) * (0.05 + 0.2 * k);
    add(i, amp * env * lp, pan);
  }
}
function glide(at, len, f0, f1, amp, pan = 0) {
  let ph = 0;
  for (let i = Math.floor(at * SR); i < (at + len) * SR; i++) {
    const k = (i / SR - at) / len; ph += TAU * (f0 + (f1 - f0) * smooth(k)) / SR;
    add(i, amp * Math.sin(Math.PI * k) ** 1.5 * Math.sin(ph), pan);
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
    const t = i / SR - at, env = clamp(t / 0.006) * Math.exp(-t * 3.4);
    add(i, amp * env * (Math.sin(TAU * f * t) + 0.35 * Math.sin(TAU * f / 2 * t)), pan);
  }
}

// 1 · the counter: one tick per new tab (same curve as film.html), rising in pitch; a dull thud at 37
{ let last = 1;
  for (let t = 0.35; t <= 2.26; t += 1 / 1000) {
    const k = clamp((t - 0.35) / 1.9), n = Math.max(1, Math.round(1 + 36 * Math.pow(k, 1.6)));
    if (n !== last) { tick(t, 0.32, (n % 2 ? 0.2 : -0.2), 1700 + n * 24); last = n; }
  } }
pop(2.25, 170, 70, 0.6, 0.45);
swish(2.8, 0.35, 0.08);

// 2 · the account chooser slides in; a small blip when the "?" appears
swish(3.0, 0.45, 0.12);
[3.6, 4.05, 4.5, 4.95].forEach((t, i) => tick(t, 0.12, -0.3 + i * 0.2, 2600, 0.03));
glide(4.5, 0.18, 600, 900, 0.1, 0.3);
swish(5.4, 0.3, 0.07);

// 3 · wrong account: the card arrives; the lock's shake is a low double "bonk"
swish(5.6, 0.45, 0.12);
pop(6.45, 240, 150, 0.45, 0.22);
pop(6.62, 220, 130, 0.4, 0.26);
glide(6.45, 0.4, 110, 95, 0.08);

// 4 · indigo rises: a swell of air and a rising tone
{ let lp = 0; for (let i = Math.floor(8.1 * SR); i < 9.1 * SR; i++) {
  const k = (i / SR - 8.1) / 1.0; lp += (rnd() - lp) * (0.02 + 0.2 * k);
  add(i, 0.22 * Math.sin(Math.PI * Math.min(1, k * 1.15)) ** 2 * lp); } }
glide(8.15, 0.85, 220, 660, 0.08);

// 5 · the widget lands; each tap clicks, and each "opened in the right account" rings softly
pop(9.2, 520, 260, 0.28, 0.3);
tick(10.6, 0.4, 0.1, 3000, 0.035);
bell(10.88, 1318.5, 0.13, 1.0, 0.15);
tick(12.0, 0.4, -0.1, 3000, 0.035);
bell(12.28, 1568.0, 0.13, 1.0, -0.15);
swish(13.35, 0.4, 0.07);

// a little room, then normalise to -3 dB
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
fs.writeFileSync(process.argv[2] || 'film.wav', buf);
console.log('wrote', process.argv[2] || 'film.wav');
