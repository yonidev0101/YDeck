// The home of YDeck for the desktop, in English (/) and Hebrew (/he): the story of the demo film, told in the brand's dot.
// The dot is YDeck's mark for a Claude session (a ring when it's open, indigo at work, amber when it needs you, grey when
// it's gone, green when nothing is open), and the page is built of it at three sizes: in the text (a headline's full stop,
// a list's bullets, a kicker's state), beside it (the projects' dots, the dot that walks the real screens), and behind it
// (one huge dot, fixed, that takes each section's state as it scrolls by). Called by build.cjs.
const fs = require('fs'), path = require('path'), { execFileSync } = require('child_process');

// The twelve projects of the film, and the state each one's dot ends in (demo.html's ASTRAY): in the mess, and in order.
const FIELD = [['Orbit', '#0EA5E9', 'wait'], ['Pulse', '#FF5A36', 'wait'], ['Mira', '#DB2777', 'wait'], ['Nova', '#2A2A30', 'work'],
  ['Atlas', '#7C3AED', 'work'], ['Quill', '#F59E0B', 'work'], ['Juno', '#E11D48', 'work'], ['Rook', '#4F46E5', 'work'],
  ['Kite', '#16A34A', 'open'], ['Ledger', '#2563EB', 'gone'], ['Sable', '#475569', 'gone'], ['Fern', '#0D9488', 'gone']];
// where each one sits in the grid (shuffled, so the order is something YDeck makes), and how far it strays in the mess
const GRID = [5, 2, 9, 0, 7, 11, 4, 1, 10, 3, 8, 6];
const STRAY = [[-14, 22, -9], [18, -12, 7], [-22, -8, 12], [10, 18, -5], [-8, -20, 10], [20, 10, -12], [-16, 6, 8], [12, -18, -10],
  [-20, -14, 6], [16, 20, 14], [-10, 16, -14], [22, -6, 9]];
const WORKS = new Set(['Nova', 'Atlas', 'Mira', 'Orbit', 'Quill', 'Juno', 'Kite', 'Rook']);

const COPY = {
  en: {
    lang: 'en', dir: 'ltr', locale: 'en_US', home: '/', other: '/he', otherLabel: 'עברית',
    title: 'YDeck · Nothing left open.',
    desc: 'With AI you start project after project. YDeck remembers what each one is, where it stands and what is still open in it, and plugs into the Claude Code you already use.',
    nav: ['How it works', 'Privacy'], beta: 'Download for Windows',
    eyebrow: 'For developers who work with Claude Code',
    h1: 'Nothing left open.',
    lead: 'With AI you start project after project. YDeck remembers what each one is, where it stands, and what is still open in it.',
    how: 'See how it works',
    steps: [
      ['open', 'Open', 'With AI, a new project starts in half an hour.', 'There used to be two or three projects a year. Now every idea is a folder with a repo by the same evening.'],
      ['work', 'At work', 'So you work on everything at once.', 'Claude builds here, fixes there, and in another terminal it’s halfway through something. The code is written faster than anyone can follow.'],
      ['wait', 'Needs you', 'And then you no longer remember what’s going on where?', null,
        ['What was this project again?', 'Where did I stop, and what’s next?', 'Is it pushed, or only on my machine?', 'What’s missing in .env?', 'Is that server still running?']],
      ['note', 'YDeck', 'YDeck puts it in order.', 'Every project in one place, each with its dot: which is at work, which needs you, what’s open and what’s closed.'],
    ],
    states: { wait: 'Needs you', work: 'Claude is working', open: 'Claude is open', gone: 'Closed' },
    moments: [
      ['note', 'Memory', 'Every project remembers where it stopped.', 'What it is, what was decided, what the last session did and what comes next. Claude writes it down at the end of the work, and every new session starts from there.', 'orbit'],
      ['work', 'Receipts', 'What Claude did in the last session.', 'What you asked, and every file it touched, line by line. Keep it in one commit, or put it back exactly as it was.', 'orbit-session'],
      ['wait', 'Open loops', 'What’s still open, in every project.', 'Changes not saved, commits not pushed, a server still running, a key missing in .env, and Claude waiting for an answer. All in one list.', 'loops'],
      ['clean', 'The end of the day', 'Close the day in one click.', 'Claude commits what’s left, pushes, stops the servers and writes tomorrow’s next step. Then nothing is open.', 'loops-clear'],
    ],
    howK: 'How it works', howH: 'A small plugin for Claude Code. Everything else stays as it was.',
    hooks: [['open', 'Open', 'SessionStart', 'Claude gets the project’s memory.'], ['work', 'At work', 'UserPromptSubmit', 'You asked for something, and it’s on it.'],
      ['wait', 'Needs you', 'Notification', 'It stopped to ask.'], ['gone', 'Closed', 'SessionEnd', 'The session is over, and where it stopped is kept.']],
    facts: [['Claude writes the memory.', 'Where things stand and what’s next go to a personal file (.ydeck.local.json); decisions and links to one in the repo (.ydeck.json). The instructions are shown in Settings, and the repo file can be turned off.'],
      ['YDeck reads git itself.', 'What isn’t saved, what isn’t pushed, which key is missing. Nothing is sent anywhere.']],
    pr: [['On your machine.', 'Your code never leaves it. No account, no server of ours.'], ['No AI of its own.', 'It works with the agents you already use: Claude Code, and Codex through the sessions it writes.'], ['Never destructive.', 'Nothing it does throws work away. Everything can be undone.'], ['Free.', 'For every developer, on Windows. Mac is next.']],
    endA: 'Nothing left open.', endB: 'Free and open source, for Windows.',
    foot: ['Privacy', 'YDeck for Android', 'Download'],
  },
  he: {
    lang: 'he', dir: 'rtl', locale: 'he_IL', home: '/he/', other: '/', otherLabel: 'English',
    title: 'YDeck · שום דבר לא נשאר פתוח.',
    desc: 'עם AI פותחים עוד ועוד פרויקטים. YDeck זוכר מה כל אחד, איפה הוא עומד ומה עוד פתוח בו, ומתחבר ל־Claude Code שכבר יש לך.',
    nav: ['איך זה עובד', 'פרטיות'], beta: 'הורדה ל־Windows',
    eyebrow: 'למפתחים שעובדים עם Claude Code',
    h1: 'שום דבר לא נשאר פתוח.',
    lead: 'עם AI פותחים עוד ועוד פרויקטים. YDeck זוכר מה כל אחד, איפה הוא עומד, ומה עוד פתוח בו.',
    how: 'איך זה עובד',
    steps: [
      ['open', 'פתוח', 'עם AI, פרויקט חדש מתחיל בחצי שעה.', 'פעם היו שניים־שלושה פרויקטים בשנה. היום כל רעיון הופך לתיקייה עם ריפו, עוד באותו ערב.'],
      ['work', 'עובד', 'אז עובדים על הכול במקביל.', 'Claude בונה פה, מתקן שם, ובטרמינל אחר הוא באמצע משהו. הקוד נכתב מהר יותר משאפשר לעקוב.'],
      ['wait', 'מחכה לך', 'ואז כבר לא זוכרים מה קורה איפה?', null,
        ['מה הפרויקט הזה בכלל?', 'איפה עצרתי, ומה הצעד הבא?', 'זה עלה ל־git, או שנשאר רק אצלי?', 'מה חסר ב־env?', 'השרת הזה עוד רץ?']],
      ['note', 'YDeck', 'YDeck עושה סדר.', 'כל הפרויקטים במקום אחד, כל אחד עם הנקודה שלו: מי עובד, מי מחכה לך, מה פתוח ומה נסגר.'],
    ],
    states: { wait: 'מחכה לך', work: 'Claude עובד', open: 'Claude פתוח', gone: 'נסגר' },
    moments: [
      ['note', 'זיכרון', 'כל פרויקט זוכר איפה עצר.', 'מה הפרויקט, מה הוחלט, מה נעשה בסשן האחרון ומה הצעד הבא. Claude כותב את זה בעצמו בסוף העבודה, וכל סשן חדש מתחיל משם.', 'orbit'],
      ['work', 'קבלות', 'מה Claude עשה בסשן האחרון.', 'מה ביקשת, וכל קובץ שהוא נגע בו, שורה אחרי שורה. שומרים בקומיט אחד, או מחזירים בדיוק למה שהיה.', 'orbit-session'],
      ['wait', 'מה פתוח', 'מה נשאר פתוח, בכל הפרויקטים.', 'שינויים שלא נשמרו, קומיטים שלא נדחפו, שרת שעוד רץ, מפתח שחסר ב־.env, ו־Claude שמחכה לתשובה. הכול ברשימה אחת.', 'loops'],
      ['clean', 'סוף היום', 'סוגרים את היום בלחיצה.', 'Claude עושה קומיט למה שנשאר, דוחף, עוצר את השרתים וכותב את הצעד הבא למחר. אחר כך שום דבר לא פתוח.', 'loops-clear'],
    ],
    howK: 'איך זה עובד', howH: 'פלאגין קטן ל־Claude Code. כל השאר נשאר כמו שהיה.',
    hooks: [['open', 'פתוח', 'SessionStart', 'Claude מקבל את הזיכרון של הפרויקט.'], ['work', 'עובד', 'UserPromptSubmit', 'ביקשת משהו, והוא עובד על זה.'],
      ['wait', 'מחכה לך', 'Notification', 'הוא עצר לשאול.'], ['gone', 'נסגר', 'SessionEnd', 'הסשן נגמר, ומה שנעשה בו נשמר.']],
    facts: [['Claude כותב את הזיכרון.', 'איפה עומדים ומה הלאה נכתבים לקובץ אישי (.ydeck.local.json), והחלטות וקישורים לקובץ שבריפו (.ydeck.json). ההוראות גלויות בהגדרות, ואת הקובץ שבריפו אפשר לכבות.'],
      ['YDeck קורא את git בעצמו.', 'מה לא נשמר, מה לא נדחף, ואיזה מפתח חסר. שום דבר לא נשלח לשום מקום.']],
    pr: [['על המחשב שלך.', 'הקוד לא יוצא ממנו. בלי חשבון, בלי שרת שלנו.'], ['בלי AI משלו.', 'עובד עם הסוכנים שכבר יש לך: Claude Code, ו־Codex דרך הסשנים שהוא כותב.'], ['אף פעם לא הורס.', 'שום דבר שהוא עושה לא זורק עבודה. הכול אפשר לבטל.'], ['חינם.', 'לכל מפתח, ב־Windows. Mac בדרך.']],
    endA: 'שום דבר לא נשאר פתוח.', endB: 'חינם וקוד פתוח, ל־Windows.',
    foot: ['פרטיות', 'YDeck לאנדרואיד', 'הורדה'],
  },
};

const esc = s => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

// The mark: a ring with an opening and the amber dot in it (store/brand/logo). `live` plays the sting once:
// "thinking", an indigo arc that spins, slows, opens and settles into the mark, and the dot lands.
const RING = (size, live) => `<svg class="ring${live ? ' live' : ''}" viewBox="0 0 100 100" width="${size}" height="${size}" aria-hidden="true">` +
  `<g class="spin"><circle class="arc" cx="50" cy="50" r="36" pathLength="360" transform="rotate(-10 50 50)"/></g><circle class="dot" cx="77.6" cy="26.9" r="7.5"/></svg>`;
const MARK_SVG = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100"><rect width="100" height="100" rx="22" fill="#0B0B0D"/><g transform="translate(19 19) scale(0.62)"><circle cx="50" cy="50" r="36" fill="none" stroke="#F4F4F5" stroke-width="11" stroke-linecap="round" stroke-dasharray="188.5 37.7" transform="rotate(-10 50 50)"/><circle cx="77.6" cy="26.9" r="7.5" fill="#F5A524"/></g></svg>`;
const FAVICON = 'data:image/svg+xml,' + encodeURIComponent(MARK_SVG);

// A headline's last full stop becomes the amber dot; a question mark stays a mark, in amber.
const amber = h => h.replace(/([.?])(\s*)$/, '<span class="p">$1</span>$2');
// a dot in the text, in a state
const dt = st => `<i class="dt ${st}" aria-hidden="true"></i>`;

// The dot on each real screen: where it lands, in the screen's pixels (3600×2340, the Hebrew layout; the English one is
// mirrored), and in which state. The same keys as demo.html's GUIDE.
const GUIDE = {
  orbit: [[2754, 932, 'note'], [2659, 1535, 'wait'], [2659, 1737, 'wait'], [2659, 1937, 'wait']],
  loops: [[2659, 628, 'wait'], [2659, 828, 'wait'], [2659, 1028, 'wait'], [2659, 1460, 'wait'], [2659, 1652, 'work'], [2659, 1852, 'wait']],
  'loops-clear': [[1494, 470, 'clean']],
};
const STATE_COLOR = { note: '#F4F4F5', wait: '#F5A524', work: '#9C97FF', clean: '#34C77B', open: '#B9B9C2', gone: '#4E4E56' };
// Each walk loops: it lands on every key for its share of the time, then rests on the last.
function guideCss() {
  return Object.entries(GUIDE).map(([name, keys]) => ['he', 'en'].map(lang => {
    const id = `g-${lang}-${name}`, n = keys.length, at = k => (8 + k * 70 / Math.max(1, n - 1)).toFixed(1);
    const pos = ([x, y, st]) => `left:${((lang === 'en' ? 3600 - x : x) / 36).toFixed(2)}%;top:${(y / 23.4).toFixed(2)}%;background:${STATE_COLOR[st]}`;
    const frames = [`0%{${pos(keys[0])};opacity:0}`, `${at(0)}%{${pos(keys[0])};opacity:1}`,
      ...keys.slice(1).map((k, i) => `${at(i + 1)}%{${pos(k)};opacity:1}`), `96%{${pos(keys[n - 1])};opacity:1}`, `100%{${pos(keys[n - 1])};opacity:0}`];
    return `@keyframes ${id}{${frames.join('')}}html[lang=${lang}] .shot.in .gd.${name}{animation:${id} ${n > 1 ? 2.2 + n * 1.1 : 5}s .4s cubic-bezier(.6,0,.2,1) infinite}` +
      `html[lang=${lang}] .gd.${name}{${pos(keys[n - 1])}}`;
  }).join('')).join('');
}

// The dot behind, per section: [state, colour, hole (0 a full dot, .86 a thin ring), x from the inline start %, y %, size vmin, opacity].
// It keeps to the corners: the top one on the side away from the text, and in the moments the corner away from the screen.
const ORB = [
  ['hero', 'open', .86, 90, 20, 30, .32], ['open', 'open', .86, 92, 18, 26, .3], ['work', 'work', 0, 92, 18, 22, .36],
  ['wait', 'wait', 0, 92, 84, 22, .34], ['note', 'note', .86, 92, 18, 24, .2],
  ['m0', 'note', 0, 8, 86, 12, .22], ['m1', 'work', .8, 92, 16, 20, .34], ['m2', 'wait', 0, 8, 86, 16, .34], ['m3', 'clean', 0, 92, 16, 16, .34],
  ['gone', 'gone', .86, 92, 18, 24, .7], ['end', 'note', 0, 50, 88, 6, .18],
];

const CSS = `
:root{--night:#0B0B0D;--ink:#F4F4F5;--soft:#C8C8CE;--mute:#8B8B93;--faint:#5A5A62;--card:#131316;--line:#24242A;--amber:#F5A524;--indigo:#3D35E0;--indigo-t:#A7A3FF;--think:#6E66F8;--green:#34C77B;
  --s-open:#B9B9C2;--s-work:#9C97FF;--s-wait:#F5A524;--s-gone:#4E4E56;--s-clean:#34C77B;--s-note:#F4F4F5}
*{box-sizing:border-box}
html{background:var(--night);color:var(--ink);-webkit-font-smoothing:antialiased;text-rendering:optimizeLegibility;scroll-behavior:smooth}
body{margin:0;font-family:'IBM Plex Sans','IBM Plex Sans Hebrew',system-ui,sans-serif;font-size:17px;line-height:1.55;overflow-x:hidden}
html[lang=he] body{font-family:'IBM Plex Sans Hebrew','IBM Plex Sans',system-ui,sans-serif}
a{color:inherit;text-decoration:none}
.wrap{max-width:1120px;margin:0 auto;padding:0 28px}
.mono{font-family:'IBM Plex Mono',ui-monospace,monospace}
main{position:relative;z-index:1}
/* nav */
.nav{position:fixed;inset:0 0 auto;z-index:10;backdrop-filter:saturate(1.4) blur(18px);-webkit-backdrop-filter:saturate(1.4) blur(18px);background:rgba(11,11,13,.72);border-bottom:1px solid rgba(255,255,255,.06)}
.nav .wrap{display:flex;align-items:center;gap:28px;height:56px}
.brand{display:flex;align-items:center;gap:10px;font-weight:500;font-size:16px;letter-spacing:-.01em;flex:1}
.brand svg{width:22px;height:22px}
.nav nav{display:flex;gap:26px;font-size:14px;color:var(--mute)}
.nav nav a:hover{color:var(--ink)}
.pill{display:inline-flex;align-items:center;gap:8px;font-size:14px;font-weight:500;padding:8px 16px;border-radius:99px;background:var(--ink);color:var(--night);transition:opacity .2s}
.pill:hover{opacity:.86}
.pill.big{font-size:16px;padding:14px 26px;background:var(--indigo);color:#fff}
.text-link{font-size:16px;color:var(--indigo-t)}
.text-link:hover{color:#fff}
/* hero */
.hero{min-height:100vh;display:flex;flex-direction:column;align-items:center;justify-content:center;text-align:center;padding:120px 28px 80px}
.hero .ring{width:112px;height:112px;margin-bottom:44px}
.eyebrow{font-size:14px;color:var(--mute);margin-bottom:20px;letter-spacing:.01em}
h1{margin:0;font-weight:300;font-size:clamp(48px,9vw,112px);line-height:1.02;letter-spacing:-.045em}
html[lang=he] h1{letter-spacing:-.02em;line-height:1.08}
.lead{max-width:620px;margin:28px auto 0;font-size:clamp(18px,2.2vw,22px);line-height:1.5;color:var(--soft)}
.ctas{display:flex;gap:26px;align-items:center;justify-content:center;margin-top:40px;flex-wrap:wrap}
/* the mark */
.ring .arc{fill:none;stroke:var(--ink);stroke-width:11;stroke-linecap:round;stroke-dasharray:300 60}
.ring .dot{fill:var(--amber)}
.ring .spin{transform-box:view-box;transform-origin:50px 50px}
.ring.live .spin{animation:spin 1.75s .15s cubic-bezier(.33,1,.68,1) both}
.ring.live .arc{animation:think 2.2s linear both}
.ring.live .dot{transform-box:fill-box;transform-origin:center;animation:land .34s 1.85s cubic-bezier(.3,1.6,.5,1) both}
@keyframes spin{from{transform:rotate(-1080deg)}to{transform:rotate(0)}}
@keyframes think{0%{stroke-dasharray:70 290;stroke:var(--indigo);opacity:0}8%{opacity:1}43%{stroke-dasharray:70 290}57%{stroke:var(--indigo)}82%{stroke-dasharray:300 60;stroke:var(--ink)}100%{stroke-dasharray:300 60;stroke:var(--ink);opacity:1}}
@keyframes land{from{transform:scale(0)}to{transform:scale(1)}}
/* a headline's full stop is the amber dot */
.p{color:var(--amber)}

/* ── the dot, behind: one small dot, fixed in a corner, in the state of the section in view (body[data-dot]).
   Quiet on purpose: it moves corner to corner and changes state, and never sits behind what you read. ── */
.orb{position:fixed;z-index:0;pointer-events:none;left:var(--x);top:var(--y);width:var(--d);height:var(--d);transform:translate(-50%,-50%);opacity:var(--o);
  transition:left 1.6s cubic-bezier(.6,0,.2,1),right 1.6s cubic-bezier(.6,0,.2,1),top 1.6s cubic-bezier(.6,0,.2,1),width 1.4s cubic-bezier(.6,0,.2,1),height 1.4s cubic-bezier(.6,0,.2,1),opacity 1.2s}
html[dir=rtl] .orb{left:auto;right:var(--x);transform:translate(50%,-50%)}
.orb i{position:absolute;inset:0;border-radius:50%}
.orb .core{background:var(--c);transition:background 1.2s}
.orb .hole{background:var(--night);transform:scale(var(--h));transition:transform 1.4s cubic-bezier(.6,0,.2,1)}
body[data-dot=work] .orb .core{animation:breathe 3.4s ease-in-out infinite}
${ORB.map(([st, c, h, x, y, d, o]) => `body[data-dot=${st}] .orb{--c:var(--s-${c});--h:${h};--x:${x}%;--y:${y}%;--d:${d}vmin;--o:${o}}`).join('\n')}
@keyframes breathe{0%,100%{transform:scale(1)}50%{transform:scale(.965)}}

/* ── the dot, in the text ── */
.dt{display:inline-block;width:.5em;height:.5em;border-radius:50%;vertical-align:.06em;background:var(--c);flex:none}
.dt.open{--c:var(--s-open);background:none;box-shadow:inset 0 0 0 .14em var(--c)}
.dt.work{--c:var(--s-work)}.dt.wait{--c:var(--s-wait)}.dt.gone{--c:var(--s-gone)}.dt.clean{--c:var(--s-clean)}.dt.note{--c:var(--s-note)}
.kicker{display:flex;align-items:center;gap:10px;font-size:13px;color:var(--mute);letter-spacing:.06em;text-transform:uppercase;margin-bottom:18px}
html[lang=he] .kicker{letter-spacing:0;text-transform:none;font-size:14px}
.kicker .dt{font-size:20px}

/* ── the story: the projects' dots beside the text, sticky, through four steps ── */
.story{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr);gap:64px}
.steps{padding:10vh 0 20vh}
.step{min-height:88vh;display:flex;flex-direction:column;justify-content:center}
.step h2,.moment h2{margin:0;font-weight:300;font-size:clamp(34px,4.6vw,56px);line-height:1.08;letter-spacing:-.035em}
html[lang=he] .step h2,html[lang=he] .moment h2{letter-spacing:-.015em;line-height:1.15}
.step p,.moment p{margin:24px 0 0;font-size:19px;line-height:1.6;color:var(--soft);max-width:460px}
.step ul{list-style:none;margin:30px 0 0;padding:0;max-width:480px}
.step li{display:flex;align-items:center;gap:16px;font-size:clamp(18px,2vw,22px);font-weight:300;color:var(--soft);padding:14px 0;border-top:1px solid var(--line)}
.step li:last-child{border-bottom:1px solid var(--line)}
.step li .dt{font-size:22px}
.js .step li{opacity:0;transform:translateY(14px);transition:opacity .7s,transform .7s}
.step.in li{opacity:1;transform:none}
${[1, 2, 3, 4].map(i => `.step li:nth-child(${i + 1}){transition-delay:${i * .12}s}`).join('')}
.stage{position:sticky;top:0;height:100vh;display:flex;align-items:center;justify-content:center}
.field{position:relative;width:min(440px,100%);height:460px}
.fi{position:absolute;display:flex;align-items:center;gap:14px;inset-inline-start:var(--gx);top:var(--gy);margin-inline-start:-32px;margin-top:-32px;
  transition:inset-inline-start .9s cubic-bezier(.6,0,.2,1),top .9s cubic-bezier(.6,0,.2,1),margin .9s,transform .9s cubic-bezier(.6,0,.2,1),opacity .6s;transition-delay:calc(var(--i) * 28ms)}
.tile{position:relative;flex:none;width:64px;height:64px;border-radius:17px;background:var(--tc);display:grid;place-items:center;font-weight:500;font-size:26px;color:#fff;
  transition:width .9s cubic-bezier(.6,0,.2,1),height .9s cubic-bezier(.6,0,.2,1),border-radius .9s,font-size .9s,opacity .6s}
.tile.dark{box-shadow:inset 0 0 0 1.5px #3A3A42}
.bd{position:absolute;inset-inline-end:-6px;bottom:-6px;width:20px;height:20px;border-radius:50%;background:var(--night);display:grid;place-items:center;transition:width .9s,height .9s}
.bd::after{content:'';width:14px;height:14px;border-radius:50%;box-shadow:inset 0 0 0 4px var(--s-open);transition:box-shadow .45s,background .45s,width .9s,height .9s}
.txt{display:flex;flex-direction:column;line-height:1.25;white-space:nowrap;opacity:0;max-width:0;overflow:hidden;transition:opacity .5s,max-width .9s}
.txt b{font-weight:400;font-size:16px}
.txt small{font-size:13px;color:var(--mute)}
/* the field appears one project after another */
.js .field .fi{opacity:0;transform:scale(.6)}
.field.in .fi{opacity:1;transform:none}
/* at work: the dots fill indigo and breathe */
.field[data-step=work] .fi.w .bd::after,.field[data-step=wait] .fi.mw .bd::after{box-shadow:inset 0 0 0 7px var(--s-work);animation:pulse 1.6s ease-in-out calc(var(--i) * -.3s) infinite}
@keyframes pulse{50%{transform:scale(.8)}}
/* the mess: they stray, and turn amber, grey */
.field[data-step=wait] .fi{transform:translate(calc(var(--mx) * 1px),calc(var(--my) * 1px)) rotate(calc(var(--mr) * 1deg))}
.field[data-step=wait] .fi .tile{animation:stray 3.2s ease-in-out calc(var(--i) * -.4s) infinite}
@keyframes stray{50%{transform:translate(6px,-5px) rotate(4deg)}}
.field[data-step=wait] .fi.s-wait .bd::after,.field[data-step=note] .fi.s-wait .bd::after{box-shadow:inset 0 0 0 7px var(--s-wait)}
.field[data-step=wait] .fi.s-gone .bd::after,.field[data-step=note] .fi.s-gone .bd::after{box-shadow:inset 0 0 0 7px var(--s-gone)}
.field[data-step=wait] .fi.s-gone .tile{opacity:.4}
/* in order: a list, by state, like YDeck's sidebar */
.field[data-step=note] .fi{inset-inline-start:0;top:calc(var(--ly) * 38px + 20px);margin-inline-start:0;margin-top:-15px}
.field[data-step=note] .tile{width:30px;height:30px;border-radius:8px;font-size:14px}
.field[data-step=note] .bd{width:14px;height:14px;inset-inline-end:-4px;bottom:-4px}
.field[data-step=note] .bd::after{width:9px;height:9px}
.field[data-step=note] .fi.s-open .bd::after{box-shadow:inset 0 0 0 2.5px var(--s-open)}
.field[data-step=note] .fi.s-work .bd::after{box-shadow:inset 0 0 0 5px var(--s-work)}
.field[data-step=note] .txt{opacity:1;max-width:320px;transition-delay:.5s}
.field[data-step=note] .txt b{font-size:15px}

/* ── the moments: the real screens, with the dot walking them ── */
.moment{min-height:100vh;display:grid;grid-template-columns:minmax(0,.8fr) minmax(0,1.2fr);gap:64px;align-items:center;padding:10vh 0}
.moment:nth-of-type(even){grid-template-columns:minmax(0,1.2fr) minmax(0,.8fr)}
.moment:nth-of-type(even) .copy{order:2}
.shot{position:relative;border-radius:14px;overflow:hidden;border:1px solid rgba(255,255,255,.1);box-shadow:0 40px 120px rgba(0,0,0,.6);aspect-ratio:3600/2340;background:var(--card)}
.shot img{display:block;width:100%;height:100%;object-fit:cover}
.gd{position:absolute;width:1.5%;min-width:10px;aspect-ratio:1;border-radius:50%;transform:translate(-50%,-50%);box-shadow:0 0 0 4px var(--night),0 0 0 6px rgba(255,255,255,.14);opacity:0}
.shot.in .gd{opacity:1}
${guideCss()}

/* ── how it works: the legend of the dot ── */
.how{padding:18vh 0 12vh;text-align:center}
.how h2{margin:0 auto;font-weight:300;font-size:clamp(34px,5vw,60px);line-height:1.1;letter-spacing:-.035em;max-width:860px}
html[lang=he] .how h2{letter-spacing:-.015em;line-height:1.18}
.how .kicker{justify-content:center}
.legend{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:0;margin:80px auto 0;max-width:1000px;position:relative}
.legend::before{content:'';position:absolute;top:28px;inset-inline:12.5%;height:2px;background:linear-gradient(90deg,transparent,var(--line) 12%,var(--line) 88%,transparent)}
.legend div{position:relative;padding:0 14px}
.legend .dt{font-size:56px;display:block;margin:0 auto 26px;box-shadow:0 0 0 10px var(--night)}
.legend .dt.open{box-shadow:inset 0 0 0 .14em var(--c),0 0 0 10px var(--night)}
.legend b{display:block;font-weight:400;font-size:22px}
.legend code{display:block;font-family:'IBM Plex Mono',ui-monospace,monospace;font-size:13px;color:var(--mute);margin:6px 0 12px;direction:ltr}
.legend p{margin:0;font-size:15px;color:var(--soft);line-height:1.5}
.js .legend div{opacity:0;transform:translateY(18px);transition:opacity .8s,transform .8s}
.legend.in div{opacity:1;transform:none}
${[1, 2, 3].map(i => `.legend div:nth-child(${i + 1}){transition-delay:${i * .35}s}`).join('')}
.facts{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:56px;max-width:960px;margin:96px auto 0;text-align:start}
.facts b{display:block;font-weight:400;font-size:22px;margin-bottom:10px}
.facts span{font-size:16px;color:var(--soft);line-height:1.6}
/* principles */
.principles{padding:12vh 0 16vh}
.principles ol{list-style:none;margin:0;padding:0}
.principles li{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1.3fr);gap:40px;padding:34px 0;border-top:1px solid var(--line);align-items:baseline}
.principles li:last-child{border-bottom:1px solid var(--line)}
.principles b{font-weight:400;font-size:clamp(24px,3vw,34px);letter-spacing:-.02em}
.principles span{font-size:18px;color:var(--soft)}
/* end */
.end{min-height:90vh;display:flex;flex-direction:column;align-items:center;justify-content:center;padding:16vh 28px;text-align:center}
.end .ring{width:72px;height:72px;margin-bottom:36px}
.end h2{margin:0;font-weight:300;font-size:clamp(44px,8vw,96px);letter-spacing:-.045em;line-height:1.04}
html[lang=he] .end h2{letter-spacing:-.02em;line-height:1.12}
.end p{margin:22px 0 40px;color:var(--soft);font-size:19px}
footer{position:relative;z-index:1;border-top:1px solid var(--line);padding:30px 0 40px;font-size:13px;color:var(--mute);background:var(--night)}
footer .wrap{display:flex;gap:26px;align-items:center;flex-wrap:wrap}
footer .brand{flex:1;color:var(--ink);font-size:14px}
footer a:hover{color:var(--ink)}
/* reveal */
.js .rise{opacity:0;transform:translateY(28px);transition:opacity 1s cubic-bezier(.2,.7,.2,1),transform 1s cubic-bezier(.2,.7,.2,1)}
.js .rise.in{opacity:1;transform:none}
.js .rise.d1{transition-delay:.12s}.js .rise.d2{transition-delay:.24s}
@media (max-width:860px){
  .nav nav{display:none}
  /* on a phone the dot keeps to the bottom corner, half off the screen, away from the text and the projects on top */
  .orb{--x:100%!important;--y:96%!important;--d:30vmin!important}
  /* the projects stay on top while the steps scroll under them */
  .story{display:flex;flex-direction:column;gap:0}
  .stage{order:-1;position:sticky;top:56px;height:auto;padding:18px 0 28px;z-index:2;background:linear-gradient(var(--night) 82%,transparent)}
  .field{width:100%;max-width:340px;height:300px;transform:scale(.7);transform-origin:top center;margin-bottom:-90px}
  .field[data-step=note] .fi{top:calc(var(--ly) * 25px + 14px)}
  .field[data-step=note] .txt small{display:none}
  .steps{padding-top:0}
  .step{min-height:72vh}
  .moment,.moment:nth-of-type(even){grid-template-columns:minmax(0,1fr);gap:36px;min-height:0;padding:12vh 0}
  .moment:nth-of-type(even) .copy{order:0}
  .legend{grid-template-columns:repeat(2,minmax(0,1fr));row-gap:48px}
  .legend::before{display:none}
  .facts{grid-template-columns:minmax(0,1fr);gap:32px}
  .principles li{grid-template-columns:minmax(0,1fr);gap:8px}
}
@media (prefers-reduced-motion:reduce){
  .ring.live .arc,.ring.live .dot,.ring.live .spin{animation:none}
  .js .rise,.js .step li,.js .legend div,.js .field .fi{opacity:1;transform:none;transition:none}
  *{animation:none!important;transition:none!important;scroll-behavior:auto}
  .shot .gd{opacity:1}
}
`;

// Reveals as things scroll in; and the section crossing the middle of the screen sets the dot's state (body[data-dot])
// and the story's step (the field's data-step).
const SCRIPT = `<script>
(() => {
  const io = new IntersectionObserver(es => es.forEach(e => { if (e.isIntersecting) { e.target.classList.add('in'); io.unobserve(e.target); } }), { threshold: 0.3 });
  document.querySelectorAll('.rise, .step, .field, .shot, .legend').forEach(el => io.observe(el));
  const field = document.querySelector('.field');
  const mid = new IntersectionObserver(es => es.forEach(e => {
    if (!e.isIntersecting) return;
    document.body.dataset.dot = e.target.dataset.dot;
    if (e.target.dataset.step && field) field.dataset.step = e.target.dataset.step;
  }), { rootMargin: '-50% 0px -50% 0px' });
  document.querySelectorAll('[data-dot]').forEach(el => el !== document.body && mid.observe(el));
})();
</script>`;

function fieldHtml(c) {
  // in order: what needs you first, then at work, open, closed (as YDeck's sidebar sorts them)
  return `<div class="field" data-step="open">${FIELD.map(([name, color, st], i) => {
    const g = GRID[i], gx = 12.5 + (g % 4) * 25, gy = 14 + Math.floor(g / 4) * 33, [mx, my, mr] = STRAY[i];
    const cls = ['fi', `s-${st}`, WORKS.has(name) ? 'w' : '', WORKS.has(name) && st === 'work' ? 'mw' : ''].filter(Boolean).join(' ');
    return `<div class="${cls}" style="--i:${GRID.indexOf(i)};--gx:${gx}%;--gy:${gy}%;--mx:${mx};--my:${my};--mr:${mr};--ly:${i}">` +
      `<b class="tile${color === '#2A2A30' ? ' dark' : ''}" style="--tc:${color}">${name[0]}<i class="bd"></i></b>` +
      `<span class="txt"><b>${name}</b><small>${esc(c.states[st])}</small></span></div>`;
  }).join('')}</div>`;
}

function html(c, siteUrl, email, fonts) {
  const beta = c.lang === 'he' ? '/he/download' : '/download';
  const privacy = '/privacy', android = '/android';
  return `<!doctype html><html lang="${c.lang}" dir="${c.dir}"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${esc(c.title)}</title>
<meta name="description" content="${esc(c.desc)}">
<meta property="og:title" content="${esc(c.title)}"><meta property="og:description" content="${esc(c.desc)}">
<meta property="og:type" content="website"><meta property="og:site_name" content="YDeck"><meta property="og:locale" content="${c.locale}">
<meta property="og:image" content="${siteUrl}/og-${c.lang}.jpg"><meta property="og:image:width" content="1200"><meta property="og:image:height" content="630">
<meta name="twitter:card" content="summary_large_image"><meta name="twitter:image" content="${siteUrl}/og-${c.lang}.jpg">
<meta name="theme-color" content="#0B0B0D"><meta name="color-scheme" content="dark">
<link rel="alternate" hreflang="en" href="${siteUrl}/"><link rel="alternate" hreflang="he" href="${siteUrl}/he/">
<link rel="icon" href="${FAVICON}">
<link rel="preconnect" href="https://fonts.googleapis.com"><link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="${fonts}" rel="stylesheet">
<style>${CSS}</style>
<script>document.documentElement.classList.add('js')</script></head><body data-dot="hero">
<div class="orb" aria-hidden="true"><i class="core"></i><i class="hole"></i></div>
<header class="nav"><div class="wrap">
<a class="brand" href="${c.home}">${RING(22)}YDeck</a>
<nav><a href="#how">${c.nav[0]}</a><a href="${privacy}">${c.nav[1]}</a><a href="${c.other}" lang="${c.lang === 'en' ? 'he' : 'en'}">${c.otherLabel}</a></nav>
<a class="pill" href="${beta}">${c.beta}</a>
</div></header>
<main>
<section class="hero" data-dot="hero">
${RING(112, true)}
<div class="eyebrow rise">${esc(c.eyebrow)}</div>
<h1 class="rise d1">${amber(esc(c.h1))}</h1>
<p class="lead rise d2">${esc(c.lead)}</p>
<div class="ctas rise d2"><a class="pill big" href="${beta}">${c.beta}</a><a class="text-link" href="#story">${c.how} ↓</a></div>
</section>

<div class="wrap story" id="story">
<div class="steps">
${c.steps.map(([st, k, h, p, list]) => `<section class="step" data-dot="${st === 'note' ? 'note' : st}" data-step="${st}">
<div class="kicker">${dt(st === 'note' ? 'note' : st)}${esc(k)}</div><h2>${amber(esc(h))}</h2>${p ? `<p>${esc(p)}</p>` : ''}
${list ? `<ul>${list.map((t, i) => `<li>${dt(i === 0 ? 'gone' : i === 4 ? 'work' : 'wait')}<span>${esc(t)}</span></li>`).join('')}</ul>` : ''}
</section>`).join('\n')}
</div>
<div class="stage">${fieldHtml(c)}</div>
</div>

<div class="wrap">
${c.moments.map(([st, k, h, p, screen], i) => `<section class="moment" data-dot="m${i}">
<div class="copy rise"><div class="kicker">${dt(st)}${esc(k)}</div><h2>${amber(esc(h))}</h2><p>${esc(p)}</p></div>
<div class="shot rise d1"><img src="/media/screen-${c.lang}-${screen}.jpg" alt="" loading="lazy" width="1800" height="1170">${GUIDE[screen] ? `<i class="gd ${screen}"></i>` : ''}</div>
</section>`).join('\n')}
</div>

<section class="how" id="how" data-dot="gone"><div class="wrap">
<div class="kicker rise">${dt('open')}${esc(c.howK)}</div>
<h2 class="rise d1">${amber(esc(c.howH))}</h2>
<div class="legend">${c.hooks.map(([st, name, hook, p]) => `<div>${dt(st)}<b>${esc(name)}</b><code>${hook}</code><p>${esc(p)}</p></div>`).join('')}</div>
<div class="facts">${c.facts.map(([b, s]) => `<div class="rise"><b>${esc(b)}</b><span>${esc(s)}</span></div>`).join('')}</div>
</div></section>

<section class="principles" data-dot="gone"><div class="wrap"><ol>
${c.pr.map(([b, s]) => `<li class="rise"><b>${esc(b)}</b><span>${esc(s)}</span></li>`).join('\n')}
</ol></div></section>

<section class="end" data-dot="end">
${RING(72)}
<h2 class="rise">${amber(esc(c.endA))}</h2>
<p class="rise d1">${esc(c.endB)}</p>
<a class="pill big rise d2" href="${beta}">${c.beta}</a>
</section>
</main>
<footer><div class="wrap">
<a class="brand" href="${c.home}">${RING(18)}YDeck</a>
<a href="${privacy}">${c.foot[0]}</a><a href="${android}">${c.foot[1]}</a><a href="${beta}">${c.foot[2]}</a><a href="/code-signing">Code signing policy</a><a href="${c.other}">${c.otherLabel}</a><a href="mailto:${email}">${email}</a>
</div></footer>
${SCRIPT}
</body></html>`;
}

/** Writes / and /he/, their screens (site/src/screens, rendered by RenderFilm.kt), and their share images from the logo sting's last frame. */
function build({ DIST, ROOT, EMAIL, SITE_URL }) {
  const fonts = 'https://fonts.googleapis.com/css2?family=IBM+Plex+Sans:wght@300;400;500&family=IBM+Plex+Sans+Hebrew:wght@300;400;500&family=IBM+Plex+Mono:wght@400&display=swap';
  for (const c of Object.values(COPY)) {
    const out = path.join(DIST, c.lang === 'en' ? 'index.html' : 'he/index.html');
    fs.mkdirSync(path.dirname(out), { recursive: true });
    fs.writeFileSync(out, html(c, SITE_URL, EMAIL, fonts));
  }
  const screens = path.join(__dirname, 'src/screens');
  fs.mkdirSync(path.join(DIST, 'media'), { recursive: true });
  for (const f of fs.readdirSync(screens)) fs.copyFileSync(path.join(screens, f), path.join(DIST, 'media', 'screen-' + f));
  const stills = path.join(ROOT, 'store/brand/logo/out');
  for (const [lang, file] of [['en', 'sting-end.png'], ['he', 'sting-end-he.png']]) {
    const src = path.join(stills, file);
    if (!fs.existsSync(src)) continue;
    // 1080×1920 portrait still → a 1200×630 card: the middle band, on the night ground
    execFileSync('ffmpeg', ['-y', '-loglevel', 'error', '-i', src, '-vf', 'crop=1080:567:0:645,scale=1200:630', '-q:v', '3', path.join(DIST, `og-${lang}.jpg`)]);
  }
}

module.exports = { build, CSS, RING, FAVICON, esc };
