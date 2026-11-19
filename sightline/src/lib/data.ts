import { SCHEDULE_AS_OF, VENUES } from '../data/venues/index.ts';
import { getSupabase } from './supabase.ts';
import type { Venue, VenueEvent } from './types.ts';

export const listVenues = (): Venue[] => VENUES;
export const getVenue = (id: string): Venue | null => VENUES.find((v) => v.id === id) ?? null;

export interface EventList { events: VenueEvent[]; source: 'database' | 'bundled'; asOf: string }

/**
 * Upcoming events for a venue. Reads the `events` table when Supabase is configured,
 * and falls back to the snapshot bundled with the venue file.
 */
export async function getEvents(venue: Venue, today = new Date()): Promise<EventList> {
  const todayIso = today.toISOString().slice(0, 10);
  const sb = getSupabase();
  if (sb) {
    const { data, error } = await sb
      .from('events')
      .select('id, date:event_date, time:event_time, title, type:event_type, setup:setup_id, confirmed')
      .eq('venue_id', venue.id)
      .gte('event_date', todayIso)
      .order('event_date');
    if (!error && data) return { events: data as VenueEvent[], source: 'database', asOf: todayIso };
  }
  const events = venue.events.filter((e) => e.date >= todayIso).sort((a, b) => a.date.localeCompare(b.date));
  return { events, source: 'bundled', asOf: SCHEDULE_AS_OF };
}

export const formatDate = (iso: string) => {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, d)).toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric', timeZone: 'UTC' });
};
