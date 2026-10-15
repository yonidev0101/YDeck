// YDeck relay: a dumb, blind mailbox between one computer and one phone.
//
// The computer and the phone share a 32-byte secret that only ever travels in the QR code on the computer's screen.
// From it each side derives (HKDF-SHA256) a box id, an access token and an encryption key. This server sees the box
// id, a hash of the token and sealed bytes (AES-256-GCM): it can't read them, and can't forge them either.
// It only stores and hands back; nothing it holds is ever executed by either side.
//
//   PUT    /v1/box/:id/state   the computer's latest snapshot (replaces the previous one)
//   GET    /v1/box/:id/state   the phone reads it (ETag / If-None-Match → 304)
//   GET    /v1/box/:id/meta    when the state was written and when the phone last read it
//   POST   /v1/box/:id/inbox   the phone leaves a sealed note (a decision, a next step) for the computer
//   GET    /v1/box/:id/inbox   the computer reads the notes waiting
//   DELETE /v1/box/:id/inbox/:n   the computer took that note
//   DELETE /v1/box/:id         unpair: everything in the box is gone
//
// The first PUT claims a box for its token; every later call must present the same token.

const MAX_STATE = 1_500_000; // bytes; projects + logos are ~300 KB today
const BOX = /^[0-9a-f]{32}$/;
const MAX_NOTE = 16_000; // bytes, sealed
const MAX_NOTES = 200;

export default {
  async fetch(req, env) {
    const url = new URL(req.url);
    if (url.pathname === '/' || url.pathname === '/v1') return text('YDeck relay. Sealed boxes only.', 200);
    const m = url.pathname.match(/^\/v1\/box\/([^/]+)(?:\/(state|meta|inbox(?:\/[0-9a-z-]{1,40})?))?$/);
    if (!m || !BOX.test(m[1])) return text('not found', 404);
    const auth = req.headers.get('authorization') || '';
    const token = auth.startsWith('Bearer ') ? auth.slice(7) : '';
    if (token.length < 40 || token.length > 100) return text('unauthorized', 401);
    const stub = env.BOXES.get(env.BOXES.idFromName(m[1]));
    return stub.fetch(new Request(`https://box/${m[2] || ''}`, req));
  },
};

export class Box {
  constructor(state) {
    this.sql = state.storage.sql;
    this.storage = state.storage;
    this.table();
  }

  // deleteAll() drops the table too: made again, so an emptied box answers "unauthorized", not an error.
  table() { this.sql.exec('CREATE TABLE IF NOT EXISTS box (k TEXT PRIMARY KEY, v BLOB)'); }

  get(k) {
    const rows = this.sql.exec('SELECT v FROM box WHERE k = ?', k).toArray();
    return rows.length ? rows[0].v : null;
  }
  set(k, v) { this.sql.exec('INSERT OR REPLACE INTO box (k, v) VALUES (?, ?)', k, v); }

  async fetch(req) {
    const what = new URL(req.url).pathname.slice(1);
    const token = req.headers.get('authorization').slice(7);
    const hash = await sha256(token);
    const owner = this.get('token');

    if (req.method === 'PUT' && what === 'state') {
      if (owner && owner !== hash) return text('unauthorized', 401);
      const body = new Uint8Array(await req.arrayBuffer());
      if (body.length === 0 || body.length > MAX_STATE) return text('too large', 413);
      if (!owner) this.set('token', hash);
      const etag = '"' + (await sha256(body)).slice(0, 32) + '"';
      this.set('state', body);
      this.set('etag', etag);
      this.set('stateAt', String(Date.now()));
      // A box nobody touches for 60 days empties itself.
      await this.storage.setAlarm(Date.now() + 60 * 86400_000);
      return json({ ok: true, etag });
    }

    if (!owner || owner !== hash) return text('unauthorized', 401);

    if (req.method === 'GET' && what === 'state') {
      const state = this.get('state');
      if (!state) return text('empty', 404);
      this.set('readAt', String(Date.now()));
      const etag = this.get('etag');
      if (req.headers.get('if-none-match') === etag) return new Response(null, { status: 304, headers: { etag } });
      return new Response(state, { headers: { 'content-type': 'application/octet-stream', etag, 'cache-control': 'no-store' } });
    }

    if (req.method === 'GET' && what === 'meta') {
      return json({ stateAt: num(this.get('stateAt')), readAt: num(this.get('readAt')) });
    }

    if (req.method === 'POST' && what === 'inbox') {
      const body = new Uint8Array(await req.arrayBuffer());
      if (body.length === 0 || body.length > MAX_NOTE) return text('too large', 413);
      const count = this.sql.exec("SELECT COUNT(*) AS n FROM box WHERE k LIKE 'in:%'").toArray()[0].n;
      if (count >= MAX_NOTES) return text('inbox full', 429);
      const n = Date.now().toString(36) + '-' + crypto.randomUUID().slice(0, 8);
      this.set('in:' + n, body);
      return json({ ok: true, n });
    }

    if (req.method === 'GET' && what === 'inbox') {
      const rows = this.sql.exec("SELECT k, v FROM box WHERE k LIKE 'in:%' ORDER BY k").toArray();
      return json({ notes: rows.map((r) => ({ n: r.k.slice(3), sealed: b64(new Uint8Array(r.v)) })) });
    }

    if (req.method === 'DELETE' && what.startsWith('inbox/')) {
      this.sql.exec('DELETE FROM box WHERE k = ?', 'in:' + what.slice(6));
      return json({ ok: true });
    }

    if (req.method === 'DELETE' && what === '') {
      await this.storage.deleteAll();
      this.table();
      return json({ ok: true });
    }

    return text('not found', 404);
  }

  async alarm() { await this.storage.deleteAll(); this.table(); }
}

async function sha256(data) {
  const bytes = typeof data === 'string' ? new TextEncoder().encode(data) : data;
  const d = await crypto.subtle.digest('SHA-256', bytes);
  return [...new Uint8Array(d)].map((b) => b.toString(16).padStart(2, '0')).join('');
}
const b64 = (u8) => { let s = ''; for (let i = 0; i < u8.length; i++) s += String.fromCharCode(u8[i]); return btoa(s); };
const num = (v) => (v == null ? null : Number(v));
const text = (s, status) => new Response(s, { status, headers: { 'content-type': 'text/plain; charset=utf-8' } });
const json = (o) => new Response(JSON.stringify(o), { headers: { 'content-type': 'application/json', 'cache-control': 'no-store' } });
