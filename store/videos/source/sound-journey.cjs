// Sound for journey.html (13.2 s, "The journey"). Usage: node sound-journey.cjs out.wav
// A ride at 120 bpm. The amber piece has its own voice (a plucked string); every stop it closes lands on a percussive lock
// and a marimba note, the notes climbing a suspended scale that never settles while there is something open. The last
// stop keeps its D. Then the pull back: everything drops to a long reversed swell, and on the reveal the D finally
// resolves into the brand's chord (C E G, as in the sting), with a low root, when the dot lands in the opening.
const fs = require('fs');
const SR = 48000, DUR = 13.2, N = Math.round(SR * DUR);
const L = new Float32Array(N), R = new Float32Array(N), VL = new Float32Array(N), VR = new Float32Array(N);
const TAU = Math.PI * 2, BEAT = 0.5;
const clamp = (v, a = 0, b = 1) => Math.max(a, Math.min(b, v));
const smooth = v => { v = clamp(v); return v * v * (3 - 2 * v); };
let seed = 909; const rnd = () => ((seed = (seed * 1664525 + 1013904223) >>> 0) / 4294967296) * 2 - 1;
const hz = m => 440 * Math.pow(2, (m - 69) / 12);
const C3 = 48, C4 = 60, D4 = 62, G4 = 67, A4 = 69, C5 = 72, D5 = 74, E5 = 76, G5 = 79, A5 = 81, C6 = 84, D6 = 86, E6 = 88, G6 = 91;
function add(i, v, pan = 0, send = 0.3) {
  if (i < 0 || i >= N) return;
  const l = v * Math.cos((pan + 1) * Math.PI / 4) * 1.41, r = v * Math.sin((pan + 1) * Math.PI / 4) * 1.41;
  L[i] += l; R[i] += r; VL[i] += l * send; VR[i] += r * send;
}

// ---------- instruments ----------
// the amber voice: a plucked string (Karplus–Strong), warm, with a slow bloom
function pluck(at, m, amp, pan = 0, len = 2.6, bright = 0.5, glideTo = null, glideAt = 0) {
  let f = hz(m), P = Math.round(SR / f);
  const buf = new Float32Array(Math.round(SR / hz(m - 24)));
  for (let i = 0; i < P; i++) buf[i] = rnd() * (1 - bright) + rnd() * bright;
  let idx = 0, last = 0;
  const i0 = Math.floor(at * SR);
  for (let n = 0; n < len * SR; n++) {
    if (glideTo !== null && n / SR > glideAt) { const k = smooth((n / SR - glideAt) / 0.35); P = Math.round(SR / (hz(m) * Math.pow(2, (glideTo - m) * k / 12))); }
    const j = idx % P, nxt = buf[(idx + 1) % P];
    const v = buf[j];
    buf[j] = 0.996 * (0.5 * (v + nxt) * 0.7 + 0.3 * last); last = v;
    idx = (idx + 1) % P;
    const env = clamp(n / (0.004 * SR)) * Math.exp(-n / SR * 1.1);
    add(i0 + n, 2.6 * amp * env * v, pan, 0.45);
  }
}
// marimba: a sine with a fast-fading 4× partial and a little body
function marimba(at, m, amp, pan = 0) {
  const f = hz(m), i0 = Math.floor(at * SR);
  for (let n = 0; n < 1.4 * SR; n++) {
    const t = n / SR, env = clamp(t / 0.002) * Math.exp(-t * 4.2);
    const v = Math.sin(TAU * f * t + 1.2 * Math.exp(-t * 30) * Math.sin(TAU * f * 4 * t)) + 0.25 * Math.sin(TAU * f * 3.93 * t) * Math.exp(-t * 18);
    add(i0 + n, amp * env * v, pan, 0.35);
  }
}
// the pulse: a soft, round low thump
function thump(at, amp) {
  const i0 = Math.floor(at * SR);
  let ph = 0;
  for (let n = 0; n < 0.35 * SR; n++) {
    const t = n / SR; ph += TAU * (46 + 60 * Math.exp(-t * 28)) / SR;
    add(i0 + n, amp * clamp(t / 0.003) * Math.exp(-t * 11) * Math.sin(ph), 0, 0.05);
  }
}
// a typed key: a short filtered click with a tiny pitched body
function key(at, amp, pan) {
  const i0 = Math.floor(at * SR), tone = 1800 + rnd() * 500;
  let hp = 0, prev = 0;
  for (let n = 0; n < 0.035 * SR; n++) {
    const t = n / SR, e = clamp(t / 0.0008) * Math.exp(-t * 140), x = rnd();
    hp = 0.8 * (hp + x - prev); prev = x;
    add(i0 + n, amp * e * (0.7 * hp + 0.3 * Math.sin(TAU * tone * t)), pan, 0.15);
  }
}
// the pad: three detuned saws per note through a low-pass that opens and closes over `cut(t)`
function pad(from, to, notes, amp, cut, fadeIn = 1.2, fadeOut = 1.2) {
  const i0 = Math.floor(from * SR), i1 = Math.floor(to * SR);
  const voices = notes.flatMap(m => [-7, 0, 6].map(c => ({ f: hz(m) * Math.pow(2, c / 1200), ph: (rnd() + 1) / 2, pan: c / 10 })));
  let lpL = 0, lpR = 0;
  for (let i = i0; i < i1 && i < N; i++) {
    const t = i / SR;
    let l = 0, r = 0;
    for (const v of voices) { v.ph = (v.ph + v.f / SR) % 1; const s = 2 * v.ph - 1; l += s * (1 - v.pan); r += s * (1 + v.pan); }
    const a = 1 - Math.exp(-TAU * cut(t) / SR);
    lpL += (l - lpL) * a; lpR += (r - lpR) * a;
    const env = smooth((t - from) / fadeIn) * smooth((to - t) / fadeOut);
    const k = amp * env / voices.length;
    L[i] += lpL * k; R[i] += lpR * k; VL[i] += lpL * k * 0.6; VR[i] += lpR * k * 0.6;
  }
}
// a reversed swell: filtered noise and a rising tone that grow toward `at` and stop there
function swellTo(at, len, amp, m = null) {
  let lp = 0;
  for (let i = Math.floor((at - len) * SR); i < at * SR; i++) {
    const k = (i / SR - (at - len)) / len;
    lp += (rnd() - lp) * (0.01 + 0.25 * k * k);
    let v = lp * 0.8;
    if (m !== null) v += 0.25 * Math.sin(TAU * hz(m) * i / SR) * k;
    add(i, amp * Math.pow(k, 2.2) * v, 0, 0.6);
  }
}
// a low swell under the camera's lean
function lowSwell(at, len, amp) {
  for (let n = 0; n < len * SR; n++) {
    const t = n / SR, k = t / len;
    add(Math.floor(at * SR) + n, amp * Math.sin(Math.PI * k) * Math.sin(TAU * hz(D4 - 24) * t), 0, 0.2);
  }
}

// a hat: a breath of bright noise
function hat(at, amp, pan = 0) {
  const i0 = Math.floor(at * SR); let hp = 0, prev = 0;
  for (let n = 0; n < 0.05 * SR; n++) { const t = n / SR, x = rnd(); hp = 0.6 * (hp + x - prev); prev = x; add(i0 + n, amp * Math.exp(-t * 90) * hp, pan, 0.1); }
}
// the ride: a band of noise that sweeps up and away, moving right to left like the piece
function whoosh(from, to, amp) {
  let lp = 0, bp = 0;
  for (let i = Math.floor(from * SR); i < to * SR; i++) {
    const k = (i / SR - from) / (to - from), env = Math.sin(Math.PI * k) ** 1.5;
    const cut = 0.02 + 0.3 * Math.sin(Math.PI * k);
    lp += (rnd() - lp) * cut; bp += (lp - bp) * 0.5;
    add(i, amp * env * (lp - bp) * 3, 0.7 - 1.4 * k, 0.25);
  }
}
// the lock: a stop closing, a short low knock with a click
function lock(at, amp) { thump(at, amp); key(at, amp * 0.4, 0); }

// ---------- the score ----------
const STOP = [{ A: 0.9, C: 1.45 }, { A: 2.5, C: 2.95 }, { A: 4.0, C: 4.45 }, { A: 5.5, C: 5.95 }, { A: 7.0 }];
// 0 · the end of a day: a pad, the agent typing (the drop of indigo), the amber piece appears
pad(0, 9.2, [C4, D4, G4], 0.04, t => 400 + 1400 * smooth(t / 2) - 900 * smooth((t - 8.2) / 1.0), 0.8, 0.8);
for (let k = 0; k < 6; k++) key(0.25 + k * BEAT / 4, 0.05, 0);
pluck(0.9, D5, 0.3, 0, 1.6, 0.5);
// 1 · the ride: a pulse on every beat, hats on the off-beats, a whoosh for every run between stops
for (let b = 3; b * BEAT < 8.9; b++) { thump(b * BEAT, b % 2 ? 0.46 : 0.38); hat(b * BEAT + BEAT / 2, 0.09, b % 2 ? 0.3 : -0.3); }
[[1.5, 2.5], [3.0, 4.0], [4.5, 5.5], [6.0, 7.0], [7.9, 8.9]].forEach(([a, b]) => whoosh(a, b, 0.4));
// 2 · each stop: the piece lands (its pluck), then what it closed locks, on a climbing note
const climb = [G4, A4, C5, D5];
STOP.forEach((s, i) => {
  if (i > 0) pluck(s.A, D5 + (i % 2 ? 5 : 0), 0.16, 0.1, 1.0, 0.4);
  if (s.C) { lock(s.C, 0.32); marimba(s.C, climb[i], 0.11, -0.2 + i * 0.13); marimba(s.C + 0.06, climb[i] + 12, 0.04, 0.2); }
});
pluck(7.0, D5, 0.3, 0, 2.4, 0.55);                                // the next step: D, held open
// 3 · the pull back: the floor drops away, a long reversed swell toward the reveal
swellTo(10.9, 2.0, 0.45, D5);
lowSwell(8.9, 2.0, 0.03);
// 4 · the reveal: the D resolves into the brand's chord, with the root underneath, as the dot lands
[[C5, -0.15], [E5, 0.2], [G5, -0.25], [E6, 0.35]].forEach(([m, pan], i) => marimba(10.9 + i * 0.02, m, 0.09 - i * 0.015, pan));
pluck(10.9, E5, 0.22, 0.05, 2.2, 0.4);
pad(10.85, 13.2, [C3, C4, E5 - 12, G4], 0.045, t => 2200 - 1400 * smooth((t - 11) / 2), 0.05, 1.4);
thump(10.9, 0.4);
key(11.15, 0.05, 0);                                             // the name rises

// ---------- the room: a small plate (4 combs + 2 all-passes per side) on the send ----------
function reverb(x, offs) {
  const combs = [1694, 1760, 1622, 1547].map(d => ({ b: new Float32Array(d + offs), i: 0, lp: 0 }));
  const aps = [605, 480].map(d => ({ b: new Float32Array(d + offs), i: 0 }));
  const y = new Float32Array(N);
  for (let n = 0; n < N; n++) {
    let s = 0;
    for (const c of combs) { const o = c.b[c.i]; c.lp = o * 0.7 + c.lp * 0.3; c.b[c.i] = x[n] + c.lp * 0.84; c.i = (c.i + 1) % c.b.length; s += o; }
    s *= 0.25;
    for (const a of aps) { const o = a.b[a.i]; const v = -s + o; a.b[a.i] = s + o * 0.5; a.i = (a.i + 1) % a.b.length; s = v; }
    y[n] = s;
  }
  return y;
}
const WL = reverb(VL, 0), WR = reverb(VR, 23);
const OL = new Float32Array(N), OR = new Float32Array(N);
let peak = 0;
for (let i = 0; i < N; i++) { OL[i] = L[i] + WL[i] * 0.9; OR[i] = R[i] + WR[i] * 0.9; peak = Math.max(peak, Math.abs(OL[i]), Math.abs(OR[i])); }
const norm = 0.8 / peak;
const buf = Buffer.alloc(44 + N * 4);
buf.write('RIFF', 0); buf.writeUInt32LE(36 + N * 4, 4); buf.write('WAVE', 8);
buf.write('fmt ', 12); buf.writeUInt32LE(16, 16); buf.writeUInt16LE(1, 20); buf.writeUInt16LE(2, 22);
buf.writeUInt32LE(SR, 24); buf.writeUInt32LE(SR * 4, 28); buf.writeUInt16LE(4, 32); buf.writeUInt16LE(16, 34);
buf.write('data', 36); buf.writeUInt32LE(N * 4, 40);
for (let i = 0; i < N; i++) {
  // a gentle soft clip keeps the thumps round
  buf.writeInt16LE(Math.round(Math.tanh(OL[i] * norm * 1.1) * 32767 * 0.95), 44 + i * 4);
  buf.writeInt16LE(Math.round(Math.tanh(OR[i] * norm * 1.1) * 32767 * 0.95), 46 + i * 4);
}
fs.writeFileSync(process.argv[2] || 'journey.wav', buf);
console.log('wrote', process.argv[2] || 'journey.wav');
