import type { BowlSection, FloorSection, Level, Levels, Section, Setup, Vec3 } from './types.ts';

export const rad = (d: number) => (d * Math.PI) / 180;
export const deg = (r: number) => (r * 180) / Math.PI;
export const clamp = (v: number, a: number, b: number) => Math.min(b, Math.max(a, v));
export const angDist = (a: number, b: number) => {
  const d = (((a - b) % 360) + 360) % 360;
  return d > 180 ? 360 - d : d;
};
/** Deterministic 0..1 noise, so crowds and seat colors look the same on every load. */
export const hash = (n: number) => {
  const s = Math.sin(n * 12.9898 + 78.233) * 43758.5453;
  return s - Math.floor(s);
};

const spow = (v: number, e: number) => Math.sign(v) * Math.pow(Math.abs(v), e);

function rawPt(L: Level, f: number, t: number): [number, number] {
  const a = L.rx0 + f * (L.rx1 - L.rx0), b = L.rz0 + f * (L.rz1 - L.rz0), e = 2 / L.n;
  return [a * spow(Math.sin(t), e), b * spow(Math.cos(t), e)];
}

// Section angles are spread by distance along the bowl (arc length) instead of raw curve angle,
// so end sections and sideline sections come out the same width, like a real arena.
const arcCache = new WeakMap<Level, { N: number; cum: number[]; total: number }>();
function warpT(L: Level, u: number): number {
  let arc = arcCache.get(L);
  if (!arc) {
    const N = 1440, cum = [0];
    let prev = rawPt(L, 0.5, 0);
    for (let i = 1; i <= N; i++) {
      const q = rawPt(L, 0.5, (i / N) * Math.PI * 2);
      cum.push(cum[i - 1] + Math.hypot(q[0] - prev[0], q[1] - prev[1]));
      prev = q;
    }
    arc = { N, cum, total: cum[N] };
    arcCache.set(L, arc);
  }
  const { N, cum, total } = arc, TAU = Math.PI * 2;
  const k = Math.floor(u / TAU), fr = ((u - k * TAU) / TAU) * total;
  let lo = 0, hi = N;
  while (hi - lo > 1) { const m = (lo + hi) >> 1; if (cum[m] <= fr) lo = m; else hi = m; }
  const seg = cum[hi] - cum[lo], w = seg > 0 ? (fr - cum[lo]) / seg : 0;
  return k * TAU + ((lo + w) / N) * TAU;
}

/** Point on a level: f = 0 front row edge .. 1 back row edge; t = section angle in radians. Returns [x, z]. */
export function ringPt(L: Level, f: number, t: number): [number, number] {
  return rawPt(L, f, warpT(L, t));
}

export const thetaOf = (L: Level, num: number) => 180 + (num - L.centerNum) * (360 / L.count);
export function numAt(L: Level, thetaDeg: number) {
  const v = (thetaDeg - 180) / (360 / L.count) + L.centerNum;
  return L.first + (((Math.round(v) - L.first) % L.count) + L.count) % L.count;
}

/** The venue's rings from the floor up: lower, middle (if any), upper. */
export const ringsOf = (levels: Levels): Level[] => (levels.middle ? [levels.lower, levels.middle, levels.upper] : [levels.lower, levels.upper]);
/** Section id for a number on a ring ('C' + 12 for prefixed club rings). */
export const secId = (L: Level, num: number) => (L.prefix ?? '') + num;
/** Height of a ring's top tread. */
export const topOf = (L: Level) => L.y0 + (L.rows - 1) * L.rise;

export function sectionsFor(levels: Levels, setup: Setup): Section[] {
  const out: Section[] = [];
  for (const L of ringsOf(levels)) {
    const step = 360 / L.count;
    for (let i = 0; i < L.count; i++) {
      const num = L.first + i, c = thetaOf(L, num), id = secId(L, num);
      out.push({ id, kind: L.key, level: L, c, a0: c - step / 2, a1: c + step / 2, blocked: setup.closed.includes(id) } satisfies BowlSection);
    }
  }
  for (const f of setup.floor) out.push({ ...f, kind: 'floor', blocked: false });
  return out;
}

export const rowsOf = (s: Section) => (s.kind === 'floor' ? s.rows : s.level.rows);
export const secTitle = (s: Section) => (s.kind === 'floor' ? s.label : 'Section ' + s.id);
export const secSub = (s: Section) =>
  s.kind === 'floor' ? (s.courtside ? 'Courtside floor seats' : s.seated ? 'Floor seats' : 'Floor') : s.level.name;

/** Eye position of a seat (head height for a standing fan, seated on floor seats at games). */
export function seatPos(sec: Section, row: number): Vec3 {
  if (sec.kind === 'floor') return floorSeat(sec, row);
  const L = sec.level, f = (row - 0.5) / L.rows, p = ringPt(L, f, rad(sec.c));
  return { x: p[0], z: p[1], y: L.y0 + (row - 1) * L.rise + 5.2 };
}

function floorSeat(sec: FloorSection, row: number): Vec3 {
  const f = (row - 0.5) / sec.rows, cx = (sec.x0 + sec.x1) / 2, cz = (sec.z0 + sec.z1) / 2;
  let fx: number, fz: number, bx: number, bz: number;
  if (sec.front === 'z0') { fx = bx = cx; fz = sec.z0; bz = sec.z1; }
  else if (sec.front === 'z1') { fx = bx = cx; fz = sec.z1; bz = sec.z0; }
  else if (sec.front === 'x0') { fz = bz = cz; fx = sec.x0; bx = sec.x1; }
  else { fz = bz = cz; fx = sec.x1; bx = sec.x0; }
  return { x: fx + (bx - fx) * f, z: fz + (bz - fz) * f, y: sec.seated ? 3.9 : 5.4 };
}
