import Link from 'next/link';

export default function NotFound() {
  return (
    <section className="hero">
      <h1>We don’t have that venue yet</h1>
      <p>Sightline covers five Florida arenas for now. <Link href="/">See the venues</Link>.</p>
    </section>
  );
}
