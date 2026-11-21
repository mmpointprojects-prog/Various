import type { Venue } from '../../lib/types.ts';
import { basketballSetup, endSetup, floor7, range } from './shared.ts';

// First venue outside Florida: the sample for the U.S. rollout.
// Lower bowl: 101–124. Sidelines 101–106 and 113–118 (Sideline Club), ends 107–112 and 119–124.
// End-stage concerts face 107–112, so the stage sits at the 119–124 end (north in the model).
// Upper bowl: 201–225. 225 is behind or beside the stage for most shows.
export const chase: Venue = {
  id: 'chase-center', name: 'Chase Center', city: 'San Francisco', state: 'CA', ceiling: 145,
  seatColors: [0x2b3442, 0x232b37, 0x323c4c],
  levels: {
    lower: { key: 'lower', name: '100 Level', rx0: 46, rx1: 96, rz0: 104, rz1: 152, n: 3.2, rows: 24, y0: 4, rise: 1.45, count: 24, first: 101, centerNum: 121.5 },
    upper: { key: 'upper', name: '200 Level', rx0: 110, rx1: 148, rz0: 168, rz1: 206, n: 2.8, rows: 18, y0: 58, rise: 2.4, count: 25, first: 201, centerNum: 200.5 },
  },
  setups: {
    end: endSetup([...range(120, 123, 101, 124), ...range(224, 201, 201, 225)], floor7(),
      'End stage: the stage sits at the 119–124 end, so 120–123 and 224, 225 and 201 are closed. 107–112 face the stage head-on. Floor 1 runs across the front, Floors 2–7 behind it.'),
    basketball: basketballSetup('Basketball (Warriors): every section is open. 103–104 and 115–116 are center court on the 100 Level (Sideline Club), 206 and 220 on the 200 Level. 109–110 and 121–122 sit behind the baskets.',
      ['Courtside East', 'Courtside West', 'Baseline North', 'Baseline South']),
  },
  defaults: {
    end: [{ L: 'A', sec: 'F3', row: 6 }, { L: 'B', sec: '110', row: 10 }, { L: 'C', sec: '116', row: 8 }, { L: 'D', sec: '210', row: 3 }],
    basketball: [{ L: 'A', sec: 'CE', row: 1 }, { L: 'B', sec: '104', row: 8 }, { L: 'C', sec: '120', row: 10 }, { L: 'D', sec: '206', row: 4 }],
  },
  modelNotes: 'Courtside club sections (2–17, C1–C32), the suite ring and the sideline theater boxes are not modeled. Real row counts vary (ends about 27 rows, Sideline Club about 16–20 above the courtside rows; 200 Level sidelines start at row 6, 225 has 9 rows); the model uses 24 and 18 rows everywhere. Concert floor layout is approximate.',
  sources: [
    { label: 'RateYourSeats: Section 109 (end-stage head-on, 107–112)', url: 'https://www.rateyourseats.com/chase-center/seating/sections/109' },
    { label: 'RateYourSeats: Section 102 (Sideline Club 101–106, 113–118)', url: 'https://www.rateyourseats.com/chase-center/seating/sections/102' },
    { label: 'RateYourSeats: Section 225 (behind/side stage)', url: 'https://rateyourseats.com/chase-center/seating/sections/225' },
    { label: 'TickPick: Chase Center seating chart', url: 'https://www.tickpick.com/blog/golden-state-warriors-seating-chart-with-seat-views' },
    { label: 'Warriors: November 2026 fan guide', url: 'https://www.nba.com/warriors/news/warriors-fan-guide-november-2026' },
    { label: 'Warriors: Holiday Hoops mini plans', url: 'https://www.nba.com/warriors/news/warriors-announce-holiday-hoops-mini-plans-20261110' },
  ],
  events: [
    { id: 'chase-1122', date: '2026-11-22', time: '5:30 PM', title: 'Warriors vs. Celtics', type: 'NBA', setup: 'basketball', confirmed: true },
    { id: 'chase-1125', date: '2026-11-25', time: '7:00 PM', title: 'Warriors vs. Kings (NBA Cup)', type: 'NBA', setup: 'basketball', confirmed: true },
    { id: 'chase-1128', date: '2026-11-28', time: '7:30 PM', title: 'Journey: Final Frontier Tour', type: 'Concert', setup: 'end', confirmed: true },
    { id: 'chase-1129', date: '2026-11-29', time: '5:30 PM', title: 'Warriors vs. Clippers', type: 'NBA', setup: 'basketball', confirmed: true },
    { id: 'chase-1201', date: '2026-12-01', time: '8:00 PM', title: 'Warriors vs. Jazz', type: 'NBA', setup: 'basketball', confirmed: true },
    { id: 'chase-1225', date: '2026-12-25', time: '', title: 'Warriors vs. Nuggets', type: 'NBA', setup: 'basketball', confirmed: true },
    { id: 'chase-1227', date: '2026-12-27', time: '5:30 PM', title: 'Warriors vs. Timberwolves', type: 'NBA', setup: 'basketball', confirmed: false },
    { id: 'chase-1230', date: '2026-12-30', time: '', title: 'Warriors vs. Rockets', type: 'NBA', setup: 'basketball', confirmed: false },
  ],
};
