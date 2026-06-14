/**
 * MinecraftAssets
 *
 * Loads and caches blockstate JSONs, model JSONs, and Three.js textures
 * from the extracted assets at docs/assets/mc/.
 */
export class MinecraftAssets {
  constructor(basePath = 'assets/mc') {
    this.base = basePath;
    this._jsonCache = new Map();
    this._texCache  = new Map();
    this._loader    = null; // set by viewer after THREE is loaded
  }

  setTextureLoader(loader) { this._loader = loader; }

  // ── JSON helpers ───────────────────────────────────────────────────────────

  async fetchJson(path) {
    if (this._jsonCache.has(path)) return this._jsonCache.get(path);
    try {
      const res = await fetch(path);
      if (!res.ok) return null;
      const data = await res.json();
      this._jsonCache.set(path, data);
      return data;
    } catch { return null; }
  }

  async blockstate(namespace, block) {
    return this.fetchJson(`${this.base}/assets/${namespace}/blockstates/${block}.json`);
  }

  async model(namespace, path) {
    // path may be like "block/cube_all" or "minecraft:block/cube_all"
    const [ns, p] = path.includes(':') ? path.split(':') : [namespace, path];
    return this.fetchJson(`${this.base}/assets/${ns}/models/${p}.json`);
  }

  // ── Texture ────────────────────────────────────────────────────────────────

  texture(namespace, path) {
    // path like "block/machine_casing" or "gtceu:block/machine_casing"
    const [ns, p] = path.includes(':') ? path.split(':') : [namespace, path];
    const url = `${this.base}/assets/${ns}/textures/${p}.png`;
    if (this._texCache.has(url)) return this._texCache.get(url);

    if (!this._loader) throw new Error('TextureLoader not set');
    const tex = this._loader.load(url, undefined, undefined, () => {
      // on error: leave as missing (magenta fallback handled by material)
    });
    tex.magFilter = 0x2630; // NearestFilter
    tex.minFilter = 0x2630;
    tex.colorSpace = 'srgb';
    this._texCache.set(url, tex);
    return tex;
  }

  // ── Model chain resolution ─────────────────────────────────────────────────

  /**
   * Loads a model and fully resolves the parent chain, merging textures and
   * elements bottom-up (child overrides parent). Returns a flat model object:
   *   { textures: {...}, elements: [...] }
   */
  async resolveModel(namespace, modelPath) {
    const chain = [];
    let current = modelPath;
    const visited = new Set();

    while (current) {
      if (visited.has(current)) break;
      visited.add(current);

      const [ns, path] = current.includes(':') ? current.split(':') : [namespace, current];
      const data = await this.model(ns, path);
      if (!data) break;

      chain.unshift({ ns, data }); // prepend so parent is first
      current = data.parent || null;
    }

    // Merge from parent → child
    const result = { textures: {}, elements: null };
    for (const { data } of chain) {
      if (data.textures) Object.assign(result.textures, data.textures);
      if (data.elements) result.elements = data.elements; // child wins
    }

    // Resolve texture variable references (#key → actual path)
    result.textures = this._resolveTextureVars(result.textures);
    return result;
  }

  _resolveTextureVars(textures) {
    const resolved = { ...textures };
    let changed = true;
    let passes = 0;
    while (changed && passes++ < 8) {
      changed = false;
      for (const [k, v] of Object.entries(resolved)) {
        if (v && v.startsWith('#')) {
          const ref = v.slice(1);
          if (resolved[ref] && !resolved[ref].startsWith('#')) {
            resolved[k] = resolved[ref];
            changed = true;
          }
        }
      }
    }
    return resolved;
  }

  // ── Blockstate variant resolution ─────────────────────────────────────────

  /**
   * Given a block's blockstate JSON and a props dict, returns an array of
   * { modelPath, x, y, uvlock } objects to render.
   */
  resolveVariants(bsJson, props) {
    if (!bsJson) return [];

    if (bsJson.variants) {
      return this._resolveVariants(bsJson.variants, props);
    }
    if (bsJson.multipart) {
      return this._resolveMultipart(bsJson.multipart, props);
    }
    return [];
  }

  _resolveVariants(variants, props) {
    // Try to find the most specific matching key
    const propsStr = this._propsToStr(props);

    // Direct match
    if (variants[propsStr]) {
      return this._normalizeVariant(variants[propsStr]);
    }
    // Try empty string key (blocks with no states)
    if (variants['']) {
      return this._normalizeVariant(variants['']);
    }
    // Try matching subsets
    for (const [key, val] of Object.entries(variants)) {
      if (this._variantKeyMatches(key, props)) {
        return this._normalizeVariant(val);
      }
    }
    // Fallback: first entry
    const first = Object.values(variants)[0];
    return first ? this._normalizeVariant(first) : [];
  }

  _propsToStr(props) {
    if (!props || !Object.keys(props).length) return '';
    return Object.entries(props)
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([k, v]) => `${k}=${v}`)
      .join(',');
  }

  _variantKeyMatches(key, props) {
    if (!key) return false;
    const conditions = key.split(',');
    return conditions.every(cond => {
      const [k, v] = cond.split('=');
      return props[k] === v;
    });
  }

  _normalizeVariant(v) {
    const arr = Array.isArray(v) ? v : [v];
    // Pick first (or random-weighted; we always pick first for determinism)
    const picked = arr[0];
    return picked ? [{ modelPath: picked.model, x: picked.x || 0, y: picked.y || 0, uvlock: picked.uvlock || false }] : [];
  }

  _resolveMultipart(parts, props) {
    const result = [];
    for (const part of parts) {
      if (this._multipartConditionMatches(part.when, props)) {
        const variants = this._normalizeVariant(part.apply);
        result.push(...variants);
      }
    }
    return result;
  }

  _multipartConditionMatches(when, props) {
    if (!when) return true;
    if (when.OR) return when.OR.some(c => this._multipartConditionMatches(c, props));
    return Object.entries(when).every(([k, v]) => {
      const vals = String(v).split('|');
      return vals.includes(String(props[k] || ''));
    });
  }
}
