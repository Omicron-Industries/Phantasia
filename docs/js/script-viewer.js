/**
 * Phantasia Online — Script Viewer
 *
 * Like viewer.js but for auto-generated machine scripts.
 * Pattern blocks are in local (0-based) coordinates; no placements.
 * Step visibility uses y=layer directly.
 */

import { MinecraftAssets }  from './mc-assets.js';
import { McBlockRenderer }  from './mc-block-renderer.js';
import { PhantasiaCamera, LerpType } from './phantasia-camera.js';

import * as THREE from 'three';

const TICKS_PER_SEC = 20;

const assets = new MinecraftAssets('assets/mc');
const texLoader = new THREE.TextureLoader();
assets.setTextureLoader(texLoader);

const renderer_gl = new THREE.WebGLRenderer({
  canvas: document.getElementById('three-canvas'),
  antialias: true,
  alpha: false,
});
renderer_gl.setPixelRatio(Math.min(devicePixelRatio, 2));
renderer_gl.setClearColor(0x060c14, 1);
renderer_gl.shadowMap.enabled = false;

const scene = new THREE.Scene();
scene.add(new THREE.AmbientLight(0xffffff, 0.7));
const dir1 = new THREE.DirectionalLight(0xffffff, 0.8);
dir1.position.set(5, 10, 7);
scene.add(dir1);
const dir2 = new THREE.DirectionalLight(0x8eceff, 0.3);
dir2.position.set(-5, 3, -7);
scene.add(dir2);

const canvas      = document.getElementById('three-canvas');
const camera      = new PhantasiaCamera(THREE, canvas);
const blockRenderer = new McBlockRenderer(THREE, assets);

let scriptData   = null;
let patternData  = null;
let stepList     = [];
let blockMeshes  = [];
let playing      = true;
let playbackTick = 0;
let playbackAccum = 0;
let playbackSpeed = 1;
let lastAppliedStepIdx = -1;
let lastFrameTime = null;
let scrubbing    = false;

// ── Step compilation ───────────────────────────────────────────────────────

function compileSteps(data) {
  if (!data || !data.steps) return [];
  return data.steps.map((s, i) => ({
    idx:      i,
    tick:     s.tick || 0,
    caption:  s.caption || null,
    show:     s.show || 'all',
    layer:    s.layer || 0,
    layerMin: s.layerMin || 0,
    layerMax: s.layerMax || 0,
    hideLayer: s.hideLayer ?? -1,
    positions: s.positions || [],
    hidePositions: s.hidePositions || [],
    camera:   s.camera || null,
  }));
}

function totalTicks() {
  if (!stepList.length) return 0;
  return stepList[stepList.length - 1].tick + 60;
}

function getActiveStepIdx(tick) {
  let idx = 0;
  for (let i = 0; i < stepList.length; i++) {
    if (stepList[i].tick <= tick) idx = i;
    else break;
  }
  return idx;
}

// ── Visibility ─────────────────────────────────────────────────────────────

function computeVisible(stepIdx) {
  if (!patternData || !stepList.length) return null;
  const step = stepList[stepIdx];
  if (!step) return null;

  const visible = new Set();
  for (const block of patternData) {
    const key = `${block.x},${block.y},${block.z}`;
    const show = step.show;
    const y = block.y;

    switch ((show || 'all').toLowerCase()) {
      case 'layer':
        if (y !== step.layer) continue;
        break;
      case 'layers':
        if (y < step.layerMin || y > step.layerMax) continue;
        break;
      case 'pos':
        if (!step.positions.some(p => p[0] === block.x && p[1] === y && p[2] === block.z)) continue;
        break;
      default:
        break;
    }

    if (step.hideLayer >= 0 && y === step.hideLayer) continue;
    if (step.hidePositions.some(p => p[0] === block.x && p[1] === y && p[2] === block.z)) continue;

    visible.add(key);
  }
  return visible;
}

let _raycastCache = null;
function invalidateRaycastCache() { _raycastCache = null; }
function getRaycastMeshes() {
  if (_raycastCache) return _raycastCache;
  _raycastCache = blockMeshes
    .filter(b => b.mesh.visible)
    .flatMap(b => { const a = []; b.mesh.traverse(c => { if (c.isMesh) a.push(c); }); return a.map(m => ({ mesh: m, block: b })); });
  return _raycastCache;
}

function applyVisibility(visible) {
  for (const { mesh, pos } of blockMeshes) {
    const key = `${pos.x},${pos.y},${pos.z}`;
    mesh.visible = !visible || visible.has(key);
  }
  invalidateRaycastCache();
}

// ── Scene building ─────────────────────────────────────────────────────────

async function buildScene() {
  for (const { mesh } of blockMeshes) scene.remove(mesh);
  blockMeshes = [];
  invalidateRaycastCache();
  if (!patternData) return;

  const posTypeMap = new Map();
  for (const b of patternData) posTypeMap.set(`${b.x},${b.y},${b.z}`, b.block);

  let minX = Infinity, maxX = -Infinity;
  let minY = Infinity, maxY = -Infinity;
  let minZ = Infinity, maxZ = -Infinity;
  for (const b of patternData) {
    if (b.x < minX) minX = b.x; if (b.x > maxX) maxX = b.x;
    if (b.y < minY) minY = b.y; if (b.y > maxY) maxY = b.y;
    if (b.z < minZ) minZ = b.z; if (b.z > maxZ) maxZ = b.z;
  }
  const cx = (minX + maxX) / 2;
  const cy = (minY + maxY) / 2;
  const cz = (minZ + maxZ) / 2;
  const span = Math.max(maxX - minX, maxY - minY, maxZ - minZ);
  camera.zoom = Math.max(8, span * 1.8);
  camera.setTarget(cx, cy, -cz);

  setLoading(true, 'Fetching assets…', `${patternData.length} blocks`);
  await assets.prefetchBlocks(patternData);

  setLoading(true, 'Building blocks…', `${patternData.length} blocks`);
  const PARALLEL = 20;
  for (let i = 0; i < patternData.length; i += PARALLEL) {
    const batch = patternData.slice(i, i + PARALLEL);
    const results = await Promise.all(batch.map(async block => {
      try {
        const [ns, blockId] = block.block.includes(':') ? block.block.split(':') : ['minecraft', block.block];
        const neighborChecker = (dx, dy, dz) =>
          posTypeMap.get(`${block.x+dx},${block.y+dy},${block.z+dz}`) === block.block;
        const obj = await blockRenderer.buildBlock(ns, blockId, block.props || {}, neighborChecker);
        obj.position.set(block.x - cx, block.y - cy, -(block.z - cz));
        return { obj, block };
      } catch { return null; }
    }));
    for (const r of results) {
      if (!r) continue;
      scene.add(r.obj);
      blockMeshes.push({ mesh: r.obj, pos: r.block });
    }
    setLoading(true, 'Building blocks…', `${Math.min(i + PARALLEL, patternData.length)} / ${patternData.length}`);
    await yieldFrame();
  }

  setLoading(false);
  if (stepList.length) {
    applyVisibility(computeVisible(0));
    applyCameraForStep(0, true);
  } else {
    applyVisibility(null);
  }
  buildShoppingList();
}

function applyCameraForStep(stepIdx, snap) {
  const step = stepList[stepIdx];
  if (!step || !step.camera) return;
  const c = step.camera;
  camera.scriptDrive(
    c.yaw   ?? camera.yaw,
    c.pitch ?? camera.pitch,
    c.zoom > 0 ? c.zoom : camera.zoom,
    snap ? LerpType.SNAP : (c.lerpType || LerpType.SNAP),
    snap ? 0 : (c.lerpTicks || 0)
  );
}

// ── Loading UI ─────────────────────────────────────────────────────────────

const overlay = document.getElementById('loading-overlay');
const loadTxt = document.getElementById('loading-text');
const loadSub = document.getElementById('loading-sub');
function setLoading(show, text = '', sub = '') {
  overlay.style.display = show ? 'flex' : 'none';
  if (text) loadTxt.textContent = text;
  if (sub)  loadSub.textContent = sub;
}
function yieldFrame() { return new Promise(r => setTimeout(r, 0)); }

// ── Timeline UI ───────────────────────────────────────────────────────────

const tlPlay  = document.getElementById('tl-play');
const tlRail  = document.getElementById('tl-rail');
const tlFill  = document.getElementById('tl-fill');
const tlThumb = document.getElementById('tl-thumb');
const tlTime  = document.getElementById('tl-time');
const tlSpeed = document.getElementById('tl-speed');
const tlTrack = document.getElementById('tl-track');

tlPlay.addEventListener('click', () => {
  if (playbackTick >= totalTicks()) { playbackTick = 0; playbackAccum = 0; }
  playing = !playing;
  tlPlay.textContent = playing ? '⏸' : '▶';
});
tlSpeed.addEventListener('click', () => {
  playbackSpeed = playbackSpeed === 1 ? 2 : playbackSpeed === 2 ? 0.5 : 1;
  tlSpeed.textContent = playbackSpeed === 1 ? '1×' : playbackSpeed === 2 ? '2×' : '½×';
});

tlTrack.addEventListener('mousedown', e => { scrubbing = true; doScrub(e); });
window.addEventListener('mousemove',  e => { if (scrubbing) doScrub(e); });
window.addEventListener('mouseup',    () => { scrubbing = false; });

function doScrub(e) {
  const rect = tlRail.getBoundingClientRect();
  const t = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width));
  playbackTick = Math.round(t * totalTicks());
  playbackAccum = 0;
  updateTimelineUI();
  const idx = getActiveStepIdx(playbackTick);
  applyStep(idx, true);
}

function updateTimelineUI() {
  const total = totalTicks();
  const frac  = total > 0 ? playbackTick / total : 0;
  const pct   = (frac * 100).toFixed(2) + '%';
  tlFill.style.width = pct;
  tlThumb.style.left = pct;
  const secs = Math.floor(playbackTick / TICKS_PER_SEC);
  tlTime.textContent = `${Math.floor(secs / 60)}:${String(secs % 60).padStart(2, '0')}`;
}

function buildStepMarkers() {
  tlRail.querySelectorAll('.tl-step-marker').forEach(el => el.remove());
  const total = totalTicks();
  if (!total) return;
  for (const s of stepList) {
    const el = document.createElement('div');
    el.className = 'tl-step-marker';
    el.style.left = (s.tick / total * 100) + '%';
    el.title = s.caption || `Step ${s.idx + 1}`;
    tlRail.appendChild(el);
  }
}

// ── Step application ───────────────────────────────────────────────────────

function applyStep(idx, snap) {
  if (!stepList[idx]) return;
  const step = stepList[idx];
  applyVisibility(computeVisible(idx));
  updateStepPanel(step);
  updateStepMarkers(idx);
  const captionStrip = document.getElementById('caption-strip');
  const captionText  = document.getElementById('caption-text');
  if (step.caption) {
    captionText.textContent = step.caption;
    captionStrip.style.display = 'flex';
  } else {
    captionStrip.style.display = 'none';
  }
  if (step.camera) applyCameraForStep(idx, snap);
}

function updateStepPanel(step) {
  const stepCap  = document.getElementById('step-caption');
  const stepInfo = document.getElementById('step-info');
  if (stepCap) stepCap.textContent  = step.caption || '';
  if (stepInfo) stepInfo.textContent = `Step ${step.idx + 1} / ${stepList.length}`;
}

function updateStepMarkers(activeIdx) {
  tlRail.querySelectorAll('.tl-step-marker').forEach((el, i) => {
    el.classList.toggle('active', i === activeIdx);
  });
}

// ── Shopping list ──────────────────────────────────────────────────────────

function buildShoppingList() {
  const el = document.getElementById('shopping-list');
  el.innerHTML = '';
  if (!patternData) return;
  const counts = {};
  for (const b of patternData) {
    counts[b.block] = (counts[b.block] || 0) + 1;
  }
  const sorted = Object.entries(counts).sort((a, b) => b[1] - a[1]);
  for (const [blockId, count] of sorted) {
    const [ns, id] = blockId.includes(':') ? blockId.split(':') : ['minecraft', blockId];
    const name = id.split('_').map(w => w[0].toUpperCase() + w.slice(1)).join(' ');
    const row = document.createElement('div');
    row.className = 'shop-item';
    row.innerHTML = `
      <span class="shop-count">×${count}</span>
      <img class="shop-icon" src="assets/mc/assets/${ns}/textures/item/${id}.png" alt="" onerror="this.style.display='none'">
      <span class="shop-name" title="${blockId}">${name}</span>`;
    el.appendChild(row);
  }
}

// Collapsible sections
document.querySelectorAll('.panel-section-header').forEach(h => {
  h.addEventListener('click', () => {
    h.closest('.panel-section').classList.toggle('collapsed');
    h.querySelector('.panel-section-toggle').textContent =
      h.closest('.panel-section').classList.contains('collapsed') ? '▶' : '▼';
  });
});

// ── Block hover tooltip ────────────────────────────────────────────────────

const tooltip   = document.getElementById('block-tooltip');
const raycaster = new THREE.Raycaster();
const mouse     = new THREE.Vector2();
canvas.addEventListener('mousemove', e => {
  const rect = canvas.getBoundingClientRect();
  mouse.x =  ((e.clientX - rect.left) / rect.width)  * 2 - 1;
  mouse.y = -((e.clientY - rect.top)  / rect.height) * 2 + 1;
  raycaster.setFromCamera(mouse, camera.cam);
  const meshes = getRaycastMeshes();
  const hits = raycaster.intersectObjects(meshes.map(m => m.mesh), false);
  if (hits.length) {
    const hit = meshes.find(m => m.mesh === hits[0].object);
    if (hit) {
      const block = hit.block.pos;
      tooltip.textContent = block.block + (block.props && Object.keys(block.props).length
        ? '[' + Object.entries(block.props).map(([k, v]) => `${k}=${v}`).join(',') + ']' : '');
      tooltip.style.display = 'block';
      tooltip.style.left = (e.clientX - rect.left + 14) + 'px';
      tooltip.style.top  = (e.clientY - rect.top  - 8)  + 'px';
      return;
    }
  }
  tooltip.style.display = 'none';
});
canvas.addEventListener('mouseleave', () => { tooltip.style.display = 'none'; });

// ── Render loop ────────────────────────────────────────────────────────────

function animate(ts) {
  requestAnimationFrame(animate);
  const dt = lastFrameTime !== null ? (ts - lastFrameTime) / 1000 : 0;
  lastFrameTime = ts;

  if (playing && !scrubbing && totalTicks() > 0) {
    playbackAccum += dt * TICKS_PER_SEC * playbackSpeed;
    while (playbackAccum >= 1) { playbackAccum -= 1; playbackTick++; }
    if (playbackTick >= totalTicks()) {
      playbackTick = totalTicks();
      playing = false;
      tlPlay.textContent = '▶';
    }
    const stepIdx = getActiveStepIdx(playbackTick);
    if (stepIdx !== lastAppliedStepIdx) {
      lastAppliedStepIdx = stepIdx;
      applyStep(stepIdx, false);
    }
  }

  updateTimelineUI();
  camera.tick(dt);

  const w = canvas.clientWidth;
  const h = canvas.clientHeight;
  if (renderer_gl.domElement.width !== w * devicePixelRatio ||
      renderer_gl.domElement.height !== h * devicePixelRatio) {
    renderer_gl.setSize(w, h, false);
    camera.onResize(w, h);
  }
  renderer_gl.render(scene, camera.cam);
}

// ── Init ───────────────────────────────────────────────────────────────────

async function init() {
  requestAnimationFrame(animate);

  const params    = new URLSearchParams(location.search);
  const machineId = params.get('id');

  if (!machineId) {
    setLoading(false);
    const el = document.getElementById('error-banner');
    el.textContent = 'No machine specified. Use ?id=namespace:machine_id';
    el.style.display = 'block';
    return;
  }

  document.getElementById('viewer-title').textContent = machineId.includes(':') ? machineId.split(':')[1].replace(/_/g, ' ') : machineId;
  document.getElementById('viewer-subtitle').textContent = machineId;
  document.title = 'Phantasia — ' + machineId;

  setLoading(true, 'Loading script…', machineId);
  const safePath = machineId.replace(':', '/');

  const [dataRes, patRes] = await Promise.all([
    fetch(`data/script-data/${safePath}.json`).catch(() => null),
    fetch(`data/script-patterns/${safePath}.json`).catch(() => null),
  ]);

  if (!dataRes || !dataRes.ok) {
    const el = document.getElementById('error-banner');
    el.textContent = 'Script data not found. Run /phantasia webexport in-game first.';
    el.style.display = 'block';
    setLoading(false);
    return;
  }

  scriptData  = await dataRes.json();
  patternData = patRes && patRes.ok ? await patRes.json() : null;

  stepList = compileSteps(scriptData);
  buildStepMarkers();

  playbackTick = 0;
  playbackAccum = 0;
  playing = true;
  tlPlay.textContent = '⏸';
  lastAppliedStepIdx = -1;

  if (!patternData) {
    const el = document.getElementById('error-banner');
    el.textContent = 'Pattern data missing — block positions unavailable.';
    el.style.display = 'block';
    setLoading(false);
    return;
  }

  await buildScene();
}

init();
