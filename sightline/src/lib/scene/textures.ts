import * as THREE from 'three';

// Canvas-drawn textures. Generic on purpose: no team or venue logos.

function canvasTexture(w: number, h: number, draw: (g: CanvasRenderingContext2D, w: number, h: number) => void, repeat = false) {
  const c = document.createElement('canvas');
  c.width = w; c.height = h;
  draw(c.getContext('2d')!, w, h);
  const t = new THREE.CanvasTexture(c);
  t.colorSpace = THREE.SRGBColorSpace;
  if (repeat) t.wrapS = THREE.RepeatWrapping;
  return t;
}

export const stageScreenTexture = () => canvasTexture(512, 256, (g) => {
  const gr = g.createLinearGradient(0, 0, 512, 256);
  gr.addColorStop(0, '#ff4f7b'); gr.addColorStop(0.55, '#ff9a3c'); gr.addColorStop(1, '#ffd36b');
  g.fillStyle = gr; g.fillRect(0, 0, 512, 256);
  g.fillStyle = 'rgba(255,255,255,.22)';
  for (let i = 0; i < 14; i++) { const h = 30 + ((i * 37) % 90); g.fillRect(14 + i * 36, 256 - h, 20, h); }
  g.fillStyle = 'rgba(24,10,32,.88)';
  g.beginPath(); g.arc(256, 92, 30, 0, Math.PI * 2); g.fill();
  g.beginPath(); g.moveTo(200, 256); g.quadraticCurveTo(206, 132, 256, 128); g.quadraticCurveTo(306, 132, 312, 256); g.fill();
  g.fillRect(300, 150, 60, 10);
});

export const scoreboardTexture = () => canvasTexture(512, 256, (g) => {
  g.fillStyle = '#0a1530'; g.fillRect(0, 0, 512, 256);
  g.fillStyle = '#ffffff'; g.font = 'bold 34px sans-serif'; g.textAlign = 'center';
  g.fillText('HOME', 128, 70); g.fillText('AWAY', 384, 70);
  g.font = 'bold 96px sans-serif'; g.fillStyle = '#ffb43a'; g.fillText('3', 128, 170); g.fillText('2', 384, 170);
  g.font = 'bold 30px sans-serif'; g.fillStyle = '#cfe0ff'; g.fillText('12:48', 256, 230);
});

export const suiteTexture = () => canvasTexture(256, 64, (g, w, h) => {
  g.fillStyle = '#12141b'; g.fillRect(0, 0, w, h);
  for (let i = 0; i < 8; i++) {
    const x = i * 32 + 3, lit = (i * 37) % 5 > 1, gr = g.createLinearGradient(0, 8, 0, 56);
    gr.addColorStop(0, lit ? '#ffd49a' : '#3a4256'); gr.addColorStop(1, lit ? '#b5652a' : '#202535');
    g.fillStyle = gr; g.fillRect(x, 8, 26, 48);
    if (lit) { g.fillStyle = 'rgba(18,16,26,.65)'; g.beginPath(); g.arc(x + 9, 31, 4, 0, 7); g.arc(x + 19, 33, 4, 0, 7); g.fill(); g.fillRect(x + 4, 36, 20, 20); }
  }
}, true);

export const ribbonTexture = (sport: boolean) => canvasTexture(1024, 32, (g, w, h) => {
  const gr = g.createLinearGradient(0, 0, w, 0);
  const stops = sport ? ['#c8102e', '#111111', '#f9a01b', '#c8102e'] : ['#ff2d75', '#ff9f1a', '#7a1cff', '#ff2d75'];
  stops.forEach((c, i) => gr.addColorStop(i / (stops.length - 1), c));
  g.fillStyle = gr; g.fillRect(0, 0, w, h);
  g.fillStyle = 'rgba(0,0,0,.3)'; for (let x = 0; x < w; x += 3) g.fillRect(x, 0, 1, h);
  g.fillStyle = '#ffffff'; g.font = 'bold 20px sans-serif';
  for (let x = 20; x < w; x += 340) g.fillText(sport ? 'DEFENSE  ·  MAKE SOME NOISE' : 'ENJOY THE SHOW  ·  SIGHTLINE', x, 23);
}, true);

export function surfaceTexture(kind: 'hockey' | 'basketball') {
  if (kind === 'hockey') return canvasTexture(460, 1040, (g, w) => {
    const k = 5; // 5 px per foot, rink 92 x 208
    g.fillStyle = '#eef3f8'; g.fillRect(0, 0, w, 1040);
    const line = (z: number, col: string, lw: number) => { g.fillStyle = col; g.fillRect(0, (104 + z) * k - (lw * k) / 2, w, lw * k); };
    line(0, '#c2383f', 1); line(-25, '#1f4fae', 1); line(25, '#1f4fae', 1); line(-93, '#c2383f', 0.3); line(93, '#c2383f', 0.3);
    g.strokeStyle = '#1f4fae'; g.lineWidth = 2; g.beginPath(); g.arc(46 * k, 104 * k, 15 * k, 0, 7); g.stroke();
    g.strokeStyle = '#c2383f';
    for (const [x, z] of [[-22, -69], [22, -69], [-22, 69], [22, 69]]) { g.beginPath(); g.arc((46 + x) * k, (104 + z) * k, 15 * k, 0, 7); g.stroke(); }
    g.fillStyle = 'rgba(80,140,220,.35)';
    for (const z of [-93, 93]) { g.beginPath(); g.arc(46 * k, (104 + z) * k, 6 * k, z < 0 ? 0 : Math.PI, z < 0 ? Math.PI : Math.PI * 2); g.fill(); }
  });
  return canvasTexture(500, 940, (g, w, h) => {
    const k = 10; // court 50 x 94
    const gr = g.createLinearGradient(0, 0, w, 0);
    gr.addColorStop(0, '#d2a868'); gr.addColorStop(0.5, '#ddb57a'); gr.addColorStop(1, '#d0a565');
    g.fillStyle = gr; g.fillRect(0, 0, w, h);
    g.strokeStyle = 'rgba(120,80,30,.18)'; g.lineWidth = 1;
    for (let x = 0; x < w; x += 8) { g.beginPath(); g.moveTo(x, 0); g.lineTo(x, h); g.stroke(); }
    g.strokeStyle = '#f6f1e6'; g.lineWidth = 4; g.strokeRect(2, 2, w - 4, h - 4);
    g.beginPath(); g.moveTo(0, 47 * k); g.lineTo(w, 47 * k); g.stroke();
    g.beginPath(); g.arc(25 * k, 47 * k, 6 * k, 0, 7); g.stroke();
    for (const end of [0, 1]) {
      const y0 = end ? h : 0, dir = end ? -1 : 1;
      g.fillStyle = 'rgba(30,60,140,.55)'; g.fillRect(17 * k, end ? y0 - 19 * k : y0, 16 * k, 19 * k);
      g.strokeRect(17 * k, end ? y0 - 19 * k : y0, 16 * k, 19 * k);
      g.beginPath(); g.arc(25 * k, y0 + dir * 19 * k, 6 * k, 0, 7); g.stroke();
      g.beginPath(); g.moveTo(3 * k, y0); g.lineTo(3 * k, y0 + dir * 14 * k); g.moveTo(47 * k, y0); g.lineTo(47 * k, y0 + dir * 14 * k); g.stroke();
      g.beginPath(); g.arc(25 * k, y0 + dir * 5.25 * k, 23.75 * k, end ? Math.PI + 0.38 : 0.38, end ? Math.PI * 2 - 0.38 : Math.PI - 0.38); g.stroke();
    }
  });
}
