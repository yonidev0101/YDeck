// Sound for desktop.html (37.6 s, the Windows launch film). UI sounds only, as in the other films; the sting brings
// the signature. Usage: node sound-desktop.cjs out.wav
const fs = require('fs');
const SR = 48000, DUR = 37.6, N = Math.round(SR * DUR);
const L = new Float32Array(N), R = new Float32Array(N);
const TAU = Math.PI * 2;
const clamp = (v, a = 0, b = 1) => Math.max(a, Math.min(b, v));
const smooth = v => { v = clamp(v); return v * v * (3 - 2 * v); };
let seed = 51; const rnd = () => ((seed = (seed * 1664525 + 1013904223) >>> 0) / 4294967296) * 2 - 1;
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
/** A key on a real keyboard: a low thock under a short click. */
function key(at, amp = 0.3, pan = 0) { pop(at, 230, 140, amp * 0.7, 0.09, pan); tick(at, amp * 0.6, pan, 1700, 0.03); }
/** Light typing: a soft click per character. */
function typing(from, chars, every, amp = 0.12) { for (let i = 0; i < chars; i++) tick(from + i * every + (rnd() * 0.012), amp * (0.8 + 0.3 * Math.abs(rnd())), (i % 3 - 1) * 0.15, 2600 + (i % 4) * 140, 0.025); }
/** The "right account" ding of all the films (G5 + D6). */
function ding(at, amp = 0.12) { bell(at, 783.99, amp, 1.3, -0.2); bell(at + 0.08, 1174.66, amp, 1.3, 0.2); }

// 0 · folders appear in Explorer
for (let i = 0; i < 10; i++) tick(0.6 + i * 0.13, 0.1, (i % 5) / 4 * 0.8 - 0.4, 1800 + i * 60, 0.035);

// 1 · the windows pile up, each landing with a soft knock; then they are pulled away
for (let i = 0; i < 8; i++) { const at = 4.55 + i * 0.36; pop(at, 360 - i * 12, 200, 0.16, 0.2, (i % 2 ? 0.4 : -0.4)); tick(at, 0.08, 0, 1300, 0.03); }
swish(8.65, 0.75, 0.16, -0.3, 0.3, 0.35);

// 2 · the empty widget lands on the desktop (A major shimmer, as the phone film)
pop(9.45, 520, 260, 0.22, 0.3);
[880, 1108.73, 1318.51, 1760].forEach((f, i) => bell(9.5 + i * 0.07, f, 0.04, 1.2, -0.3 + i * 0.2));

// 3 · Explorer slides in; the folder is picked up, dragged, dropped; everything fills in
swish(12.3, 0.5, 0.08, -0.5, -0.3);
tick(13.2, 0.3, -0.4, 2600, 0.035);
swish(13.3, 0.85, 0.07, -0.4, 0.3, 0.12);
pop(14.2, 300, 170, 0.24, 0.28, 0.2); tick(14.2, 0.18, 0.2, 1500, 0.04);
[659.25, 987.77, 1318.51].forEach((f, i) => bell(14.3 + i * 0.06, f, 0.045, 1.0, 0.1 + i * 0.1));
for (let i = 0; i < 8; i++) tick(14.6 + i * 0.13, 0.13, 0.5 - (i % 4) / 3, 2000 + i * 110, 0.032);
for (let i = 0; i < 3; i++) pop(15.8 + i * 0.14, 640, 330, 0.1, 0.12, -0.3 - i * 0.1);
swish(16.0, 0.4, 0.05, 0, 0, 0.3);

// 4 · VS Code opens, then Claude Code; a question is typed; both close
tick(18.15, 0.36, -0.2, 3000, 0.035); swish(18.2, 0.5, 0.1, -0.2, 0.1, 0.3);
tick(19.8, 0.36, -0.3, 3000, 0.035); swish(19.85, 0.5, 0.1, -0.3, 0.2, 0.3);
typing(20.45, 26, 0.031, 0.09);
swish(21.4, 0.4, 0.08, 0.2, -0.2, 0.15);

// 5 · the git line zooms; the commit: dot turns green with the ding
swish(22.0, 0.45, 0.06, 0.3, 0.3, 0.3);
ding(23.55, 0.12);

// 6 · Ctrl, Alt, Space; the search opens; "nova fire"; Enter; Chrome in the right profile
key(25.65, 0.32, -0.2); key(25.8, 0.32, 0); key(25.95, 0.38, 0.2);
pop(26.42, 560, 300, 0.18, 0.22); bell(26.45, 1318.51, 0.04, 0.8);
typing(26.78, 9, 0.094, 0.13);
key(28.0, 0.34, 0);
swish(28.15, 0.5, 0.1, 0, 0.2, 0.3);
ding(28.6, 0.13);

// 7 · Explorer again: one folder after another turns into its logo (rising notes)
swish(30.5, 0.45, 0.07, -0.2, 0, 0.2);
[783.99, 880, 987.77, 1046.5, 1174.66, 1318.51, 1479.98, 1567.98].forEach((f, i) => { pop(31.0 + i * 0.16, 520, 300, 0.09, 0.14, -0.4 + i * 0.1); bell(31.03 + i * 0.16, f, 0.03, 0.7, -0.4 + i * 0.1); });
tick(32.15, 0.22, -0.3, 2600, 0.035);

// 8 · the widget alone; a breath of light; the riser into the sting
swish(34.15, 0.6, 0.08, 0, 0, 0.1);
pop(34.45, 460, 230, 0.16, 0.3);
[1174.66, 1479.98, 1760].forEach((f, i) => bell(34.5 + i * 0.08, f, 0.035, 1.4, -0.2 + i * 0.2));
{ let lp = 0; for (let i = Math.floor(36.5 * SR); i < 37.6 * SR; i++) {
  const k = (i / SR - 36.5) / 1.1; lp += (rnd() - lp) * (0.02 + 0.28 * k * k);
  add(i, 0.24 * smooth(k / 0.85) * (1 - smooth((k - 0.93) / 0.07)) * lp); } }

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
fs.writeFileSync(process.argv[2] || 'desktop.wav', buf);
console.log('wrote', process.argv[2] || 'desktop.wav');
