// Sound for demo.html (30 s, the demo). Usage: node sound-demo.cjs out.wav
// A driving track, the way launch films are scored: four on the floor, a side-chained bass, chord stabs, in A minor at
// ~122 bpm. The tempo is set so the two moments land on the beat: the drop as Now opens (6.6 s) and the hit on
// "nothing is open" (24.35 s), 36 beats apart.
//   0.3   the terminals: a blip as each arrives; hats, then a filtered bass and a half-time kick, tense; Orbit's question pings
//   4.6   a riser and a snare roll, and the drop
//   6.6   the groove: kick, claps, open hats, bass in eighths, stabs on Am F C G; the rings on the screen ping on top
//  20.4   the filter closes; 22.4 a roll and a riser under Close the day; 24.35 the hit, and the groove comes back brighter
//  28.3   the last stab, and its tail into the logo sting
const fs = require('fs');
const SR = 48000, DUR = 30.0, N = Math.round(SR * DUR);
// two buses: the drums, and the music (ducked by the kick); each with a reverb send
const DL = new Float32Array(N), DR = new Float32Array(N), ML = new Float32Array(N), MR = new Float32Array(N);
const VL = new Float32Array(N), VR = new Float32Array(N);
const TAU = Math.PI * 2;
const clamp = (v, a = 0, b = 1) => Math.max(a, Math.min(b, v));
const smooth = v => { v = clamp(v); return v * v * (3 - 2 * v); };
let seed = 2026; const rnd = () => ((seed = (seed * 1664525 + 1013904223) >>> 0) / 4294967296) * 2 - 1;
const hz = m => 440 * Math.pow(2, (m - 69) / 12);

const DROP = 6.6, HIT = 24.35, B = (HIT - DROP) / 36;    // one beat, ~0.493 s
const beat = b => DROP + b * B;                           // beat 0 is the drop; the intro counts up from below zero

function put(bus, i, v, pan = 0, send = 0.2) {
  if (i < 0 || i >= N || !Number.isFinite(v)) return;
  const l = v * Math.cos((pan + 1) * Math.PI / 4) * 1.41, r = v * Math.sin((pan + 1) * Math.PI / 4) * 1.41;
  if (bus === 'd') { DL[i] += l; DR[i] += r; } else { ML[i] += l; MR[i] += r; }
  VL[i] += l * send; VR[i] += r * send;
}
// a state-variable filter, for noise and synths
function svf() { let lo = 0, bp = 0; return (x, f, q = 0.7) => { const k = 2 * Math.sin(Math.PI * Math.min(f, SR / 6) / SR); const hi = x - lo - q * bp; bp += k * hi; lo += k * bp; return { lo, bp, hi }; }; }

// ---------- drums ----------
const KICKS = [];
function kick(at, amp = 1) {
  KICKS.push(at);
  const i0 = Math.floor(at * SR); let ph = 0;
  for (let n = 0; n < 0.5 * SR; n++) {
    const t = n / SR; ph += TAU * (46 + 130 * Math.exp(-t * 32)) / SR;
    const body = Math.sin(ph) * Math.exp(-t * 7.5), click = Math.exp(-t * 900) * 0.6;
    put('d', i0 + n, amp * 0.9 * Math.tanh(1.6 * (body + click)), 0, 0.02);
  }
}
function clap(at, amp = 1, pan = 0) {
  const f = svf(), i0 = Math.floor(at * SR);
  for (let n = 0; n < 0.35 * SR; n++) {
    const t = n / SR;
    // three quick bursts, then the room
    const e = [0, 0.009, 0.019].reduce((s, d) => s + (t >= d ? Math.exp(-(t - d) * 160) : 0), 0) + (t > 0.019 ? 0.5 * Math.exp(-t * 16) : 0);
    put('d', i0 + n, amp * 0.55 * e * f(rnd(), 1500, 0.9).bp * 3, pan, 0.5);
  }
}
function hat(at, amp = 1, open = false, pan = 0.2) {
  const f = svf(), i0 = Math.floor(at * SR), len = open ? 0.28 : 0.05;
  for (let n = 0; n < len * SR; n++) {
    const t = n / SR;
    put('d', i0 + n, amp * 0.22 * Math.exp(-t * (open ? 14 : 75)) * f(rnd(), 9000, 0.6).hi, pan, 0.1);
  }
}
function snare(at, amp = 1) {
  const f = svf(), i0 = Math.floor(at * SR);
  for (let n = 0; n < 0.2 * SR; n++) {
    const t = n / SR;
    const v = 0.6 * f(rnd(), 4000, 0.8).hi * Math.exp(-t * 28) + 0.5 * Math.sin(TAU * 190 * t) * Math.exp(-t * 40);
    put('d', i0 + n, amp * 0.5 * v, 0, 0.35);
  }
}
// a crash on the big moments: a long, bright noise
function crash(at, amp = 1) {
  const f = svf(), i0 = Math.floor(at * SR);
  for (let n = 0; n < 2.2 * SR; n++) {
    const t = n / SR;
    put('d', i0 + n, amp * 0.25 * Math.exp(-t * 1.8) * f(rnd(), 7000, 0.5).hi, rnd() * 0.3, 0.6);
  }
}
// the impact under a drop: a deep sine boom
function boom(at, amp = 1) {
  const i0 = Math.floor(at * SR); let ph = 0;
  for (let n = 0; n < 1.6 * SR; n++) {
    const t = n / SR; ph += TAU * (32 + 50 * Math.exp(-t * 6)) / SR;
    put('d', i0 + n, amp * 0.8 * Math.sin(ph) * Math.exp(-t * 2.4), 0, 0.1);
  }
}

// ---------- music ----------
const saw = ph => 2 * (ph % 1) - 1;
function bass(at, len, m, amp = 1, cut = 1) {
  const f = svf(), i0 = Math.floor(at * SR), fr = hz(m);
  let a = 0, b = 0.37;
  for (let n = 0; n < (len + 0.03) * SR; n++) {
    const t = n / SR; a += fr / SR; b += fr * 1.004 / SR;
    const env = clamp(t / 0.004) * (t < len ? 1 : Math.exp(-(t - len) * 120));
    const x = 0.5 * (saw(a) + saw(b));
    const y = f(x, (180 + 1600 * Math.exp(-t * 9)) * cut, 0.5).lo + 0.5 * Math.sin(TAU * fr * t);
    put('m', i0 + n, amp * 0.42 * env * y, 0, 0.03);
  }
}
// a chord stab: three detuned saws a note, through a filter that snaps shut
function stab(at, notes, amp = 1, len = 0.26, bright = 1) {
  const i0 = Math.floor(at * SR);
  const vs = notes.flatMap(m => [-9, 0, 8].map(c => ({ fr: hz(m) * Math.pow(2, c / 1200), ph: (rnd() + 1) / 2, pan: c / 14 })));
  const fl = svf(), fr = svf();
  for (let n = 0; n < (len + 0.5) * SR; n++) {
    const i = i0 + n; if (i >= N) break;
    const t = n / SR;
    let l = 0, r = 0;
    for (const v of vs) { v.ph += v.fr / SR; const s = saw(v.ph); l += s * (1 - v.pan); r += s * (1 + v.pan); }
    const cut = 500 + 5200 * bright * Math.exp(-t * 7), env = clamp(t / 0.003) * (t < len ? 1 : Math.exp(-(t - len) * 9));
    const k = amp * 0.32 * env / vs.length;
    const yl = fl(l, cut, 0.6).lo * k, yr = fr(r, cut, 0.6).lo * k;
    ML[i] += yl; MR[i] += yr; VL[i] += yl * 0.5; VR[i] += yr * 0.5;
  }
}
// a held pad, filtered by cut(t)
function pad(from, to, notes, amp, cut) {
  const i0 = Math.floor(from * SR), i1 = Math.min(N, Math.floor(to * SR));
  const vs = notes.flatMap(m => [-7, 0, 6].map(c => ({ fr: hz(m) * Math.pow(2, c / 1200), ph: (rnd() + 1) / 2, pan: c / 10 })));
  const fl = svf(), fr = svf();
  for (let i = i0; i < i1; i++) {
    const t = i / SR;
    let l = 0, r = 0;
    for (const v of vs) { v.ph += v.fr / SR; const s = saw(v.ph); l += s * (1 - v.pan); r += s * (1 + v.pan); }
    const env = smooth((t - from) / 0.8) * smooth((to - t) / 0.6), k = amp * env / vs.length;
    const yl = fl(l, cut(t), 0.5).lo * k, yr = fr(r, cut(t), 0.5).lo * k;
    ML[i] += yl; MR[i] += yr; VL[i] += yl * 0.6; VR[i] += yr * 0.6;
  }
}
// a UI blip: a short sine with a fast upward bend (each terminal, each ring on the screen)
function blip(at, m, amp = 1, pan = 0) {
  const i0 = Math.floor(at * SR); let ph = 0;
  for (let n = 0; n < 0.3 * SR; n++) {
    const t = n / SR; ph += TAU * hz(m) * (1 + 0.06 * Math.exp(-t * 60)) / SR;
    put('m', i0 + n, amp * 0.3 * clamp(t / 0.002) * Math.exp(-t * 14) * (Math.sin(ph) + 0.2 * Math.sin(2 * ph)), pan, 0.4);
  }
}
// a riser: noise through a band-pass that climbs, and a rising tone, growing toward `at`
function riser(at, len, amp = 1) {
  const f = svf(); let ph = 0;
  for (let i = Math.max(0, Math.floor((at - len) * SR)); i < at * SR; i++) {
    const k = clamp((i / SR - (at - len)) / len);
    ph += TAU * (200 + 1800 * k * k) / SR;
    const v = f(rnd(), 300 + 7000 * k * k, 0.4).bp * 1.4 + 0.25 * Math.sin(ph);
    put('d', i, amp * 0.5 * k * k * v, 0, 0.5);
  }
}

// ---------- the score ----------
const CH = { AM: [57, 60, 64], F: [53, 57, 60], C: [55, 60, 64], G: [55, 59, 62] };
const ROOT = { AM: 45, F: 41, C: 48, G: 43 };
const BASS = [0, 0, 12, 0, 0, 12, 0, 7];

// 0 · the terminals: tense, filtered, building
[[0.3, 76, -0.5], [0.9, 79, -0.15], [1.5, 83, 0.45], [2.1, 86, 0.2]].forEach(([at, m, pan]) => blip(at, m, 0.9, pan));
pad(0, DROP - 0.25, [45, 52, 57, 60], 0.05, t => 250 + 1500 * smooth(t / 6));
for (let b = -12; b < 0; b += 0.25) if (beat(b) > 0.3) hat(beat(b), (b - Math.floor(b) === 0.5 ? 0.9 : 0.45) * smooth((beat(b) - 0.3) / 3), false, 0.25);
for (let b = -11; b < -0.5; b += 0.5) bass(beat(b), B * 0.42, 45, 0.8, 0.25 + 0.5 * smooth((beat(b) - 1) / 5));
for (let b = -8; b < -1; b += 2) kick(beat(b), 0.75);
// Orbit's question: an amber two-note ping
[[2.55, 0.7], [3.5, 1], [4.7, 0.6]].forEach(([at, a]) => { blip(at, 81, 0.8 * a, -0.2); blip(at + 0.12, 88, 0.6 * a, -0.2); });
riser(DROP - 0.05, 2.0, 0.9);
for (let b = -4; b < -0.5; b += (b < -2 ? 0.5 : 0.25)) snare(beat(b), 0.35 + 0.5 * smooth((b + 4) / 3.5));

// 1 · the drop: an impact, and the groove (beats 0 to 28)
boom(DROP, 1); crash(DROP, 1);
const PROG = ['AM', 'F', 'C', 'G'];
function groove(from, to, prog, cut = () => 1, bright = 1) {
  for (let b = from; b < to; b++) {
    const t = beat(b), inBar = b % 4, ch = prog[Math.floor(b / 4) % prog.length];
    kick(t, 1);
    if (inBar === 1 || inBar === 3) clap(t, 0.9);
    hat(t + B / 2, 0.9, true, 0.25);
    hat(t + B / 4, 0.35, false, -0.2); hat(t + 3 * B / 4, 0.35, false, -0.2);
    for (let e = 0; e < 2; e++) bass(t + e * B / 2, B * 0.4, ROOT[ch] + BASS[inBar * 2 + e], 1, cut(t));
    // stabs: the downbeat, and the "and" of two
    if (inBar === 0) stab(t, CH[ch], 0.9 * cut(t), 0.22, bright);
    if (inBar === 1) stab(t + B / 2, CH[ch], 0.55 * cut(t), 0.14, bright);
  }
}
groove(0, 28, PROG);
// the rings on the screen ping over the groove (demo.html's RINGS)
[[9.6, 81], [12.2, 76], [12.6, 81], [13.0, 84], [15.2, 79], [15.6, 81]].forEach(([at, m], i) => blip(at, m + 12, 0.7, i % 2 ? 0.35 : -0.35));
// Review's click, and Close the day's
[18.3, 22.6].forEach(at => { blip(at, 84, 0.8, -0.3); hat(at, 1.2, false, -0.3); });

// 2 · the build: the filter closes (beats 28 to 32), then a roll and a riser (32 to 36)
groove(28, 32, PROG, t => 1 - 0.65 * smooth((t - beat(28)) / (4 * B)));
for (let b = 32; b < 36; b++) kick(beat(b), b < 34 ? 0.8 : 0.9);
for (let b = 32; b < 35.75; b += (b < 34 ? 0.5 : b < 35 ? 0.25 : 0.125)) snare(beat(b), 0.3 + 0.6 * smooth((b - 32) / 3.75));
pad(beat(32), HIT - 0.02, [45, 52, 57, 60, 64], 0.05, t => 300 + 3000 * smooth((t - beat(32)) / (4 * B)));
riser(HIT - 0.02, 4 * B, 1);

// 3 · the hit on "nothing is open", and the groove again, brighter, on F G C C (beats 36 to 44)
boom(HIT, 1.1); crash(HIT, 1.2); stab(HIT, [48, 55, 60, 64, 67], 1.1, 0.5, 1.2);
groove(36, 44, ['F', 'G', 'C', 'C'], () => 1, 1.25);
blip(26.2, 84, 0.6, 0.3); blip(26.8, 88, 0.5, -0.2); blip(27.4, 91, 0.45, 0.1);   // the end card's lines

// 4 · the last stab, and its tail into the sting
const END = beat(44);
kick(END, 1); boom(END, 0.8); crash(END, 0.9); stab(END, [48, 55, 60, 64, 67, 71], 1, 0.9, 1);

// ---------- the side-chain: the music ducks under each kick ----------
const duck = new Float32Array(N).fill(1);
for (const at of KICKS) for (let n = 0; n < 0.3 * SR; n++) {
  const i = Math.floor(at * SR) + n; if (i >= N) break;
  duck[i] = Math.min(duck[i], 1 - 0.7 * Math.exp(-n / SR / 0.09) * clamp(n / (0.004 * SR) + 0.3));
}

// ---------- the room: a small plate (4 combs + 2 all-passes per side) on the send ----------
function reverb(x, offs) {
  const combs = [1694, 1760, 1622, 1547].map(d => ({ b: new Float32Array(d + offs), i: 0, lp: 0 }));
  const aps = [605, 480].map(d => ({ b: new Float32Array(d + offs), i: 0 }));
  const y = new Float32Array(N);
  for (let n = 0; n < N; n++) {
    let s = 0;
    for (const c of combs) { const o = c.b[c.i]; c.lp = o * 0.7 + c.lp * 0.3; c.b[c.i] = x[n] + c.lp * 0.8; c.i = (c.i + 1) % c.b.length; s += o; }
    s *= 0.25;
    for (const a of aps) { const o = a.b[a.i]; const v = -s + o; a.b[a.i] = s + o * 0.5; a.i = (a.i + 1) % a.b.length; s = v; }
    y[n] = s;
  }
  return y;
}
const WL = reverb(VL, 0), WR = reverb(VR, 23);
const OL = new Float32Array(N), OR = new Float32Array(N);
let peak = 0;
for (let i = 0; i < N; i++) {
  // the last half second fades, so the sting comes in clean
  const tail = 1 - smooth((i / SR - (DUR - 0.5)) / 0.5);
  OL[i] = (DL[i] + ML[i] * duck[i] + WL[i] * 0.5) * tail; OR[i] = (DR[i] + MR[i] * duck[i] + WR[i] * 0.5) * tail;
  // a silent film once came from one NaN; say where instead
  if (!Number.isFinite(OL[i]) || !Number.isFinite(OR[i])) throw new Error(`not a number at ${(i / SR).toFixed(3)} s`);
  peak = Math.max(peak, Math.abs(OL[i]), Math.abs(OR[i]));
}
const norm = 0.9 / peak;
const buf = Buffer.alloc(44 + N * 4);
buf.write('RIFF', 0); buf.writeUInt32LE(36 + N * 4, 4); buf.write('WAVE', 8);
buf.write('fmt ', 12); buf.writeUInt32LE(16, 16); buf.writeUInt16LE(1, 20); buf.writeUInt16LE(2, 22);
buf.writeUInt32LE(SR, 24); buf.writeUInt32LE(SR * 4, 28); buf.writeUInt16LE(4, 32); buf.writeUInt16LE(16, 34);
buf.write('data', 36); buf.writeUInt32LE(N * 4, 40);
for (let i = 0; i < N; i++) {
  // a soft clip glues it and keeps it loud
  buf.writeInt16LE(Math.round(Math.tanh(OL[i] * norm * 1.5) / Math.tanh(1.5) * 32767 * 0.95), 44 + i * 4);
  buf.writeInt16LE(Math.round(Math.tanh(OR[i] * norm * 1.5) / Math.tanh(1.5) * 32767 * 0.95), 46 + i * 4);
}
fs.writeFileSync(process.argv[2] || 'demo.wav', buf);
console.log('wrote', process.argv[2] || 'demo.wav');
