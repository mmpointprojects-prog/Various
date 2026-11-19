import type { Venue } from '../../lib/types.ts';
import { endSetup, floor9, hockeySetup, range } from './shared.ts';

export const vystar: Venue = {
  id: 'vystar-veterans-memorial-arena', name: 'VyStar Veterans Memorial Arena', city: 'Jacksonville', state: 'FL', ceiling: 120,
  seatColors: [0x23325c, 0x1c2a4e, 0x2b3c6c],
  levels: {
    lower: { key: 'lower', name: '100 Level', rx0: 46, rx1: 98, rz0: 104, rz1: 156, n: 3.0, rows: 25, y0: 4, rise: 1.35, count: 22, first: 100, centerNum: 119.5 },
    upper: { key: 'upper', name: '300 Level', rx0: 112, rx1: 140, rz0: 170, rz1: 198, n: 2.8, rows: 13, y0: 46, rise: 2.6, count: 25, first: 300, centerNum: 321 },
  },
  setups: {
    end: endSetup([...range(118, 121, 100, 121), ...range(319, 323, 300, 324)], floor9(14),
      'End stage: 118–121 and 319–323 sit behind the stage. Floor 1–9 has rows 1–14. 102–104 and 113–115 are the side picks.'),
    hockey: hockeySetup('Hockey (Icemen, ECHL): the rink fills the floor. The first rows of the 100 Level sit against the glass.', 0.3),
  },
  defaults: {
    end: [{ L: 'A', sec: 'F2', row: 5 }, { L: 'B', sec: '108', row: 12 }, { L: 'C', sec: '103', row: 8 }, { L: 'D', sec: '308', row: 4 }],
    hockey: [{ L: 'A', sec: '114', row: 2 }, { L: 'B', sec: '103', row: 14 }, { L: 'C', sec: '108', row: 10 }, { L: 'D', sec: '309', row: 3 }],
  },
  modelNotes: 'Rows are lettered at this arena (A–Y lower, A–M upper); the model numbers them. Stage end is inferred from section 121 being behind the stage.',
  sources: [
    { label: 'RateYourSeats: VyStar sections', url: 'https://www.rateyourseats.com/vystar-veterans-memorial-arena/seating/sections' },
    { label: 'RateYourSeats: Section 121 (behind the stage)', url: 'https://www.rateyourseats.com/vystar-veterans-memorial-arena/seating/sections/121' },
    { label: 'ECHL: Jacksonville Icemen', url: 'https://echl.com/teams/jacksonville-icemen' },
  ],
  events: [
    { id: 'vva-1120', date: '2026-11-20', time: '7:00 PM', title: 'Icemen vs. Solar Bears (Cars Night)', type: 'ECHL', setup: 'hockey', confirmed: true },
    { id: 'vva-1204', date: '2026-12-04', time: '7:00 PM', title: 'Icemen vs. Solar Bears', type: 'ECHL', setup: 'hockey', confirmed: true },
    { id: 'vva-1211', date: '2026-12-11', time: '7:00 PM', title: 'Icemen vs. Everblades', type: 'ECHL', setup: 'hockey', confirmed: true },
    { id: 'vva-1212', date: '2026-12-12', time: '7:00 PM', title: 'Icemen vs. Everblades', type: 'ECHL', setup: 'hockey', confirmed: true },
    { id: 'vva-1217', date: '2026-12-17', time: '7:00 PM', title: 'Trans-Siberian Orchestra', type: 'Concert', setup: 'end', confirmed: true },
    { id: 'vva-1230', date: '2026-12-30', time: '7:00 PM', title: 'Icemen vs. Stingrays', type: 'ECHL', setup: 'hockey', confirmed: true },
    { id: 'vva-1231', date: '2026-12-31', time: '5:00 PM', title: 'Icemen vs. Solar Bears (New Year’s Eve)', type: 'ECHL', setup: 'hockey', confirmed: true },
  ],
};
