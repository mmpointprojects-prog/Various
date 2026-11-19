# Sightline

Compare the view from up to four seats side by side before you buy concert or game tickets.
Pick a venue and an event, tap sections on the seat map, and each seat gets a 3D view of the stage or court,
plus distance, viewing angle, eye height, a sightline score and a sample price.

This first version covers five Florida arenas. They are the template for every venue that follows.

| Venue | City | Setups |
| --- | --- | --- |
| Kaseya Center | Miami | Concerts, Heat basketball |
| Kia Center | Orlando | Concerts, Magic basketball, Solar Bears hockey |
| Benchmark International Arena | Tampa | Concerts, Lightning hockey |
| Amerant Bank Arena | Sunrise | Concerts, Panthers hockey |
| VyStar Veterans Memorial Arena | Jacksonville | Concerts, Icemen hockey |

## Run it

Needs Node 20 or newer.

```bash
cd sightline
npm install
npm run dev        # http://localhost:3000
```

Other commands:

```bash
npm test           # venue data, geometry and scoring checks
npm run typecheck
npm run build
npm run seed:sql   # regenerate supabase/seed.sql from the venue files
```

## How it's built

- **Next.js 14 + TypeScript.** Venue pages are generated at build time and refresh hourly.
- **three.js** draws the seat views. One WebGL renderer draws all four views in turn and copies each into its card,
  which keeps phones under their limit of live 3D canvases.
- **Supabase (optional).** With `NEXT_PUBLIC_SUPABASE_URL` and `NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY` set
  (see `.env.example`), each venue's event list is read from the `events` table. Without them the site uses the
  schedule bundled in the venue files.

```
src/
  app/                    pages: home (venue list) and /venues/[venueId]
  components/             CompareApp (the compare screen), SeatMap (SVG map)
  data/venues/            one file per venue + shared building blocks
  lib/geometry.ts         seating bowl shape, sections, seat positions
  lib/scoring.ts          sightline score, notes and sample prices
  lib/scene/              3D scene (VenueScene) and canvas textures
supabase/migrations/      database schema
supabase/seed.sql         generated from src/data/venues
tests/                    node:test checks on every venue
```

## How a venue is modeled

A venue stores its seating bowl once: two rings (lower and upper level), each with its size, row count, rise per
row and section numbering. What changes from event to event is the **setup**:

- where the stage or playing surface is,
- which sections are closed (for example, behind an end stage),
- what's on the floor (standing sections, courtside seats, or nothing for hockey).

Seats are spaced by distance around the bowl, so end and sideline sections come out the same width.

### Adding a venue

1. Copy one of the files in `src/data/venues/` and fill in the levels, setups, default seats and events.
   `shared.ts` has the end-stage, basketball and hockey setups and common floor layouts.
2. Add it to `src/data/venues/index.ts`.
3. Run `npm test`. It checks that closed sections exist, default seats are open and in range, floor sections
   fit inside the bowl, and event setups exist.
4. Run `npm run seed:sql` if the database is in use.

## Database

`supabase/migrations/20261119000000_init.sql` creates `venues`, `levels`, `setups`, `floor_sections` and `events`,
all readable by the public and not writable from the browser (row level security). `supabase/seed.sql` loads the
five venues and their events, and can be run again safely to update them.

The Supabase project isn't created yet: the account's organization is at its free-tier limit of two active
projects.

## Known limits

- Venue shapes, sizes and section positions are estimates from public seating charts, not measured drawings.
  Production accuracy needs official seat maps or a 3D vendor.
- Club rings, suites, loge boxes and a few small sections aren't modeled. Each venue lists what's missing on its page.
- Prices are samples. Real prices come once a ticket data partner (Ticket Evolution or TicketNetwork) is approved.
- Event lists are a snapshot of public schedules from 2026-11-18. Events marked "confirm before buying" come from
  only one source.
- Not affiliated with any venue, team or ticket seller. No logos are used.
