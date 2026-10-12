// Second pass: align each lockup (wordmark centred on the ring, consistent gap) and centre it on the frame.
var jobs = [['s2b', 's2b_lock', 's2b_ring', 's2b_t', 40], ['s9', 's9_lock', 's9_ring', 's9_t', 170]];
var out = {};
jobs.forEach(function (j) {
  var g = cav.find(j[1]), ring = cav.find(j[2]), wm = cav.find(j[3]); if (!g || !ring || !wm) return;
  var f = H.T.shots[j[0]][0] + 60; api.setFrame(f);
  var rb = cav.bbox(ring), wb = cav.bbox(wm), gp = api.get(g, 'position'), wp = api.get(wm, 'position');
  var rc = { x: rb.centre.x, y: rb.centre.y }, wc = { x: wb.centre.x, y: wb.centre.y };
  var gap = rb.width * 0.5;
  // move the wordmark (in the group's space; the group is unscaled) so its left edge sits gap after the ring and its centre is level
  var dxw = (rb.right + gap) - wb.left, dyw = rc.y - wc.y;
  cav.set(wm, { x: wp.x + dxw, y: wp.y + dyw });
  var left = rb.left, right = wb.right + dxw;
  var cx = (left + right) / 2;
  cav.set(g, { x: gp.x - cx, y: j[4] - (rc.y - gp.y) + 0 });
  out[j[1]] = { dxw: dxw, dyw: dyw, shift: -cx };
  // the s2b tagline lines up with the wordmark's left edge
  if (j[0] === 's2b') {
    var tg = cav.find('s2b_s');
    if (tg) {
      var tgg = cav.find('s2b_sg');
      var tb = cav.bbox(tg), tp = api.get(tgg, 'position'), cs = api.get(cav.find('s2b_cam'), 'scale');
      var k = cs && cs.x !== undefined ? cs.x : (cs || 1);
      var wmLeft = (rb.right + gap) - cx;
      cav.set(tgg, { x: tp.x + (wmLeft - tb.left) / k - 13, y: tp.y }); // -13: optical, the Y's ink starts inside its box
      out.tag = { dx: (wmLeft - tb.left) / k };
    }
  }
});
return out;
