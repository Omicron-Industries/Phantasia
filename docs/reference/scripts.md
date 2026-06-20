# Script JSON Reference

Scripts define step-by-step 3D walkthroughs for a single multiblock machine.

**File location:** `data/<modid>/phantasia/scripts/<machine_namespace>/<machine_name>.json`

Example: a script for `gtceu:electric_blast_furnace` goes in
`data/yourmod/phantasia/scripts/gtceu/electric_blast_furnace.json`

---

## Top-Level Fields

```json
{
  "machine":        "gtceu:electric_blast_furnace",
  "startCamera":    { ... },
  "scriptDuration": 600,
  "expandable":     false,
  "recipeId":       null,
  "items":          [ ... ],
  "mistakes":       [ ... ],
  "globalMistakes": [ ... ],
  "optionalGroups": [ ... ],
  "steps":          [ ... ]
}
```

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `machine` | string | required | Registry ID of the multiblock |
| `startCamera` | CameraData | null | Camera state when the viewer first opens |
| `scriptDuration` | int | -1 | Total tick duration (`-1` = auto from last step tick) |
| `expandable` | bool | false | Whether the machine supports size variants |
| `recipeId` | string | null | Lock to a specific recipe (Ars Nouveau, etc.) |
| `items` | ItemConditionData[] | [] | Items shown globally in the panel |
| `mistakes` | MistakeData[] | [] | Coordinate-specific warnings |
| `globalMistakes` | string[] | [] | Free-text warnings not tied to positions |
| `optionalGroups` | OptionalGroupData[] | [] | Swappable block variant definitions |
| `steps` | StepData[] | [] | The walkthrough steps |

---

## CameraData

Used in `startCamera` and `step.camera`.

```json
{
  "yaw":       -135.0,
  "pitch":     -35.0,
  "zoom":      3.0,
  "lerpType":  "SPRING",
  "lerpTicks": 25
}
```

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `yaw` | float | -135 | Horizontal rotation in degrees (−180 to 180) |
| `pitch` | float | -35 | Vertical rotation in degrees (−90 to 90) |
| `zoom` | float | -1 | Distance from target. `-1` leaves zoom unchanged. |
| `lerpType` | string | "SNAP" | Animation easing — see table below |
| `lerpTicks` | int | 0 | Duration of animation in ticks (20 ticks = 1 second) |

### Lerp Types

| Value | Behaviour |
|-------|-----------|
| `"SNAP"` | Instant — no animation |
| `"LINEAR"` | Constant speed throughout |
| `"EASE_IN"` | Starts slow, accelerates |
| `"EASE_OUT"` | Starts fast, decelerates naturally |
| `"EASE_IN_OUT"` | Slow → fast → slow |
| `"SPRING"` | Overshoots slightly then settles. Recommended for most transitions — feels alive. |

A good default for most steps: `"lerpType": "SPRING", "lerpTicks": 25`

---

## StepData

One entry in the `steps` array.

```json
{
  "tick":          0,
  "caption":       "Place the outer casing walls.",
  "show":          "layer",
  "layer":         1,
  "layerMin":      0,
  "layerMax":      0,
  "positions":     [],
  "hideLayer":     -1,
  "hidePositions": [],
  "working":       false,
  "fakeRecipeId":  null,
  "hold":          null,
  "layerCount":    -1,
  "showItems":     true,
  "items":         [],
  "worldItems":    [],
  "camera":        { ... }
}
```

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `tick` | int | required | Absolute tick offset for this step |
| `caption` | string | null | Text shown in the caption strip during this step |
| `show` | string | "all" | Block visibility mode — see Show Modes below |
| `layer` | int | 0 | Y-layer index when `show == "layer"` |
| `layerMin` | int | 0 | Min Y-layer when `show == "layers"` |
| `layerMax` | int | 0 | Max Y-layer when `show == "layers"` |
| `positions` | int[3][] | [] | Explicit positions when `show == "pos"` |
| `hideLayer` | int | -1 | Subtract a specific Y-layer from the visible set (`-1` = off) |
| `hidePositions` | int[3][] | [] | Always hide these positions regardless of show mode |
| `working` | bool | false | Show a fake recipe animation overlay |
| `fakeRecipeId` | string | null | Recipe to display in the working animation (e.g. `"gtceu:ebf_iron"`) |
| `hold` | string | null | Named pause condition (advanced) |
| `layerCount` | int | -1 | Force a specific expansion layer count (`-1` = no change) |
| `showItems` | bool | true | Whether the item panel is visible during this step |
| `items` | ItemConditionData[] | [] | Step-specific item panel entries (merged with global) |
| `worldItems` | WorldItemEntry[] | [] | Item overrides on specific block entities |
| `camera` | CameraData | null | Camera movement for this step |

### Show Modes

| Value | What's visible |
|-------|---------------|
| `"all"` | Every block in the structure |
| `"layer"` | Only the Y-layer at `layer` |
| `"layers"` | Y-layers from `layerMin` through `layerMax` |
| `"pos"` | Only blocks at the explicit `positions` list |
| `"parts"` | All functional part blocks (hatches, coils, etc.) |
| `"controller"` | Only the controller block |
| `"functional"` | All functional blocks |
| `"parts:<expr>"` | Parts expression — see below |

### Parts Expressions

Parts expressions filter by block category using a mini query language:

| Expression | Selects |
|------------|---------|
| `"parts:@coil"` | All coil-type blocks |
| `"parts:@type(parts)"` | All blocks tagged as "parts" |
| `"parts:hatch"` | All hatch blocks |
| `"parts:hatch \| @coil"` | Hatches OR coils (pipe = OR) |
| `"parts:gtceu:some_block"` | Exactly one specific registry ID |

---

## OptionalGroupData

Defines a set of blocks that the player can toggle in the Variants panel.

```json
{
  "id":             "fusion_glass",
  "label":          "Fusion Glass",
  "category":       "optional",
  "shownByDefault": true,
  "primaryBlock":   "gtceu:fusion_glass",
  "fallbackBlock":  "gtceu:heatproof_machine_casing",
  "autoDetected":   false,
  "positions":      []
}
```

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `id` | string | required | Unique identifier for this group |
| `label` | string | required | Display name in the Variants panel |
| `category` | string | "optional" | UI grouping: `"optional"`, `"hatches_buses"`, `"mufflers"`, `"casings"` |
| `shownByDefault` | bool | true | Whether primary (`true`) or fallback (`false`) is shown by default |
| `primaryBlock` | string | null | Registry ID of the primary block. `null` = auto-detect from GTCEu pattern. |
| `fallbackBlock` | string | null | Registry ID of the fallback block |
| `autoDetected` | bool | false | Set by the engine; marks groups populated automatically |
| `positions` | PositionOverride[] | [] | Per-position block overrides within the group |

---

## MistakeData

A coordinate-specific warning flag shown in the viewer.

```json
{
  "x":     1,
  "y":     0,
  "z":     1,
  "label": "Wrong coil tier for this recipe.",
  "color": "FFB74D"
}
```

| Field | Type | Description |
|-------|------|-------------|
| `x`, `y`, `z` | int | Local-space coordinates of the offending block |
| `label` | string | Warning message shown to the player |
| `color` | string | Hex RGB colour of the highlight marker (no `#`) |

---

## ItemConditionData

Used in `items`, `step.items`, and `placement.items`.

```json
{
  "item":              "gtceu:cupronickel_coil_block",
  "count":             4,
  "label":             "Heating Coils",
  "type":              "input",
  "description":       "§7At least Cupronickel tier.§r",
  "track":             "none",
  "trackDurationTicks": 20
}
```

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `item` | string | required | Registry ID of the item/block |
| `count` | int | 1 | Stack size hint shown in the panel |
| `label` | string | null | Short label below the icon |
| `type` | string | "input" | `"input"`, `"output"`, `"catalyst"`, `"optional"` |
| `description` | string | null | Rich text description shown on hover |
| `track` | string | "none" | Animation preset (`"none"`, `"pulse"`, `"highlight"`) |
| `trackDurationTicks` | int | 20 | Duration of the track animation |

---

## WorldItemEntry

Places an item inside a block entity at a specific position, used for fake recipe displays.

```json
{
  "x":            0,
  "y":            0,
  "z":            0,
  "item":         "minecraft:diamond",
  "sourceAmount": -1
}
```

| Field | Type | Description |
|-------|------|-------------|
| `x`, `y`, `z` | int | Local-space coordinates of the target block |
| `item` | string | Registry ID of the item to place in slot 0 |
| `sourceAmount` | int | Ars Nouveau: source charge to set on a source jar (`-1` = ignore) |

---

## Full Example

```json
{
  "machine": "gtceu:electric_blast_furnace",
  "startCamera": {
    "yaw": -135.0,
    "pitch": -35.0,
    "zoom": 3.5,
    "lerpType": "SNAP",
    "lerpTicks": 0
  },
  "globalMistakes": [
    "The EBF must have exactly one Muffler Hatch."
  ],
  "optionalGroups": [
    {
      "id": "coil_tier",
      "label": "Coil Tier",
      "category": "casings",
      "shownByDefault": true,
      "primaryBlock": "gtceu:cupronickel_coil_block",
      "fallbackBlock": null,
      "positions": []
    }
  ],
  "steps": [
    {
      "tick": 0,
      "caption": "Start with the bottom casing ring.",
      "show": "layer",
      "layer": 0,
      "camera": {
        "yaw": -135.0,
        "pitch": -20.0,
        "zoom": 3.5,
        "lerpType": "SPRING",
        "lerpTicks": 25
      }
    },
    {
      "tick": 60,
      "caption": "Add three rings of Cupronickel Heating Coils.",
      "show": "layers",
      "layerMin": 1,
      "layerMax": 3,
      "camera": {
        "yaw": -135.0,
        "pitch": -35.0,
        "zoom": 3.0,
        "lerpType": "SPRING",
        "lerpTicks": 25
      }
    },
    {
      "tick": 120,
      "caption": "Cap with the top casing ring, muffler, and maintenance hatch.",
      "show": "layer",
      "layer": 4,
      "camera": {
        "yaw": -135.0,
        "pitch": -50.0,
        "zoom": 3.0,
        "lerpType": "SPRING",
        "lerpTicks": 25
      }
    },
    {
      "tick": 180,
      "caption": "Done! Attach hatches and energy input on the outer wall.",
      "show": "parts",
      "camera": {
        "yaw": -90.0,
        "pitch": -25.0,
        "zoom": 4.0,
        "lerpType": "SPRING",
        "lerpTicks": 30
      }
    }
  ]
}
```
