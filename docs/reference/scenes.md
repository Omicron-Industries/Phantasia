# Scene JSON Reference

Scenes define a spatial arrangement of multiple multiblock machines shown together in a shared 3D preview. Each machine has its own script-style step overrides, so you can walk players through a full factory section in one guided sequence.

**File location:** `<gameDir>/phantasia/scenes/<namespace>/<name>.json`

Like guides, scenes live in the world directory and are created/edited in-game via the Scene Editor (creative mode → `/phantasia` → Scenes → **✏ Edit**).

---

## Top-Level Fields

```json
{
  "id":           "yourmod:ore_processing_line",
  "name":         "Ore Processing Line",
  "iconItem":     "minecraft:hopper",
  "tooltipItems": ["minecraft:iron_ore"],
  "placements":   [ ... ],
  "mistakes":     [ ... ],
  "steps":        [ ... ]
}
```

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `id` | string | required | Unique identifier in `namespace:name` format |
| `name` | string | required | Display name shown on the card |
| `iconItem` | string | null | Registry ID of the card icon item |
| `tooltipItems` | string[] | [] | Block/item IDs that trigger hold-\[P\] to open this scene |
| `placements` | PlacementData[] | required | The machines in this scene and their world-space positions |
| `mistakes` | SceneMistakeData[] | [] | Layout validation warnings |
| `steps` | SceneStepData[] | [] | The guided walkthrough steps |

---

## PlacementData

One machine placed in the scene.

```json
{
  "machine": "gtceu:electric_blast_furnace",
  "x":       0,
  "y":       0,
  "z":       0,
  "items":   [ ... ]
}
```

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `machine` | string | required | Registry ID of the multiblock |
| `x`, `y`, `z` | int | 0 | World-space offset from the scene origin in blocks |
| `items` | ItemConditionData[] | [] | Items shown in the panel when this placement is the focus |

Machine placement positions are absolute — place them to reflect a realistic factory layout. The viewer renders all machines at their real relative positions.

---

## SceneStepData

One step in the guided walkthrough.

```json
{
  "tick":             0,
  "caption":          "The EBF smelts raw ore into ingots.",
  "show":             "all",
  "machineOverrides": {
    "0": {
      "show":          "all",
      "layer":         0,
      "layerMin":      0,
      "layerMax":      0,
      "hideLayer":     -1,
      "hidePositions": [],
      "working":       true,
      "fakeRecipeId":  "gtceu:ebf_iron"
    },
    "1": {
      "show": "all",
      "working": false
    }
  },
  "camera": {
    "yaw":       -135.0,
    "pitch":     -30.0,
    "zoom":      5.0,
    "lerpType":  "SPRING",
    "lerpTicks": 25
  }
}
```

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `tick` | int | required | Absolute tick offset for this step |
| `caption` | string | null | Text shown in the caption strip |
| `show` | string | "all" | Default visibility for all placements (overridden per-machine) |
| `machineOverrides` | map | {} | Per-placement visibility overrides, keyed by placement index (string) |
| `camera` | CameraData | null | Camera movement — pans over the full scene |

### MachineOverride Fields

Each value in `machineOverrides` supports a subset of script step fields:

| Field | Description |
|-------|-------------|
| `show` | Visibility mode for this machine — same options as script steps |
| `layer` | Y-layer when `show == "layer"` |
| `layerMin`, `layerMax` | Range when `show == "layers"` |
| `hideLayer` | Exclude a specific Y-layer (`-1` = off) |
| `hidePositions` | Always-hidden positions in local space |
| `working` | Show a fake recipe animation on this machine |
| `fakeRecipeId` | Which recipe to animate |

The key in `machineOverrides` is the zero-based index of the placement in the `placements` array, written as a string (`"0"`, `"1"`, etc.).

---

## SceneMistakeData

Layout validation rule that flags a known problem with the scene's arrangement.

```json
{
  "id":          "too_close",
  "description": "The EBF and Chemical Reactor are too close — leave 2 blocks of clearance.",
  "severity":    "WARNING",
  "placements":  [0, 1]
}
```

| Field | Type | Description |
|-------|------|-------------|
| `id` | string | Unique rule identifier |
| `description` | string | Warning message shown to the player |
| `severity` | string | `"INFO"`, `"WARNING"`, or `"ERROR"` |
| `placements` | int[] | Indices of the placements involved in this mistake |

Mistakes appear in the scene's guide view (if linked) to warn players before they build.

---

## CameraData

See [scripts.md — CameraData](scripts.md#cameradata) for the full field reference. Scenes use exactly the same camera definition. The camera views the full scene — zoom out far enough to see all machines.

---

## Full Example

```json
{
  "id": "yourmod:ore_processing_line",
  "name": "Ore Processing Line",
  "iconItem": "minecraft:hopper",
  "tooltipItems": ["minecraft:raw_iron"],
  "placements": [
    {
      "machine": "gtceu:electric_blast_furnace",
      "x": 0, "y": 0, "z": 0,
      "items": [
        {
          "item": "minecraft:raw_iron",
          "count": 1,
          "label": "Raw Iron",
          "type": "input"
        }
      ]
    },
    {
      "machine": "gtceu:large_chemical_reactor",
      "x": 0, "y": 0, "z": 20,
      "items": []
    },
    {
      "machine": "gtceu:macerator",
      "x": -10, "y": 0, "z": 10,
      "items": []
    }
  ],
  "mistakes": [
    {
      "id": "ebf_clearance",
      "description": "Leave at least 2 blocks between the EBF and Chemical Reactor for pipe routing.",
      "severity": "WARNING",
      "placements": [0, 1]
    }
  ],
  "steps": [
    {
      "tick": 0,
      "caption": "Overview — three machines work together to process ore into refined products.",
      "machineOverrides": {
        "0": { "show": "all" },
        "1": { "show": "all" },
        "2": { "show": "all" }
      },
      "camera": {
        "yaw": -135.0, "pitch": -35.0, "zoom": 8.0,
        "lerpType": "SPRING", "lerpTicks": 30
      }
    },
    {
      "tick": 80,
      "caption": "Step 1 — The Macerator crushes raw ore into crushed ore.",
      "machineOverrides": {
        "0": { "show": "all" },
        "1": { "show": "all" },
        "2": { "show": "all", "working": true, "fakeRecipeId": "gtceu:macerator_iron_ore" }
      },
      "camera": {
        "yaw": -90.0, "pitch": -30.0, "zoom": 5.0,
        "lerpType": "SPRING", "lerpTicks": 25
      }
    },
    {
      "tick": 160,
      "caption": "Step 2 — The EBF smelts crushed ore into ingots at high temperature.",
      "machineOverrides": {
        "0": { "show": "all", "working": true, "fakeRecipeId": "gtceu:ebf_iron" },
        "1": { "show": "all" },
        "2": { "show": "all" }
      },
      "camera": {
        "yaw": -135.0, "pitch": -35.0, "zoom": 5.0,
        "lerpType": "SPRING", "lerpTicks": 25
      }
    },
    {
      "tick": 240,
      "caption": "Step 3 — The Chemical Reactor refines the output into final products.",
      "machineOverrides": {
        "0": { "show": "all" },
        "1": { "show": "all", "working": true },
        "2": { "show": "all" }
      },
      "camera": {
        "yaw": -160.0, "pitch": -25.0, "zoom": 5.0,
        "lerpType": "SPRING", "lerpTicks": 25
      }
    }
  ]
}
```
