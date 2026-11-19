import type { Venue } from '../../lib/types.ts';
import { basketballSetup, endSetup, floor7, hockeySetup, range } from './shared.ts';

export const kia: Venue = {
  id: 'kia-center', name: 'Kia Center', city: 'Orlando', state: 'FL', ceiling: 140,
  seatColors: [0x1f4fa8, 0x1a438f, 0x2459b8],
  levels: {
    lower: { key: 'lower', name: '100 Level', rx0: 46, rx1: 90, rz0: 104, rz1: 148, n: 3.2, rows: 20, y0: 4, rise: 1.4, count: 18, first: 101, centerNum: 101 },
    upper: { key: 'upper', name: '200 Level', rx0: 104, rx1: 142, rz0: 162, rz1: 200, n: 2.8, rows: 16, y0: 50, rise: 2.4, count: 32, first: 201, centerNum: 200 },
  },
  setups: {
    end: endSetup([...range(118, 102, 101, 118), ...range(230, 202, 201, 232)], floor7(),
      'End stage: the stage usually sits in front of section 101, so 118, 101, 102 and 230–202 are closed. Floor 1 runs across the front, Floors 2–7 behind it.'),
    basketball: basketballSetup('Basketball (Magic): every section is open. 105–106 and 114–115 are center court on the 100 Level, 207–208 and 223–224 on the 200 Level.',
      ['Floor East', 'Floor West', 'Floor North', 'Floor South']),
    hockey: hockeySetup('Hockey (Solar Bears, ECHL): the rink fills the floor. The first rows of the 100 Level sit against the glass.', 0.3),
  },
  defaults: {
    end: [{ L: 'A', sec: 'F3', row: 6 }, { L: 'B', sec: '110', row: 10 }, { L: 'C', sec: '105', row: 8 }, { L: 'D', sec: '216', row: 5 }],
    basketball: [{ L: 'A', sec: 'CE', row: 1 }, { L: 'B', sec: '106', row: 8 }, { L: 'C', sec: '110', row: 10 }, { L: 'D', sec: '208', row: 4 }],
    hockey: [{ L: 'A', sec: '114', row: 2 }, { L: 'B', sec: '105', row: 12 }, { L: 'C', sec: '110', row: 10 }, { L: 'D', sec: '224', row: 4 }],
  },
  modelNotes: 'Lettered loge boxes, club seats (CA–CF) and the small 109A–111A sections are not modeled. Concert floor layout is approximate.',
  sources: [
    { label: 'RateYourSeats: Kia Center sections', url: 'https://www.rateyourseats.com/kia-center/seating/sections' },
    { label: 'RateYourSeats: Section 102 (stage in front of 101)', url: 'https://rateyourseats.com/kia-center/seating/sections/102' },
    { label: 'Kia Center: Solar Bears', url: 'https://www.kiacenter.com/events/orlando-solar-bears' },
  ],
  events: [
    { id: 'kia-1121', date: '2026-11-21', time: '7:00 PM', title: 'Solar Bears vs. Icemen', type: 'ECHL', setup: 'hockey', confirmed: true },
    { id: 'kia-1124', date: '2026-11-24', time: '7:00 PM', title: 'Magic vs. Pistons', type: 'NBA', setup: 'basketball', confirmed: true },
    { id: 'kia-1127', date: '2026-11-27', time: '', title: 'Solar Bears vs. Stingrays', type: 'ECHL', setup: 'hockey', confirmed: true },
    { id: 'kia-1205', date: '2026-12-05', time: '7:00 PM', title: 'Solar Bears vs. Gladiators', type: 'ECHL', setup: 'hockey', confirmed: true },
    { id: 'kia-1214', date: '2026-12-14', time: '7:00 PM', title: 'Magic vs. 76ers', type: 'NBA', setup: 'basketball', confirmed: true },
    { id: 'kia-1215', date: '2026-12-15', time: '7:00 PM', title: 'Solar Bears vs. Icemen', type: 'ECHL', setup: 'hockey', confirmed: true },
    { id: 'kia-1216', date: '2026-12-16', time: '7:00 PM', title: 'Magic vs. Cavaliers', type: 'NBA', setup: 'basketball', confirmed: true },
    { id: 'kia-1219a', date: '2026-12-19', time: '2:30 PM', title: 'Trans-Siberian Orchestra (matinee)', type: 'Concert', setup: 'end', confirmed: true },
    { id: 'kia-1219b', date: '2026-12-19', time: '7:30 PM', title: 'Trans-Siberian Orchestra', type: 'Concert', setup: 'end', confirmed: true },
    { id: 'kia-1221', date: '2026-12-21', time: '7:00 PM', title: 'Magic vs. Kings', type: 'NBA', setup: 'basketball', confirmed: true },
    { id: 'kia-1223', date: '2026-12-23', time: '7:00 PM', title: 'Magic vs. Hornets', type: 'NBA', setup: 'basketball', confirmed: true },
    { id: 'kia-1226', date: '2026-12-26', time: '7:00 PM', title: 'Magic vs. Raptors', type: 'NBA', setup: 'basketball', confirmed: true },
    { id: 'kia-1227', date: '2026-12-27', time: '6:00 PM', title: 'Magic vs. Bucks', type: 'NBA', setup: 'basketball', confirmed: true },
    { id: 'kia-1229', date: '2026-12-29', time: '', title: 'Solar Bears vs. Ghost Pirates', type: 'ECHL', setup: 'hockey', confirmed: false },
    { id: 'kia-1231', date: '2026-12-31', time: '', title: 'Jim Gaffigan', type: 'Comedy', setup: 'end', confirmed: true },
  ],
};
