import { clamp, deg, rad, seatPos } from './geometry.ts';
import type { Section, SeatMetrics, Setup } from './types.ts';

// Sightline score and sample prices. Prices are modeled samples until a ticket data partner is connected.

export function metrics(sec: Section, row: number, setup: Setup): SeatMetrics {
  return setup.sport ? sportMetrics(sec, row, setup) : concertMetrics(sec, row, setup);
}

function concertMetrics(sec: Section, row: number, setup: Setup): SeatMetrics {
  const p = seatPos(sec, row);
  const F = setup.front ?? { x: 0, y: 6, z: -70 };
  const dx = p.x - F.x, dz = p.z - F.z, dy = p.y - F.y;
  const d = Math.hypot(dx, dy, dz);
  const ang = deg(Math.atan2(Math.abs(dx), dz)); // 0 = straight on, 90 = level with the stage front
  const dScore = clamp(1 - (d - 25) / 300, 0, 1) * 100;
  const aScore = clamp(1 - Math.max(0, ang - 20) / 90, 0, 1) * 100;
  const pen = sec.kind === 'floor' ? Math.min(18, (row - 1) * 1.2) : 0;
  const score = Math.round(clamp(0.6 * dScore + 0.4 * aScore - pen, 0, 100));
  const facing = Math.max(0, Math.cos(rad(Math.min(ang, 90))));
  let price = sec.kind === 'floor' ? 330 - d * 0.9 : sec.kind === 'lower' ? 110 + 150 * facing - row * 2.5 : sec.kind === 'middle' ? 85 + 100 * facing - row * 2 : 55 + 60 * facing - row * 1.5;
  price = Math.max(45, Math.round(price / 5) * 5);

  const notes: string[] = [];
  if (d < 70) notes.push('Close enough to see faces without the screens.');
  else if (d > 200) notes.push('Far from the stage. Expect to watch the video screens a lot.');
  if (ang > 55) notes.push('Sharp side angle. Speaker stacks and the stage edge cut into the view.');
  else if (ang < 20 && sec.kind !== 'floor') notes.push('Straight-on view of the stage and main screen.');
  if (sec.kind === 'floor') notes.push(row <= 3 ? 'Near the barricade. Nothing between you and the stage.' : 'Flat floor. You look over the heads in front of you.');
  if (sec.kind === 'middle') notes.push('Mid-level seat. Raised over the floor crowd without the upper-bowl climb.');
  if (sec.kind === 'upper') notes.push('Steep upper bowl. Good overview of the whole light show.');
  return { d, ang, eye: p.y, score, price, value: (score / price) * 100, notes: notes.slice(0, 3), sport: false };
}

function sportMetrics(sec: Section, row: number, setup: Setup): SeatMetrics {
  const p = seatPos(sec, row), hockey = setup.sport === 'hockey';
  const d = Math.hypot(p.x, p.y - 2, p.z);
  const ang = deg(Math.atan2(Math.abs(p.z), Math.abs(p.x) + 0.001)); // 0 = center sideline, 90 = behind the goal or basket
  const view = ang < 30 ? 'Sideline' : ang < 60 ? 'Corner' : 'End';
  const dScore = clamp(1 - (d - 20) / 260, 0, 1) * 100, aScore = 100 - ang * 0.45;
  const pen = hockey && sec.kind === 'lower' && row <= 3 ? 8 : 0;
  const score = Math.round(clamp(0.55 * dScore + 0.45 * aScore - pen, 0, 100));
  const side = Math.cos(rad(ang)), scale = setup.priceScale ?? 1;
  let price: number;
  if (sec.kind === 'floor') price = sec.courtside ? 3000 - (row - 1) * 700 : 1200 - row * 150;
  else if (sec.kind === 'lower') price = hockey ? 140 + 220 * side - row * 5 + (row <= 2 ? 60 : 0) : 150 + 300 * side - row * 7;
  else if (sec.kind === 'middle') price = (hockey ? 95 + 140 * side : 100 + 200 * side) - row * 4;
  else price = (hockey ? 55 + 70 * side : 60 + 90 * side) - row * 2;
  price = Math.max(scale < 1 ? 15 : 45, Math.round((price * scale) / 5) * 5);

  const notes: string[] = [];
  if (sec.kind === 'floor') notes.push(sec.courtside
    ? 'On the floor at courtside. Players right in front of you; the far side can be hidden behind them.'
    : 'Floor seats behind the baseline. Close to the basket, but depth on the far end is hard to judge.');
  if (hockey && sec.kind === 'lower' && row <= 3) notes.push('Against the glass. Great for speed and hits; the far end is hard to follow and the glass can distort.');
  if (sec.kind !== 'floor') {
    if (view === 'Sideline') notes.push(hockey ? 'Center-ice view. Both ends equally easy to follow.' : 'Sideline view. Both baskets equally easy to follow.');
    if (view === 'Corner') notes.push(hockey ? 'Corner view. Plays coming into your end look great; you see both zones at an angle.' : 'Corner view. Good angle on both baskets, a little foreshortened.');
    if (view === 'End') notes.push(hockey ? 'Behind the goal. Your end is great; the far end is a long way off.' : 'Behind the basket. The near end is great; the far end flattens out.');
  }
  if (sec.kind === 'middle') notes.push('Mid-level view: high enough to read plays, still close to the action.');
  if (sec.kind === 'upper') notes.push('High view of plays developing. Players look small; the scoreboard helps.');
  return { d, ang, view, eye: p.y, score, price, value: (score / price) * 100, notes: notes.slice(0, 3), sport: true };
}
