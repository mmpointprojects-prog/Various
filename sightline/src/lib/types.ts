// Shared types for venues, setups, sections and seats.
// Units are feet. The stage end of every venue is north (-z); +x is east.

export type SetupId = 'end' | 'basketball' | 'hockey';
export type Sport = 'basketball' | 'hockey';
export type LevelKey = 'lower' | 'upper';
export type PickLetter = 'A' | 'B' | 'C' | 'D';

export interface Vec3 { x: number; y: number; z: number }

/** One seating ring (a level), drawn as a superellipse that widens row by row. */
export interface Level {
  key: LevelKey;
  name: string;            // "100 Level"
  rx0: number; rx1: number; // half-width at the front row and at the back row
  rz0: number; rz1: number; // half-length at the front row and at the back row
  n: number;               // superellipse exponent (2 = oval, higher = squarer)
  rows: number;
  y0: number;              // height of row 1's tread
  rise: number;            // height gained per row
  count: number;           // sections in the ring
  first: number;           // lowest section number
  centerNum: number;       // section number centered on the stage end (may be fractional)
}

export interface FloorSection {
  id: string;
  label: string;           // "Floor 3"
  short: string;           // "FL 3", shown on the map
  x0: number; x1: number; z0: number; z1: number;
  front: 'x0' | 'x1' | 'z0' | 'z1'; // which edge faces the stage or court
  rows: number;
  seated?: boolean;
  courtside?: boolean;
}

export interface Setup {
  id: SetupId;
  label: string;           // "End stage"
  sport?: Sport;
  closed: string[];        // section ids not sold in this setup
  target: Vec3;            // where the camera looks by default
  front?: Vec3;            // end stage: front-center of the stage
  floor: FloorSection[];
  note: string;
  priceScale?: number;     // 1 = NHL/NBA-level sample prices
}

export interface VenueEvent {
  id: string;
  date: string;            // YYYY-MM-DD
  time: string;            // "7:00 PM" or ""
  title: string;
  type: string;            // "NBA", "NHL", "ECHL", "Concert", "Comedy"
  setup: SetupId;
  confirmed: boolean;      // false = only one public source lists it
}

export interface DefaultPick { L: PickLetter; sec: string; row: number }

export interface Venue {
  id: string;
  name: string;
  city: string;
  state: string;
  ceiling: number;
  seatColors: number[];    // approximate seat colors, used by the enhanced view
  levels: { lower: Level; upper: Level };
  setups: Partial<Record<SetupId, Setup>>;
  defaults: Partial<Record<SetupId, DefaultPick[]>>;
  events: VenueEvent[];
  modelNotes: string;      // what is estimated or missing in this model
  sources: { label: string; url: string }[];
}

export interface BowlSection {
  kind: LevelKey;
  id: string;
  level: Level;
  c: number;   // center angle in degrees (180 = stage end)
  a0: number;
  a1: number;
  blocked: boolean;
}
export interface FloorSectionInstance extends FloorSection { kind: 'floor'; blocked: false }
export type Section = BowlSection | FloorSectionInstance;

export interface Pick { L: PickLetter; sec: string; row: number; yaw: number; pitch: number }

export interface SeatMetrics {
  d: number;               // feet to the stage front (concerts) or center (sports)
  ang: number;             // degrees off the stage axis (concerts) or off the sideline (sports)
  view?: 'Sideline' | 'Corner' | 'End';
  eye: number;             // eye height in feet
  score: number;           // 0-100 sightline score
  price: number;           // sample price in USD
  value: number;           // score per $100
  notes: string[];
  sport: boolean;
}
