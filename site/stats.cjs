// The site's counts (stats/src/index.js): a page seen, a download clicked, and the site that sent the visitor
// (its host only, or ?ref=… on a shared link). No cookies, nothing stored in the browser, no address kept.
module.exports = `<script>(()=>{const u='https://ydeck-stats.yonidev-cloud.workers.dev/v1/site';
const send=e=>{let ref=(new URLSearchParams(location.search).get('ref')||'').toLowerCase();
if(!ref)try{const h=new URL(document.referrer).hostname;if(h&&h!==location.hostname)ref=h}catch(_){}
try{navigator.sendBeacon(u,new Blob([JSON.stringify({e,page:location.pathname.toLowerCase().slice(0,41),ref:ref.slice(0,60)})],{type:'text/plain'}))}catch(_){}};
send('view');
document.addEventListener('click',ev=>{const a=ev.target.closest&&ev.target.closest('a');if(a&&/github[.]com[/].*[/]releases|[.]msi$|[.]apk$/.test(a.href))send('download')},true)})()</script>`;
