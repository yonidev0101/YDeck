// node render.cjs [film|trick|vanish|…|demo] → ../YDeck-problems-status.mp4, …, ../YDeck-demo.mp4: the film + the logo sting (3.6 s), 1080x1920
// (the Windows film and the demo 1920x1080), 30 fps. PLAYWRIGHT points at another install of it.
// STILLS=1 only writes a few frames to ./frames for checking. LANG_CUT=en renders the English cut where a film has one (journey, loops, night).
const { chromium } = require(process.env.PLAYWRIGHT || 'C:/Users/user/Desktop/YoniDev projects/al-hamacom-dev/node_modules/.pnpm/playwright@1.57.0/node_modules/playwright');
const { spawn, execFileSync } = require('child_process');
const path = require('path'), fs = require('fs'), { pathToFileURL } = require('url');
const FPS = 30;
const NAME = ['trick', 'vanish', 'keys', 'explainer', 'desktop', 'close', 'journey', 'loops', 'night', 'demo'].includes(process.argv[2]) ? process.argv[2] : 'film';
const OUTNAME = { film: 'YDeck-problems-status.mp4', trick: 'YDeck-trick-status.mp4', vanish: 'YDeck-vanish-status.mp4', keys: 'YDeck-keys-status.mp4', explainer: 'YDeck-explainer-status.mp4', desktop: 'YDeck-Windows-launch.mp4', close: 'YDeck-close-status.mp4', journey: 'YDeck-journey-status.mp4', loops: 'YDeck-loops-status.mp4', night: 'YDeck-opening-status.mp4', demo: 'YDeck-demo.mp4' }[NAME];
const SOUND = { film: 'sound.cjs', trick: 'sound-trick.cjs', vanish: 'sound-vanish.cjs', keys: 'sound-keys.cjs', explainer: 'sound-explainer.cjs', desktop: 'sound-desktop.cjs', close: 'sound-close.cjs', journey: 'sound-journey.cjs', loops: 'sound-loops.cjs', night: 'sound-night.cjs', demo: 'sound-demo.cjs' }[NAME];
const OUT = path.join(__dirname, '..');
// The Windows film and the demo are landscape (1920x1080), with the landscape sting.
const WIDE = NAME === 'desktop' || NAME === 'demo';
// The films in the brand's line end on the current sting, "thinking" (store/brand/logo), in Hebrew.
const STING = NAME === 'close' ? path.join(__dirname, '../../brand/logo/out/ydeck-logo-sting-he.mp4')
  : NAME === 'demo' ? path.join(__dirname, '../../brand/logo/out/ydeck-logo-sting-landscape-he.mp4')
  : path.join(__dirname, WIDE ? '../../brand/sound/ydeck-sting-landscape.mp4' : '../../brand/sound/ydeck-sting.mp4');
const TMP = path.join(__dirname, 'tmp');
fs.mkdirSync(TMP, { recursive: true });

(async () => {
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: WIDE ? { width: 1920, height: 1080 } : { width: 1080, height: 1920 } });
  await p.goto(pathToFileURL(path.join(__dirname, NAME + '.html')).href + (process.env.LANG_CUT === 'en' ? '?lang=en' : ''));
  await p.evaluate(() => window.ready);
  const DUR = await p.evaluate(() => window.DURATION);
  if (process.env.STILLS) {
    fs.mkdirSync(path.join(__dirname, 'frames'), { recursive: true });
    for (const t of process.env.STILLS.split(',').map(Number)) {
      await p.evaluate(t => window.render(t), t);
      await p.screenshot({ path: path.join(__dirname, 'frames', `t${t}.png`) });
    }
    await b.close(); return;
  }
  const wav = path.join(TMP, 'film.wav');
  execFileSync('node', [path.join(__dirname, SOUND), wav], { stdio: 'inherit' });
  const film = path.join(TMP, 'film.mp4');
  const ff = spawn('ffmpeg', ['-y', '-loglevel', 'error', '-f', 'image2pipe', '-framerate', String(FPS), '-i', '-', '-i', wav,
    '-c:v', 'libx264', '-pix_fmt', 'yuv420p', '-crf', '16', '-preset', 'slow', '-c:a', 'aac', '-b:a', '192k', '-ar', '48000', '-shortest', film],
    { stdio: ['pipe', 'ignore', 'inherit'] });
  for (let f = 0; f < Math.round(FPS * DUR); f++) {
    await p.evaluate(([t, f]) => window.render(t, f), [f / FPS, f]);
    const buf = await p.screenshot({ type: 'png' });
    if (!ff.stdin.write(buf)) await new Promise(r => ff.stdin.once('drain', r));
  }
  ff.stdin.end(); await new Promise(r => ff.on('close', r));
  await b.close();
  const out = path.join(OUT, process.env.LANG_CUT === 'en' ? OUTNAME.replace('.mp4', '-en.mp4') : OUTNAME);
  // these end on the mark itself: no sting after them
  if (['journey', 'loops', 'night'].includes(NAME)) { execFileSync('ffmpeg', ['-y', '-loglevel', 'error', '-i', film, '-c', 'copy', '-movflags', '+faststart', out]); console.log('wrote', out); return; }
  execFileSync('ffmpeg', ['-y', '-loglevel', 'error', '-i', film, '-i', STING, '-filter_complex',
    `[1:v]fps=${FPS},setsar=1[s];[0:v]setsar=1[f];[1:a]aresample=48000[sa];[f][0:a][s][sa]concat=n=2:v=1:a=1[v][a]`, '-map', '[v]', '-map', '[a]',
    '-c:v', 'libx264', '-pix_fmt', 'yuv420p', '-crf', '17', '-preset', 'slow', '-c:a', 'aac', '-b:a', '192k', '-movflags', '+faststart', out]);
  console.log('wrote', out);
})();
