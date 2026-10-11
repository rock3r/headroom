// Launcher, lock screen, widgets and the reset island, rebuilt. Units: device-screen px (1233 x 2673).
var OS = globalThis.OS = {};
OS.W = { card: '#eff0f6', ring: '#455d9a', track: '#dfe1ea', ink: '#1b1c20', ink2: '#45464f', shape: '#d8e2ff', shapeInk: '#2c4678' };
OS.L = { card: '#1e1f24', ring: '#aac7ff', track: '#34363c', ink: '#f1f0f7', ink2: '#c6c6cf' };
function txt(name, s, size, o) { return UI.t(name, s, size, o); }
// Rings widget (2 x 2 accounts), local origin top-left, size 540.
OS.rings = function (name, o) {
  o = o || {};
  var g = cav.group(name, { parent: o.parent, x: o.x || 0, y: o.y || 0 });
  UI.box(name + '_bg', 0, 0, 540, 540, 84, o.card || OS.W.card, { parent: g });
  var acc = [['claude', 71, 'Claude'], ['codex', 34, 'Codex'], ['grok', 88, 'Grok'], ['copilot', 58, 'Copilot']], rings = [];
  acc.forEach(function (a, i) {
    var cx = 145 + (i % 2) * 250, cy = 150 + Math.floor(i / 2) * 250;
    var r = H.ring(name + '_r' + i, 78, a[1], { parent: g, x: cx, y: -cy + 12, width: 18, color: OS.W.ring, trackColor: OS.W.track, gap: 12 });
    var n = txt(name + '_n' + i, String(a[1]), 52, { parent: g, x: cx, y: -cy + 12, align: 'center', color: OS.W.ink, kind: 'display' });
    txt(name + '_l' + i, a[2], 34, { parent: g, x: cx, y: -cy - 92, align: 'center', color: OS.W.ink2, kind: 'label' });
    rings.push({ ring: r, num: n });
  });
  return { g: g, rings: rings };
};
// Shape widget (cookies with numbers and logos), size 540.
OS.shape = function (name, o) {
  o = o || {};
  var g = cav.group(name, { parent: o.parent, x: o.x || 0, y: o.y || 0 });
  UI.box(name + '_bg', 0, 0, 540, 540, 84, OS.W.card, { parent: g });
  var acc = [['claude', 71], ['codex', 34], ['grok', 88], ['copilot', 58]], items = [];
  acc.forEach(function (a, i) {
    var cx = 145 + (i % 2) * 250, cy = 150 + Math.floor(i / 2) * 250;
    var it = cav.group(name + '_i' + i, { parent: g, x: cx, y: -cy });
    cav.path(name + '_c' + i, H.cookiePts(112, 8, 0.1), { parent: it, closed: true, smooth: true, fill: OS.W.shape });
    txt(name + '_n' + i, String(a[1]), 60, { parent: it, x: 0, y: 24, align: 'center', color: OS.W.shapeInk, kind: 'display' });
    H.logo(name + '_lg' + i, a[0], 38, { parent: it, y: -38, color: OS.W.shapeInk });
    items.push(it);
  });
  return { g: g, items: items };
};
// Bars widget (4 rows), size 1113 x 520.
OS.bars = function (name, o) {
  o = o || {};
  var g = cav.group(name, { parent: o.parent, x: o.x || 0, y: o.y || 0 });
  UI.box(name + '_bg', 0, 0, 1113, 520, 84, OS.W.card, { parent: g });
  var acc = [['claude', 71, 'Claude', 60, true], ['codex', 34, 'Codex', 41, false], ['grok', 88, 'Grok', 76, true], ['copilot', 58, 'Copilot', 52, true]], bars = [];
  acc.forEach(function (a, i) {
    var y = 92 + i * 112;
    UI.avatar(name + '_a' + i, a[0], 36, { parent: g, x: 90, y: -y });
    txt(name + '_l' + i, a[2], 42, { parent: g, x: 150, y: -y, color: OS.W.ink });
    var b = UI.bar(name + '_b' + i, 400, 860, y, { parent: g, th: 14, color: OS.W.ring, track: OS.W.track, pct: a[1], pace: a[3], wavy: a[4], tickColor: OS.W.ink });
    txt(name + '_p' + i, a[1] + '%', 44, { parent: g, x: 1060, y: -y, align: 'right', color: OS.W.ink, kind: 'title' });
    bars.push(b);
  });
  return { g: g, bars: bars };
};
// Lock-screen widget (dark), size 1000 x 420, as in 1.2: numbers inside the rings, names under them.
OS.lockW = function (name, o) {
  o = o || {};
  var g = cav.group(name, { parent: o.parent, x: o.x || 0, y: o.y || 0 });
  UI.box(name + '_bg', 0, 0, 1000, 420, 64, '#1f2024', { parent: g });
  var acc = [['claude', 71, 'Claude'], ['codex', 34, 'Codex'], ['grok', 88, 'Grok']], rings = [], nums = [];
  acc.forEach(function (a, i) {
    var cx = 187 + i * 313, cy = 168;
    var r = H.ring(name + '_r' + i, 66, a[1], { parent: g, x: cx, y: -cy, width: 14, color: '#b2c5ff', trackColor: '#34353a', gap: 12 });
    nums.push(txt(name + '_n' + i, String(a[1]), 48, { parent: g, x: cx, y: -cy, align: 'center', color: '#ececf2', kind: 'title', wght: 800 }));
    txt(name + '_l' + i, a[2], 32, { parent: g, x: cx, y: -cy - 104, align: 'center', color: '#d8d8e0' });
    rings.push(r);
  });
  txt(name + '_f', 'Next weekly reset: Grok, Mon 04:00', 34, { parent: g, x: 500, y: -372, align: 'center', color: '#d0d0d8' });
  return { g: g, rings: rings, nums: nums };
};
// Status bar.
OS.status = function (name, col, o) {
  var g = cav.group(name, { parent: o.parent });
  txt(name + '_t', '9:41', 40, { parent: g, x: 76, y: -70, color: col, kind: 'label' });
  [0, 1, 2].forEach(function (i) { cav.rect(name + '_i' + i, i === 2 ? 52 : 30, 26, { parent: g, x: 1040 + i * 50, y: -70, radius: 6, fill: col, opacity: i === 2 ? 100 : 80 }); });
  return g;
};
// Home screen, origin = screen centre (place inside UI.device(...).screen).
OS.home = function (name, o) {
  o = o || {};
  var g = cav.group(name, { parent: o.parent });
  var wp = cav.rect(name + '_wp', UI.SW, UI.SH, { parent: g });
  cav.gradient(wp, ['#cdd6f6', '#8fa2e4', '#5d73c9'], { rotation: -75 });
  var root = cav.group(name + '_root', { parent: g, x: -UI.SW / 2, y: UI.SH / 2 });
  OS.status(name + '_sb', '#ffffff', { parent: root });
  txt(name + '_glance', 'Fri, Oct 10', 66, { parent: root, x: 72, y: -290, color: '#ffffff', kind: 'title' });
  txt(name + '_wx', '18°  ·  Light rain at 4 PM', 36, { parent: root, x: 74, y: -350, color: '#f2f4ff' });
  var parts = {};
  parts.rings = OS.rings(name + '_wr', { parent: root, x: 60, y: -460 });
  parts.shape = OS.shape(name + '_ws', { parent: root, x: 633, y: -460 });
  parts.bars = OS.bars(name + '_wb', { parent: root, x: 60, y: -1040 });
  var appCols = ['#f2b8a8', '#a8d5ba', '#f6d78b', '#b9c3f2', '#e7b3d9', '#9fd8e6', '#f7c59f', '#c7e39b'];
  for (var i = 0; i < 8; i++) {
    var ax = 200 + (i % 4) * 278, ay = 1770 + Math.floor(i / 4) * 300;
    H.cookie(name + '_app' + i, 82, { parent: root, x: ax, y: -ay, fill: appCols[i], lobes: i % 2 ? 4 : 12, amp: i % 2 ? 0.02 : 0.04 });
    txt(name + '_appl' + i, ['Mail', 'Maps', 'Photos', 'Chat', 'Music', 'Files', 'Notes', 'Fit'][i], 30, { parent: root, x: ax, y: -ay - 130, align: 'center', color: '#ffffff' });
  }
  UI.box(name + '_search', 70, 2440, 1093, 150, 75, '#eef1fb', { parent: root, opacity: 92 });
  txt(name + '_searcht', 'Search', 42, { parent: root, x: 150, y: -2530, color: '#5b5f6e' });
  parts.root = root;
  return { g: g, p: parts };
};
// Lock screen.
OS.lock = function (name, o) {
  o = o || {};
  var g = cav.group(name, { parent: o.parent });
  var wp = cav.rect(name + '_wp', UI.SW, UI.SH, { parent: g });
  cav.gradient(wp, ['#3b4a8c', '#1b2246', '#0a0d1c'], { rotation: -80 });
  var root = cav.group(name + '_root', { parent: g, x: -UI.SW / 2, y: UI.SH / 2 });
  OS.status(name + '_sb', '#e8ebff', { parent: root });
  txt(name + '_date', 'Fri, Oct 10  ·  18°', 44, { parent: root, x: 100, y: -330, color: '#dfe5ff', kind: 'label' });
  var c1 = txt(name + '_h', '09', 430, { parent: root, x: 80, y: -760, color: '#d9e2ff', kind: 'display', wght: 520 });
  var c2 = txt(name + '_m', '41', 430, { parent: root, x: 80, y: -1150, color: '#aab9f2', kind: 'display', wght: 520 });
  var w = OS.lockW(name + '_lw', { parent: root, x: 116, y: -1640 });
  OS.fingerprint(name + '_fp', { parent: root, x: 616, y: -2400, size: 110, color: '#dfe5ff' });
  return { g: g, widget: w, clock: [c1, c2] };
};
// Material "fingerprint" icon (the real glyph, from assets/logos/fingerprint.svg).
OS.fingerprint = function (name, o) {
  return H.logo(name, 'fingerprint', o.size || 120, { parent: o.parent, x: o.x || 0, y: o.y || 0, color: o.color });
};
// Reset island: grows out of the camera cutout. Origin = cutout centre. The pill is a rectangle whose size
// animates (generator.dimensions), so it starts as a circle the size of the cutout.
OS.island = function (name, key, o) {
  // App geometry (IslandGeometry/IslandSurface) at 3 px per dp: hole 72, pad 18 -> 108 tall,
  // 70 dp each side -> 492 wide. Logo 22 dp, ring 18 dp / 2.5 dp stroke, label 12 dp.
  o = o || {};
  var g = cav.group(name, { parent: o.parent, x: o.x || 0, y: o.y || 0 });
  var pill = cav.rect(name + '_pill', 72, 72, { parent: g, radius: 54, fill: '#000000' });
  var content = cav.group(name + '_content', { parent: g });
  var av = cav.group(name + '_av', { parent: content, x: -183 });
  cav.path(name + '_avc', H.cookiePts(33, 10, 0.08), { parent: av, closed: true, smooth: true, fill: H.P[key][1] });
  H.logo(name + '_avl', key, 30, { parent: av, color: '#1a1a1a' });
  var ring = H.ring(name + '_ring', 23, 100, { parent: content, x: 79, width: 7.5, color: H.C.green, trackColor: '#3a3a3a' });
  var word = UI.t(name + '_w', 'reset', 36, { parent: content, x: 216, y: 0, align: 'right', color: '#ffffff', kind: 'label' });
  return { g: g, pill: pill, content: content, av: av, ring: ring, word: word };
};
