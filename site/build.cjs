// node site/build.cjs → site/dist, the static site for ydeck-app.pages.dev: the desktop home (/ and /he, desktop.cjs),
// the other pages (pages.cjs: privacy, download, /android, 404), and the Android app's APK. `--deploy` also publishes it (Cloudflare Pages,
// project "ydeck-app", with the wrangler login of yonidev-cloud). `--no-og` reuses the cached share image.
// The app is the source of truth: service names, brand colours, URL patterns and icons come from ServiceCatalog.kt and
// the simple-icons drawables; the APK from releases/.
const fs = require('fs'), path = require('path'), crypto = require('crypto'), { execFileSync } = require('child_process');

const ROOT = path.join(__dirname, '..'), SRC = path.join(__dirname, 'src'), DIST = path.join(__dirname, 'dist'), CACHE = path.join(__dirname, '.cache');
const SITE_URL = 'https://ydeck-app.pages.dev';
const EMAIL = 'yonidev0101@gmail.com';
const VERSION = '0.8', APK_SRC = path.join(ROOT, 'releases', 'YDeck-0.8-glasspreview.apk'), APK_NAME = `YDeck-${VERSION}.apk`;
const UPDATED = '11 באוקטובר 2026', RELEASED = '11.10.2026';
const PLAYWRIGHT = 'C:/Users/user/Desktop/YoniDev projects/al-hamacom-dev/node_modules/playwright';

// ── the app's catalog ──
const model = fs.readFileSync(path.join(ROOT, 'app/src/main/java/com/yonidev/ydeck/data/Model.kt'), 'utf8');
const CATEGORY = Object.fromEntries([...model.matchAll(/(\w+)\("([^"]+)"\)[,\s]/g)].map(m => [m[1], m[2]]));
const catalog = fs.readFileSync(path.join(ROOT, 'app/src/main/java/com/yonidev/ydeck/data/ServiceCatalog.kt'), 'utf8');
const services = {}, icons = {};
for (const m of catalog.matchAll(/Service\("([^"]+)", "([^"]+)", "([^"]+)", R\.drawable\.(\w+), (true|false), (?:0x([0-9A-Fa-f]{8})\.toInt\(\)|null), Route\.(\w+), "[^"]*", listOf\(([^)]*)\), Category\.(\w+)\)/g)) {
  const [, id, label, short, drawable, brand, color, route, match, cat] = m;
  services[id] = { label, short, color: color ? '#' + color.slice(2) : '#8A8A90', route: route.toLowerCase(), brand: brand === 'true',
    match: [...match.matchAll(/"([^"]+)"/g)].map(x => x[1]), cat: CATEGORY[cat] || '' };
  if (drawable.startsWith('si_')) {
    const xml = fs.readFileSync(path.join(ROOT, 'app/src/main/res/drawable', drawable + '.xml'), 'utf8');
    icons[id] = [...xml.matchAll(/android:pathData="([^"]+)"/g)].map(x => x[1]).join(' ');
  }
}
const SERVICE_COUNT = Object.keys(services).length;
if (SERVICE_COUNT < 150) throw new Error('catalog parse failed: ' + SERVICE_COUNT);

// The demo projects of the films.
const PROJECTS = [
  { name: 'Nova', mono: 'N', color: '#0B0B0C', email: 'dana@nova.dev', vercel: 'Chrome Beta', links: ['firebase', 'play', 'vercel', 'github', 'sentry', 'analytics', 'drive'] },
  { name: 'Orbit', mono: 'O', color: '#0EA5E9', email: 'hello@orbit.app', vercel: 'Chrome', links: ['vercel', 'github', 'supabase', 'stripe', 'gmail', 'resend', 'sentry'] },
  { name: 'Pulse', mono: 'P', color: '#FF5A36', email: 'dana@pulse.io', vercel: 'Chrome Dev', links: ['play', 'firebase', 'revenuecat', 'mixpanel', 'github', 'gmail', 'drive'] },
];
// Browser tabs of three projects, each signed in to a different account (the avatar).
const ACCOUNTS = [{ letter: 'D', color: '#6E5BFF' }, { letter: 'H', color: '#0EA5E9' }, { letter: 'Y', color: '#F08A24' }];
const TABS = [['firebase', 'Firebase – nova', 0], ['play', 'Play Console', 2], ['vercel', 'nova-web – Vercel', 0], ['github', 'nova/app', 0],
  ['gmail', 'Inbox (12)', 2], ['sentry', 'Issues – nova', 0], ['firebase', 'Firebase – pulse', 2], ['supabase', 'orbit | Supabase', 1],
  ['stripe', 'Payments – Stripe', 1], ['vercel', 'orbit – Vercel', 1], ['analytics', 'Analytics', 2], ['drive', 'My Drive', 0],
  ['github', 'orbit/web', 1], ['mixpanel', 'Pulse – Mixpanel', 2], ['revenuecat', 'RevenueCat', 2], ['gmail', 'Inbox (3)', 0], ['cloudflare', 'Cloudflare', 1], ['resend', 'Resend', 1]];
const MARQUEE = ['firebase', 'play', 'vercel', 'github', 'supabase', 'mongodb', 'cloudflare', 'stripe', 'sentry', 'railway', 'netlify', 'figma', 'notion', 'linear',
  'anthropic', 'claude', 'gemini', 'huggingface', 'neon', 'upstash', 'redis', 'prisma', 'expo', 'appstore', 'revenuecat', 'mixpanel', 'posthog', 'analytics',
  'gcloud', 'gmail', 'drive', 'docker', 'npm', 'gitlab', 'jira', 'discord', 'telegram', 'resend', 'cloudinary', 'n8n', 'zapier', 'digitalocean', 'datadog', 'paypal'].filter(id => icons[id]);

// ── shared pieces ──
const esc = s => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
const MARK_PATHS = (() => {
  const xml = fs.readFileSync(path.join(ROOT, 'app/src/main/res/drawable/ic_launcher_foreground.xml'), 'utf8');
  return [...xml.matchAll(/android:fillColor="#FF([0-9A-F]{6})" android:pathData="([^"]+)"/g)].map(m => ({ mint: m[1] === '5EF2C2', d: m[2] }));
})();
/** The app icon: white mark (mint tile) on an indigo rounded square. */
const MARK = size => `<svg class="mark" viewBox="0 0 64 64" width="${size}" height="${size}" aria-hidden="true"><rect width="64" height="64" rx="16" fill="#3D35E0"/><svg x="11" y="11" width="42" height="42" viewBox="48 76 542 489">${MARK_PATHS.map(p => `<path fill="${p.mint ? '#5EF2C2' : '#fff'}" d="${p.d}"/>`).join('')}</svg></svg>`;
const FAVICON = 'data:image/svg+xml,' + encodeURIComponent(MARK(64).replace('<svg class="mark"', '<svg xmlns="http://www.w3.org/2000/svg"'));
const IC = {
  download: '<path d="M12 4v11m0 0-4.5-4.5M12 15l4.5-4.5M5 20h14"/>',
  arrow: '<path d="M14 6l-6 6 6 6"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  lock: '<rect x="5" y="10.5" width="14" height="10" rx="2.5"/><path d="M8 10.5V8a4 4 0 0 1 8 0v2.5"/>',
  phone: '<rect x="7" y="3" width="10" height="18" rx="2.5"/><path d="M11 18h2"/><path d="M3 9.5a9 9 0 0 1 2-4M21 9.5a9 9 0 0 0-2-4"/>',
};
const I = name => `<svg class="ic" viewBox="0 0 24 24" aria-hidden="true">${IC[name]}</svg>`;
const svc = (id, color) => `<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="${color || 'currentColor'}" d="${icons[id]}"/></svg>`;
function adaptLight(hex) {
  const lin = v => { v /= 255; return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); };
  const c = [1, 3, 5].map(i => parseInt(hex.substr(i, 2), 16));
  const l = 0.2126 * lin(c[0]) + 0.7152 * lin(c[1]) + 0.0722 * lin(c[2]);
  return l > 0.55 ? `rgb(${c.map(v => Math.round(v * 0.65)).join(',')})` : hex;
}
const css = fs.readFileSync(path.join(SRC, 'site.css'), 'utf8');
const stingJs = fs.readFileSync(path.join(SRC, 'sting.js'), 'utf8');
const homeJs = fs.readFileSync(path.join(SRC, 'home.js'), 'utf8');

function page(file, title, desc, body, script = '') {
  const html = `<!doctype html><html lang="he" dir="rtl"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${title}</title>
<meta name="description" content="${desc}">
<meta property="og:title" content="${title}"><meta property="og:description" content="${desc}">
<meta property="og:type" content="website"><meta property="og:site_name" content="YDeck"><meta property="og:locale" content="he_IL">
<meta property="og:image" content="${SITE_URL}/og.jpg"><meta property="og:image:width" content="1200"><meta property="og:image:height" content="630">
<meta name="twitter:card" content="summary_large_image"><meta name="twitter:image" content="${SITE_URL}/og.jpg">
<meta name="theme-color" content="#FFFFFF"><meta name="color-scheme" content="light">
<link rel="icon" href="${FAVICON}">
<link rel="preconnect" href="https://fonts.googleapis.com"><link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=IBM+Plex+Sans+Hebrew:wght@400;500;600;700&family=IBM+Plex+Mono:wght@400;500&family=Noto+Sans+Hebrew:wght@200;300&display=swap" rel="stylesheet">
<style>${css}</style>
<script>document.documentElement.classList.add('js')</script></head><body>
<header class="nav"><div class="wrap">
<a class="brand" href="/">${MARK(28)}YDeck</a>
<nav><a href="/android#order">איך זה עובד</a><a href="/android#features">יכולות</a><a href="/privacy">פרטיות</a></nav>
<a class="btn" href="/download">${I('download')}הורדה</a>
</div></header>
${body}
<footer><div class="wrap">
<a class="brand" href="/">${MARK(22)}YDeck</a><span>מבית YoniDev</span>
<nav><a href="/download">הורדה</a><a href="/privacy">מדיניות פרטיות</a><a href="mailto:${EMAIL}">${EMAIL}</a></nav>
</div></footer>
${script}
</body></html>`;
  const out = path.join(DIST, file);
  fs.mkdirSync(path.dirname(out), { recursive: true });
  fs.writeFileSync(out, html);
}

// ── home ──
const FAQ = [
  ['באילו טלפונים YDeck עובדת?', 'באנדרואיד 12 ומעלה, עם כל מסך בית שתומך בווידג\'טים. אין גרסה לאייפון.'],
  ['איך YDeck יודעת באיזה חשבון לפתוח?', 'לכל פרויקט שומרים את המייל של חשבון הגוגל שלו. שירותי גוגל נפתחים דרך בוחר החשבונות של גוגל עם המייל הזה, ולכן נכנסים ישר לחשבון הנכון. YDeck לא מתחברת לשום חשבון ולא רואה סיסמאות.'],
  ['ומה עם Vercel?', 'Vercel לא מחזיקה כמה חשבונות באותו דפדפן. לכן כל חשבון Vercel גר בערוץ Chrome משלו (Chrome, Beta, Dev או Canary): מתחברים פעם אחת בכל ערוץ, ובוחרים לכל פרויקט את הערוץ שלו.'],
  ['צריך להירשם?', 'לא. אין חשבון ואין שרת. פותחים את האפליקציה, מוסיפים פרויקט ומוסיפים ווידג\'ט. אפשר גם להתחיל מפרויקט לדוגמה.'],
  ['מה קורה כשמחליפים טלפון?', 'הגיבוי של אנדרואיד לחשבון גוגל מעביר את הפרויקטים והלוגואים לטלפון החדש. אפשר גם לייצא קובץ גיבוי ולשחזר ממנו.'],
  ['כמה זה עולה?', 'YDeck חינמית, בלי פרסומות.'],
];

function buildHome() {
  const marquee = ids => ids.concat(ids).map(id => `<div title="${esc(services[id].label)}">${svc(id, adaptLight(services[id].color))}</div>`).join('');
  const half = Math.ceil(MARQUEE.length / 2);
  const data = {
    projects: PROJECTS, accounts: ACCOUNTS, tabs: TABS,
    services: Object.fromEntries(Object.entries(services).map(([id, s]) => [id, { label: s.label, short: s.short, color: s.color, route: s.route, brand: s.brand, match: s.match, cat: s.cat }])),
    icons,
  };
  page('android/index.html', 'YDeck לאנדרואיד · כל הפרויקט. במקום אחד.', 'ווידג\'ט לאנדרואיד שמחזיק את כל הקישורים של כל פרויקט, וכל נגיעה פותחת את הקישור בחשבון הנכון. חינם, בלי חשבון.', `<main>
<section class="hero"><div class="wrap">
<p class="eyebrow rise">ווידג'ט לאנדרואיד · למפתחים ולפרילנסרים</p>
<h1 class="h1 rise">כל הפרויקט.<br><em>במקום אחד.</em></h1>
<p class="lead rise">Firebase, Play Console, Vercel, GitHub וכל השאר, בווידג'ט אחד על מסך הבית. <b>וכל נגיעה נפתחת בחשבון הנכון.</b></p>
<div class="ctas rise"><a class="btn big" href="/download">${I('download')}הורדה לאנדרואיד</a><a class="link" href="#route-sec">איך זה יודע איזה חשבון ${I('arrow')}</a></div>
<p class="meta rise">חינם · בלי חשבון · בלי שרת · אנדרואיד 12 ומעלה</p>
</div></section>

<section class="order" id="order"><div class="pin">
<p class="say" aria-live="polite"><span class="before"><b>18 טאבים.</b> שלושה פרויקטים, שלושה חשבונות.</span><span class="after"><b>ווידג'ט אחד.</b> וכל אחד בחשבון שלו.</span></p>
<div class="screen"><span class="clock">09:41</span><div class="widget"></div><div class="apps"><i></i><i></i><i></i><i></i></div></div>
</div></section>

<section class="sec" id="route-sec"><div class="wrap split">
<div class="rise">
<p class="eyebrow">החשבון הנכון</p>
<h2 class="h2">כל קישור יודע<br><em>לאן הוא שייך.</em></h2>
<p class="lead">לכל פרויקט שומרים פעם אחת את חשבון הגוגל שלו ואת ערוץ ה־Chrome של חשבון ה־Vercel שלו. מאז, כל נגיעה בווידג'ט נוחתת מחוברת, <b>בלי בוחר חשבונות ובלי להתנתק.</b></p>
</div>
<div class="route rise" id="route"><div class="seg" role="tablist" aria-label="פרויקט"></div><ul class="rows"></ul>
<p class="note">שירותי גוגל עוברים דרך בוחר החשבונות של גוגל עם המייל של הפרויקט. Vercel נפתח בערוץ Chrome שבו החשבון שלו מחובר.</p></div>
</div></section>

<section class="sec gray" id="features"><div class="wrap">
<div class="rise"><p class="eyebrow">יכולות</p><h2 class="h2">קטן על המסך.<br><em>גדול בעבודה.</em></h2></div>
<div class="bento">
<div class="tile t-size rise"><h3 class="h3">כל גודל</h3><p>מ־<span class="ltr">4×2</span> ועד שורה אחת מתחת לשעון. הפריסה מסתדרת לבד, ואפשר כמה ווידג'טים, לכל אחד פרויקט משלו.</p>
<div class="seg" role="tablist" aria-label="גודל"><button type="button" data-i="0"><span class="ltr">4×2</span></button><button type="button" data-i="1"><span class="ltr">2×2</span></button><button type="button" data-i="2"><span class="ltr">4×1</span></button></div>
<div class="area"><div class="widget"></div></div></div>
<div class="tile t-paste rise"><h3 class="h3">מדביקים. זה מזוהה.</h3><p>מדביקים קישור, ו־YDeck יודעת איזה שירות זה. נסו להדביק כאן קישור משלכם.</p>
<div class="res" aria-live="polite"></div>
<label class="in"><input type="url" inputmode="url" placeholder="https://" aria-label="קישור" spellcheck="false" autocomplete="off"></label></div>
<div class="tile t-logos rise"><div class="big-n">${SERVICE_COUNT}</div><h3 class="h3">שירותים, עם הלוגו האמיתי</h3><p>בצבע המותג. וכל קישור אחר מקבל את האייקון של האתר עצמו.</p>
<div class="marq" aria-hidden="true"><div class="row">${marquee(MARQUEE.slice(0, half))}</div><div class="row r2">${marquee(MARQUEE.slice(half))}</div></div></div>
<div class="tile t-copy rise"><h3 class="h3">מה שתמיד מחפשים</h3><p>package, repo ומזהים, בחלונית "עוד" של הווידג'ט. נגיעה, והם בלוח.</p>
<div class="copies"><button class="copy" type="button"><span><small>Package</small><code>dev.nova.app</code></span><span class="st">העתקה</span></button>
<button class="copy" type="button"><span><small>Firebase project</small><code>nova-app-4f21</code></span><span class="st">העתקה</span></button></div></div>
<div class="tile t-priv rise"><span class="lock">${I('lock')}</span><h3 class="h3">בלי שרת. בלי חשבון.</h3><p>הפרויקטים נשמרים בטלפון בלבד. כדי להביא אייקון של אתר נשלח רק שם האתר, בלי הקישור ובלי המייל. <a href="/privacy">המדיניות המלאה</a></p></div>
<div class="tile t-back rise"><span class="lock">${I('phone')}</span><h3 class="h3">עובר איתך לטלפון חדש</h3><p>הגיבוי של אנדרואיד מעביר את הפרויקטים והלוגואים. ואפשר גם לייצא קובץ אחד ולשחזר ממנו.</p></div>
</div></div></section>

<section class="sec"><div class="wrap film">
<div class="rise"><p class="eyebrow">YDeck בחצי דקה</p><h2 class="h2">מפתח, שלושה פרויקטים,<br><em>ווידג'ט אחד.</em></h2>
<p class="lead">כל פרויקט עם הקישורים שלו, וכל קישור עם החשבון שלו. ככה זה נראה בטלפון.</p></div>
<video class="rise" controls playsinline preload="none" poster="/media/ydeck-explainer.jpg"><source src="/media/ydeck-explainer.mp4" type="video/mp4"></video>
</div></section>

<section class="sec gray"><div class="wrap faqs">
<div class="rise"><p class="eyebrow">שאלות</p><h2 class="h2">מה ששואלים<br><em>לפני שמתקינים.</em></h2></div>
<div class="faq rise">${FAQ.map(([q, a]) => `<details><summary>${q}${I('plus')}</summary><p>${a}</p></details>`).join('')}</div>
</div></section>

<section class="end"><div class="wrap">
<canvas id="endmark" aria-hidden="true"></canvas>
<h2 class="h2">פחות טאבים.<br><b>יותר פרויקט.</b></h2>
<a class="btn big white" href="/download">${I('download')}הורדה לאנדרואיד</a>
<p class="meta">חינם · גרסה ${VERSION} · בקרוב גם ב־Google Play</p>
</div></section>
</main>`,
    `<script type="application/json" id="data">${JSON.stringify(data).replace(/</g, '\\u003c')}</script><script>${stingJs}</script><script>${homeJs}</script>`);
}

// ── document pages ──
function doc(file, title, desc, head, body) {
  page(file, title, desc, `<header class="dhead"><div class="wrap">${head}</div></header><main class="doc"><div class="wrap">${body}</div></main>`);
}
const dsec = (n, h, html, dir) => `<section class="dsec"${dir ? ` dir="${dir}" lang="en"` : ''}><span class="num">${String(n).padStart(2, '0')}</span><div><h2>${h}</h2>${html}</div></section>`;
const steps = (...xs) => `<div class="path">${xs.map(x => `<span>${x}</span>`).join(I('arrow'))}</div>`;

function buildDownload(apk) {
  doc('download/index.html', 'הורדה · YDeck', `YDeck ${VERSION} לאנדרואיד 12 ומעלה. חינם.`,
    `<p class="eyebrow">הורדה</p><h1 class="h1">להוריד את YDeck</h1><p class="lead">גרסה ${VERSION} · לאנדרואיד 12 ומעלה · חינם, בלי חשבון.</p>`,
    `<div class="dl"><a class="btn big" href="/download/${APK_NAME}" download>${I('download')}הורדת YDeck ${VERSION}</a><span class="meta">APK · ${apk.mb} MB · ${RELEASED}</span></div>
${dsec(1, 'מתקינים', `<p>פותחים את הקובץ שירד ומאשרים התקנה. בפעם הראשונה אנדרואיד מבקש לאשר התקנות מהדפדפן:</p>${steps('פתיחת הקובץ', 'הגדרות', 'לאפשר מהמקור הזה', 'התקנה')}<p>פותחים את YDeck. במסך הפתיחה אפשר להתחיל מ<b>"נסה עם פרויקט לדוגמה"</b>. את הווידג'ט מוסיפים מהאפליקציה, או בלחיצה ארוכה על מסך הבית:</p>${steps('לחיצה ארוכה על מסך הבית', 'ווידג\'טים', 'YDeck')}`)}
${dsec(2, 'למה מופיעה אזהרה?', '<p>אנדרואיד מזהיר על כל אפליקציה שלא מגיעה מ־Google Play. זה לא אומר שמשהו לא בסדר. YDeck בדרך לחנות, ואז ההתקנה תהיה משם.</p>')}
${dsec(3, 'יש לי גרסה קודמת', '<p>מגרסה 0.6 YDeck חתומה במפתח הקבוע שלה, ולכן אנדרואיד לא מתקין אותה מעל גרסה ישנה יותר ("מתנגש בחבילה קיימת"). פעם אחת בלבד:</p>' + steps('בגרסה הישנה: ייצוא לקובץ', 'הסרה', 'התקנת ' + VERSION, 'שחזור מקובץ') + '<p>מעכשיו כל גרסה חדשה מותקנת מעל הקודמת, והפרויקטים נשמרים.</p>')}
${dsec(4, 'איך יודעים שהקובץ אמיתי?', `<p>מי שרוצה לוודא שהקובץ לא שונה בדרך, יכול להשוות את טביעת האצבע שלו (SHA-256):</p><div class="hash">${apk.sha256}</div>`)}
${dsec(5, 'ואייפון?', '<p>YDeck היא ווידג\'ט לאנדרואיד בלבד.</p>')}`);
}

function buildPrivacy() {
  const he = [
    ['בקצרה', '<p>YDeck היא ווידג\'ט שפותח את הקישורים של הפרויקטים שלך בחשבון הנכון. אין לה שרת, אין חשבון משתמש, אין פרסומות ואין אנליטיקס. הפרויקטים נשמרים בטלפון בלבד, ואנחנו לא מקבלים מהם כלום.</p>'],
    ['מה נשמר בטלפון', '<ul><li>הפרויקטים שהזנת: שמות, צבעים, לוגואים, קישורים, כתובות המייל של החשבונות שלהם, וערכים להעתקה.</li><li>אייקונים של אתרים, שנטענו פעם אחת ונשמרו כדי שהווידג\'ט לא יצטרך רשת.</li></ul><p>הסרת האפליקציה מוחקת את כל אלה.</p>'],
    ['מה יוצא מהטלפון', '<p><b>שמות אתרים, כדי להביא אייקונים.</b> לקישור שאין לו סמל מובנה (למשל אתר משלך), YDeck מביאה את האייקון של האתר. לשם כך נשלח <b>רק שם האתר</b> (למשל <code>example.com</code>), בלי הכתובת המלאה ובלי שום מידע אחר, אל שירות האייקונים של Google ואל שירות האייקונים של DuckDuckGo, ולפעמים לאתר עצמו (העמוד הראשי ו־<code>/favicon.ico</code>). כמו בכל בקשה באינטרנט, השירותים האלה רואים את כתובת ה־IP של הטלפון. הם לא מקבלים את כתובות המייל, את שמות הפרויקטים או את הקישורים המלאים. לפרטים: <a href="https://policies.google.com/privacy">מדיניות הפרטיות של Google</a>, <a href="https://duckduckgo.com/privacy">מדיניות הפרטיות של DuckDuckGo</a>.</p><p><b>פתיחת קישור.</b> נגיעה בסמל בווידג\'ט פותחת את הקישור בדפדפן (Chrome או ערוץ אחר שלו), ולשירותי גוגל עם הכתובת של החשבון שבחרת לפרויקט, כדי שגוגל תפתח אותו בחשבון הנכון. מה שקורה משם הוא בין הדפדפן לאתר.</p><p>כל התעבורה מוצפנת (HTTPS).</p>'],
    ['גיבוי', '<ul><li><b>גיבוי של אנדרואיד:</b> אם הגיבוי של הטלפון לחשבון גוגל פעיל, אנדרואיד כולל בו את קובץ הפרויקטים ואת הלוגואים, ומחזיר אותם בהתקנה בטלפון חדש. הגיבוי הזה מנוהל ומוצפן על ידי אנדרואיד וחשבון הגוגל שלך, ואין לנו גישה אליו. אפשר לכבות אותו בהגדרות הטלפון.</li><li><b>ייצוא לקובץ:</b> קובץ שנוצר רק כשביקשת, ונשמר איפה שבחרת.</li></ul>'],
    ['הרשאות', '<ul><li><b>אינטרנט:</b> רק כדי להביא אייקונים של אתרים, כמו שמתואר למעלה.</li><li><b>ערוצי Chrome:</b> האפליקציה בודקת אם Chrome, Beta, Dev או Canary מותקנים, כדי לפתוח כל חשבון Vercel בערוץ שלו. לא נבדקות אפליקציות אחרות.</li></ul>'],
    ['ילדים', '<p>YDeck מיועדת למפתחים ולא לילדים, ולא אוספת ביודעין מידע מילדים.</p>'],
    ['שינויים ויצירת קשר', `<p>אם המדיניות תשתנה, הגרסה המעודכנת תופיע בעמוד הזה עם תאריך חדש. שאלות: <a href="mailto:${EMAIL}">${EMAIL}</a>.</p>`],
  ];
  const en = [
    ['Summary', '<p>YDeck is a widget that opens your projects\' links in the right account. It has no server, no user accounts, no ads and no analytics. Your projects are stored on your phone only, and nothing about them is sent to us.</p>'],
    ['What is stored on your phone', '<ul><li>The projects you enter: names, colours, logos, links, the email addresses of their accounts, and values to copy.</li><li>Website icons, fetched once and cached so the widget never needs the network.</li></ul><p>Uninstalling the app deletes all of it.</p>'],
    ['What leaves your phone', '<p><b>Website names, to fetch icons.</b> For a link without a built-in mark, YDeck fetches the site\'s icon. It sends <b>only the domain name</b> (e.g. <code>example.com</code>), never the full link or anything else, to Google\'s favicon service and DuckDuckGo\'s icon service, and sometimes to the site itself (its home page and <code>/favicon.ico</code>). As with any web request, these services see your phone\'s IP address. They do not receive your email addresses, project names or full links. See the <a href="https://policies.google.com/privacy">Google Privacy Policy</a> and the <a href="https://duckduckgo.com/privacy">DuckDuckGo Privacy Policy</a>.</p><p><b>Opening a link.</b> Tapping a mark opens the link in your browser (Chrome or one of its channels); for Google services, with the address of the account you chose for the project, so Google opens it in that account. From there it is between your browser and the site.</p><p>All traffic is encrypted (HTTPS).</p>'],
    ['Backup', '<ul><li><b>Android backup:</b> if your phone\'s backup to your Google account is on, Android includes the projects file and the logos and restores them on a new phone. This backup is run and encrypted by Android and your Google account; we cannot access it. You can turn it off in your phone\'s settings.</li><li><b>Export to file:</b> created only when you ask, saved where you choose.</li></ul>'],
    ['Permissions', '<ul><li><b>Internet:</b> only to fetch website icons, as described above.</li><li><b>Chrome channels:</b> the app checks whether Chrome, Beta, Dev or Canary is installed, to open each Vercel account in its own channel. No other apps are checked.</li></ul>'],
    ['Children', '<p>YDeck is made for developers, not children, and does not knowingly collect data from children.</p>'],
    ['Changes and contact', `<p>If this policy changes, the new version will appear on this page with a new date. Questions: <a href="mailto:${EMAIL}">${EMAIL}</a>.</p>`],
  ];
  doc('privacy/index.html', 'מדיניות פרטיות · YDeck', 'מה YDeck שומרת, מה יוצא מהטלפון, ומה לא. בעברית ובאנגלית.',
    `<p class="eyebrow">פרטיות</p><h1 class="h1">מדיניות פרטיות</h1><p class="lead">YDeck · בתוקף מ־${UPDATED} · <a href="#en" style="color:var(--indigo)">English</a></p>`,
    he.map(([h, b], i) => dsec(i + 1, h, b)).join('\n') +
    `<div class="en" id="en">${en.map(([h, b], i) => dsec(i + 1, h, b, 'ltr')).join('\n')}<p dir="ltr" style="text-align:left;margin-top:20px;font-size:13px;color:var(--faint)">YDeck · Privacy Policy · Effective October 11, 2026 · Developer: YoniDev · ${EMAIL}</p></div>`);
}

// ── assets ──
function sha256(file) { return crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex'); }
function media() {
  fs.mkdirSync(CACHE, { recursive: true });
  const src = path.join(ROOT, 'store/videos/YDeck-explainer-status.mp4');
  const mp4 = path.join(CACHE, 'ydeck-explainer.mp4'), jpg = path.join(CACHE, 'ydeck-explainer.jpg');
  if (!fs.existsSync(mp4) || fs.statSync(mp4).mtimeMs < fs.statSync(src).mtimeMs) {
    execFileSync('ffmpeg', ['-y', '-loglevel', 'error', '-i', src, '-vf', 'scale=720:1280', '-c:v', 'libx264', '-crf', '24', '-preset', 'slow', '-pix_fmt', 'yuv420p', '-c:a', 'aac', '-b:a', '128k', '-movflags', '+faststart', mp4]);
    execFileSync('ffmpeg', ['-y', '-loglevel', 'error', '-ss', '12', '-i', src, '-frames:v', '1', '-vf', 'scale=720:1280', '-q:v', '4', jpg]);
  }
  fs.mkdirSync(path.join(DIST, 'media'), { recursive: true });
  fs.copyFileSync(mp4, path.join(DIST, 'media/ydeck-explainer.mp4'));
  fs.copyFileSync(jpg, path.join(DIST, 'media/ydeck-explainer.jpg'));
}
/** The share image: white like the site, the logo dealt on its indigo tile, the line beside it. */
async function og() {
  const out = path.join(CACHE, 'og.jpg');
  if (!process.argv.includes('--no-og') || !fs.existsSync(out)) {
    const { chromium } = require(PLAYWRIGHT);
    const b = await chromium.launch();
    const p = await b.newPage({ viewport: { width: 1200, height: 630 } });
    await p.setContent(`<!doctype html><html dir="rtl"><head><link href="https://fonts.googleapis.com/css2?family=IBM+Plex+Sans+Hebrew:wght@400;600&family=Noto+Sans+Hebrew:wght@200;300&display=swap" rel="stylesheet">
<style>html,body{margin:0;width:1200px;height:630px;overflow:hidden;font-family:'IBM Plex Sans Hebrew';background:#fff}
.tile{position:absolute;left:96px;top:115px;width:400px;height:400px;border-radius:96px;background:#3D35E0;overflow:hidden}
canvas{width:400px;height:400px;display:block}
h1{position:absolute;right:92px;top:168px;margin:0;color:#0B0B0C;font-family:'Noto Sans Hebrew';font-weight:200;font-size:86px;line-height:1.06;letter-spacing:-2px}
h1 b{font-weight:300;color:#3D35E0}
p{position:absolute;right:94px;top:384px;margin:0;color:#5C5C61;font-size:26px}
.n{position:absolute;right:92px;bottom:74px;color:#0B0B0C;font-size:30px;font-weight:600;letter-spacing:-.5px}</style></head>
<body><div class="tile"><canvas id="c" width="800" height="800"></canvas></div><h1>כל הפרויקט.<br><b>במקום אחד.</b></h1><p>ווידג'ט לכל הפרויקטים, בחשבון הנכון.</p><div class="n">YDeck</div>
<script>${stingJs}</script><script>YDeckSting.draw(document.getElementById('c').getContext('2d'), 3, { ground: '#3D35E0', cx: 400, cy: 415, k: 1.15 });</script></body></html>`);
    await p.evaluate(() => document.fonts.ready);
    await p.waitForTimeout(400);
    await p.screenshot({ path: out, type: 'jpeg', quality: 90 });
    await b.close();
  }
  fs.copyFileSync(out, path.join(DIST, 'og.jpg'));
}

(async () => {
  fs.rmSync(DIST, { recursive: true, force: true });
  fs.mkdirSync(path.join(DIST, 'download'), { recursive: true });
  fs.copyFileSync(APK_SRC, path.join(DIST, 'download', APK_NAME));
  const apk = { sha256: sha256(APK_SRC), mb: (fs.statSync(APK_SRC).size / 1048576).toFixed(1) };
  fs.writeFileSync(path.join(DIST, '_headers'), `/*
  X-Frame-Options: DENY
  Referrer-Policy: strict-origin-when-cross-origin
  X-Content-Type-Options: nosniff
/download/*.apk
  Content-Type: application/vnd.android.package-archive
  Content-Disposition: attachment
/media/*
  Cache-Control: public, max-age=86400
`);
  media();
  await og();
  // The home: YDeck for the desktop, in English and Hebrew.
  require('./desktop.cjs').build({ DIST, ROOT, EMAIL, SITE_URL });
  // The other pages, in the same style: privacy, download, the Android app, 404.
  require('./pages.cjs').build({ DIST, EMAIL, SITE_URL, apk: { ...apk, version: VERSION, name: APK_NAME, released: RELEASED } });
  console.log('built', DIST, `(${SERVICE_COUNT} services)`);
  if (process.argv.includes('--deploy')) {
    execFileSync('"C:/Users/user/Desktop/YoniDev projects/yonidev-cloud/node_modules/.bin/wrangler"', [ 'pages', 'deploy', `"${DIST}"`, '--project-name', 'ydeck-app', '--branch', 'main', '--commit-dirty=true'],
      { stdio: 'inherit', shell: true, cwd: __dirname });
  }
})();
