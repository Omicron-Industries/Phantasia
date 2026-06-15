/**
 * McBlockRenderer
 *
 * Converts Minecraft block data (blockstate + model chain) into Three.js meshes.
 *
 * Handles:
 *   - Full model element rendering with per-face UVs
 *   - Model rotation (x/y from blockstate variant)
 *   - Custom Forge model loaders → particle-texture cube fallback
 *   - LDLib compact CTM (2×2 atlas, per-face neighbor-aware tile selection)
 *   - Overlay element z-fighting fix via polygonOffset
 *   - Graceful fallback for missing textures / models
 */

export class McBlockRenderer {
  constructor(THREE, assets) {
    this.THREE  = THREE;
    this.assets = assets;
    this._matCache    = new Map(); // texPath → MeshLambertMaterial
    this._meshCache   = new Map(); // cacheKey → Group (prototype, clone on use)
    this._ctmTileCache = new Map(); // "ctmPath:col:row" → CanvasTexture
  }

  // ── Public API ─────────────────────────────────────────────────────────────

  /**
   * @param {string} namespace
   * @param {string} blockId
   * @param {object} props
   * @param {function|null} neighborChecker  (dx,dy,dz)=>boolean — same block type?
   */
  async buildBlock(namespace, blockId, props, neighborChecker = null) {
    const baseKey = `${namespace}:${blockId}:${this._propsKey(props)}`;
    if (!neighborChecker && this._meshCache.has(baseKey)) {
      return this._meshCache.get(baseKey).clone();
    }

    const group = new this.THREE.Group();

    try {
      const bsJson   = await this.assets.blockstate(namespace, blockId);
      const variants = this.assets.resolveVariants(bsJson, props);

      if (!variants.length) {
        group.add(this._fallbackCube(namespace, blockId));
      } else {
        for (const variant of variants) {
          const mesh = await this._buildVariantMesh(namespace, variant, neighborChecker);
          group.add(mesh);
        }
      }
    } catch (e) {
      console.warn(`[McBlockRenderer] ${namespace}:${blockId}`, e.message);
      group.add(this._fallbackCube(namespace, blockId));
    }

    if (!neighborChecker) {
      this._meshCache.set(baseKey, group);
    }
    return neighborChecker ? group : group.clone();
  }

  // ── Variant ────────────────────────────────────────────────────────────────

  async _buildVariantMesh(namespace, variant, neighborChecker) {
    const { modelPath, x: rotX, y: rotY } = variant;
    const model = await this.assets.resolveModel(namespace, modelPath);

    let obj;
    if (model.customLoader) {
      obj = this._buildLoaderFallback(model.textures, namespace, modelPath);
    } else if (model.elements && model.elements.length) {
      obj = await this._buildFromElements(model.elements, model.textures, namespace, neighborChecker);
    } else {
      obj = this._buildTextureCube(model.textures, namespace);
    }

    if (rotX) obj.rotateX(this.THREE.MathUtils.degToRad(-rotX));
    if (rotY) obj.rotateY(this.THREE.MathUtils.degToRad(-rotY));

    return obj;
  }

  // ── Custom loader fallback ─────────────────────────────────────────────────

  _buildLoaderFallback(textures, namespace, modelPath) {
    const CANDIDATE_KEYS = ['particle', 'all', 'side', 'north', 'top', 'front', 'texture'];
    let texPath = null;
    for (const k of CANDIDATE_KEYS) {
      const v = textures[k];
      if (v && !v.startsWith('#')) { texPath = v; break; }
    }
    if (!texPath) {
      texPath = Object.values(textures).find(v => v && !v.startsWith('#')) || null;
    }
    return texPath
      ? this._fullCubeFromTexture(namespace, texPath)
      : this._fallbackCube(namespace, modelPath);
  }

  // ── Element-based rendering ────────────────────────────────────────────────

  async _buildFromElements(elements, textures, namespace, neighborChecker) {
    const { THREE } = this;
    const group = new THREE.Group();

    for (const el of elements) {
      if (!el.from || !el.to) continue;
      const from = el.from.map(v => v / 16);
      const to   = el.to.map(v => v / 16);

      const sx = to[0] - from[0];
      const sy = to[1] - from[1];
      const sz = to[2] - from[2];
      if (sx <= 0 || sy <= 0 || sz <= 0) continue;

      const cx = (from[0] + to[0]) / 2;
      const cy = (from[1] + to[1]) / 2;
      const cz = (from[2] + to[2]) / 2;

      // Elements that extend beyond [0,1] are overlay layers — need polygonOffset
      const isOverlay = el.from.some(v => v < 0) || el.to.some(v => v > 16);

      const mats = await this._buildFaceMats(el.faces || {}, textures, namespace, neighborChecker, isOverlay);
      const geo  = new THREE.BoxGeometry(sx, sy, sz);
      const mesh = new THREE.Mesh(geo, mats);

      mesh.position.set(cx - 0.5, cy - 0.5, -(cz - 0.5));

      if (el.rotation) {
        const { origin, axis, angle } = el.rotation;
        const ox = origin[0] / 16 - 0.5;
        const oy = origin[1] / 16 - 0.5;
        const oz = -(origin[2] / 16 - 0.5);
        mesh.position.sub(new THREE.Vector3(ox, oy, oz));
        const rad = THREE.MathUtils.degToRad(angle);
        if      (axis === 'x') mesh.rotateX(rad);
        else if (axis === 'y') mesh.rotateY(-rad);
        else if (axis === 'z') mesh.rotateZ(-rad);
        mesh.position.add(new THREE.Vector3(ox, oy, oz));
      }

      group.add(mesh);
    }

    return group;
  }

  /**
   * BoxGeometry face order: +x, -x, +y, -y, +z, -z
   * MC face names:          east west  up  down south north
   * (south/north flipped because we invert Z when placing the mesh)
   */
  async _buildFaceMats(faces, textures, namespace, neighborChecker, isOverlay) {
    const MC_FACES = ['east', 'west', 'up', 'down', 'south', 'north'];
    const mats = await Promise.all(MC_FACES.map(dir => {
      const face = faces[dir];
      if (!face) return Promise.resolve(this._transparentMat());

      const rawKey = face.texture || '';
      const texKey = rawKey.startsWith('#') ? rawKey.slice(1) : rawKey;
      const texPath = textures[texKey];

      if (!texPath || texPath.startsWith('#')) return Promise.resolve(this._fallbackMat());
      return this._getFaceMat(namespace, texPath, dir, neighborChecker, isOverlay);
    }));
    return mats;
  }

  /**
   * Build a material for one face, applying CTM if available.
   * @param {string} dir  MC face direction (east/west/up/down/south/north)
   */
  async _getFaceMat(namespace, texPath, dir, neighborChecker, isOverlay) {
    if (texPath === 'gtceu:block/void' || texPath.endsWith('/void')) {
      return this._transparentMat();
    }

    // Check for LDLib CTM connection
    if (neighborChecker) {
      const ctmPath = await this.assets.ctmPathFor(namespace, texPath);
      if (ctmPath) {
        const { col, row } = this._ctmTile(dir, neighborChecker);
        return this._getCtmTileMat(namespace, ctmPath, col, row, isOverlay);
      }
    }

    return this._getMat(namespace, texPath, isOverlay);
  }

  // ── CTM tile selection ─────────────────────────────────────────────────────

  /**
   * For a given face direction + neighbor checker, determine which 2×2 CTM tile to use.
   * LDLib compact CTM layout (our interpretation):
   *   col=0,row=0 (TL): isolated / outer appearance
   *   col=1,row=0 (TR): horizontal axis connected
   *   col=0,row=1 (BL): vertical axis connected
   *   col=1,row=1 (BR): both axes connected (inner)
   *
   * The H and V axes for each face direction (in world space):
   *   north/south: H=±X, V=±Y
   *   east/west:   H=±Z, V=±Y
   *   up/down:     H=±X, V=±Z
   */
  _ctmTile(dir, neighborChecker) {
    let hDirs, vDirs;
    switch (dir) {
      case 'north': case 'south': hDirs = [[1,0,0],[-1,0,0]]; vDirs = [[0,1,0],[0,-1,0]]; break;
      case 'east':  case 'west':  hDirs = [[0,0,1],[0,0,-1]]; vDirs = [[0,1,0],[0,-1,0]]; break;
      case 'up':    case 'down':  hDirs = [[1,0,0],[-1,0,0]]; vDirs = [[0,0,1],[0,0,-1]]; break;
      default:                    hDirs = [[1,0,0],[-1,0,0]]; vDirs = [[0,1,0],[0,-1,0]];
    }
    const hasH = hDirs.some(([dx,dy,dz]) => neighborChecker(dx, dy, dz));
    const hasV = vDirs.some(([dx,dy,dz]) => neighborChecker(dx, dy, dz));
    return { col: hasH ? 1 : 0, row: hasV ? 1 : 0 };
  }

  /**
   * Returns a material sampling the (col, row) tile from the 2×2 CTM atlas.
   * Uses UV offset/repeat on a cloned Three.js texture — no canvas or blob URLs needed.
   * With Three.js flipY=true (default): UV (0,0) = PNG top-left, so
   *   offset.x = col * 0.5,  offset.y = row * 0.5
   */
  _getCtmTileMat(namespace, ctmPath, col, row, isOverlay) {
    const cacheKey = `${ctmPath}:${col}:${row}:${isOverlay ? 1 : 0}`;
    if (this._ctmTileCache.has(cacheKey)) return this._ctmTileCache.get(cacheKey);

    const [ns, p] = ctmPath.includes(':') ? ctmPath.split(':') : [namespace, ctmPath];
    const atlas = this.assets.texture(ns, p);

    const tile = atlas.clone();
    tile.needsUpdate = true;
    tile.repeat.set(0.5, 0.5);
    tile.offset.set(col * 0.5, row * 0.5);
    tile.wrapS = this.THREE.ClampToEdgeWrapping;
    tile.wrapT = this.THREE.ClampToEdgeWrapping;

    const mat = new this.THREE.MeshLambertMaterial({
      map: tile,
      transparent: true,
      alphaTest: 0.05,
      depthWrite: !isOverlay,
    });
    if (isOverlay) {
      mat.polygonOffset      = true;
      mat.polygonOffsetFactor = -4;
      mat.polygonOffsetUnits  = -8;
    }
    this._ctmTileCache.set(cacheKey, mat);
    return mat;
  }

  // ── Texture-only cube (model had textures but no elements) ─────────────────

  _buildTextureCube(textures, namespace) {
    const FACE_MAP = {
      east:  ['east',  'side', 'all', 'particle'],
      west:  ['west',  'side', 'all', 'particle'],
      up:    ['up',    'top',  'end', 'all', 'particle'],
      down:  ['down',  'bottom','end','all', 'particle'],
      south: ['south', 'side', 'front','all','particle'],
      north: ['north', 'side', 'front','all','particle'],
    };

    const MC_FACES = ['east', 'west', 'up', 'down', 'south', 'north'];
    const mats = MC_FACES.map(dir => {
      for (const k of FACE_MAP[dir]) {
        const v = textures[k];
        if (v && !v.startsWith('#')) return this._getMat(namespace, v, false);
      }
      return this._fallbackMat();
    });

    return new this.THREE.Mesh(new this.THREE.BoxGeometry(1, 1, 1), mats);
  }

  _fullCubeFromTexture(namespace, texPath) {
    const mat = this._getMat(namespace, texPath, false);
    return new this.THREE.Mesh(new this.THREE.BoxGeometry(1, 1, 1), mat);
  }

  // ── Material helpers ───────────────────────────────────────────────────────

  _getMat(namespace, texPath, isOverlay) {
    if (texPath === 'gtceu:block/void' || texPath.endsWith('/void')) {
      return this._transparentMat();
    }

    const cacheKey = (texPath.includes(':') ? texPath : `${namespace}:${texPath}`) + (isOverlay ? ':ov' : '');
    if (this._matCache.has(cacheKey)) return this._matCache.get(cacheKey);

    const [ns, path] = texPath.includes(':') ? texPath.split(':') : [namespace, texPath];
    const tex = this.assets.texture(ns, path);

    const mat = new this.THREE.MeshLambertMaterial({
      map: tex,
      transparent: true,
      alphaTest: 0.05,
      depthWrite: !isOverlay,
    });
    if (isOverlay) {
      mat.polygonOffset      = true;
      mat.polygonOffsetFactor = -4;
      mat.polygonOffsetUnits  = -8;
    }
    this._matCache.set(cacheKey, mat);
    return mat;
  }

  _fallbackMat() {
    if (!this._fallbackMat_) {
      this._fallbackMat_ = new this.THREE.MeshLambertMaterial({ color: 0x884444 });
    }
    return this._fallbackMat_;
  }

  _transparentMat() {
    return new this.THREE.MeshLambertMaterial({ transparent: true, opacity: 0, depthWrite: false });
  }

  _fallbackCube(namespace, id) {
    const color = this._deterministicColor(`${namespace}:${id}`);
    return new this.THREE.Mesh(
      new this.THREE.BoxGeometry(1, 1, 1),
      new this.THREE.MeshLambertMaterial({ color })
    );
  }

  _deterministicColor(str) {
    let h = 0;
    for (let i = 0; i < str.length; i++) h = (Math.imul(31, h) + str.charCodeAt(i)) | 0;
    return new this.THREE.Color().setHSL(((h & 0xffff) / 0xffff), 0.4, 0.3);
  }

  _propsKey(props) {
    if (!props) return '';
    return Object.entries(props).sort(([a],[b]) => a.localeCompare(b)).map(([k,v])=>`${k}=${v}`).join(',');
  }

  dispose() {
    for (const mat of this._matCache.values()) mat.dispose();
    this._matCache.clear();
    this._meshCache.clear();
    this._ctmTileCache.clear();
  }
}
