/**
 * MinecraftAssets
 *
 * Loads and caches blockstate JSONs, model JSONs, and Three.js textures
 * from the extracted assets at docs/assets/mc/.
 *
 * Special handling for the GTCEu "gtceu:machine" custom loader:
 *   The loader embeds inline variant models inside the top-level model JSON
 *   (keyed by machine-state strings).  We resolve those inline models against
 *   their declared `parent` file to get real elements + textures.
 */
export class MinecraftAssets {
  constructor(basePath = 'assets/mc') {
    this.base = basePath;
    this._jsonCache  = new Map();
    this._texCache   = new Map();
    this._loader     = null;
    this._mcmetaCache = new Map(); // texPath → mcmeta JSON or null
  }

  setTextureLoader(loader) { this._loader = loader; }

  // ── Prefetch ───────────────────────────────────────────────────────────────

  /**
   * Fire-and-forget: fetches all blockstates + their model chains in parallel so
   * subsequent buildBlock calls hit the JSON cache instead of making serial HTTP requests.
   * @param {Array<{block: string, props?: object}>} blockList
   */
  async prefetchBlocks(blockList) {
    // Collect unique block IDs
    const unique = [...new Set(blockList.map(b => b.block))];

    // Phase 1: fetch all blockstates in parallel
    const bsResults = await Promise.all(unique.map(async id => {
      const [ns, blockId] = id.includes(':') ? id.split(':') : ['minecraft', id];
      const bs = await this.blockstate(ns, blockId);
      return { ns, blockId, bs };
    }));

    // Phase 2: collect all model paths and fetch models in parallel
    const modelPaths = new Set();
    for (const { ns, bs } of bsResults) {
      if (!bs) continue;
      const variants = this.resolveVariants(bs, {});
      for (const v of variants) {
        if (v.modelPath) modelPaths.add(`${ns}|${v.modelPath}`);
      }
    }
    await Promise.all([...modelPaths].map(async key => {
      const [ns, path] = key.split('|');
      try { await this.resolveModel(ns, path); } catch { /* ignore */ }
    }));
  }

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
    const data = await this.fetchJson(`${this.base}/assets/${namespace}/blockstates/${block}.json`);
    if (data) return data;

    // Synthetic fallback: check if a block model exists at the conventional path
    const modelData = await this.model(namespace, `block/${block}`);
    if (modelData) {
      return { variants: { '': { model: `${namespace}:block/${block}` } } };
    }

    // Also check exported synthetic fallback model (written by PhantasiaWebExport)
    const synthData = await this.model(namespace, `block/_synth/${block}`);
    if (synthData) {
      return { variants: { '': { model: `${namespace}:block/_synth/${block}` } } };
    }

    return null;
  }

  async model(namespace, path) {
    const [ns, p] = path.includes(':') ? path.split(':') : [namespace, path];
    return this.fetchJson(`${this.base}/assets/${ns}/models/${p}.json`);
  }

  // ── CTM / mcmeta ──────────────────────────────────────────────────────────

  /** Returns the ldlib CTM texture path for a given texture, or null if none. */
  async ctmPathFor(namespace, texPath) {
    const [ns, p] = texPath.includes(':') ? texPath.split(':') : [namespace, texPath];
    const url = `${this.base}/assets/${ns}/textures/${p}.png.mcmeta`;
    if (this._mcmetaCache.has(url)) return this._mcmetaCache.get(url);
    const data = await this.fetchJson(url);
    const result = data?.ldlib?.connection ?? null;
    this._mcmetaCache.set(url, result);
    return result;
  }

  // ── Texture ────────────────────────────────────────────────────────────────

  texture(namespace, path) {
    const [ns, p] = path.includes(':') ? path.split(':') : [namespace, path];
    const url = `${this.base}/assets/${ns}/textures/${p}.png`;
    if (this._texCache.has(url)) return this._texCache.get(url);

    if (!this._loader) throw new Error('TextureLoader not set');
    const tex = this._loader.load(url, loaded => {
      loaded.magFilter     = 0x2630; // NearestFilter
      loaded.minFilter     = 0x2630;
      loaded.generateMipmaps = false;
      loaded.colorSpace    = 'srgb';
      loaded.needsUpdate   = true;
    });
    tex.magFilter      = 0x2630;
    tex.minFilter      = 0x2630;
    tex.generateMipmaps = false;
    this._texCache.set(url, tex);
    return tex;
  }

  // ── Model chain resolution ─────────────────────────────────────────────────

  /**
   * Fully resolves a model path (file-based) to { textures, elements }.
   * Follows parent chains, detects GTCEu machine loaders, and returns
   * something the block renderer can directly consume.
   */
  async resolveModel(namespace, modelPath) {
    const [ns] = modelPath.includes(':') ? modelPath.split(':') : [namespace];
    const rawData = await this._loadModelFile(namespace, modelPath);
    if (!rawData) return { textures: {}, elements: null, customLoader: null };

    // ── GTCEu machine loader ───────────────────────────────────────────────
    if (rawData.loader === 'gtceu:machine') {
      return this._resolveGtceuMachine(namespace, rawData);
    }

    // ── Forge composite loader ─────────────────────────────────────────────
    // children map: pick "base" or first child, resolve its inline model chain
    if (rawData.loader === 'forge:composite' && rawData.children) {
      return this._resolveForgeComposite(namespace, rawData);
    }

    // ── Standard Minecraft model chain ────────────────────────────────────
    return this._resolveStandardChain(namespace, modelPath, rawData);
  }

  // ── Forge composite loader ─────────────────────────────────────────────────

  async _resolveForgeComposite(namespace, compositeData) {
    const children = compositeData.children || {};
    // Prefer "base" child, then first child
    const childKey  = Object.prototype.hasOwnProperty.call(children, 'base') ? 'base' : Object.keys(children)[0];
    const child     = children[childKey];
    if (!child) {
      // Fall back to particle texture cube
      const textures = this._resolveTextureVars({ ...(compositeData.textures || {}) });
      return { textures, elements: null, customLoader: null };
    }

    // child may have its own parent + textures (inline model)
    const topTextures  = { ...(compositeData.textures || {}), ...(child.textures || {}) };
    const parentPath   = child.parent;
    if (!parentPath) {
      return { textures: this._resolveTextureVars(topTextures), elements: null, customLoader: null };
    }

    const parentResult = await this._resolveStandardChain(namespace, parentPath, null);
    return {
      textures:     this._resolveTextureVars({ ...parentResult.textures, ...topTextures }),
      elements:     parentResult.elements,
      customLoader: null,
    };
  }

  // ── GTCEu machine loader ───────────────────────────────────────────────────

  async _resolveGtceuMachine(namespace, machineData) {
    // Base textures from texture_overrides
    const baseTextures = { ...(machineData.texture_overrides || {}) };

    // Pick idle/default inner variant model — prefer formed (is_formed=true) for better visuals
    const variants = machineData.variants || {};
    const PREFERRED = ['is_formed=true,recipe_logic_status=idle', 'is_formed=false,recipe_logic_status=idle'];
    let innerModelData = null;
    for (const key of PREFERRED) {
      if (variants[key]?.model) { innerModelData = variants[key].model; break; }
    }
    if (!innerModelData) {
      const first = Object.values(variants)[0];
      innerModelData = first?.model || null;
    }

    if (!innerModelData) {
      // No inner variants — just render with base textures
      return { textures: this._resolveTextureVars(baseTextures), elements: null, customLoader: null };
    }

    // Resolve the inline model's parent chain (it's a real file reference)
    const parent = innerModelData.parent;
    if (!parent) {
      return { textures: this._resolveTextureVars({ ...baseTextures, ...(innerModelData.textures || {}) }), elements: null, customLoader: null };
    }

    const parentResult = await this._resolveStandardChain(namespace, parent, null);
    const merged = {
      ...parentResult.textures,
      ...baseTextures,
      ...(innerModelData.textures || {}),
    };
    return {
      textures:      this._resolveTextureVars(merged),
      elements:      parentResult.elements,
      customLoader:  null,
    };
  }

  // ── Standard file-based chain ──────────────────────────────────────────────

  async _resolveStandardChain(namespace, modelPath, preloaded) {
    const chain   = [];
    let current   = modelPath;
    let preload   = preloaded; // first file already fetched
    const visited = new Set();

    while (current) {
      if (visited.has(current)) break;
      visited.add(current);

      const data = preload || await this._loadModelFile(namespace, current);
      preload = null;
      if (!data) break;

      // Stop traversal if a custom loader appears (gtceu:machine handled above;
      // other custom loaders — just read textures and stop)
      chain.unshift({ data });
      if (data.loader && data.loader !== 'minecraft:elements') break;

      current = data.parent || null;
    }

    const result = { textures: {}, elements: null, customLoader: null };
    for (const { data } of chain) {
      if (data.textures) Object.assign(result.textures, data.textures);
      if (data.elements) result.elements = data.elements;
      if (data.loader)   result.customLoader = data.loader;
    }
    result.textures = this._resolveTextureVars(result.textures);
    return result;
  }

  async _loadModelFile(namespace, modelPath) {
    const [ns, p] = modelPath.includes(':') ? modelPath.split(':') : [namespace, modelPath];
    return this.model(ns, p);
  }

  // ── Texture variable resolution ────────────────────────────────────────────

  _resolveTextureVars(textures) {
    const resolved = { ...textures };
    let changed = true;
    let passes  = 0;
    while (changed && passes++ < 10) {
      changed = false;
      for (const [k, v] of Object.entries(resolved)) {
        if (v && v.startsWith('#')) {
          const ref = v.slice(1);
          if (resolved[ref] !== undefined && !resolved[ref].startsWith('#')) {
            resolved[k] = resolved[ref];
            changed = true;
          }
        }
      }
    }
    return resolved;
  }

  // ── Blockstate variant resolution ─────────────────────────────────────────

  resolveVariants(bsJson, props) {
    if (!bsJson) return [];
    if (bsJson.variants)  return this._resolveVariants(bsJson.variants, props);
    if (bsJson.multipart) return this._resolveMultipart(bsJson.multipart, props);
    return [];
  }

  _resolveVariants(variants, props) {
    // Try exact match first, then partial, then empty key, then first
    const propsStr = this._propsToStr(props);
    if (variants[propsStr]) return this._normalizeVariant(variants[propsStr]);

    // Find the key with the most matching conditions
    let bestKey  = null;
    let bestScore = -1;
    for (const key of Object.keys(variants)) {
      const score = this._variantScore(key, props);
      if (score > bestScore) { bestScore = score; bestKey = key; }
    }
    if (bestKey !== null) return this._normalizeVariant(variants[bestKey]);

    if (variants['']) return this._normalizeVariant(variants['']);
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

  /** Returns how many conditions in the variant key match the given props. -1 if any mismatch. */
  _variantScore(key, props) {
    if (!key) return 0;
    let score = 0;
    for (const cond of key.split(',')) {
      const eq = cond.indexOf('=');
      if (eq < 0) continue;
      const k = cond.slice(0, eq);
      const v = cond.slice(eq + 1);
      if (props[k] === v) score++;
      else if (props[k] !== undefined) return -1; // explicit mismatch
    }
    return score;
  }

  _normalizeVariant(v) {
    const arr    = Array.isArray(v) ? v : [v];
    const picked = arr[0];
    return picked
      ? [{ modelPath: picked.model, x: picked.x || 0, y: picked.y || 0, uvlock: picked.uvlock || false }]
      : [];
  }

  _resolveMultipart(parts, props) {
    const result = [];
    for (const part of parts) {
      if (this._mpMatches(part.when, props)) {
        result.push(...this._normalizeVariant(part.apply));
      }
    }
    return result;
  }

  _mpMatches(when, props) {
    if (!when) return true;
    if (when.OR) return when.OR.some(c => this._mpMatches(c, props));
    return Object.entries(when).every(([k, v]) =>
      String(v).split('|').includes(String(props[k] ?? '')));
  }
}
