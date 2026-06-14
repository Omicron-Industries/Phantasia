/**
 * PhantasiaCamera
 *
 * Orbit camera matching the in-game Phantasia camera:
 *   - Yaw / pitch / zoom around a target point
 *   - Smooth lerp between poses with SNAP / EASE_OUT / EASE_IN_OUT / LINEAR
 *   - Mouse drag to orbit, scroll to zoom
 */

const LerpType = {
  SNAP:        'SNAP',
  LINEAR:      'LINEAR',
  EASE_OUT:    'EASE_OUT',
  EASE_IN_OUT: 'EASE_IN_OUT',
  EASE_IN:     'EASE_IN',
};

function easeOutCubic(t) { return 1 - (1-t)**3; }
function easeInOutCubic(t) { return t < 0.5 ? 4*t*t*t : 1 - (-2*t+2)**3/2; }
function easeInCubic(t) { return t*t*t; }

function applyLerp(t, type) {
  t = Math.max(0, Math.min(1, t));
  switch (type) {
    case LerpType.EASE_OUT:    return easeOutCubic(t);
    case LerpType.EASE_IN_OUT: return easeInOutCubic(t);
    case LerpType.EASE_IN:     return easeInCubic(t);
    default: return t; // LINEAR / SNAP
  }
}

export class PhantasiaCamera {
  constructor(THREE, canvas) {
    this.THREE  = THREE;
    this.canvas = canvas;

    // Current pose
    this.yaw    = -135;    // degrees — matches MC default
    this.pitch  = -35;     // degrees
    this.zoom   = 30;      // world units (distance from target)
    this.target = new THREE.Vector3(0, 0, 0);

    // Lerp state
    this._fromYaw   = this.yaw;
    this._fromPitch = this.pitch;
    this._fromZoom  = this.zoom;
    this._toYaw     = this.yaw;
    this._toPitch   = this.pitch;
    this._toZoom    = this.zoom;
    this._lerpTick  = 0;
    this._lerpTotal = 0;
    this._lerpType  = LerpType.SNAP;

    // Three.js camera
    const w = canvas.clientWidth  || 800;
    const h = canvas.clientHeight || 600;
    this.cam = new THREE.PerspectiveCamera(45, w / h, 0.1, 2000);

    // Mouse interaction
    this._drag      = false;
    this._lastX     = 0;
    this._lastY     = 0;
    this._isPanning = false;

    this._bindEvents();
    this.update();
  }

  // ── Script-driven camera ───────────────────────────────────────────────────

  /**
   * Drive camera to a new pose from a script step.
   * Mimics PhantasiaSceneScreen's `camera.scriptDrive(...)`.
   *
   * @param {number} yaw
   * @param {number} pitch
   * @param {number} zoom
   * @param {string} lerpType
   * @param {number} lerpTicks  — game ticks (20 ticks/s)
   */
  scriptDrive(yaw, pitch, zoom, lerpType, lerpTicks) {
    this._fromYaw   = this.yaw;
    this._fromPitch = this.pitch;
    this._fromZoom  = this.zoom;
    this._toYaw     = yaw;
    this._toPitch   = pitch;
    this._toZoom    = zoom > 0 ? zoom : this.zoom;
    this._lerpType  = lerpType || LerpType.SNAP;
    // Convert ticks to seconds — we animate in real seconds (60fps target)
    this._lerpTotal = (lerpType === LerpType.SNAP || lerpTicks <= 0) ? 0
                    : lerpTicks / 20; // ticks → seconds
    this._lerpTick  = 0;

    if (this._lerpTotal === 0) {
      this.yaw   = this._toYaw;
      this.pitch = this._toPitch;
      this.zoom  = this._toZoom;
    }
  }

  setTarget(x, y, z) {
    this.target.set(x, y, z);
  }

  // ── Per-frame update ───────────────────────────────────────────────────────

  tick(dt) {
    if (this._lerpTotal > 0 && this._lerpTick < this._lerpTotal) {
      this._lerpTick += dt;
      const t = applyLerp(this._lerpTick / this._lerpTotal, this._lerpType);
      this.yaw   = lerp(this._fromYaw,   this._toYaw,   t);
      this.pitch = lerp(this._fromPitch, this._toPitch, t);
      this.zoom  = lerp(this._fromZoom,  this._toZoom,  t);
      if (this._lerpTick >= this._lerpTotal) {
        this.yaw   = this._toYaw;
        this.pitch = this._toPitch;
        this.zoom  = this._toZoom;
        this._lerpTotal = 0;
      }
    }
    this.update();
  }

  update() {
    const { THREE } = this;
    const yRad = THREE.MathUtils.degToRad(this.yaw);
    const pRad = THREE.MathUtils.degToRad(this.pitch);

    const cosP = Math.cos(pRad);
    const x = this.target.x + this.zoom * Math.sin(yRad) * cosP;
    const y = this.target.y + this.zoom * Math.sin(pRad);
    const z = this.target.z + this.zoom * Math.cos(yRad) * cosP;

    this.cam.position.set(x, y, z);
    this.cam.lookAt(this.target);
  }

  onResize(w, h) {
    this.cam.aspect = w / h;
    this.cam.updateProjectionMatrix();
  }

  // ── Mouse / touch interaction ─────────────────────────────────────────────

  _bindEvents() {
    const el = this.canvas;
    el.addEventListener('mousedown',  e => this._onDown(e));
    el.addEventListener('mousemove',  e => this._onMove(e));
    el.addEventListener('mouseup',    () => this._drag = false);
    el.addEventListener('mouseleave', () => this._drag = false);
    el.addEventListener('wheel',      e => this._onWheel(e), { passive: false });
    el.addEventListener('contextmenu', e => e.preventDefault());
  }

  _onDown(e) {
    this._drag     = true;
    this._lastX    = e.clientX;
    this._lastY    = e.clientY;
    this._isPanning = e.button === 2;
    // Cancel any in-flight lerp so dragging feels immediate
    this._lerpTotal = 0;
  }

  _onMove(e) {
    if (!this._drag) return;
    const dx = e.clientX - this._lastX;
    const dy = e.clientY - this._lastY;
    this._lastX = e.clientX;
    this._lastY = e.clientY;

    if (this._isPanning) {
      // Pan target in the camera's local XY plane
      const right = new this.THREE.Vector3();
      const up    = new this.THREE.Vector3();
      this.cam.getWorldDirection(right);
      right.cross(this.cam.up).normalize();
      up.copy(this.cam.up);
      const speed = this.zoom * 0.001;
      this.target.addScaledVector(right, -dx * speed);
      this.target.addScaledVector(up,     dy * speed);
    } else {
      this.yaw   -= dx * 0.4;
      this.pitch  = Math.max(-89, Math.min(89, this.pitch - dy * 0.3));
    }
  }

  _onWheel(e) {
    e.preventDefault();
    const factor = e.deltaY > 0 ? 1.12 : 0.89;
    this.zoom = Math.max(1, Math.min(500, this.zoom * factor));
    this._lerpTotal = 0;
  }
}

function lerp(a, b, t) { return a + (b - a) * t; }
export { LerpType };
