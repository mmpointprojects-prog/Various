import Link from 'next/link';
import { formatDate, getEvents, listVenues } from '@/lib/data.ts';

export const revalidate = 3600;

export default async function Home() {
  const venues = listVenues();
  const nextEvents = await Promise.all(venues.map((v) => getEvents(v).then((r) => r.events[0] ?? null)));
  return (
    <>
      <section className="hero">
        <h1>See your seat before you buy it</h1>
        <p>Pick a venue, choose up to four seats and compare the view from each one side by side, with distance, angle and a sightline score.</p>
      </section>
      <div className="venue-grid">
        {venues.map((v, i) => {
          const ev = nextEvents[i];
          return (
            <Link key={v.id} href={`/venues/${v.id}`} className="venue-card">
              <h2>{v.name}</h2>
              <p className="city">{v.city}, {v.state}</p>
              <div className="chips">
                {Object.values(v.setups).map((s) => <span key={s!.id} className="tag">{s!.sport ? s!.label : 'Concerts'}</span>)}
              </div>
              <p className="next">
                <span>Next event</span>
                {ev ? `${formatDate(ev.date)} · ${ev.title}` : 'No upcoming events listed'}
              </p>
            </Link>
          );
        })}
      </div>
    </>
  );
}
