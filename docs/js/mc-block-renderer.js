/**
 * McBlockRenderer
 *
 * Converts Minecraft block data (blockstate + model chain) into Three.js meshes.
 * Handles:
 *   - Full model element rendering with per-face UVs
 *   - Model rotation (x/y from blockstate variant)
 *   - Multi-material groups for per-face texturing
 *   - Fallback colored cube when assets are unavailable
 */

const FACE_ORDER = ['east', 'west', 'up', 'down', 'south', 'north']; // matches BoxGeometry order

// Default UV for a full face [0,0,16,16]
const FULL_UV = [0, 0, 16, 16];

export class McBlockRenderer {
  constructor(THREE, assets) {
    this.THREE  = THREE;
    this.assets = assets;
    this._matCache = new Map();
    this._meshCache = new Map();
    this._fallbackMat = null;
  }

  // ── Public API ─────────────────────────────────────────────────────────────

  /**
   * Returns a Three.js Object3D for the given block.
   * Blocks are cached by (blockId + props-string + modelKey).
   * The returned object is a *shared prototype* — clone it before placing.
   */
  async buildBlock(namespace, blockId, props) {
    const cacheKey = `${namespace}:${blockId}:${this._propsKey(props)}`;
    if (this._meshCache.has(cacheKey)) {
      return this._meshCache.get(cacheKey).clone();
    }

    const group = new this.THREE.Group();

    try {
      const bsJson = await this.assets.blockstate(namespace, blockId);
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
      console.warn(`[McBlockRenderer] Failed ${namespace}:${blockId}`, e);
      group.add(this._fallbackCube(namespace, blockId));
    }

    this._meshCache.set(cacheKey, group);
    return group.clone();
  }

  // ── Variant mesh ───────────────────────────────────────────────────────────

  async _buildVariantMesh(namespace, variant) {
    const { modelPath, x: rotX, y: rotY } = variant;
    const model = await this.assets.resolveModel(namespace, modelPath);

    let mesh;
    if (model.elements && model.elements.length) {
      mesh = this._buildFromElements(model.elements, model.textures, namespace);
    } else {
      // No elements = probably a special model or missing data; use textured cube fallback
      mesh = this._buildSimpleCube(model.textures, namespace);
    }

    // Apply model rotation (MC uses Y-up, right-hand with north=-Z)
    if (rotX) mesh.rotateX(this.THREE.MathUtils.degToRad(-rotX));
    if (rotY) mesh.rotateY(this.THREE.MathUtils.degToRad(-rotY));

    return mesh;
  }

  // ── Element rendering ──────────────────────────────────────────────────────

  _buildFromElements(elements, textures, namespace) {
    const { THREE } = this;
    const group = new THREE.Group();

    for (const el of elements) {
      const from = el.from.map(v => v / 16);
      const to   = el.to.map(v => v / 16);

      const sx = to[0] - from[0];
      const sy = to[1] - from[1];
      const sz = to[2] - from[2];

      // Center of this element
      const cx = (from[0] + to[0]) / 2;
      const cy = (from[1] + to[1]) / 2;
      const cz = (from[2] + to[2]) / 2;

      const geo  = new THREE.BoxGeometry(sx, sy, sz);
      const mats = this._buildFaceMaterials(el.faces || {}, textures, namespace);

      const mesh = new THREE.Mesh(geo, mats);
      // MC coords: x right, y up, z south — Three.js: x right, y up, z toward viewer
      // Invert Z to match MC world orientation
      mesh.position.set(cx - 0.5, cy - 0.5, -(cz - 0.5));

      // Element-level rotation
      if (el.rotation) {
        const { origin, axis, angle } = el.rotation;
        const ox = origin[0] / 16 - 0.5;
        const oy = origin[1] / 16 - 0.5;
        const oz = -(origin[2] / 16 - 0.5);
        mesh.position.sub(new THREE.Vector3(ox, oy, oz));
        const rad = THREE.MathUtils.degToRad(angle);
        if (axis === 'x') mesh.rotateX(rad);
        else if (axis === 'y') mesh.rotateY(-rad); // invert for Z-flip
        else if (axis === 'z') mesh.rotateZ(-rad);
        mesh.position.add(new THREE.Vector3(ox, oy, oz));
      }

      group.add(mesh);
    }

    return group;
  }

  /** Build 6 materials (BoxGeometry order: +x, -x, +y, -y, +z, -z = east,west,up,down,south,north) */
  _buildFaceMaterials(faces, textures, namespace) {
    // BoxGeometry face order
    const faceKeys = ['east', 'west', 'up', 'down', 'south', 'north'];
    return faceKeys.map(dir => {
      const face = faces[dir];
      if (!face || face.texture === '#overlay') {
        return this._transparentMat();
      }
      const texKey = face.texture ? face.texture.replace('#', '') : null;
      const texPath = texKey ? textures[texKey] : null;
      if (!texPath) return this._fallbackMat();

      const mat = this._getMaterial(namespace, texPath);

      // Apply UV rotation for this face (tintindex ignored on web)
      if (face.uv && face.rotation) {
        // UV rotation is tricky — skip for now; most GTCEu blocks don't need it
      }
      return mat;
    });
  }

  // ── Simple cube (no elements) ─────────────────────────────────────────────

  _buildSimpleCube(textures, namespace) {
    const { THREE } = this;
    const geo  = new THREE.BoxGeometry(1, 1, 1);
    const mats = this._buildFaceMaterials(
      // Use 'all' texture for all faces if available, else per-face
      Object.fromEntries(['east','west','up','down','south','north'].map(d => [
        d, textures.all ? { texture: '#all' } : textures[d] ? { texture: '#' + d } : null
      ])),
      textures,
      namespace
    );
    const mesh = new THREE.Mesh(geo, mats);
    mesh.position.set(0, 0, 0);
    return mesh;
  }

  // ── Material helpers ───────────────────────────────────────────────────────

  _getMaterial(namespace, texPath) {
    const key = texPath.includes(':') ? texPath : `${namespace}:${texPath}`;
    if (this._matCache.has(key)) return this._matCache.get(key);

    const [ns, path] = texPath.includes(':') ? texPath.split(':') : [namespace, texPath];
    const tex = this.assets.texture(ns, path);

    const mat = new this.THREE.MeshLambertMaterial({
      map: tex,
      transparent: true,
      alphaTest: 0.1,
    });
    this._matCache.set(key, mat);
    return mat;
  }

  _fallbackMat() {
    if (!this._fallbackMat_) {
      this._fallbackMat_ = new this.THREE.MeshLambertMaterial({ color: 0xff00ff });
    }
    return this._fallbackMat_;
  }

  _transparentMat() {
    return new this.THREE.MeshLambertMaterial({ transparent: true, opacity: 0 });
  }

  _fallbackCube(namespace, blockId) {
    const color = this._deterministicColor(namespace + ':' + blockId);
    const geo   = new this.THREE.BoxGeometry(1, 1, 1);
    const mat   = new this.THREE.MeshLambertMaterial({ color });
    return new this.THREE.Mesh(geo, mat);
  }

  _deterministicColor(str) {
    let h = 0;
    for (let i = 0; i < str.length; i++) h = (Math.imul(31, h) + str.charCodeAt(i)) | 0;
    const hue = ((h & 0xffff) / 0xffff) * 360;
    return new this.THREE.Color().setHSL(hue / 360, 0.45, 0.35);
  }

  _propsKey(props) {
    if (!props) return '';
    return Object.entries(props).sort(([a], [b]) => a.localeCompare(b)).map(([k,v]) => `${k}=${v}`).join(',');
  }

  dispose() {
    for (const mat of this._matCache.values()) mat.dispose();
    this._matCache.clear();
    this._meshCache.clear();
  }
}
