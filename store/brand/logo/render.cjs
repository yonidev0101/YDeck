// Films sting.html frame by frame and adds its sound: node render.cjs [en|he]
// Writes out/ydeck-logo-sting{,-landscape,-square}[-he].mp4 (60 fps, 3.6 s), the .wav/.mp3, and a still of the end.
const { chromium } = require('C:/Users/user/Desktop/YoniDev projects/al-hamacom-dev/node_modules/playwright');
const { spawn, execFileSync } = require('child_process');
const path = require('path'), fs = require('fs'), { pathToFileURL } = require('url');
const OUT = path.join(__dirname, 'out');
const FPS = 60, DUR = 3.6;
const lang = process.argv[2] === 'he' ? 'he' : 'en';
const SIZES = [['', 1080, 1920], ['-landscape', 1920, 1080], ['-square', 1080, 1080]];

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const wav = path.join(OUT, 'ydeck-logo-sting.wav');
  execFileSync('node', [path.join(__dirname, 'sound.cjs'), wav], { stdio: 'inherit' });
  execFileSync('ffmpeg', ['-y', '-loglevel', 'error', '-i', wav, '-b:a', '192k', path.join(OUT, 'ydeck-logo-sting.mp3')]);
  const b = await chromium.launch();
  for (const [suffix, w, h] of SIZES) {
    const p = await b.newPage({ viewport: { width: w, height: h } });
    await p.goto(pathToFileURL(path.join(__dirname, 'sting.html')).href + `?w=${w}&h=${h}&lang=${lang}`);
    await p.evaluate(() => window.ready);
    const tag = lang === 'he' ? '-he' : '';
    const file = path.join(OUT, `ydeck-logo-sting${suffix}${tag}.mp4`);
    const ff = spawn('ffmpeg', ['-y', '-f', 'image2pipe', '-framerate', String(FPS), '-i', '-', '-i', wav,
      '-c:v', 'libx264', '-pix_fmt', 'yuv420p', '-crf', '16', '-preset', 'slow', '-c:a', 'aac', '-b:a', '192k',
      '-shortest', '-movflags', '+faststart', file], { stdio: ['pipe', 'ignore', 'inherit'] });
    for (let f = 0; f < Math.round(FPS * DUR); f++) {
      await p.evaluate((t) => window.render(t), f / FPS);
      const buf = await p.screenshot({ type: 'png' });
      if (!ff.stdin.write(buf)) await new Promise(r => ff.stdin.once('drain', r));
    }
    ff.stdin.end(); await new Promise(r => ff.on('close', r));
    // stills: thinking, and the end frame
    if (!suffix) {
      await p.evaluate(() => window.render(0.9)); await p.screenshot({ path: path.join(OUT, `sting-thinking${tag}.png`) });
      await p.evaluate(() => window.render(3.5)); await p.screenshot({ path: path.join(OUT, `sting-end${tag}.png`) });
    }
    await p.close();
    console.log('wrote', file);
  }
  await b.close();
})();
