// Turns the app's own vector drawables into window.ICONS for the store pages, so the marks are the app's exact ones.
// node icons.cjs → icons.js
const fs = require('fs'), path = require('path');
const DR = path.join(__dirname, '../../../app/src/main/res/drawable');
const names = ['si_googleplay', 'si_firebase', 'si_github', 'si_vercel', 'si_googleanalytics', 'si_gmail', 'si_sentry', 'si_supabase', 'si_stripe', 'ic_s_dots', 'ic_chev_left', 'ic_chev_right'];
const out = {};
for (const n of names) {
  const xml = fs.readFileSync(path.join(DR, n + '.xml'), 'utf8');
  const vb = [xml.match(/viewportWidth="([\d.]+)"/)[1], xml.match(/viewportHeight="([\d.]+)"/)[1]];
  const paths = [...xml.matchAll(/<path([^>]*)\/?>/g)].map(m => {
    const a = m[1], d = (a.match(/pathData="([^"]+)"/) || [])[1];
    const stroke = (a.match(/strokeColor="([^"]+)"/) || [])[1], sw = (a.match(/strokeWidth="([^"]+)"/) || [])[1];
    return { d, stroke: !!stroke, sw };
  });
  out[n] = { vb, paths };
}
fs.writeFileSync(path.join(__dirname, 'icons.js'), 'window.ICONS=' + JSON.stringify(out) + ';\n');
console.log('icons.js', Object.keys(out).length);
