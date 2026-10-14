// The home of YDeck for the desktop, in English (/) and Hebrew (/he): one message, "Nothing left open", told in a few
// quiet screens, each with one drawing in the brand's line that comes alive as it scrolls into view. Called by build.cjs.
const fs = require('fs'), path = require('path'), { execFileSync } = require('child_process');

const COPY = {
  en: {
    lang: 'en', dir: 'ltr', locale: 'en_US', home: '/', other: '/he', otherLabel: 'עברית',
    title: 'YDeck · Nothing left open.',
    desc: 'For developers who work with AI agents. YDeck keeps track of what your agents did, what changed, and what still needs you, across every project.',
    nav: ['How it works', 'Privacy'], beta: 'Download for Windows', betaSubject: 'YDeck beta',
    eyebrow: 'For developers who work with AI agents',
    h1: 'Nothing left open.',
    lead: 'Your agents work. YDeck keeps track of what they did, what changed, and what still needs you, across every project.',
    how: 'See how it works',
    s1a: 'Your agents write the code.', s1b: 'And you keep everything else in your head.',
    s1list: ['A question waiting in a terminal you can’t see.', 'Thirty files changed in an hour, by someone else.', 'Commits never pushed. A server still running.', 'Tomorrow, starting again from zero.'],
    s1c: 'YDeck holds it for you.',
    m1k: 'Waiting', m1h: 'Something needs you.\nYou’ll know.', m1p: 'Claude and Codex, in every project, in one place. The moment an agent stops to ask, its dot turns amber, wherever you are.',
    m2k: 'Receipts', m2h: 'Every session\nleaves a receipt.', m2p: 'What you asked. Every file it touched, line by line. Keep it in one commit, or put it back exactly as it was. Nothing is lost either way.',
    m3k: 'The ritual', m3h: 'Close the day.', m3p: 'Commit what’s left, push it, stop the servers, and leave yourself the next step. One click, then nothing is open.',
    m4h: 'And tomorrow starts\nwhere today ended.', m4p: 'Decisions you made, where each session left off, what comes next. Every new session, by any agent, starts from it.',
    pr: [['On your machine.', 'Your code never leaves it. No account, no server of ours.'], ['No AI of its own.', 'It works with the agents you already use: Claude Code and Codex.'], ['Never destructive.', 'Nothing it does throws work away. Everything can be undone.'], ['Free.', 'For every developer, on Windows. Mac is next.']],
    endA: 'Nothing left open.', endB: 'Free and open source, for Windows.',
    foot: ['Privacy', 'YDeck for Android'],
    ui: {
      loops: 'Open loops', waiting: 'Claude needs you', waitingD: 'Nova · Allow editing src/auth.ts?', session: 'Claude’s session: 3 files not committed', sessionD: 'Orbit · “Add the Stripe webhook for deposits”',
      review: 'Review', pushed: '2 commits pushed', pushedD: 'Pulse · closed just now',
      rTitle: 'Add the Stripe webhook for deposits, with tests', rMeta: 'Claude · 40 min ago · 3 files changed', keep: 'Commit these files', back: 'Put back as before',
      dayItems: ['Commit 3 changes in Orbit', 'Push to the server', 'Stop the dev server on :3000', 'Next step: test the webhook against Stripe’s test clock'],
      dayDone: 'The day is closed.', dayDoneD: 'Tomorrow starts here.',
      memDecision: 'Payments go through Stripe only', memDone: 'Added the deposit flow and its webhook, with a test.', memNext: 'Test the webhook against Stripe’s test clock.',
      memLabels: ['Decided', 'Last session', 'Next'],
    },
  },
  he: {
    lang: 'he', dir: 'rtl', locale: 'he_IL', home: '/he/', other: '/', otherLabel: 'English',
    title: 'YDeck · שום דבר לא נשאר פתוח.',
    desc: 'למפתחים שעובדים עם סוכני AI. YDeck זוכר מה הסוכנים עשו, מה השתנה ומה עוד מחכה לך, בכל הפרויקטים.',
    nav: ['איך זה עובד', 'פרטיות'], beta: 'הורדה ל־Windows', betaSubject: 'בטא של YDeck',
    eyebrow: 'למפתחים שעובדים עם סוכני AI',
    h1: 'שום דבר לא נשאר פתוח.',
    lead: 'הסוכנים עובדים. YDeck זוכר מה הם עשו, מה השתנה ומה עוד מחכה לך, בכל הפרויקטים.',
    how: 'איך זה עובד',
    s1a: 'הסוכנים כותבים את הקוד.', s1b: 'ואת כל השאר אתה מחזיק בראש.',
    s1list: ['שאלה שמחכה בטרמינל שאתה לא רואה.', 'שלושים קבצים שהשתנו בשעה, ולא על ידך.', 'קומיטים שלא נדחפו. שרת שעוד רץ.', 'ומחר, להתחיל שוב מאפס.'],
    s1c: 'YDeck מחזיק את זה בשבילך.',
    m1k: 'מחכה', m1h: 'משהו צריך אותך.\nאתה תדע.', m1p: 'Claude ו־Codex, בכל הפרויקטים, במקום אחד. ברגע שסוכן עוצר לשאול, הנקודה שלו הופכת לכתומה, איפה שלא תהיה.',
    m2k: 'קבלות', m2h: 'כל סשן\nמשאיר קבלה.', m2p: 'מה ביקשת. כל קובץ שהוא נגע בו, שורה אחרי שורה. שומרים בקומיט אחד, או מחזירים בדיוק למה שהיה. כך או כך, שום דבר לא הולך לאיבוד.',
    m3k: 'הטקס', m3h: 'סוגרים את היום.', m3p: 'קומיט למה שנשאר, דחיפה, עצירת השרתים, וצעד הבא למחר. לחיצה אחת, ושום דבר לא נשאר פתוח.',
    m4h: 'ומחר מתחיל\nאיפה שהיום נגמר.', m4p: 'ההחלטות שקיבלת, איפה כל סשן עצר, ומה הלאה. כל סשן חדש, של כל סוכן, מתחיל משם.',
    pr: [['על המחשב שלך.', 'הקוד לא יוצא ממנו. בלי חשבון, בלי שרת שלנו.'], ['בלי AI משלו.', 'עובד עם הסוכנים שכבר יש לך: Claude Code ו־Codex.'], ['אף פעם לא הורס.', 'שום דבר שהוא עושה לא זורק עבודה. הכול אפשר לבטל.'], ['חינם.', 'לכל מפתח, ב־Windows. Mac בדרך.']],
    endA: 'שום דבר לא נשאר פתוח.', endB: 'חינם וקוד פתוח, ל־Windows.',
    foot: ['פרטיות', 'YDeck לאנדרואיד'],
    ui: {
      loops: 'מה פתוח', waiting: 'Claude מחכה לך', waitingD: 'Nova · לאשר עריכה של src/auth.ts?', session: '3 קבצים מהסשן של Claude לא נשמרו', sessionD: 'Orbit · “Add the Stripe webhook for deposits”',
      review: 'לבדיקה', pushed: '2 קומיטים נדחפו', pushedD: 'Pulse · נסגר עכשיו',
      rTitle: 'Add the Stripe webhook for deposits, with tests', rMeta: 'Claude · לפני 40 דקות · 3 קבצים השתנו', keep: 'קומיט לקבצים האלה', back: 'החזרה למצב שלפני',
      dayItems: ['קומיט ל־3 שינויים ב־Orbit', 'דחיפה לשרת', 'עצירת שרת הפיתוח ב־:3000', 'הצעד הבא: לבדוק את ה־webhook מול שעון הבדיקות של Stripe'],
      dayDone: 'היום סגור.', dayDoneD: 'מחר מתחילים מכאן.',
      memDecision: 'תשלומים רק דרך Stripe', memDone: 'נוסף תהליך הפיקדון וה־webhook שלו, עם בדיקה.', memNext: 'לבדוק את ה־webhook מול שעון הבדיקות של Stripe.',
      memLabels: ['הוחלט', 'הסשן האחרון', 'הבא'],
    },
  },
};

const esc = s => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
const br = s => esc(s).replace(/\n/g, '<br>');

// The mark: a ring with an opening and the amber dot in it (store/brand/logo). `live` plays the sting once:
// "thinking", an indigo arc that spins, slows, opens and settles into the mark, and the dot lands.
const RING = (size, live) => `<svg class="ring${live ? ' live' : ''}" viewBox="0 0 100 100" width="${size}" height="${size}" aria-hidden="true">` +
  `<g class="spin"><circle class="arc" cx="50" cy="50" r="36" pathLength="360" transform="rotate(-10 50 50)"/></g><circle class="dot" cx="77.6" cy="26.9" r="7.5"/></svg>`;
const MARK_SVG = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100"><rect width="100" height="100" rx="22" fill="#0B0B0D"/><g transform="translate(19 19) scale(0.62)"><circle cx="50" cy="50" r="36" fill="none" stroke="#F4F4F5" stroke-width="11" stroke-linecap="round" stroke-dasharray="188.5 37.7" transform="rotate(-10 50 50)"/><circle cx="77.6" cy="26.9" r="7.5" fill="#F5A524"/></g></svg>`;
const FAVICON = 'data:image/svg+xml,' + encodeURIComponent(MARK_SVG);

// A headline's last full stop becomes the amber dot.
const amber = h => h.replace(/\.(\s*)$/, '<span class="p">.</span>$1');

// The drawings, in the brand's line (store/ads/language): one thick round stroke, white for what is done, and one amber
// piece, set apart by a space, for what is still open. Indigo is only a drop: the one waiting terminal's line while it is written, once. `f` draws in when it scrolls into view.
const rr = (x, y, w, h, r) => `M${x + r} ${y}H${x + w - r}A${r} ${r} 0 0 1 ${x + w} ${y + r}V${y + h - r}A${r} ${r} 0 0 1 ${x + w - r} ${y + h}H${x + r}A${r} ${r} 0 0 1 ${x} ${y + h - r}V${y + r}A${r} ${r} 0 0 1 ${x + r} ${y}Z`;
const svg = (cls, box, body) => `<svg class="art ${cls}" viewBox="${box}" aria-hidden="true">${body}</svg>`;
const d = (cls, path, delay) => `<path class="${cls}" pathLength="1" d="${path}"${delay ? ` style="transition-delay:${delay}s"` : ''}/>`;
// Waiting: three terminals, agents typing; the amber cursor moves between them and stops at the one that needs you.
const WAIT = svg('wait', '0 0 480 360', [360, 250, 330].map((end, i) => {
  const y = 20 + i * 116;
  return d('w f', rr(20, y, 440, 88, 18), i * .15) + d('w f', `M48 ${y + 28}L66 ${y + 44}L48 ${y + 60}`, .3 + i * .15) + d(`w type t${i}`, `M92 ${y + 44}H${end}`);
}).join('') + `<g class="cur"><path class="a blink" d="M386 64h28"/></g>`);
// a line being written
const wr = (path, delay) => `<path class="w wr" pathLength="1" d="${path}" style="animation-delay:${delay}s"/>`;
// Receipts: the page fills, line by line; what is left to decide is the amber line at the bottom.
const RECEIPT = svg('receipt flip', '0 0 480 360', d('w f', rr(100, 10, 280, 340, 22)) + wr('M140 62H290', .4) +
  [340, 300, 330, 270].map((end, i) => wr(`M140 ${118 + i * 42}H${end}`, .8 + i * .45)).join('') + d('a f', 'M140 300H232', 2.9));
// The day: four things open; they close one by one, and only the next step stays amber.
const DAY = svg('close flip', '0 0 480 360', [380, 330, 360, 300].map((end, i) => {
  const y = 48 + i * 88, last = i === 3, t = .5 + i * .55;
  return d('w f', rr(30, y - 24, 48, 48, 12), i * .1) + (last ? d('a', `M112 ${y}H${end}`) :
    d('w chk', `M43 ${y + 1}L52 ${y + 10}L66 ${y - 9}`, t) + `<path class="ln" d="M112 ${y}H${end}" style="transition-delay:${t}s"/>`);
}).join(''));
// The memory's three columns: what was decided, the last session, and the next step (its arrowhead the amber piece).
const icon = (body, cls = '') => `<svg class="art icon ${cls}" viewBox="0 0 64 64" aria-hidden="true">${body}</svg>`;
const ICON = {
  decided: icon(d('w f', rr(8, 8, 48, 48, 12)) + d('w f', 'M20 33L28 41L44 24', .3)),
  last: icon(d('w f', rr(12, 6, 40, 52, 9)) + d('w f', 'M22 22H42', .3) + d('w f', 'M22 32H42', .4) + d('w f', 'M22 42H34', .5), 'flip'),
  next: icon(d('w f', 'M8 32H36') + d('a f', 'M46 20L58 32L46 44', .3), 'flip'),
};

const CSS = `
:root{--night:#0B0B0D;--ink:#F4F4F5;--soft:#C8C8CE;--mute:#8B8B93;--faint:#5A5A62;--card:#131316;--line:#24242A;--amber:#F5A524;--indigo:#3D35E0;--indigo-t:#A7A3FF;--think:#6E66F8;--green:#34C77B}
*{box-sizing:border-box}
html{background:var(--night);color:var(--ink);-webkit-font-smoothing:antialiased;text-rendering:optimizeLegibility;scroll-behavior:smooth}
body{margin:0;font-family:'IBM Plex Sans','IBM Plex Sans Hebrew',system-ui,sans-serif;font-size:17px;line-height:1.55}
html[lang=he] body{font-family:'IBM Plex Sans Hebrew','IBM Plex Sans',system-ui,sans-serif}
a{color:inherit;text-decoration:none}
.wrap{max-width:1120px;margin:0 auto;padding:0 28px}
.mono{font-family:'IBM Plex Mono',ui-monospace,monospace}
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
/* the ring */
.ring .arc{fill:none;stroke:var(--ink);stroke-width:11;stroke-linecap:round;stroke-dasharray:300 60}
.ring .dot{fill:var(--amber)}
.ring .spin{transform-box:view-box;transform-origin:50px 50px}
.ring.live .spin{animation:spin 1.75s .15s cubic-bezier(.33,1,.68,1) both}
.ring.live .arc{animation:think 2.2s linear both}
.ring.live .dot{transform-box:fill-box;transform-origin:center;animation:land .34s 1.85s cubic-bezier(.3,1.6,.5,1) both}
@keyframes spin{from{transform:rotate(-1080deg)}to{transform:rotate(0)}}
@keyframes think{0%{stroke-dasharray:70 290;stroke:var(--indigo);opacity:0}8%{opacity:1}43%{stroke-dasharray:70 290}57%{stroke:var(--indigo)}82%{stroke-dasharray:300 60;stroke:var(--ink)}100%{stroke-dasharray:300 60;stroke:var(--ink);opacity:1}}
@keyframes land{from{transform:scale(0)}to{transform:scale(1)}}
/* statement */
.statement{padding:22vh 0 18vh}
.big{margin:0;font-weight:300;font-size:clamp(34px,5.6vw,68px);line-height:1.08;letter-spacing:-.035em}
html[lang=he] .big{letter-spacing:-.015em;line-height:1.15}
.big .dim{color:var(--faint)}
.list{list-style:none;margin:64px 0 0;padding:0;max-width:760px}
.list li{font-size:clamp(20px,2.4vw,26px);line-height:1.4;color:var(--soft);padding:22px 0;border-top:1px solid var(--line);font-weight:300}
.list li:last-child{border-bottom:1px solid var(--line)}
.after{margin-top:64px;font-size:clamp(24px,3vw,34px);font-weight:400;letter-spacing:-.02em}
/* moments */
.moment{padding:16vh 0;display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1.15fr);gap:72px;align-items:center}
.kicker{font-size:13px;color:var(--amber);letter-spacing:.06em;text-transform:uppercase;margin-bottom:18px}
html[lang=he] .kicker{letter-spacing:0;text-transform:none;font-size:14px}
.moment h2{margin:0;font-weight:300;font-size:clamp(34px,4.6vw,56px);line-height:1.08;letter-spacing:-.035em}
html[lang=he] .moment h2{letter-spacing:-.015em;line-height:1.15}
.moment p{margin:24px 0 0;font-size:19px;line-height:1.6;color:var(--soft);max-width:440px}
/* the drawings, in the brand's line: white is what is done, amber is the one piece still open, set apart by a space */
.art{display:block;width:100%;height:auto;max-width:520px;margin-inline:auto;overflow:visible}
.art path{fill:none;stroke-width:10;stroke-linecap:round;stroke-linejoin:round}
.icon path{stroke-width:5}
.art .w{stroke:var(--ink)}
.art .a{stroke:var(--amber)}
.js .art .f,.js .art .chk,.js .art .type{stroke-dasharray:1 1;stroke-dashoffset:1}
.art .f{transition:stroke-dashoffset 1.1s cubic-bezier(.6,0,.2,1)}
.art.in .f{stroke-dashoffset:0}
/* waiting: agents type in their terminals; the amber cursor moves between them and stops at the one that needs you */
.wait.in .t0{animation:type 3.4s .9s infinite}
.wait.in .t2{animation:type 2.8s 1.3s infinite}
.wait.in .t1{animation:typed 1.2s 1s both}
.js .wait .cur{opacity:0}
.wait.in .cur{animation:hop 2.6s 1.2s cubic-bezier(.6,0,.2,1) both}
.wait.in .blink{animation:blink 1.1s 3.9s infinite}
@keyframes type{0%{stroke-dashoffset:1;opacity:1}55%{stroke-dashoffset:0}82%{stroke-dashoffset:0;opacity:1}100%{stroke-dashoffset:0;opacity:0}}
@keyframes typed{0%,75%{stroke:var(--think)}75%{stroke-dashoffset:0}100%{stroke-dashoffset:0;stroke:var(--ink)}}
@keyframes hop{0%{opacity:0;transform:none}10%,32%{opacity:1;transform:none}55%,68%{opacity:1;transform:translate(-30px,232px)}92%,100%{opacity:1;transform:translate(-110px,116px)}}
@keyframes blink{0%,49%{opacity:1}50%,100%{opacity:.2}}
/* the day: four things open, they close one by one, and only the next step stays amber */
.close .ln{stroke:var(--amber)}
.close.in .ln{stroke:var(--ink);transition:stroke .5s}
/* the receipt: the page fills, line by line */
.js .art .wr{stroke-dasharray:1 1;stroke-dashoffset:1}
.receipt.in .wr{animation:write .8s both}
@keyframes write{to{stroke-dashoffset:0}}
.art .chk{transition:stroke-dashoffset .45s cubic-bezier(.6,0,.2,1)}
.art.in .chk{stroke-dashoffset:0}
/* a headline's full stop is the amber dot */
.p{color:var(--amber)}
/* the open loops in the list: an amber piece before each */
.list li{position:relative;padding-inline-start:46px}
.list li::before{content:'';position:absolute;inset-inline-start:0;top:calc(50% - 3px);width:24px;height:6px;border-radius:3px;background:var(--amber);transform:scaleX(0);transform-origin:left center;transition:transform .7s .35s cubic-bezier(.6,0,.2,1)}
html[dir=rtl] .list li::before{transform-origin:right center}
.list li.in::before,html:not(.js) .list li::before{transform:none}
/* in Hebrew, text-like drawings read from the right; the ticks are turned back so they still read as ticks */
html[dir=rtl] .art.flip{transform:scaleX(-1)}
html[dir=rtl] .art.flip .chk{transform:scaleX(-1);transform-box:fill-box;transform-origin:center}
.mem .icon{width:44px;height:44px;margin-bottom:16px;max-width:none;margin-inline:0}
/* memory */
.memory{padding:18vh 0;text-align:center}
.memory h2{margin:0 auto;font-weight:300;font-size:clamp(34px,5vw,60px);line-height:1.08;letter-spacing:-.035em;max-width:900px}
html[lang=he] .memory h2{letter-spacing:-.015em;line-height:1.15}
.memory p{margin:24px auto 0;font-size:19px;color:var(--soft);max-width:560px}
.mem{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:0;margin:64px auto 0;max-width:960px;text-align:start;border-top:1px solid var(--line)}
.mem div{padding:26px 22px 0}
.mem div+div{border-inline-start:1px solid var(--line)}
.mem small{display:block;font-size:13px;color:var(--mute);margin-bottom:10px}
.mem div p{margin:0;font-size:17px;line-height:1.5;color:var(--ink)}
/* principles */
.principles{padding:12vh 0 16vh}
.principles ol{list-style:none;margin:0;padding:0}
.principles li{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1.3fr);gap:40px;padding:34px 0;border-top:1px solid var(--line);align-items:baseline}
.principles li:last-child{border-bottom:1px solid var(--line)}
.principles b{font-weight:400;font-size:clamp(24px,3vw,34px);letter-spacing:-.02em}
.principles span{font-size:18px;color:var(--soft)}
/* end */
.end{padding:20vh 28px 22vh;text-align:center}
.end .ring{width:72px;height:72px;margin-bottom:36px}
.end h2{margin:0;font-weight:300;font-size:clamp(44px,8vw,96px);letter-spacing:-.045em;line-height:1.04}
html[lang=he] .end h2{letter-spacing:-.02em;line-height:1.12}
.end p{margin:22px 0 40px;color:var(--soft);font-size:19px}
footer{border-top:1px solid var(--line);padding:30px 0 40px;font-size:13px;color:var(--mute)}
footer .wrap{display:flex;gap:26px;align-items:center;flex-wrap:wrap}
footer .brand{flex:1;color:var(--ink);font-size:14px}
footer a:hover{color:var(--ink)}
/* reveal */
.js .rise{opacity:0;transform:translateY(28px);transition:opacity 1s cubic-bezier(.2,.7,.2,1),transform 1s cubic-bezier(.2,.7,.2,1)}
.js .rise.in{opacity:1;transform:none}
.js .rise.d1{transition-delay:.12s}.js .rise.d2{transition-delay:.24s}
@media (max-width:860px){
  .nav nav{display:none}
  .moment{grid-template-columns:minmax(0,1fr);gap:44px;padding:12vh 0}
  .principles li{grid-template-columns:minmax(0,1fr);gap:8px}
  .mem{grid-template-columns:minmax(0,1fr)}
  .mem div+div{border-inline-start:0;border-top:1px solid var(--line);margin-top:22px}
}
@media (prefers-reduced-motion:reduce){
  .ring.live .arc,.ring.live .dot,.ring.live .spin{animation:none}
  .js .rise{opacity:1;transform:none;transition:none}
  .art *{animation:none!important}
  .js .art .type,.js .art .wr{stroke-dashoffset:0}
  .js .wait .cur{opacity:1;transform:translate(-110px,116px)}
  *{transition:none!important;scroll-behavior:auto}
}
`;

const SCRIPT = `<script>
(() => {
  const io = new IntersectionObserver(es => es.forEach(e => { if (e.isIntersecting) { e.target.classList.add('in'); io.unobserve(e.target); } }), { threshold: 0.35 });
  document.querySelectorAll('.rise, .art').forEach(el => io.observe(el));
})();
</script>`;

function html(c, siteUrl, email, fonts) {
  const u = c.ui;
  // The download page: the installer on GitHub Releases, and why Windows warns.
  const beta = '/download';
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
<script>document.documentElement.classList.add('js')</script></head><body>
<header class="nav"><div class="wrap">
<a class="brand" href="${c.home}">${RING(22)}YDeck</a>
<nav><a href="#how">${c.nav[0]}</a><a href="${privacy}">${c.nav[1]}</a><a href="${c.other}" lang="${c.lang === 'en' ? 'he' : 'en'}">${c.otherLabel}</a></nav>
<a class="pill" href="${beta}">${c.beta}</a>
</div></header>
<main>
<section class="hero">
${RING(112, true)}
<div class="eyebrow rise">${esc(c.eyebrow)}</div>
<h1 class="rise d1">${amber(esc(c.h1))}</h1>
<p class="lead rise d2">${esc(c.lead)}</p>
<div class="ctas rise d2"><a class="pill big" href="${beta}">${c.beta}</a><a class="text-link" href="#how">${c.how} ↓</a></div>
</section>

<section class="statement" id="how"><div class="wrap">
<h2 class="big rise">${esc(c.s1a)}<br><span class="dim">${esc(c.s1b)}</span></h2>
<ul class="list">${c.s1list.map((t, i) => `<li class="rise${i % 2 ? ' d1' : ''}">${esc(t)}</li>`).join('')}</ul>
<p class="after rise">${esc(c.s1c)}</p>
</div></section>

<div class="wrap">
<section class="moment">
<div class="rise"><div class="kicker">${c.m1k}</div><h2>${amber(br(c.m1h))}</h2><p>${esc(c.m1p)}</p></div>
${WAIT}
</section>

<section class="moment">
<div class="rise"><div class="kicker">${c.m2k}</div><h2>${amber(br(c.m2h))}</h2><p>${esc(c.m2p)}</p></div>
${RECEIPT}
</section>

<section class="moment">
<div class="rise"><div class="kicker">${c.m3k}</div><h2>${amber(br(c.m3h))}</h2><p>${esc(c.m3p)}</p></div>
${DAY}
</section>
</div>

<section class="memory"><div class="wrap">
<h2 class="rise">${amber(br(c.m4h))}</h2>
<p class="rise d1">${esc(c.m4p)}</p>
<div class="mem rise d2">
<div>${ICON.decided}<small>${u.memLabels[0]}</small><p>${esc(u.memDecision)}</p></div>
<div>${ICON.last}<small>${u.memLabels[1]}</small><p>${esc(u.memDone)}</p></div>
<div>${ICON.next}<small>${u.memLabels[2]}</small><p>${esc(u.memNext)}</p></div>
</div>
</div></section>

<section class="principles"><div class="wrap"><ol>
${c.pr.map(([b, s]) => `<li class="rise"><b>${esc(b)}</b><span>${esc(s)}</span></li>`).join('\n')}
</ol></div></section>

<section class="end">
${RING(72)}
<h2 class="rise">${amber(esc(c.endA))}</h2>
<p class="rise d1">${esc(c.endB)}</p>
<a class="pill big rise d2" href="${beta}">${c.beta}</a>
</section>
</main>
<footer><div class="wrap">
<a class="brand" href="${c.home}">${RING(18)}YDeck</a>
<a href="${privacy}">${c.foot[0]}</a><a href="${android}">${c.foot[1]}</a><a href="/download">${c.foot[0] === 'פרטיות' ? 'הורדה' : 'Download'}</a><a href="/code-signing">Code signing policy</a><a href="${c.other}">${c.otherLabel}</a><a href="mailto:${email}">${email}</a>
</div></footer>
${SCRIPT}
</body></html>`;
}

/** Writes / and /he/, and their share images from the logo sting's last frame. */
function build({ DIST, ROOT, EMAIL, SITE_URL }) {
  const fonts = 'https://fonts.googleapis.com/css2?family=IBM+Plex+Sans:wght@300;400;500&family=IBM+Plex+Sans+Hebrew:wght@300;400;500&family=IBM+Plex+Mono:wght@400&display=swap';
  for (const c of Object.values(COPY)) {
    const out = path.join(DIST, c.lang === 'en' ? 'index.html' : 'he/index.html');
    fs.mkdirSync(path.dirname(out), { recursive: true });
    fs.writeFileSync(out, html(c, SITE_URL, EMAIL, fonts));
  }
  const stills = path.join(ROOT, 'store/brand/logo/out');
  for (const [lang, file] of [['en', 'sting-end.png'], ['he', 'sting-end-he.png']]) {
    const src = path.join(stills, file);
    if (!fs.existsSync(src)) continue;
    // 1080×1920 portrait still → a 1200×630 card: the middle band, on the night ground
    execFileSync('ffmpeg', ['-y', '-loglevel', 'error', '-i', src, '-vf', 'crop=1080:567:0:645,scale=1200:630', '-q:v', '3', path.join(DIST, `og-${lang}.jpg`)]);
  }
}

module.exports = { build, CSS, RING, FAVICON, esc };
