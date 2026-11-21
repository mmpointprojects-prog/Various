import type { Venue } from '../../lib/types.ts';
import { endSetup, floor9, hockeySetup, range } from './shared.ts';

export const benchmark: Venue = {
  id: 'benchmark-international-arena', name: 'Benchmark International Arena', city: 'Tampa', state: 'FL', ceiling: 140,
  seatColors: [0x1c3f8f, 0x163478, 0x2149a0],
  levels: {
    lower: { key: 'lower', name: '100 Level', rx0: 46, rx1: 92, rz0: 104, rz1: 150, n: 3.4, rows: 21, y0: 4, rise: 1.45, count: 30, first: 101, centerNum: 123.5 },
    middle: { key: 'middle', name: '200 Level (Club)', rx0: 95, rx1: 107, rz0: 153, rz1: 165, n: 3.2, rows: 7, y0: 37, rise: 1.9, count: 30, first: 201, centerNum: 223.5 },
    upper: { key: 'upper', name: '300 Level', rx0: 112, rx1: 156, rz0: 170, rz1: 214, n: 3.0, rows: 20, y0: 52, rise: 2.3, count: 30, first: 301, centerNum: 323.5 },
  },
  setups: {
    end: endSetup([...range(121, 126, 101, 130), ...range(222, 225, 201, 230), ...range(321, 326, 301, 330)], floor9(13),
      'End stage: the stage is usually in front of sections 123–124, so 121–126, 222–225 and 321–326 are closed. 118 and 129 are the closest side sections.'),
    hockey: hockeySetup('Hockey (Lightning): the rink fills the floor. Rows A–B of the 100 Level are glass seats. 201 and 216 are the center-ice Club sections. The Lightning shoot twice toward 108–109.'),
  },
  defaults: {
    end: [{ L: 'A', sec: 'F2', row: 6 }, { L: 'B', sec: '108', row: 12 }, { L: 'C', sec: '118', row: 8 }, { L: 'D', sec: '308', row: 6 }],
    hockey: [{ L: 'A', sec: '116', row: 2 }, { L: 'B', sec: '101', row: 12 }, { L: 'C', sec: '108', row: 10 }, { L: 'D', sec: '316', row: 5 }],
  },
  modelNotes: 'Rows are lettered at this arena (A–U lower, A–T upper); the model numbers them 1–21 and 1–20. The 200 Level Club (201–230, 201 and 216 at center ice) is modeled with 7 rows (real sections run 4–10 rows, A–J). The Mark Club and lofts are not modeled. Concert floor layout is approximate.',
  sources: [
    { label: 'RateYourSeats: Section 205 (Club Level 201–219, center ice 201 and 216)', url: 'https://www.rateyourseats.com/benchmark-international-arena/seating/sections/205' },
    { label: 'RateYourSeats: Benchmark sections', url: 'https://www.rateyourseats.com/benchmark-international-arena/seating/sections' },
    { label: 'RateYourSeats: Promenade Level (stage at 123–124)', url: 'https://rateyourseats.com/benchmark-international-arena/seating/promenade-level' },
    { label: 'TickPick: Lightning seating chart', url: 'https://tpblog.tickpick.com/?p=63611' },
  ],
  events: [
    { id: 'bia-1119', date: '2026-11-19', time: '', title: 'Lightning vs. Oilers', type: 'NHL', setup: 'hockey', confirmed: true },
    { id: 'bia-1122', date: '2026-11-22', time: '', title: 'Lightning vs. Sabres', type: 'NHL', setup: 'hockey', confirmed: true },
    { id: 'bia-1125', date: '2026-11-25', time: '7:00 PM', title: 'Lightning vs. Hurricanes', type: 'NHL', setup: 'hockey', confirmed: true },
    { id: 'bia-1127', date: '2026-11-27', time: '3:00 PM', title: 'Lightning vs. Senators', type: 'NHL', setup: 'hockey', confirmed: true },
    { id: 'bia-1129', date: '2026-11-29', time: '', title: 'for KING & COUNTRY', type: 'Concert', setup: 'end', confirmed: true },
    { id: 'bia-1207', date: '2026-12-07', time: '', title: 'Lightning vs. Blue Jackets', type: 'NHL', setup: 'hockey', confirmed: true },
    { id: 'bia-1210', date: '2026-12-10', time: '', title: 'Lightning vs. Bruins', type: 'NHL', setup: 'hockey', confirmed: true },
    { id: 'bia-1212', date: '2026-12-12', time: '3:30 PM', title: 'Lightning vs. Penguins', type: 'NHL', setup: 'hockey', confirmed: true },
    { id: 'bia-1217', date: '2026-12-17', time: '', title: 'Lightning vs. Flames', type: 'NHL', setup: 'hockey', confirmed: true },
    { id: 'bia-1220a', date: '2026-12-20', time: '3:00 PM', title: 'Trans-Siberian Orchestra (matinee)', type: 'Concert', setup: 'end', confirmed: true },
    { id: 'bia-1220b', date: '2026-12-20', time: '7:30 PM', title: 'Trans-Siberian Orchestra', type: 'Concert', setup: 'end', confirmed: true },
    { id: 'bia-1222', date: '2026-12-22', time: '', title: 'Lightning vs. Blues', type: 'NHL', setup: 'hockey', confirmed: true },
    { id: 'bia-1229', date: '2026-12-29', time: '', title: 'Lightning vs. Predators', type: 'NHL', setup: 'hockey', confirmed: true },
    { id: 'bia-1231', date: '2026-12-31', time: '2:00 PM', title: 'Lightning vs. Canadiens', type: 'NHL', setup: 'hockey', confirmed: true },
  ],
};
