// YDeck's usage counts: how many install it, come back, and which parts they use. Anonymous by construction.
//
// What arrives (and nothing else is kept; the IP address, the user agent and the time of day are never stored):
//   POST /v1/day    {id, day, v, lang, projects, claude, features}   an install's day: one row per install per day,
//                   its features OR-ed together as the day goes on. id is a random 32-hex string made on the PC.
//   POST /v1/event  {id, e: "install" | "uninstall", v}               once each, from the app
//   POST /v1/site   {e: "view" | "download", page, ref}               the website: a page seen, a download clicked;
//                   ref is the referring site's host only (whatsapp.com, reddit.com), never a full address
//   GET  /v1/report?key=…   the counts, as JSON;   GET /?key=…   the same, as a page
//
// One Durable Object holds it all in SQLite: the volume is small (a row per install per day).

const ID = /^[0-9a-f]{32}$/;
const DAY = /^\d{4}-\d{2}-\d{2}$/;
const VERSION = /^[0-9][0-9a-z.\-]{0,15}$/;
const FEATURES = ['now', 'receipt', 'commit', 'push', 'closeday', 'fill', 'widget', 'strip', 'search', 'phone'];
const PAGES = /^\/[a-z0-9/\-]{0,40}$/;
const HOST = /^[a-z0-9.\-]{1,60}$/;

export default {
  async fetch(req, env) {
    const url = new URL(req.url);
    if (req.method === 'OPTIONS') return cors(new Response(null, { status: 204 }));
    const stub = env.STATS.get(env.STATS.idFromName('all'));
    if (req.method === 'POST' && ['/v1/day', '/v1/event', '/v1/site'].includes(url.pathname)) {
      const body = await req.text();
      if (body.length > 2000) return cors(text('too big', 413));
      let o;
      try { o = JSON.parse(body); } catch { return cors(text('bad json', 400)); }
      const clean = check(url.pathname, o);
      if (!clean) return cors(text('bad', 400));
      await stub.fetch('https://stats/put', { method: 'POST', body: JSON.stringify({ kind: url.pathname, ...clean }) });
      return cors(new Response(null, { status: 204 }));
    }
    if (req.method === 'GET' && (url.pathname === '/v1/report' || url.pathname === '/')) {
      if (!env.REPORT_KEY || url.searchParams.get('key') !== env.REPORT_KEY) return text('YDeck stats. Counts only.', url.pathname === '/' ? 200 : 401);
      const r = await (await stub.fetch('https://stats/report')).json();
      return url.pathname === '/' ? html(page(r)) : json(r);
    }
    return text('not found', 404);
  },
};

/** Only the known fields, in their known shapes; anything else is dropped. */
function check(path, o) {
  if (!o || typeof o !== 'object') return null;
  const today = new Date().toISOString().slice(0, 10);
  if (path === '/v1/day') {
    if (!ID.test(o.id) || !DAY.test(o.day) || o.day > today || !VERSION.test(o.v || '')) return null;
    const features = Array.isArray(o.features) ? o.features.filter(f => FEATURES.includes(f)) : [];
    return {
      id: o.id, day: o.day, v: o.v, lang: o.lang === 'he' ? 'he' : 'en',
      projects: ['0', '1', '2-5', '6-15', '16+'].includes(o.projects) ? o.projects : '0',
      claude: o.claude ? 1 : 0, features: FEATURES.reduce((m, f, i) => features.includes(f) ? m | (1 << i) : m, 0),
    };
  }
  if (path === '/v1/event') {
    if (!ID.test(o.id) || !['install', 'uninstall'].includes(o.e) || !VERSION.test(o.v || '')) return null;
    return { id: o.id, e: o.e, v: o.v, day: today };
  }
  if (!['view', 'download'].includes(o.e)) return null;
  const ref = typeof o.ref === 'string' && HOST.test(o.ref) ? o.ref.replace(/^www\./, '') : '';
  return { e: o.e, page: typeof o.page === 'string' && PAGES.test(o.page) ? o.page : '/', ref, day: today };
}

export class Stats {
  constructor(state) {
    this.sql = state.storage.sql;
    this.sql.exec(`CREATE TABLE IF NOT EXISTS days (id TEXT, day TEXT, v TEXT, lang TEXT, projects TEXT, claude INTEGER, features INTEGER, PRIMARY KEY (id, day))`);
    this.sql.exec(`CREATE TABLE IF NOT EXISTS events (id TEXT, e TEXT, v TEXT, day TEXT, PRIMARY KEY (id, e))`);
    this.sql.exec(`CREATE TABLE IF NOT EXISTS site (day TEXT, e TEXT, page TEXT, ref TEXT, n INTEGER, PRIMARY KEY (day, e, page, ref))`);
  }

  async fetch(req) {
    const url = new URL(req.url);
    if (url.pathname === '/put') {
      const o = await req.json();
      if (o.kind === '/v1/day') this.sql.exec(
        `INSERT INTO days VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT (id, day) DO UPDATE SET v = excluded.v, lang = excluded.lang,
         projects = excluded.projects, claude = MAX(claude, excluded.claude), features = features | excluded.features`,
        o.id, o.day, o.v, o.lang, o.projects, o.claude, o.features);
      else if (o.kind === '/v1/event') this.sql.exec(`INSERT OR IGNORE INTO events VALUES (?, ?, ?, ?)`, o.id, o.e, o.v, o.day);
      else this.sql.exec(`INSERT INTO site VALUES (?, ?, ?, ?, 1) ON CONFLICT (day, e, page, ref) DO UPDATE SET n = n + 1`, o.day, o.e, o.page, o.ref);
      return new Response(null, { status: 204 });
    }
    if (url.pathname === '/report') return json(this.report());
    return text('not found', 404);
  }

  report() {
    const all = q => [...this.sql.exec(q)];
    const one = q => all(q)[0] || {};
    const today = new Date().toISOString().slice(0, 10);
    const ago = n => new Date(Date.now() - n * 86_400_000).toISOString().slice(0, 10);
    // An install counts from its first day seen (or its install event, if earlier).
    const first = `SELECT id, MIN(day) AS first FROM (SELECT id, day FROM days UNION ALL SELECT id, day FROM events WHERE e = 'install') GROUP BY id`;
    const retained = n => one(`SELECT COUNT(*) AS cohort, SUM(CASE WHEN EXISTS (SELECT 1 FROM days d WHERE d.id = f.id AND d.day >= date(f.first, '+${n} day')) THEN 1 ELSE 0 END) AS back
      FROM (${first}) f WHERE f.first <= '${ago(n)}'`);
    return {
      at: new Date().toISOString(),
      installs: one(`SELECT COUNT(*) AS n FROM (${first})`).n || 0,
      uninstalls: one(`SELECT COUNT(*) AS n FROM events WHERE e = 'uninstall'`).n || 0,
      active: { today: one(`SELECT COUNT(DISTINCT id) AS n FROM days WHERE day = '${today}'`).n || 0,
        week: one(`SELECT COUNT(DISTINCT id) AS n FROM days WHERE day >= '${ago(6)}'`).n || 0 },
      // Set up: at least one project, and Claude Code connected, on some day.
      activated: one(`SELECT COUNT(DISTINCT id) AS n FROM days WHERE projects != '0' AND claude = 1`).n || 0,
      retention: { day1: retained(1), day7: retained(7) },
      features: Object.fromEntries(['now', 'receipt', 'commit', 'push', 'closeday', 'fill', 'widget', 'strip', 'search', 'phone'].map((f, i) =>
        [f, one(`SELECT COUNT(DISTINCT id) AS n FROM days WHERE features & ${1 << i} AND day >= '${ago(6)}'`).n || 0])),
      versions: all(`SELECT v, COUNT(DISTINCT id) AS n FROM days WHERE day >= '${ago(6)}' GROUP BY v ORDER BY n DESC`),
      languages: all(`SELECT lang, COUNT(DISTINCT id) AS n FROM days GROUP BY lang`),
      projects: all(`SELECT projects, COUNT(*) AS n FROM (SELECT id, projects, MAX(day) FROM days WHERE day >= '${ago(6)}' GROUP BY id) GROUP BY projects`),
      byDay: all(`SELECT d.day, COUNT(DISTINCT d.id) AS active, (SELECT COUNT(*) FROM (${first}) f WHERE f.first = d.day) AS new
        FROM days d WHERE d.day >= '${ago(29)}' GROUP BY d.day ORDER BY d.day`),
      site: {
        views: all(`SELECT day, SUM(n) AS n FROM site WHERE e = 'view' AND day >= '${ago(29)}' GROUP BY day ORDER BY day`),
        downloads: one(`SELECT SUM(n) AS n FROM site WHERE e = 'download'`).n || 0,
        refs: all(`SELECT ref, SUM(n) AS n FROM site WHERE e = 'view' AND ref != '' GROUP BY ref ORDER BY n DESC LIMIT 12`),
        pages: all(`SELECT page, SUM(n) AS n FROM site WHERE e = 'view' GROUP BY page ORDER BY n DESC LIMIT 12`),
      },
    };
  }
}

function page(r) {
  const pct = (a, b) => b ? Math.round(100 * a / b) + '%' : '—';
  const row = (k, v) => `<tr><td>${k}</td><td>${v}</td></tr>`;
  const list = (rows, k) => rows.map(x => row(x[k] || '—', x.n)).join('');
  const bars = rows => { const max = Math.max(1, ...rows.map(x => x.n)); return rows.map(x => `<div class="bar"><span>${x.day.slice(5)}</span><i style="width:${100 * x.n / max}%"></i><b>${x.n}</b></div>`).join(''); };
  return `<!doctype html><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>YDeck stats</title>
<style>body{font:15px/1.5 system-ui,sans-serif;background:#0b0b0c;color:#f4f4f5;margin:0;padding:24px;max-width:860px}
h1{font-weight:500;margin:0 0 4px}h2{font-weight:500;font-size:15px;color:#a1a1a8;margin:28px 0 8px}.k{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:12px}
.c{background:#161618;border:1px solid #2a2a2e;border-radius:14px;padding:14px}.c b{display:block;font-size:28px;font-weight:500}.c span{color:#a1a1a8;font-size:13px}
table{border-collapse:collapse;width:100%}td{padding:6px 0;border-bottom:1px solid #222}td:last-child{text-align:right}
.bar{display:grid;grid-template-columns:48px 1fr 40px;gap:8px;align-items:center;font-size:12px;color:#a1a1a8}.bar i{display:block;height:8px;border-radius:4px;background:#5b54f0}.bar b{color:#f4f4f5;font-weight:500}</style>
<h1>YDeck</h1><div style="color:#a1a1a8">${r.at.replace('T', ' ').slice(0, 16)} UTC · counts only, no personal data</div>
<h2>The app</h2><div class="k">
<div class="c"><b>${r.installs}</b><span>installs</span></div>
<div class="c"><b>${r.activated}</b><span>set up (a project + Claude Code) · ${pct(r.activated, r.installs)}</span></div>
<div class="c"><b>${r.active.today} / ${r.active.week}</b><span>active today / this week</span></div>
<div class="c"><b>${pct(r.retention.day1.back, r.retention.day1.cohort)}</b><span>back after a day (${r.retention.day1.back || 0}/${r.retention.day1.cohort || 0})</span></div>
<div class="c"><b>${pct(r.retention.day7.back, r.retention.day7.cohort)}</b><span>back after a week (${r.retention.day7.back || 0}/${r.retention.day7.cohort || 0})</span></div>
<div class="c"><b>${r.uninstalls}</b><span>uninstalls</span></div></div>
<h2>Used this week (installs)</h2><table>${Object.entries(r.features).sort((a, b) => b[1] - a[1]).map(([f, n]) => row(f, `${n} · ${pct(n, r.active.week)}`)).join('')}</table>
<h2>Active installs, last 30 days</h2>${bars(r.byDay.map(x => ({ day: x.day, n: x.active })))}
<h2>Projects per install (this week)</h2><table>${list(r.projects, 'projects')}</table>
<h2>Versions (this week)</h2><table>${list(r.versions, 'v')}</table>
<h2>Languages</h2><table>${list(r.languages, 'lang')}</table>
<h2>The website</h2><div class="k"><div class="c"><b>${r.site.views.reduce((s, x) => s + x.n, 0)}</b><span>page views, 30 days</span></div>
<div class="c"><b>${r.site.downloads}</b><span>download clicks</span></div></div>
<h2>Views per day</h2>${bars(r.site.views)}
<h2>Where they came from</h2><table>${list(r.site.refs, 'ref')}</table>
<h2>Pages</h2><table>${list(r.site.pages, 'page')}</table>`;
}

const text = (s, status) => new Response(s, { status, headers: { 'content-type': 'text/plain; charset=utf-8' } });
const json = o => new Response(JSON.stringify(o, null, 2), { headers: { 'content-type': 'application/json' } });
const html = s => new Response(s, { headers: { 'content-type': 'text/html; charset=utf-8', 'cache-control': 'no-store' } });
const cors = r => { r.headers.set('access-control-allow-origin', '*'); r.headers.set('access-control-allow-methods', 'POST'); r.headers.set('access-control-allow-headers', 'content-type'); return r; };
