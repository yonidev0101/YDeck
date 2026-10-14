// The installer's two images (WiX UI): the side panel of the first and last screens (493×312) and the banner over the
// others (493×58). The words sit on the light part (WiX draws them black), the mark on the night panel.
//   node store/brand/installer/make.cjs   → desktop/installer/dialog.bmp, banner.bmp
const { chromium } = require('C:/Users/user/Desktop/YoniDev projects/al-hamacom-dev/node_modules/playwright');
const { execFileSync } = require('child_process');
const fs = require('fs'), path = require('path');

const root = path.resolve(__dirname, '../../..');
const out = path.join(root, 'desktop/installer');
const tmp = path.join(__dirname, 'out');
fs.mkdirSync(out, { recursive: true }); fs.mkdirSync(tmp, { recursive: true });
// The font goes in the page itself: a page set from a string can't load a local file.
const font = 'data:font/ttf;base64,' + fs.readFileSync(path.join(root, 'app/src/main/res/font/plex_sans_hebrew_medium.ttf')).toString('base64');

const ring = (color, size) => `<svg width="${size}" height="${size}" viewBox="0 0 100 100"><circle cx="50" cy="50" r="36" fill="none" stroke="${color}" stroke-width="11" stroke-linecap="round" stroke-dasharray="188.5 37.7" transform="rotate(-10 50 50)"/><circle cx="77.6" cy="26.9" r="7.5" fill="#F5A524"/></svg>`;
const page = body => `<html><head><style>@font-face{font-family:Plex;src:url(${font})}html,body{margin:0}body{font-family:Plex,sans-serif}</style></head><body>${body}</body></html>`;

// The first and last screens: a night panel with the mark on the left third, the words on white to its right.
const dialog = page(`<div style="width:493px;height:312px;display:flex;background:#FFFFFF">
  <div style="width:164px;height:312px;background:#0B0B0D;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:14px">
    ${ring('#F4F4F5', 62)}
    <div style="color:#F4F4F5;font-size:17px;letter-spacing:-0.2px">YDeck</div>
  </div>
  <div style="flex:1;background:#FFFFFF"></div>
</div>`);
// The banner: white for WiX's title and line, the mark at its end.
const banner = page(`<div style="width:493px;height:58px;background:#FFFFFF;position:relative;border-bottom:1px solid #E7E7EA;box-sizing:border-box">
  <div style="position:absolute;right:16px;top:13px">${ring('#0B0B0D', 32)}</div>
</div>`);

(async () => {
  const b = await chromium.launch();
  const p = await b.newPage({ deviceScaleFactor: 1 });
  for (const [name, html, w, h] of [['dialog', dialog, 493, 312], ['banner', banner, 493, 58]]) {
    await p.setViewportSize({ width: w, height: h });
    await p.setContent(html);
    await p.evaluate(() => document.fonts.ready);
    const png = path.join(tmp, name + '.png');
    await p.screenshot({ path: png, clip: { x: 0, y: 0, width: w, height: h } });
    // WiX takes 24-bit BMPs
    execFileSync('ffmpeg', ['-y', '-loglevel', 'error', '-i', png, '-pix_fmt', 'bgr24', path.join(out, name + '.bmp')]);
  }
  await b.close();
  console.log('ok', fs.statSync(path.join(out, 'dialog.bmp')).size, fs.statSync(path.join(out, 'banner.bmp')).size);
})();
