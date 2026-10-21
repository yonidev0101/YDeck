// The home page's cards: the part of each real screen a moment is about, cropped sharp from the 3× renders of
// desktop/src/test/.../RenderFilm.kt (`gradlew :desktop:test -Prender --tests "*RenderFilm"`) into site/src/cards/.
// Run it after the screens change: `node site/cards.cjs`. Needs ffmpeg.
const { execFileSync } = require('child_process'), path = require('path'), fs = require('fs');
const SRC = path.join(__dirname, '../desktop/build/film-renders'), OUT = path.join(__dirname, 'src/cards');
// [card, screen, x, y, width, height] in the Hebrew screen's pixels (3600×2340); the English one is mirrored.
const CARDS = [
  ['now-orbit', 'loops', 230, 345, 2520, 915],          // Orbit on Now: Claude waits for you, last time, what's open
  ['now-nova', 'loops', 230, 1310, 2520, 920],          // Nova behind it: at work, a server, a key missing
  ['receipt', 'orbit-session', 120, 845, 1440, 1150],   // the session's receipt and its diff
  ['memory', 'orbit', 130, 880, 2610, 980],             // the next step, last time, what's open in the project
  ['clear', 'loops-clear', 250, 430, 2490, 1090],       // nothing is open, and every project saved
];
// The crops are 3×; the site gets them at 2× (sharp on a retina screen at the size they're shown).
const SCALE = 2 / 3;
fs.mkdirSync(OUT, { recursive: true });
for (const [id, screen, x, y, w, h] of CARDS) for (const [lang, suffix] of [['he', ''], ['en', '-en']]) {
  const cx = lang === 'en' ? 3600 - x - w : x;
  const out = path.join(OUT, `${lang}-${id}.jpg`);
  execFileSync('ffmpeg', ['-y', '-loglevel', 'error', '-i', path.join(SRC, `app-${screen}${suffix}.png`),
    '-vf', `crop=${w}:${h}:${cx}:${y},scale=${Math.round(w * SCALE)}:-2:flags=lanczos`, '-q:v', '3', out]);
  console.log('wrote', path.relative(process.cwd(), out));
}
