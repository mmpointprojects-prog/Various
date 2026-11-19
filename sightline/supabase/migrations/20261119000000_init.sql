-- Sightline schema: venues store their seat bowl once; setups describe what changes per event type.
-- All tables are public read-only for the website (RLS: select for anon and authenticated, no writes).

create table public.venues (
  id            text primary key,
  name          text not null,
  city          text not null,
  state         text not null,
  ceiling_ft    numeric not null,
  seat_colors   integer[] not null default '{}',
  model_notes   text not null default '',
  sources       jsonb not null default '[]',
  created_at    timestamptz not null default now()
);

create table public.levels (
  venue_id    text not null references public.venues(id) on delete cascade,
  level_key   text not null check (level_key in ('lower','upper')),
  name        text not null,
  rx0 numeric not null, rx1 numeric not null,
  rz0 numeric not null, rz1 numeric not null,
  n           numeric not null,
  row_count   integer not null check (row_count > 0),
  y0          numeric not null,
  rise        numeric not null,
  section_count integer not null check (section_count > 0),
  first_section integer not null,
  center_num  numeric not null,
  primary key (venue_id, level_key)
);

create table public.setups (
  venue_id     text not null references public.venues(id) on delete cascade,
  setup_id     text not null check (setup_id in ('end','basketball','hockey')),
  label        text not null,
  sport        text check (sport in ('basketball','hockey')),
  closed       text[] not null default '{}',
  target       jsonb not null,
  front        jsonb,
  note         text not null default '',
  price_scale  numeric not null default 1,
  default_picks jsonb not null default '[]',
  primary key (venue_id, setup_id)
);

create table public.floor_sections (
  venue_id   text not null,
  setup_id   text not null,
  section_id text not null,
  label      text not null,
  short      text not null,
  x0 numeric not null, x1 numeric not null, z0 numeric not null, z1 numeric not null,
  front      text not null check (front in ('x0','x1','z0','z1')),
  row_count  integer not null check (row_count > 0),
  seated     boolean not null default false,
  courtside  boolean not null default false,
  primary key (venue_id, setup_id, section_id),
  foreign key (venue_id, setup_id) references public.setups(venue_id, setup_id) on delete cascade
);

create table public.events (
  id          text primary key,
  venue_id    text not null references public.venues(id) on delete cascade,
  event_date  date not null,
  event_time  text not null default '',
  title       text not null,
  event_type  text not null,
  setup_id    text not null,
  confirmed   boolean not null default true,
  updated_at  timestamptz not null default now(),
  foreign key (venue_id, setup_id) references public.setups(venue_id, setup_id)
);
create index events_venue_date_idx on public.events (venue_id, event_date);

alter table public.venues enable row level security;
alter table public.levels enable row level security;
alter table public.setups enable row level security;
alter table public.floor_sections enable row level security;
alter table public.events enable row level security;

create policy "public read venues" on public.venues for select to anon, authenticated using (true);
create policy "public read levels" on public.levels for select to anon, authenticated using (true);
create policy "public read setups" on public.setups for select to anon, authenticated using (true);
create policy "public read floor sections" on public.floor_sections for select to anon, authenticated using (true);
create policy "public read events" on public.events for select to anon, authenticated using (true);
