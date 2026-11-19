# Sightline: project brief

Last updated 2026-11-19. Upload this file to the Sightline Project's knowledge so every new chat starts with full context.

## What Sightline is

A website (and later an app) for concertgoers and sports fans. You pick a venue and an event, choose up to four
seats on the seat map, and see the view from each seat side by side: a 3D view of the stage or court, distance,
viewing angle, eye height, a 0–100 sightline score and a price. Ticket sites show one seat's view at a time;
the side-by-side comparison is the point.

Owner: Marc. Working name "Sightline" (not yet checked for trademark or domain).

## Where things live

| Item | Location |
| --- | --- |
| Website code | GitHub `mmpointprojects-prog/Various`, branch `sightline`, folder `/sightline` (public repo for now) |
| Decision Log | claude.ai artifact "2026-11-19_0326 Sightline Decision Log": https://claude.ai/artifact/9Ywx1hJVVvocqcdWq83Fbj |
| Project index | claude.ai artifact: https://claude.ai/artifact/7bdAEy89z4eeK2HosSeDD6 |
| Prototype | claude.ai artifact "Sightline Seat Compare": https://claude.ai/artifact/Qoip5azZYGZMRb3XCha3cN |
| Database | Supabase, not created yet (see open questions). Schema and seed are in the repo under `sightline/supabase/` |

## Current state

- Phase 4 (Build) of the project workflow. Phase 1 validation (user interviews, revenue math) is not done; that
  risk is logged in the Decision Log.
- The website runs locally: home page with five venues, a compare page per venue, enhanced 3D views, event
  dropdowns, 26 passing tests. It is not hosted yet.
- Venues: Kaseya Center (Miami), Kia Center (Orlando), Benchmark International Arena (Tampa),
  Amerant Bank Arena (Sunrise), VyStar Veterans Memorial Arena (Jacksonville). These five are the template
  for every venue after them.
- Event lists are a snapshot of public schedules taken 2026-11-18 (Nov 19 to Dec 31, 2026). Events from a
  single source are flagged "confirm before buying".
- Prices are modeled samples. Scores come from a formula in `src/lib/scoring.ts`.

## Key decisions (details and reasons in the Decision Log)

- Website first, native app later (React Native/Expo can reuse the logic).
- Stack: Next.js 14 + TypeScript, three.js for 3D, Supabase for data.
- A venue stores its seating bowl once; a "setup" per event type (end stage, basketball, hockey) sets the stage
  or playing surface, closed sections and floor layout.
- Revenue: affiliate commissions on ticket click-throughs first; premium price alerts second; licensing the
  compare widget to venues and teams later.
- Ticket prices: apply to Ticket Evolution or TicketNetwork for seat-level listings. StubHub is parked.
- Events feed: Ticketmaster Discovery API later, to replace the snapshot lists.
- Production 3D: professional venue models built from official seat maps; fan photos as a real-life check;
  photo scans (Gaussian splatting) for flagship venues later.
- No team, venue or league logos anywhere. Every page says the models are approximate and not affiliated.

## How a venue is modeled

- Two seating rings (lower and upper level), each a rounded-rectangle shape that widens row by row. Each ring has
  its section count, first section number, rows, rise per row, and which section number sits at the stage end.
- Sections are spaced evenly by distance around the bowl, so end and sideline sections are the same width.
- Units are feet. The stage end is north (top of the map).
- All five arenas share the same floor size, so stage, court and rink layouts are reused (`src/data/venues/shared.ts`).
- Section numbers, row counts and stage positions come from public seating guides (RateYourSeats, TickPick,
  venue sites). Shapes and sizes are estimates.

## Open questions

1. Supabase: the Quick Easy Claims org is at its limit of 2 free active projects. Pause or delete one (or
   upgrade), then create the "sightline" project and load `supabase/migrations` and `supabase/seed.sql`.
2. Hosting and domain: Vercel recommended; connect the GitHub repo in Vercel's dashboard.
3. Validation: talk to 5–10 concertgoers about how they choose seats; write the revenue math at low, mid and
   high traffic.
4. Name and domain check for "Sightline".
5. Ticket data partner application.
6. Move the code to its own private repo.
7. Model gaps: club rings, suites and loge boxes; Amerant's floor layout; lettered rows at Benchmark and VyStar
   are numbered in the model.

## Working preferences (Marc)

- Concise and solution-first; recommend one fix with up to two alternatives.
- Timestamp every filename YYYY-MM-DD_HHMM. For batches, build one sample first and wait for OK.
- Update artifacts in place at the same link; pin key deliverables; keep this Project's index updated at the end
  of each session.
- Suggest the best model for each task.
