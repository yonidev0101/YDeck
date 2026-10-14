// Home page. Everything shown is the app's own logic: the widget's glass and colours (DeckWidget.kt), which
// account and Chrome channel each link opens in (Router.kt), how a pasted URL is recognised (Service.detect).
(function () {
  var D = JSON.parse(document.getElementById('data').textContent);
  var reduce = matchMedia('(prefers-reduced-motion: reduce)').matches;
  function $(s, r) { return (r || document).querySelector(s); }
  function $$(s, r) { return [].slice.call((r || document).querySelectorAll(s)); }
  function clamp(v) { return Math.max(0, Math.min(1, v)); }
  function ease(p) { return p < .5 ? 4 * p * p * p : 1 - Math.pow(-2 * p + 2, 3) / 2; }
  function rgb(hex) { return [1, 3, 5].map(function (i) { return parseInt(hex.substr(i, 2), 16); }); }
  function lum(hex) {
    var c = rgb(hex).map(function (v) { v /= 255; return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); });
    return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
  }
  /** BrandColors.adapt(dark = false): pale marks are darkened so they read on the light glass. */
  function adapt(hex) { if (lum(hex) <= 0.55) return hex; return 'rgb(' + rgb(hex).map(function (v) { return Math.round(v * 0.65); }).join(',') + ')'; }
  /** The day glass: white at 68%, washed with 14% of the project colour (a near-black project stays neutral). */
  function glass(hex) {
    var t = lum(hex) < 0.03 ? 0 : 0.14, a = t + 0.68 * (1 - t), c = rgb(hex);
    return 'rgba(' + c.map(function (v) { return Math.round((v * t + 255 * 0.68 * (1 - t)) / a); }).join(',') + ',' + a.toFixed(3) + ')';
  }
  function icon(id, color) {
    var p = D.icons[id];
    if (!p) return '<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="9" fill="none" stroke="' + (color || '#8A8A90') + '" stroke-width="1.8"/><path d="M3 12h18M12 3c2.8 3 2.8 15 0 18M12 3c-2.8 3-2.8 15 0 18" fill="none" stroke="' + (color || '#8A8A90') + '" stroke-width="1.8"/></svg>';
    return '<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="' + (color || 'currentColor') + '" d="' + p + '"/></svg>';
  }
  var MORE = '<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="5" cy="12" r="2" fill="#3C3C43"/><circle cx="12" cy="12" r="2" fill="#3C3C43"/><circle cx="19" cy="12" r="2" fill="#3C3C43"/></svg>';
  function logoStyle(p) { return 'background:' + p.color; }

  // ── the widget, as DeckWidget draws it: 4×2, 2×2 or the one-row strip ──
  function widget(root, p, size, hidden) {
    root.className = 'widget s' + size;
    root.style.backgroundColor = glass(p.color);
    var n = size === '42' ? 7 : size === '22' ? 3 : 4;
    var marks = p.links.slice(0, n).map(function (id, k) {
      var s = D.services[id];
      return '<div class="mk" data-k="' + k + '"' + (hidden ? ' style="opacity:0"' : '') + '>' + icon(id, k === 0 ? '#1D1D1F' : adapt(s.color)) + '<span>' + s.short + '</span></div>';
    }).join('') + '<div class="mk" data-k="' + n + '"' + (hidden ? ' style="opacity:0"' : '') + '>' + MORE + '<span>עוד</span></div>';
    root.innerHTML = '<div class="head"><div class="plogo" style="' + logoStyle(p) + '">' + p.mono + '</div><div class="who"><b>' + p.name + '</b><span>' + p.email + '</span></div>' +
      '<div class="dots">' + D.projects.map(function (q) { return '<i' + (q === p ? ' class="on" style="background:' + (lum(p.color) < 0.03 ? '#1D1D1F' : p.color) + '"' : '') + '></i>'; }).join('') + '</div></div>' +
      '<div class="grid"><span class="slogo" style="' + logoStyle(p) + '">' + p.mono + '</span>' + marks + '</div>';
  }

  // ── nav hairline, and sections that rise in ──
  var nav = $('.nav');
  addEventListener('scroll', function () { nav.classList.toggle('scrolled', scrollY > 8); }, { passive: true });
  var rio = new IntersectionObserver(function (es) {
    es.forEach(function (e) { if (e.isIntersecting) { e.target.classList.add('in'); rio.unobserve(e.target); } });
  }, { threshold: .15 });
  $$('.rise').forEach(function (e) { rio.observe(e); });
  requestAnimationFrame(function () { $$('.hero .rise').forEach(function (e, i) { setTimeout(function () { e.classList.add('in'); }, 80 + i * 120); }); });

  // ── order: the tabs fold into the widget as the page scrolls ──
  (function () {
    var sec = $('#order'), screen = $('.screen', sec), w = $('.widget', screen), nova = D.projects[0];
    widget(w, nova, '42', true);
    var cells = $$('.mk', w);
    var START = [[.1, .14], [.36, .09], [.64, .12], [.9, .18], [.07, .4], [.93, .44], [.14, .68], [.86, .7], [.28, .86], [.72, .88], [.5, .07],
      [.22, .3], [.78, .3], [.08, .88], [.94, .9], [.52, .93], [.4, .2], [.6, .24]];
    var tabs = D.tabs.map(function (t, i) {
      var a = D.accounts[t[2]], e = document.createElement('div');
      e.className = 'tab';
      e.innerHTML = icon(t[0], adapt(D.services[t[0]].color)) + '<span>' + t[1] + '</span><span class="who" style="background:' + a.color + '">' + a.letter + '</span>';
      screen.appendChild(e);
      return { el: e, s: START[i % START.length], rot: (i % 2 ? 1 : -1) * (3 + (i * 7) % 6), cell: i % cells.length, i: i };
    });
    var geo = null;
    function measure() {
      var r = screen.getBoundingClientRect();
      geo = { w: r.width, h: r.height, cells: cells.map(function (c) { var b = c.getBoundingClientRect(); return [b.left - r.left + b.width / 2, b.top - r.top + 22]; }) };
      tabs.forEach(function (t) { t.w = t.el.offsetWidth; t.h = t.el.offsetHeight; });
    }
    function frame() {
      if (!geo) measure();
      var r = sec.getBoundingClientRect(), p = reduce ? 1 : clamp(-r.top / (r.height - innerHeight));
      var N = tabs.length, lit = cells.map(function () { return 0; });
      w.style.opacity = 0.25 + 0.75 * clamp((p - 0.02) / 0.2);
      tabs.forEach(function (t) {
        var q = ease(clamp((p - (0.06 + t.i * (0.5 / N))) / 0.3));
        var sx = Math.min(Math.max(t.s[0] * geo.w, t.w / 2 + 6), geo.w - t.w / 2 - 6), sy = t.s[1] * geo.h, c = geo.cells[t.cell];
        var x = sx + (c[0] - sx) * q, y = sy + (c[1] - sy) * q, k = 1 - 0.75 * q;
        t.el.style.transform = 'translate(' + (x - t.w / 2) + 'px,' + (y - t.h / 2) + 'px) rotate(' + (t.rot * (1 - q)) + 'deg) scale(' + k + ')';
        t.el.style.opacity = q < 0.7 ? 1 : 1 - (q - 0.7) / 0.3;
        lit[t.cell] = Math.max(lit[t.cell], clamp((q - 0.75) / 0.25));
      });
      cells.forEach(function (c, k) { c.style.opacity = lit[k]; });
      sec.classList.toggle('done', p > 0.8);
    }
    var queued = false;
    function onScroll() { if (queued) return; queued = true; requestAnimationFrame(function () { queued = false; frame(); }); }
    addEventListener('scroll', onScroll, { passive: true });
    addEventListener('resize', function () { geo = null; onScroll(); });
    if (document.fonts) document.fonts.ready.then(function () { geo = null; frame(); });
    frame();
  })();

  // ── routing: every link knows its account ──
  (function () {
    var box = $('#route'), seg = $('.seg', box), rows = $('.rows', box), CH = { 'Chrome': '#1A73E8', 'Chrome Beta': '#F2A900', 'Chrome Dev': '#5F6368', 'Chrome Canary': '#E37400' };
    seg.innerHTML = D.projects.map(function (p, i) { return '<button type="button" data-i="' + i + '"' + (i ? '' : ' class="on"') + '><i style="background:' + p.color + '">' + p.mono + '</i>' + p.name + '</button>'; }).join('');
    function show(i) {
      var p = D.projects[i];
      $$('button', seg).forEach(function (b, k) { b.classList.toggle('on', k === i); });
      rows.innerHTML = p.links.slice(0, 6).map(function (id, k) {
        var s = D.services[id], to;
        if (s.route === 'google') to = '<span class="chip acct">' + p.email + '</span>';
        else if (s.route === 'vercel') to = '<span class="chip chan" style="--c:' + CH[p.vercel] + '"><span class="ltr">' + p.vercel + '</span></span>';
        else to = '<span class="chip chan" style="--c:' + CH.Chrome + '">Chrome</span>';
        return '<li style="animation-delay:' + (k * 45) + 'ms"><span class="si">' + icon(id, adapt(s.color)) + '</span><span class="nm">' + s.label + '<small>' + s.cat + '</small></span><span class="to">' + to + '</span></li>';
      }).join('');
    }
    seg.addEventListener('click', function (e) { var b = e.target.closest('button'); if (b) { auto = false; show(+b.dataset.i); } });
    var auto = true, cur = 0;
    show(0);
    if (!reduce) setInterval(function () { if (auto && isVisible(box)) { cur = (cur + 1) % D.projects.length; show(cur); } }, 3600);
  })();
  function isVisible(el) { var r = el.getBoundingClientRect(); return r.bottom > 0 && r.top < innerHeight; }

  // ── sizes ──
  (function () {
    var tile = $('.t-size'), w = $('.widget', tile), seg = $('.seg', tile), sizes = ['42', '22', '41'], cur = 0, auto = true;
    function show(i) { cur = i; widget(w, D.projects[0], sizes[i]); $$('button', seg).forEach(function (b, k) { b.classList.toggle('on', k === i); }); }
    seg.addEventListener('click', function (e) { var b = e.target.closest('button'); if (b) { auto = false; show(+b.dataset.i); } });
    show(0);
    if (!reduce) setInterval(function () { if (auto && isVisible(tile)) show((cur + 1) % sizes.length); }, 2800);
  })();

  // ── paste: Service.detect, the most specific pattern wins ──
  (function () {
    var tile = $('.t-paste'), input = $('input', tile), res = $('.res', tile);
    var PATTERNS = [];
    Object.keys(D.services).forEach(function (id) { D.services[id].match.forEach(function (m) { PATTERNS.push([id, m]); }); });
    function detect(raw) {
      var u; try { u = new URL(/^[a-z]+:\/\//i.test(raw) ? raw : 'https://' + raw); } catch (e) { return null; }
      var host = u.hostname.toLowerCase().replace(/^www\./, ''), path = u.pathname, best = null;
      if (!host.includes('.')) return null;
      PATTERNS.forEach(function (pm) {
        var h = pm[1].split('/')[0], pre = pm[1].indexOf('/') > 0 ? '/' + pm[1].split('/').slice(1).join('/') : '';
        if ((host === h || host.endsWith('.' + h)) && path.indexOf(pre) === 0 && (!best || pm[1].length > best[1].length)) best = pm;
      });
      return best ? best[0] : 'link';
    }
    var last = null;
    function render(id) {
      if (id === last) return; last = id;
      if (!id) { res.innerHTML = ''; return; }
      var s = D.services[id], how = s.route === 'google' ? 'נפתח בחשבון הגוגל של הפרויקט' : s.route === 'vercel' ? 'נפתח בערוץ Chrome של החשבון' : 'נפתח ב־Chrome';
      res.innerHTML = '<span class="si">' + icon(id, s.brand ? adapt(s.color) : null) + '</span><span><b>' + (id === 'link' ? 'קישור אחר' : s.label) + '</b><span>' +
        (id === 'link' ? 'עם האייקון של האתר עצמו' : s.cat + ' · ' + how) + '</span></span>';
      res.classList.remove('pop'); void res.offsetWidth; res.classList.add('pop');
    }
    input.addEventListener('input', function () { typing = false; render(input.value.trim() ? detect(input.value.trim()) : null); });
    input.addEventListener('focus', function () { typing = false; });
    var EX = ['https://console.firebase.google.com/project/nova', 'https://vercel.com/nova/web', 'https://github.com/nova/app', 'https://dashboard.stripe.com/payments',
      'https://supabase.com/dashboard/project/nova', 'https://play.google.com/console/developers', 'https://nova.dev/admin'];
    var typing = !reduce, n = 0;
    async function loop() {
      while (typing) {
        if (!isVisible(tile)) { await wait(600); continue; }
        var t = EX[n++ % EX.length]; input.value = ''; render(null);
        for (var i = 1; i <= t.length && typing; i++) { input.value = t.slice(0, i); if (i > 14) render(detect(input.value)); await wait(i < 9 ? 40 : 24); }
        await wait(2200);
      }
    }
    function wait(ms) { return new Promise(function (r) { setTimeout(r, ms); }); }
    if (typing) loop(); else { input.value = EX[0]; render(detect(EX[0])); }
  })();

  // ── copy ──
  $$('.copy').forEach(function (b) {
    b.addEventListener('click', function () {
      var v = $('code', b).textContent, st = $('.st', b);
      (navigator.clipboard ? navigator.clipboard.writeText(v) : Promise.reject()).catch(function () {}).then(function () {
        b.classList.add('done'); st.textContent = 'הועתק';
        setTimeout(function () { b.classList.remove('done'); st.textContent = 'העתקה'; }, 1600);
      });
    });
  });

  // ── the end: the logo dealt on indigo ──
  var c = $('#endmark'), played = false;
  new IntersectionObserver(function (es) {
    es.forEach(function (e) {
      if (!e.isIntersecting || played) return;
      played = true;
      YDeckSting.play(c, function (w, h) { return { ground: '#3D35E0', cx: w / 2, cy: h / 2 + h * .02, k: Math.min(w / 560, h / 520) * .92 }; }, reduce ? 0 : 2.3);
    });
  }, { threshold: .5 }).observe(c);
})();
