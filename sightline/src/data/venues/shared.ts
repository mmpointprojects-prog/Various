import type { FloorSection, Setup } from '../../lib/types.ts';

// Building blocks shared by the venue files. Every arena here uses the same floor size
// (a hockey rink fits inside: ~92 x 208 ft), so stage and floor layouts can be reused.

export const STAGE_FRONT = -70; // z of the stage's front edge in end-stage setups

export const endSetup = (closed: string[], floor: FloorSection[], note: string): Setup => ({
  id: 'end', label: 'End stage', closed, floor, note,
  target: { x: 0, y: 17, z: STAGE_FRONT - 16 },
  front: { x: 0, y: 6, z: STAGE_FRONT },
});

export const hockeySetup = (note: string, priceScale = 1): Setup => ({
  id: 'hockey', label: 'Hockey', sport: 'hockey', closed: [], floor: [], note, priceScale,
  target: { x: 0, y: 2, z: 0 },
});

const COURT_FLOOR: FloorSection[] = [
  { id: 'CE', label: 'Courtside East', short: 'CS', x0: 27, x1: 33, z0: -36, z1: 36, front: 'x0', rows: 3, seated: true, courtside: true },
  { id: 'CW', label: 'Courtside West', short: 'CS', x0: -33, x1: -27, z0: -36, z1: 36, front: 'x1', rows: 3, seated: true, courtside: true },
  { id: 'BN', label: 'Baseline North', short: 'BL', x0: -22, x1: 22, z0: -60, z1: -52, front: 'z1', rows: 3, seated: true },
  { id: 'BS', label: 'Baseline South', short: 'BL', x0: -22, x1: 22, z0: 52, z1: 60, front: 'z0', rows: 3, seated: true },
];

export const basketballSetup = (note: string, names?: [string, string, string, string]): Setup => ({
  id: 'basketball', label: 'Basketball', sport: 'basketball', closed: [], note,
  floor: COURT_FLOOR.map((f, i) => ({ ...f, label: names ? names[i] : f.label })),
  target: { x: 0, y: 2, z: 0 },
});

const fsec = (n: number, x0: number, x1: number, z0: number, z1: number, rows: number): FloorSection =>
  ({ id: 'F' + n, label: 'Floor ' + n, short: 'FL ' + n, x0, x1, z0, z1, front: 'z0', rows });

const XS = [-38, -12.7, 12.7, 38];

/** Floor 1 across the front, 2-4 in the middle, 5-7 at the back (left to right facing the stage). */
export function floor7(): FloorSection[] {
  const out = [fsec(1, -38, 38, STAGE_FRONT + 4, STAGE_FRONT + 22, 8)];
  for (let c = 0; c < 3; c++) out.push(fsec(c + 2, XS[c], XS[c + 1], STAGE_FRONT + 22, 10, 20));
  for (let c = 0; c < 3; c++) out.push(fsec(c + 5, XS[c], XS[c + 1], 10, 62, 16));
  return out;
}

/** Floor 1-3 front, 4-6 middle, 7-9 back. */
export function floor9(rows: number): FloorSection[] {
  const zs = [STAGE_FRONT + 4, -16, 22, 62], out: FloorSection[] = [];
  for (let r = 0; r < 3; r++) for (let c = 0; c < 3; c++) out.push(fsec(r * 3 + c + 1, XS[c], XS[c + 1], zs[r], zs[r + 1], rows));
  return out;
}

/** Columns: 1-3 left, 4-7 center, 8-10 right, each front to back. */
export function floor10(): FloorSection[] {
  const out: FloorSection[] = [], zA = [STAGE_FRONT + 4, -23, 22, 62], zB = [STAGE_FRONT + 4, -34, -2, 30, 62];
  for (let i = 0; i < 3; i++) out.push(fsec(i + 1, XS[0], XS[1], zA[i], zA[i + 1], 14));
  for (let i = 0; i < 4; i++) out.push(fsec(i + 4, XS[1], XS[2], zB[i], zB[i + 1], 10));
  for (let i = 0; i < 3; i++) out.push(fsec(i + 8, XS[2], XS[3], zA[i], zA[i + 1], 14));
  return out;
}

/** Section ids from a to b inclusive, wrapping past `last` back to `first`. */
export function range(a: number, b: number, first: number, last: number, prefix = ''): string[] {
  const out: string[] = [];
  for (let n = a; ; n = n === last ? first : n + 1) { out.push(prefix + n); if (n === b) break; }
  return out;
}
