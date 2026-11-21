'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { clamp, rowsOf, seatPos, secSub, secTitle, sectionsFor } from '@/lib/geometry.ts';
import { metrics } from '@/lib/scoring.ts';
import type { Pick, PickLetter, Section, SetupId, Venue, VenueEvent } from '@/lib/types.ts';
import type { Quality, VenueScene } from '@/lib/scene/VenueScene.ts';
import { LETTERS, PICK_COLOR, scoreColor } from './colors.ts';
import { SeatMap } from './SeatMap.tsx';

interface Props {
  venue: Venue;
  events: VenueEvent[];
  eventsSource: 'database' | 'bundled';
  asOf: string;
}

const SETUP_ORDER: SetupId[] = ['end', 'basketball', 'hockey'];
const storageKey = (venueId: string) => `sightline:${venueId}`;

function defaultsFor(venue: Venue, setupId: SetupId): Pick[] {
  return (venue.defaults[setupId] ?? []).map((p) => ({ ...p, yaw: 0, pitch: 0 }));
}

function fmtDate(iso: string) {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, d)).toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric', timeZone: 'UTC' });
}

export function CompareApp({ venue, events, eventsSource, asOf }: Props) {
  const setupIds = SETUP_ORDER.filter((id) => venue.setups[id]);
  const [setupId, setSetupId] = useState<SetupId>(setupIds[0]);
  const [eventId, setEventId] = useState<string>('');
  const [picks, setPicks] = useState<Pick[]>(() => defaultsFor(venue, setupIds[0]));
  const [zoom, setZoom] = useState<1 | 2>(1);
  const [quality, setQuality] = useState<Quality>('enhanced');
  const [toastMsg, setToastMsg] = useState('');
  const [glError, setGlError] = useState(false);
  const sceneRef = useRef<VenueScene | null>(null);
  const canvases = useRef<Partial<Record<PickLetter, HTMLCanvasElement>>>({});
  const [sceneReady, setSceneReady] = useState(0);

  const setup = venue.setups[setupId]!;
  const sections = useMemo(() => sectionsFor(venue.levels, setup), [venue, setup]);
  const secById = useMemo(() => new Map(sections.map((s) => [s.id, s])), [sections]);
  const event = events.find((e) => e.id === eventId);

  const toast = useCallback((m: string) => { setToastMsg(m); window.setTimeout(() => setToastMsg((cur) => (cur === m ? '' : cur)), 2600); }, []);

  // Restore this viewer's last state (picks, setup, event) from the URL or local storage.
  useEffect(() => {
    const qs = new URLSearchParams(window.location.search);
    const ev = events.find((e) => e.id === qs.get('event'));
    let saved: { setupId?: SetupId; picks?: Pick[]; quality?: Quality } = {};
    try { saved = JSON.parse(localStorage.getItem(storageKey(venue.id)) || '{}'); } catch { /* storage blocked */ }
    const sid = ev?.setup ?? (saved.setupId && venue.setups[saved.setupId] ? saved.setupId : setupIds[0]);
    setSetupId(sid);
    if (ev) setEventId(ev.id);
    if (saved.quality) setQuality(saved.quality);
    const secs = new Set(sectionsFor(venue.levels, venue.setups[sid]!).filter((s) => !s.blocked).map((s) => s.id));
    const restored = saved.setupId === sid ? (saved.picks ?? []).filter((p) => secs.has(p.sec)) : [];
    setPicks(restored.length ? restored : defaultsFor(venue, sid));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [venue.id]);

  useEffect(() => {
    try { localStorage.setItem(storageKey(venue.id), JSON.stringify({ setupId, picks, quality })); } catch { /* storage blocked */ }
    const url = new URL(window.location.href);
    if (eventId) url.searchParams.set('event', eventId); else url.searchParams.delete('event');
    window.history.replaceState(null, '', url);
  }, [venue.id, setupId, picks, quality, eventId]);

  // Create the 3D scene once, in the browser only.
  useEffect(() => {
    let disposed = false;
    import('@/lib/scene/VenueScene.ts').then(({ VenueScene }) => {
      if (disposed) return;
      try { sceneRef.current = new VenueScene(); setSceneReady((n) => n + 1); }
      catch { setGlError(true); }
    });
    return () => { disposed = true; sceneRef.current?.dispose(); sceneRef.current = null; };
  }, []);

  const renderAll = useCallback(() => {
    const sc = sceneRef.current; if (!sc) return;
    for (const p of picks) { const cv = canvases.current[p.L]; if (cv) sc.render(p, cv, zoom); }
  }, [picks, zoom]);

  // Rebuild the scene when the venue, setup or quality changes.
  useEffect(() => {
    const sc = sceneRef.current; if (!sc) return;
    sc.load(venue, setup, sections, quality);
    sc.setPicks(picks);
    requestAnimationFrame(renderAll);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sceneReady, venue, setup, sections, quality]);

  // The crowd leaves the chosen seats empty, so it is rebuilt when seats or rows change (not on look-around).
  const seatKey = picks.map((p) => p.sec + ':' + p.row).join('|');
  useEffect(() => {
    const sc = sceneRef.current; if (!sc) return;
    sc.setPicks(picks);
    requestAnimationFrame(renderAll);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [seatKey]);

  useEffect(() => { requestAnimationFrame(renderAll); }, [zoom, renderAll]);

  useEffect(() => {
    let t = 0;
    const onResize = () => { window.clearTimeout(t); t = window.setTimeout(renderAll, 120); };
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, [renderAll]);

  function changeSetup(next: SetupId, fromEvent = false) {
    if (next === setupId) return;
    const nextSections = sectionsFor(venue.levels, venue.setups[next]!);
    const open = nextSections.filter((s) => !s.blocked);
    let moved = 0;
    const nextPicks = picks.map((p) => {
      const s = nextSections.find((x) => x.id === p.sec);
      if (s && !s.blocked && s.kind !== 'floor') return { ...p, yaw: 0, pitch: 0, row: Math.min(p.row, rowsOf(s)) };
      // Floor layouts differ per setup: move the seat to the nearest open section.
      const old = secById.get(p.sec), o = old ? seatPos(old, p.row) : { x: 0, y: 5, z: 0 };
      let best: Section | null = null, bd = Infinity;
      for (const c of open) {
        if (picks.some((q) => q.sec === c.id)) continue;
        const q = seatPos(c, Math.ceil(rowsOf(c) / 2)), dd = Math.hypot(q.x - o.x, q.z - o.z, (q.y - o.y) * 0.5);
        if (dd < bd) { bd = dd; best = c; }
      }
      moved++;
      return best ? { ...p, sec: best.id, row: Math.max(1, Math.ceil(rowsOf(best) / 3)), yaw: 0, pitch: 0 } : null;
    }).filter(Boolean) as Pick[];
    setSetupId(next);
    setPicks(nextPicks.length ? nextPicks : defaultsFor(venue, next));
    if (!fromEvent && event && event.setup !== next) setEventId('');
    if (moved) toast(moved === 1 ? 'One seat isn’t sold in this setup, so it moved to the nearest open section.' : `${moved} seats aren’t sold in this setup, so they moved to the nearest open sections.`);
  }

  function toggleSection(id: string) {
    const sec = secById.get(id); if (!sec) return;
    if (sec.blocked) { toast(`${secTitle(sec)} is usually closed in this setup.`); return; }
    const idx = picks.findIndex((p) => p.sec === id);
    if (idx >= 0) { setPicks(picks.filter((_, i) => i !== idx)); toast(`Removed ${secTitle(sec)}`); return; }
    if (picks.length >= 4) { toast('You can compare four seats at a time. Remove one first.'); return; }
    const L = LETTERS.find((l) => !picks.some((p) => p.L === l))!;
    setPicks([...picks, { L, sec: id, row: Math.max(1, Math.ceil(rowsOf(sec) / 3)), yaw: 0, pitch: 0 }].sort((a, b) => a.L.localeCompare(b.L)));
    toast(`Added ${secTitle(sec)} as seat ${L}`);
  }

  const ms = useMemo(() => {
    const out: Partial<Record<PickLetter, ReturnType<typeof metrics>>> = {};
    for (const p of picks) { const s = secById.get(p.sec); if (s) out[p.L] = metrics(s, p.row, setup); }
    return out;
  }, [picks, secById, setup]);
  const ranked = picks.filter((p) => ms[p.L]);
  const bestView = ranked.length >= 2 ? ranked.reduce((a, b) => (ms[b.L]!.score > ms[a.L]!.score ? b : a)).L : null;
  const bestValue = ranked.length >= 2 ? ranked.reduce((a, b) => (ms[b.L]!.value > ms[a.L]!.value ? b : a)).L : null;

  // Look around by dragging a view.
  const drag = useRef<{ L: PickLetter; x: number; y: number; yaw: number; pitch: number; raf: number } | null>(null);
  const onPointerDown = (L: PickLetter) => (e: React.PointerEvent<HTMLCanvasElement>) => {
    const p = picks.find((x) => x.L === L); if (!p) return;
    drag.current = { L, x: e.clientX, y: e.clientY, yaw: p.yaw, pitch: p.pitch, raf: 0 };
    e.currentTarget.setPointerCapture(e.pointerId);
  };
  const onPointerMove = (e: React.PointerEvent<HTMLCanvasElement>) => {
    const d = drag.current; if (!d) return;
    const k = zoom === 2 ? 0.0022 : 0.0045;
    const yaw = clamp(d.yaw + (e.clientX - d.x) * k, -1.4, 1.4), pitch = clamp(d.pitch + (e.clientY - d.y) * k, -0.7, 0.7);
    const p = picks.find((x) => x.L === d.L); if (!p) return;
    p.yaw = yaw; p.pitch = pitch; // live update without a React re-render per frame
    if (!d.raf) d.raf = requestAnimationFrame(() => { d.raf = 0; const cv = canvases.current[d.L]; if (cv && sceneRef.current) sceneRef.current.render(p, cv, zoom); });
  };
  const onPointerUp = () => { if (drag.current) { drag.current = null; setPicks((ps) => [...ps]); } };

  const nextSlot = LETTERS.find((l) => !picks.some((p) => p.L === l));

  return (
    <>
      <div className="controls" style={{ justifyContent: 'flex-end' }}>
        <div className="seg" role="group" aria-label="Setup">
          <span className="seg-l">Setup</span>
          {setupIds.map((id) => (
            <button key={id} aria-pressed={id === setupId} onClick={() => changeSetup(id)}>{venue.setups[id]!.label}</button>
          ))}
        </div>
        <div className="seg" role="group" aria-label="3D detail">
          <span className="seg-l">3D</span>
          <button aria-pressed={quality === 'basic'} onClick={() => setQuality('basic')}>Basic</button>
          <button aria-pressed={quality === 'enhanced'} onClick={() => setQuality('enhanced')}>Enhanced</button>
        </div>
        <div className="seg" role="group" aria-label="Lens">
          <button aria-pressed={zoom === 1} onClick={() => setZoom(1)}>Eye level</button>
          <button aria-pressed={zoom === 2} onClick={() => setZoom(2)}>2× zoom</button>
        </div>
      </div>

      <section className="eventbar" aria-label="Event">
        <div className="ev-pick">
          <label htmlFor="event">Event</label>
          <select id="event" value={eventId} onChange={(e) => {
            const ev = events.find((x) => x.id === e.target.value);
            setEventId(ev ? ev.id : '');
            if (ev && ev.setup !== setupId) changeSetup(ev.setup, true);
          }}>
            <option value="">Browse setups (no event)</option>
            {groupByMonth(events).map(([month, list]) => (
              <optgroup key={month} label={month}>
                {list.map((e) => <option key={e.id} value={e.id}>{fmtDate(e.date)} · {e.title}</option>)}
              </optgroup>
            ))}
          </select>
        </div>
        <p className="ev-info">
          {event ? (
            <>
              <strong>{fmtDate(event.date)}{event.time ? ', ' + event.time : ''}</strong>
              <span>{event.type}</span>
              <span>{venue.setups[event.setup]?.label} setup{event.type === 'Concert' ? ' (assumed; check the show’s seating chart)' : ''}</span>
              {!event.confirmed && <span className="ev-warn">Date from one source. Confirm before buying.</span>}
            </>
          ) : (
            <span>Pick an event to load its setup, or browse setups with the buttons above.</span>
          )}
          <span className="ev-src">{eventsSource === 'database' ? 'Live schedule' : `Schedule as of ${fmtDate(asOf)}`}</span>
        </p>
      </section>

      <main className="layout">
        <aside className="mapcol">
          <div className="panel">
            <h2>Pick up to four seats</h2>
            <p className="hint">Tap a section to add it. Tap it again to remove it. Change the row on each card.</p>
            <p className="hint">{setup.note}</p>
            <SeatMap venue={venue} setup={setup} sections={sections} picks={picks} onToggle={toggleSection} />
            <ul className="legend">
              <li><span className="sw f" />Floor</li>
              <li><span className="sw l" />{venue.levels.lower.name}</li>
              {venue.levels.middle && <li><span className="sw m" />{venue.levels.middle.name}</li>}
              <li><span className="sw u" />{venue.levels.upper.name}</li>
              {setup.closed.length > 0 && <li><span className="sw b" />Closed in this setup</li>}
            </ul>
          </div>
        </aside>

        <section className="compare" aria-labelledby="cmp-h">
          <div className="compare-head">
            <h2 id="cmp-h">Your seats, side by side</h2>
            <p>{picks.length} of 4 seats · {event ? event.title : setup.label}</p>
          </div>
          <div className="grid">
            {LETTERS.map((L) => {
              const p = picks.find((x) => x.L === L);
              const sec = p && secById.get(p.sec);
              if (!p || !sec) {
                return (
                  <div key={L} className={`slot empty${L === nextSlot ? '' : ' later'}`} style={{ ['--pc' as string]: PICK_COLOR[L] }}>
                    <span className="chip">{L}</span>
                    <p>Tap a section on the map to add seat {L}.</p>
                  </div>
                );
              }
              const m = ms[L]!;
              return (
                <article key={L} className="card" style={{ ['--pc' as string]: PICK_COLOR[L] }} aria-label={`Seat ${L}: ${secTitle(sec)}, row ${p.row}`}>
                  <div className="card-h">
                    <span className="chip">{L}</span>
                    <div className="ttl"><strong>{secTitle(sec)}</strong><span>{secSub(sec)}</span></div>
                    <label className="rowpick" htmlFor={`row-${L}`}>Row
                      <select id={`row-${L}`} value={p.row} onChange={(e) => setPicks(picks.map((x) => (x.L === L ? { ...x, row: +e.target.value, yaw: 0, pitch: 0 } : x)))}>
                        {Array.from({ length: rowsOf(sec) }, (_, i) => <option key={i + 1} value={i + 1}>{i + 1}</option>)}
                      </select>
                    </label>
                    <button className="x" aria-label={`Remove seat ${L}`} onClick={() => toggleSection(p.sec)}>×</button>
                  </div>
                  <div className="view">
                    <canvas
                      ref={(el) => { if (el) canvases.current[L] = el; else delete canvases.current[L]; }}
                      aria-label={`Rendered view of the ${setup.sport ? 'court' : 'stage'} from ${secTitle(sec)}, row ${p.row}`}
                      onPointerDown={onPointerDown(L)} onPointerMove={onPointerMove} onPointerUp={onPointerUp} onPointerCancel={onPointerUp}
                    />
                    {glError && <p className="gl-error">This browser can’t show 3D views (WebGL is off).</p>}
                    <div className="badges">
                      {L === bestView && <span className="badge">Best view</span>}
                      {L === bestValue && <span className="badge">Best value</span>}
                    </div>
                    <div className="vtools">
                      <span>Drag to look around</span>
                      <button className="recenter" onClick={() => setPicks(picks.map((x) => (x.L === L ? { ...x, yaw: 0, pitch: 0 } : x)))}>Re-center</button>
                    </div>
                  </div>
                  <dl className="stats">
                    <div><dt>{m.sport ? 'To center' : 'To stage'}</dt><dd>{Math.round(m.d)}<small>ft</small></dd></div>
                    <div><dt>{m.sport ? 'View' : 'Angle'}</dt><dd>{m.sport ? m.view : Math.round(m.ang) + '°'}</dd></div>
                    <div><dt>Eye height</dt><dd>{Math.round(m.eye)}<small>ft</small></dd></div>
                    <div><dt>Sample price</dt><dd>${m.price}</dd></div>
                  </dl>
                  <div className="score" style={{ ['--sc' as string]: scoreColor(m.score) }}>
                    <span>Sightline score</span>
                    <div className="bar" role="meter" aria-valuemin={0} aria-valuemax={100} aria-valuenow={m.score} aria-label="Sightline score"><i style={{ width: m.score + '%' }} /></div>
                    <b>{m.score}</b>
                  </div>
                  <ul className="notes">{m.notes.map((n) => <li key={n}>{n}</li>)}</ul>
                </article>
              );
            })}
          </div>
        </section>
      </main>
      <div id="toast" role="status" aria-live="polite" className={toastMsg ? 'on' : ''}>{toastMsg}</div>
    </>
  );
}

function groupByMonth(events: VenueEvent[]): [string, VenueEvent[]][] {
  const out = new Map<string, VenueEvent[]>();
  for (const e of events) {
    const [y, m] = e.date.split('-').map(Number);
    const key = new Date(Date.UTC(y, m - 1, 1)).toLocaleDateString('en-US', { month: 'long', year: 'numeric', timeZone: 'UTC' });
    out.set(key, [...(out.get(key) ?? []), e]);
  }
  return [...out];
}
