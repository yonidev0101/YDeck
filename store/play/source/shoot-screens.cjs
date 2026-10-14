// node store/play/source/shoot-screens.cjs → ../screenshots/YDeck-01.png … 08 (1080x1920, Google Play phone screenshots)
const { chromium } = require('C:/Users/user/Desktop/YoniDev projects/al-hamacom-dev/node_modules/playwright');
const path = require('path'), fs = require('fs'), { pathToFileURL } = require('url');
(async () => {
  const out = path.join(__dirname, '..', 'screenshots');
  fs.mkdirSync(out, { recursive: true });
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: 1080, height: 1920 } });
  const page = pathToFileURL(path.join(__dirname, 'screenshot.html')).href;
  for (let n = 1; n <= 8; n++) {
    await p.goto(page + '?n=' + n);
    await p.evaluate(() => window.ready);
    await p.screenshot({ path: path.join(out, `YDeck-${String(n).padStart(2, '0')}.png`) });
  }
  await b.close();
  console.log('done', out);
})();
