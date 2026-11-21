import type { Metadata, Viewport } from 'next';
import Link from 'next/link';
import './globals.css';

export const metadata: Metadata = {
  title: { default: 'Sightline', template: '%s · Sightline' },
  description: 'Compare the view from up to four seats side by side before you buy concert or game tickets.',
};

export const viewport: Viewport = { width: 'device-width', initialScale: 1, viewportFit: 'cover' };

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <head>
        <link rel="preconnect" href="https://fonts.googleapis.com" />
        <link rel="preconnect" href="https://fonts.gstatic.com" crossOrigin="" />
        {/* eslint-disable-next-line @next/next/no-page-custom-font */}
        <link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Bricolage+Grotesque:opsz,wght@12..96,600;12..96,700&family=Inter:wght@400;500;600&family=JetBrains+Mono:wght@500&display=swap" />
      </head>
      <body>
        <div className="app">
          <header className="top">
            <Link href="/" className="brand-link">
              <span className="mark" aria-hidden="true" />
              <span><h1 style={{ fontSize: 26 }}>Sightline</h1></span>
            </Link>
            <span className="tag">Preview</span>
          </header>
          {children}
          <footer>
            Venues are approximate 3D models built from public seating charts (section numbers, row counts, usual stage position).
            Sightline is not affiliated with any venue, team or ticket seller. Prices are samples, not real listings.
            Which sections close behind the stage varies by show, so check your event’s seating chart before you buy.
          </footer>
        </div>
      </body>
    </html>
  );
}
