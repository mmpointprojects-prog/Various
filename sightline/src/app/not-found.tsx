import Link from 'next/link';

export default function NotFound() {
  return (
    <section className="hero">
      <h1>We don’t have that venue yet</h1>
      <p>Sightline covers a handful of U.S. arenas for now. <Link href="/">See the venues</Link>.</p>
    </section>
  );
}
