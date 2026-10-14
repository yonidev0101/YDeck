// The YDeck mark, direction A ("the loop that closes"): a ring with an opening, and an amber dot where it is still
// open. Writes the SVG sources, the PNGs and the Windows .ico, then copies what the desktop app uses into place.
//   node store/brand/logo/make.cjs
const { chromium } = require('C:/Users/user/Desktop/YoniDev projects/al-hamacom-dev/node_modules/playwright');
const fs = require('fs'), path = require('path');

const here = __dirname;
const root = path.resolve(here, '../../..');
const out = path.join(here, 'out');
fs.mkdirSync(out, { recursive: true });

const INK = '#0B0B0D', PAPER = '#F4F4F5', AMBER = '#F5A524', INDIGO = '#3D35E0', GREEN = '#34C77B';

// Two drawings of the same mark: the full one, and a heavier one that stays clear at 32 px and below.
function ring(color, dot, small) {
  return small
    ? `<circle cx="50" cy="50" r="34" fill="none" stroke="${color}" stroke-width="16" stroke-linecap="round" stroke-dasharray="170.9 42.7" transform="rotate(-4 50 50)"/><circle cx="76" cy="28.1" r="8.5" fill="${dot}"/>`
    : `<circle cx="50" cy="50" r="36" fill="none" stroke="${color}" stroke-width="11" stroke-linecap="round" stroke-dasharray="188.5 37.7" transform="rotate(-10 50 50)"/><circle cx="77.6" cy="26.9" r="7.5" fill="${dot}"/>`;
}
const svg = (body, view = '0 0 100 100') => `<svg xmlns="http://www.w3.org/2000/svg" viewBox="${view}">${body}</svg>\n`;
// The app icon: the mark on a dark tile.
const tile = (small) => `<rect width="100" height="100" rx="${small ? 20 : 22}" fill="${INK}"/><g transform="translate(${small ? 14 : 19} ${small ? 14 : 19}) scale(${small ? 0.72 : 0.62})">${ring(PAPER, AMBER, small)}</g>`;
// The ring closed: what a closed loop looks like (motion's last frame).
const closed = `<circle cx="50" cy="50" r="36" fill="none" stroke="${GREEN}" stroke-width="11"/>`;

const files = {
  'ydeck-mark-ink.svg': svg(ring(INK, AMBER)),
  'ydeck-mark-white.svg': svg(ring(PAPER, AMBER)),
  'ydeck-mark-small-ink.svg': svg(ring(INK, AMBER, true)),
  'ydeck-mark-small-white.svg': svg(ring(PAPER, AMBER, true)),
  'ydeck-mark-closed.svg': svg(closed),
  'ydeck-icon.svg': svg(tile(false)),
  'ydeck-icon-small.svg': svg(tile(true)),
  'ydeck-icon-indigo.svg': svg(`<rect width="100" height="100" rx="22" fill="${INDIGO}"/><g transform="translate(19 19) scale(0.62)">${ring('#FFFFFF', AMBER)}</g>`),
};
for (const [name, text] of Object.entries(files)) fs.writeFileSync(path.join(out, name), text);

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage();
  async function png(svgText, size) {
    await page.setViewportSize({ width: size, height: size });
    await page.setContent(`<html><body style="margin:0;background:transparent">${svgText.replace('<svg ', `<svg width="${size}" height="${size}" `)}</body></html>`);
    return page.screenshot({ omitBackground: true, clip: { x: 0, y: 0, width: size, height: size } });
  }
  const big = await png(files['ydeck-icon.svg'], 1024);
  fs.writeFileSync(path.join(out, 'ydeck-icon-1024.png'), big);
  fs.writeFileSync(path.join(out, 'ydeck-icon-512.png'), await png(files['ydeck-icon.svg'], 512));
  fs.writeFileSync(path.join(out, 'ydeck-mark-white-512.png'), await png(files['ydeck-mark-white.svg'], 512));

  // The .ico: each size drawn on its own, the heavy mark at 32 px and below.
  const sizes = [16, 20, 24, 32, 40, 48, 64, 128, 256];
  const images = [];
  for (const s of sizes) images.push(await png(files[s <= 32 ? 'ydeck-icon-small.svg' : 'ydeck-icon.svg'], s));
  await browser.close();
  const header = Buffer.alloc(6 + 16 * sizes.length);
  header.writeUInt16LE(0, 0); header.writeUInt16LE(1, 2); header.writeUInt16LE(sizes.length, 4);
  let offset = header.length;
  sizes.forEach((s, i) => {
    const e = 6 + 16 * i;
    header.writeUInt8(s >= 256 ? 0 : s, e); header.writeUInt8(s >= 256 ? 0 : s, e + 1);
    header.writeUInt8(0, e + 2); header.writeUInt8(0, e + 3);
    header.writeUInt16LE(1, e + 4); header.writeUInt16LE(32, e + 6);
    header.writeUInt32LE(images[i].length, e + 8); header.writeUInt32LE(offset, e + 12);
    offset += images[i].length;
  });
  const ico = Buffer.concat([header, ...images]);
  fs.writeFileSync(path.join(out, 'ydeck.ico'), ico);

  // Into the desktop app: its icon (window, taskbar, tray, installer) and the marks it draws.
  fs.copyFileSync(path.join(out, 'ydeck.ico'), path.join(root, 'desktop/icons/ydeck.ico'));
  fs.copyFileSync(path.join(out, 'ydeck-icon-512.png'), path.join(root, 'desktop/icons/ydeck.png'));
  const res = path.join(root, 'desktop/src/main/resources/brand');
  for (const n of ['ydeck-mark-ink.svg', 'ydeck-mark-white.svg', 'ydeck-mark-small-ink.svg', 'ydeck-mark-small-white.svg', 'ydeck-icon.svg'])
    fs.copyFileSync(path.join(out, n), path.join(res, n));
  console.log('ok', Object.keys(files).length, 'svg,', sizes.length, 'icon sizes');
})();
