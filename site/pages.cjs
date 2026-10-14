// The site's other pages in the brand's dark, quiet style (the home is desktop.cjs): the privacy policy (YDeck for
// Windows and for Android, Hebrew then English: Google Play links here), the download page, the Android app's page,
// and the 404. Called by build.cjs.
const fs = require('fs'), path = require('path');
const { CSS, RING, FAVICON, esc } = require('./desktop.cjs');

const DOC_CSS = `
.doc{padding:132px 0 96px}
.doc .wrap{max-width:760px}
.doc h1{font-size:clamp(40px,6vw,64px);text-align:start}
.doc .lead{margin:18px 0 0;text-align:start;max-width:none}
.doc .meta{margin-top:14px;font-size:14px;color:var(--mute)}
.doc .meta a{color:var(--indigo-t)}
.part{margin-top:72px}
.part>h2{margin:0 0 6px;font-weight:300;font-size:clamp(28px,3.4vw,38px);letter-spacing:-.02em}
.part>p.sub{margin:0;color:var(--mute);font-size:15px}
.sec{padding:28px 0;border-top:1px solid var(--line)}
.sec:first-of-type{margin-top:28px}
.sec h3{margin:0 0 10px;font-weight:500;font-size:19px;letter-spacing:-.01em}
.sec p,.sec li{color:var(--soft);font-size:16px;line-height:1.7}
.sec p{margin:0 0 12px}
.sec ul{margin:0 0 12px;padding-inline-start:20px}
.sec li{margin-bottom:8px}
.sec b{color:var(--ink);font-weight:500}
.sec a{color:var(--indigo-t);text-decoration:underline;text-underline-offset:3px}
.sec code{font-family:'IBM Plex Mono',monospace;font-size:14px;background:#17171B;border:1px solid var(--line);border-radius:6px;padding:1px 6px;direction:ltr;unicode-bidi:isolate}
.en{margin-top:96px;padding-top:24px;border-top:1px solid var(--line)}
.fine{margin-top:28px;font-size:13px;color:var(--faint)}
.dl{display:flex;gap:18px;align-items:center;flex-wrap:wrap;margin-top:36px}
.dl .size{font-size:14px;color:var(--mute)}
.hash{font-family:'IBM Plex Mono',monospace;font-size:13px;color:var(--soft);background:#141417;border:1px solid var(--line);border-radius:12px;padding:12px 14px;word-break:break-all;direction:ltr;text-align:left}
.steps{display:flex;flex-wrap:wrap;gap:8px;margin:6px 0 14px}
.steps span{font-size:14px;color:var(--ink);background:#17171B;border:1px solid var(--line);border-radius:99px;padding:5px 12px}
.center{min-height:70vh;display:flex;flex-direction:column;align-items:center;justify-content:center;text-align:center;gap:22px}
.center .ring{width:64px;height:64px}
`;

function shell({ lang, dir, title, desc, siteUrl, email, body, alt }) {
  const he = lang === 'he';
  return `<!doctype html><html lang="${lang}" dir="${dir}"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${esc(title)}</title>
<meta name="description" content="${esc(desc)}">
<meta property="og:title" content="${esc(title)}"><meta property="og:description" content="${esc(desc)}">
<meta property="og:type" content="website"><meta property="og:site_name" content="YDeck">
<meta property="og:image" content="${siteUrl}/og-${he ? 'he' : 'en'}.jpg">
<meta name="twitter:card" content="summary_large_image">
<meta name="theme-color" content="#0B0B0D"><meta name="color-scheme" content="dark">
<link rel="icon" href="${FAVICON}">
<link rel="preconnect" href="https://fonts.googleapis.com"><link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=IBM+Plex+Sans:wght@300;400;500&family=IBM+Plex+Sans+Hebrew:wght@300;400;500&family=IBM+Plex+Mono:wght@400&display=swap" rel="stylesheet">
<style>${CSS}${DOC_CSS}</style></head><body>
<header class="nav"><div class="wrap">
<a class="brand" href="${he ? '/he/' : '/'}">${RING(22)}YDeck</a>
<nav><a href="/privacy">${he ? 'פרטיות' : 'Privacy'}</a><a href="/android">${he ? 'אנדרואיד' : 'Android'}</a>${alt ? `<a href="${alt.href}">${alt.label}</a>` : ''}</nav>
<a class="pill" href="mailto:${email}?subject=${encodeURIComponent(he ? 'בטא של YDeck' : 'YDeck beta')}">${he ? 'הצטרפות לבטא' : 'Join the beta'}</a>
</div></header>
<main>${body}</main>
<footer><div class="wrap">
<a class="brand" href="${he ? '/he/' : '/'}">${RING(18)}YDeck</a>
<a href="/privacy">${he ? 'פרטיות' : 'Privacy'}</a><a href="/download">${he ? 'הורדה' : 'Download'}</a><a href="mailto:${email}">${email}</a>
</div></footer>
</body></html>`;
}

const sec = (h, html) => `<section class="sec"><h3>${h}</h3>${html}</section>`;
const steps = (...xs) => `<div class="steps">${xs.map(x => `<span>${x}</span>`).join('')}</div>`;

// ── privacy ──
function privacy(email) {
  const he = {
    windows: [
      ['מה נשמר במחשב', `<ul><li><b>הפרויקטים וההגדרות:</b> שמות, צבעים, לוגואים, קישורים, תיקיות ופקודות, בתיקייה <code>%APPDATA%\\YDeck</code>.</li><li><b>קבלות של סשנים ומפתחות מקומיים:</b> בתיקייה <code>%LOCALAPPDATA%\\YDeck</code>.</li><li><b>בתוך תיקיות הפרויקטים:</b> <code>.ydeck.json</code> (משותף דרך הריפו: רק תקציר, החלטות, קישורים, פקודות וגרסאות, אף פעם לא סודות) ו־<code>.ydeck.local.json</code> (אישי, ומוחרג מ־git).</li><li><b>נקודות חזרה של הקוד:</b> לפני סשן של סוכן, YDeck שומר את מצב הקוד כקומיט בתוך הריפו עצמו, תחת <code>refs/ydeck/</code>. הוא לא נוגע בענפים שלך, ו־git לא דוחף אותו לשרת אלא אם תבקש במפורש.</li></ul><p>הסרת התוכנה מוחקת את התיקיות של YDeck. הקבצים בתוך הפרויקטים נשארים, כי הם שלך.</p>`],
      ['מה יוצא מהמחשב', `<p><b>אייקונים של אתרים.</b> כדי להציג סמל לקישור שאין לו סמל מובנה, נשלח <b>רק שם האתר</b> (למשל <code>example.com</code>) לשירות האייקונים של Google ושל DuckDuckGo, ולפעמים לאתר עצמו. בלי הכתובת המלאה, בלי מיילים ובלי שמות פרויקטים.</p><p><b>Git.</b> YDeck מריץ את git המותקן אצלך על הריפו שלך. בדיקת קומיטים חדשים בשרת (כל 10 דקות, אפשר לכבות בהגדרות), ודחיפה ומשיכה כשאתה לוחץ, הולכות רק לשרתים שהריפו שלך כבר עובד מולם (למשל GitHub), עם ההרשאות שכבר יש לך ב־Git.</p><p><b>Claude Code.</b> כשאתה מבקש תקציר, הודעת קומיט, סיכום מעבר או מילוי פרטים, וגם בסוף סשן (סיכום מעבר, אפשר לכבות), YDeck מריץ את Claude Code שמותקן אצלך. Claude Code שולח ל־Anthropic את מה שנדרש למשימה (קבצים רלוונטיים, שינויים, הבקשה), תחת החשבון שלך ולפי <a href="https://www.anthropic.com/legal/privacy">מדיניות הפרטיות של Anthropic</a>. YDeck עצמו לא מקבל מזה כלום.</p><p><b>החיבור ל־Claude Code.</b> התוסף של YDeck ל־Claude Code שולח את מצב הסשן רק ל־YDeck שבמחשב שלך (<code>127.0.0.1</code>), עם מפתח שרק המחשב שלך מכיר. שום דבר מזה לא יוצא לאינטרנט.</p><p><b>Codex.</b> YDeck קורא את קבצי הסשנים ש־Codex שומר במחשב, לקריאה בלבד, ולא משנה בהם כלום.</p><p><b>פתיחת קישור.</b> קישור נפתח בדפדפן שלך, בפרופיל Chrome של הפרויקט. מה שקורה משם הוא בין הדפדפן לאתר.</p>`],
    ],
    android: [
      ['מה נשמר בטלפון', '<ul><li><b>הפרויקטים והזיכרון שלהם</b>, כפי שהמחשב שלח: שמות, צבעים, לוגואים, קישורים, כתובות המייל של החשבונות, ערכים להעתקה, ולכל פרויקט התקציר, הצעד הבא, הסשן האחרון, ההחלטות והגרסה האחרונה.</li><li><b>מפתח ההצמדה למחשב</b>, באזור של האפליקציה שלא נכלל בשום גיבוי.</li><li>אייקונים של אתרים, שנטענו פעם אחת ונשמרו כדי שהווידג\'ט לא יצטרך רשת.</li></ul><p>הסרת האפליקציה מוחקת את כל זה.</p>'],
      ['מה יוצא מהטלפון', '<p><b>בקשות לממסר.</b> כשהטלפון מחובר למחשב, הוא מבקש מהממסר את המצב האחרון שהמחשב השאיר (כשפותחים את האפליקציה, ופעם בשעה בשביל הווידג\'ט). ראו "החיבור בין הטלפון למחשב" למטה.</p><p><b>שמות אתרים, כדי להביא אייקונים.</b> לקישור שאין לו סמל מובנה נשלח <b>רק שם האתר</b> (למשל <code>example.com</code>) אל שירות האייקונים של Google ושל DuckDuckGo, ולפעמים לאתר עצמו. לפרטים: <a href="https://policies.google.com/privacy">מדיניות הפרטיות של Google</a>, <a href="https://duckduckgo.com/privacy">מדיניות הפרטיות של DuckDuckGo</a>.</p><p><b>סריקת הקוד.</b> קודם נפתח הסורק של Google Play services, שמחזיר לאפליקציה רק את הטקסט שבקוד. אם הוא לא זמין, YDeck פותח סורק משלו שעובד כולו בטלפון. התמונה מהמצלמה לא נשמרת ולא יוצאת מהטלפון.</p><p><b>פתיחת קישור.</b> נגיעה בקישור פותחת אותו בדפדפן, ולשירותי גוגל עם הכתובת של החשבון של הפרויקט.</p>'],
      ['גיבוי', '<p>אם הגיבוי של הטלפון לחשבון גוגל פעיל, אנדרואיד כולל בו את קובץ הפרויקטים ואת הלוגואים. הגיבוי מנוהל ומוצפן על ידי אנדרואיד וחשבון הגוגל שלך, ואין לנו גישה אליו. מפתח ההצמדה לא נכלל בו: בטלפון חדש מתחברים שוב בסריקה.</p>'],
      ['הרשאות', '<ul><li><b>אינטרנט:</b> לממסר ולאייקונים של אתרים, כמתואר למעלה.</li><li><b>מצלמה:</b> רק לסורק המובנה, ורק אם הסורק של Google לא זמין. הבקשה מופיעה ברגע הסריקה, ואפשר לסרב.</li><li><b>ערוצי Chrome:</b> האפליקציה בודקת אם Chrome, Beta, Dev או Canary מותקנים, כדי לפתוח כל חשבון Vercel בערוץ שלו. לא נבדקות אפליקציות אחרות.</li></ul>'],
    ],
    link: [
      ['מה עובר', '<p>כשמחברים טלפון (במחשב: הגדרות ← טלפון), המחשב משאיר לטלפון את הפרויקטים, את הזיכרון שלהם ואת מה שהוא רואה בהם (מצב Git ושמות הקבצים ששונו, מצב הסוכן, הסשנים האחרונים) בתיבה על שרת ממסר קטן שלנו (Cloudflare Workers). הטלפון משאיר שם למחשב החלטה או צעד הבא שכתבת או הקלטת, והמחשב שומר אותם בקבצי הפרויקט ומוחק אותם מהממסר. ההקלטה עצמה עוברת לזיהוי הדיבור של הטלפון (Google), ורק הטקסט נשלח. עוברים רק נתונים. הטלפון לא מקבל שום דבר שהוא מריץ, והמחשב לא מריץ שום דבר שמגיע מהטלפון.</p>'],
      ['הצפנה מקצה לקצה', '<p>המפתח נוצר במחשב ועובר לטלפון רק בקוד ה־QR שעל המסך. הוא אף פעם לא עובר דרך השרת. כל מה שבתיבה מוצפן ב־AES-256-GCM לפני שהוא יוצא מהמחשב, והשרת רואה רק בתים מוצפנים, מזהה תיבה ואסימון גישה (נשמר אצלו רק כ־hash). הוא לא יכול לקרוא את התוכן ולא לזייף אותו.</p>'],
      ['מה השרת כן רואה', '<p>כמו כל שרת: כתובות IP, את גודל התיבה ואת זמני הכתיבה והקריאה (הזמן האחרון שבו הטלפון קרא מוצג במחשב). אין חשבונות משתמש ואין שמות.</p>'],
      ['ניתוק ומחיקה', '<p>"ניתוק טלפון" במחשב מוחק את התיבה מהשרת ומחליף את המפתח. תיבה שאף אחד לא נגע בה 60 יום נמחקת מעצמה. במחשב אין שום פתח: שני הצדדים רק יוצאים החוצה לשרת.</p>'],
    ],
    end: [
      ['ילדים', '<p>YDeck מיועד למפתחים ולא לילדים, ולא אוסף ביודעין מידע מילדים.</p>'],
      ['שינויים ויצירת קשר', `<p>אם המדיניות תשתנה, הגרסה המעודכנת תופיע בעמוד הזה עם תאריך חדש. שאלות: <a href="mailto:${email}">${email}</a>.</p>`],
    ],
  };
  const en = {
    windows: [
      ['What is stored on your computer', `<ul><li><b>Projects and settings:</b> names, colours, logos, links, folders and commands, in <code>%APPDATA%\\YDeck</code>.</li><li><b>Session receipts and local keys:</b> in <code>%LOCALAPPDATA%\\YDeck</code>.</li><li><b>In your project folders:</b> <code>.ydeck.json</code> (shared through the repository: only a summary, decisions, links, commands and releases, never secrets) and <code>.ydeck.local.json</code> (personal, kept out of git).</li><li><b>Restore points of your code:</b> before an agent's session, YDeck keeps the code as it was as a commit inside the repository itself, under <code>refs/ydeck/</code>. It never touches your branches, and git doesn't push it unless you ask it to.</li></ul><p>Uninstalling removes YDeck's folders. The files inside your projects stay; they're yours.</p>`],
      ['What leaves your computer', `<p><b>Website icons.</b> To show a mark for a link without a built-in one, only the <b>domain name</b> (e.g. <code>example.com</code>) is sent to Google's and DuckDuckGo's icon services, and sometimes to the site itself. Never the full link, emails or project names.</p><p><b>Git.</b> YDeck runs your installed git on your repositories. Checking the server for new commits (every 10 minutes, can be turned off in Settings), and push and pull when you click, go only to the servers your repositories already use (such as GitHub), with the credentials Git already has.</p><p><b>Claude Code.</b> When you ask for a summary, a commit message, a handoff or to fill in a project, and when a session ends (a handoff, can be turned off), YDeck runs the Claude Code installed on your computer. Claude Code sends Anthropic what the task needs (relevant files, changes, the request), under your own account and <a href="https://www.anthropic.com/legal/privacy">Anthropic's Privacy Policy</a>. YDeck itself receives none of it.</p><p><b>The Claude Code link.</b> YDeck's plugin for Claude Code sends a session's state only to YDeck on your computer (<code>127.0.0.1</code>), with a key only your computer knows. None of it goes to the internet.</p><p><b>Codex.</b> YDeck reads the session files Codex keeps on your computer, read-only, and never changes them.</p><p><b>Opening a link.</b> Links open in your browser, in the project's Chrome profile. From there it is between your browser and the site.</p>`],
    ],
    android: [
      ['What is stored on your phone', '<ul><li><b>The projects and their memory</b>, as your computer sent them: names, colours, logos, links, the email addresses of their accounts, values to copy, and for each project its summary, next step, last session, decisions and latest release.</li><li><b>The pairing key</b> to your computer, in the part of the app\'s storage that no backup includes.</li><li>Website icons, fetched once and cached so the widget never needs the network.</li></ul><p>Uninstalling the app deletes all of it.</p>'],
      ['What leaves your phone', '<p><b>Requests to the relay.</b> When paired, the phone asks the relay for the latest state your computer left (when you open the app, and once an hour for the widget). See "The link between phone and computer" below.</p><p><b>Website names, to fetch icons.</b> For a link without a built-in mark, <b>only the domain name</b> (e.g. <code>example.com</code>) is sent to Google\'s and DuckDuckGo\'s icon services, and sometimes to the site itself. See the <a href="https://policies.google.com/privacy">Google Privacy Policy</a> and the <a href="https://duckduckgo.com/privacy">DuckDuckGo Privacy Policy</a>.</p><p><b>Scanning the code.</b> Google Play services\' scanner opens first and hands the app only the text in the code. If it isn\'t available, YDeck opens its own scanner, which runs entirely on the phone. Camera images are never stored and never leave the phone.</p><p><b>Opening a link.</b> Tapping a link opens it in your browser; for Google services, with the address of the project\'s account.</p>'],
      ['Backup', '<p>If your phone\'s backup to your Google account is on, Android includes the projects file and the logos. This backup is run and encrypted by Android and your Google account; we cannot access it. The pairing key is not included: on a new phone you pair again by scanning.</p>'],
      ['Permissions', '<ul><li><b>Internet:</b> for the relay and website icons, as described above.</li><li><b>Camera:</b> only for the built-in scanner, and only if Google\'s scanner isn\'t available. It is asked for at the moment of scanning, and you can decline.</li><li><b>Chrome channels:</b> the app checks whether Chrome, Beta, Dev or Canary is installed, to open each Vercel account in its own channel. No other apps are checked.</li></ul>'],
    ],
    link: [
      ['What goes through', '<p>When you pair a phone (on the computer: Settings → Phone), the computer leaves the projects, their memory and what it sees in them (the Git state and names of changed files, the agent\'s state, recent sessions) for the phone in a box on a small relay server of ours (Cloudflare Workers). The phone leaves a decision or a next step you typed or spoke for the computer there, and the computer stores it in the project\'s files and deletes it from the relay. Speech goes to the phone\'s own speech recognition (Google); only the text is sent. Only data goes through. The phone receives nothing it runs, and the computer never runs anything that comes from the phone.</p>'],
      ['End-to-end encryption', '<p>The key is made on the computer and reaches the phone only through the QR code on the screen; it never passes through the server. Everything in the box is encrypted with AES-256-GCM before it leaves the computer, and the server sees only encrypted bytes, a box id and an access token (kept only as a hash). It can neither read the contents nor forge them.</p>'],
      ['What the server does see', '<p>As any server: IP addresses, the size of the box, and when it was written and read (the last time the phone read is shown on the computer). There are no user accounts and no names.</p>'],
      ['Unpairing and deletion', '<p>"Unpair phone" on the computer deletes the box from the server and replaces the key. A box nobody touches for 60 days deletes itself. Nothing on the computer listens for connections: both sides only reach out to the server.</p>'],
    ],
    end: [
      ['Children', '<p>YDeck is made for developers, not children, and does not knowingly collect data from children.</p>'],
      ['Changes and contact', `<p>If this policy changes, the new version will appear on this page with a new date. Questions: <a href="mailto:${email}">${email}</a>.</p>`],
    ],
  };
  const part = (h, sub, list) => `<div class="part"><h2>${h}</h2>${sub ? `<p class="sub">${sub}</p>` : ''}${list.map(([a, b]) => sec(a, b)).join('')}</div>`;
  return `<div class="doc"><div class="wrap">
<h1>מדיניות פרטיות</h1>
<p class="lead">ל־YDeck אין חשבון משתמש, אין פרסומות ואין אנליטיקס. מה שלך נשאר אצלך. השרת היחיד הוא ממסר בין הטלפון למחשב, וכל מה שעובר בו מוצפן במפתח שרק לך יש.</p>
<p class="meta">בתוקף מ־13 באוקטובר 2026 · <a href="#en">English</a></p>
${part('YDeck ל־Windows', 'התוכנה למחשב.', he.windows)}
${part('YDeck לאנדרואיד', 'הזיכרון של הפרויקטים, בטלפון.', he.android)}
${part('החיבור בין הטלפון למחשב', 'רק אם מחברים טלפון.', he.link)}
${part('כללי', '', he.end)}
<div class="en" id="en" dir="ltr" lang="en">
<h1>Privacy Policy</h1>
<p class="lead">YDeck has no user accounts, no ads and no analytics. What's yours stays with you. The only server is a relay between your phone and your computer, and everything that passes through it is encrypted with a key only you have.</p>
<p class="meta">Effective October 13, 2026 · Developer: YoniDev</p>
${part('YDeck for Windows', 'The desktop app.', en.windows)}
${part('YDeck for Android', 'The projects\' memory, on the phone.', en.android)}
${part('The link between phone and computer', 'Only if you pair a phone.', en.link)}
${part('General', '', en.end)}
<p class="fine">YDeck · Privacy Policy · Effective October 13, 2026 · YoniDev · ${email}</p>
</div>
</div></div>`;
}

// ── download ──
function download(apk) {
  return `<div class="doc"><div class="wrap">
<h1>הורדה</h1>
<p class="lead">YDeck ל־Windows בבטא סגורה. YDeck לאנדרואיד זמין כאן להורדה.</p>
<div class="part"><h2>YDeck ל־Windows</h2><p class="sub">בטא סגורה. רוצה להיות מהראשונים? כתוב לנו.</p>
<div class="dl"><a class="pill big" href="mailto:${apk.email}?subject=${encodeURIComponent('בטא של YDeck')}">הצטרפות לבטא</a></div></div>
<div class="part"><h2>YDeck לאנדרואיד</h2><p class="sub">גרסה ${apk.version} · לאנדרואיד 12 ומעלה · חינם, בלי חשבון.</p>
<div class="dl"><a class="pill big" href="/download/${apk.name}" download>הורדת YDeck ${apk.version}</a><span class="size">APK · ${apk.mb} MB · ${apk.released}</span></div>
${sec('מתקינים', `<p>פותחים את הקובץ שירד ומאשרים התקנה. בפעם הראשונה אנדרואיד מבקש לאשר התקנות מהדפדפן:</p>${steps('פתיחת הקובץ', 'הגדרות', 'לאפשר מהמקור הזה', 'התקנה')}<p>את הווידג'ט מוסיפים מהאפליקציה, או בלחיצה ארוכה על מסך הבית:</p>${steps('לחיצה ארוכה על מסך הבית', 'ווידג\'טים', 'YDeck')}`)}
${sec('למה מופיעה אזהרה?', '<p>אנדרואיד מזהיר על כל אפליקציה שלא מגיעה מ־Google Play. זה לא אומר שמשהו לא בסדר. YDeck בדרך לחנות.</p>')}
${sec('איך יודעים שהקובץ אמיתי?', `<p>טביעת האצבע של הקובץ (SHA-256), להשוואה:</p><div class="hash">${apk.sha256}</div>`)}
</div></div></div>`;
}

// ── the Android app ──
function android() {
  return `<section class="hero" style="min-height:auto;padding-top:160px;padding-bottom:40px">
${RING(84)}
<div class="eyebrow">YDeck לאנדרואיד</div>
<h1 style="font-size:clamp(40px,7vw,84px)">כל פרויקט, נגיעה אחת.</h1>
<p class="lead">ווידג'ט למסך הבית: הקישורים של כל פרויקט, וכל נגיעה פותחת את הקונסולה בחשבון הנכון.</p>
<div class="ctas"><a class="pill big" href="/download">הורדה לאנדרואיד</a><a class="text-link" href="/he/">YDeck ל־Windows</a></div>
</section>
<section class="principles"><div class="wrap"><ol>
<li><b>החשבון הנכון.</b><span>לכל פרויקט שומרים את חשבון הגוגל שלו, וכל קונסולה נפתחת ישר בו.</span></li>
<li><b>בלי חשבון, בלי שרת.</b><span>הפרויקטים נשמרים בטלפון בלבד.</span></li>
<li><b>עם המחשב.</b><span>אותו קובץ פרויקטים כמו YDeck ל־Windows: מייצאים מכאן, מייבאים שם.</span></li>
</ol></div></section>`;
}

function build({ DIST, EMAIL, SITE_URL, apk }) {
  const write = (file, html) => { const out = path.join(DIST, file); fs.mkdirSync(path.dirname(out), { recursive: true }); fs.writeFileSync(out, html); };
  const he = (file, title, desc, body) => write(file, shell({ lang: 'he', dir: 'rtl', title, desc, siteUrl: SITE_URL, email: EMAIL, body }));
  he('privacy/index.html', 'מדיניות פרטיות · YDeck', 'מה YDeck שומר, מה יוצא מהמחשב ומהטלפון, ומה לא. בעברית ובאנגלית.', privacy(EMAIL));
  he('download/index.html', 'הורדה · YDeck', `YDeck ${apk.version} לאנדרואיד, והבטא של YDeck ל־Windows.`, download({ ...apk, email: EMAIL }));
  he('android/index.html', 'YDeck לאנדרואיד', 'ווידג\'ט לאנדרואיד שמחזיק את הקישורים של כל פרויקט, וכל נגיעה פותחת בחשבון הנכון.', android());
  he('404.html', 'לא נמצא · YDeck', 'העמוד לא נמצא.', `<div class="center">${RING(64)}<h1 style="font-size:clamp(36px,6vw,64px)">אין כאן כלום.</h1><a class="text-link" href="/he/">לעמוד הבית</a></div>`);
}

module.exports = { build };
