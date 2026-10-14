// The sound of the logo sting, "thinking", on sting.html's times. Usage: node sound.cjs out.wav
// A soft hum that flutters with the spinning arc and slows with it → a light tone as it turns white → it stops, the
// dot lands: a soft pop and the brand's chord, ringing out.
const fs = require('fs');
const SR = 48000, DUR = 3.6, N = Math.round(SR * DUR);
const L = new Float32Array(N), R = new Float32Array(N);
const TAU = Math.PI * 2;
const clamp = (v, a = 0, b = 1) => Math.max(a, Math.min(b, v));
const smooth = v => { v = clamp(v); return v * v * (3 - 2 * v); };
let seed = 7; const rnd = () => ((seed = (seed * 1664525 + 1013904223) >>> 0) / 4294967296) * 2 - 1;
const add = (i, v, pan = 0) => { if (i >= 0 && i < N) { L[i] += v * (1 - pan); R[i] += v * (1 + pan); } };

// air: filtered noise whose brightness follows the ring as it draws, moving across the stereo field
function air(at, len, amp) {
  let lp = 0;
  for (let i = Math.floor(at * SR); i < (at + len) * SR; i++) {
    const k = (i / SR - at) / len, env = Math.sin(Math.PI * Math.min(1, k * 1.3)) ** 2 * (1 - 0.6 * k);
    lp += (rnd() - lp) * (0.02 + 0.18 * smooth(k));
    add(i, amp * env * lp, -0.6 + 1.2 * smooth(k));
  }
}
function glide(at, len, f0, f1, amp, pan = 0) {
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
    add(i, amp * clamp(k * len / 0.004) * Math.exp(-k * 5) * Math.sin(ph));
  }
}
// a soft click: a very short damped tone with a breath of noise, like a good switch
function click(at, amp) {
  for (let i = Math.floor(at * SR); i < (at + 0.04) * SR; i++) {
    const t = i / SR - at, env = clamp(t / 0.0008) * Math.exp(-t * 160);
    add(i, amp * env * (0.7 * Math.sin(TAU * 2100 * t) + 0.3 * rnd()));
  }
}
// a soft bell: the note, a quiet third partial that fades fast, an octave below
function note(at, f, amp, len, pan = 0, decay = 2.4) {
  for (let i = Math.floor(at * SR); i < (at + len) * SR; i++) {
    const t = i / SR - at, env = clamp(t / 0.01) * Math.exp(-t * decay);
    add(i, amp * env * (Math.sin(TAU * f * t) + 0.16 * Math.sin(TAU * f * 3 * t) * Math.exp(-t * 9) + 0.4 * Math.sin(TAU * f / 2 * t)), pan);
  }
}

// 0.00 thinking: a soft hum whose flutter follows the arc's turns, fast and then slower, pitch easing down
{
  let ph = 0, turn = 0, lp = 0;
  for (let i = 0; i < 2.1 * SR; i++) {
    const t = i / SR, sp = 1 - Math.pow(1 - clamp((t - 0.15) / 1.75), 3);
    const speed = 3 * 3 * Math.pow(1 - clamp((t - 0.15) / 1.75), 2) / 1.75 + 0.2;   // turns per second, as the arc
    turn += speed / SR;
    const flutter = 0.55 + 0.45 * Math.cos(Math.PI * 2 * turn);
    const f = 300 - 90 * sp;
    ph += TAU * f / SR;
    lp += (rnd() - lp) * 0.03;
    const env = clamp(t / 0.12) * (1 - clamp((t - 1.6) / 0.45));
    add(i, env * (0.06 * Math.sin(ph) * flutter + 0.09 * lp * flutter), 0.35 * Math.sin(Math.PI * 2 * turn));
  }
}
// 1.25 it turns white: a light tone rises into place
glide(1.2, 0.65, 392, 523.3, 0.06);

// 1.85 it stops, the dot lands: a soft pop, then the brand chord (C major, spread), ringing out
pop(1.86, 900, 220, 0.2, 0.12);
click(1.85, 0.16);
note(1.88, 523.3, 0.12, 1.7, -0.15);
note(1.90, 659.3, 0.085, 1.68, 0.2);
note(1.92, 784.0, 0.075, 1.66, -0.25);
note(1.96, 1318.5, 0.05, 1.2, 0.35, 4);

// 2.35 the name: a quiet low C under it all
note(2.35, 130.8, 0.08, 1.2, 0, 1.6);

// a little room: two short echoes, quieter and wider
const dry = [L.slice(), R.slice()];
for (const [d, g] of [[0.083, 0.16], [0.167, 0.08]]) {
  const s = Math.round(d * SR);
  for (let i = s; i < N; i++) { L[i] += g * dry[1][i - s]; R[i] += g * dry[0][i - s]; }
}
// fade the very end, normalise to -1 dB
for (let i = Math.round((DUR - 0.25) * SR); i < N; i++) { const k = (N - i) / (0.25 * SR); L[i] *= k; R[i] *= k; }
let peak = 0; for (let i = 0; i < N; i++) peak = Math.max(peak, Math.abs(L[i]), Math.abs(R[i]));
const gain = 0.89 / (peak || 1);

const buf = Buffer.alloc(44 + N * 4);
buf.write('RIFF', 0); buf.writeUInt32LE(36 + N * 4, 4); buf.write('WAVE', 8); buf.write('fmt ', 12);
buf.writeUInt32LE(16, 16); buf.writeUInt16LE(1, 20); buf.writeUInt16LE(2, 22); buf.writeUInt32LE(SR, 24);
buf.writeUInt32LE(SR * 4, 28); buf.writeUInt16LE(4, 32); buf.writeUInt16LE(16, 34); buf.write('data', 36); buf.writeUInt32LE(N * 4, 40);
for (let i = 0; i < N; i++) {
  buf.writeInt16LE(Math.round(clamp(L[i] * gain, -1, 1) * 32767), 44 + i * 4);
  buf.writeInt16LE(Math.round(clamp(R[i] * gain, -1, 1) * 32767), 46 + i * 4);
}
fs.writeFileSync(process.argv[2] || 'ydeck-logo-sting.wav', buf);
