// Films sting.html frame by frame and muxes the sound: node render.cjs [indigo|night]
// Writes ../sound/ydeck-sting.wav/.mp3 and ../sound/ydeck-sting{,-landscape,-square}[-night].mp4 (30 fps, 3.6 s).
const { chromium } = require('C:/Users/user/Desktop/YoniDev projects/al-hamacom-dev/node_modules/playwright');
const { spawn, execFileSync } = require('child_process');
const path = require('path'), fs = require('fs'), { pathToFileURL } = require('url');
const OUT = path.join(__dirname, '..', 'sound');
const FPS = 30, DUR = 3.6;
const ground = process.argv[2] === 'night' ? 'night' : 'indigo';
const SIZES = [['', 1080, 1920], ['-landscape', 1920, 1080], ['-square', 1080, 1080]];

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const wav = path.join(OUT, 'ydeck-sting.wav');
  execFileSync('node', [path.join(__dirname, 'sound.cjs'), wav], { stdio: 'inherit' });
  execFileSync('ffmpeg', ['-y', '-loglevel', 'error', '-i', wav, '-b:a', '192k', path.join(OUT, 'ydeck-sting.mp3')]);
  const b = await chromium.launch();
  for (const [suffix, w, h] of SIZES) {
    const p = await b.newPage({ viewport: { width: w, height: h } });
    await p.goto(pathToFileURL(path.join(__dirname, 'sting.html')).href + `?w=${w}&h=${h}&ground=${ground}`);
    await p.evaluate(() => window.ready);
    const file = path.join(OUT, `ydeck-sting${suffix}${ground === 'night' ? '-night' : ''}.mp4`);
    const ff = spawn('ffmpeg', ['-y', '-f', 'image2pipe', '-framerate', String(FPS), '-i', '-', '-i', wav,
      '-c:v', 'libx264', '-pix_fmt', 'yuv420p', '-crf', '16', '-preset', 'slow', '-c:a', 'aac', '-b:a', '192k',
      '-shortest', '-movflags', '+faststart', file], { stdio: ['pipe', 'ignore', 'inherit'] });
    for (let f = 0; f < Math.round(FPS * DUR); f++) {
      await p.evaluate(([t, f]) => window.render(t, f), [f / FPS, f]);
      const buf = await p.screenshot({ type: 'png' });
      if (!ff.stdin.write(buf)) await new Promise(r => ff.stdin.once('drain', r));
    }
    ff.stdin.end(); await new Promise(r => ff.on('close', r));
    if (!suffix) { await p.evaluate(() => window.render(3.3, 99)); await p.screenshot({ path: path.join(OUT, `logo-sting${ground === 'night' ? '-night' : ''}.png`) }); }
    await p.close();
    console.log('wrote', file);
  }
  await b.close();
})();
