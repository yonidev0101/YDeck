// Saves the ads in projects.html to out/ydeck-projects-<id>[-en][-story].png (1080×1350 for groups, 1080×1920 for status).
//   node store/ads/projects/render.cjs
const { chromium } = require(process.env.PLAYWRIGHT || 'C:/Users/user/Desktop/YoniDev projects/al-hamacom-dev/node_modules/playwright');
const fs = require('fs'), path = require('path'), { pathToFileURL } = require('url');
(async () => {
  const out = path.join(__dirname, 'out'); fs.mkdirSync(out, { recursive: true });
  const b = await chromium.launch(); const p = await b.newPage({ viewport: { width: 1200, height: 900 } });
  const errs = []; p.on('pageerror', e => errs.push(e.message));
  for (const lang of ['he', 'en']) for (const fmt of ['feed', 'story']) {
    const qs = new URLSearchParams({ ...(lang === 'en' && { lang }), ...(fmt === 'story' && { fmt }) }).toString();
    await p.goto(pathToFileURL(path.join(__dirname, 'projects.html')).href + (qs ? '?' + qs : ''));
    await p.evaluate(() => window.ready);
    const shots = await p.evaluate(() => [...document.querySelectorAll('canvas')].map(c => [c.id, c.toDataURL('image/png')]));
    for (const [id, url] of shots) fs.writeFileSync(path.join(out, `ydeck-projects-${id}${lang === 'en' ? '-en' : ''}${fmt === 'story' ? '-story' : ''}.png`), Buffer.from(url.split(',')[1], 'base64'));
  }
  console.log(fs.readdirSync(out).join(' '), errs.length ? errs : 'ok');
  await b.close();
})();
