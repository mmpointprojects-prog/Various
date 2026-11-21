-- Optional middle seating ring (club level or second tier) and prefixed section ids (e.g. C1–C35).
alter table public.levels drop constraint if exists levels_level_key_check;
alter table public.levels add constraint levels_level_key_check check (level_key in ('lower','middle','upper'));
alter table public.levels add column if not exists section_prefix text not null default '';
