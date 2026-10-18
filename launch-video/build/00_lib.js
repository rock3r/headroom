// Headroom launch video: shared library. Loaded first in every run (see build/run.sh).
var H = globalThis.H = {};
// Paths are relative to this folder (launch-video/). Cavalry scripts cannot see their own location,
// so build/run.sh writes build/.root.js, which sets LV_DIR, and runs it before this file.
if (!globalThis.LV_DIR) throw new Error('LV_DIR is not set: run the scripts through build/run.sh');
H.DIR = globalThis.LV_DIR;
H.ROOT = H.DIR + '/..';
H.SHOTS = H.ROOT + '/docs/screenshots/';
H.ASSETS = H.DIR + '/assets/';
H.T = JSON.parse(api.readFromFile(H.DIR + '/build/timing.json'));
H.C = {
  paper: '#f8fafe', ink: '#1a1c22', ink2: '#44474f', cobalt: '#305eb7', icon: '#3553b0',
  blueC: '#c9dfff', blueSoft: '#dde8ff', pink: '#fdcdef', night: '#0d0f14', onNight: '#a4c5ff',
  green: '#3ddc84', track: '#dfe3ec', white: '#ffffff', tick: '#1a1c22'
};
H.P = { // provider accent / container (OKLCH from ProviderPalette, light theme)
  claude: ['#b55422', '#f9c3ab', 'Claude'], codex: ['#008b4e', '#addfbd', 'Codex'],
  copilot: ['#885cb5', '#dbc6f7', 'Copilot'], grok: ['#007fba', '#a4d9f9', 'Grok'],
  zai: ['#008c8e', '#9ae0df', 'Z.AI'], kimi: ['#556dc6', '#bfd0ff', 'Kimi Code'],
  opencode: ['#887400', '#dbd39e', 'OpenCode Go'], jetbrains: ['#af4d80', '#f6bfd7', 'JetBrains AI']
};
H.ORDER = ['claude', 'codex', 'copilot', 'grok', 'zai', 'kimi', 'opencode', 'jetbrains'];
H.SEEDS = { coral: '#ff6f61', tangerine: '#ff8c1a', lemon: '#ffd60a', lime: '#8bd41a', lagoon: '#00bfa5', sky: '#2e9bff', grape: '#8a4dff', bubblegum: '#ff4fa3' };
H.FONT = 'Google Sans Flex';

// Display type: wide, heavy, fully rounded (the app's display styles).
H.head = function (name, str, size, o) {
  o = o || {};
  var t = cav.text(name, str, size, Object.assign({ font: H.FONT, style: 'Regular', color: H.C.ink }, o));
  cav.axes(t, { wght: o.wght || 850, wdth: o.wdth || 116, ROND: o.rond === undefined ? 100 : o.rond });
  return t;
};
// Labels: medium weight, slightly rounded.
H.label = function (name, str, size, o) {
  o = o || {};
  var t = cav.text(name, str, size, Object.assign({ font: H.FONT, style: 'Regular', color: H.C.ink2 }, o));
  cav.axes(t, { wght: o.wght || 620, wdth: o.wdth || 100, ROND: o.rond === undefined ? 50 : o.rond });
  return t;
};

// M3 Expressive "cookie": a circle with soft scallops (n lobes).
H.cookiePts = function (r, n, amp) {
  var pts = [], N = 144; n = n || 9; amp = amp === undefined ? 0.07 : amp;
  for (var i = 0; i < N; i++) { var a = i / N * Math.PI * 2; var rr = r * (1 + amp * Math.cos(n * a)); pts.push([rr * Math.cos(a), rr * Math.sin(a)]); }
  return pts;
};
H.cookie = function (name, r, o) {
  o = o || {};
  return cav.path(name, H.cookiePts(r, o.lobes, o.amp), Object.assign({ closed: true, smooth: true, fill: o.fill || H.C.blueC }, o));
};

// Provider logo from assets/logos/<key>.svg, centred, `size` px, recoloured. Returns a group.
H.logo = function (name, key, size, o) {
  o = o || {};
  api.select([]);
  var ids = api.convertSVGToLayers(H.ASSETS + 'logos/' + key + '.svg');
  api.select([]);
  // Measure the imported layers at the origin first, then centre, scale and place.
  var inner = cav.group(name + '_svg', {});
  ids.forEach(function (id) { try { api.parent(id, inner); } catch (e) {} });
  var bb = cav.bbox(inner);
  var s = size / Math.max(bb.width, bb.height);
  ids.forEach(function (id) { try { if (api.hasAttribute(id, 'material.materialColor')) cav.set(id, { fill: o.color || H.C.white }); } catch (e) {} });
  var g = cav.group(name, { parent: o.parent, x: o.x || 0, y: o.y || 0 });
  cav.set(inner, { parent: g, x: -(bb.centre.x !== undefined ? bb.centre.x : bb.centre[0]) * s, y: -(bb.centre.y !== undefined ? bb.centre.y : bb.centre[1]) * s, scale: s });
  return g;
};

// Provider avatar: cookie in the accent colour with the logo in white.
H.avatar = function (name, key, r, o) {
  o = o || {};
  var g = cav.group(name, { parent: o.parent, x: o.x || 0, y: o.y || 0, in: o.in, out: o.out });
  H.cookie(name + '_bg', r, { parent: g, fill: H.P[key][0] });
  H.logo(name + '_logo', key, r * 1.05, { parent: g, color: H.C.white });
  return g;
};

// Quota bar (QuotaBar.kt): track + filled part + even-pace tick + end dot. wavy: a wave on the fill.
H.bar = function (name, w, pct, o) {
  o = o || {};
  var th = o.thick || 22, g = cav.group(name, { parent: o.parent, x: o.x || 0, y: o.y || 0, in: o.in, out: o.out });
  var fillW = w * pct / 100, gap = th * 0.9;
  var track = cav.rect(name + '_track', Math.max(1, w - fillW - gap), th * 0.45, { parent: g, fill: o.trackColor || H.C.track, radius: th });
  cav.set(track, { x: -w / 2 + fillW + gap + (w - fillW - gap) / 2 });
  var fill;
  if (o.wavy) {
    var pts = [], seg = 48, amp = th * 0.55, len = th * 3.2;
    for (var i = 0; i <= seg * 4; i++) { var x = -w / 2 + fillW * i / (seg * 4); pts.push([x, amp * Math.sin((x + w / 2) / len * Math.PI * 2)]); }
    fill = cav.path(name + '_fill', pts, { parent: g, noFill: true, smooth: true, stroke: { color: o.color || H.C.cobalt, width: th, cap: 'round', trim: true } });
  } else {
    fill = cav.line(name + '_fill', [-w / 2 + th / 2, 0], [-w / 2 + Math.max(th / 2 + 1, fillW - th / 2), 0], { parent: g, stroke: { color: o.color || H.C.cobalt, width: th, cap: 'round', trim: true } });
  }
  var dot = cav.circle(name + '_dot', th * 0.32, { parent: g, fill: o.color || H.C.cobalt, x: w / 2 });
  var tick = null;
  if (o.pace !== undefined) tick = cav.rect(name + '_tick', th * 0.38, th * 2.6, { parent: g, fill: H.C.tick, radius: th * 0.2, x: -w / 2 + w * o.pace / 100 });
  return { group: g, fill: fill, track: track, dot: dot, tick: tick };
};

// Quota ring (QuotaRing.kt): faint track + arc, starting at 12 o'clock, clockwise.
H.ring = function (name, r, pct, o) {
  o = o || {};
  var g = cav.group(name, { parent: o.parent, x: o.x || 0, y: o.y || 0, in: o.in, out: o.out });
  var w = o.width || r * 0.16;
  var circ = function (nm, col, trim) {
    var pts = [], N = 120;
    for (var i = 0; i <= N; i++) { var a = Math.PI / 2 - (i / N) * Math.PI * 2; pts.push([r * Math.cos(a), r * Math.sin(a)]); }
    return cav.path(nm, pts, { parent: g, noFill: true, smooth: true, stroke: { color: col, width: w, cap: 'round', trim: trim } });
  };
  var tr, arc;
  if (o.gap) {
    // M3 progress ring: the track starts after the arc, with a gap at both ends.
    var gp = (w + o.gap) / (2 * Math.PI * r) * 100;
    tr = circ(name + '_track', o.trackColor || H.C.track, true);
    api.set(tr, { 'stroke.trimStart': Math.min(100, pct + gp), 'stroke.trimEnd': Math.max(0, 100 - gp) });
    arc = circ(name + '_arc', o.color || H.C.cobalt, true);
    api.set(arc, { 'stroke.trimEnd': pct });
    return { group: g, track: tr, arc: arc, gapPct: gp };
  }
  tr = cav.circle(name + '_track', r, { parent: g, noFill: true, stroke: { color: o.trackColor || H.C.track, width: w } });
  var arcPts = [], N2 = 120;
  for (var j = 0; j <= N2; j++) { var b = Math.PI / 2 - (j / N2) * Math.PI * 2 * pct / 100; arcPts.push([r * Math.cos(b), r * Math.sin(b)]); }
  arc = cav.path(name + '_arc', arcPts, { parent: g, noFill: true, smooth: true, stroke: { color: o.color || H.C.cobalt, width: w, cap: 'round', trim: true } });
  return { group: g, track: tr, arc: arc };
};
// Animate a gapped ring from 0 to pct (arc and track move together).
H.ringGrow = function (rg, f0, f1, pct, ease) {
  cav.tween(rg.arc, 'stroke.trimEnd', f0, f1, 0, pct, ease || 'outCubic');
  if (rg.gapPct !== undefined) cav.tween(rg.track, 'stroke.trimStart', f0, f1, rg.gapPct, Math.min(100, pct + rg.gapPct), ease || 'outCubic');
};

// Phone: bezel, rounded screen with a screenshot (1233 x 2673), punch-hole camera.
H.SCREEN_RATIO = 1233 / 2673;
H.phone = function (name, shot, h, o) {
  o = o || {};
  var g = cav.group(name, { parent: o.parent, x: o.x || 0, y: o.y || 0, in: o.in, out: o.out });
  var sw = h * 0.96 * H.SCREEN_RATIO, bez = h * 0.022, rad = h * 0.06;
  cav.rect(name + '_shadow', sw + bez * 2, h + bez * 2, { parent: g, fill: '#000000', radius: rad + bez, y: -h * 0.02, opacity: 12 });
  cav.rect(name + '_body', sw + bez * 2, h + bez * 2, { parent: g, fill: o.bodyColor || '#16181d', radius: rad + bez });
  var scr = cav.group(name + '_screen', { parent: g });
  var imgs = {}, sb = h * 0.04, ih = h - sb; // status bar strip above the screenshot
  cav.rect(name + '_sbar', sw, h, { parent: scr, fill: o.statusColor || H.C.paper });
  H.label(name + '_time', '9:41', Math.max(10, h * 0.017), { parent: scr, x: -sw / 2 + h * 0.03, y: h / 2 - sb / 2, align: 'left', color: H.C.ink, wght: 600 });
  (Array.isArray(shot) ? shot : [shot]).forEach(function (s, i) {
    var im = cav.image(H.SHOTS + s + '.png', name + '_' + s.replace(/[^a-z0-9]/gi, '_'), { parent: scr, scale: ih / 2673, y: -sb / 2 });
    if (i > 0) cav.set(im, { opacity: 0 });
    imgs[s] = im;
  });
  var m = cav.rect(name + '_mask', sw, h, { parent: g, radius: rad });
  cav.mask(m, scr);
  cav.circle(name + '_cam', h * 0.01, { parent: g, fill: '#050507', y: h / 2 - h * 0.02 });
  return { group: g, screen: scr, imgs: imgs, w: sw + bez * 2, h: h + bez * 2 };
};


// ---- previz helpers ----
H.bf = function (i) { var b = H.T.beatFrames; return i < b.length ? b[i] : Math.round((0.128 + i * 60 / 128) * 60); };
H.shot = function (id, bg, o) {
  o = o || {};
  var sh = H.T.shots[id], c = cav.comp();
  var g = cav.group(id, { in: sh[0], out: sh[1] - 1 });
  var bgId = cav.rect(id + '_bg', c.width + 40, c.height + 40, { parent: g, fill: bg || H.C.paper });
  var cam = cav.group(id + '_cam', { parent: g });
  return { g: g, cam: cam, bg: bgId, f0: sh[0], f1: sh[1], d: sh[1] - sh[0],
    beat: function (n) { return sh[0] + Math.round(n * (H.T.bpm ? 60 * 60 / H.T.bpm : 28.125)); } };
};
H.trim = function (l, f0, f1, a, b, ease) { cav.tween(l, 'stroke.trimEnd', f0, f1, a, b, ease || 'outCubic'); return l; };
// Centre a group horizontally/vertically on (x,y) using its measured bbox.
H.centre = function (g, x, y) {
  var bb = cav.bbox(g), p = api.get(g, 'position');
  cav.set(g, { x: p.x + (x || 0) - (bb.centre.x !== undefined ? bb.centre.x : bb.centre[0]), y: p.y + (y || 0) - (bb.centre.y !== undefined ? bb.centre.y : bb.centre[1]) });
  return g;
};
