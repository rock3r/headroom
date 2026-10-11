// Hook, logo, overview build, providers.
cav.clear();
var c = cav.comp(), T = H.T.shots, TH = UI.TH.cobalt;
function beatAt(f0, n) { return f0 + Math.round(n * 28.125); }
globalThis.TAPF = function (parent, x, y, f, col) {
  var r = cav.circle('tap_' + f + '_' + Math.round(x), 12, { parent: parent, x: x, y: y, fill: col || '#ffffff', opacity: 0, in: f - 12, out: f + 26 });
  cav.key(r, 'scale', [[f, 1, 'outCubic'], [f + 22, 7]]);
  cav.key(r, 'opacity', [[f, 55, 'outCubic'], [f + 22, 0]]);
  var d = cav.circle('tapd_' + f + '_' + Math.round(x), 26, { parent: parent, x: x, y: y, fill: '#000000', opacity: 0, in: f - 12, out: f + 10 });
  cav.key(d, 'opacity', [[f - 12, 0, 'outCubic'], [f - 4, 22], [f + 10, 0]]);
  cav.key(d, 'scale', [[f - 12, 1.4, 'outCubic'], [f, 0.85], [f + 10, 1.1]]);
};
// ================= HOOK (s1a + s1c + s1d share one stage) =================
var hookIn = 0, hookOut = T.s1d[1] - 1, LIMIT = T.s1c[0];
var hook = cav.group('hook', { in: hookIn, out: hookOut });
cav.rect('hook_bg', 2000, 1160, { parent: hook, fill: H.C.paper });
// Agent transcript scrolling behind.
var tr = cav.group('hook_tr', { parent: hook, x: -880, y: 0 });
var lines = ['› Read app/src/main/kotlin/ui/OverviewScreen.kt', '› Edit QuotaBar.kt   +42 −7', '› Run ./gradlew check', '  ✓ 312 tests passed', '› Edit PaceChart.kt   +88 −21', '› Thinking…', '› Write ResetIsland.kt', '› Run ./gradlew :app:installDebug', '  ✓ BUILD SUCCESSFUL in 41s', '› Edit RedeemSheet.kt   +17 −4', '› Read docs/RESETS.md', '› Edit WidgetBars.kt   +63 −12', '› Run ./gradlew check', '  ✓ 318 tests passed', '› Edit ThemePalettes.kt   +9 −2', '› Thinking…', '› Edit HomeDelights.kt   +31 −5', '› Run ./gradlew lint', '  ✓ 0 issues'];
for (var i = 0; i < lines.length; i++) {
  var ln = cav.text('hook_ln' + i, lines[i], 30, { parent: tr, x: 0, y: -i * 58, align: 'left', color: lines[i].indexOf('✓') >= 0 ? '#2e7d4f' : '#7d8597', font: UI.MONO, style: 'Regular' });
}
var err = cav.text('hook_err', '✕ Usage limit reached · resets in 4h 12m', 30, { parent: tr, x: 0, y: -lines.length * 58, align: 'left', color: '#ba1a1a', font: UI.MONO, style: 'Regular', in: LIMIT });
cav.set(tr, { opacity: 55 });
cav.blur(tr, 1.5);
// scroll so the last lines reach the vertical middle exactly at the limit
cav.key(tr, 'position.y', [[0, 420, 'linear'], [LIMIT, (lines.length - 6) * 58]]);
var trR = cav.group('hook_tr2', { parent: hook, x: 380, y: 0 });
var lines2 = ['tokens   1.2M', 'tokens   1.4M', 'tokens   1.9M', 'tokens   2.6M', 'tokens   3.1M', 'tokens   3.8M', 'tokens   4.4M', 'tokens   5.0M'];
for (var j = 0; j < lines2.length; j++) cav.text('hook_tk' + j, lines2[j], 30, { parent: trR, x: 0, y: -j * 116, align: 'left', color: '#9aa1b2', font: UI.MONO, style: 'Regular' });
cav.set(trR, { opacity: 40 }); cav.blur(trR, 2.5);
cav.key(trR, 'position.y', [[0, 380, 'linear'], [LIMIT, 380 + 7 * 116]]);
// veil so the transcript reads as background
var veil = cav.rect('hook_veil', 1500, 520, { parent: hook, fill: H.C.paper, y: 40, opacity: 82 });
cav.blur(veil, 60);
// Foreground copy.
var h1 = H.head('hook_t1', 'Your agent is on a roll.', 104, { parent: hook, y: 200, out: 119 });
cav.slideIn(h1, 4, { dy: -40, dur: 18 });
var h2 = H.head('hook_t2', 'Faster than your plan.', 104, { parent: hook, y: 200, in: 120, out: LIMIT - 1 });
cav.slideIn(h2, 120, { dy: -40, dur: 14 });
cav.slideOut(h1, 106, { dy: 40, dur: 13 });
// The one usage bar: fills steadily, passes the static pace tick, goes wavy, hits 100% on the limit downbeat.
var bar = UI.bar('hook_bar', -760, 760, 70, { parent: hook, th: 38, color: H.C.cobalt, track: '#d5e0f5', pct: 0, pace: 62, tickColor: '#171b22' });
cav.set(bar.g, { out: T.s1c[1] - 1 });
UI.barSet(bar, 8, 0, undefined, 'inQuad');
UI.barSet(bar, LIMIT, 100, undefined, 'linear');
UI.barWavyGrow(bar, 183, 10, 2);
// Limit: bar turns red, flash, shake, pill slams in.
UI.barColor(bar, LIMIT - 2, '#ba1a1a', 6);
cav.flash(LIMIT, { dur: 6, peak: 45, color: '#ffdad6' });
cav.shake(hook, LIMIT, 26, 20);
var pill = cav.group('hook_pill', { parent: hook, y: 250, in: LIMIT, out: T.s1c[1] - 1 });
cav.rect('hook_pill_bg', 1180, 200, { parent: pill, radius: 100, fill: '#ffdad6' });
H.head('hook_pill_t', 'Usage limit reached', 86, { parent: pill, color: '#93000a', y: 26 });
H.label('hook_pill_s', 'Resets in 4h 12m. Your agent waits.', 40, { parent: pill, color: '#93000a', y: -52 });
cav.pop(pill, LIMIT, { dur: 14, from: 1.5 });
// s1d: Know your headroom, then everything collapses into a dot (dot only appears as it collapses).
var f = T.s1d[0];
var gl = cav.glyphs('hook_know', 'Know your headroom.', 140, { parent: hook, font: H.FONT, color: H.C.ink, y: 0 });
cav.set(gl.group, { in: f });
cav.cascade(gl, f + 1, { step: 2, dy: -60, dur: 16 });
cav.tween(tr, 'opacity', f, f + 20, 55, 20, 'outCubic');
cav.tween(hook, 'scale', T.s1d[1] - 16, T.s1d[1] - 1, 1, 0.01, 'inExpo');
cav.circle('hook_dot', 14, { fill: H.C.icon, in: T.s1d[1] - 3, out: T.s2a[0] + 3 });
// ================= LOGO =================
var S = H.shot('s2a', H.C.icon);
// the blue opens as a circle out of the hook's dot
cav.set(S.bg, { opacity: 0 });
var wipeIn = cav.circle('s2a_wipe', 14, { parent: S.g, fill: H.C.icon });
cav.key(wipeIn, 'generator.radius', [[S.f0, [14, 14], 'outExpo'], [S.f0 + 18, [1200, 1200]]]);
cav.order(wipeIn, S.cam);
var rr = H.ring('s2a_ring', 170, 75, { parent: S.cam, color: H.C.white, trackColor: '#7b8fd0', width: 66 });
H.trim(rr.arc, S.f0 + 4, S.f0 + 40, 0, 100, 'outCubic');
cav.pop(rr.track, S.f0 + 2, { dur: 18 });
var d2 = cav.circle('s2a_dot', 52, { parent: S.cam, fill: H.C.white });
cav.pop(d2, S.beat(2), { dur: 14, ease: 'outBackBig' });
cav.ring(S.f0, 0, 0, { r1: 900, dur: 30, width: 14, color: '#ffffff' });
cav.tween(S.cam, 'scale', S.f0, S.f1, 1.15, 1, 'outCubic');
S = H.shot('s2b', H.C.icon);
var lock = cav.group('s2b_lock', { parent: S.cam, y: 40 });
var lr = H.ring('s2b_ring', 92, 75, { parent: lock, color: H.C.white, trackColor: '#7b8fd0', width: 36 });
cav.circle('s2b_dot', 28, { parent: lock, fill: H.C.white });
var wmg = cav.group('s2b_wmg', { parent: lock });
var wm = H.head('s2b_t', 'Headroom', 170, { parent: wmg, color: H.C.white, x: 160, y: 0, align: 'left' });
cav.slideIn(wmg, S.f0 + 2, { dx: -60, dy: 0, dur: 18 });
var tgg = cav.group('s2b_sg', { parent: S.cam }); // moved by 90_centre to line up with the wordmark
var tg = H.label('s2b_s', 'Your AI tokens watchtower.', 54, { parent: tgg, color: '#dde3ff', y: -170 });
cav.slideIn(tg, S.beat(1), { dy: -24, dur: 18 });
cav.tween(S.cam, 'scale', S.f0, S.f1, 1, 1.05, 'linear');
// the lockup bows out, then the blue closes as a circle (in s3a, so it sits over the overview)
cav.key(lock, 'scale', [[S.f1 - 16, 1, 'inBack'], [S.f1 - 2, 0.6]]);
cav.key(lock, 'opacity', [[S.f1 - 12, 100, 'inCubic'], [S.f1 - 2, 0]]);
cav.key(tg, 'opacity', [[S.f1 - 16, 100, 'inCubic'], [S.f1 - 6, 0]]);
// ================= OVERVIEW BUILD (s3a) =================
S = H.shot('s3a');
UI.stage('s3a_stage', TH, { parent: S.g, f0: S.f0, f1: S.f1 });
cav.order(cav.find('s3a_stage'), S.cam);
var t1 = H.head('s3a_t1', 'All your AI plans.', 92, { parent: S.cam, x: -860, y: 70, align: 'left' });
var t2 = H.head('s3a_t2', 'One screen.', 92, { parent: S.cam, x: -860, y: -50, align: 'left', color: H.C.cobalt });
cav.slideIn(t1, S.beat(1), { dx: -60, dy: 0, dur: 18 }); cav.slideIn(t2, S.beat(2), { dx: -60, dy: 0, dur: 18 });
var ov = UI.overview('s3a_ov', TH, { parent: S.cam, x: 470 });
cav.set(ov.g, { scale: 0.38 });
cav.tween(ov.g, 'position.y', S.f0, S.f1, -30, 30, 'linear');
var shadow = cav.rect('s3a_sh', UI.SW, UI.SH, { parent: ov.g, fill: '#000000', opacity: 10, radius: 120, y: -40 });
cav.blur(shadow, 60); cav.order(shadow, ov.bg);
cav.set(ov.bg, { opacity: 0 });
cav.key(ov.bg, 'opacity', [[S.beat(5), 0, 'outCubic'], [S.beat(6), 100]]);
cav.key(shadow, 'opacity', [[S.beat(5), 0, 'outCubic'], [S.beat(6), 10]]);
var P = ov.p, items = [P.title.g, P.hero.g, P.week, P.c1.g, P.c2.g, P.c3.g, P.tb.g];
items.forEach(function (it, k) {
  var fb = S.f0 + 4 + k * 14;
  var py = api.get(it, 'position');
  cav.key(it, 'position', [[fb, [py.x + (k % 2 ? 260 : -260), py.y - 520], 'outBack'], [fb + 22, [py.x, py.y]]]);
  cav.key(it, 'scale', [[fb, 0.7, 'outBack'], [fb + 22, 1]]);
  cav.key(it, 'opacity', [[fb, 0, 'outCubic'], [fb + 8, 100]]);
  cav.key(it, 'rotation', [[fb, k % 2 ? 6 : -6, 'outBack'], [fb + 24, 0]]);
});
[P.c1, P.c2, P.c3].forEach(function (cd, k) {
  var fb = S.f0 + 4 + (k + 3) * 14 + 10;
  var d = [UI.DATA.claude, UI.DATA.codex, UI.DATA.grok][k];
  UI.barSet(cd.weekly, fb, 0, undefined, 'outCubic'); UI.barSet(cd.weekly, fb + 30, d.pct, undefined, 'linear');
  if (cd.session) { UI.barSet(cd.session, fb + 4, 0, undefined, 'outCubic'); UI.barSet(cd.session, fb + 34, d.session.pct, undefined, 'linear'); }
  if (cd.chip) cav.pop(cd.chip, fb + 30, { dur: 12 });
});
// ================= PROVIDERS (s3b): each provider's card arrives with its details on its beat =================
S = H.shot('s3b');
UI.stage('s3b_stage', TH, { parent: S.g, f0: S.f0, f1: S.f1 });
cav.order(cav.find('s3b_stage'), S.cam);
H.ORDER.forEach(function (k, i) {
  var d = UI.DATA[k], fb = S.beat(i);
  var x = i % 2 ? 372 : -372, y = 300 - Math.floor(i / 2) * 200;
  var m = UI.mini('s3b_m' + i, TH, d, { parent: S.cam, x: x, y: y, in: fb - 2 });
  cav.set(m.g, { scale: 0.62 });
  cav.key(m.g, 'scale', [[fb - 2, 0.3, 'spring'], [fb + 22, 0.62]]);
  cav.key(m.g, 'opacity', [[fb - 2, 0, 'outCubic'], [fb + 6, 100]]);
  cav.key(m.g, 'position.x', [[fb - 2, x + (i % 2 ? 300 : -300), 'outExpo'], [fb + 20, x]]);
  cav.key(m.av, 'rotation', [[fb, -40, 'outBack'], [fb + 24, 0]]);
  UI.barSet(m.bar, fb + 4, 0, undefined, 'outCubic'); UI.barSet(m.bar, fb + 30, d.pct, undefined, 'linear');
});
cav.tween(S.cam, 'scale', S.f0, S.f1, 1.04, 1, 'outCubic');
var wipeOut = cav.circle('s3a_wipe', 1200, { fill: H.C.icon, in: T.s3a[0], out: T.s3a[0] + 22 });
cav.key(wipeOut, 'generator.radius', [[T.s3a[0], [1200, 1200], 'inOutCubic'], [T.s3a[0] + 20, [0, 0]]]);
return 'A ok';
