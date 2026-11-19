import type { PickLetter } from '@/lib/types.ts';

export const LETTERS: PickLetter[] = ['A', 'B', 'C', 'D'];
export const PICK_COLOR: Record<PickLetter, string> = { A: '#FF5D73', B: '#2FC3D3', C: '#4FCB8A', D: '#A68BFF' };
export const scoreColor = (s: number) => (s >= 75 ? 'var(--good)' : s >= 55 ? 'var(--ok)' : 'var(--poor)');
