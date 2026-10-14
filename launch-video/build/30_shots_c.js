// Locked out, use a reset, refill, or wait for the island.
var T = H.T.shots, TH = UI.TH.cobalt;
var rnd = (function () { var s = 7; return function () { s = (s * 16807) % 2147483647; return (s - 1) / 2147483646; }; })();
// ================= s6a: locked out until Monday (night) =================
var S = H.shot('s6a', H.C.night);
// starfield drifting
var sf = cav.group('s6a_stars', { parent: S.g });
var STARS = [];
for (var i = 0; i < 70; i++) {
  var sr = 1.5 + rnd() * 3.5, so = 25 + rnd() * 60, sx = -980 + rnd() * 1960, sy = -560 + rnd() * 1120;
  var st = cav.circle('s6a_st' + i, sr, { parent: sf, fill: '#ffffff', opacity: so, x: sx, y: sy });
  STARS.push({ l: st, r: sr, o: so, x: sx, y: sy });
}
cav.tween(sf, 'position.x', S.f0, S.f1, 0, -120, 'linear');
cav.order(sf, S.cam);
// giant draining ring with the Grok logo
var big = H.ring('s6a_ring', 380, 100, { parent: S.cam, x: 420, y: -20, width: 34, color: '#a4c5ff', trackColor: '#1d2433' });
// the ring draws itself in, then drains
cav.key(big.arc, 'stroke.trimEnd', [[S.f0, 0, 'outCubic'], [S.f0 + 26, 100, 'inOutQuad'], [S.f1, 62]]);
cav.key(big.track, 'opacity', [[S.f0, 0, 'outCubic'], [S.f0 + 16, 100]]);
var bigLogo = H.logo('s6a_logo', 'grok', 210, { parent: S.cam, x: 420, y: -20, color: '#ffffff' });
cav.set(bigLogo, { in: S.f0 + 14 });
// countdown: days / hours fixed, minutes and seconds roll fast
var cd = cav.group('s6a_cd', { parent: S.cam, x: -420, y: 60 });
UI.t('s6a_lbl', 'Grok resets in', 46, { parent: cd, x: -380, y: 150, color: '#c3cad8', kind: 'label' });
UI.t('s6a_d', '3d 22h', 150, { parent: cd, x: -380, y: -20, color: '#ffffff', kind: 'display' });
var mm = UI.t('s6a_m', '59m', 150, { parent: cd, x: -380, y: -190, color: '#a4c5ff', kind: 'display' });
cav.counter(mm, S.f0, S.f1, 59, 12, { suffix: 'm', ease: 'linear' });
cav.slideIn(cd, S.f0, { dx: -80, dy: 0, dur: 18 });
var nn = H.head('s6a_n', 'No need to wait.', 84, { parent: S.g, color: H.C.green, x: -900, y: -430, align: 'left', in: S.beat(6) });
cav.pop(nn, S.beat(6), { dur: 14, from: 1.3 });
for (var b = 0; b < 8; b++) cav.punch(cd, S.beat(b), 0.02, 10);
cav.tween(S.cam, 'scale', S.f0, S.f1, 0.95, 1.08, 'linear');
// no cut into the sheet: s6a keeps playing under it, the ring and copy fade out, and the very same
// stars stretch into hyperspace streaks and fly off (s6r's stage only fades in behind them)
(function () {
  var E = S.f1, END = E + 34;
  cav.set(S.g, { out: END });
  [big.group, bigLogo, cd, nn].forEach(function (l, i) { cav.key(l, 'opacity', [[E, 100, 'inCubic'], [E + 14 + i * 2, 0]]); });
  var cxw = 120; // the field has drifted 120 px left, so the vanishing point sits at local x 120
  STARS.forEach(function (st, i) {
    var dx = st.x - cxw, dy = st.y, Z = 7;
    var ln = cav.line('s6a_sk' + i, [st.x, st.y], [cxw + dx * Z, dy * Z], { parent: sf, stroke: { color: '#ffffff', width: st.r * 2, cap: 'round', trim: true }, opacity: st.o, in: E });
    api.set(ln, { 'stroke.trimStart': 0, 'stroke.trimEnd': 0.05 });
    var d0 = E + 4 + Math.round(rnd() * 4);
    cav.key(ln, 'stroke.trimEnd', [[d0, 0.05, 'inExpo'], [d0 + 18, 45]]);
    cav.key(ln, 'stroke.trimStart', [[d0 + 8, 0, 'inExpo'], [d0 + 26, 100]]);
    cav.set(st.l, { out: E - 1 });
  });
})();
// shared element: the xAI logo flies out of the Grok card's avatar and becomes the big logo here
(function () {
  var c5 = T.s5c, SH0 = c5[0] + Math.round(6 * 28.125), SH1 = S.f0 + 14;
  var camS = 1 + 0.06 * (SH0 - c5[0]) / (c5[1] - c5[0]);
  var sx = (-1137 / 2 + 102) * 1.3 * camS, sy = (200 - 113 - 60 / 1.3) * 1.3 * camS;
  var lg = cav.group('xai_shared', { in: SH0, out: SH1 - 1 });
  H.logo('xai_shared_l', 'grok', 100, { parent: lg, color: '#ffffff' });
  cav.key(lg, 'position', [[SH0, [sx, sy], 'inOutCubic'], [SH1, [420 * 0.955, -20 * 0.955]]]);
  cav.key(lg, 'scale', [[SH0, 58 * 1.3 * camS / 100, 'inOutCubic'], [SH1, 2.1 * (0.95 + 0.13 * 14 / (T.s6a[1] - T.s6a[0]))]]);
})();
// ================= s6r1 + s6r2: the redeem sheet, rebuilt as in the app =================
var f0 = T.s6r1[0], f1 = T.s6r2[1], HIT = T.s6r2[0];
var B = function (n) { return f0 + Math.round(n * 28.125); };
var g = cav.group('s6r', { in: f0, out: f1 - 1 });
UI.stage('s6r_stage', TH, { parent: g, f0: f0, f1: f1 });
// the night (s6a, still playing underneath) gives way to the stage as its stars streak off
cav.key(cav.find('s6r_stage'), 'opacity', [[f0 + 10, 0, 'inOutQuad'], [f0 + 32, 100]]);
// the Grok detail page, refilled: once the sheet is half way out, it builds itself behind it
var DONE = T.s6r2[1] - 70, OUT0 = DONE + 6, OUT1 = DONE + 30, HALF = DONE + 24;
var det = cav.group('s6r_detail', { parent: g, in: HALF - 6 });
var dbg = cav.rect('s6r_det_bg', 2000, 1160, { parent: det, fill: TH.surface });
cav.key(dbg, 'opacity', [[HALF - 6, 0, 'outCubic'], [HALF + 6, 100]]);
var dh = cav.group('s6r_det_head', { parent: det });
UI.avatar('s6r_det_av', 'grok', 46, { parent: dh, x: -860, y: 400 });
UI.t('s6r_det_n', 'Grok', 52, { parent: dh, x: -795, y: 418, color: TH.onSurface, kind: 'title' });
UI.t('s6r_det_p', 'SuperGrok', 34, { parent: dh, x: -795, y: 372, color: TH.onSurfaceVar });
cav.slideIn(dh, HALF, { dx: -60, dy: 0, dur: 16 });
var dring = cav.group('s6r_det_ring', { parent: det, x: -430, y: -60 });
var dr = H.ring('s6r_det_r', 270, 0.01, { parent: dring, width: 50, color: TH.primary, trackColor: TH.track, gap: 12 });
cav.set(dr.arc, { opacity: 0 });
cav.key(dr.track, 'stroke.trimEnd', [[HALF, dr.gapPct + 0.1, 'outCubic'], [HALF + 26, 100 - dr.gapPct]]);
var dnum = H.head('s6r_det_num', '0%', 150, { parent: dring, y: 30, color: TH.onSurface });
var dwl = H.label('s6r_det_wl', 'weekly used', 42, { parent: dring, y: -70, color: TH.onSurfaceVar });
cav.pop(dnum, HALF + 8, { dur: 16 }); cav.fadeIn(dwl, HALF + 12, 10);
cav.set(dnum, { in: HALF + 8 }); cav.set(dwl, { in: HALF + 12 });
var drows = [['5-hour', 'Resets in 5h 00m', '0%'], ['Weekly', 'Resets Mon 12 Oct, 4:00 AM', '0%'], ['Resets', '2 left · the next expires Fri 9 Oct', '2']];
drows.forEach(function (r, i) {
  var rw = cav.group('s6r_det_row' + i, { parent: det, x: 480, y: 160 - i * 180, in: HALF + 4 + i * 5 });
  cav.rect('s6r_det_rbg' + i, 780, 160, { parent: rw, radius: 40, fill: TH.card });
  UI.t('s6r_det_rt' + i, r[0], 38, { parent: rw, x: -350, y: 22, color: TH.onSurface, kind: 'title' });
  UI.t('s6r_det_rs' + i, r[1], 28, { parent: rw, x: -350, y: -28, color: TH.onSurfaceVar });
  UI.t('s6r_det_rp' + i, r[2], 62, { parent: rw, x: 350, y: 0, align: 'right', color: TH.onSurface, kind: 'display' });
  cav.pop(rw, HALF + 4 + i * 5, { dur: 16 });
});
var cam = cav.group('s6r_cam', { parent: g });
// --- the sheet: 1000 wide, built in comp pixels, like RedeemSheet's Confirm step ---
var SW = 1000, SHh = 960;
var sh = cav.group('s6r_sheet', { parent: cam, x: 0, y: 0 });
// a real bottom sheet: the surface runs on past the bottom of the frame, only the top corners show
var EXT = 700;
cav.rect('s6r_shadow', SW, SHh + EXT, { parent: sh, radius: 72, fill: '#000000', opacity: 16, y: -EXT / 2 + 12 }); cav.blur(cav.find('s6r_shadow'), 50);
cav.rect('s6r_bg', SW, SHh + EXT, { parent: sh, radius: 72, fill: '#f3f5fb', y: -EXT / 2 });
cav.rect('s6r_handle', 96, 12, { parent: sh, radius: 6, fill: '#c3c6cf', y: SHh / 2 - 36 });
var L = -SW / 2 + 64, R = SW / 2 - 64, TOP = SHh / 2;
// header: avatar, name, "Resets"; count pill on the right
UI.avatar('s6r_av', 'grok', 54, { parent: sh, x: L + 54, y: TOP - 130 });
UI.t('s6r_name', 'Grok', 50, { parent: sh, x: L + 130, y: TOP - 112, color: TH.onSurface, kind: 'title' });
UI.t('s6r_sub', 'Resets', 34, { parent: sh, x: L + 130, y: TOP - 160, color: TH.onSurfaceVar });
var cnt = cav.group('s6r_cnt', { parent: sh, x: R - 95, y: TOP - 130 });
cav.rect('s6r_cntbg', 190, 72, { parent: cnt, radius: 36, fill: TH.hero });
var col = cav.group('s6r_col', { parent: cnt });
UI.t('s6r_c3', '3 left', 36, { parent: col, x: 0, y: 0, align: 'center', color: TH.onHero, kind: 'label' });
UI.t('s6r_c2', '2 left', 36, { parent: col, x: 0, y: -80, align: 'center', color: TH.onHero, kind: 'label' });
cav.mask(cav.rect('s6r_cntm', 190, 72, { parent: cnt, radius: 36 }), col);
// usage bars: label left, percent right, thick rounded bar (12 dp) with secondaryContainer track
var bars = [], pcts = [];
[['5-hour', TOP - 250], ['Weekly', TOP - 390]].forEach(function (r, i) {
  UI.t('s6r_bl' + i, r[0], 38, { parent: sh, x: L, y: r[1], color: TH.onSurface, kind: 'label' });
  pcts.push(UI.t('s6r_bp' + i, '100% used', 38, { parent: sh, x: R, y: r[1], align: 'right', color: TH.onSurfaceVar, kind: 'label' }));
  var tr = cav.rect('s6r_btr' + i, R - L, 36, { parent: sh, radius: 18, fill: TH.secondary, x: 0, y: r[1] - 62 });
  var bf = cav.rect('s6r_bfill' + i, R - L, 36, { parent: sh, radius: 18, fill: TH.primary, x: 0, y: r[1] - 62 });
  bars.push({ fill: bf, track: tr, y: r[1] - 62 });
});
// confirm copy (body + warning)
// confirm copy: the note (bodyMedium) and the Warning panel (tertiary container, error icon), wrapped
var body = cav.group('s6r_body', { parent: sh });
UI.t('s6r_body1', 'Headroom uses the reset that expires first,', 34, { parent: body, x: L, y: TOP - 540, color: TH.onSurfaceVar });
UI.t('s6r_body2', 'on Tue 6 Oct.', 34, { parent: body, x: L, y: TOP - 586, color: TH.onSurfaceVar });
var warn = cav.group('s6r_warn', { parent: sh });
cav.rect('s6r_warnbg', R - L, 140, { parent: warn, radius: 42, fill: TH.chip, y: TOP - 704 });
cav.circle('s6r_warni', 24, { parent: warn, fill: TH.onChip, x: L + 62, y: TOP - 680 });
UI.t('s6r_warnx', '!', 34, { parent: warn, x: L + 62, y: TOP - 680, align: 'center', color: TH.chip, kind: 'display' });
UI.t('s6r_warn1', 'You can use this reset before you reach', 32, { parent: warn, x: L + 112, y: TOP - 680, color: TH.onChip });
UI.t('s6r_warn2', 'a limit. It cannot be undone.', 32, { parent: warn, x: L + 112, y: TOP - 726, color: TH.onChip });
// buttons, right aligned: TextButton + Button
var btns = cav.group('s6r_btns', { parent: sh, y: -TOP + 100, out: B(4) + 6 });
UI.t('s6r_not', 'Not now', 40, { parent: btns, x: R - 470, y: 0, align: 'center', color: TH.primary, kind: 'label' });
var ub = cav.group('s6r_use', { parent: btns, x: R - 160 });
cav.rect('s6r_usebg', 320, 112, { parent: ub, radius: 56, fill: TH.primary });
UI.t('s6r_uset', 'Use a reset', 40, { parent: ub, x: 0, y: 0, align: 'center', color: '#ffffff', kind: 'label' });
// Working row: expressive loading indicator (a morphing cookie) + "Resetting your usage…"
var wkO = cav.group('s6r_workO', { parent: sh, out: HIT + 10 });
cav.key(wkO, 'opacity', [[HIT - 6, 100, 'inCubic'], [HIT + 8, 0]]);
var wk = cav.group('s6r_work', { parent: wkO, y: -TOP + 100, in: B(4) + 6 });
(function () {
  var N = 96, R0 = 50, W0 = B(4) + 6, W1 = HIT + 10, SEG = 18;
  var poly = function (n, a) { var k = Math.PI / n, m = ((a % (2 * k)) + 2 * k) % (2 * k) - k; return Math.cos(k) / Math.cos(m); };
  var sup = function (a, bx, by, e) { var c = Math.abs(Math.cos(a)), d = Math.abs(Math.sin(a)); return 1 / Math.pow(Math.pow(c / bx, e) + Math.pow(d / by, e), 1 / e); };
  var shapes = [
    function (a) { return 0.9 * (1 + 0.1 * Math.cos(10 * a)); },                 // soft burst
    function (a) { return 0.92 * (1 + 0.08 * Math.cos(9 * a)); },                // 9-sided cookie
    function (a) { return 0.95 * (0.8 * poly(5, a + Math.PI / 2) + 0.2) / 1.05; }, // pentagon
    function (a) { return sup(a, 1, 0.56, 3); },                                 // pill
    function (a) { return 0.9 * (1 + 0.09 * Math.cos(8 * a)); },                 // sunny
    function (a) { return 0.88 * (1 + 0.13 * Math.cos(4 * a)); },                // 4-sided cookie
    function (a) { return sup(a, 1, 0.74, 2); }                                  // oval
  ];
  var ease = function (t) { return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2; };
  for (var fr = W0; fr < W1; fr++) {
    var t = (fr - W0) / SEG, i0 = Math.floor(t) % shapes.length, i1 = (i0 + 1) % shapes.length;
    var m = ease(Math.min(1, (t - Math.floor(t)) / 0.55));
    var rot = (fr - W0) * 4 + Math.floor(t) * 90 + m * 90;
    var pts = [];
    for (var j = 0; j < N; j++) {
      var a = j / N * Math.PI * 2, rr = R0 * ((1 - m) * shapes[i0](a) + m * shapes[i1](a)), ar = a + rot * Math.PI / 180;
      pts.push([rr * Math.cos(ar), rr * Math.sin(ar)]);
    }
    cav.path('s6r_li' + fr, pts, { parent: wk, closed: true, smooth: true, fill: TH.primary, x: -255, in: fr, out: fr });
  }
})();
UI.t('s6r_wkt', 'Resetting your usage…', 42, { parent: wk, x: -150, y: 0, color: TH.onSurface, kind: 'title' });
cav.fadeIn(wk, B(4) + 6, 8);
// working shimmer: a soft white band sweeps along each bar only (SrcAtop: masked to the bar)
bars.forEach(function (b, i) {
  var band = cav.rect('s6r_shm' + i, 260, 60, { parent: sh, fill: '#ffffff', opacity: 65, y: b.y, in: B(4) + 6, out: HIT - 1 });
  cav.blur(band, 22);
  var ks = [], per = 66;
  for (var t0 = B(4) + 6; t0 < HIT; t0 += per) { ks.push([t0, [-R + L - 300, b.y], 'linear']); ks.push([Math.min(HIT - 1, t0 + per - 1), [R - L + 300, b.y]]); }
  cav.key(band, 'position', ks);
  cav.mask(cav.rect('s6r_shmm' + i, R - L, 36, { parent: sh, radius: 18, y: b.y }), band);
});
// sheet enters over the starfield, then sits in front of the copy
// anchored to the bottom: content bottom (local -436) sits just above the frame's bottom edge.
// After the reset the content is shorter, so the sheet settles lower (its top comes down), like the app.
var SY1 = -64, SY2 = -142.5, SY3 = SY2 - 220 * 0.82, SUC0 = HIT + 60;
cav.key(sh, 'position', [[f0, [0, -1300], 'outExpo'], [f0 + 30, [0, SY1]], [B(2), [0, SY1], 'inOutCubic'], [B(3), [380, SY2]], [SUC0, [380, SY2], 'outCubic'], [SUC0 + 24, [380, SY3]], [OUT0, [380, SY3], 'inCubic'], [OUT1, [380, -1500]]]);
cav.key(sh, 'scale', [[B(2), 1, 'inOutCubic'], [B(3), 0.82]]);
TAPF(sh, R - 160, -TOP + 100, B(4));
var lt1 = H.head('s6r_t1', 'Got a spare reset?', 76, { parent: cam, x: -900, y: 60, align: 'left', in: B(3) });
var lt2 = H.head('s6r_t2', 'Use it in one tap.', 76, { parent: cam, x: -900, y: -50, align: 'left', color: TH.primary, in: B(3) + 4 });
cav.slideIn(lt1, B(3), { dx: -60, dy: 0, dur: 16 }); cav.slideIn(lt2, B(3) + 4, { dx: -60, dy: 0, dur: 16 });
// the copy leaves as the reset lands, instead of popping off
var ltg = cav.group('s6r_ltg', { parent: cam, out: HIT + 14 }); api.parent(lt1, ltg); api.parent(lt2, ltg);
cav.key(ltg, 'opacity', [[HIT - 8, 100, 'inCubic'], [HIT + 12, 0]]);
cav.key(ltg, 'position.x', [[HIT - 8, 0, 'inCubic'], [HIT + 12, -80]]);
// one clean, slow push towards the sheet through the riser; no zoom-out jump
cav.key(cam, 'scale', [[B(4), 1, 'inOutCubic'], [HIT + 20, 1.1, 'inOutCubic'], [T.s6r2[1] - 60, 1.1, 'inOutCubic'], [T.s6r2[1], 1.0]]);
cav.key(cam, 'position', [[B(4), [0, 0], 'inOutCubic'], [HIT + 20, [-380 * 1.1, 30], 'inOutCubic'], [T.s6r2[1] - 60, [-380 * 1.1, 30], 'inOutCubic'], [T.s6r2[1], [-380, 30]]]);
// ===== the reset lands: bars drain in turn (critically damped, no overshoot) =====
bars.forEach(function (b, i) {
  var t0 = HIT + i * 10;
  // shrink from the right edge: animate width and keep the left edge pinned
  var Wd = R - L;
  cav.key(b.fill, 'generator.dimensions', [[t0, [Wd, 36], 'outExpo'], [t0 + 40, [36, 36]]]);
  cav.key(b.fill, 'position.x', [[t0, 0, 'outExpo'], [t0 + 40, -Wd / 2 + 18]]);
  cav.counter(pcts[i], t0, t0 + 40, 100, 0, { suffix: '% used', ease: 'outExpo' });
  // then a gloss: a narrow leaning iridescent band sweeps the bar once, stars twinkle as it passes
  var gl0 = t0 + 44, gl1 = gl0 + 32;
  var gloss = cav.rect('s6r_gloss' + i, 30, 90, { parent: sh, rotation: -20, y: b.y, in: gl0, out: gl1 });
  cav.gradient(gloss, ['#ffd1e8', '#ffffff', '#c9f2ff', '#e5d4ff'], { rotation: 0 });
  cav.key(gloss, 'position.x', [[gl0, L - 40, 'inOutQuad'], [gl1, R + 40]]);
  cav.mask(cav.rect('s6r_glm' + i, R - L, 36, { parent: sh, radius: 18, y: b.y }), gloss);
  for (var k2 = 0; k2 < 6; k2++) {
    var sxp = L + (R - L) * (k2 + 0.5) / 6, born = gl0 + Math.round(32 * (k2 + 0.5) / 6);
    var stc = ['#ffb3d9', '#ffe08a', '#a8e6ff', '#d0b8ff', '#b6f0c2', '#ffffff'][k2];
    var st2 = cav.star('s6r_gs' + i + '_' + k2, 4, 12 + rnd() * 8, { parent: sh, inner: 3, fill: stc, x: sxp, y: b.y + (rnd() - 0.5) * 70, in: born, out: born + 16 });
    cav.key(st2, 'scale', [[born, 0, 'outBack'], [born + 6, 1.2, 'inCubic'], [born + 16, 0]]);
    cav.key(st2, 'rotation', [[born, 0], [born + 16, 40]]);
  }
});
// success step: title and count update after the bars settle, then the confetti (after the gloss)
var SUC = HIT + 60, CONF = HIT + 90;
var okt = UI.t('s6r_ok', 'Usage reset', 50, { parent: sh, x: L + 130, y: TOP - 112, color: TH.onSurface, kind: 'title', in: SUC });
cav.set(cav.find('s6r_name'), { out: SUC - 1 });
cav.fadeIn(okt, SUC, 8);
cav.key(col, 'position.y', [[SUC, 0, 'outBack'], [SUC + 16, 80]]);
cav.punch(cnt, SUC + 2, 0.12, 14);
var dn = cav.group('s6r_done', { parent: sh, x: R - 160, y: TOP - 650, in: SUC });
cav.rect('s6r_donebg', 320, 112, { parent: dn, radius: 56, fill: TH.primary });
UI.t('s6r_donet', 'Done', 40, { parent: dn, x: 0, y: 0, align: 'center', color: '#ffffff', kind: 'label' });
cav.pop(dn, SUC, { dur: 14 });
cav.set(warn, { out: SUC - 1 });
UI.t('s6r_left', '2 resets left. The next expires on Fri 9 Oct.', 34, { parent: sh, x: L, y: TOP - 540, color: TH.onSurface, in: SUC });
cav.set(body, { out: SUC - 1 });
// confetti: fast burst, then a steady fall (never slowing), flutter and spin, 4 shapes
var cols = [TH.primary, '#ff4fa3', '#ffd60a', '#3ddc84', '#2e9bff', '#ff8c1a', '#8a4dff', '#ffffff'];
var cf = cav.group('s6r_cf', { parent: g, in: CONF });
for (var k = 0; k < 110; k++) {
  var kind = k % 4, sz = 8 + rnd() * 18, p;
  if (kind === 0) p = cav.rect('s6r_cf' + k, sz, sz * (1.6 + rnd()), { parent: cf, fill: cols[k % 8], radius: 3 });
  else if (kind === 1) p = cav.circle('s6r_cf' + k, sz * 0.45, { parent: cf, fill: cols[(k + 3) % 8] });
  else if (kind === 2) p = cav.path('s6r_cf' + k, [[-sz, 0], [-sz / 2, sz / 2], [0, 0], [sz / 2, -sz / 2], [sz, 0]], { parent: cf, noFill: true, smooth: true, stroke: { color: cols[(k + 5) % 8], width: 4, cap: 'round' } });
  else p = cav.star('s6r_cf' + k, 5, sz * 0.6, { parent: cf, inner: sz * 0.28, fill: cols[(k + 1) % 8] });
  var a3 = -Math.PI / 2 + (rnd() - 0.5) * 2.4, v = 700 + rnd() * 700;
  var ax = Math.cos(a3) * v * -1, ay = -Math.sin(a3) * v * 0.9 + 200;
  var apex = CONF + 16 + Math.round(rnd() * 10);
  var drift = (rnd() - 0.5) * 500, endY = -1400 - rnd() * 400;
  cav.key(p, 'position', [[CONF, [0, -380], 'outCubic'], [apex, [ax, ay], 'inQuad'], [f1, [ax + drift, endY]]]);
  cav.key(p, 'rotation', [[CONF, rnd() * 360, 'linear'], [f1, rnd() * 1440 - 720]]);
  var fl = [[CONF, 1, 'inOutQuad']];
  for (var q2 = 1; q2 < 8; q2++) fl.push([CONF + q2 * (14 + (k % 5) * 3), q2 % 2 ? -1 : 1, 'inOutQuad']);
  cav.key(p, 'scale.x', fl);
}
var bt = H.head('s6r_bt', 'Back to full.', 80, { parent: g, y: 468, in: CONF, out: DONE + 1 });
cav.pop(bt, CONF, { dur: 16, from: 1.3 });
// Done: tap and the sheet leaves, so the shot keeps moving to the end
TAPF(sh, R - 160, TOP - 650, DONE);
// ================= s6b: or just wait: the reset island =================
S = H.shot('s6b', '#e6e9f3');
var dv = UI.device('s6b_dev', 2400, { parent: S.cam, x: 0, y: -900, body: '#121317', rim: '#62656f' });
OS.home('s6b_home', { parent: dv.screen });
var isl = OS.island('s6b_isl', 'grok', { parent: dv.screen, y: UI.SH / 2 - 70 });
var I0 = S.f0 + 8;
// Grows like IslandSurface: hole (72) -> capsule (108) for the first 35%, then sideways to 492,
// on a spring that passes its target a little. Content fades in, the ring refills, then "reset".
cav.key(isl.pill, 'generator.dimensions', [[I0, [72, 72], 'inQuad'], [I0 + 8, [108, 108], 'outBack'], [I0 + 30, [492, 108]]]);
cav.set(isl.content, { opacity: 0 }); cav.key(isl.content, 'opacity', [[I0 + 20, 0, 'outCubic'], [I0 + 34, 100]]);
api.set(isl.ring.arc, { 'stroke.trimEnd': 0 });
H.trim(isl.ring.arc, I0 + 34, I0 + 88, 0, 100, 'inOutQuad');
cav.set(isl.word, { opacity: 0 }); cav.key(isl.word, 'opacity', [[I0 + 88, 0, 'outCubic'], [I0 + 100, 100]]);
cav.key(isl.word, 'position.x', [[I0 + 88, 198, 'outCubic'], [I0 + 100, 216]]);
// camera: start tight on the island, pull back to the phone and copy
cav.key(S.cam, 'scale', [[S.f0, 1.6, 'inOutCubic'], [S.beat(3), 1.6, 'inOutCubic'], [S.beat(5), 0.62]]);
cav.key(S.cam, 'position', [[S.f0, [0, -600], 'inOutCubic'], [S.beat(3), [0, -600], 'inOutCubic'], [S.beat(5), [380, 120]]]);
var w1 = H.head('s6b_t1', 'Or just wait.', 88, { parent: S.g, x: -900, y: 70, align: 'left', in: S.beat(5) });
var w2 = H.head('s6b_t2', 'Headroom tells you', 64, { parent: S.g, x: -900, y: -40, align: 'left', color: TH.primary, in: S.beat(5) + 4 });
var w3 = H.head('s6b_t3', 'the second it resets.', 64, { parent: S.g, x: -900, y: -120, align: 'left', color: TH.primary, in: S.beat(5) + 8 });
[w1, w2, w3].forEach(function (w, i) { cav.slideIn(w, S.beat(5) + i * 4, { dx: -60, dy: 0, dur: 16 }); });
return 'C ok';
