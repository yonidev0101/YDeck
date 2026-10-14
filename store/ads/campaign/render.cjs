// Saves the status images in campaign.html to out/ydeck-status-<id>.png and out/ydeck-status-<id>-en.png (1080×1920).
//   node store/ads/campaign/render.cjs
const { chromium } = require('C:/Users/user/Desktop/YoniDev projects/al-hamacom-dev/node_modules/playwright');
const fs = require('fs'), path = require('path'), { pathToFileURL } = require('url');
(async () => {
  const out = path.join(__dirname, 'out'); fs.mkdirSync(out, { recursive: true });
  const b = await chromium.launch(); const p = await b.newPage({ viewport: { width: 1200, height: 900 } });
  const errs = []; p.on('pageerror', e => errs.push(e.message));
  for (const lang of ['he', 'en']) {
    await p.goto(pathToFileURL(path.join(__dirname, 'campaign.html')).href + (lang === 'en' ? '?lang=en' : ''));
    await p.evaluate(() => window.ready);
    const shots = await p.evaluate(() => [...document.querySelectorAll('canvas')].map(c => [c.id, c.toDataURL('image/png')]));
    for (const [id, url] of shots) fs.writeFileSync(path.join(out, `ydeck-status-${id}${lang === 'en' ? '-en' : ''}.png`), Buffer.from(url.split(',')[1], 'base64'));
  }
  console.log(fs.readdirSync(out).join(' '), errs.length ? errs : 'ok');
  await b.close();
})();
