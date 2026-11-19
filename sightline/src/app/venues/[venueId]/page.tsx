import type { Metadata } from 'next';
import Link from 'next/link';
import { notFound } from 'next/navigation';
import { CompareApp } from '@/components/CompareApp.tsx';
import { getEvents, getVenue, listVenues } from '@/lib/data.ts';

export const revalidate = 3600;

export function generateStaticParams() {
  return listVenues().map((v) => ({ venueId: v.id }));
}

export function generateMetadata({ params }: { params: { venueId: string } }): Metadata {
  const v = getVenue(params.venueId);
  return v ? { title: `${v.name} seat views`, description: `Compare seat views at ${v.name}, ${v.city}, side by side.` } : {};
}

export default async function VenuePage({ params }: { params: { venueId: string } }) {
  const venue = getVenue(params.venueId);
  if (!venue) notFound();
  const { events, source, asOf } = await getEvents(venue);
  return (
    <>
      <nav className="crumbs" aria-label="Breadcrumb"><Link href="/">Venues</Link><span aria-hidden="true">/</span><span>{venue.name}</span></nav>
      <div className="brand">
        <div>
          <h1 style={{ fontSize: 'clamp(24px,3vw,32px)' }}>{venue.name}</h1>
          <p className="venue">{venue.city}, {venue.state} <span className="tag">Approximate model</span></p>
        </div>
      </div>
      <CompareApp venue={venue} events={events} eventsSource={source} asOf={asOf} />
      <p className="model-notes">
        Model notes: {venue.modelNotes} Sources:{' '}
        {venue.sources.map((s, i) => <span key={s.url}>{i ? ', ' : ''}<a href={s.url} target="_blank" rel="noreferrer">{s.label}</a></span>)}.
      </p>
    </>
  );
}
