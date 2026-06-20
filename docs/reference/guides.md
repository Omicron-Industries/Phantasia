# Guide JSON Reference

Guides are multi-page text documents with optional item cards and cross-links to other Phantasia content.

**File location:** `<gameDir>/phantasia/guides/<namespace>/<name>.json`

Guides live in the world directory rather than a datapack so they can be created and edited in-game. Create them via the Guide Editor (creative mode → `/phantasia` → Guides → **✏ Edit**), then copy the saved files into your modpack's overrides under the same path.

---

## Top-Level Fields

```json
{
  "id":           "yourmod:ebf_basics",
  "title":        "EBF Basics",
  "iconItem":     "gtceu:electric_blast_furnace",
  "subtitle":     "Your first smelter",
  "tag":          "gtceu",
  "tooltipItems": ["gtceu:electric_blast_furnace"],
  "pages":        [ ... ]
}
```

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `id` | string | required | Unique identifier in `namespace:name` format |
| `title` | string | required | Display name shown on the card and at the top of the reader |
| `iconItem` | string | null | Registry ID of the item used as the card icon |
| `subtitle` | string | null | Short line shown below the title on the card |
| `tag` | string | null | Filter tag for grouping guides in the list |
| `tooltipItems` | string[] | [] | Registry IDs of blocks/items that trigger hold-\[P\] to open this guide |
| `pages` | PageData[] | [] | The document pages, in order |

---

## PageData

One entry in the `pages` array.

```json
{
  "headline":  "What is the EBF?",
  "text":      "The Electric Blast Furnace smelts metals\nthat require high heat. It needs §bHeating Coils§r.",
  "items":     [ ... ],
  "guideId":   null,
  "sceneId":   null,
  "scriptId":  null
}
```

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `headline` | string | null | Large heading rendered at 1.5× scale above the body text |
| `text` | string | null | Body text. Supports Minecraft colour codes and `\n` for line breaks. |
| `items` | ItemConditionData[] | [] | Item cards shown in a grid below the text |
| `guideId` | string | null | Link to another guide — renders a "Continue reading" button |
| `sceneId` | string | null | Link to a scene — opens the scene viewer |
| `scriptId` | string | null | Link to a machine script — opens the machine viewer |

Only one cross-link (`guideId`, `sceneId`, or `scriptId`) is shown per page. If multiple are set, `guideId` takes priority, then `sceneId`, then `scriptId`.

---

## ItemConditionData

Item cards shown in the guide's item grid.

```json
{
  "item":               "gtceu:cupronickel_coil_block",
  "count":              4,
  "label":              "Heating Coils",
  "type":               "input",
  "description":        "§7At least Cupronickel tier.§r",
  "track":              "none",
  "trackDurationTicks": 20,
  "guideId":            null,
  "microsceneId":       null
}
```

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `item` | string | required | Registry ID of the item/block |
| `count` | int | 1 | Stack size hint displayed on the icon |
| `label` | string | null | Short label below the icon |
| `type` | string | "input" | Visual category: `"input"`, `"output"`, `"catalyst"`, `"optional"` |
| `description` | string | null | Rich text shown when the item card is hovered or tapped |
| `track` | string | "none" | Animation preset: `"none"`, `"pulse"`, `"highlight"` |
| `trackDurationTicks` | int | 20 | Duration of the animation |
| `guideId` | string | null | Clicking the card opens this guide |
| `microsceneId` | string | null | Clicking the card opens a micro scene preview |

---

## Text Formatting

Guide text supports the full Minecraft formatting code set. Use `§` followed by a code character.

### Colour Codes

| Code | Colour | Code | Colour |
|------|--------|------|--------|
| `§0` | Black | `§8` | Dark Grey |
| `§1` | Dark Blue | `§9` | Blue |
| `§2` | Dark Green | `§a` | Green |
| `§3` | Dark Aqua | `§b` | Aqua |
| `§4` | Dark Red | `§c` | Red |
| `§5` | Dark Purple | `§d` | Light Purple |
| `§6` | Gold | `§e` | Yellow |
| `§7` | Grey | `§f` | White |

### Format Codes

| Code | Effect |
|------|--------|
| `§l` | **Bold** |
| `§o` | *Italic* |
| `§n` | Underline |
| `§m` | Strikethrough |
| `§k` | Obfuscated (scrambled) |
| `§r` | Reset to default |

Use `\n` in the JSON string for explicit line breaks. Long lines are word-wrapped automatically.

---

## Full Example

```json
{
  "id": "yourmod:ebf_basics",
  "title": "EBF Basics",
  "iconItem": "gtceu:electric_blast_furnace",
  "subtitle": "Your first smelter",
  "tag": "gtceu",
  "tooltipItems": ["gtceu:electric_blast_furnace"],
  "pages": [
    {
      "headline": "What is the EBF?",
      "text": "The §bElectric Blast Furnace§r smelts metals that require\nhigh temperatures — iron, steel, aluminium, and beyond.\n\nIt cannot run without §eHeating Coils§r and a minimum\nenergy input of §aLV tier§r.",
      "items": [
        {
          "item": "gtceu:cupronickel_coil_block",
          "count": 1,
          "label": "Heating Coils",
          "type": "input",
          "description": "§7Cupronickel is the minimum tier.\nHigher tiers unlock hotter recipes.§r"
        }
      ]
    },
    {
      "headline": "Energy Requirements",
      "text": "The EBF requires at least §a128 EU/t§r at LV tier.\n\nPower is supplied via §bEnergy Hatches§r placed on the\nouter casing wall. Multiple hatches can be used.",
      "items": [
        {
          "item": "gtceu:lv_energy_hatch",
          "count": 1,
          "label": "Energy Hatch (LV)",
          "type": "input"
        }
      ]
    },
    {
      "headline": "Build It",
      "text": "Ready to build? The step-by-step assembly walkthrough\nwill guide you through every layer.",
      "scriptId": "gtceu:electric_blast_furnace"
    }
  ]
}
```
