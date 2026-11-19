'use client';

import { rad, ringPt, seatPos, secTitle } from '@/lib/geometry.ts';
import type { Level, Pick, PickLetter, Section, Setup, Venue } from '@/lib/types.ts';
import { PICK_COLOR } from './colors.ts';

function sectorPath(L: Level, a0: number, a1: number) {
  const pts: [number, number][] = [], n = 10;
  for (let i = 0; i <= n; i++) pts.push(ringPt(L, 0, rad(a0 + ((a1 - a0) * i) / n)));
  for (let i = n; i >= 0; i--) pts.push(ringPt(L, 1, rad(a0 + ((a1 - a0) * i) / n)));
  return 'M' + pts.map((p) => p[0].toFixed(1) + ' ' + p[1].toFixed(1)).join(' L') + 'Z';
}
function outline(L: Level, f: number, scale = 1) {
  const pts: string[] = [];
  for (let i = 0; i < 96; i++) { const p = ringPt(L, f, (i / 96) * Math.PI * 2); pts.push((p[0] * scale).toFixed(1) + ' ' + (p[1] * scale).toFixed(1)); }
  return 'M' + pts.join(' L') + 'Z';
}

interface Props {
  venue: Venue;
  setup: Setup;
  sections: Section[];
  picks: Pick[];
  onToggle: (id: string) => void;
}

export function SeatMap({ venue, setup, sections, picks, onToggle }: Props) {
  const U = venue.levels.upper, Lw = venue.levels.lower;
  const w = U.rx1 + 12, h = U.rz1 + 12;
  const picked = new Map<string, PickLetter>(picks.map((p) => [p.sec, p.L]));
  const shell = { ...U, rx1: U.rx1 + 6, rz1: U.rz1 + 6 };

  return (
    <svg id="map" viewBox={`${-w} ${-h} ${2 * w} ${2 * h}`} role="group" aria-label="Seating map. The stage end is at the top.">
      <defs>
        <pattern id="hatch" width="5" height="5" patternUnits="userSpaceOnUse" patternTransform="rotate(45)">
          <rect width="5" height="5" className="hatch-bg" />
          <line x1="0" y1="0" x2="0" y2="5" className="hatch-line" />
        </pattern>
      </defs>
      <path d={outline(shell, 1)} className="shell" />
      <path d={outline(Lw, 0)} className="floor-bg" />
      {sections.map((s) => {
        const L = picked.get(s.id);
        const d = s.kind === 'floor' ? `M${s.x0} ${s.z0} H${s.x1} V${s.z1} H${s.x0} Z` : sectorPath(s.level, s.a0, s.a1);
        const name = secTitle(s) + (s.blocked ? ', closed for this setup' : L ? `, selected as seat ${L}` : '');
        return (
          <path
            key={s.id}
            d={d}
            className={`sec ${s.kind}${s.blocked ? ' blocked' : ''}${L ? ' picked' : ''}`}
            style={L ? { stroke: PICK_COLOR[L] } : undefined}
            tabIndex={s.blocked ? undefined : 0}
            role="button"
            aria-disabled={s.blocked || undefined}
            aria-label={name}
            onClick={() => onToggle(s.id)}
            onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onToggle(s.id); } }}
          >
            <title>{name}</title>
          </path>
        );
      })}
      {sections.map((s) => {
        let lx: number, ly: number;
        if (s.kind === 'floor') { lx = (s.x0 + s.x1) / 2; ly = (s.z0 + s.z1) / 2; }
        else { const p = ringPt(s.level, picked.has(s.id) ? 0.14 : 0.5, rad(s.c)); lx = p[0]; ly = p[1]; }
        return <text key={'l' + s.id} x={lx.toFixed(1)} y={ly.toFixed(1)} className={`lbl${s.blocked ? ' dim' : ''}`}>{s.kind === 'floor' ? s.short : s.id}</text>;
      })}
      <Surface setup={setup} lower={Lw} />
      {picks.map((p) => {
        const sec = sections.find((s) => s.id === p.sec);
        if (!sec) return null;
        const sp = seatPos(sec, p.row);
        return (
          <g key={'pin' + p.L} className="pin" pointerEvents="none">
            <circle cx={sp.x.toFixed(1)} cy={sp.z.toFixed(1)} r="6" fill={PICK_COLOR[p.L]} />
            <text x={sp.x.toFixed(1)} y={(sp.z + 0.3).toFixed(1)}>{p.L}</text>
          </g>
        );
      })}
    </svg>
  );
}

function Surface({ setup, lower }: { setup: Setup; lower: Level }) {
  if (setup.sport === 'hockey') {
    return (
      <g pointerEvents="none">
        <path d={outline(lower, 0, 0.97)} style={{ fill: 'var(--ice)', stroke: 'var(--court-line)', strokeWidth: 0.6 }} />
        <line x1="-44" x2="44" y1="0" y2="0" style={{ stroke: 'var(--ice-line)', strokeWidth: 1.2 }} />
        <line x1="-45" x2="45" y1="-25" y2="-25" style={{ stroke: '#1f4fae', strokeWidth: 1.2 }} />
        <line x1="-45" x2="45" y1="25" y2="25" style={{ stroke: '#1f4fae', strokeWidth: 1.2 }} />
        <circle r="15" fill="none" style={{ stroke: '#1f4fae', strokeWidth: 0.8 }} />
        <text x="0" y="-60" className="lbl">ICE</text>
      </g>
    );
  }
  if (setup.sport === 'basketball') {
    return (
      <g pointerEvents="none">
        <rect x="-25" y="-47" width="50" height="94" className="court" />
        <line x1="-25" x2="25" y1="0" y2="0" className="court-l" />
        <circle r="6" className="court-l" />
        <rect x="-8" y="-47" width="16" height="19" className="court-l" />
        <rect x="-8" y="28" width="16" height="19" className="court-l" />
      </g>
    );
  }
  const F = setup.front?.z ?? -70;
  return (
    <g pointerEvents="none">
      <rect x="-28" y={F - 26} width="56" height="26" rx="2" className="stage" />
      <text x="0" y={F - 13} className="stage-t">STAGE</text>
    </g>
  );
}
