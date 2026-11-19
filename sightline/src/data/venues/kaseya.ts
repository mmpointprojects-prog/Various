import type { Venue } from '../../lib/types.ts';
import { basketballSetup, endSetup, floor7, range } from './shared.ts';

export const kaseya: Venue = {
  id: 'kaseya-center', name: 'Kaseya Center', city: 'Miami', state: 'FL', ceiling: 150,
  seatColors: [0xa51d2d, 0x8f1826, 0xb3263a],
  levels: {
    lower: { key: 'lower', name: '100 Level', rx0: 46, rx1: 112, rz0: 104, rz1: 166, n: 3.2, rows: 30, y0: 4, rise: 1.3, count: 22, first: 101, centerNum: 101 },
    upper: { key: 'upper', name: '300 Level', rx0: 124, rx1: 176, rz0: 180, rz1: 230, n: 2.8, rows: 23, y0: 60, rise: 2.3, count: 32, first: 301, centerNum: 300.5 },
  },
  setups: {
    end: endSetup([...range(122, 102, 101, 122), ...range(330, 303, 301, 332)], floor7(),
      'End stage: 122, 101 and 102 and the 300 Level behind the stage are usually closed. Floor 1 runs across the front, Floors 2–7 behind it.'),
    basketball: basketballSetup('Basketball (Heat): every section is open. Courtside seats line both sidelines; 308–309 and 324–325 are the 300 Level center-court sections.'),
  },
  defaults: {
    end: [{ L: 'A', sec: 'F3', row: 6 }, { L: 'B', sec: '112', row: 15 }, { L: 'C', sec: '106', row: 10 }, { L: 'D', sec: '316', row: 6 }],
    basketball: [{ L: 'A', sec: 'CE', row: 1 }, { L: 'B', sec: '117', row: 10 }, { L: 'C', sec: '112', row: 12 }, { L: 'D', sec: '324', row: 5 }],
  },
  modelNotes: 'Lower-level range 101–122 is inferred (101 behind the stage, 112 head-on). The small 400 Level and 200 Level suites are not modeled. Concert floor layout is approximate.',
  sources: [
    { label: 'RateYourSeats: Kaseya Center Heat seating', url: 'https://www.rateyourseats.com/ftx-arena/seating/seating-chart/heat' },
    { label: 'RateYourSeats: Section 101 (concerts)', url: 'https://www.rateyourseats.com/sections/americanairlines-arena-concert/101' },
    { label: 'Kaseya Center concerts', url: 'https://www.kaseyacenter.com/events/category/concerts' },
  ],
  events: [
    { id: 'kc-1120', date: '2026-11-20', time: '', title: 'John Summit: CTRL Escape Tour', type: 'Concert', setup: 'end', confirmed: true },
    { id: 'kc-1121', date: '2026-11-21', time: '', title: 'John Summit: CTRL Escape Tour', type: 'Concert', setup: 'end', confirmed: true },
    { id: 'kc-1122', date: '2026-11-22', time: '6:00 PM', title: 'Heat vs. Thunder', type: 'NBA', setup: 'basketball', confirmed: true },
    { id: 'kc-1201', date: '2026-12-01', time: '', title: 'Heat vs. Hawks', type: 'NBA', setup: 'basketball', confirmed: true },
    { id: 'kc-1203', date: '2026-12-03', time: '', title: 'Heat vs. Wizards', type: 'NBA', setup: 'basketball', confirmed: true },
    { id: 'kc-1204', date: '2026-12-04', time: '', title: 'Rawayana', type: 'Concert', setup: 'end', confirmed: false },
    { id: 'kc-1205', date: '2026-12-05', time: '', title: 'Rawayana', type: 'Concert', setup: 'end', confirmed: false },
    { id: 'kc-1206', date: '2026-12-06', time: '', title: 'Rawayana', type: 'Concert', setup: 'end', confirmed: false },
    { id: 'kc-1211', date: '2026-12-11', time: '', title: 'Gente de Zona: Isla Viva', type: 'Concert', setup: 'end', confirmed: false },
    { id: 'kc-1212', date: '2026-12-12', time: '8:30 PM', title: 'Heat vs. Knicks', type: 'NBA', setup: 'basketball', confirmed: true },
    { id: 'kc-1218', date: '2026-12-18', time: '', title: 'Heat vs. Clippers', type: 'NBA', setup: 'basketball', confirmed: true },
    { id: 'kc-1219', date: '2026-12-19', time: '', title: 'Ryan Castro: Los Chorros Con Richy Tour', type: 'Concert', setup: 'end', confirmed: false },
    { id: 'kc-1220', date: '2026-12-20', time: '', title: 'Heat vs. Kings', type: 'NBA', setup: 'basketball', confirmed: true },
    { id: 'kc-1221', date: '2026-12-21', time: '', title: 'Heat vs. Bulls', type: 'NBA', setup: 'basketball', confirmed: false },
    { id: 'kc-1223', date: '2026-12-23', time: '', title: 'Heat vs. Bulls', type: 'NBA', setup: 'basketball', confirmed: false },
    { id: 'kc-1226', date: '2026-12-26', time: '8:00 PM', title: 'Bebeshito: El Comboy Tour', type: 'Concert', setup: 'end', confirmed: true },
    { id: 'kc-1227', date: '2026-12-27', time: '', title: 'Heat vs. Hornets', type: 'NBA', setup: 'basketball', confirmed: true },
  ],
};
