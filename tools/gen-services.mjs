// Generates the service catalog (app/.../data/ServiceCatalog.kt) and brand vector drawables (res/drawable/si_*.xml)
// from a local copy of the simple-icons npm package (CC0, https://simpleicons.org).
//
//   npm pack simple-icons@16.32.0 && tar -xzf simple-icons-16.32.0.tgz
//   node tools/gen-services.mjs <path-to-extracted>/package
//
// Services without a simple-icons slug (removed from the library at the brand's request) show the site's favicon instead.

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const pkg = process.argv[2];
if (!pkg) throw new Error('usage: node tools/gen-services.mjs <simple-icons package dir>');
const siData = JSON.parse(fs.readFileSync(path.join(pkg, 'data/simple-icons.json'), 'utf8'));
const siVersion = JSON.parse(fs.readFileSync(path.join(pkg, 'package.json'), 'utf8')).version;
const bySlug = Object.fromEntries(siData.map((d) => [d.slug, d]));

// [id, slug | null, title | null (null = simple-icons title), short | null, category, route g=Google v=Vercel p=plain, default URL, "host[/path] ..." matchers]
// Ids that existed before the catalog (play, firebase, gmail, …) must keep their names: saved projects refer to them.
const S = [
  // Google
  ['play', 'googleplay', 'Play Console', 'Play', 'GOOGLE', 'g', 'https://play.google.com/console', 'play.google.com/console'],
  ['firebase', 'firebase', null, null, 'GOOGLE', 'g', 'https://console.firebase.google.com', 'console.firebase.google.com firebase.google.com'],
  ['gmail', 'gmail', null, null, 'GOOGLE', 'g', 'https://mail.google.com', 'mail.google.com'],
  ['drive', 'googledrive', 'Drive', null, 'GOOGLE', 'g', 'https://drive.google.com', 'drive.google.com'],
  ['analytics', 'googleanalytics', 'Analytics', null, 'GOOGLE', 'g', 'https://analytics.google.com', 'analytics.google.com'],
  ['gcloud', 'googlecloud', 'Cloud Console', 'Cloud', 'GOOGLE', 'g', 'https://console.cloud.google.com', 'console.cloud.google.com cloud.google.com'],
  ['searchconsole', 'googlesearchconsole', 'Search Console', 'Search', 'GOOGLE', 'g', 'https://search.google.com/search-console', 'search.google.com/search-console'],
  ['gads', 'googleads', 'Google Ads', 'Ads', 'GOOGLE', 'g', 'https://ads.google.com', 'ads.google.com'],
  ['gtm', 'googletagmanager', 'Tag Manager', 'GTM', 'GOOGLE', 'g', 'https://tagmanager.google.com', 'tagmanager.google.com'],
  ['bigquery', 'googlebigquery', 'BigQuery', null, 'GOOGLE', 'g', 'https://console.cloud.google.com/bigquery', 'console.cloud.google.com/bigquery'],
  ['gmaps', 'googlemaps', 'Maps Platform', 'Maps', 'GOOGLE', 'g', 'https://console.cloud.google.com/google/maps-apis', 'console.cloud.google.com/google/maps-apis'],
  ['gcal', 'googlecalendar', 'Calendar', null, 'GOOGLE', 'g', 'https://calendar.google.com', 'calendar.google.com'],
  ['gsheets', 'googlesheets', 'Sheets', null, 'GOOGLE', 'g', 'https://docs.google.com/spreadsheets', 'docs.google.com/spreadsheets'],
  ['gdocs', 'googledocs', 'Docs', null, 'GOOGLE', 'g', 'https://docs.google.com/document', 'docs.google.com/document'],
  ['gpay', 'googlepay', 'Google Pay', 'G Pay', 'PAYMENTS', 'g', 'https://pay.google.com/business/console', 'pay.google.com'],
  // Stores
  ['store', 'googleplay', 'עמוד בחנות', 'בחנות', 'STORES', 'p', 'https://play.google.com/store/apps/details?id=', 'play.google.com/store'],
  ['appstore', 'appstore', 'App Store Connect', 'App Store', 'STORES', 'p', 'https://appstoreconnect.apple.com', 'appstoreconnect.apple.com apps.apple.com'],
  ['appledev', 'apple', 'Apple Developer', 'Apple', 'STORES', 'p', 'https://developer.apple.com/account', 'developer.apple.com'],
  // Hosting
  ['vercel', 'vercel', null, null, 'HOSTING', 'v', 'https://vercel.com/dashboard', 'vercel.com'],
  ['netlify', 'netlify', null, null, 'HOSTING', 'p', 'https://app.netlify.com', 'netlify.com'],
  ['render', 'render', null, null, 'HOSTING', 'p', 'https://dashboard.render.com', 'render.com'],
  ['fly', 'flydotio', null, null, 'HOSTING', 'p', 'https://fly.io/dashboard', 'fly.io'],
  ['cloudflare', 'cloudflare', null, null, 'HOSTING', 'p', 'https://dash.cloudflare.com', 'cloudflare.com'],
  ['railway', 'railway', null, null, 'HOSTING', 'p', 'https://railway.com/dashboard', 'railway.com railway.app'],
  ['digitalocean', 'digitalocean', null, 'DO', 'HOSTING', 'p', 'https://cloud.digitalocean.com', 'digitalocean.com'],
  ['hetzner', 'hetzner', null, null, 'HOSTING', 'p', 'https://console.hetzner.cloud', 'hetzner.cloud hetzner.com'],
  ['vultr', 'vultr', null, null, 'HOSTING', 'p', 'https://my.vultr.com', 'vultr.com'],
  ['ovh', 'ovh', null, null, 'HOSTING', 'p', 'https://www.ovh.com/manager', 'ovh.com ovhcloud.com'],
  ['contabo', 'contabo', null, null, 'HOSTING', 'p', 'https://my.contabo.com', 'contabo.com'],
  ['aws', null, 'AWS', null, 'HOSTING', 'p', 'https://console.aws.amazon.com', 'aws.amazon.com'],
  ['heroku', null, 'Heroku', null, 'HOSTING', 'p', 'https://dashboard.heroku.com', 'heroku.com'],
  ['expo', 'expo', null, null, 'HOSTING', 'p', 'https://expo.dev', 'expo.dev'],
  // Code
  ['github', 'github', null, null, 'CODE', 'p', 'https://github.com/', 'github.com'],
  ['gitlab', 'gitlab', null, null, 'CODE', 'p', 'https://gitlab.com', 'gitlab.com'],
  ['bitbucket', 'bitbucket', null, null, 'CODE', 'p', 'https://bitbucket.org', 'bitbucket.org'],
  ['npm', 'npm', null, null, 'CODE', 'p', 'https://www.npmjs.com', 'npmjs.com'],
  ['docker', 'docker', 'Docker Hub', 'Docker', 'CODE', 'p', 'https://hub.docker.com', 'docker.com'],
  ['postman', 'postman', null, null, 'CODE', 'p', 'https://web.postman.co', 'postman.com postman.co'],
  ['codesandbox', 'codesandbox', null, 'Sandbox', 'CODE', 'p', 'https://codesandbox.io/dashboard', 'codesandbox.io'],
  ['stackblitz', 'stackblitz', null, null, 'CODE', 'p', 'https://stackblitz.com', 'stackblitz.com'],
  ['replit', 'replit', null, null, 'CODE', 'p', 'https://replit.com', 'replit.com'],
  ['cursor', 'cursor', null, null, 'CODE', 'p', 'https://cursor.com/dashboard', 'cursor.com'],
  ['copilot', 'githubcopilot', 'GitHub Copilot', 'Copilot', 'CODE', 'p', 'https://github.com/copilot', 'github.com/copilot'],
  ['jetbrains', 'jetbrains', null, null, 'CODE', 'p', 'https://account.jetbrains.com', 'jetbrains.com'],
  // Data
  ['mongodb', 'mongodb', null, null, 'DATA', 'p', 'https://cloud.mongodb.com', 'mongodb.com'],
  ['neon', 'neon', null, null, 'DATA', 'p', 'https://console.neon.tech', 'neon.tech'],
  ['supabase', 'supabase', null, null, 'DATA', 'p', 'https://supabase.com/dashboard', 'supabase.com supabase.co'],
  ['planetscale', 'planetscale', null, null, 'DATA', 'p', 'https://app.planetscale.com', 'planetscale.com'],
  ['turso', 'turso', null, null, 'DATA', 'p', 'https://app.turso.tech', 'turso.tech'],
  ['redis', 'redis', null, null, 'DATA', 'p', 'https://cloud.redis.io', 'redis.io redis.com'],
  ['upstash', 'upstash', null, null, 'DATA', 'p', 'https://console.upstash.com', 'upstash.com'],
  ['appwrite', 'appwrite', null, null, 'DATA', 'p', 'https://cloud.appwrite.io', 'appwrite.io'],
  ['pocketbase', 'pocketbase', null, null, 'DATA', 'p', 'https://pocketbase.io', 'pocketbase.io'],
  ['hasura', 'hasura', null, null, 'DATA', 'p', 'https://cloud.hasura.io', 'hasura.io'],
  ['prisma', 'prisma', null, null, 'DATA', 'p', 'https://console.prisma.io', 'prisma.io'],
  ['drizzle', 'drizzle', null, null, 'DATA', 'p', 'https://orm.drizzle.team', 'drizzle.team'],
  ['algolia', 'algolia', null, null, 'DATA', 'p', 'https://dashboard.algolia.com', 'algolia.com'],
  ['meilisearch', 'meilisearch', null, 'Meili', 'DATA', 'p', 'https://cloud.meilisearch.com', 'meilisearch.com'],
  ['sanity', 'sanity', null, null, 'DATA', 'p', 'https://www.sanity.io/manage', 'sanity.io'],
  ['contentful', 'contentful', null, null, 'DATA', 'p', 'https://app.contentful.com', 'contentful.com'],
  ['strapi', 'strapi', null, null, 'DATA', 'p', 'https://cloud.strapi.io', 'strapi.io'],
  ['storyblok', 'storyblok', null, null, 'DATA', 'p', 'https://app.storyblok.com', 'storyblok.com'],
  ['directus', 'directus', null, null, 'DATA', 'p', 'https://directus.cloud', 'directus.io directus.cloud'],
  // AI
  ['openai', null, 'OpenAI', null, 'AI', 'p', 'https://platform.openai.com', 'openai.com chatgpt.com'],
  ['anthropic', 'anthropic', null, null, 'AI', 'p', 'https://console.anthropic.com', 'anthropic.com'],
  ['claude', 'claude', null, null, 'AI', 'p', 'https://claude.ai', 'claude.ai claude.com'],
  ['gemini', 'googlegemini', 'Gemini', null, 'AI', 'g', 'https://aistudio.google.com', 'gemini.google.com aistudio.google.com'],
  ['huggingface', 'huggingface', null, 'HF', 'AI', 'p', 'https://huggingface.co', 'huggingface.co'],
  ['replicate', 'replicate', null, null, 'AI', 'p', 'https://replicate.com', 'replicate.com'],
  ['perplexity', 'perplexity', null, null, 'AI', 'p', 'https://www.perplexity.ai', 'perplexity.ai'],
  ['mistral', 'mistralai', 'Mistral AI', 'Mistral', 'AI', 'p', 'https://console.mistral.ai', 'mistral.ai'],
  ['ollama', 'ollama', null, null, 'AI', 'p', 'https://ollama.com', 'ollama.com'],
  // Analytics & monitoring
  ['mixpanel', 'mixpanel', null, null, 'ANALYTICS', 'p', 'https://mixpanel.com', 'mixpanel.com'],
  ['posthog', 'posthog', 'PostHog', null, 'ANALYTICS', 'p', 'https://app.posthog.com', 'posthog.com'],
  ['plausible', 'plausibleanalytics', 'Plausible', null, 'ANALYTICS', 'p', 'https://plausible.io/sites', 'plausible.io'],
  ['hotjar', 'hotjar', null, null, 'ANALYTICS', 'p', 'https://insights.hotjar.com', 'hotjar.com'],
  ['clarity', null, 'Clarity', null, 'ANALYTICS', 'p', 'https://clarity.microsoft.com', 'clarity.microsoft.com'],
  ['sentry', 'sentry', null, null, 'ANALYTICS', 'p', 'https://sentry.io', 'sentry.io'],
  ['datadog', 'datadog', null, null, 'ANALYTICS', 'p', 'https://app.datadoghq.com', 'datadoghq.com'],
  ['grafana', 'grafana', null, null, 'ANALYTICS', 'p', 'https://grafana.com', 'grafana.com grafana.net'],
  ['newrelic', 'newrelic', null, null, 'ANALYTICS', 'p', 'https://one.newrelic.com', 'newrelic.com'],
  ['betterstack', 'betterstack', null, 'Uptime', 'ANALYTICS', 'p', 'https://uptime.betterstack.com', 'betterstack.com'],
  ['statuspage', 'statuspage', null, 'Status', 'ANALYTICS', 'p', 'https://manage.statuspage.io', 'statuspage.io'],
  // Payments
  ['stripe', 'stripe', null, null, 'PAYMENTS', 'p', 'https://dashboard.stripe.com', 'stripe.com'],
  ['paypal', 'paypal', 'PayPal', null, 'PAYMENTS', 'p', 'https://www.paypal.com/businessmanage', 'paypal.com'],
  ['lemonsqueezy', 'lemonsqueezy', null, 'Lemon', 'PAYMENTS', 'p', 'https://app.lemonsqueezy.com', 'lemonsqueezy.com'],
  ['paddle', 'paddle', null, null, 'PAYMENTS', 'p', 'https://vendors.paddle.com', 'paddle.com'],
  ['revenuecat', 'revenuecat', 'RevenueCat', null, 'PAYMENTS', 'p', 'https://app.revenuecat.com', 'revenuecat.com'],
  ['wise', 'wise', null, null, 'PAYMENTS', 'p', 'https://wise.com', 'wise.com'],
  ['revolut', 'revolut', null, null, 'PAYMENTS', 'p', 'https://business.revolut.com', 'revolut.com'],
  ['gumroad', 'gumroad', null, null, 'PAYMENTS', 'p', 'https://app.gumroad.com', 'gumroad.com'],
  ['patreon', 'patreon', null, null, 'PAYMENTS', 'p', 'https://www.patreon.com', 'patreon.com'],
  ['buymeacoffee', 'buymeacoffee', 'Buy Me a Coffee', 'Coffee', 'PAYMENTS', 'p', 'https://www.buymeacoffee.com', 'buymeacoffee.com'],
  ['cardcom', null, 'Cardcom', null, 'PAYMENTS', 'p', 'https://secure.cardcom.solutions', 'cardcom.solutions cardcom.co.il'],
  ['greeninvoice', null, 'חשבונית ירוקה', 'חשבונית', 'PAYMENTS', 'p', 'https://app.greeninvoice.co.il', 'greeninvoice.co.il'],
  ['icount', null, 'iCount', null, 'PAYMENTS', 'p', 'https://app.icount.co.il', 'icount.co.il'],
  ['tranzila', null, 'Tranzila', null, 'PAYMENTS', 'p', 'https://my.tranzila.com', 'tranzila.com'],
  // Email & messaging
  ['resend', 'resend', null, null, 'COMMS', 'p', 'https://resend.com', 'resend.com'],
  ['mailgun', 'mailgun', null, null, 'COMMS', 'p', 'https://app.mailgun.com', 'mailgun.com'],
  ['mailchimp', 'mailchimp', 'Mailchimp', null, 'COMMS', 'p', 'https://admin.mailchimp.com', 'mailchimp.com'],
  ['brevo', 'brevo', null, null, 'COMMS', 'p', 'https://app.brevo.com', 'brevo.com'],
  ['sendgrid', null, 'SendGrid', null, 'COMMS', 'p', 'https://app.sendgrid.com', 'sendgrid.com'],
  ['twilio', null, 'Twilio', null, 'COMMS', 'p', 'https://console.twilio.com', 'twilio.com'],
  ['onesignal', null, 'OneSignal', null, 'COMMS', 'p', 'https://dashboard.onesignal.com', 'onesignal.com'],
  ['slack', null, 'Slack', null, 'COMMS', 'p', 'https://app.slack.com', 'slack.com'],
  ['discord', 'discord', null, null, 'COMMS', 'p', 'https://discord.com/app', 'discord.com'],
  ['telegram', 'telegram', null, null, 'COMMS', 'p', 'https://web.telegram.org', 'telegram.org t.me'],
  ['whatsapp', 'whatsapp', 'WhatsApp', null, 'COMMS', 'p', 'https://business.facebook.com/wa/manage', 'whatsapp.com wa.me'],
  ['gmeet', 'googlemeet', 'Meet', null, 'COMMS', 'g', 'https://meet.google.com', 'meet.google.com'],
  ['zoom', 'zoom', null, null, 'COMMS', 'p', 'https://zoom.us', 'zoom.us'],
  ['calendly', 'calendly', null, null, 'COMMS', 'p', 'https://calendly.com', 'calendly.com'],
  // Auth
  ['clerk', 'clerk', null, null, 'AUTH', 'p', 'https://dashboard.clerk.com', 'clerk.com clerk.dev'],
  ['auth0', 'auth0', null, null, 'AUTH', 'p', 'https://manage.auth0.com', 'auth0.com'],
  ['okta', 'okta', null, null, 'AUTH', 'p', 'https://login.okta.com', 'okta.com'],
  // Design
  ['figma', 'figma', null, null, 'DESIGN', 'p', 'https://www.figma.com/files', 'figma.com'],
  ['framer', 'framer', null, null, 'DESIGN', 'p', 'https://framer.com/projects', 'framer.com'],
  ['webflow', 'webflow', null, null, 'DESIGN', 'p', 'https://webflow.com/dashboard', 'webflow.com'],
  ['canva', null, 'Canva', null, 'DESIGN', 'p', 'https://www.canva.com', 'canva.com'],
  ['dribbble', 'dribbble', null, null, 'DESIGN', 'p', 'https://dribbble.com', 'dribbble.com'],
  ['behance', 'behance', null, null, 'DESIGN', 'p', 'https://www.behance.net', 'behance.net'],
  ['unsplash', 'unsplash', null, null, 'DESIGN', 'p', 'https://unsplash.com', 'unsplash.com'],
  ['lottiefiles', 'lottiefiles', null, 'Lottie', 'DESIGN', 'p', 'https://app.lottiefiles.com', 'lottiefiles.com'],
  ['rive', 'rive', null, null, 'DESIGN', 'p', 'https://rive.app', 'rive.app'],
  ['miro', 'miro', null, null, 'DESIGN', 'p', 'https://miro.com/app/dashboard', 'miro.com'],
  ['excalidraw', 'excalidraw', null, null, 'DESIGN', 'p', 'https://excalidraw.com', 'excalidraw.com'],
  ['sketch', 'sketch', null, null, 'DESIGN', 'p', 'https://www.sketch.com', 'sketch.com'],
  ['gfonts', 'googlefonts', 'Google Fonts', 'Fonts', 'DESIGN', 'p', 'https://fonts.google.com', 'fonts.google.com'],
  ['fontawesome', 'fontawesome', 'Font Awesome', 'Icons', 'DESIGN', 'p', 'https://fontawesome.com', 'fontawesome.com'],
  // Work & automation
  ['notion', 'notion', null, null, 'WORK', 'p', 'https://www.notion.so', 'notion.so notion.com'],
  ['trello', 'trello', null, null, 'WORK', 'p', 'https://trello.com', 'trello.com'],
  ['jira', 'jira', null, null, 'WORK', 'p', 'https://id.atlassian.com', 'atlassian.net atlassian.com'],
  ['linear', 'linear', null, null, 'WORK', 'p', 'https://linear.app', 'linear.app'],
  ['asana', 'asana', null, null, 'WORK', 'p', 'https://app.asana.com', 'asana.com'],
  ['clickup', 'clickup', 'ClickUp', null, 'WORK', 'p', 'https://app.clickup.com', 'clickup.com'],
  ['airtable', 'airtable', null, null, 'WORK', 'p', 'https://airtable.com', 'airtable.com'],
  ['n8n', 'n8n', null, null, 'WORK', 'p', 'https://app.n8n.cloud', 'n8n.cloud n8n.io'],
  ['zapier', 'zapier', null, null, 'WORK', 'p', 'https://zapier.com/app/home', 'zapier.com'],
  ['make', 'make', null, null, 'WORK', 'p', 'https://www.make.com', 'make.com'],
  ['loom', 'loom', null, null, 'WORK', 'p', 'https://www.loom.com/looms', 'loom.com'],
  // Domains
  ['godaddy', 'godaddy', 'GoDaddy', null, 'DOMAINS', 'p', 'https://dcc.godaddy.com', 'godaddy.com'],
  ['namecheap', 'namecheap', null, null, 'DOMAINS', 'p', 'https://ap.www.namecheap.com', 'namecheap.com'],
  ['porkbun', 'porkbun', null, null, 'DOMAINS', 'p', 'https://porkbun.com/account/domainsSpeedy', 'porkbun.com'],
  ['hostinger', 'hostinger', null, null, 'DOMAINS', 'p', 'https://hpanel.hostinger.com', 'hostinger.com'],
  // Files & media
  ['cloudinary', 'cloudinary', null, null, 'FILES', 'p', 'https://console.cloudinary.com', 'cloudinary.com'],
  ['dropbox', 'dropbox', null, null, 'FILES', 'p', 'https://www.dropbox.com/home', 'dropbox.com'],
  ['box', 'box', null, null, 'FILES', 'p', 'https://app.box.com', 'box.com'],
  ['mega', 'mega', 'MEGA', null, 'FILES', 'p', 'https://mega.nz', 'mega.nz mega.io'],
  ['wetransfer', 'wetransfer', 'WeTransfer', null, 'FILES', 'p', 'https://wetransfer.com', 'wetransfer.com'],
  ['icloud', 'icloud', 'iCloud', null, 'FILES', 'p', 'https://www.icloud.com', 'icloud.com'],
  // Passwords
  ['bitwarden', 'bitwarden', null, null, 'PASSWORDS', 'p', 'https://vault.bitwarden.com', 'bitwarden.com'],
  ['1password', '1password', null, null, 'PASSWORDS', 'p', 'https://my.1password.com', '1password.com'],
  ['lastpass', 'lastpass', null, null, 'PASSWORDS', 'p', 'https://lastpass.com/vault', 'lastpass.com'],
  // Social
  ['instagram', 'instagram', null, null, 'SOCIAL', 'p', 'https://www.instagram.com', 'instagram.com'],
  ['facebook', 'facebook', null, null, 'SOCIAL', 'p', 'https://www.facebook.com', 'facebook.com'],
  ['metabusiness', 'meta', 'Meta Business', 'Meta', 'SOCIAL', 'p', 'https://business.facebook.com', 'business.facebook.com'],
  ['tiktok', 'tiktok', 'TikTok', null, 'SOCIAL', 'p', 'https://www.tiktok.com', 'tiktok.com'],
  ['x', 'x', 'X', null, 'SOCIAL', 'p', 'https://x.com', 'x.com twitter.com'],
  ['linkedin', null, 'LinkedIn', null, 'SOCIAL', 'p', 'https://www.linkedin.com', 'linkedin.com'],
  ['youtube', 'youtube', 'YouTube', null, 'SOCIAL', 'g', 'https://studio.youtube.com', 'youtube.com'],
  // General (favicon of the link's own site)
  ['site', null, 'האתר', null, 'GENERAL', 'p', 'https://', ''],
  ['link', null, 'קישור אחר', 'קישור', 'GENERAL', 'p', 'https://', ''],
];

// Simple-icons paths use compact SVG syntax (e.g. arc flags written as "01"); re-serialize with explicit separators
// so every Android version's VectorDrawable parser reads them the same way.
function normalizePath(d) {
  const out = [];
  let i = 0, cmd = null, arg = 0;
  const num = /^[-+]?(?:\d*\.\d+|\d+\.?\d*)(?:[eE][-+]?\d+)?/;
  while (i < d.length) {
    const c = d[i];
    if (/[\s,]/.test(c)) { i++; continue; }
    if (/[MmZzLlHhVvCcSsQqTtAa]/.test(c)) { cmd = c; out.push(c); arg = 0; i++; continue; }
    if ((cmd === 'a' || cmd === 'A') && (arg % 7 === 3 || arg % 7 === 4)) { out.push(c); i++; arg++; continue; }
    const m = num.exec(d.slice(i));
    if (!m) throw new Error(`bad path near "${d.slice(i, i + 20)}"`);
    out.push(m[0]); i += m[0].length; arg++;
  }
  return out.join(' ');
}

const drawableDir = path.join(root, 'app/src/main/res/drawable');
for (const f of fs.readdirSync(drawableDir)) if (f.startsWith('si_')) fs.unlinkSync(path.join(drawableDir, f));

const kt = [];
const missing = [];
const written = new Set();
for (const [id, slug, title, short, cat, route, url, matches] of S) {
  let icon = 'R.drawable.ic_s_globe', brand = 'false', color = 'null', name = title;
  const si = slug && bySlug[slug];
  if (slug && !si) missing.push(slug);
  if (si) {
    const svg = fs.readFileSync(path.join(pkg, 'icons', `${slug}.svg`), 'utf8');
    const d = /<path d="([^"]+)"/.exec(svg)[1];
    const res = 'si_' + slug.replace(/[^a-z0-9]/g, '_');
    if (!written.has(res)) {
      fs.writeFileSync(path.join(drawableDir, res + '.xml'),
        `<?xml version="1.0" encoding="utf-8"?>\n<!-- ${si.title} from simple-icons ${siVersion} (CC0). Generated by tools/gen-services.mjs. -->\n` +
        `<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">\n` +
        `    <path android:fillColor="#FFFFFFFF" android:pathData="${normalizePath(d)}"/>\n</vector>\n`);
      written.add(res);
    }
    icon = `R.drawable.${res}`; brand = 'true'; color = `0xFF${si.hex.toUpperCase()}.toInt()`;
    name = title ?? si.title;
  }
  const q = (s) => JSON.stringify(s);
  const r = { g: 'Route.GOOGLE', v: 'Route.VERCEL', p: 'Route.PLAIN' }[route];
  const m = matches ? matches.split(' ').map(q).join(', ') : '';
  kt.push(`    Service(${q(id)}, ${q(name)}, ${q(short ?? name)}, ${icon}, ${brand}, ${color}, ${r}, ${q(url)}, listOf(${m}), Category.${cat}),`);
}

fs.writeFileSync(path.join(root, 'app/src/main/java/com/yonidev/ydeck/data/ServiceCatalog.kt'),
  `// Generated by tools/gen-services.mjs from simple-icons ${siVersion}. Edit the table there, not here.\n` +
  `package com.yonidev.ydeck.data\n\nimport com.yonidev.ydeck.R\n\n` +
  `internal val CATALOG: List<Service> = listOf(\n${kt.join('\n')}\n)\n`);

console.log(`${S.length} services, ${written.size} brand icons, simple-icons ${siVersion}`);
if (missing.length) console.log('slugs not in this simple-icons version (favicon fallback):', missing.join(' '));
