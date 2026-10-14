// node store/play/source/shoot.cjs → ../feature-graphic-he.png, ../feature-graphic-en.png (1024x500), ../icon-512.png
const { chromium } = require('C:/Users/user/Desktop/YoniDev projects/al-hamacom-dev/node_modules/playwright');
const path = require('path'), { pathToFileURL } = require('url');
require('./icons.cjs');
(async () => {
  const b = await chromium.launch();
  const page = pathToFileURL(path.join(__dirname, 'feature.html')).href;
  const p = await b.newPage({ viewport: { width: 1024, height: 500 } });
  for (const lang of ['he', 'en']) {
    await p.goto(page + '?lang=' + lang);
    await p.evaluate(() => window.ready);
    await p.screenshot({ path: path.join(__dirname, '..', `feature-graphic-${lang}.png`) });
  }
  const i = await b.newPage({ viewport: { width: 512, height: 512 } });
  await i.goto(page + '?icon=1');
  await i.evaluate(() => window.ready);
  await i.screenshot({ path: path.join(__dirname, '..', 'icon-512.png') });
  await b.close();
  console.log('done');
})();
