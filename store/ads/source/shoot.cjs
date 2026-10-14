// node store/ads/source/shoot.cjs [feed|status] [id …]
//   feed   → ../ydeck-<n>-<id>.png (1080x1350) and ../sheet.png
//   status → ../status/ydeck-status-<n>-<id>.png (1080x1920) and ../status/sheet.png (two rows)
const { chromium } = require('C:/Users/user/Desktop/YoniDev projects/al-hamacom-dev/node_modules/playwright');
const path = require('path'), fs = require('fs'), { pathToFileURL } = require('url'), { execFileSync } = require('child_process');
const ALL = ['tabs', 'tile', 'accounts', 'wrong', 'count', 'order', 'route', 'bookmarks', 'u2', 'incognito', 'omnibox', 'beforeafter', 'countdown', 'launch'];
const args = process.argv.slice(2);
const fmt = args[0] === 'status' ? 'status' : 'feed';
const only = args.filter(a => a !== 'status' && a !== 'feed');
const H = fmt === 'status' ? 1920 : 1350;
const dir = fmt === 'status' ? path.join(__dirname, '..', 'status') : path.join(__dirname, '..');
fs.mkdirSync(dir, { recursive: true });
(async () => {
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: 1080, height: H } });
  const files = [];
  for (const [i, id] of ALL.entries()) {
    const file = path.join(dir, `ydeck-${fmt === 'status' ? 'status-' : ''}${String(i + 1).padStart(2, '0')}-${id}.png`);
    files.push(file);
    if (only.length && !only.includes(id)) continue;
    await p.goto(pathToFileURL(path.join(__dirname, 'ads.html')).href + `?v=${id}&fmt=${fmt}`);
    await p.evaluate(() => window.ready);
    await p.screenshot({ path: file });
    console.log('wrote', path.basename(file));
  }
  await b.close();
  // contact sheet: two rows of seven
  const w = 270, h = Math.round(w * H / 1080);
  const inputs = files.flatMap(f => ['-i', f]);
  const tiles = files.map((_, i) => `[${i}]scale=${w}:${h},pad=${w + 12}:${h + 12}:6:6:#DDDDDD[s${i}]`).join(';');
  const row = (a, z, n) => files.slice(a, z).map((_, i) => `[s${a + i}]`).join('') + `hstack=${z - a}[${n}]`;
  execFileSync('ffmpeg', ['-y', '-loglevel', 'error', ...inputs, '-filter_complex',
    `${tiles};${row(0, 7, 'r1')};${row(7, 14, 'r2')};[r1][r2]vstack=2`, path.join(dir, 'sheet.png')]);
})();
