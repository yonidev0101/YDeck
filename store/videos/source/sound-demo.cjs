// Sound for demo.html (30 s, the demo). Usage: node sound-demo.cjs out.wav
// The instruments of sound-close.cjs, scored to the dot. The same idea: everything sits on a suspended chord (C with D
// and G), the amber voice is a plucked D that wants to resolve, and only the end resolves (C E G), into the sting.
//   0.4  each project that arrives: a marimba note, sooner and sooner
//   3.4  at work: a soft pulse at 100 bpm, typing on the 16ths, spread across the room
//   6.6  astray: plucks (amber), low muted notes (grey); 8.45 into order: the pulse stops, a run up the chord as they line up
//   9.2  a reversed swell, and the folder opens into YDeck on a marimba figure; the pulse comes back, lighter
//  11.7… each landing of the dot: a note in its state's voice (the amber voice where it needs you)
//  22.6  the click; 24.35 nothing is open: C E G, and the amber D settles to E on the green dot
//  26.05 the legend: one note per hook, a pad on C E G, and a swell into the sting
const fs = require('fs');
const SR = 48000, DUR = 30.0, N = Math.round(SR * DUR);
const L = new Float32Array(N), R = new Float32Array(N), VL = new Float32Array(N), VR = new Float32Array(N); // dry, and the reverb send
const TAU = Math.PI * 2, BEAT = 0.6;
const clamp = (v, a = 0, b = 1) => Math.max(a, Math.min(b, v));
const smooth = v => { v = clamp(v); return v * v * (3 - 2 * v); };
let seed = 2026; const rnd = () => ((seed = (seed * 1664525 + 1013904223) >>> 0) / 4294967296) * 2 - 1;
const hz = m => 440 * Math.pow(2, (m - 69) / 12);
const C4 = 60, D4 = 62, E4 = 64, G4 = 67, A4 = 69, C5 = 72, D5 = 74, E5 = 76, G5 = 79, A5 = 81, C6 = 84, D6 = 86;
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
// 0 · the projects arrive, each with its dot (the times of demo.html's APPEAR)
pad(0, 9.0, [C4, D4, G4], 0.045, t => 300 + 900 * smooth(t / 5), 2.0, 0.6);
const RUN = [C5, D5, G5, A5, C6, D6];
for (let k = 0; k < 12; k++) marimba(0.4 + 2.6 * Math.pow(k / 11, 0.55) + 0.12, RUN[k % 6] - (k < 6 ? 12 : 0), 0.06, k % 2 ? 0.35 : -0.35);

// 1 · at work: the pulse and the typing
for (let b = 6; b * BEAT < 8.4; b++) thump(b * BEAT, b % 2 ? 0.3 : 0.22);
const desks = [[3.5, 1.6, -0.7], [3.7, 1.3, 0.6], [4.0, 1.9, -0.2], [3.8, 1.5, 0.3], [4.3, 1.7, -0.45], [4.1, 1.4, 0.75]];
for (const [start, period, pan] of desks) for (let s0 = start; s0 < 8.3; s0 += period)
  for (let k = 0; k < 6; k++) { const at = Math.round((s0 + k * BEAT / 4) / (BEAT / 4)) * (BEAT / 4); if (at < 8.3 && rnd() > -0.5) key(at + rnd() * 0.004, 0.035, pan); }

// 2 · astray: amber plucks, grey low notes (demo.html's ASTRAY)
[[6.6, 'wait', 0.4], [6.95, 'wait', 0.1], [7.25, 'gone', -0.3], [7.4, 'gone', -0.6], [7.6, 'wait', 0.6], [7.72, 'gone', -0.1], [7.92, 'open', -0.4]]
  .forEach(([at, st, pan], i) => st === 'wait' ? pluck(at, i % 2 ? G5 : D5, 0.16, pan, 1.6, 0.5)
    : st === 'gone' ? marimba(at, D4 - 12 + (i % 2) * 7, 0.07, pan) : marimba(at, D6, 0.03, pan));
lowSwell(6.5, 2.0, 0.02);

// 3 · into order: a run up the chord as the dots line up, then the folder opens into YDeck
[C5, D5, E5, G5, A5, C6, D6, E5 + 12, G5 + 12].forEach((m, i) => marimba(8.75 + i * 0.045, m, 0.045, 0.4 - i * 0.1));
swellTo(9.25, 0.9, 0.14, G5);
[[9.3, C5], [9.45, D5], [9.6, G5], [9.75, A5]].forEach(([at, m], i) => marimba(at, m, 0.07, 0.3 - i * 0.2));
pad(9.1, 25.7, [A4 - 12, C5 - 12, E5 - 12, G4], 0.04, t => 500 + 1100 * smooth((t - 9.1) / 2) - 1100 * smooth((t - 24.1) / 1.4), 0.9, 1.2);
for (let b = 18; b * BEAT < 22.5; b++) thump(b * BEAT, b % 2 ? 0.15 : 0.11);

// 4 · each landing of the dot, in its state's voice (demo.html's GUIDE)
[[11.7, 'note'], [13.45, 'wait'], [13.7, 'wait'], [13.95, 'wait'], [18.55, 'wait'], [18.85, 'wait'], [19.15, 'wait'],
  [19.75, 'wait'], [20.05, 'work'], [20.35, 'wait']].forEach(([at, st], i) => {
  const pan = i % 2 ? 0.25 : -0.25, m = st === 'work' ? E5 : A5;
  if (st === 'wait') pluck(at, [D5, G5, D6][i % 3], 0.1, pan, 1.4, 0.4);
  else { marimba(at, m, 0.07, pan); marimba(at + 0.09, m + 12, 0.02, pan); }
});
marimba(14.5, C5, 0.05, 0.3); marimba(17.8, G5, 0.05, -0.3);  // the screen changes like a tab
// the diff scrolls: a few keys, as if it were being written
for (let k = 0; k < 10; k++) key(15.8 + k * BEAT / 4, 0.03, 0.2 - k * 0.04);

// 5 · the click, the day's plan, and nothing is open
swellTo(22.6, 0.7, 0.06);
key(22.58, 0.14, -0.3); marimba(22.62, G4, 0.06, -0.3);
[[23.0, D5], [23.2, G5], [23.4, D5]].forEach(([at, m], i) => marimba(at, m, 0.05, -0.2 + i * 0.2));
[[24.35, C5, -0.3], [24.5, E5, 0], [24.65, G5, 0.3], [24.8, C6, 0]].forEach(([at, m, pan]) => marimba(at, m, 0.08, pan));
pluck(24.75, E5, 0.16, 0, 2.0, 0.35);                     // the amber D, settled to E at last: the green dot

// 6 · the legend: one note per hook
pad(25.3, 30.0, [C4, E4, G4], 0.04, t => 600 + 600 * smooth((t - 25.3) / 1.5) - 700 * smooth((t - 28.6) / 1.4), 0.8, 0.3);
marimba(26.05, G5, 0.07, 0.4);                            // SessionStart: open
marimba(26.75, E5, 0.07, 0.15); [26.75, 26.9, 27.05].forEach(at => key(at, 0.045, 0.15)); // UserPromptSubmit: at work
pluck(27.45, D5, 0.2, -0.15, 2.0, 0.45);                  // Notification: the amber voice
marimba(28.15, C4, 0.08, -0.4);                           // SessionEnd: gone, the low C
swellTo(29.95, 1.3, 0.1);

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
fs.writeFileSync(process.argv[2] || 'demo.wav', buf);
console.log('wrote', process.argv[2] || 'demo.wav');
