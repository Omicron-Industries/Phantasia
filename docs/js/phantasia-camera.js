/**
 * PhantasiaCamera
 *
 * Orbit camera matching the in-game Phantasia camera system:
 *   - Left drag  → orbit (yaw / pitch)
 *   - Right drag → pan target (shift in camera's X/Y plane)
 *   - Middle drag → pan target (same as right drag — matches Phantasia behaviour)
 *   - Scroll     → zoom
 *   - Smooth lerp between script-driven camera poses
 */

export const LerpType = {
  SNAP:        'SNAP',
  LINEAR:      'LINEAR',
  EASE_OUT:    'EASE_OUT',
  EASE_IN_OUT: 'EASE_IN_OUT',
  EASE_IN:     'EASE_IN',
};

function easeOutCubic(t)    { return 1 - (1 - t) ** 3; }
function easeInOutCubic(t)  { return t < 0.5 ? 4*t*t*t : 1 - (-2*t+2)**3/2; }
function easeInCubic(t)     { return t * t * t; }

function applyLerp(t, type) {
  t = Math.max(0, Math.min(1, t));
  switch (type) {
    case LerpType.EASE_OUT:    return easeOutCubic(t);
    case LerpType.EASE_IN_OUT: return easeInOutCubic(t);
    case LerpType.EASE_IN:     return easeInCubic(t);
    default: return t; // LINEAR
  }
}

function lerp(a, b, t) { return a + (b - a) * t; }

export class PhantasiaCamera {
  constructor(THREE, canvas) {
    this.THREE  = THREE;
    this.canvas = canvas;

    this.yaw    = -135;
    this.pitch  = -35;
    this.zoom   = 30;
    this.target = new THREE.Vector3(0, 0, 0);

    this._fromYaw   = this.yaw;
    this._fromPitch = this.pitch;
    this._fromZoom  = this.zoom;
    this._toYaw     = this.yaw;
    this._toPitch   = this.pitch;
    this._toZoom    = this.zoom;
    this._lerpElapsed = 0;
    this._lerpTotal   = 0;
    this._lerpType    = LerpType.SNAP;

    const w = canvas.clientWidth  || 800;
    const h = canvas.clientHeight || 600;
    this.cam = new THREE.PerspectiveCamera(45, w / h, 0.1, 2000);

    this._dragging  = false;
    this._panning   = false;
    this._lastX     = 0;
    this._lastY     = 0;

    // Pre-allocated vectors for pan math (avoids allocations every frame)
    this._vRight = new THREE.Vector3();
    this._vUp    = new THREE.Vector3();
    this._vFwd   = new THREE.Vector3();

    this._bindEvents();
    this._syncCamera();
  }

  // ── Script-driven camera pose ─────────────────────────────────────────────

  scriptDrive(yaw, pitch, zoom, lerpType, lerpTicks) {
    this._fromYaw   = this.yaw;
    this._fromPitch = this.pitch;
    this._fromZoom  = this.zoom;
    this._toYaw     = yaw;
    this._toPitch   = pitch;
    this._toZoom    = zoom > 0 ? zoom : this.zoom;
    this._lerpType  = lerpType || LerpType.SNAP;

    const isSnap = lerpType === LerpType.SNAP || lerpTicks <= 0;
    this._lerpTotal   = isSnap ? 0 : lerpTicks / 20; // ticks → seconds
    this._lerpElapsed = 0;

    if (isSnap) {
      this.yaw   = this._toYaw;
      this.pitch = this._toPitch;
      this.zoom  = this._toZoom;
      this._syncCamera();
    }
  }

  setTarget(x, y, z) { this.target.set(x, y, z); }

  // ── Per-frame update ───────────────────────────────────────────────────────

  tick(dt) {
    if (this._lerpTotal > 0) {
      this._lerpElapsed += dt;
      const t = applyLerp(this._lerpElapsed / this._lerpTotal, this._lerpType);
      this.yaw   = lerp(this._fromYaw,   this._toYaw,   t);
      this.pitch = lerp(this._fromPitch, this._toPitch, t);
      this.zoom  = lerp(this._fromZoom,  this._toZoom,  t);
      if (this._lerpElapsed >= this._lerpTotal) {
        this.yaw   = this._toYaw;
        this.pitch = this._toPitch;
        this.zoom  = this._toZoom;
        this._lerpTotal = 0;
      }
    }
    this._syncCamera();
  }

  onResize(w, h) {
    this.cam.aspect = w / h;
    this.cam.updateProjectionMatrix();
  }

  // ── Camera math ────────────────────────────────────────────────────────────

  _syncCamera() {
    const { THREE } = this;
    const yRad = THREE.MathUtils.degToRad(this.yaw);
    const pRad = THREE.MathUtils.degToRad(this.pitch);
    const cosP = Math.cos(pRad);

    this.cam.position.set(
      this.target.x + this.zoom * Math.sin(yRad) * cosP,
      this.target.y + this.zoom * Math.sin(pRad),
      this.target.z + this.zoom * Math.cos(yRad) * cosP,
    );
    this.cam.lookAt(this.target);
  }

  // ── Input events ───────────────────────────────────────────────────────────

  _bindEvents() {
    const el = this.canvas;
    el.addEventListener('mousedown',   e => this._onDown(e));
    window.addEventListener('mousemove',  e => this._onMove(e));
    window.addEventListener('mouseup',    () => { this._dragging = false; });
    el.addEventListener('wheel',       e => this._onWheel(e), { passive: false });
    el.addEventListener('contextmenu', e => e.preventDefault());
  }

  _onDown(e) {
    e.preventDefault();
    this._dragging = true;
    this._lastX    = e.clientX;
    this._lastY    = e.clientY;
    // button 0 = left (orbit), 1 = middle (pan), 2 = right (pan)
    this._panning  = e.button === 1 || e.button === 2;
    // Cancel any script-driven lerp so manual control feels instant
    this._lerpTotal = 0;
  }

  _onMove(e) {
    if (!this._dragging) return;
    const dx = e.clientX - this._lastX;
    const dy = e.clientY - this._lastY;
    this._lastX = e.clientX;
    this._lastY = e.clientY;
    if (!dx && !dy) return;

    if (this._panning) {
      this._pan(dx, dy);
    } else {
      // Orbit
      this.yaw   -= dx * 0.4;
      this.pitch  = Math.max(-89, Math.min(89, this.pitch - dy * 0.3));
    }
  }

  _pan(dx, dy) {
    const { THREE } = this;

    // Compute camera's right and up vectors from its current pose
    // right = normalize(cam.position - target) × worldUp  ... no, easier:
    // Use the camera matrix directly.
    this.cam.updateMatrix();
    this.cam.updateMatrixWorld();

    // Right vector = column 0 of camera world matrix
    this._vRight.setFromMatrixColumn(this.cam.matrixWorld, 0);
    // Up vector = column 1 of camera world matrix
    this._vUp.setFromMatrixColumn(this.cam.matrixWorld, 1);

    // Pan speed proportional to distance so far targets feel the same
    const speed = this.zoom * 0.0015;

    // dx > 0 = mouse moved right → move target right (positive right vec)
    // dy > 0 = mouse moved down  → move target down (negative up vec)
    this.target.addScaledVector(this._vRight, -dx * speed);
    this.target.addScaledVector(this._vUp,     dy * speed);
  }

  _onWheel(e) {
    e.preventDefault();
    const factor = e.deltaY > 0 ? 1.12 : 0.89;
    this.zoom = Math.max(1, Math.min(600, this.zoom * factor));
    this._lerpTotal = 0;
  }
}
