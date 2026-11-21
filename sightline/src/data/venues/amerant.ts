import type { Venue } from '../../lib/types.ts';
import { endSetup, floor10, hockeySetup, range } from './shared.ts';

export const amerant: Venue = {
  id: 'amerant-bank-arena', name: 'Amerant Bank Arena', city: 'Sunrise', state: 'FL', ceiling: 140,
  seatColors: [0xa31f34, 0x8c1a2c, 0x1d2a4a],
  levels: {
    lower: { key: 'lower', name: '100 Level', rx0: 46, rx1: 100, rz0: 104, rz1: 158, n: 3.4, rows: 26, y0: 4, rise: 1.35, count: 34, first: 101, centerNum: 123 },
    middle: { key: 'middle', name: 'Club Level', rx0: 102, rx1: 112, rz0: 160, rz1: 170, n: 3.2, rows: 5, y0: 40, rise: 1.8, count: 35, first: 1, centerNum: 27, prefix: 'C' },
    upper: { key: 'upper', name: '300 Level', rx0: 118, rx1: 154, rz0: 176, rz1: 212, n: 3.0, rows: 16, y0: 54, rise: 2.4, count: 34, first: 301, centerNum: 323 },
  },
  setups: {
    end: endSetup([...range(121, 125, 101, 134), ...range(26, 28, 1, 35, 'C'), ...range(321, 325, 301, 334)], floor10(),
      'End stage: the stage usually sits near sections 122–124, so 121–125, C26–C28 and 321–325 are closed. 119–120 are good side seats; C1–C19 face the stage.'),
    hockey: hockeySetup('Hockey (Panthers): the rink fills the floor. The first rows of the 100 Level sit against the glass. C1, C35 and C17–C19 are the center-ice Club sections.'),
  },
  defaults: {
    end: [{ L: 'A', sec: 'F4', row: 6 }, { L: 'B', sec: '106', row: 12 }, { L: 'C', sec: '117', row: 8 }, { L: 'D', sec: '306', row: 5 }],
    hockey: [{ L: 'A', sec: '115', row: 2 }, { L: 'B', sec: '131', row: 12 }, { L: 'C', sec: '106', row: 10 }, { L: 'D', sec: '314', row: 4 }],
  },
  modelNotes: 'The Club Level (C1–C35) is modeled with 5 rows everywhere (real sections run 3 rows on the sides, up to 8 at the ends). Amerant Vault seats are not modeled. Floor 1–10 layout is a guess from "Floor 4 and 7 are center" in public guides.',
  sources: [
    { label: 'RateYourSeats: Club Seats (C1–C35)', url: 'https://www.rateyourseats.com/fla-live-arena/seating/club-seats' },
    { label: 'RateYourSeats: Amerant sections', url: 'https://www.rateyourseats.com/amerant-bank-arena/seating/sections' },
    { label: 'RateYourSeats: Section 124 (behind the stage)', url: 'https://rateyourseats.com/amerant-bank-arena/seating/sections/124' },
    { label: 'NHL.com: Panthers 2026-27 schedule', url: 'https://www.nhl.com/panthers/news/florida-panthers-announce-2026-27-regular-season-schedule' },
  ],
  events: [
    { id: 'aba-1121', date: '2026-11-21', time: '6:00 PM', title: 'Panthers vs. Sabres', type: 'NHL', setup: 'hockey', confirmed: true },
    { id: 'aba-1125', date: '2026-11-25', time: '7:00 PM', title: 'Panthers vs. Senators', type: 'NHL', setup: 'hockey', confirmed: true },
    { id: 'aba-1128', date: '2026-11-28', time: '6:00 PM', title: 'Panthers vs. Lightning', type: 'NHL', setup: 'hockey', confirmed: false },
    { id: 'aba-1218', date: '2026-12-18', time: '7:00 PM', title: 'Trans-Siberian Orchestra', type: 'Concert', setup: 'end', confirmed: false },
    { id: 'aba-1219', date: '2026-12-19', time: '1:00 PM', title: 'Panthers vs. Flames', type: 'NHL', setup: 'hockey', confirmed: false },
    { id: 'aba-1221', date: '2026-12-21', time: '', title: 'Panthers vs. Blues', type: 'NHL', setup: 'hockey', confirmed: true },
    { id: 'aba-1226', date: '2026-12-26', time: '', title: 'Panthers vs. Lightning', type: 'NHL', setup: 'hockey', confirmed: true },
    { id: 'aba-1228', date: '2026-12-28', time: '', title: 'Panthers vs. Predators', type: 'NHL', setup: 'hockey', confirmed: true },
    { id: 'aba-1230', date: '2026-12-30', time: '', title: 'Panthers vs. Sabres', type: 'NHL', setup: 'hockey', confirmed: true },
  ],
};
