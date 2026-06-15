/**
 * McBlockRenderer
 *
 * Converts Minecraft block data (blockstate + model chain) into Three.js meshes.
 *
 * Handles:
 *   - Full model element rendering with per-face UVs
 *   - Model rotation (x/y from blockstate variant)
 *   - Custom Forge model loaders → particle-texture cube fallback
 *   - Graceful fallback for missing textures / models
 */

export class McBlockRenderer {
  constructor(THREE, assets) {
    this.THREE  = THREE;
    this.assets = assets;
    this._matCache  = new Map(); // texPath → MeshLambertMaterial
    this._meshCache = new Map(); // cacheKey → Group (prototype, clone on use)
  }

  // ── Public API ─────────────────────────────────────────────────────────────

  async buildBlock(namespace, blockId, props) {
    const cacheKey = `${namespace}:${blockId}:${this._propsKey(props)}`;
    if (this._meshCache.has(cacheKey)) {
      return this._meshCache.get(cacheKey).clone();
    }

    const group = new this.THREE.Group();

    try {
      const bsJson   = await this.assets.blockstate(namespace, blockId);
      const variants = this.assets.resolveVariants(bsJson, props);

      if (!variants.length) {
        group.add(this._fallbackCube(namespace, blockId));
      } else {
        for (const variant of variants) {
          const mesh = await this._buildVariantMesh(namespace, variant);
          group.add(mesh);
        }
      }
    } catch (e) {
      console.warn(`[McBlockRenderer] ${namespace}:${blockId}`, e.message);
      group.add(this._fallbackCube(namespace, blockId));
    }

    this._meshCache.set(cacheKey, group);
    return group.clone();
  }

  // ── Variant ────────────────────────────────────────────────────────────────

  async _buildVariantMesh(namespace, variant) {
    const { modelPath, x: rotX, y: rotY } = variant;
    const model = await this.assets.resolveModel(namespace, modelPath);

    let obj;
    if (model.customLoader) {
      // Custom Forge model loader (gtceu:machine, etc.) — use particle/all texture
      obj = this._buildLoaderFallback(model.textures, namespace, modelPath);
    } else if (model.elements && model.elements.length) {
      obj = this._buildFromElements(model.elements, model.textures, namespace);
    } else {
      obj = this._buildTextureCube(model.textures, namespace);
    }

    // Apply blockstate variant rotation
    // MC Y rotation is clockwise looking down (+Y), Three.js Y is CCW — negate
    if (rotX) obj.rotateX(this.THREE.MathUtils.degToRad(-rotX));
    if (rotY) obj.rotateY(this.THREE.MathUtils.degToRad(-rotY));

    return obj;
  }

  // ── Custom loader fallback ─────────────────────────────────────────────────

  _buildLoaderFallback(textures, namespace, modelPath) {
    // Try to find any usable texture: particle, all, side, front, top, or first value
    const CANDIDATE_KEYS = ['particle', 'all', 'side', 'north', 'top', 'front', 'texture'];
    let texPath = null;
    for (const k of CANDIDATE_KEYS) {
      const v = textures[k];
      if (v && !v.startsWith('#')) { texPath = v; break; }
    }
    if (!texPath) {
      // try any resolved texture value
      texPath = Object.values(textures).find(v => v && !v.startsWith('#')) || null;
    }

    if (texPath) {
      return this._fullCubeFromTexture(namespace, texPath);
    }
    return this._fallbackCube(namespace, modelPath);
  }

  // ── Element-based rendering ────────────────────────────────────────────────

  _buildFromElements(elements, textures, namespace) {
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

      // Center of this element in MC space [0..1]
      const cx = (from[0] + to[0]) / 2;
      const cy = (from[1] + to[1]) / 2;
      const cz = (from[2] + to[2]) / 2;

      const mats = this._faceMats(el.faces || {}, textures, namespace);
      const geo  = new THREE.BoxGeometry(sx, sy, sz);
      const mesh = new THREE.Mesh(geo, mats);

      // MC origin is the block's [0,0,0] corner; Three.js is centred at 0.
      // Invert Z because MC Z goes south (+) but Three.js Z comes toward you.
      mesh.position.set(cx - 0.5, cy - 0.5, -(cz - 0.5));

      // Element rotation
      if (el.rotation) {
        const { origin, axis, angle } = el.rotation;
        const ox = origin[0] / 16 - 0.5;
        const oy = origin[1] / 16 - 0.5;
        const oz = -(origin[2] / 16 - 0.5);
        mesh.position.sub(new THREE.Vector3(ox, oy, oz));
        const rad = THREE.MathUtils.degToRad(angle);
        if      (axis === 'x') mesh.rotateX(rad);
        else if (axis === 'y') mesh.rotateY(-rad); // negate for Z-flip
        else if (axis === 'z') mesh.rotateZ(-rad);
        mesh.position.add(new THREE.Vector3(ox, oy, oz));
      }

      group.add(mesh);
    }

    return group;
  }

  /**
   * Build 6 materials for a BoxGeometry.
   * Three.js BoxGeometry face order: +x, -x, +y, -y, +z, -z
   * Minecraft face names:            east west  up  down south north
   * (south/north flipped because we invert Z when placing the mesh)
   */
  _faceMats(faces, textures, namespace) {
    const MC_FACES = ['east', 'west', 'up', 'down', 'south', 'north'];
    return MC_FACES.map(dir => {
      const face = faces[dir];
      if (!face) return this._transparentMat();

      const rawKey = face.texture || '';
      const texKey = rawKey.startsWith('#') ? rawKey.slice(1) : rawKey;
      const texPath = textures[texKey];

      if (!texPath || texPath.startsWith('#')) return this._fallbackMat();
      return this._getMat(namespace, texPath);
    });
  }

  // ── Texture-only cube (model had textures but no elements) ─────────────────

  _buildTextureCube(textures, namespace) {
    // Common texture keys in priority order
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
      const candidates = FACE_MAP[dir];
      for (const k of candidates) {
        const v = textures[k];
        if (v && !v.startsWith('#')) return this._getMat(namespace, v);
      }
      return this._fallbackMat();
    });

    const geo  = new this.THREE.BoxGeometry(1, 1, 1);
    const mesh = new this.THREE.Mesh(geo, mats);
    return mesh;
  }

  _fullCubeFromTexture(namespace, texPath) {
    const mat  = this._getMat(namespace, texPath);
    const geo  = new this.THREE.BoxGeometry(1, 1, 1);
    return new this.THREE.Mesh(geo, mat);
  }

  // ── Material helpers ───────────────────────────────────────────────────────

  _getMat(namespace, texPath) {
    const cacheKey = texPath.includes(':') ? texPath : `${namespace}:${texPath}`;
    if (this._matCache.has(cacheKey)) return this._matCache.get(cacheKey);

    const [ns, path] = texPath.includes(':') ? texPath.split(':') : [namespace, texPath];
    const tex = this.assets.texture(ns, path);
    tex.wrapS = tex.wrapT = 1000; // ClampToEdgeWrapping
    tex.generateMipmaps = false;

    const mat = new this.THREE.MeshLambertMaterial({
      map: tex,
      transparent: true,
      alphaTest: 0.1,
    });
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
    const geo   = new this.THREE.BoxGeometry(1, 1, 1);
    const mat   = new this.THREE.MeshLambertMaterial({ color });
    return new this.THREE.Mesh(geo, mat);
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
  }
}
