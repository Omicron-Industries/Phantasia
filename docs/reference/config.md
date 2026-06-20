# Config Reference

Phantasia stores its config at `config/phantasia.yaml` (all settings are client-side).

The settings panel in `/phantasia` → Settings exposes the most commonly changed options. A handful of advanced options are only available by editing the file directly.

---

## Full File Layout

```yaml
phantasiaUI:
  # ── Display ─────────────────────────────────────────────────────────────────
  displayMode: TOOLTIP_JADE
  activationTicks: 20

  # ── Camera ──────────────────────────────────────────────────────────────────
  scriptLockCamera: true
  autoPlayScripts: true
  cameraSensitivity: 1.0
  scrollZoomSpeed: 1.0

  # ── Rendering ───────────────────────────────────────────────────────────────
  showBaseplate: true
  baseplateBlock: minecraft:deepslate_bricks
  streamingMode: BALANCED

  # ── World / Server (file-only) ───────────────────────────────────────────────
  giveBookOnFirstJoin: true
  animateTickBudget: 2048
```

---

## Settings Panel Options

These are visible and editable in the in-game Settings tab.

### Display

**`displayMode`**
Controls where the hold-to-open indicator appears.

| Value | Description |
|-------|-------------|
| `TOOLTIP_ONLY` | Standard item tooltip only |
| `JADE_ONLY` | Jade/WTHIT overlay only |
| `HOTBAR_ONLY` | Hotbar toast only |
| `TOOLTIP_JADE` | Tooltip + Jade (default) |
| `TOOLTIP_HOTBAR` | Tooltip + hotbar toast |

**`activationTicks`** `int` · Default: `20`
How many ticks you must hold \[P\] before the viewer opens. 20 ticks = 1 second. Lower values open faster; higher values reduce accidental opens.

---

### Camera

**`scriptLockCamera`** `bool` · Default: `true`
- `true` — Walkthroughs drive your camera (recommended). Players can toggle the lock button in the viewer's timeline bar at any time.
- `false` — The camera is always free; walkthrough scripts never move it.

**`autoPlayScripts`** `bool` · Default: `true`
- `true` — The walkthrough starts playing automatically when you open a machine.
- `false` — The viewer opens paused; you advance manually.

**`cameraSensitivity`** `float` · Default: `1.0`
Multiplier for orbit speed when clicking and dragging the 3D view. Higher values rotate faster.

**`scrollZoomSpeed`** `float` · Default: `1.0`
Multiplier for zoom speed when scrolling. Higher values zoom faster.

---

### Rendering

**`showBaseplate`** `bool` · Default: `true`
Toggles the decorative floor rendered beneath machines in the 3D preview.

**`baseplateBlock`** `string` · Default: `minecraft:deepslate_bricks`
Registry ID of the block used for the decorative floor. Any valid block ID works.

**`streamingMode`** `enum` · Default: `BALANCED`
Controls the block-count threshold at which the renderer switches to async streaming.

| Value | Threshold | Best for |
|-------|-----------|----------|
| `PERFORMANCE` | 500 blocks | Weak hardware, strict FPS targets. Blocks may pop in progressively. |
| `BALANCED` | 4,000 blocks | Most players. Good compromise between visual smoothness and GPU load. |
| `QUALITY` | 20,000 blocks | High-end systems. Full structure baked before display — no pop-in, but a larger one-time GPU spike on open. |

---

## File-Only Options

These options are not shown in the Settings panel and must be edited directly in `config/phantasia.yaml`.

**`giveBookOnFirstJoin`** `bool` · Default: `true`
Whether the Phantasia Manual is automatically placed in a new player's inventory (slot 8 preferred, else first empty slot) on their first join. Set to `false` if you distribute the manual via loot tables or starter kits instead.

**`animateTickBudget`** `int` · Default: `2048`
Maximum animation ticks processed per render frame. Lowering this reduces frame time spikes when opening large machines but may make the initial preview appear more gradually. For very large modpacks on slow hardware, try `512` or `1024`.
