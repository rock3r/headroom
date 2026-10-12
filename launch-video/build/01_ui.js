// Headroom UI kit: the app rebuilt as live layers. Units are screenshot pixels (1233 x 2673 screen,
// 3 px per dp). Every builder takes a theme object from build/themes.json.
var UI = globalThis.UI = {};
UI.TH = JSON.parse(api.readFromFile(H.DIR + '/build/themes.json'));
UI.SW = 1233; UI.SH = 2673;
UI.X = function (x) { return x - UI.SW / 2; };
UI.Y = function (y) { return UI.SH / 2 - y; };
UI.MONO = 'JetBrains Mono';
// Text in the app's styles (Google Sans Flex). kind: display | title | body | label
UI.t = function (name, str, size, o) {
  o = o || {};
  var t = cav.text(name, str, size, { parent: o.parent, x: o.x || 0, y: o.y || 0, align: o.align || 'left', color: o.color || '#000000', font: H.FONT, style: 'Regular', in: o.in, out: o.out });
  var k = o.kind || 'body';
  var ax = k === 'display' ? { wght: 860, wdth: 116, ROND: 100 } : k === 'title' ? { wght: 700, wdth: 104, ROND: 70 } : k === 'label' ? { wght: 620, wdth: 100, ROND: 50 } : { wght: 420, wdth: 100, ROND: 30 };
  if (o.wght) ax.wght = o.wght;
  cav.axes(t, ax);
  return t;
};
// Rounded rectangle from a top-left box in local coordinates (y down), placed in a parent whose origin is the box's reference.
UI.box = function (name, x, y, w, h, r, fill, o) {
  o = o || {};
  return cav.rect(name, w, h, { parent: o.parent, x: x + w / 2, y: -(y + h / 2), radius: r, fill: fill, opacity: o.opacity, stroke: o.stroke, in: o.in, out: o.out });
};
// Quota bar: full-length track and fill, both trimmed, so the fill can grow and the track follows it.
// Proportions follow M3 Expressive's LinearWavyProgressIndicator: 4 dp stroke and track, 4 dp gap,
// 3 dp wave amplitude, 32 dp wavelength (so amp = 0.75 th, wavelength = 8 th).
UI.bar = function (name, x0, x1, y, o) {
  o = o || {};
  var th = o.th || 15, gapPct = (o.gap || th) / (x1 - x0) * 100;
  var g = cav.group(name, { parent: o.parent });
  var tr = cav.line(name + '_track', [x0, -y], [x1, -y], { parent: g, stroke: { color: o.track, width: th, cap: 'round', trim: true } });
  var amp = o.amp || th * 0.75, len = o.len || th * 8;
  var flat = cav.line(name + '_flat', [x0, -y], [x1, -y], { parent: g, stroke: { color: o.color, width: th, cap: 'round', trim: true } });
  var wave = cav.path(name + '_wave', UI.wavePts(x0, x1, y, amp, len, null), { parent: g, noFill: true, smooth: true, stroke: { color: o.color, width: th, cap: 'round', trim: true } });
  var wg = cav.group(name + '_wg', { parent: g });
  var dot = cav.circle(name + '_dot', th * 0.45, { parent: g, fill: o.color, x: x1 + th * 1.2, y: -y });
  var tick = o.pace === undefined ? null : cav.rect(name + '_tick', th * 0.5, th * 3.5, { parent: g, fill: o.tickColor || '#171b22', radius: th * 0.25, x: x0 + (x1 - x0) * o.pace / 100, y: -y });
  var b = { g: g, wg: wg, track: tr, flat: flat, wave: wave, dot: dot, tick: tick, gapPct: gapPct, name: name, color: o.color, th: th, geo: [x0, x1, y, amp, len], log: [], init: o.pct || 0 };
  UI.barSet(b, null, o.pct || 0, !!o.wavy);
  return b;
};
// Wave points; env(u) scales the amplitude along the bar (u = 0 at the start, 1 at the end).
UI.wavePts = function (x0, x1, y, amp, len, env) {
  var pts = [], N = Math.max(160, Math.round((x1 - x0) / len * 24));
  for (var i = 0; i <= N; i++) { var u = i / N, x = x0 + (x1 - x0) * u; pts.push([x, -y + amp * (env ? env(u) : 1) * Math.sin((x - x0) / len * Math.PI * 2)]); }
  return pts;
};
// Set (frame null) or key a bar's fill at a frame.
UI.barSet = function (b, f, pct, wavy, ease) {
  var ts = Math.min(100, pct + (pct > 0.5 ? b.gapPct : 0));
  if (f === null || f === undefined) {
    b.init = pct;
    api.set(b.flat, { 'stroke.trimEnd': pct }); api.set(b.wave, { 'stroke.trimEnd': pct });
    api.set(b.track, { 'stroke.trimStart': ts, 'stroke.trimEnd': 100 });
    if (wavy !== undefined) { api.set(b.wave, { opacity: wavy ? 100 : 0 }); api.set(b.flat, { opacity: wavy ? 0 : 100 }); }
    return;
  }
  if (b.log) b.log.push([f, pct, ease || 'outCubic']);
  cav.key(b.flat, 'stroke.trimEnd', [[f, pct, ease || 'outCubic']]);
  cav.key(b.wave, 'stroke.trimEnd', [[f, pct, ease || 'outCubic']]);
  cav.key(b.track, 'stroke.trimStart', [[f, ts, ease || 'outCubic']]);
};
// Flat -> wavy with the amplitude growing from the left end to the right, like the indicator easing
// into its wave. Call it after the bar's fill keys: the in-between shapes replay them.
UI.barWavyGrow = function (b, f, steps, per) {
  steps = steps || 10; per = per || 2;
  var G = b.geo, soft = 0.45;
  for (var k = 1; k < steps; k++) {
    var front = k / steps * (1 + soft);
    var env = (function (fr) { return function (u) { var t = Math.max(0, Math.min(1, (fr - u) / soft)); return t * t * (3 - 2 * t); }; })(front);
    var w = cav.path(b.name + '_wg' + k, UI.wavePts(G[0], G[1], G[2], G[3], G[4], env), { parent: b.wg, noFill: true, smooth: true, stroke: { color: b.color, width: b.th, cap: 'round', trim: true } });
    api.set(w, { 'stroke.trimEnd': b.init });
    b.log.forEach(function (e) { cav.key(w, 'stroke.trimEnd', [[e[0], e[1], e[2]]]); });
    cav.set(w, { in: f + (k - 1) * per, out: f + k * per - 1 });
  }
  cav.key(b.flat, 'opacity', [[f - 1, 100, 'linear'], [f, 0]]);
  cav.key(b.wave, 'opacity', [[f + (steps - 1) * per - 1, 0, 'linear'], [f + (steps - 1) * per, 100]]);
};
UI.barTween = function (b, f0, f1, p0, p1, ease) { UI.barSet(b, f0, p0, undefined, ease || 'inOutCubic'); UI.barSet(b, f1, p1, undefined, 'linear'); };
UI.barWavy = function (b, f, on, dur) {
  dur = dur || 8;
  cav.key(b.wave, 'opacity', [[f, on ? 0 : 100, 'inOutCubic'], [f + dur, on ? 100 : 0]]);
  cav.key(b.flat, 'opacity', [[f, on ? 100 : 0, 'inOutCubic'], [f + dur, on ? 0 : 100]]);
};
UI.barColor = function (b, f, col, dur) {
  [b.flat, b.wave, b.dot].forEach(function (l) {
    var a = l === b.dot ? 'fill' : 'stroke.strokeColor';
    var cur = '#305eb7';
    try { cur = l === b.dot ? cav.hex(api.get(l, 'material.materialColor')) : cav.hex(api.get(l, 'stroke.strokeColor')); } catch (e) {}
    cav.key(l, a, [[f, cur, 'inOutCubic'], [f + (dur || 10), col]]);
  });
};
// Provider avatar (cookie in the provider colour, white logo).
UI.avatar = function (name, key, r, o) {
  o = o || {};
  var g = cav.group(name, { parent: o.parent, x: o.x || 0, y: o.y || 0 });
  H.cookie(name + '_bg', r, { parent: g, fill: o.bg || H.P[key][0], lobes: 9, amp: 0.06 });
  H.logo(name + '_logo', key, r * 1.08, { parent: g, color: o.fg || '#ffffff' });
  return g;
};
// Account card, 1137 x 478, local origin at its top-left corner (y down handled by UI.box/negative y).
UI.card = function (name, th, d, o) {
  o = o || {};
  var g = cav.group(name, { parent: o.parent, x: o.x || 0, y: o.y || 0 });
  var W = 1137, Hh = d.session ? 478 : 400;
  var bg = UI.box(name + '_bg', 0, 0, W, Hh, 90, th.card, { parent: g });
  var av = UI.avatar(name + '_av', d.key, 54, { parent: g, x: 102, y: -113 });
  UI.t(name + '_name', d.name, 50, { parent: g, x: 192, y: -88, color: th.onSurface, kind: 'title' });
  UI.t(name + '_plan', d.plan, 40, { parent: g, x: 192, y: -150, color: th.onSurfaceVar });
  var pct = UI.t(name + '_pct', d.pct + '%', 86, { parent: g, x: 1088, y: -88, align: 'right', color: th.onSurface, kind: 'display' });
  UI.t(name + '_used', d.unit || 'weekly used', 34, { parent: g, x: 1088, y: -163, align: 'right', color: th.onSurfaceVar, kind: 'label' });
  UI.t(name + '_wl', 'Weekly', 38, { parent: g, x: 48, y: -238, color: th.onSurfaceVar, kind: 'label' });
  var BX = d.x0 || 255;
  var wb = UI.bar(name + '_wb', BX, 790, 238, { parent: g, color: th.primary, track: th.track, pct: d.pct, pace: d.pace, wavy: d.wavy, tickColor: th.onSurface });
  UI.t(name + '_ww', d.when, 36, { parent: g, x: 1088, y: -238, align: 'right', color: th.onSurface, kind: 'label' });
  var sb = null;
  if (d.session) {
    UI.t(name + '_sl', d.session.label || 'Session', 38, { parent: g, x: 48, y: -315, color: th.onSurfaceVar, kind: 'label' });
    sb = UI.bar(name + '_sb', BX, d.session.x1 || 862, 315, { parent: g, color: th.primary, track: th.track, pct: d.session.pct, pace: d.session.pace, tickColor: th.onSurface });
    UI.t(name + '_sw', d.session.when, d.session.x1 ? 36 : 38, { parent: g, x: 1088, y: -315, align: 'right', color: th.onSurface, kind: 'label' });
  }
  var chip = null;
  if (d.chip) {
    var cy = d.session ? 367 : 290;
    chip = cav.group(name + '_chip', { parent: g, x: 48, y: -cy });
    var over = d.chip.over;
    var cw = 56 + d.chip.text.length * 17.5;
    UI.box(name + '_chipbg', 0, 0, cw, 72, 24, over ? th.chip : th.chipUnder, { parent: chip });
    UI.t(name + '_chipt', d.chip.text, 36, { parent: chip, x: cw / 2, y: -36, align: 'center', color: over ? th.onChip : th.onChipUnder, kind: 'label' });
  }
  return { g: g, bg: bg, av: av, pct: pct, weekly: wb, session: sb, chip: chip, w: W, h: Hh };
};
UI.DATA = {
  claude: { key: 'claude', name: 'Claude', plan: 'Max 20x', pct: 71, pace: 60, wavy: true, when: 'Wed 7:00 AM', session: { pct: 38, when: '1h 12m' }, chip: { text: 'Runs out before reset', over: true } },
  codex: { key: 'codex', name: 'ChatGPT Codex', plan: 'Pro', pct: 34, pace: 41, wavy: false, when: 'Thu 4:20 PM', session: { pct: 12, when: '3h 05m' }, chip: { text: 'On track', over: false } },
  grok: { key: 'grok', name: 'Grok', plan: 'SuperGrok', pct: 88, pace: 76, wavy: true, when: 'Mon 4:00 AM', session: null, chip: { text: 'Runs out before reset', over: true } },
  copilot: { key: 'copilot', name: 'GitHub Copilot', plan: 'Pro+', pct: 58, pace: 52, wavy: false, when: '1 Oct', session: null, chip: null, unit: 'monthly used' },
  zai: { key: 'zai', name: 'Z.AI', plan: 'GLM Coding Max', pct: 22, pace: 35, wavy: false, when: 'Sun 9:10 PM', session: null, chip: null },
  kimi: { key: 'kimi', name: 'Kimi Code', plan: 'Allegretto', pct: 47, pace: 50, wavy: false, when: 'Sat 11:00 AM', session: null, chip: null },
  opencode: { key: 'opencode', name: 'OpenCode Go', plan: 'Go', pct: 64, pace: 55, wavy: false, when: 'Fri 6:30 PM', session: null, chip: null },
  jetbrains: { key: 'jetbrains', name: 'JetBrains AI', plan: 'AI Ultimate', pct: 39, pace: 45, wavy: false, when: '1 Nov', session: null, chip: null, unit: 'monthly used' }
};
// Next-reset hero card, 1137 x 602, origin top-left.
UI.hero = function (name, th, o) {
  o = o || {};
  var g = cav.group(name, { parent: o.parent, x: o.x || 0, y: o.y || 0 });
  var bg = UI.box(name + '_bg', 0, 0, 1137, 602, 90, th.hero, { parent: g });
  var blobG = cav.group(name + '_blobg', { parent: g });
  var blob = cav.path(name + '_blob', H.cookiePts(190, 5, 0.12), { parent: blobG, closed: true, smooth: true, fill: th.blob, x: 1000, y: -190 });
  var m = UI.box(name + '_bmask', 0, 0, 1137, 602, 90, '#ffffff', { parent: g });
  cav.mask(m, blobG);
  UI.t(name + '_l', o.label || 'Next weekly reset', 44, { parent: g, x: 48, y: -78, color: th.onHero, kind: 'title' });
  var cd = UI.t(name + '_cd', o.time || '15h 28m', 156, { parent: g, x: 58, y: -210, color: th.onHero, kind: 'display' });
  UI.t(name + '_s', o.sub || 'Grok · Mon 28 Sep, 4:00 AM', 44, { parent: g, x: 48, y: -350, color: th.onHero });
  var b1 = cav.group(name + '_b1', { parent: g });
  UI.box(name + '_b1bg', 48, 423, 332, 120, 60, th.primary, { parent: b1 });
  UI.t(name + '_b1t', 'Alert on', 44, { parent: b1, x: 214, y: -483, align: 'center', color: th.onPrimary, kind: 'label' });
  var b2 = cav.group(name + '_b2', { parent: g });
  UI.box(name + '_b2bg', 405, 423, 405, 120, 60, th.secondary, { parent: b2 });
  UI.t(name + '_b2t', 'All resets', 44, { parent: b2, x: 607, y: -483, align: 'center', color: th.onSurface, kind: 'label' });
  return { g: g, bg: bg, blob: blob, cd: cd, b1: b1, b2: b2 };
};
// Top of the overview: title, subtitle, settings gear.
UI.title = function (name, th, o) {
  o = o || {};
  var g = cav.group(name, { parent: o.parent, x: o.x || 0, y: o.y || 0 });
  var t = UI.t(name + '_t', o.text || 'Headroom', 124, { parent: g, x: 50, y: -120, color: th.onSurface, kind: 'display' });
  var s = UI.t(name + '_s', o.sub || '4 accounts · synced just now', 40, { parent: g, x: 50, y: -215, color: th.onSurfaceVar });
  var gear = cav.group(name + '_gear', { parent: g, x: 1112, y: -145 });
  cav.path(name + '_gearo', H.cookiePts(30, 8, 0.16), { parent: gear, closed: true, smooth: true, fill: th.onSurface });
  cav.circle(name + '_geari', 11, { parent: gear, fill: th.surface });
  return { g: g, t: t, s: s, gear: gear };
};
// Floating toolbar + refresh button, origin top-left of the screen.
UI.toolbar = function (name, th, o) {
  o = o || {};
  var g = cav.group(name, { parent: o.parent, x: o.x || 0, y: o.y || 0 });
  UI.box(name + '_sh', 252, 2420, 545, 190, 95, '#000000', { parent: g, opacity: 8 });
  UI.box(name + '_bg', 248, 2410, 545, 190, 95, th.card, { parent: g });
  UI.box(name + '_sel', 270, 2445, 167, 120, 60, th.primary, { parent: g });
  // the app's tabs (Overview selected, Resets, Stats): Material Symbols Rounded, as in HomeTab.kt
  [[352, 2505, 'tb_dashboard'], [520, 2505, 'tb_monitoring'], [688, 2505, 'tb_piechart']].forEach(function (p, i) {
    H.logo(name + '_i' + i, p[2], 64, { parent: g, x: p[0], y: -p[1], color: i ? th.onSurface : th.onPrimary });
  });
  UI.box(name + '_fab', 820, 2422, 168, 168, 52, th.hero, { parent: g });
  H.logo(name + '_fabi', 'tb_sync', 66, { parent: g, x: 904, y: -2506, color: th.onHero });
  return { g: g };
};
// The whole overview screen at screen scale (origin = screen centre).
UI.overview = function (name, th, o) {
  o = o || {};
  var g = cav.group(name, { parent: o.parent, x: o.x || 0, y: o.y || 0, in: o.in, out: o.out });
  var bg = cav.rect(name + '_bg', UI.SW, UI.SH, { parent: g, fill: th.surface });
  var root = cav.group(name + '_root', { parent: g, x: -UI.SW / 2, y: UI.SH / 2 - (o.top || 0) });
  if (o.status) OS.status(name + '_sb', th.onSurface, { parent: cav.group(name + '_sbg', { parent: g, x: -UI.SW / 2, y: UI.SH / 2 }) });
  var parts = {};
  parts.title = UI.title(name + '_title', th, { parent: root, y: -40 });
  parts.hero = UI.hero(name + '_hero', th, { parent: root, x: 48, y: -300 });
  parts.week = UI.t(name + '_week', 'This week', 44, { parent: root, x: 60, y: -1000, color: th.onSurfaceVar, kind: 'label' });
  parts.c1 = UI.card(name + '_c1', th, UI.DATA.claude, { parent: root, x: 48, y: -1060 });
  parts.c2 = UI.card(name + '_c2', th, UI.DATA.codex, { parent: root, x: 48, y: -1570 });
  parts.c3 = UI.card(name + '_c3', th, UI.DATA.grok, { parent: root, x: 48, y: -2080 });
  if (!o.noToolbar) parts.tb = UI.toolbar(name + '_tb', th, { parent: root, y: -(o.top || 0) * 0 });
  return { g: g, bg: bg, root: root, p: parts };
};
// Device: body, screen group (masked), punch-hole camera. Screen is scaled so the rebuilt UI fits.
UI.device = function (name, h, o) {
  o = o || {};
  var g = cav.group(name, { parent: o.parent, x: o.x || 0, y: o.y || 0, in: o.in, out: o.out });
  var k = h / UI.SH, w = UI.SW * k, bez = h * 0.018, rad = h * 0.058;
  cav.rect(name + '_shadow', w + bez * 2, h + bez * 2, { parent: g, fill: '#000000', radius: rad + bez, y: -h * 0.03, opacity: o.shadow === undefined ? 22 : o.shadow });
  var sh = cav.blur(cav.find(name + '_shadow'), 40);
  cav.rect(name + '_body', w + bez * 2, h + bez * 2, { parent: g, fill: o.body || '#1c1d22', radius: rad + bez, stroke: { color: o.rim || '#5a5d68', width: 3 } });
  var screen = cav.group(name + '_screen', { parent: g, scale: k });
  var m = cav.rect(name + '_mask', w, h, { parent: g, radius: rad });
  cav.mask(m, screen);
  var cam = cav.circle(name + '_cam', 36 * k, { parent: g, fill: '#050507', y: h / 2 - 70 * k });
  return { g: g, screen: screen, k: k, w: w, h: h, cam: cam };
};
cav.hex = cav.hex || function (c) { if (typeof c === 'string') return c; function h(v) { v = Math.round(v); return (v < 16 ? '0' : '') + v.toString(16); } return '#' + h(c.r) + h(c.g) + h(c.b); };

// Compact account row card (1137 x 250): avatar, name, plan, percent, weekly bar.
UI.mini = function (name, th, d, o) {
  o = o || {};
  var g = cav.group(name, { parent: o.parent, x: o.x || 0, y: o.y || 0, in: o.in });
  var inner = cav.group(name + '_in', { parent: g, x: -1137 / 2, y: 125 });
  UI.box(name + '_bg', 0, 0, 1137, 250, 80, th.card, { parent: inner });
  var av = UI.avatar(name + '_av', d.key, 50, { parent: inner, x: 100, y: -86 });
  UI.t(name + '_name', d.name, 50, { parent: inner, x: 180, y: -62, color: th.onSurface, kind: 'title' });
  UI.t(name + '_plan', d.plan, 38, { parent: inner, x: 180, y: -112, color: th.onSurfaceVar });
  var pct = UI.t(name + '_pct', d.pct + '%', 80, { parent: inner, x: 1090, y: -86, align: 'right', color: th.onSurface, kind: 'display' });
  var b = UI.bar(name + '_wb', 60, 1060, 200, { parent: inner, color: th.primary, track: th.track, pct: d.pct, pace: d.pace, wavy: d.wavy, tickColor: th.onSurface });
  return { g: g, av: av, pct: pct, bar: b };
};
// Soft stage behind floating UI: theme surface plus slow blurred blobs.
UI.stage = function (name, th, o) {
  o = o || {};
  var g = cav.group(name, { parent: o.parent, in: o.in, out: o.out });
  cav.rect(name + '_bg', 2000, 1160, { parent: g, fill: o.bg || th.surface });
  var cols = [th.hero, th.blob, th.chip];
  for (var i = 0; i < 3; i++) {
    var b = cav.path(name + '_blob' + i, H.cookiePts(380 + i * 90, 5 + i, 0.14), { parent: g, closed: true, smooth: true, fill: cols[i], opacity: o.op || 55, x: [-700, 650, 80][i], y: [300, -280, 420][i] });
    cav.blur(b, 90);
    if (o.f0 !== undefined) { cav.tween(b, 'rotation', o.f0, o.f1, i * 30, i * 30 + (i % 2 ? -25 : 25), 'linear'); cav.tween(b, 'position.x', o.f0, o.f1, [-700, 650, 80][i], [-700, 650, 80][i] + (i % 2 ? -120 : 120), 'linear'); }
  }
  return g;
};
