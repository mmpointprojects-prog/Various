import type { Venue } from '../../lib/types.ts';
import { amerant } from './amerant.ts';
import { benchmark } from './benchmark.ts';
import { kaseya } from './kaseya.ts';
import { kia } from './kia.ts';
import { vystar } from './vystar.ts';

/** Venues in display order. Event lists are snapshots of public schedules taken 2026-11-18. */
export const VENUES: Venue[] = [kaseya, kia, benchmark, amerant, vystar];
export const SCHEDULE_AS_OF = '2026-11-18';
