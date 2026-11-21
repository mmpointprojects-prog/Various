import * as THREE from 'three';
import { EffectComposer } from 'three/examples/jsm/postprocessing/EffectComposer.js';
import { RenderPass } from 'three/examples/jsm/postprocessing/RenderPass.js';
import { UnrealBloomPass } from 'three/examples/jsm/postprocessing/UnrealBloomPass.js';
import { OutputPass } from 'three/examples/jsm/postprocessing/OutputPass.js';
import { clamp, hash, numAt, rad, ringPt, ringsOf, seatPos, secId, thetaOf, topOf } from '../geometry.ts';
import type { Level, Pick, Section, Setup, Venue } from '../types.ts';
import { ribbonTexture, scoreboardTexture, stageScreenTexture, suiteTexture, surfaceTexture } from './textures.ts';

export type Quality = 'enhanced' | 'basic';

// One shared WebGL renderer draws every seat view in turn, then each view is copied into its card's
// 2D canvas. Four live WebGL contexts would be heavier and hit browser context limits on phones.
// Light intensities are multiplied by PI because three.js uses physical light units.
const PI = Math.PI;

export class VenueScene {
  private renderer: THREE.WebGLRenderer;
  private scene = new THREE.Scene();
  private camera = new THREE.PerspectiveCamera(55, 1.6, 0.3, 1000);
  private composer: EffectComposer;
  private renderPass: RenderPass;
  private bloom: UnrealBloomPass;
  private ambient = new THREE.AmbientLight(0x5a6580, 0.55 * PI);
  private hemi = new THREE.HemisphereLight(0x8a96b8, 0x0d0d12, 0.35 * PI);
  private floorMesh!: THREE.Mesh;
  private groups: Record<'bowl' | 'stage' | 'crowd' | 'detail' | 'static', THREE.Group | null> = { bowl: null, stage: null, crowd: null, detail: null, static: null };
  private venue!: Venue;
  private setup!: Setup;
  private sections: Section[] = [];
  private quality: Quality = 'enhanced';

  constructor() {
    this.renderer = new THREE.WebGLRenderer({ antialias: true, preserveDrawingBuffer: true });
    this.renderer.setPixelRatio(1);
    this.scene.background = new THREE.Color(0x06070b);
    this.scene.fog = new THREE.Fog(0x06070b, 260, 720);
    this.scene.add(this.ambient, this.hemi);
    this.composer = new EffectComposer(this.renderer);
    this.renderPass = new RenderPass(this.scene, this.camera);
    this.bloom = new UnrealBloomPass(new THREE.Vector2(640, 400), 0.7, 0.5, 0.78);
    this.composer.addPass(this.renderPass);
    this.composer.addPass(this.bloom);
    this.composer.addPass(new OutputPass());
  }

  dispose() { this.renderer.dispose(); this.composer.dispose(); }

  private get enhanced() { return this.quality === 'enhanced'; }
  private replace(key: keyof VenueScene['groups'], g: THREE.Group | null) {
    const old = this.groups[key];
    if (old) { this.scene.remove(old); disposeTree(old); }
    this.groups[key] = g;
    if (g) this.scene.add(g);
  }

  /** Rebuild everything that depends on the venue, setup or quality. Call setPicks afterwards. */
  load(venue: Venue, setup: Setup, sections: Section[], quality: Quality) {
    const venueChanged = this.venue !== venue;
    this.venue = venue; this.setup = setup; this.sections = sections; this.quality = quality;
    if (venueChanged || !this.groups.static) this.buildStatic();
    this.buildBowl();
    this.buildStage();
    this.buildDetail();
  }

  /** The crowd leaves the chosen seats empty so the camera isn't inside someone's head. */
  setPicks(picks: Pick[]) { this.buildCrowd(picks); }

  render(pick: Pick, canvas: HTMLCanvasElement, zoom: 1 | 2) {
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    const w = Math.max(320, Math.min(1400, Math.round(canvas.clientWidth * dpr))), h = Math.round(w * 0.625);
    if (canvas.width !== w || canvas.height !== h) { canvas.width = w; canvas.height = h; }
    const sec = this.sections.find((s) => s.id === pick.sec);
    if (!sec) return;
    const p = seatPos(sec, pick.row), T = this.setup.target;
    const dx = T.x - p.x, dy = T.y - p.y, dz = T.z - p.z;
    this.camera.position.set(p.x, p.y, p.z);
    this.camera.rotation.set(clamp(Math.atan2(dy, Math.hypot(dx, dz)) + pick.pitch, -1.3, 1.3), Math.atan2(-dx, -dz) + pick.yaw, 0, 'YXZ');
    this.camera.fov = zoom === 2 ? 28 : 55;
    this.camera.aspect = w / h;
    this.camera.updateProjectionMatrix();
    this.renderer.setSize(w, h, false);
    const sport = !!this.setup.sport;
    if (this.enhanced) {
      this.bloom.strength = sport ? 0.28 : 0.7; this.bloom.threshold = sport ? 0.9 : 0.78; this.bloom.radius = sport ? 0.35 : 0.5;
      this.composer.setSize(w, h);
      this.composer.render();
    } else {
      this.renderer.render(this.scene, this.camera);
    }
    canvas.getContext('2d')!.drawImage(this.renderer.domElement, 0, 0, w, h);
  }

  // ---------- building ----------

  private buildStatic() {
    const g = new THREE.Group(), L = this.venue.levels.lower, shape = new THREE.Shape();
    for (let i = 0; i <= 96; i++) {
      const p = ringPt(L, 0, (i / 96) * Math.PI * 2);
      if (i) shape.lineTo(p[0], -p[1]); else shape.moveTo(p[0], -p[1]);
    }
    this.floorMesh = new THREE.Mesh(new THREE.ShapeGeometry(shape), new THREE.MeshLambertMaterial({ color: 0x1b1d24 }));
    this.floorMesh.rotation.x = -Math.PI / 2;
    g.add(this.floorMesh);
    const ceil = new THREE.Mesh(new THREE.CircleGeometry(280, 64), new THREE.MeshLambertMaterial({ color: 0x0b0c12, side: THREE.DoubleSide }));
    ceil.rotation.x = Math.PI / 2; ceil.position.y = this.venue.ceiling;
    g.add(ceil);
    this.replace('static', g);
  }

  private buildBowl() {
    const rings = ringsOf(this.venue.levels), closed = new Set(this.setup.closed), enh = this.enhanced;
    const pos: number[] = [], col: number[] = [], SEG = 216;
    const C = (h: number) => new THREE.Color(h);
    const push = (q: number[][], c: THREE.Color) => {
      const [a, b, cc, d] = q;
      pos.push(...a, ...b, ...cc, ...a, ...cc, ...d);
      for (let k = 0; k < 6; k++) col.push(c.r, c.g, c.b);
    };
    const palette = { lower: [C(0x7a1f2b), C(0x681a25)], middle: [C(0x5b3a8a), C(0x4c2f75)], upper: [C(0x2a3f86), C(0x223672)] };
    const aisle = C(0x3a3a44), drape = C(0x0a0a0d), dasher = C(0x24252d), suite = C(0x3d4560), wall = C(0x14151b), conc = C(0x2a2b33);
    const concrete = C(0x33343b), stair = C(0x4a4b53);
    rings.forEach((L, ri) => {
      const below = ri > 0 ? topOf(rings[ri - 1]) : 0, next = rings[ri + 1];
      const step = 360 / L.count;
      for (let s = 0; s < SEG; s++) {
        const d0 = (s / SEG) * 360, d1 = ((s + 1) / SEG) * 360, t0 = rad(d0), t1 = rad(d1);
        const num = numAt(L, (d0 + d1) / 2);
        const v0 = (d0 - 180) / step + L.centerNum, v1 = (d1 - 180) / step + L.centerNum;
        const isAisle = Math.floor(v0 + 0.5) !== Math.floor(v1 + 0.5);
        const blocked = closed.has(secId(L, num));
        for (let r = 1; r <= L.rows; r++) {
          const fa = (r - 1) / L.rows, fb = r / L.rows, y = L.y0 + (r - 1) * L.rise;
          const i0 = ringPt(L, fa, t0), i1 = ringPt(L, fa, t1), o0 = ringPt(L, fb, t0), o1 = ringPt(L, fb, t1);
          let tc = blocked ? drape : isAisle ? (enh ? stair : aisle) : enh ? concrete : palette[L.key][num % 2];
          if (!blocked && !isAisle && r % 2) tc = tc.clone().multiplyScalar(0.86);
          push([[i0[0], y, i0[1]], [o0[0], y, o0[1]], [o1[0], y, o1[1]], [i1[0], y, i1[1]]], tc);
          const yLow = r === 1 ? below : y - L.rise;
          const rc = r === 1 ? (ri === 0 ? dasher : enh ? wall : suite) : blocked ? drape : tc.clone().multiplyScalar(enh ? 0.62 : 0.5);
          push([[i0[0], yLow, i0[1]], [i1[0], yLow, i1[1]], [i1[0], y, i1[1]], [i0[0], y, i0[1]]], rc);
        }
        if (next) {
          const top = topOf(L), a0 = ringPt(L, 1, t0), a1 = ringPt(L, 1, t1), b0 = ringPt(next, 0, t0), b1 = ringPt(next, 0, t1);
          push([[a0[0], top, a0[1]], [b0[0], top, b0[1]], [b1[0], top, b1[1]], [a1[0], top, a1[1]]], conc);
        } else {
          const top = L.y0 + (L.rows - 1) * L.rise, a0 = ringPt(L, 1, t0), a1 = ringPt(L, 1, t1), ce = this.venue.ceiling;
          push([[a0[0], top, a0[1]], [a1[0], top, a1[1]], [a1[0], ce, a1[1]], [a0[0], ce, a0[1]]], wall);
        }
      }
    });
    const geo = new THREE.BufferGeometry();
    geo.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3));
    geo.setAttribute('color', new THREE.Float32BufferAttribute(col, 3));
    geo.computeVertexNormals();
    const g = new THREE.Group();
    g.add(new THREE.Mesh(geo, new THREE.MeshLambertMaterial({ vertexColors: true, side: THREE.DoubleSide })));
    this.replace('bowl', g);
  }

  private buildStage() {
    const G = new THREE.Group(), sport = this.setup.sport, enh = this.enhanced;
    const scene = this.scene, fog = scene.fog as THREE.Fog;
    this.ambient.intensity = (sport ? (enh ? 0.65 : 0.8) : enh ? 0.42 : 0.55) * PI;
    this.hemi.intensity = (sport ? (enh ? 0.4 : 0.5) : enh ? 0.25 : 0.35) * PI;
    const bg = sport ? 0x0d0f15 : enh ? 0x0b0a14 : 0x06070b;
    (scene.background as THREE.Color).setHex(bg);
    fog.color.setHex(bg); fog.near = !sport && enh ? 90 : 260; fog.far = !sport && enh ? 560 : 720;
    (this.floorMesh.material as THREE.MeshLambertMaterial).color.setHex(sport === 'basketball' ? 0x2a2f3a : 0x1b1d24);

    const box = (w: number, h: number, d: number, x: number, y: number, z: number, color: number, phong = false) => {
      const m = new THREE.Mesh(new THREE.BoxGeometry(w, h, d), phong ? new THREE.MeshPhongMaterial({ color, shininess: 20 }) : new THREE.MeshLambertMaterial({ color }));
      m.position.set(x, y, z); G.add(m); return m;
    };
    if (sport) this.buildSport(G, sport, box);
    else this.buildEndStage(G, box);
    this.replace('stage', G);
  }

  private buildEndStage(G: THREE.Group, box: BoxFn) {
    const F = this.setup.front!.z, o = (z: number) => F + z, enh = this.enhanced;
    const tex = stageScreenTexture();
    const screen = (w: number, h: number, x: number, y: number, z: number, ry: number) => {
      const m = new THREE.Mesh(new THREE.PlaneGeometry(w, h), new THREE.MeshBasicMaterial({ map: tex, color: enh ? 0xb8b8b8 : 0xffffff, side: THREE.DoubleSide }));
      m.position.set(x, y, z); m.rotation.y = ry; G.add(m);
    };
    const person = (x: number, z: number, y0: number, color: number) => {
      const b = new THREE.Mesh(new THREE.CylinderGeometry(0.8, 0.7, 4.4, 12), new THREE.MeshPhongMaterial({ color }));
      b.position.set(x, y0 + 2.2, z); G.add(b);
      const h = new THREE.Mesh(new THREE.SphereGeometry(0.6, 14, 10), new THREE.MeshPhongMaterial({ color: 0xd9b38c }));
      h.position.set(x, y0 + 5, z); G.add(h);
    };
    const beam = (x: number, z: number, top: number, color: number) => {
      const hgt = top - 6;
      const cone = new THREE.Mesh(new THREE.ConeGeometry(6, hgt, 24, 1, true), additive(color, 0.09));
      cone.position.set(x, 6 + hgt / 2, z); G.add(cone);
      const pool = new THREE.Mesh(new THREE.CircleGeometry(6, 28), additive(color, 0.28));
      pool.rotation.x = -Math.PI / 2; pool.position.set(x, 6.06, z); G.add(pool);
      const sl = new THREE.SpotLight(color, 1.3 * PI, 90, 0.38, 0.6, 0);
      sl.position.set(x, top, z); sl.target.position.set(x, 6, z); G.add(sl, sl.target);
    };
    const warm = 0xffd9a0, cool = 0x9fd0ff, pink = 0xff8fb0;
    box(56, 6, 26, 0, 3, o(-13), 0x26252c, true);
    box(56, 0.4, 0.4, 0, 6.1, F, 0xf5b841);
    box(14, 3, 9, 0, 7.5, o(-20), 0x1e1d24, true);
    box(60, 4, 1, 0, 2, o(2.5), 0x2a2a31);
    screen(50, 26, 0, 23, o(-25), 0);
    screen(18, 10, -42, 34, o(-6), 0.35); screen(18, 10, 42, 34, o(-6), -0.35);
    if (!enh) { box(4.5, 22, 4.5, -31, 36, o(1), 0x111115); box(4.5, 22, 4.5, 31, 36, o(1), 0x111115); }
    box(62, 1.2, 1.2, 0, 44, o(-1), 0x3a3a42); box(62, 1.2, 1.2, 0, 44, o(-23), 0x3a3a42);
    box(1.2, 1.2, 22, -31, 44, o(-12), 0x3a3a42); box(1.2, 1.2, 22, 31, 44, o(-12), 0x3a3a42);
    ([[-22, warm], [-11, cool], [0, pink], [11, cool], [22, warm]] as const).forEach(([x, c]) => beam(x, o(-11), 44, c));
    person(-13, o(-8), 6, 0x2b2b35); person(0, o(-4), 6, 0x3a2230); person(13, o(-8), 6, 0x2b2b35); person(0, o(-20), 9, 0x22222a);
    const wash = new THREE.DirectionalLight(0xffe6c8, 0.55 * PI);
    wash.position.set(0, 40, 30); wash.target.position.set(0, 6, F - 12); G.add(wash, wash.target);

    if (!enh) return;
    // curved line-array speaker hangs
    for (const sd of [-1, 1]) for (let i = 0; i < 10; i++) {
      const m = box(4.4, 1.55, 3.8, sd * 31, 47 - i * 1.62, F + 1 + i * 0.18, 0x15161b); m.rotation.x = i * 0.035;
    }
    // LED edge strips on the stage and drum riser
    const led = (w: number, x: number, y: number, z: number, c: number) => {
      const m = new THREE.Mesh(new THREE.BoxGeometry(w, 0.35, 0.35), new THREE.MeshBasicMaterial({ color: c })); m.position.set(x, y, z); G.add(m);
    };
    led(56, 0, 5.6, F + 0.2, 0x3ad7ff); led(14, 0, 9.1, F - 15.4, 0xff3aa8); led(56, 0, 0.3, F + 0.3, 0x7a5cff);
    // moving-head beams sweeping into the haze
    const cols = [0xff4fb0, 0x46c8ff, 0xffffff, 0x9b6bff];
    for (let i = 0; i < 12; i++) {
      const x = -28 + i * 5.1, side = i < 6 ? -1 : 1;
      beamFrom(G, [x, 44, F - 4], [x + side * (30 + i * 4), 6, F + 60 + ((i * 37) % 40)], cols[i % 4], 2.4, 0.05);
    }
    for (let i = 0; i < 6; i++) beamFrom(G, [-25 + i * 10, 2, F - 22], [-60 + i * 24, 120, F - 60], cols[(i + 1) % 4], 3, 0.04);
  }

  private buildSport(G: THREE.Group, sport: 'basketball' | 'hockey', box: BoxFn) {
    const L = this.venue.levels.lower, hockey = sport === 'hockey', enh = this.enhanced;
    const tex = surfaceTexture(sport);
    if (hockey) {
      const shape = new THREE.Shape();
      for (let i = 0; i <= 120; i++) {
        const p = ringPt(L, 0, (i / 120) * Math.PI * 2), x = p[0] * 0.985, y = -p[1] * 0.985;
        if (i) shape.lineTo(x, y); else shape.moveTo(x, y);
      }
      tex.repeat.set(1 / 92, 1 / 208); tex.offset.set(0.5, 0.5);
      const ice = new THREE.Mesh(new THREE.ShapeGeometry(shape), new THREE.MeshBasicMaterial({ map: tex, color: 0xd8dee6 }));
      ice.rotation.x = -Math.PI / 2; ice.position.y = 0.05; G.add(ice);
      G.add(ringBand(L, 0, 0, 3.5, new THREE.MeshLambertMaterial({ color: 0xe9edf2, side: THREE.DoubleSide }), 0, 0.985));
      G.add(ringBand(L, 0, 3.5, 12, new THREE.MeshBasicMaterial({ color: 0xbfd6ea, transparent: true, opacity: 0.13, depthWrite: false, side: THREE.DoubleSide }), 0, 0.985));
      for (const z of [-93, 93]) {
        const s = Math.sign(z);
        box(6, 0.3, 0.3, 0, 4, z, 0xc2383f); box(0.3, 4, 0.3, -3, 2, z, 0xc2383f); box(0.3, 4, 0.3, 3, 2, z, 0xc2383f);
        box(6, 0.2, 3.3, 0, 0.1, z + s * 1.6, 0xdddddd);
      }
      const team = (x: number, z: number, col: number) => { box(1.6, 4.6, 1.1, x, 2.6, z, col); box(0.9, 0.9, 0.9, x, 5.4, z, 0x222222); };
      [[0, -6], [-14, -30], [14, -22], [-24, 10], [20, 14], [0, -86]].forEach(([x, z]) => team(x, z, 0x1f4fae));
      [[3, 4], [16, -8], [-10, 18], [-26, -14], [10, 32], [0, 86]].forEach(([x, z]) => team(x, z, 0xf2f2f2));
    } else {
      const mat = enh
        ? new THREE.MeshStandardMaterial({ map: tex, color: 0xb8b0a4, emissiveMap: tex, emissive: 0xffffff, emissiveIntensity: 0.22, roughness: 0.3, metalness: 0.05 })
        : new THREE.MeshBasicMaterial({ map: tex, color: 0xe4e4e4 });
      const court = new THREE.Mesh(new THREE.PlaneGeometry(50, 94), mat);
      court.rotation.x = -Math.PI / 2; court.position.y = 0.06; G.add(court);
      for (const z of [-47, 47]) {
        const s = Math.sign(z);
        box(3, 3, 3, 0, 1.5, z + s * 7, 0x30343e); box(0.8, 11, 0.8, 0, 7, z + s * 6, 0x30343e);
        box(0.5, 0.5, 10, 0, 12.6, z + s * 1, 0x30343e);
        box(6, 3.5, 0.2, 0, 11.5, z - s * 4, 0xf4f6fa);
        const rim = new THREE.Mesh(new THREE.TorusGeometry(0.75, 0.06, 8, 20), new THREE.MeshLambertMaterial({ color: 0xff6a1a }));
        rim.rotation.x = Math.PI / 2; rim.position.set(0, 10, z - s * 5.25); G.add(rim);
      }
      const player = (x: number, z: number, col: number) => {
        if (enh) {
          const b = new THREE.Mesh(new THREE.CylinderGeometry(0.75, 0.6, 5, 12), new THREE.MeshStandardMaterial({ color: col, roughness: 0.6 }));
          b.position.set(x, 3.2, z); G.add(b);
          const h = new THREE.Mesh(new THREE.SphereGeometry(0.5, 14, 10), new THREE.MeshStandardMaterial({ color: 0x5a3a28, roughness: 0.7 }));
          h.position.set(x, 6.2, z); G.add(h);
        } else { box(1.5, 5, 1, x, 3.2, z, col); box(0.9, 0.9, 0.9, x, 6.2, z, 0x5a3a28); }
      };
      [[0, -30], [-12, -36], [12, -38], [-18, -42], [6, -44]].forEach(([x, z]) => player(x, z, 0x1f4fae));
      [[3, -27], [-9, -33], [14, -34], [-15, -39], [8, -41]].forEach(([x, z]) => player(x, z, 0xf2f2f2));
      if (enh) {
        const dark = new THREE.MeshStandardMaterial({ color: 0x14151a, roughness: 0.5, metalness: 0.3 });
        const rib = new THREE.MeshBasicMaterial({ map: ribbonTexture(true) });
        const add = (w: number, h: number, d: number, x: number, y: number, z: number, m: THREE.Material | THREE.Material[]) => {
          const mesh = new THREE.Mesh(new THREE.BoxGeometry(w, h, d), m); mesh.position.set(x, y, z); G.add(mesh);
        };
        add(1.6, 3, 24, -26.2, 1.5, 0, dark); add(0.1, 1.6, 23, -25.3, 2.2, 0, rib); // scorer's table, LED front
        for (const z of [-50.5, 50.5]) { const m = [dark, dark, dark, dark, dark, dark] as THREE.Material[]; m[z < 0 ? 4 : 5] = rib; add(40, 2.4, 0.3, 0, 1.2, z, m); }
        for (const z of [-47, 47]) add(3, 1.4, 1, 0, 14.6, z - Math.sign(z) * 4, new THREE.MeshBasicMaterial({ color: 0xff3b30 })); // shot clocks
      }
    }
    // center-hung scoreboard
    const sb = scoreboardTexture(), sbY = hockey ? 70 : 64;
    box(26, 16, 26, 0, sbY, 0, 0x101219);
    const face = (x: number, z: number, ry: number) => {
      const m = new THREE.Mesh(new THREE.PlaneGeometry(24, 13.5), new THREE.MeshBasicMaterial({ map: sb })); m.position.set(x, sbY, z); m.rotation.y = ry; G.add(m);
    };
    face(0, 13.1, 0); face(0, -13.1, Math.PI); face(13.1, 0, Math.PI / 2); face(-13.1, 0, -Math.PI / 2);
    const top = new THREE.DirectionalLight(0xffffff, 0.35 * PI); top.position.set(0, 120, 0); G.add(top, top.target);
    const key = new THREE.DirectionalLight(0xfff2e0, 0.2 * PI); key.position.set(80, 90, 60); G.add(key);
  }

  /** Enhanced-only venue detail: individual seats, railings, glass, suites, LED ribbon, aisle lights, roof. */
  private buildDetail() {
    if (!this.enhanced) { this.replace('detail', null); return; }
    const G = new THREE.Group(), sport = !!this.setup.sport, rings = ringsOf(this.venue.levels);
    const Lw = rings[0], Up = rings[rings.length - 1], lowerTop = topOf(Lw), dmy = new THREE.Object3D();

    const slots: [number, number, number, number][] = [];
    for (const sec of this.sections) {
      if (sec.kind === 'floor' || sec.blocked) continue;
      const L = sec.level, t = rad(sec.c), half = rad(180 / L.count);
      for (let r = 1; r <= L.rows; r++) {
        const f = (r - 0.5) / L.rows, y = L.y0 + (r - 1) * L.rise, dt = seatStep(L, f, t), K = Math.floor((half - dt * 0.8) / dt);
        for (let k = -K; k <= K; k++) {
          const tt = t + k * dt, q = ringPt(L, f, tt), a = ringPt(L, f, tt - 0.003), b = ringPt(L, f, tt + 0.003);
          let nx = b[1] - a[1], nz = -(b[0] - a[0]);
          if (nx * -q[0] + nz * -q[1] < 0) { nx = -nx; nz = -nz; } // face into the bowl
          slots.push([q[0], y, q[1], Math.atan2(nx, nz)]);
        }
      }
    }
    const panG = new THREE.BoxGeometry(1.55, 0.28, 1.2); panG.translate(0, 1.45, 0.1);
    const backG = new THREE.BoxGeometry(1.55, 1.55, 0.2); backG.translate(0, 2.2, -0.55);
    const seatM = new THREE.MeshStandardMaterial({ color: 0xffffff, roughness: 0.55, metalness: 0.05 });
    const pans = new THREE.InstancedMesh(panG, seatM, slots.length), backs = new THREE.InstancedMesh(backG, seatM, slots.length);
    const colors = this.venue.seatColors.map((c) => new THREE.Color(c));
    slots.forEach(([x, y, z, ry], i) => {
      dmy.position.set(x, y, z); dmy.rotation.set(0, ry, 0); dmy.updateMatrix();
      pans.setMatrixAt(i, dmy.matrix); backs.setMatrixAt(i, dmy.matrix);
      const c = colors[i % colors.length]; pans.setColorAt(i, c); backs.setColorAt(i, c);
    });
    G.add(pans, backs);

    const metal = new THREE.MeshStandardMaterial({ color: 0xaab3c2, roughness: 0.3, metalness: 0.85, side: THREE.DoubleSide });
    const glass = new THREE.MeshBasicMaterial({ color: 0xa9c4e0, transparent: true, opacity: 0.13, depthWrite: false, side: THREE.DoubleSide });
    G.add(ringBand(Lw, 0, Lw.y0 - 0.05, Lw.y0 + 0.2, metal));
    G.add(ringBand(Lw, 1, lowerTop, lowerTop + 2.8, glass), ringBand(Lw, 1, lowerTop + 2.8, lowerTop + 3, metal));
    const suiteM = new THREE.MeshBasicMaterial({ map: suiteTexture(), side: THREE.DoubleSide });
    rings.slice(1).forEach((R, i) => {
      const prevTop = topOf(rings[i]);
      G.add(ringBand(R, 0, R.y0 - 0.2, R.y0 + 2.7, glass), ringBand(R, 0, R.y0 + 2.7, R.y0 + 2.9, metal));
      const suiteTop = Math.min(prevTop + 11, R.y0 - (R === Up ? 5.5 : 1));
      if (suiteTop - (prevTop + 4) > 2) G.add(ringBand(R, 0, prevTop + 4, suiteTop, suiteM, 20, 0.994));
    });
    G.add(ringBand(Up, 0, Up.y0 - 4.8, Up.y0 - 1.4, new THREE.MeshBasicMaterial({ map: ribbonTexture(sport), side: THREE.DoubleSide }), 70, 0.992));

    const steps: [number, number, number][] = [];
    for (const L of rings) {
      const stp = 360 / L.count;
      for (let i = 0; i < L.count; i++) {
        const ang = rad(thetaOf(L, L.first + i) + stp / 2);
        for (let r = 1; r <= L.rows; r++) { const q = ringPt(L, (r - 0.5) / L.rows, ang); steps.push([q[0], L.y0 + (r - 1) * L.rise + 0.12, q[1]]); }
      }
    }
    const sl = new THREE.InstancedMesh(new THREE.BoxGeometry(0.35, 0.12, 0.35), new THREE.MeshBasicMaterial({ color: sport ? 0x9fb0d0 : 0x5c7cff }), steps.length);
    steps.forEach(([x, y, z], i) => { dmy.position.set(x, y, z); dmy.rotation.set(0, 0, 0); dmy.updateMatrix(); sl.setMatrixAt(i, dmy.matrix); });
    G.add(sl);

    const trussM = new THREE.MeshStandardMaterial({ color: 0x2b2d35, roughness: 0.7, metalness: 0.5 }), cy = this.venue.ceiling - 8;
    for (let x = -120; x <= 120; x += 40) { const b = new THREE.Mesh(new THREE.BoxGeometry(2.2, 3, 400), trussM); b.position.set(x, cy, 0); G.add(b); }
    for (let z = -180; z <= 180; z += 45) { const b = new THREE.Mesh(new THREE.BoxGeometry(300, 3, 2.2), trussM); b.position.set(0, cy, z); G.add(b); }
    const fixtures: [number, number][] = [];
    for (let x = -100; x <= 100; x += 20) for (let z = -140; z <= 140; z += 20) fixtures.push([x, z]);
    const hl = new THREE.InstancedMesh(new THREE.CylinderGeometry(1.3, 1.3, 0.5, 14), new THREE.MeshBasicMaterial({ color: sport ? 0xfff4e2 : 0x24242c }), fixtures.length);
    fixtures.forEach(([x, z], i) => { dmy.position.set(x, cy - 2, z); dmy.rotation.set(0, 0, 0); dmy.updateMatrix(); hl.setMatrixAt(i, dmy.matrix); });
    G.add(hl);
    this.replace('detail', G);
  }

  private buildCrowd(picks: Pick[]) {
    const excl = picks.map((p) => { const s = this.sections.find((x) => x.id === p.sec); return s ? seatPos(s, p.row) : null; }).filter(Boolean) as { x: number; y: number; z: number }[];
    const list: [number, number, number, number][] = [];
    let seed = 1;
    const add = (x: number, y: number, z: number) => {
      seed++;
      for (const e of excl) if (Math.abs(e.x - x) < 1 && Math.abs(e.z - z) < 1 && Math.abs(e.y - 0.2 - y) < 1.5) return;
      if (hash(seed) < 0.06) return; // a few empty seats
      list.push([x, y + (hash(seed * 1.7) - 0.5) * 0.5, z, seed]);
    };
    for (const sec of this.sections) {
      if (sec.blocked) continue;
      if (sec.kind === 'floor') {
        const alongZ = sec.front[0] === 'z', w = alongZ ? sec.x1 - sec.x0 : sec.z1 - sec.z0, sp = 1.9, K = Math.floor((w / 2 - 1) / sp);
        for (let r = 1; r <= sec.rows; r++) {
          const p = seatPos(sec, r), y = p.y - 0.5;
          for (let k = -K; k <= K; k++) { if (alongZ) add(p.x + k * sp, y, p.z); else add(p.x, y, p.z + k * sp); }
        }
      } else {
        const L = sec.level, t = rad(sec.c), half = rad(180 / L.count);
        for (let r = 1; r <= L.rows; r++) {
          const f = (r - 0.5) / L.rows, y = L.y0 + (r - 1) * L.rise + 5.0, dt = seatStep(L, f, t), K = Math.floor((half - dt * 0.8) / dt);
          for (let k = -K; k <= K; k++) { const q = ringPt(L, f, t + k * dt); add(q[0], y, q[1]); }
        }
      }
    }
    const enh = this.enhanced, sport = this.setup.sport, n = list.length, G = new THREE.Group();
    const heads = new THREE.InstancedMesh(enh ? new THREE.SphereGeometry(0.38, 12, 9) : new THREE.IcosahedronGeometry(0.42, 1), new THREE.MeshLambertMaterial({ color: 0xffffff }), n);
    const bodies = new THREE.InstancedMesh(enh ? new THREE.CylinderGeometry(0.5, 0.66, 2.6, 10) : new THREE.BoxGeometry(1.5, 2.2, 0.8), new THREE.MeshLambertMaterial({ color: 0xffffff }), n);
    // Seen from behind, so heads read as hair colors rather than faces.
    const hair = [0x16120f, 0x2a1d14, 0x3b2a1c, 0x0e0d0d, 0x5a4632, 0x6b6460, 0x24180f].map((c) => new THREE.Color(c));
    const shirts = (sport ? [0x98002e, 0x98002e, 0x111111, 0xd8d8d8, 0x111111, 0xc9831a, 0x2b2f3a] : [0x111114, 0x8a8a90, 0x2b2e36, 0x1e3254, 0x7a2a50, 0x16161a, 0x3d1d7a]).map((c) => new THREE.Color(c));
    const basicHead = [0x1d1a1f, 0x2a211c, 0x3a2c22, 0x14141a, 0x4a3a2a].map((c) => new THREE.Color(c));
    const basicBody = [0x1f2433, 0x2b2b2f, 0x3b1f2a, 0x16303a, 0x3a3a40, 0x4a3f28].map((c) => new THREE.Color(c));
    const dmy = new THREE.Object3D(), T = this.setup.target, phoneAt: [number, number, number, number][] = [];
    list.forEach(([x, y, z, s], i) => {
      const ry = Math.atan2(T.x - x, T.z - z);
      dmy.position.set(x, y, z); dmy.rotation.set(0, ry, 0); dmy.updateMatrix(); heads.setMatrixAt(i, dmy.matrix);
      dmy.position.y = y - (enh ? 1.7 : 1.6); dmy.updateMatrix(); bodies.setMatrixAt(i, dmy.matrix);
      const hc = enh ? hair : basicHead, bc = enh ? shirts : basicBody;
      heads.setColorAt(i, hc[Math.floor(hash(s * 3.1) * hc.length)]);
      bodies.setColorAt(i, bc[Math.floor(hash(s * 5.3) * bc.length)]);
      const near = excl.some((e) => Math.hypot(e.x - x, e.z - z) < 10 && Math.abs(e.y - y) < 6);
      if (enh && !sport && !near && hash(s * 7.7) < 0.06) phoneAt.push([x + Math.sin(ry) * 0.6, y + 1.1, z + Math.cos(ry) * 0.6, ry]);
    });
    G.add(heads, bodies);
    if (phoneAt.length) {
      const phones = new THREE.InstancedMesh(new THREE.PlaneGeometry(0.22, 0.4), new THREE.MeshBasicMaterial({ color: 0xc9d6ff, side: THREE.DoubleSide }), phoneAt.length);
      phoneAt.forEach(([x, y, z, ry], i) => { dmy.position.set(x, y, z); dmy.rotation.set(0, ry, 0); dmy.updateMatrix(); phones.setMatrixAt(i, dmy.matrix); });
      G.add(phones);
    }
    this.replace('crowd', G);
  }
}

type BoxFn = (w: number, h: number, d: number, x: number, y: number, z: number, color: number, phong?: boolean) => THREE.Mesh;

const additive = (color: number, opacity: number) =>
  new THREE.MeshBasicMaterial({ color, transparent: true, opacity, blending: THREE.AdditiveBlending, depthWrite: false, side: THREE.DoubleSide });

/** Angle step between neighboring seats (about 2 ft apart) at depth f of a level. */
function seatStep(L: Level, f: number, t: number) {
  const p0 = ringPt(L, f, t), p1 = ringPt(L, f, t + 0.001);
  return 2.0 / (Math.hypot(p1[0] - p0[0], p1[1] - p0[1]) / 0.001);
}

/** A vertical band that follows a level's ring (railings, glass, ribbon boards). */
function ringBand(L: Level, f: number, yA: number, yB: number, mat: THREE.Material, period = 0, inset = 1) {
  const N = 240, pos: number[] = [], uv: number[] = [], idx: number[] = [];
  let acc = 0, prev: number[] | null = null;
  for (let i = 0; i <= N; i++) {
    const p = ringPt(L, f, (i / N) * Math.PI * 2).map((v) => v * inset);
    if (prev) acc += Math.hypot(p[0] - prev[0], p[1] - prev[1]);
    prev = p;
    pos.push(p[0], yA, p[1], p[0], yB, p[1]);
    const u = period ? acc / period : i / N;
    uv.push(u, 0, u, 1);
  }
  for (let i = 0; i < N; i++) { const a = i * 2; idx.push(a, a + 2, a + 1, a + 1, a + 2, a + 3); }
  const g = new THREE.BufferGeometry();
  g.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3));
  g.setAttribute('uv', new THREE.Float32BufferAttribute(uv, 2));
  g.setIndex(idx);
  g.computeVertexNormals();
  return new THREE.Mesh(g, mat);
}

function beamFrom(G: THREE.Group, from: number[], to: number[], color: number, radius: number, opacity: number) {
  const dir = new THREE.Vector3(to[0] - from[0], to[1] - from[1], to[2] - from[2]), len = dir.length();
  dir.normalize();
  const g = new THREE.ConeGeometry(radius, len, 20, 1, true);
  g.translate(0, -len / 2, 0);
  const m = new THREE.Mesh(g, additive(color, opacity));
  m.position.set(from[0], from[1], from[2]);
  m.quaternion.setFromUnitVectors(new THREE.Vector3(0, -1, 0), dir);
  G.add(m);
}

function disposeTree(o: THREE.Object3D) {
  o.traverse((c) => {
    const m = c as THREE.Mesh;
    if (m.geometry) m.geometry.dispose();
    const mats = m.material ? (Array.isArray(m.material) ? m.material : [m.material]) : [];
    for (const mat of mats) { (mat as THREE.MeshBasicMaterial).map?.dispose(); mat.dispose(); }
  });
}
