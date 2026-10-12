// Pace story, detail, chart, running out.
var T = H.T.shots, TH = UI.TH.cobalt;
// Headline swapper: a stack of lines, one visible at a time, swapped on given frames.
function swap(name, parent, lines, frames, o) {
  o = o || {};
  var out = [];
  lines.forEach(function (s, i) {
    var f0 = frames[i], f1 = i + 1 < frames.length ? frames[i + 1] : (o.end || f0 + 200);
    var t = H.head(name + i, s, o.size || 92, { parent: parent, x: o.x || 0, y: o.y || 0, align: o.align || 'center', color: (o.colors && o.colors[i]) || H.C.ink, in: f0, out: f1 - 1 });
    cav.slideIn(t, f0, { dy: -50, dur: 14, ease: 'outBack' });
    if (i + 1 < frames.length) cav.slideOut(t, f1 - 10, { dy: 50, dur: 10 });
    out.push(t);
  });
  return out;
}
globalThis.SWAP = swap;
// ================= s4a + s4b: one bar teaches pace =================
var f0 = T.s4a[0], f1 = T.s4b[1];
var g = cav.group('s4', { in: f0, out: f1 - 1 });
UI.stage('s4_stage', TH, { parent: g, f0: f0, f1: f1 });
var cam = cav.group('s4_cam', { parent: g });
var B = function (n) { return f0 + Math.round(n * 28.125); };
// One Claude card, under pace to begin with: 34% used, even pace at 41%, flat bar.
var d = JSON.parse(JSON.stringify(UI.DATA.claude)); d.wavy = false; d.chip = null; d.pct = 34; d.pace = 60; d.x0 = 330;
// a second weekly limit, for Fable: it shares the week, so it shares the pace tick, but grows far slower
d.session = { label: 'Weekly Fable', pct: 0, when: 'Wed 7:00 AM', pace: 60, x1: 790 };
var cc = UI.card('s4_claude', TH, d, { parent: cam });
var cg = cav.group('s4_cg', { parent: cam }); api.parent(cc.g, cg); cav.set(cc.g, { x: -1137 / 2, y: 194 });
cav.set(cg, { scale: 1.5, y: -90 });
cav.key(cg, 'scale', [[f0, 2.6, 'outExpo'], [f0 + 26, 1.5]]);
// no chip in this story, so the card is only as tall as its two rows
var cbg = cav.find('s4_claude_bg');
cav.key(cbg, 'generator.dimensions', [[f0, [1137, 388], 'linear'], [f1, [1137, 388]]]);
cav.key(cbg, 'position.y', [[f0, -194, 'linear'], [f1, -194]]);
cav.flash(f0, { dur: 6, peak: 35 });
UI.barSet(cc.weekly, f0 + 2, 0, undefined, 'outCubic'); UI.barSet(cc.weekly, B(3), 34, undefined, 'linear');
UI.barSet(cc.session, f0 + 12, 0, undefined, 'outCubic'); UI.barSet(cc.session, B(3) + 8, 9, undefined, 'linear');
cav.counter(cc.pct, f0 + 2, B(3), 0, 34, { suffix: '%' });
// time passes: weekly usage climbs past the fixed pace mark (34% -> 71%); Fable doesn't move, so neither does its bar.
var x0 = 330, x1 = 790, tick = cc.weekly.tick;
UI.barSet(cc.weekly, B(8), 34, undefined, 'inOutCubic'); UI.barSet(cc.weekly, B(11), 71, undefined, 'linear');
// a second number layer, so the two counters never fight over one text
cav.set(cc.pct, { out: B(8) - 1 });
var pct2 = UI.t('s4_claude_pct2', '34%', 86, { parent: cc.g, x: 1088, y: -88, align: 'right', color: TH.onSurface, kind: 'display', in: B(8) });
cav.counter(pct2, B(8), B(11), 34, 71, { suffix: '%', ease: 'inOutCubic' });
// the fill passes the tick about two thirds of the way through: it turns wavy there
// the fill passes the tick at about frame 1395: the wave grows in from the left there
// the frame where the fill (34 -> 71, inOutCubic over B8..B11) passes the mark at 60
var WV = (function () { var e = (60 - 34) / 37, t = e < 0.5 ? Math.cbrt(e / 4) : 1 - Math.cbrt(2 * (1 - e)) / 2; return Math.round(B(8) + t * (B(11) - B(8))); })();
UI.barWavyGrow(cc.weekly, WV, 10, 2);
swap('s4_h', cam, ['Headroom lets you know.', 'Flat bar: all good.', 'Wavy bar: slow down!'], [f0 + 6, B(5), WV + 4], { y: 340, size: 92, colors: [H.C.ink, H.C.cobalt, '#ba1a1a'], end: f1 });
cav.tween(cam, 'scale', f0, f1, 1, 1.05, 'linear');
// ================= s5a: detail, rebuilt =================
var S = H.shot('s5a');
UI.stage('s5a_stage', TH, { parent: S.g, f0: S.f0, f1: S.f1 }); cav.order(cav.find('s5a_stage'), S.cam);
var dg = cav.group('s5a_ring', { parent: S.cam, x: -430, y: 20 });
var r1 = H.ring('s5a_r1', 300, 71, { parent: dg, width: 54, color: TH.primary, trackColor: TH.track });
var r2 = H.ring('s5a_r2', 220, 38, { parent: dg, width: 30, color: '#8e4585', trackColor: '#f1e4ee' });
H.trim(r1.arc, S.f0 + 6, S.beat(3), 0, 100, 'outCubic'); H.trim(r2.arc, S.beat(1), S.beat(4), 0, 100, 'outCubic');
var num = H.head('s5a_num', '0%', 150, { parent: dg, y: 30, color: TH.onSurface });
cav.counter(num, S.f0 + 6, S.beat(3), 0, 71, { suffix: '%' });
H.label('s5a_wl', 'weekly used', 42, { parent: dg, y: -70, color: TH.onSurfaceVar });
cav.pop(dg, S.f0, { dur: 18 });
// rows of limits pop in on the right
var rows = [['Session', 'Resets in 1h 12m · no alert', '38%'], ['Weekly · all models', 'Resets Wed 30 Sep, 7:00 AM', '71%'], ['Weekly · Fable', 'Resets Wed 30 Sep, 7:00 AM', '52%']];
var rg = cav.group('s5a_rows', { parent: S.cam, x: 480, y: 120 });
rows.forEach(function (r, i) {
  var rw = cav.group('s5a_row' + i, { parent: rg, y: -i * 175, in: S.beat(2 + i) });
  cav.rect('s5a_rowbg' + i, 760, 160, { parent: rw, radius: 40, fill: TH.card });
  UI.t('s5a_rt' + i, r[0], 38, { parent: rw, x: -340, y: 10, color: TH.onSurface, kind: 'title' });
  UI.t('s5a_rs' + i, r[1], 28, { parent: rw, x: -340, y: -38, color: TH.onSurfaceVar });
  UI.t('s5a_rp' + i, r[2], 62, { parent: rw, x: 340, y: -20, align: 'right', color: TH.onSurface, kind: 'display' });
  cav.slideIn(rw, S.beat(2 + i), { dx: 200, dy: 0, dur: 16, ease: 'outBack' });
});
var h5 = H.head('s5a_t', 'Every limit, down to the hour.', 72, { parent: S.cam, y: 430, in: S.beat(1) });
cav.slideIn(h5, S.beat(1), { dy: -30, dur: 16 });
cav.tween(S.cam, 'scale', S.f0, S.f1, 1, 1.05, 'linear');
// ================= s5b: pace chart that makes sense =================
S = H.shot('s5b');
UI.stage('s5b_stage', TH, { parent: S.g, f0: S.f0, f1: S.f1 }); cav.order(cav.find('s5b_stage'), S.cam);
var W = 1400, Hh = 560, cy = -60, X0 = -W / 2, Y0 = cy - Hh / 2;
var card = cav.rect('s5b_card', W + 140, Hh + 200, { parent: S.cam, radius: 56, fill: TH.card, y: cy + 10 });
var days = ['Wed', 'Thu', 'Fri', 'Sat', 'Sun', 'Mon', 'Tue'];
days.forEach(function (dname, i) { UI.t('s5b_d' + i, dname, 30, { parent: S.cam, x: X0 + W * (i + 0.5) / 7, y: Y0 - 50, align: 'center', color: TH.onSurfaceVar, kind: 'label' }); });
var lim = cav.line('s5b_lim', [X0, Y0 + Hh], [X0 + W, Y0 + Hh], { parent: S.cam, stroke: { color: '#ba1a1a', width: 4, dash: '4, 10' } });
UI.t('s5b_liml', 'limit', 30, { parent: S.cam, x: X0 + 10, y: Y0 + Hh + 14, color: '#ba1a1a', kind: 'label' });
var even = cav.line('s5b_even', [X0, Y0], [X0 + W, Y0 + Hh], { parent: S.cam, stroke: { color: '#7a8299', width: 5, dash: '18, 14', trim: true } });
H.trim(even, S.f0 + 4, S.beat(2), 0, 100, 'inOutCubic');
// usage: stays above even pace (over pace), climbs in steps (busy days), now = 45% of the week at 60%... actually 71%
var now = 0.42, pts = [];
for (var i = 0; i <= 42; i++) {
  var u = i / 42 * now;
  var v = 0.71 * Math.pow(u / now, 1.15) + (i > 0 && i < 42 ? 0.012 * Math.sin(i * 1.9) : 0);
  pts.push([X0 + W * u, Y0 + Hh * Math.max(v, u + 0.01)]);
}
var usage = cav.path('s5b_use', pts, { parent: S.cam, noFill: true, smooth: true, stroke: { color: TH.primary, width: 12, cap: 'round', trim: true } });
H.trim(usage, S.beat(1), S.beat(4), 0, 100, 'inOutCubic');
var nowDot = cav.circle('s5b_now', 16, { parent: S.cam, fill: TH.primary, x: X0 + W * now, y: Y0 + Hh * 0.71, in: S.beat(4) });
cav.pop(nowDot, S.beat(4), { dur: 12 });
// projection from now at the current rate hits the limit well before the reset
var slope = 0.71 * 1.15 / now; // the usage curve's own slope at now
var tHit = now + (1 - 0.71) / slope; // about 1d 1h later
var proj = cav.line('s5b_proj', [X0 + W * now, Y0 + Hh * 0.71], [X0 + W * tHit, Y0 + Hh], { parent: S.cam, stroke: { color: TH.primary, width: 7, dash: '12, 12', trim: true } });
H.trim(proj, S.beat(4), S.beat(6), 0, 100, 'outCubic');
var hit = cav.group('s5b_hit', { parent: S.cam, x: X0 + W * tHit, y: Y0 + Hh, in: S.beat(6) });
cav.circle('s5b_hitd', 18, { parent: hit, fill: '#ba1a1a' });
cav.rect('s5b_hitbg', 300, 72, { parent: hit, radius: 36, fill: '#ffdad6', y: 74 });
UI.t('s5b_hitt', 'Out in 1d 1h', 34, { parent: hit, y: 74, align: 'center', color: '#93000a', kind: 'label' });
cav.pop(hit, S.beat(6), { dur: 14 });
var h6 = H.head('s5b_t', 'See it coming, days ahead.', 76, { parent: S.g, y: 440, in: S.f0 + 4 });
cav.slideIn(h6, S.f0 + 4, { dy: -30, dur: 16 });
cav.key(S.cam, 'scale', [[S.f0, 1, 'inOutCubic'], [S.beat(4), 1.02, 'inOutCubic'], [S.f1, 1.22]]);
cav.key(S.cam, 'position', [[S.f0, [0, 0], 'inOutCubic'], [S.beat(4), [0, 0], 'inOutCubic'], [S.f1, [-(X0 + W * tHit) * 0.6, -(Y0 + Hh) * 0.4]]]);
// ================= s5c: running out anyway: Grok goes from wavy blue to red at 100% =================
S = H.shot('s5c');
UI.stage('s5c_stage', TH, { parent: S.g, f0: S.f0, f1: S.f1 }); cav.order(cav.find('s5c_stage'), S.cam);
var dg2 = JSON.parse(JSON.stringify(UI.DATA.grok)); dg2.chip = { text: 'Runs out before reset', over: true };
var gk = UI.card('s5c_grok', TH, dg2, { parent: S.cam });
var gg = cav.group('s5c_g', { parent: S.cam }); api.parent(gk.g, gg); cav.set(gk.g, { x: -1137 / 2, y: 200 });
cav.set(gg, { scale: 1.3, y: -60 });
UI.barSet(gk.weekly, S.f0, 88, undefined, 'inCubic'); UI.barSet(gk.weekly, S.beat(3), 100, undefined, 'linear');
cav.counter(gk.pct, S.f0, S.beat(3), 88, 100, { suffix: '%' });
UI.barColor(gk.weekly, S.beat(3), '#ba1a1a', 6);
cav.shake(gg, S.beat(3), 18, 16);
cav.flash(S.beat(3), { dur: 6, peak: 40, color: '#ffdad6' });
var lc = cav.group('s5c_lc', { parent: gg, x: 0, y: 0, in: S.beat(3) });
cav.rect('s5c_lcbg', 520, 72, { parent: lc, radius: 24, fill: '#ffdad6', x: -1137 / 2 + 48 + 260, y: 200 - 290 - 36 });
UI.t('s5c_lct', 'Limit reached · resets Mon', 36, { parent: lc, x: -1137 / 2 + 48 + 260, y: 200 - 290 - 36, align: 'center', color: '#93000a', kind: 'label' });
cav.set(gk.chip, { out: S.beat(3) - 1 });
cav.pop(lc, S.beat(3), { dur: 14 });
var h7 = H.head('s5c_t', 'Ran dry anyway?', 92, { parent: S.cam, y: 410, in: S.beat(3) });
cav.slideIn(h7, S.beat(3), { dy: -40, dur: 16 });
var dim = cav.rect('s5c_dim', 2000, 1160, { parent: S.g, fill: H.C.night, opacity: 0 });
cav.key(dim, 'opacity', [[S.beat(6), 0, 'inCubic'], [S.f1 - 4, 100]]);
cav.tween(S.cam, 'scale', S.f0, S.f1, 1, 1.06, 'linear');
cav.set(cav.find('s5c_grok_av_logo'), { out: S.beat(6) - 1 });
return 'B ok';
