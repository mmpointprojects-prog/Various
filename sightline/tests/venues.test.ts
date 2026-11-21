import { test } from 'node:test';
import assert from 'node:assert/strict';
import { VENUES } from '../src/data/venues/index.ts';
import { rad, ringPt, rowsOf, sectionsFor, seatPos } from '../src/lib/geometry.ts';
import { metrics } from '../src/lib/scoring.ts';
import type { SetupId } from '../src/lib/types.ts';

test('venues have unique ids and U.S. state codes', () => {
  assert.ok(VENUES.length >= 6);
  assert.equal(new Set(VENUES.map((v) => v.id)).size, VENUES.length);
  for (const v of VENUES) assert.match(v.state, /^[A-Z]{2}$/);
});

for (const v of VENUES) {
  test(`${v.name}: setups, closed sections and default seats are consistent`, () => {
    for (const [id, setup] of Object.entries(v.setups)) {
      assert.equal(setup!.id, id);
      const secs = sectionsFor(v.levels, setup!);
      const ids = new Set(secs.map((s) => s.id));
      assert.equal(ids.size, secs.length, 'section ids are unique');
      for (const c of setup!.closed) assert.ok(ids.has(c), `closed section ${c} exists`);
      const defaults = v.defaults[id as SetupId] ?? [];
      assert.equal(defaults.length, 4, 'four default seats');
      for (const d of defaults) {
        const s = secs.find((x) => x.id === d.sec);
        assert.ok(s, `default section ${d.sec} exists`);
        assert.ok(!s!.blocked, `default section ${d.sec} is open`);
        assert.ok(d.row >= 1 && d.row <= rowsOf(s!), `default row ${d.row} in range`);
      }
    }
  });

  test(`${v.name}: events are well formed and point at a real setup`, () => {
    const ids = new Set<string>();
    for (const e of v.events) {
      assert.match(e.date, /^\d{4}-\d{2}-\d{2}$/);
      assert.ok(v.setups[e.setup], `${e.id} setup ${e.setup} exists`);
      assert.ok(!ids.has(e.id), `${e.id} unique`); ids.add(e.id);
    }
  });

  test(`${v.name}: floor sections sit inside the bowl`, () => {
    const L = v.levels.lower, e = L.n;
    for (const setup of Object.values(v.setups)) for (const f of setup!.floor) {
      for (const [x, z] of [[f.x0, f.z0], [f.x1, f.z0], [f.x0, f.z1], [f.x1, f.z1]]) {
        const r = Math.pow(Math.abs(x) / L.rx0, e) + Math.pow(Math.abs(z) / L.rz0, e);
        assert.ok(r <= 1, `${f.id} corner (${x}, ${z}) inside the floor (${r.toFixed(2)})`);
      }
    }
  });

  test(`${v.name}: sections are evenly spaced around the bowl`, () => {
    const L = v.levels.lower, step = 360 / L.count, widths: number[] = [];
    for (let i = 0; i < L.count; i++) {
      const a = ringPt(L, 0.5, rad(i * step)), b = ringPt(L, 0.5, rad((i + 1) * step));
      widths.push(Math.hypot(b[0] - a[0], b[1] - a[1]));
    }
    const min = Math.min(...widths), max = Math.max(...widths);
    assert.ok(max / min < 1.1, `section widths vary ${min.toFixed(1)}–${max.toFixed(1)} ft`);
  });

  test(`${v.name}: scores stay in range and the head-on lower seat beats the far upper seat`, () => {
    for (const setup of Object.values(v.setups)) {
      for (const s of sectionsFor(v.levels, setup!)) {
        const m = metrics(s, 1, setup!);
        assert.ok(m.score >= 0 && m.score <= 100);
        assert.ok(m.price > 0);
        assert.ok(Number.isFinite(seatPos(s, 1).y));
      }
    }
    const end = v.setups.end!;
    const secs = sectionsFor(v.levels, end);
    const best = (kind: 'lower' | 'upper') => secs.filter((s) => s.kind === kind && !s.blocked)
      .map((s) => metrics(s, 5, end)).sort((a, b) => a.ang - b.ang)[0];
    assert.ok(best('lower').score > best('upper').score);
  });
}
