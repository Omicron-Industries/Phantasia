/**
 * Phantasia Online — 3D Viewer
 *
 * Orchestrates scene loading, block rendering, step animation,
 * camera control, and the shopping list side panel.
 */

import { MinecraftAssets }  from './mc-assets.js';
import { McBlockRenderer }  from './mc-block-renderer.js';
import { PhantasiaCamera, LerpType } from './phantasia-camera.js';
import { mergeGeometries } from 'three/addons/utils/BufferGeometryUtils.js';

import * as THREE from 'three';

// ── Globals ────────────────────────────────────────────────────────────────
const TICKS_PER_SEC = 20;

const assets   = new MinecraftAssets('assets/mc');
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

// ── Lighting ──────────────────────────────────────────────────────────────
scene.add(new THREE.AmbientLight(0xffffff, 0.7));
const dirLight = new THREE.DirectionalLight(0xffffff, 0.8);
dirLight.position.set(5, 10, 7);
scene.add(dirLight);
const dirLight2 = new THREE.DirectionalLight(0x8eceff, 0.3);
dirLight2.position.set(-5, 3, -7);
scene.add(dirLight2);

const canvas = document.getElementById('three-canvas');
const camera = new PhantasiaCamera(THREE, canvas);

const blockRenderer = new McBlockRenderer(THREE, assets);

// ── State ──────────────────────────────────────────────────────────────────
let sceneData    = null;   // parsed scene JSON
let patternData  = null;   // array of block objects
let stepList     = [];     // compiled step objects
let blockMeshes  = [];     // { mesh: Group (not in scene), pos, isBaseplate }
let pickMeshes   = [];     // { mesh: BoxMesh (not in scene), pos, isBaseplate } for raycasting
let mergedObjects = [];    // merged Mesh[] currently in scene
let visibleSet   = null;   // Set<string "x,y,z"> or null = all visible
let playing      = true;
let playbackTick = 0;
let playbackAccum = 0;
let playbackSpeed = 1;
let lastAppliedStepIdx = -1;
let lastFrameTime = null;

// ── Step compilation ───────────────────────────────────────────────────────

/**
 * Compiles scene steps into a flat list with pre-computed properties.
 * Mirrors PhantasiaScenePattern.computeVisible logic.
 */
function compileSteps(sceneJson) {
  if (!sceneJson || !sceneJson.steps) return [];
  return sceneJson.steps.map((s, i) => ({
    idx:      i,
    tick:     s.tick || 0,
    caption:  s.caption || null,
    description: s.description || null,
    show:     s.show || 'all',
    layer:    s.layer || 0,
    layerMin: s.layerMin || 0,
    layerMax: s.layerMax || 0,
    hideLayer: s.hideLayer ?? -1,
    positions: s.positions || [],
    hidePositions: s.hidePositions || [],
    working:  s.working || false,
    showItems: s.showItems !== false,
    camera:   s.camera || null,
    overrides: s.machineOverrides || {},
  }));
}

function totalTicks() {
  if (!stepList.length) return 0;
  return stepList[stepList.length - 1].tick + 60; // 3s after last step
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
  const placements = sceneData.placements || [];

  for (const block of patternData) {
    const key = `${block.x},${block.y},${block.z}`;
    const pi  = block.p ?? 0;
    const ov  = step.overrides[String(pi)] || null;
    const placement = placements[pi] || {};
    const offset = { x: placement.x || 0, y: placement.y || 0, z: placement.z || 0 };

    if (block.bp) { visible.add(key); continue; } // baseplate always visible

    const show      = (ov && ov.show)      || step.show;
    const layer     = (ov && ov.layer     !== undefined) ? ov.layer     : step.layer;
    const layerMin  = (ov && ov.layerMin  !== undefined) ? ov.layerMin  : step.layerMin;
    const layerMax  = (ov && ov.layerMax  !== undefined) ? ov.layerMax  : step.layerMax;
    const hideLayer = (ov && ov.hideLayer !== undefined) ? ov.hideLayer : step.hideLayer;
    const positions = (ov && ov.positions && ov.positions.length) ? ov.positions : step.positions;
    const hidePos   = (ov && ov.hidePositions && ov.hidePositions.length) ? ov.hidePositions : step.hidePositions;

    // Placement-relative coords
    const rx = block.x - offset.x;
    const ry = block.y - offset.y;
    const rz = block.z - offset.z;

    if (!matchesShow(show, rx, ry, rz, layer, layerMin, layerMax, positions)) continue;
    if (matchesHide(rx, ry, rz, hideLayer, hidePos)) continue;

    visible.add(key);
  }
  return visible;
}

function matchesShow(show, x, y, z, layer, layerMin, layerMax, positions) {
  switch ((show || 'all').toLowerCase()) {
    case 'layer':  return y === layer;
    case 'layers': return y >= layerMin && y <= layerMax;
    case 'pos':    return posContains(positions, x, y, z);
    default:       return true;
  }
}

function matchesHide(x, y, z, hideLayer, hidePositions) {
  if (hideLayer >= 0 && y === hideLayer) return true;
  return posContains(hidePositions, x, y, z);
}

function posContains(list, x, y, z) {
  if (!list) return false;
  return list.some(p => p[0] === x && p[1] === y && p[2] === z);
}

function applyVisibility(visible) {
  visibleSet = visible;
  rebuildMerged(visible);
  invalidateRaycastCache();
}

// ── Geometry helpers ───────────────────────────────────────────────────────

/**
 * Extract a single-material sub-geometry from a multi-material BoxGeometry
 * for the given index range [start, start+count), applying a world transform.
 */
function extractSubGeo(geo, srcIdx, start, count, worldMatrix) {
  const slice = srcIdx.subarray
    ? Array.from(srcIdx.subarray(start, start + count))
    : Array.from(srcIdx).slice(start, start + count);
  const unique = [...new Set(slice)].sort((a, b) => a - b);
  const remap = new Map(unique.map((v, i) => [v, i]));

  const subGeo = new THREE.BufferGeometry();
  for (const [name, attr] of Object.entries(geo.attributes)) {
    const n = attr.itemSize;
    const src = attr.array;
    const dst = new Float32Array(unique.length * n);
    for (let i = 0; i < unique.length; i++) {
      const f = unique[i] * n, t = i * n;
      for (let j = 0; j < n; j++) dst[t + j] = src[f + j];
    }
    subGeo.setAttribute(name, new THREE.BufferAttribute(dst, n));
  }
  const newIdx = new Uint16Array(count);
  for (let i = 0; i < count; i++) newIdx[i] = remap.get(slice[i]);
  subGeo.setIndex(new THREE.BufferAttribute(newIdx, 1));
  if (worldMatrix) subGeo.applyMatrix4(worldMatrix);
  return subGeo;
}

/**
 * Merge all visible block geometry per material into a small set of draw calls.
 * Blocks are NOT in the scene — only the resulting merged meshes are.
 */
function rebuildMerged(visible) {
  // Dispose and remove previous merged objects
  for (const m of mergedObjects) { scene.remove(m); m.geometry.dispose(); }
  mergedObjects = [];

  const byMat = new Map(); // matUUID → { mat, geos: [] }

  function collect(geo, mat) {
    const k = mat.uuid;
    if (!byMat.has(k)) byMat.set(k, { mat, geos: [] });
    byMat.get(k).geos.push(geo);
  }

  for (const { mesh: group, pos, isBaseplate } of blockMeshes) {
    const key = `${pos.x},${pos.y},${pos.z}`;
    if (visible && !isBaseplate && !visible.has(key)) continue;

    group.traverse(child => {
      if (!child.isMesh) return;
      const geo  = child.geometry;
      const mats = Array.isArray(child.material) ? child.material : [child.material];
      const idx  = geo.index;
      if (!idx) return;

      if (mats.length === 1 || !geo.groups.length) {
        const cloned = geo.clone();
        cloned.applyMatrix4(child.matrixWorld);
        collect(cloned, mats[0]);
      } else {
        for (const g of geo.groups) {
          const mat = mats[g.materialIndex];
          if (mat) collect(extractSubGeo(geo, idx.array, g.start, g.count, child.matrixWorld), mat);
        }
      }
    });
  }

  for (const [, { mat, geos }] of byMat) {
    const merged = mergeGeometries(geos, false);
    for (const g of geos) g.dispose();
    if (!merged) continue;
    const mesh = new THREE.Mesh(merged, mat);
    scene.add(mesh);
    mergedObjects.push(mesh);
  }
}

// ── Scene building ─────────────────────────────────────────────────────────

async function buildScene() {
  for (const m of mergedObjects) { scene.remove(m); m.geometry.dispose(); }
  mergedObjects = [];
  for (const b of pickMeshes) b.mesh.geometry.dispose();
  pickMeshes = [];
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

  const _pickGeo = new THREE.BoxGeometry(1, 1, 1);
  const PARALLEL = 20;
  for (let i = 0; i < patternData.length; i += PARALLEL) {
    const batch = patternData.slice(i, i + PARALLEL);
    const results = await Promise.all(batch.map(async block => {
      try {
        const [ns, blockId] = block.block.includes(':') ? block.block.split(':') : ['minecraft', block.block];
        const neighborChecker = (dx, dy, dz) =>
          posTypeMap.get(`${block.x+dx},${block.y+dy},${block.z+dz}`) === block.block;
        const obj = await blockRenderer.buildBlock(ns, blockId, block.props || {}, neighborChecker);
        return { obj, block };
      } catch { return null; }
    }));
    for (const r of results) {
      if (!r) continue;
      const { obj, block } = r;
      const bx = block.x - cx, by = block.y - cy, bz = -(block.z - cz);
      // Bake world matrices — group is NOT added to scene
      obj.position.set(bx, by, bz);
      obj.updateMatrixWorld(true);
      blockMeshes.push({ mesh: obj, pos: block, isBaseplate: !!block.bp });
      // Simple pick cube for hover raycasting
      const pick = new THREE.Mesh(_pickGeo);
      pick.position.set(bx, by, bz);
      pick.updateMatrix();
      pick.matrixWorld.copy(pick.matrix);
      pickMeshes.push({ mesh: pick, pos: block, isBaseplate: !!block.bp });
    }
    setLoading(true, 'Building blocks…', `${Math.min(i + PARALLEL, patternData.length)} / ${patternData.length}`);
    await yieldFrame();
  }

  setLoading(true, 'Merging geometry…', '');
  await yieldFrame();

  if (stepList.length) {
    applyVisibility(computeVisible(0));
    applyCameraForStep(0, true);
  } else {
    applyVisibility(null);
  }

  setLoading(false);
}

function applyCameraForStep(stepIdx, snap) {
  const step = stepList[stepIdx];
  if (!step || !step.camera) return;
  const c = step.camera;
  camera.scriptDrive(
    c.yaw   ?? camera.yaw,
    c.pitch ?? camera.pitch,
    c.zoom  > 0 ? c.zoom : camera.zoom,
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

function yieldFrame() {
  return new Promise(r => requestAnimationFrame(r));
}

// ── Timeline UI ───────────────────────────────────────────────────────────

const tlPlay  = document.getElementById('tl-play');
const tlTrack = document.getElementById('tl-track');
const tlFill  = document.getElementById('tl-fill');
const tlThumb = document.getElementById('tl-thumb');
const tlTime  = document.getElementById('tl-time');
const tlSpeed = document.getElementById('tl-speed');
const tlRail  = document.getElementById('tl-rail');

tlPlay.addEventListener('click', () => {
  if (playbackTick >= totalTicks()) { playbackTick = 0; playbackAccum = 0; }
  playing = !playing;
  tlPlay.textContent = playing ? '⏸' : '▶';
});

tlSpeed.addEventListener('click', () => {
  playbackSpeed = playbackSpeed === 1 ? 2 : playbackSpeed === 2 ? 0.5 : 1;
  tlSpeed.textContent = playbackSpeed === 1 ? '1×' : playbackSpeed === 2 ? '2×' : '½×';
});

let scrubbing = false;
tlTrack.addEventListener('mousedown', e => { scrubbing = true; doScrub(e); });
window.addEventListener('mousemove', e => { if (scrubbing) doScrub(e); });
window.addEventListener('mouseup', () => { scrubbing = false; });

function doScrub(e) {
  const rect = tlRail.getBoundingClientRect();
  const t = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width));
  playbackTick = Math.round(t * totalTicks());
  playbackAccum = 0;
  updateTimelineUI();
  forceApplyCurrentStep(true);
}

function updateTimelineUI() {
  const total = totalTicks();
  const frac  = total > 0 ? playbackTick / total : 0;
  const pct   = (frac * 100).toFixed(2) + '%';
  tlFill.style.width = pct;
  tlThumb.style.left = pct;

  const secs = Math.floor(playbackTick / TICKS_PER_SEC);
  tlTime.textContent = `${Math.floor(secs / 60)}:${String(secs % 60).padStart(2,'0')}`;
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

// ── Caption ────────────────────────────────────────────────────────────────

const captionStrip = document.getElementById('caption-strip');
const captionText  = document.getElementById('caption-text');
const captionDesc  = document.getElementById('caption-description');

function setCaption(text, desc) {
  if (text) {
    captionText.textContent = text;
    captionDesc.textContent = desc || '';
    captionStrip.style.display = 'flex';
  } else {
    captionStrip.style.display = 'none';
  }
}

// ── Side panel ─────────────────────────────────────────────────────────────

const stepCaption = document.getElementById('step-caption');
const stepDesc    = document.getElementById('step-description');
const stepInfo    = document.getElementById('step-info');

function updateStepPanel(step) {
  if (!step) return;
  stepCaption.textContent = step.caption || '';
  stepDesc.textContent    = step.description || '';
  const total = stepList.length;
  stepInfo.textContent    = `Step ${step.idx + 1} / ${total}`;
}

// Shopping list
function buildShoppingList() {
  const el = document.getElementById('shopping-list');
  el.innerHTML = '';
  if (!patternData) return;

  const counts = {};
  for (const b of patternData) {
    if (b.bp) continue; // skip baseplate
    counts[b.block] = (counts[b.block] || 0) + 1;
  }

  const sorted = Object.entries(counts).sort((a,b) => b[1] - a[1]);
  for (const [blockId, count] of sorted) {
    const [ns, id] = blockId.includes(':') ? blockId.split(':') : ['minecraft', blockId];
    const name = id.split('_').map(w => w[0].toUpperCase() + w.slice(1)).join(' ');
    const row = document.createElement('div');
    row.className = 'shop-item';

    const iconSrc = `assets/mc/assets/${ns}/textures/item/${id}.png`;
    row.innerHTML = `
      <span class="shop-count">×${count}</span>
      <img class="shop-icon" src="${iconSrc}" alt="" onerror="this.style.display='none'">
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

// Pick mesh list cached per visibility state — pickMeshes are NOT in scene
let _raycastCache = null;
function invalidateRaycastCache() { _raycastCache = null; }
function getPickList() {
  if (_raycastCache) return _raycastCache;
  _raycastCache = pickMeshes.filter(b => {
    const key = `${b.pos.x},${b.pos.y},${b.pos.z}`;
    return !visibleSet || b.isBaseplate || visibleSet.has(key);
  });
  return _raycastCache;
}

canvas.addEventListener('mousemove', e => {
  const rect = canvas.getBoundingClientRect();
  mouse.x =  ((e.clientX - rect.left)  / rect.width)  * 2 - 1;
  mouse.y = -((e.clientY - rect.top)   / rect.height) * 2 + 1;

  raycaster.setFromCamera(mouse, camera.cam);
  const picks = getPickList();
  const hits = raycaster.intersectObjects(picks.map(p => p.mesh), false);
  if (hits.length) {
    const hit = picks.find(p => p.mesh === hits[0].object);
    if (hit) {
      const block = hit.pos;
      tooltip.textContent = block.block + (block.props && Object.keys(block.props).length
        ? '[' + Object.entries(block.props).map(([k,v]) => `${k}=${v}`).join(',') + ']' : '');
      tooltip.style.display = 'block';
      tooltip.style.left = (e.clientX - rect.left + 14) + 'px';
      tooltip.style.top  = (e.clientY - rect.top  - 8)  + 'px';
      return;
    }
  }
  tooltip.style.display = 'none';
});
canvas.addEventListener('mouseleave', () => { tooltip.style.display = 'none'; });

// ── Step application ───────────────────────────────────────────────────────

function forceApplyCurrentStep(snap) {
  const idx = getActiveStepIdx(playbackTick);
  applyStep(idx, snap);
}

function applyStep(idx, snap) {
  if (!stepList[idx]) return;
  const step = stepList[idx];
  applyVisibility(computeVisible(idx));
  updateStepPanel(step);
  setCaption(step.caption, step.description);
  updateStepMarkers(idx);

  if (step.camera) {
    applyCameraForStep(idx, snap);
  }
}

function updateStepMarkers(activeIdx) {
  tlRail.querySelectorAll('.tl-step-marker').forEach((el, i) => {
    el.classList.toggle('active', i === activeIdx);
  });
}

// ── Render loop ────────────────────────────────────────────────────────────

function animate(ts) {
  requestAnimationFrame(animate);

  const dt = lastFrameTime !== null ? (ts - lastFrameTime) / 1000 : 0;
  lastFrameTime = ts;

  // Advance playback
  if (playing && !scrubbing && totalTicks() > 0) {
    playbackAccum += dt * TICKS_PER_SEC * playbackSpeed;
    while (playbackAccum >= 1) {
      playbackAccum -= 1;
      playbackTick++;
    }
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

  // Resize if needed
  const w = canvas.clientWidth;
  const h = canvas.clientHeight;
  if (renderer_gl.domElement.width !== w * devicePixelRatio ||
      renderer_gl.domElement.height !== h * devicePixelRatio) {
    renderer_gl.setSize(w, h, false);
    camera.onResize(w, h);
  }

  renderer_gl.render(scene, camera.cam);
}

// ── Scene loading ──────────────────────────────────────────────────────────

async function loadScene(sceneId) {
  setLoading(true, 'Loading scene…', sceneId);

  const safePath = sceneId.replace(':', '/');

  const [sceneRes, patternRes] = await Promise.all([
    fetch(`data/scenes/${safePath}.json`).catch(() => null),
    fetch(`data/patterns/${safePath}.json`).catch(() => null),
  ]);

  if (!sceneRes || !sceneRes.ok) {
    showError('Scene data not found. Run /phantasia webexport in-game first.');
    setLoading(false);
    return;
  }

  sceneData   = await sceneRes.json();
  patternData = patternRes && patternRes.ok ? await patternRes.json() : null;

  // Update header
  document.getElementById('viewer-title').textContent = sceneData.name || sceneId;
  document.getElementById('viewer-subtitle').textContent = sceneId;
  document.title = 'Phantasia — ' + (sceneData.name || sceneId);

  stepList = compileSteps(sceneData);
  buildStepMarkers();
  buildShoppingList();

  playbackTick = 0;
  playbackAccum = 0;
  playing = true;
  tlPlay.textContent = '⏸';
  lastAppliedStepIdx = -1;

  if (!patternData) {
    showError('Pattern data missing — block positions unavailable.');
    setLoading(false);
    return;
  }

  await buildScene();
}

function showError(msg) {
  const el = document.getElementById('error-banner');
  el.textContent = msg;
  el.style.display = 'block';
  setTimeout(() => { el.style.display = 'none'; }, 8000);
}

// ── Init ───────────────────────────────────────────────────────────────────

async function init() {
  // Start render loop
  requestAnimationFrame(animate);

  // Parse scene from URL
  const params  = new URLSearchParams(location.search);
  const sceneId = params.get('scene');

  if (!sceneId) {
    setLoading(false);
    showError('No scene specified. Use ?scene=namespace:name');
    return;
  }

  await loadScene(sceneId);
}

init();
