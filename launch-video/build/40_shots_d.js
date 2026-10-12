// Widgets on the home and lock screens, palettes, outro.
var T = H.T.shots, TH = UI.TH.cobalt;
// ================= s7a: home screen =================
var S = H.shot('s7a', '#e3e7f2');
var DX = 420, k = 980 / UI.SH;
var dv = UI.device('s7a_dev', 980, { parent: S.cam, x: DX });
var hm = OS.home('s7a_home', { parent: dv.screen });
var P = hm.p;
P.bars.bars.forEach(function (b, i) { var pct = [71, 34, 88, 58][i]; UI.barSet(b, S.f0 + 2 + i * 5, 0, undefined, 'outCubic'); UI.barSet(b, S.f0 + 34 + i * 5, pct, undefined, 'linear'); });
P.rings.rings.forEach(function (r, i) { H.ringGrow(r.ring, S.beat(3) + i * 4, S.beat(3) + 34 + i * 4, [71, 34, 88, 58][i], 'outCubic'); cav.counter(r.num, S.beat(3) + i * 4, S.beat(3) + 34 + i * 4, 0, [71, 34, 88, 58][i], {}); });
P.shape.items.forEach(function (it, i) { cav.key(it, 'scale', [[S.beat(4) + i * 3, 0.4, 'spring'], [S.beat(4) + 24 + i * 3, 1]]); cav.key(it, 'rotation', [[S.beat(4) + i * 3, -60, 'outBack'], [S.beat(4) + 26 + i * 3, 0]]); });
// camera: tight on the bars widget, slide to the rings, pull back
var bx = DX, by = 36 * k, rx = DX - 286 * k, ry = 606 * k;
cav.key(S.cam, 'scale', [[S.f0, 2.7, 'inOutCubic'], [S.beat(2), 2.7, 'inOutCubic'], [S.beat(3), 2.5, 'inOutCubic'], [S.beat(4), 2.5, 'inOutCubic'], [S.beat(6), 1]]);
cav.key(S.cam, 'position', [[S.f0, [-bx * 2.7, -by * 2.7], 'inOutCubic'], [S.beat(2), [-bx * 2.7, -by * 2.7], 'inOutCubic'], [S.beat(3), [-rx * 2.5, -ry * 2.5], 'inOutCubic'], [S.beat(4), [-rx * 2.5, -ry * 2.5], 'inOutCubic'], [S.beat(6), [0, 0]]]);
var a1 = H.head('s7a_t1', 'Your limits,', 80, { parent: S.g, x: -900, y: 60, align: 'left', in: S.beat(5) });
var a2 = H.head('s7a_t2', 'on your home screen.', 80, { parent: S.g, x: -900, y: -40, align: 'left', color: H.C.cobalt, in: S.beat(5) + 4 });
[a1, a2].forEach(function (w, i) { cav.slideIn(w, S.beat(5) + i * 4, { dx: -60, dy: 0, dur: 16 }); });
// ================= s7b: lock screen =================
S = H.shot('s7b', '#e3e7f2');
var LX = -500;
var dv2 = UI.device('s7b_dev', 980, { parent: S.cam, x: LX });
var lk = OS.lock('s7b_lock', { parent: dv2.screen });
lk.widget.rings.forEach(function (r, i) { H.ringGrow(r, S.f0 + 6 + i * 5, S.f0 + 40 + i * 5, [71, 34, 88][i], 'outCubic'); });
lk.widget.nums.forEach(function (n, i) { cav.counter(n, S.f0 + 6 + i * 5, S.f0 + 40 + i * 5, 0, [71, 34, 88][i], { ease: 'outCubic' }); });
cav.key(lk.clock[0], 'position.y', [[S.f0, -660, 'outExpo'], [S.f0 + 24, -760]]);
cav.key(lk.clock[1], 'position.y', [[S.f0 + 3, -1250, 'outExpo'], [S.f0 + 27, -1150], [S.beat(6), -1150, 'inOutCubic'], [S.beat(6) + 16, -1000]]);
cav.key(lk.clock[1], 'opacity', [[S.beat(6), 100, 'inCubic'], [S.beat(6) + 10, 0]]);
// the minute ticks over, so the screen is alive while the copy reads
var m42 = UI.t('s7b_lock_m2', '42', 430, { parent: cav.find('s7b_lock_root'), x: 80, y: -1300, color: '#aab9f2', kind: 'display', wght: 520, in: S.beat(6) });
cav.key(m42, 'position.y', [[S.beat(6), -1300, 'outCubic'], [S.beat(6) + 18, -1150]]);
cav.key(m42, 'opacity', [[S.beat(6), 0, 'outCubic'], [S.beat(6) + 12, 100]]);
var wy = -514 * k;
// camera never parks: a slow drift on the widget, the pull back, then a slow push to the end
cav.key(S.cam, 'scale', [[S.f0, 2.5, 'linear'], [S.beat(3), 2.3, 'inOutCubic'], [S.beat(5), 1, 'linear'], [S.f1, 1.08]]);
cav.key(S.cam, 'position', [[S.f0, [-LX * 2.5, -wy * 2.5 - 30], 'linear'], [S.beat(3), [-LX * 2.3, -wy * 2.3], 'inOutCubic'], [S.beat(5), [0, 0], 'linear'], [S.f1, [60, 10]]]);
cav.tween(dv2.g, 'rotation', S.f0, S.f1, -1.5, 1.5, 'inOutQuad');
var b1 = H.head('s7b_t1', 'And one glance', 72, { parent: S.g, x: 880, y: 56, align: 'right', in: S.beat(4) });
var b2 = H.head('s7b_t2', 'from your lock screen.', 72, { parent: S.g, x: 880, y: -40, align: 'right', color: H.C.cobalt, in: S.beat(4) + 4 });
[b1, b2].forEach(function (w, i) { cav.slideIn(w, S.beat(4) + i * 4, { dx: 60, dy: 0, dur: 16 }); });
// ================= s8: palettes ripple through the app, no phone =================
// The app's own components float over the section, built once per palette. Each palette is a layer
// revealed by a ripple from the tapped swatch; every component pops as the ripple reaches it.
S = H.shot('s8', UI.TH.cobalt.surface);
// three palettes, then the theme switch: dark is a mode of the palette, not a palette of its own
UI.TH.lagoonDark = { surface: '#0e1513', onSurface: '#dde4e1', onSurfaceVar: '#bec9c5', hero: '#00513f', onHero: '#9ff2dc', blob: '#006b56', primary: '#5ddbbf', onPrimary: '#00382e', secondary: '#1f3a35', track: '#23332f', card: '#161d1b', chip: '#5a3a5f', onChip: '#fcd7f5', chipUnder: '#23332f', onChipUnder: '#bfeee3' };
var PAL = ['cobalt', 'bubblegum', 'tangerine', 'lagoon', 'lagoonDark'];
var SWN = ['cobalt', 'bubblegum', 'tangerine', 'lagoon'];
var CHG = [S.f0, S.beat(3), S.beat(6), S.beat(9), S.beat(12)];
var SWC = { cobalt: '#305eb7', lagoon: UI.TH.seeds.lagoon, bubblegum: UI.TH.seeds.bubblegum, tangerine: UI.TH.seeds.tangerine };
var PKX = -650, PKY = -150, SC = 0.52, RIP = 30, RMAX = 2600, TGY = PKY - 150;
var swX = function (j) { return PKX - 144 + j * 96; };
// component slots: [key, centre x, centre y, width, height] in frame units
var CA = 5, CB = 626, OY = -40;
var SLOTS = [['hero', CA, OY + 296 - 157], ['c1', CA, OY + 296 - 313 - 30 - 124], ['c2', CB, OY + 310 - 124], ['c3', CB, OY + 310 - 249 - 30 - 104], ['tb', CB + 32, OY + 310 - 249 - 30 - 208 - 30 - 52], ['pick', PKX, PKY], ['tg', PKX, TGY], ['head', -900, 120]];
var layers = [];
PAL.forEach(function (pn, i) {
  var th = UI.TH[pn], L = cav.group('s8_L' + i, { parent: S.cam, in: CHG[i] });
  cav.rect('s8_L' + i + '_bg', 2200, 1300, { parent: L, fill: th.surface });
  var glow = cav.circle('s8_L' + i + '_glow', 520, { parent: L, x: 620, y: 360, fill: th.hero, opacity: pn === 'lagoonDark' ? 55 : 80 });
  cav.blur(glow, 160);
  var w = {};
  SLOTS.forEach(function (sl) { w[sl[0]] = cav.group('s8_L' + i + '_' + sl[0], { parent: L, x: sl[1], y: sl[2] }); });
  var inner = function (k, W, Hh) { return cav.group('s8_L' + i + '_' + k + 'i', { parent: w[k], scale: SC, x: -W * SC / 2, y: Hh * SC / 2 }); };
  var parts = {};
  parts.hero = UI.hero('s8_L' + i + '_h', th, { parent: inner('hero', 1137, 602), time: '15h 28m', sub: 'Grok · Mon, 4:00 AM' });
  parts.c1 = UI.card('s8_L' + i + '_c1', th, UI.DATA.claude, { parent: inner('c1', 1137, 478) });
  parts.c2 = UI.card('s8_L' + i + '_c2', th, UI.DATA.codex, { parent: inner('c2', 1137, 478) });
  parts.c3 = UI.card('s8_L' + i + '_c3', th, UI.DATA.grok, { parent: inner('c3', 1137, 400) });
  UI.toolbar('s8_L' + i + '_tb', th, { parent: cav.group('s8_L' + i + '_tbi', { parent: w.tb, scale: SC, x: -618 * SC, y: 2510 * SC }) });
  // colour picker: a pill of swatches, the current palette ringed
  cav.rect('s8_L' + i + '_pkbg', 430, 150, { parent: w.pick, radius: 75, fill: th.card });
  SWN.forEach(function (sn, j) {
    var c = cav.circle('s8_L' + i + '_sw' + j, 34, { parent: w.pick, x: swX(j) - PKX, fill: SWC[sn] });
    if (i === 0) cav.pop(c, S.f0 + 10 + j * 3, { dur: 14 });
  });
  var ring = cav.circle('s8_L' + i + '_sel', 44, { parent: w.pick, x: swX(Math.min(i, 3)) - PKX, noFill: true, stroke: { color: th.onSurface, width: 5 } });
  if (i > 0 && i < 4) cav.pop(ring, CHG[i], { dur: 14, from: 1.5 });
  // theme switch: Light | Dark segmented button
  var dark = pn === 'lagoonDark';
  cav.rect('s8_L' + i + '_tgbg', 430, 96, { parent: w.tg, radius: 48, fill: th.surface, stroke: { color: th.onSurfaceVar, width: 2 } });
  var seg = cav.rect('s8_L' + i + '_tgsel', 215, 96, { parent: w.tg, radius: 48, fill: th.secondary, x: dark ? 107 : -107 });
  if (dark) cav.pop(seg, CHG[i], { dur: 14, from: 0.6 });
  UI.t('s8_L' + i + '_tgl', 'Light', 36, { parent: w.tg, x: -107, y: 0, align: 'center', color: th.onSurface, kind: 'label' });
  UI.t('s8_L' + i + '_tgd', 'Dark', 36, { parent: w.tg, x: 107, y: 0, align: 'center', color: th.onSurface, kind: 'label' });
  var h1 = H.head('s8_L' + i + '_t1', 'Make it', 96, { parent: w.head, x: 0, y: 70, align: 'left', color: th.onSurface });
  var h2 = H.head('s8_L' + i + '_t2', 'yours.', 96, { parent: w.head, x: 0, y: -40, align: 'left', color: th.primary });
  if (i === 0) { cav.slideIn(h1, S.f0, { dx: -60, dy: 0, dur: 16 }); cav.slideIn(h2, S.f0 + 4, { dx: -60, dy: 0, dur: 16 }); }
  layers.push({ g: L, w: w, p: parts });
  if (i === 0) {
    // entry: every component pops in, bars fill
    ['hero', 'c1', 'c2', 'c3', 'tb', 'pick', 'tg'].forEach(function (k, n) { cav.pop(w[k], S.f0 + 2 + n * 4, { dur: 16 }); });
    [[parts.c1.weekly, 71], [parts.c2.weekly, 34], [parts.c3.weekly, 88], [parts.c1.session, 38], [parts.c2.session, 12]].forEach(function (b, n) {
      UI.barSet(b[0], S.f0 + 8 + n * 3, 0, undefined, 'outCubic'); UI.barSet(b[0], S.f0 + 36 + n * 3, b[1], undefined, 'linear');
    });
    return;
  }
  // ripple from the tapped swatch; components punch as it reaches them
  var fr = CHG[i], ox = i < 4 ? swX(i) : PKX + 107, oy = i < 4 ? PKY : TGY;
  var m = cav.circle('s8_rip' + i, 10, { parent: S.cam, x: ox, y: oy, in: fr });
  cav.key(m, 'generator.radius', [[fr, [10, 10], 'outQuad'], [fr + RIP, [RMAX, RMAX]]]);
  cav.mask(m, L);
  ['hero', 'c1', 'c2', 'c3', 'tb', 'pick', 'tg'].forEach(function (k) {
    var sl = SLOTS.filter(function (q) { return q[0] === k; })[0];
    var d = Math.sqrt(Math.pow(sl[1] - ox, 2) + Math.pow(sl[2] - oy, 2)) / RMAX;
    var at = fr + Math.round(RIP * (1 - Math.sqrt(Math.max(0, 1 - d))));
    cav.punch(w[k], at, 0.06, 14);
  });
  TAPF(S.cam, ox, oy, fr - 3, '#000000');
});
cav.key(S.cam, 'scale', [[S.f0, 1, 'linear'], [S.f1, 1.04]]);
// ================= s9: outro =================
S = H.shot('s9', H.C.icon);
// no hard cut: the dark palette stays under a cobalt circle that opens from the centre
cav.set(cav.find('s8'), { out: S.f0 + 26 });
cav.set(S.bg, { opacity: 0 });
var wipe = cav.circle('s9_wipe', 10, { parent: S.g, fill: H.C.icon });
cav.key(wipe, 'generator.radius', [[S.f0, [10, 10], 'inOutCubic'], [S.f0 + 24, [1300, 1300]]]);
cav.order(wipe, S.cam);
for (var rr = 0; rr < 3; rr++) {
  var bg = H.ring('s9_bg' + rr, 430 + rr * 270, 75, { parent: S.g, color: '#4a66c2', trackColor: '#3d5bb9', width: 22 });
  cav.tween(bg.group, 'rotation', S.f0, S.f1, rr * 40, rr * 40 + (rr % 2 ? -50 : 50), 'linear');
  cav.set(bg.group, { opacity: 0 }); cav.key(bg.group, 'opacity', [[S.f0 + 14 + rr * 4, 0, 'outCubic'], [S.f0 + 30 + rr * 4, 100]]);
  cav.key(bg.group, 'scale', [[S.f0 + 14 + rr * 4, 0.6, 'outExpo'], [S.f0 + 44 + rr * 4, 1]]);
  cav.order(bg.group, S.cam);
}
var lock = cav.group('s9_lock', { parent: S.cam, y: 170, in: S.f0 + 12 });
var r9 = H.ring('s9_ring', 86, 75, { parent: lock, color: H.C.white, trackColor: '#7b8fd0', width: 34 });
H.trim(r9.arc, S.f0 + 14, S.f0 + 42, 0, 100, 'outCubic');
cav.circle('s9_dot', 26, { parent: lock, fill: H.C.white });
var wmg = cav.group('s9_wmg', { parent: lock });
var wm = H.head('s9_t', 'Headroom', 160, { parent: wmg, color: H.C.white, x: 150, y: 0, align: 'left' });
cav.slideIn(wmg, S.f0 + 18, { dx: -50, dy: 0, dur: 18 });
var tag = H.head('s9_tag', 'Know your headroom.', 72, { parent: S.cam, color: '#dde3ff', y: -10, in: S.beat(2) });
cav.slideIn(tag, S.beat(2), { dy: -30, dur: 16 });
var row = cav.group('s9_cta', { parent: S.cam, y: -150, in: S.beat(4) });
cav.rect('s9_pill_bg', 760, 88, { parent: row, radius: 44, fill: H.C.white });
H.label('s9_url', 'github.com/rock3r/headroom', 42, { parent: row, color: H.C.icon, wght: 720 });
cav.pop(row, S.beat(4), { dur: 14 });
var a1o = H.label('s9_a', 'Get it on Google Play or Obtainium', 42, { parent: S.cam, color: '#ffffff', y: -255, in: S.beat(5), wght: 680 });
cav.slideIn(a1o, S.beat(5), { dy: -20, dur: 14 });
var a2o = H.label('s9_a2', 'Free and open source · Android 16+', 34, { parent: S.cam, color: '#dde3ff', y: -320, in: S.beat(5) + 6 });
cav.fadeIn(a2o, S.beat(5) + 6, 10);
var lg = H.label('s9_legal', 'Not affiliated with any AI provider. Logos are trademarks of their owners.', 28, { parent: S.cam, color: '#aab6e6', y: -470, in: S.beat(6) });
cav.fadeIn(lg, S.beat(6), 12);
var STOP = 4283; // the groove stops: the lockup lands
cav.punch(lock, STOP, 0.06, 16);
cav.ring(STOP, 0, 170, { r1: 1100, dur: 46, width: 8, color: '#ffffff' });
cav.tween(S.cam, 'scale', S.f0, S.f1, 1.05, 1, 'outCubic');
return 'D ok';
