// Sound for close.html (18.2 s, "Close the day"). Usage: node sound-close.cjs out.wav
// Scored, not a string of UI blips. The idea is musical and matches the brand: the film stays OPEN. Everything sits on a
// suspended chord (C with D and G, no E), and the amber piece has its own voice, a plucked D, the note that wants to
// resolve and doesn't. The sting that follows lands on C major (its chord is C E G): only there does the D settle to E.
//   0.0  the agents work: a soft pulse at 100 bpm, typing on the 16ths, panned to each terminal, a pad breathing in
//   3.5  one stops: the pulse drops out, the room goes quiet, the amber voice (D, then G); a low swell as the camera leans in
//   6.6  a reversed swell, and YDeck's rows arrive on a marimba figure; the pulse comes back, lighter
//  10.8  Nova and Pulse close: two rising marimba notes that resolve; Orbit's next step: the amber D again, left open
//  14.2  the pad closes its filter; the amber piece gathers: its D slides up and stops short, into the sting
const fs = require('fs');
const SR = 48000, DUR = 18.2, N = Math.round(SR * DUR);
const L = new Float32Array(N), R = new Float32Array(N), VL = new Float32Array(N), VR = new Float32Array(N); // dry, and the reverb send
const TAU = Math.PI * 2, BEAT = 0.6;
const clamp = (v, a = 0, b = 1) => Math.max(a, Math.min(b, v));
const smooth = v => { v = clamp(v); return v * v * (3 - 2 * v); };
let seed = 2026; const rnd = () => ((seed = (seed * 1664525 + 1013904223) >>> 0) / 4294967296) * 2 - 1;
const hz = m => 440 * Math.pow(2, (m - 69) / 12);
const C4 = 60, D4 = 62, G4 = 67, A4 = 69, C5 = 72, D5 = 74, E5 = 76, G5 = 79, A5 = 81, C6 = 84, D6 = 86;
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

// ---------- the score ----------
// 0 · the agents work
pad(0, 3.7, [C4, D4, G4], 0.05, t => 300 + 900 * smooth(t / 3.2), 2.0, 0.25);
for (let b = 1; b * BEAT < 3.45; b++) thump(b * BEAT, b % 2 ? 0.34 : 0.26);
// typing on the 16ths: Nova (left) and Pulse (right) in their loops, Orbit (centre) once
const loops = [[1.2, 1.6, -0.55], [1.4, 1.3, 0.55]];
for (const [start, period, pan] of loops) for (let s0 = start; s0 < 3.45; s0 += period)
  for (let k = 0; k < 6; k++) { const at = Math.round((s0 + k * BEAT / 4) / (BEAT / 4)) * (BEAT / 4); if (at < 3.45 && rnd() > -0.6) key(at + rnd() * 0.004, 0.05, pan); }
for (let k = 0; k < 7; k++) key(1.35 + k * BEAT / 4, 0.055, 0);

// 1 · one of them waits: everything drops out; the amber voice
pluck(3.5, D5, 0.34, 0.05, 3.0, 0.55);
pluck(3.78, G5, 0.2, 0.15, 2.6, 0.4);
lowSwell(3.6, 2.6, 0.025);
[4.1 + 0.9, 4.1 + 1.8].forEach(at => pluck(at, D6, 0.05, 0.1, 1.0, 0.3)); // the blink, barely there

// 2 · YDeck shows you where
swellTo(6.75, 0.9, 0.16, G5);
[[6.8, C5], [6.95, D5], [7.1, G5], [7.25, A5]].forEach(([at, m], i) => marimba(at, m, 0.08, 0.3 - i * 0.2));
pluck(6.85, D5, 0.16, 0, 2.0, 0.3);                        // the amber piece moving to its place
pad(6.7, 14.6, [A4 - 12, C5 - 12, E5 - 12, G4], 0.045, t => 500 + 1100 * smooth((t - 6.7) / 2) - 1300 * smooth((t - 13.4) / 1.0), 0.8, 1.0);
for (let b = 13; b * BEAT < 10.1; b++) thump(b * BEAT, 0.18);

// 3 · the day closes: two resolutions, and the one that stays open
[[10.8 + 0.25, E5, -0.4], [11.5 + 0.25, G5, 0.4]].forEach(([at, m, pan]) => { key(at - 0.25, 0.08, pan); marimba(at, m, 0.1, pan); marimba(at + 0.1, m + 12, 0.04, pan); });
pluck(12.2, D5, 0.3, 0.05, 3.2, 0.5);                       // Orbit's next step: D, unresolved
for (let b = 17; b * BEAT < 13.5; b++) thump(b * BEAT, 0.14);

// 4 · tomorrow starts here: the room thins; the amber D gathers, slides up and stops short of resolving
pad(14.2, 18.2, [C4, D4, G4], 0.035, t => 1400 - 1100 * smooth((t - 14.2) / 3.5), 0.6, 0.4);
pluck(16.6, D5, 0.22, 0, 1.8, 0.35, E5 - 0.6, 0.55);          // almost E: the sting finishes the thought
swellTo(18.15, 1.2, 0.12);

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
fs.writeFileSync(process.argv[2] || 'close.wav', buf);
console.log('wrote', process.argv[2] || 'close.wav');
