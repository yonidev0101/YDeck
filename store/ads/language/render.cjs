// Saves every status image in language.html to out/<id>.png (1080×1920).   node store/ads/language/render.cjs
const { chromium } = require('C:/Users/user/Desktop/YoniDev projects/al-hamacom-dev/node_modules/playwright');
const fs = require('fs'), path = require('path');
(async () => {
  const out = path.join(__dirname, 'out'); fs.mkdirSync(out, { recursive: true });
  const b = await chromium.launch(); const p = await b.newPage({ viewport: { width: 1200, height: 900 } });
  const errs = []; p.on('pageerror', e => errs.push(e.message));
  await p.goto('file:///' + path.join(__dirname, 'language.html').split(path.sep).join('/'));
  await p.evaluate(() => window.ready);
  const shots = await p.evaluate(() => [...document.querySelectorAll('canvas')].map(c => [c.id, c.toDataURL('image/png')]));
  for (const [id, url] of shots) fs.writeFileSync(path.join(out, `ydeck-lang-${id}.png`), Buffer.from(url.split(',')[1], 'base64'));
  console.log(shots.map(s => s[0]).join(' '), errs.length ? errs : 'ok');
  await b.close();
})();
