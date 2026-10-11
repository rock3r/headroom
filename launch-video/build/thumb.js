// YouTube thumbnail, built at frame 0 of a cleared scene (1920 x 1080, exported to 1280 x 720).
cav.clear();
var TH = UI.TH.cobalt, F = 0;
cav.rect('th_bg', 2000, 1160, { fill: H.C.icon });
// big quiet rings, as in the outro
for (var rr = 0; rr < 3; rr++) {
  var bg = H.ring('th_bgr' + rr, 430 + rr * 270, 75, { x: 520, y: -60, color: '#4a66c2', trackColor: '#3d5bb9', width: 22 });
  cav.set(bg.group, { rotation: rr * 40 });
}
// lockup, top left
var lock = cav.group('th_lock', { x: -860, y: 340 });
H.ring('th_ring', 62, 75, { parent: lock, x: 62, color: '#ffffff', trackColor: '#7b8fd0', width: 24 });
cav.circle('th_dot', 19, { parent: lock, x: 62, fill: '#ffffff' });
H.head('th_wm', 'Headroom', 116, { parent: lock, x: 168, y: 0, align: 'left', color: '#ffffff' });
// the hook
H.head('th_h1', 'Your AI limits,', 124, { x: -872, y: 90, align: 'left', color: '#ffffff' });
H.head('th_h2', 'at a glance.', 124, { x: -872, y: -50, align: 'left', color: '#bcd0ff' });
H.label('th_sub', 'Free and open source · Android 16+', 50, { x: -868, y: -230, align: 'left', color: '#dde3ff' });
// floating cards, right
var cards = cav.group('th_cards', { x: 590, y: -60, rotation: -4 });
function card(name, d, x, y, s) {
  var g = cav.group(name + '_g', { parent: cards, x: x, y: y, scale: s });
  var sh = cav.rect(name + '_sh', 1137, d.session ? 478 : 400, { parent: g, radius: 90, fill: '#000000', opacity: 28, y: -24 });
  cav.blur(sh, 50);
  var c = UI.card(name, TH, d, { parent: g, x: -1137 / 2, y: (d.session ? 478 : 400) / 2 });
  return c;
}
var dg = JSON.parse(JSON.stringify(UI.DATA.grok));
card('th_c2', UI.DATA.codex, 70, -300, 0.5);
card('th_c1', UI.DATA.claude, 0, 30, 0.56);
// the reset pill, like the island
var pill = cav.group('th_pill', { x: 610, y: 370, rotation: 3 });
cav.rect('th_pillbg', 492, 108, { parent: pill, radius: 54, fill: '#000000' });
var av = cav.group('th_pav', { parent: pill, x: -183 });
cav.path('th_pavc', H.cookiePts(33, 10, 0.08), { parent: av, closed: true, smooth: true, fill: H.P.grok[1] });
H.logo('th_pavl', 'grok', 30, { parent: av, color: '#1a1a1a' });
H.ring('th_pring', 23, 100, { parent: pill, x: 79, width: 7.5, color: H.C.green, trackColor: '#3a3a3a' });
UI.t('th_pw', 'reset', 36, { parent: pill, x: 216, y: 0, align: 'right', color: '#ffffff', kind: 'label' });
cav.set(pill, { scale: 1.1 });
return 'thumb ok';
