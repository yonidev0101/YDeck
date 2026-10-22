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
<a class="pill" href="${he ? '/he/download' : '/download'}">${he ? 'הורדה' : 'Download'}</a>
</div></header>
<main>${body}</main>
<footer><div class="wrap">
<a class="brand" href="${he ? '/he/' : '/'}">${RING(18)}YDeck</a>
<a href="/privacy">${he ? 'פרטיות' : 'Privacy'}</a><a href="${he ? '/he/download' : '/download'}">${he ? 'הורדה' : 'Download'}</a><a href="/code-signing">Code signing policy</a><a href="https://github.com/yonidev0101/YDeck">GitHub</a><a href="mailto:${email}">${email}</a>
</div></footer>
${require('./stats.cjs')}
</body></html>`;
}

const sec = (h, html) => `<section class="sec"><h3>${h}</h3>${html}</section>`;
const steps = (...xs) => `<div class="steps">${xs.map(x => `<span>${x}</span>`).join('')}</div>`;

// ── privacy ──
function privacy(email) {
  const he = {
    windows: [
      ['מה נשמר במחשב', `<ul><li><b>הפרויקטים וההגדרות:</b> שמות, צבעים, לוגואים, קישורים, תיקיות ופקודות, בתיקייה <code>%APPDATA%\\YDeck</code>.</li><li><b>קבלות של סשנים ומפתחות מקומיים:</b> בתיקייה <code>%LOCALAPPDATA%\\YDeck</code>.</li><li><b>בתוך תיקיות הפרויקטים:</b> <code>.ydeck.json</code> (משותף דרך הריפו: רק תקציר, החלטות, קישורים, פקודות וגרסאות, אף פעם לא סודות) ו־<code>.ydeck.local.json</code> (אישי, ומוחרג מ־git).</li><li><b>נקודות חזרה של הקוד:</b> לפני סשן של סוכן, YDeck שומר את מצב הקוד כקומיט בתוך הריפו עצמו, תחת <code>refs/ydeck/</code>. הוא לא נוגע בענפים שלך, ו־git לא דוחף אותו לשרת אלא אם תבקש במפורש.</li></ul><p>הסרת התוכנה מוחקת את התיקיות של YDeck. הקבצים בתוך הפרויקטים נשארים, כי הם שלך.</p>`],
      ['מה יוצא מהמחשב', `<p><b>בדיקת עדכונים.</b> כל כמה שעות YDeck שואל את GitHub מה הגרסה האחרונה, ואם יש חדשה הוא מציע לעדכן. זו בקשה רגילה ל־GitHub, בלי שום מידע עליך או על הפרויקטים (GitHub רואה את כתובת ה־IP, כמו בכל הורדה). אפשר לכבות בהגדרות ← כללי.</p><p><b>נתוני שימוש אנונימיים, אם השארת אותם פועלים.</b> YDeck מציג את הבחירה הזאת בפתיחה הראשונה, ואפשר לכבות אותה שם או בכל רגע בהגדרות ← כללי. כשהיא פועלת, פעם ביום נשלחים לשרת של YDeck (ב־Cloudflare): מזהה אקראי שנוצר במחשב ולא קשור לשום פרט שלך, התאריך, הגרסה, השפה, כמה פרויקטים יש (כטווח: 1, 2–5, 6–15), אם Claude Code מחובר, ובאילו חלקים השתמשת באותו יום (למשל ״עכשיו״, קבלות, סגירת יום). וגם פעם אחת בהתקנה ופעם אחת בהסרה. <b>אף פעם לא</b> שמות של פרויקטים, נתיבים, קוד, קישורים, בקשות ל־Claude, כתובות מייל או כתובת IP שנשמרת. הקוד של מה שנשלח ושל השרת פתוח ב־GitHub.</p><p><b>אייקונים של אתרים.</b> כדי להציג סמל לקישור שאין לו סמל מובנה, נשלח <b>רק שם האתר</b> (למשל <code>example.com</code>) לשירות האייקונים של Google ושל DuckDuckGo, ולפעמים לאתר עצמו. בלי הכתובת המלאה, בלי מיילים ובלי שמות פרויקטים. אפשר לכבות את זה בהגדרות (״סמלים של אתרים״).</p><p><b>Git.</b> YDeck מריץ את git המותקן אצלך על הריפו שלך. בדיקת קומיטים חדשים בשרת (כל 10 דקות, אפשר לכבות בהגדרות), ודחיפה ומשיכה כשאתה לוחץ, הולכות רק לשרתים שהריפו שלך כבר עובד מולם (למשל GitHub), עם ההרשאות שכבר יש לך ב־Git.</p><p><b>Claude Code.</b> כשאתה מבקש תקציר, הודעת קומיט, סיכום מעבר או מילוי פרטים, וגם בסוף סשן (סיכום מעבר, אפשר לכבות), YDeck מריץ את Claude Code שמותקן אצלך. Claude Code שולח ל־Anthropic את מה שנדרש למשימה (קבצים רלוונטיים, שינויים, הבקשה), תחת החשבון שלך ולפי <a href="https://www.anthropic.com/legal/privacy">מדיניות הפרטיות של Anthropic</a>. YDeck עצמו לא מקבל מזה כלום.</p><p><b>החיבור ל־Claude Code.</b> התוסף של YDeck ל־Claude Code שולח את מצב הסשן, כולל הטקסט של הבקשה, רק ל־YDeck שבמחשב שלך (<code>127.0.0.1</code>), עם מפתח שרק המחשב שלך מכיר. שום דבר מזה לא יוצא לאינטרנט. YDeck שומר במחשב שלך את הבקשות ואת הקבצים שהשתנו כ״קבלות״ של סשנים. בתחילת סשן התוסף נותן ל־Claude את ההקשר של הפרויקט; את הטקסט המדויק רואים בהגדרות ← Claude Code.</p><p><b>Codex.</b> YDeck קורא את קבצי הסשנים ש־Codex שומר במחשב, לקריאה בלבד, ולא משנה בהם כלום.</p><p><b>פתיחת קישור.</b> קישור נפתח בדפדפן שלך, בפרופיל Chrome של הפרויקט. מה שקורה משם הוא בין הדפדפן לאתר.</p>`],
    ],
    android: [
      ['מה נשמר בטלפון', '<ul><li><b>הפרויקטים והזיכרון שלהם</b>, כפי שהמחשב שלח: שמות, צבעים, לוגואים, קישורים, כתובות המייל של החשבונות, ערכים להעתקה, ולכל פרויקט התקציר, הצעד הבא, הסשן האחרון, ההחלטות והגרסה האחרונה.</li><li><b>מפתח ההצמדה למחשב</b>, באזור של האפליקציה שלא נכלל בשום גיבוי.</li><li>אייקונים של אתרים, שנטענו פעם אחת ונשמרו כדי שהווידג\'ט לא יצטרך רשת.</li></ul><p>הסרת האפליקציה מוחקת את כל זה.</p>'],
      ['האתר הזה', '<p>האתר סופר כמה פעמים נצפה כל עמוד, כמה לחצו על הורדה, ומאיזה אתר הגיעו (רק שם האתר, למשל whatsapp.com). בלי עוגיות, בלי שמירה בדפדפן, ובלי כתובת IP שנשמרת.</p>'],
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
      ['What leaves your computer', `<p><b>Update checks.</b> Every few hours YDeck asks GitHub for the latest version and offers a newer one. It's a plain request to GitHub with nothing about you or your projects (GitHub sees the IP address, as with any download). Off in Settings → General.</p><p><b>Anonymous usage counts, if you leave them on.</b> YDeck shows this choice on the first run, and it can be turned off there or any time in Settings → General. When it's on, once a day YDeck's server (on Cloudflare) receives: a random id made on your computer and tied to nothing about you, the date, the version, the language, how many projects there are (as a range: 1, 2–5, 6–15), whether Claude Code is connected, and which parts you used that day (such as Now, receipts, closing the day); plus once on install and once on uninstall. <b>Never</b> project names, paths, code, links, prompts, email addresses, or a stored IP address. The code that sends it and the server's code are open on GitHub.</p><p><b>Website icons.</b> To show a mark for a link without a built-in one, only the <b>domain name</b> (e.g. <code>example.com</code>) is sent to Google's and DuckDuckGo's icon services, and sometimes to the site itself. Never the full link, emails or project names. This can be turned off in Settings ("Website icons").</p><p><b>Git.</b> YDeck runs your installed git on your repositories. Checking the server for new commits (every 10 minutes, can be turned off in Settings), and push and pull when you click, go only to the servers your repositories already use (such as GitHub), with the credentials Git already has.</p><p><b>Claude Code.</b> When you ask for a summary, a commit message, a handoff or to fill in a project, and when a session ends (a handoff, can be turned off), YDeck runs the Claude Code installed on your computer. Claude Code sends Anthropic what the task needs (relevant files, changes, the request), under your own account and <a href="https://www.anthropic.com/legal/privacy">Anthropic's Privacy Policy</a>. YDeck itself receives none of it.</p><p><b>The Claude Code link.</b> YDeck's plugin for Claude Code sends a session's state, including the text of your prompt, only to YDeck on your computer (<code>127.0.0.1</code>), with a key only your computer knows. None of it goes to the internet. YDeck keeps the prompts and the files changed as session receipts on your computer. At a session's start the plugin gives Claude the project's context; the exact text is in Settings → Claude Code.</p><p><b>Codex.</b> YDeck reads the session files Codex keeps on your computer, read-only, and never changes them.</p><p><b>Opening a link.</b> Links open in your browser, in the project's Chrome profile. From there it is between your browser and the site.</p>`],
    ],
    android: [
      ['What is stored on your phone', '<ul><li><b>The projects and their memory</b>, as your computer sent them: names, colours, logos, links, the email addresses of their accounts, values to copy, and for each project its summary, next step, last session, decisions and latest release.</li><li><b>The pairing key</b> to your computer, in the part of the app\'s storage that no backup includes.</li><li>Website icons, fetched once and cached so the widget never needs the network.</li></ul><p>Uninstalling the app deletes all of it.</p>'],
      ['This website', '<p>The site counts how often each page is seen, how many clicked a download, and which site sent the visitor (the site\x27s name only, such as whatsapp.com). No cookies, nothing stored in your browser, and no stored IP address.</p>'],
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
<p class="lead">ל־YDeck אין חשבון משתמש ואין פרסומות. הדבר היחיד שנספר הוא נתוני שימוש אנונימיים, שמוצגים לך בפתיחה ואפשר לכבות. מה שלך נשאר אצלך. השרת היחיד הוא ממסר בין הטלפון למחשב, וכל מה שעובר בו מוצפן במפתח שרק לך יש.</p>
<p class="meta">בתוקף מ־13 באוקטובר 2026 · <a href="#en">English</a></p>
${part('YDeck ל־Windows', 'התוכנה למחשב.', he.windows)}
${part('YDeck לאנדרואיד', 'הזיכרון של הפרויקטים, בטלפון.', he.android)}
${part('החיבור בין הטלפון למחשב', 'רק אם מחברים טלפון.', he.link)}
${part('כללי', '', he.end)}
<div class="en" id="en" dir="ltr" lang="en">
<h1>Privacy Policy</h1>
<p class="lead">YDeck has no user accounts and no ads. The only thing counted is anonymous usage, shown to you on the first run and easy to turn off. What's yours stays with you. The only server is a relay between your phone and your computer, and everything that passes through it is encrypted with a key only you have.</p>
<p class="meta">Effective October 13, 2026 · Developer: YoniDev</p>
${part('YDeck for Windows', 'The desktop app.', en.windows)}
${part('YDeck for Android', 'The projects\' memory, on the phone.', en.android)}
${part('The link between phone and computer', 'Only if you pair a phone.', en.link)}
${part('General', '', en.end)}
<p class="fine">YDeck · Privacy Policy · Effective October 13, 2026 · YoniDev · ${email}</p>
</div>
</div></div>`;
}

// ── Google Play: the closed test. Join the group, then become a tester; one link for the campaign, /beta. ──
const BETA_GROUP = 'https://groups.google.com/g/ydeck-testers';
const BETA_OPT_IN = 'https://play.google.com/apps/testing/com.yonidev.ydeck';
const PLAY_LISTING = 'https://play.google.com/store/apps/details?id=com.yonidev.ydeck';
const playHe = `<div class="part"><h2>YDeck לאנדרואיד</h2><p class="sub">בטא ב־Google Play · אנדרואיד 12 ומעלה · חינם, בלי חשבון. מתחבר ל־YDeck במחשב.</p>
<div class="dl"><a class="pill big" href="${BETA_GROUP}" target="_blank" rel="noopener">1. הצטרפות לקבוצת הבודקים</a><span class="size">"הצטרפות לקבוצה", עם החשבון שמחובר ל־Google Play בטלפון</span></div>
<div class="dl"><a class="pill big" href="${BETA_OPT_IN}" target="_blank" rel="noopener">2. התקנה מ־Google Play</a><span class="size">לוחצים "להיות בודק", ואז מתקינים מהחנות</span></div>
${sec('למה שני צעדים?', '<p>YDeck לאנדרואיד בבדיקה לפני שהיא נפתחת לכולם ב־Google Play. בשלב הזה Google מאפשרת להתקין רק למי שהצטרף לקבוצת הבודקים. כל מי שמצטרף ומשאיר אותה מותקנת שבועיים עוזר לה להגיע לחנות.</p>')}
${sec('כתוב "האפליקציה לא זמינה"', '<p>כמעט תמיד הצעד הראשון לא הושלם, או שנעשה בחשבון Google אחר מזה שבטלפון. מצטרפים לקבוצה עם אותו חשבון, מחכים כמה דקות ופותחים שוב את הקישור השני.</p>')}
${sec('מחברים למחשב', '<p>ב־YDeck במחשב: הגדרות ← טלפון, וסורקים את הקוד מהטלפון. מאז הפרויקטים, איפה כל אחד עומד והצעד הבא מגיעים לטלפון, מוצפנים מקצה לקצה.</p>')}
<p class="sub"><a href="${PLAY_LISTING}" target="_blank" rel="noopener">YDeck ב־Google Play</a> (נפתח אחרי שני הצעדים)</p>
</div>`;
const playEn = `<div class="part"><h2>YDeck for Android</h2><p class="sub">Beta on Google Play · Android 12 and later · free, no account. Pairs with YDeck on your computer.</p>
<div class="dl"><a class="pill big" href="${BETA_GROUP}" target="_blank" rel="noopener">1. Join the testers group</a><span class="size">"Join group", with the account your phone uses for Google Play</span></div>
<div class="dl"><a class="pill big" href="${BETA_OPT_IN}" target="_blank" rel="noopener">2. Install from Google Play</a><span class="size">Tap "Become a tester", then install from the store</span></div>
${sec('Why two steps?', '<p>YDeck for Android is in testing before it opens to everyone on Google Play. Until then, Google lets only members of the testers group install it. Everyone who joins and keeps it for two weeks helps it reach the store.</p>')}
${sec('It says "app not available"', '<p>Almost always the first step wasn’t finished, or was done with a different Google account from the one on the phone. Join the group with that account, wait a few minutes and open the second link again.</p>')}
${sec('Pair it with your computer', '<p>In YDeck on your computer: Settings → Phone, and scan the code with the phone. From then on your projects, where each one stands and the next step reach the phone, end-to-end encrypted.</p>')}
<p class="sub"><a href="${PLAY_LISTING}" target="_blank" rel="noopener">YDeck on Google Play</a> (opens after the two steps)</p>
</div>`;

// ── download ──
function download(apk) {
  return `<div class="doc"><div class="wrap">
<h1>הורדה</h1>
<p class="lead">YDeck ל־Windows ו־YDeck לאנדרואיד, חינם.</p>
<div class="part"><h2>YDeck ל־Windows</h2><p class="sub">קוד פתוח (MIT). כל גרסה נבנית ב־GitHub Actions מהקוד שבריפו, ועולה לדף ה־Releases.</p>
<div class="dl"><a class="pill big" href="https://github.com/yonidev0101/YDeck/releases/latest">הורדת הגרסה האחרונה</a><span class="size">MSI · Windows 10 ומעלה</span></div>
${sec('למה Windows מזהיר?', '<p>הקובץ עוד לא חתום דיגיטלית, אז SmartScreen מציג אזהרה בפעם הראשונה: לוחצים "מידע נוסף" ואז "הפעל בכל זאת". ראו <a href="/code-signing">Code signing policy</a>.</p>')}
${sec('הסרה', '<p>הגדרות Windows ← אפליקציות ← YDeck ← הסרה. ההסרה מחזירה את כל מה ש־YDeck הוסיף: התוסף ל־Claude Code, ״פתיחה ב־YDeck״ בתפריט, הלוגו על התיקיות וההפעלה עם Windows. הקוד שלך וקובצי ה־.ydeck בפרויקטים נשארים.</p>')}</div>
${playHe}
</div></div></div>`;
}

function downloadEn(apk) {
  return `<div class="doc"><div class="wrap">
<h1>Download</h1>
<p class="lead">YDeck for Windows and YDeck for Android, free.</p>
<div class="part"><h2>YDeck for Windows</h2><p class="sub">Open source (MIT). Every release is built by GitHub Actions from the code in the repository, and published on its Releases page.</p>
<div class="dl"><a class="pill big" href="https://github.com/yonidev0101/YDeck/releases/latest">Download the latest version</a><span class="size">MSI · Windows 10 and later</span></div>
${sec('Why does Windows warn?', '<p>The installer isn’t digitally signed yet, so SmartScreen warns the first time: click "More info", then "Run anyway". See the <a href="/code-signing">Code signing policy</a>.</p>')}
${sec('Uninstalling', '<p>Windows Settings → Apps → YDeck → Uninstall. It takes back everything YDeck added: the Claude Code plugin, "Open in YDeck" on the menu, the logo on folders and starting with Windows. Your code and your projects’ .ydeck files stay.</p>')}
${sec('The source', '<p><a href="https://github.com/yonidev0101/YDeck">github.com/yonidev0101/YDeck</a></p>')}</div>
${playEn}
</div></div></div>`;
}

// ── the Android app ──
function android() {
  return `<section class="hero" style="min-height:auto;padding-top:160px;padding-bottom:40px">
${RING(84)}
<div class="eyebrow">YDeck לאנדרואיד</div>
<h1 style="font-size:clamp(40px,7vw,84px)">שום דבר לא נשאר פתוח, גם בדרך.</h1>
<p class="lead">הזיכרון של הפרויקטים, בטלפון: איפה כל פרויקט עומד, מה Claude עשה בסשן האחרון, ומה הצעד הבא.</p>
<div class="ctas"><a class="pill big" href="/beta">הצטרפות לבטא ב־Google Play</a><a class="text-link" href="/he/">YDeck ל־Windows</a></div>
</section>
<section class="principles"><div class="wrap"><ol>
<li><b>מה הבא.</b><span>הצעד הבא של כל פרויקט, ונקודה כתומה כשמשהו עוד פתוח.</span></li>
<li><b>כותבים מהדרך.</b><span>החלטה או צעד הבא שעלו לך מגיעים ל־YDeck במחשב.</span></li>
<li><b>מוצפן מקצה לקצה.</b><span>בלי חשבון. המפתח נמצא רק בקוד שסרקת מהמחשב.</span></li>
</ol></div></section>`;
}

// ── code signing policy ──
function codeSigning(email) {
  return `<div class="doc"><div class="wrap" dir="ltr" lang="en">
<h1>Code signing policy</h1>
<p class="lead">How YDeck for Windows is built and released, and why it isn’t signed yet.</p>
<p class="meta">YDeck · open source under the MIT License · <a href="https://github.com/yonidev0101/YDeck">github.com/yonidev0101/YDeck</a></p>
${sec('Signing', '<p><b>Not signed yet.</b> The Windows installer (<code>YDeck-Windows-&lt;version&gt;.msi</code>) is not code-signed for now, so Windows SmartScreen warns the first time it runs: click “More info”, then “Run anyway”. Signing will come once the project can have it; this page will say so.</p>')}
${sec('Where to get it', '<p>Only from the <a href="https://github.com/yonidev0101/YDeck/releases">Releases</a> page of the public repository, or from this site’s <a href="/download">download page</a>, which points there. Third-party libraries and the bundled Java runtime are included as their maintainers publish them.</p>')}
${sec('How a release is built', '<p>Every release is built on GitHub Actions, on a clean Windows machine, from the tagged commit in the public repository (<a href="https://github.com/yonidev0101/YDeck/blob/main/.github/workflows/windows.yml">.github/workflows/windows.yml</a>), and published on the <a href="https://github.com/yonidev0101/YDeck/releases">Releases</a> page. Nothing built on a developer’s own computer is released, and every release is started by hand.</p>')}
${sec('Team and roles', '<p>YDeck is a one-person project.</p><ul><li><b>Committer and reviewer:</b> <a href="https://github.com/yonidev0101">Yoni (yonidev0101)</a></li><li><b>Approver:</b> <a href="https://github.com/yonidev0101">Yoni (yonidev0101)</a></li></ul><p>Changes proposed by anyone else (pull requests) are reviewed before they are merged. The account uses two-factor authentication.</p>')}
${sec('Privacy', '<p>YDeck has no accounts and no ads. The program does not transfer any information to other networked systems unless specifically requested by the user or the person installing or operating it, with two exceptions, both shown and both possible to turn off: for a link without a built-in mark, only the site’s domain name is sent to Google’s and DuckDuckGo’s icon services; and anonymous usage counts (the version, a range of how many projects, which parts were used; never names, paths, code or links), offered on the first run and off with one switch in Settings. The full policy, including the optional encrypted phone link: <a href="/privacy">Privacy Policy</a>.</p>')}
${sec('Contact', `<p>Questions or a report about a release: <a href="mailto:${email}">${email}</a> or <a href="https://github.com/yonidev0101/YDeck/issues">GitHub issues</a>.</p>`)}
</div></div>`;
}

function build({ DIST, EMAIL, SITE_URL, apk }) {
  const write = (file, html) => { const out = path.join(DIST, file); fs.mkdirSync(path.dirname(out), { recursive: true }); fs.writeFileSync(out, html); };
  const he = (file, title, desc, body) => write(file, shell({ lang: 'he', dir: 'rtl', title, desc, siteUrl: SITE_URL, email: EMAIL, body }));
  he('privacy/index.html', 'מדיניות פרטיות · YDeck', 'מה YDeck שומר, מה יוצא מהמחשב ומהטלפון, ומה לא. בעברית ובאנגלית.', privacy(EMAIL));
  write('download/index.html', shell({ lang: 'en', dir: 'ltr', title: 'Download · YDeck', desc: 'YDeck for Windows (open source), and YDeck for Android in beta on Google Play.', siteUrl: SITE_URL, email: EMAIL, body: downloadEn({ ...apk, email: EMAIL }), alt: { href: '/he/download', label: 'עברית' } }));
  write('he/download/index.html', shell({ lang: 'he', dir: 'rtl', title: 'הורדה · YDeck', desc: 'YDeck ל־Windows (קוד פתוח), ו־YDeck לאנדרואיד בבטא ב־Google Play.', siteUrl: SITE_URL, email: EMAIL, body: download({ ...apk, email: EMAIL }), alt: { href: '/download', label: 'English' } }));
  write('code-signing/index.html', shell({ lang: 'en', dir: 'ltr', title: 'Code signing policy · YDeck', desc: 'How YDeck for Windows is built and released, and why it isn’t signed yet.', siteUrl: SITE_URL, email: EMAIL, body: codeSigning(EMAIL) }));
  he('android/index.html', 'YDeck לאנדרואיד', 'הזיכרון של הפרויקטים, בטלפון: איפה כל פרויקט עומד ומה הבא. בטא ב־Google Play.', android());
  he('beta/index.html', 'בטא ב־Google Play · YDeck', 'להתקין את YDeck לאנדרואיד מ־Google Play: שני צעדים, דקה אחת.', `<div class="doc"><div class="wrap"><h1>YDeck ב־Google Play</h1><p class="lead">מתקינים מהחנות, בלי אזהרות. שני צעדים, דקה אחת, ומעכשיו העדכונים מגיעים לבד.</p>${playHe}</div></div>`);
  he('404.html', 'לא נמצא · YDeck', 'העמוד לא נמצא.', `<div class="center">${RING(64)}<h1 style="font-size:clamp(36px,6vw,64px)">אין כאן כלום.</h1><a class="text-link" href="/he/">לעמוד הבית</a></div>`);
}

module.exports = { build };
