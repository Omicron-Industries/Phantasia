# Phantasia — Pack Author Guide

This guide covers everything you need to add Phantasia content to a modpack: scripts, guides, scenes, config distribution, and editor workflows.

---

## Content Types at a Glance

| Type | What it is | Where the file lives |
|------|-----------|---------------------|
| **Script** | Step-by-step walkthrough for a single multiblock | Mod datapack — `data/<modid>/phantasia/scripts/` |
| **Guide** | Multi-page text document with item cards | World directory — `<gameDir>/phantasia/guides/` |
| **Scene** | Layout of multiple machines shown together | World directory — `<gameDir>/phantasia/scenes/` |
| **Config** | Client settings (streaming, camera, etc.) | `config/phantasia.yaml` |

Scripts ship inside a mod or modpack datapack so they're version-controlled and distributed automatically. Guides and scenes live in the world folder so they can be created/edited in-game with the built-in editors, then exported and committed to the pack.

---

## In-Game Editors (Creative Mode)

All three content types have full in-game editors. Open them by going to `/phantasia`, finding the relevant card, and clicking the **✏ Edit** button (only visible in creative mode).

- **Script editor** — Visual step editor with live 3D preview, camera controls, per-step visibility, and caption input
- **Guide editor** — WYSIWYG page editor with headline, body text (colour codes supported), and item cards
- **Scene editor** — Machine placement canvas, per-placement visibility overrides, mistake rule builder

After saving, content reloads immediately without a restart. Export the saved files from the world folder and commit them to your pack.

---

## File & Folder Layout

```
<gameDir>/
├── phantasia/
│   ├── guides/
│   │   └── yourmod/
│   │       └── my_guide.json
│   └── scenes/
│       └── yourmod/
│           └── ore_line.json
└── config/
    └── phantasia.yaml

modpack datapack/
└── data/
    └── yourmod/
        └── phantasia/
            └── scripts/
                └── gtceu/
                    └── electric_blast_furnace.json
```

---

## GTCEu Integration

If your pack includes GregTech CEu (or a fork), Phantasia **automatically discovers all multiblocks** — no registration step required. Every multiblock appears in the Multiblocks tab of `/phantasia` immediately.

Phantasia reads the `MultiblockShapeInfo` from GTCEu to render machines in the 3D preview. Scripts add walkthroughs on top; machines without scripts still show a full static preview.

Auto-detection also handles:
- **Coil variant groups** — `.or()` coil swap patterns become variant toggles automatically
- **Fusion glass** — Reactor glass/casing variants are detected and made optional
- **Expandable machines** — Tiled multiblocks expose a size stepper in the viewer

---

## Script Reference

See [`reference/scripts.md`](reference/scripts.md) for the full JSON schema. Key concepts:

### Steps

Each step defines what the player sees at that point in the walkthrough:

```json
{
  "tick": 0,
  "caption": "Place the casing walls.",
  "show": "layer",
  "layer": 1,
  "camera": {
    "yaw": -135.0,
    "pitch": -30.0,
    "zoom": 3.0,
    "lerpType": "SPRING",
    "lerpTicks": 25
  }
}
```

### Show Modes

| Value | What's visible |
|-------|---------------|
| `"all"` | Every block in the structure |
| `"layer"` | Only the Y-layer specified by `layer` |
| `"layers"` | Y-layers from `layerMin` to `layerMax` |
| `"pos"` | Only blocks at specific `[x, y, z]` positions |
| `"parts"` | All functional part blocks (hatches, coils, etc.) |
| `"controller"` | Only the controller block |
| `"functional"` | All functional blocks |
| `"parts:@coil"` | Parts expression — only coil-type blocks |
| `"parts:hatch \| @coil"` | Parts expression — hatches OR coils |

### Camera Lerp Types

| Type | Behaviour |
|------|-----------|
| `"SNAP"` | Instant jump (no animation) |
| `"LINEAR"` | Constant speed |
| `"EASE_IN"` | Starts slow, ends fast |
| `"EASE_OUT"` | Starts fast, ends slow (natural deceleration) |
| `"EASE_IN_OUT"` | Slow → fast → slow |
| `"SPRING"` | Overshoot and settle — feels alive; recommended for most steps |

A `lerpTicks` of 20–30 with `SPRING` is a good default for most transitions.

### Variant (Optional) Groups

Define swappable blocks so players can toggle coil tiers, hatch tiers, etc.:

```json
"optionalGroups": [
  {
    "id": "fusion_glass",
    "label": "Fusion Glass",
    "category": "optional",
    "shownByDefault": true,
    "primaryBlock": "gtceu:fusion_glass",
    "fallbackBlock": "gtceu:heatproof_machine_casing",
    "positions": []
  }
]
```

`category` options: `"optional"`, `"hatches_buses"`, `"mufflers"`, `"casings"`

Leave `primaryBlock` as `null` to let auto-detection fill it from the GTCEu pattern.

### Mistakes

Flag known assembly errors with coordinates and messages:

```json
"mistakes": [
  {
    "x": 1, "y": 0, "z": 1,
    "label": "Coil block is wrong tier for this recipe.",
    "color": "FFB74D"
  }
],
"globalMistakes": [
  "Machine must have line-of-sight to the sky."
]
```

---

## Guide Reference

See [`reference/guides.md`](reference/guides.md) for the full schema. Quick example:

```json
{
  "id": "yourmod:ebf_basics",
  "title": "EBF Basics",
  "iconItem": "gtceu:electric_blast_furnace",
  "subtitle": "Your first smelter",
  "tooltipItems": ["gtceu:electric_blast_furnace"],
  "pages": [
    {
      "headline": "What is the EBF?",
      "text": "The Electric Blast Furnace smelts metals that require\nhigh temperatures. It needs §bHeating Coils§r and\na minimum of §eLV energy§r to run.",
      "items": [
        {
          "item": "gtceu:cupronickel_coil_block",
          "count": 1,
          "label": "Heating Coils",
          "type": "input",
          "description": "At least Cupronickel tier for basic steel."
        }
      ]
    },
    {
      "text": "See the full assembly walkthrough here:",
      "scriptId": "gtceu:electric_blast_furnace"
    }
  ]
}
```

### Text Formatting

Guide text supports standard Minecraft colour and formatting codes:

| Code | Effect |
|------|--------|
| `§0`–`§9`, `§a`–`§f` | Colours |
| `§l` | Bold |
| `§o` | Italic |
| `§n` | Underline |
| `§r` | Reset |
| `\n` | Line break |

### tooltipItems

List block/item registry IDs that should trigger hold-\[P\] to open this guide when the player is looking at or holding that item. Multiple IDs are fine.

### Cross-Links

Pages can link to other content:

| Field | Opens |
|-------|-------|
| `guideId` | Another guide |
| `scriptId` | A machine walkthrough |
| `sceneId` | A scene layout |

---

## Scene Reference

See [`reference/scenes.md`](reference/scenes.md) for the full schema. Scenes work like scripts but define multiple machine placements and can override each machine's visibility independently per step.

```json
{
  "id": "yourmod:ore_processing_line",
  "name": "Ore Processing Line",
  "iconItem": "minecraft:hopper",
  "placements": [
    { "machine": "gtceu:electric_blast_furnace", "x": 0, "y": 0, "z": 0 },
    { "machine": "gtceu:large_chemical_reactor", "x": 0, "y": 0, "z": 20 }
  ],
  "steps": [
    {
      "tick": 0,
      "caption": "The EBF smelts raw ore into ingots.",
      "machineOverrides": {
        "0": { "show": "all", "working": true, "fakeRecipeId": "gtceu:ebf_iron" },
        "1": { "show": "all" }
      },
      "camera": { "yaw": -135, "pitch": -30, "lerpType": "EASE_OUT", "lerpTicks": 20 }
    }
  ]
}
```

The machine index in `machineOverrides` matches the zero-based index of the placement in the `placements` array.

---

## Config Distribution

Ship a default `config/phantasia.yaml` in your modpack's overrides. Players can still change it locally. The settings most worth tweaking for a pack:

```yaml
phantasiaUI:
  giveBookOnFirstJoin: true        # Disable if you distribute the manual via loot tables
  autoPlayScripts: true            # Turn off if players prefer manual control
  streamingMode: BALANCED          # PERFORMANCE for low-end packs, QUALITY for high-end
  dimensionPrePopDelay: 2000       # Increase on slow servers (milliseconds)
  animateTickBudget: 2048          # Decrease on low-end hardware to reduce frame spikes
```

---

## Ars Nouveau Support

Phantasia has built-in support for Ars Nouveau ritual and apparatus recipes. Use `ArsNouveauScriptEditorScreen` (opens via the script editor when a ritual multiblock is selected). World items can set source jar charges:

```json
"worldItems": [
  {
    "x": 0, "y": 0, "z": 0,
    "item": "ars_nouveau:source_jar",
    "sourceAmount": 5000
  }
]
```

---

## EMI Integration

If EMI is installed, the **🧮 Materials** button appears in the viewer's right panel. It queries EMI's recipe graph to show the full crafting cost tree for the machine. No setup required — Phantasia detects EMI automatically.

---

## Tips

- **Build steps per-layer.** One step per Y-layer with `show: "layer"` is the most readable walkthrough structure for most machines.
- **Use SPRING lerp at 20–30 ticks** for camera transitions. It feels natural and players can follow along.
- **Keep captions short.** Players read while watching the 3D preview — one sentence is ideal.
- **Add a globalMistake for any common assembly error** you've seen on your server. It saves a lot of support time.
- **Test with a fresh world.** After exporting scripts/guides, test in a world that doesn't have cached Phantasia dimension data to verify everything loads correctly.
- **Colour codes go a long way.** Use `§b` (aqua) for block names and `§7` (grey) for secondary info — it reads better than plain white.
