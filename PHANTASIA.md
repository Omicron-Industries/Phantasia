# Phantasia

Phantasia is a Minecraft Forge mod that provides interactive 3D documentation and guided walkthroughs for complex GregTech multiblock machinery. It lets pack authors write in-game tutorials — step-by-step assembly guides, multi-machine scene layouts, reference pages — that players access by holding a keybind while looking at a machine or holding a relevant item.

---

## Core Concepts

There are three content types, each stored as JSON files under `phantasia/` in the game directory:

| Type | Folder | Purpose |
|---|---|---|
| **Guide** | `phantasia/guides/` | Text + item reference pages, no 3D viewport |
| **Script** | `phantasia/scripts/` | Step-by-step walkthrough for a single machine |
| **Scene** | `phantasia/scenes/` | Multi-machine layout with coordinated steps |

All three support an in-game editor so authors never need to touch JSON manually.

---

## Guides

A guide is a standalone reference document made of **pages**. Each page can have:

- A `headline` (rendered at 1.5× scale)
- Body `text` (supports `§` color codes, auto-wraps)
- An `items` strip — item icons with labels, descriptions, and type coloring (`input` / `output` / `catalyst`)
- Links to other guides, scripts, or scenes for cross-referencing
- A `microsceneId` on any item to open a 3D scene when the item is clicked

**Item animation tracks** — each item in a strip can animate: `left`, `right`, `up`, `down`, or `pulse`, with a configurable `trackDuration` in ticks.

**Tooltip items** — a `tooltipItems` list of item/block registry IDs causes the Phantasia hold-tooltip to appear whenever the player holds or looks at one of those items, and activating it opens the guide.

---

## Scripts

A script is a step-by-step walkthrough for one multiblock machine. The machine is specified by its registry ID (e.g. `gtceu:fusion_reactor`). Scripts contain:

### Steps

Each step has a `tick` index and controls:

- **`caption` / `description`** — text shown in the side panel
- **`show`** — visibility mode for the machine structure:
  - `"all"` — show everything
  - `"layer"` — show only one Y layer
  - `"layerMin"` / `"layerMax"` — show a Y range
  - `"single"` — show only listed positions
  - `"none"` — hide everything
- **`positions` / `hidePositions`** — explicit block positions to show or hide
- **`hideLayer`** — hide a specific Y level
- **`working`** — whether the machine renders as actively processing
- **`fakeRecipeId`** — a GregTech recipe ID to load as a visual-only animation
- **`showItems`** — whether the item strip is visible this step
- **`items`** — per-step item strip override
- **`camera`** — smooth camera transition for this step (see Camera below)

### Camera

Each step can move the camera smoothly:

```
yaw         rotation in degrees (0 = north, +90 = west)
pitch       vertical angle (0 = horizontal, -90 = straight down)
zoom        distance in world units
lerpType    SNAP | LINEAR | EASE_IN | EASE_OUT | EASE_IN_OUT | SPRING
lerpTicks   duration of the interpolation in ticks
```

A `startCamera` block at the script root sets the initial position when the screen opens.

### Optional Groups (manual variants)

Scripts can declare `optionalGroups` — named sets of positions where the player can toggle between a primary block and a fallback. These appear in the Variants panel and persist across sessions.

---

## Scenes

A scene places multiple machines at specified offsets from a shared origin and coordinates them through shared steps.

### Placements

Each placement has:
- `machine` — registry ID of the multiblock
- `x`, `y`, `z` — offset from scene origin
- `items` — recipe-hint items shown for this placement

### Steps

Scene steps work the same as script steps but also support **`machineOverrides`** — a map of placement index → override block:

```
show            per-placement visibility mode
layer/Min/Max   per-placement layer control
hidePositions   per-placement position culling
fakeRecipeId    per-placement recipe animation
particleEffects list of particle IDs to spawn on this placement
machineWorking  override working state (null = inherit global)
```

This lets one step show machine A running, machine B idle, and machine C hidden simultaneously.

### Mistakes

Scenes (and scripts) support a `mistakes` array — layout validation annotations:

```
id            unique identifier
description   text shown to player
severity      INFO (blue) | WARNING (amber) | ERROR (red)
placements    list of placement indices this mistake involves
```

Mistakes are shown as colored overlays on the relevant machines.

---

## Variants

Variants let players toggle between block alternatives at runtime without rebuilding the pattern.

### Auto-detected variants

When a script loads, Phantasia automatically scans the machine's available shapes and produces variant groups for:

- **Hatch / Bus / Muffler tiers** — positions whose block differs between shapes
- **Optional blocks** — positions that appear in some shapes but not others (e.g. fusion glass vs. machine casing)

### Manual variants (via `optionalGroups`)

Authors can define explicit groups in a script's `optionalGroups`:

```json
{
  "id": "fusion_glass",
  "label": "Fusion Glass",
  "category": "optional",
  "shownByDefault": true,
  "positions": [{"x": 0, "y": 0, "z": 0}]
}
```

Categories: `optional`, `hatches_buses`, `mufflers`, `casings`

Variant selections — including which structure size (shape index) is displayed — are persisted to `phantasia_variants.json` and restored whenever the same machine is opened.

---

## Keybind & Tooltip System

Phantasia has a configurable keybind (unbound by default). While the player holds it:

1. A toast appears at the bottom of the screen showing the matched machine/guide name and a progress bar.
2. After a configurable hold duration (default 20 ticks), the relevant screen opens.

**What triggers the tooltip:**

- Holding a `MetaMachineItem` (a GregTech multiblock item)
- Looking at a `MetaMachineBlock` in the world
- Holding or looking at any item/block listed in a guide's or scene's `tooltipItems`

**What opens:**

- Exactly one match → opens that scene viewer or guide directly
- Multiple matches → opens `PhantasiaContextualSelectionScreen` to pick

The `[KEY]` text in the tooltip is fully blue (`§b[KEY§b]`).

---

## Theme System

Phantasia's UI is fully skinnable. A theme defines 12 color fields plus a baseplate block:

| Field | Role |
|---|---|
| `bg` | Screen background |
| `panel` | Side panel / card backgrounds |
| `accent` | Highlight color, selected items |
| `btn` / `btnHov` / `btnAct` | Button states |
| `text` | Primary text |
| `dim` | Secondary / disabled text |
| `tlBg` | Timeline background |
| `prog` | Progress bars |
| `warn` | Warning indicators |
| `hilight` | Special highlights |
| `baseplateBlock` | Block ID for the scene floor (`minecraft:air` = no floor) |

Colors accept hex strings (`FF4FC3F7`) or dynamic keywords:

| Keyword | Effect |
|---|---|
| `RAINBOW` | Fast animated hue cycle |
| `PASTEL_RAINBOW` | Softer hue cycle |
| `GALAXY` | Slow purple-violet sweep |
| `AURORA` | Green-teal northern lights |
| `MAGMA` | Lava glow pulse |
| `NONE` / `TRANSPARENT` | Fully transparent |

**Built-in themes:** COBALT, RAINBOW, AMETHYST, MINECRAFT, CRIMSON, EMERALD, VOID, CYBERPUNK, QUARTZ, LIGHT_RAINBOW

Custom themes are saved as JSON files in `phantasia/themes/` and the active theme name is persisted in `phantasia/themes/active.txt`.

---

## Editors

Every content type has a full in-game editor — no external tools required.

| Editor | What it edits |
|---|---|
| **Guide Editor** | Pages, headlines, text, items, cross-links, tooltip items |
| **Scene Editor** | Placements, steps, machine overrides, mistakes, tooltip items |
| **Script Editor** | Steps, camera, visibility, fake recipes, items, optional groups |
| **Placement Editor** | Machine ID, XYZ offset, per-placement items |
| **Mistakes Editor** | Severity, description, placement indices |
| **Item Editor** | Item ID, count, label, description, type, animation, microscene |
| **Theme Editor** | All color fields, baseplate block, save/load/undo |
| **Hide-Pos Editor** | Spatial position picker for `hidePositions` fields |

Editors support undo (Ctrl+Z), live preview, and immediate save-to-disk.

---

## Rendering

The 3D viewport renders into a **shared dummy world** (`PhantasiaTrackedDummyWorld`) that is separate from the actual game world. Key rendering features:

- **Layer culling** — show/hide individual Y layers of a structure
- **Position isolation** — highlight specific blocks while dimming others
- **Variant substitution** — swap block states at render time without modifying the pattern
- **Coil state** — GTCEu coil blocks animate visually when `working = true`
- **Fake recipes** — input/output item animations driven by a GregTech recipe
- **Particle effects** — per-placement particle spawning
- **Baseplate** — a configurable floor block extends around the structure (or disabled with `minecraft:air`)

Pattern loading for large machines is asynchronous (`PhantasiaPatternLoader`) with a progress indicator so the screen stays responsive during load.

---

## Web Export

`PhantasiaWebExport` serializes all registered scenes, scripts, guides, and their underlying block patterns to a JSON bundle. This is intended for use in external web documentation tools.

---

## GregTech Integration

Phantasia is built around GTCEu's multiblock system:

- Machine definitions come from `MultiblockMachineDefinition` (registered via GTCEu's addon system)
- Shapes come from `MultiblockShapeInfo` — the same data GTCEu uses for structure validation
- Each `BlockInfo` in a shape is stamped into the dummy world exactly as GTCEu would place it
- Block entity rendering uses GTCEu's own renderers, so machines look identical to in-world
- Recipe simulation reads live GTCEu recipe data (no custom recipe format needed)
- Hatch/bus/muffler tier detection is driven by GTCEu's `PartAbility` system

---

## File Layout

```
phantasia/
  guides/
    <namespace>/
      <name>.json
  scripts/
    <namespace>/
      <name>.json
  scenes/
    <namespace>/
      <name>.json
  themes/
    active.txt
    <custom_theme>.json
phantasia_variants.json     (in Forge config dir — variant + shape selections)
```

All files are plain JSON and can be edited by hand or via the in-game editors. Changes are hot-reloaded when the relevant screen is opened.
