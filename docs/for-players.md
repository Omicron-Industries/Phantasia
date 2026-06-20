# Phantasia — Player Guide

Phantasia is an in-game documentation and visualisation tool. It gives you interactive 3D previews of multiblock machines, step-by-step assembly walkthroughs, multi-machine scene layouts, and rich text guides — all without leaving the game.

---

## Opening Phantasia

### Hold \[P\] near a machine
Point your crosshair at any multiblock machine in the world and hold the **P** key. A progress bar appears at the bottom of your screen. Keep holding until it fills and the viewer opens automatically.

### Use the command
Type `/phantasia` at any time to open a searchable browser of every machine, scene, and guide in the pack. You don't need to be near anything.

### Use the Phantasia Manual
Right-click the **Phantasia Manual** book (automatically given when you first join) to open the same browser.

---

## The Main Browser (`/phantasia`)

The browser has five tabs across the top:

| Tab | What it shows |
|-----|---------------|
| **Multiblocks** | Every registered multiblock machine. Cards with a green dot have a step-by-step walkthrough. |
| **Scenes** | Multi-machine factory layouts — groups of machines shown together. |
| **Guides** | Standalone text guides: recipes, progression tips, lore. |
| **Tutorials** | Interactive tutorials for learning how to use Phantasia itself. |
| **Settings** | Display, camera, performance, and theme options. |

Use the **search bar** to filter cards by name in the current tab. Click any card to open it.

---

## The Machine Viewer

When you open a machine, you see a 3D preview of the full structure rendered in real time.

### Controls

| Action | How |
|--------|-----|
| Orbit camera | Click and drag |
| Zoom | Scroll wheel |
| Auto-play walkthrough | Starts automatically (can be toggled in Settings) |
| Pause / resume | Click **⏸** in the timeline bar at the bottom |
| Lock camera to script | Click **🔒** in the timeline bar |
| Change playback speed | Click **1x** to cycle through speeds |
| Jump to a step | Click any numbered dot on the timeline |

### The Caption Strip
The strip just above the timeline shows the current instruction for whatever step you're on. It updates as the walkthrough plays.

### The Right Panel
Click the **◀** arrow on the right edge to expand the panel. Inside you'll find:

- **Show** — Filter what blocks are visible: All / Layer / Range / Parts
- **Layer** — Step through individual Y-layers of the machine using ◀ and ▶
- **🧱 Build Mode** — Breaks the build into numbered build groups so you can follow along block by block
- **🗺 Footprint** — Shows the 2D floor footprint and dimensions
- **⊕ Center Camera** — Snaps the camera back to the center of the machine
- **🔍 Block List** — Lists every distinct block type and how many you need
- **🧮 Materials** — (Requires EMI) Full ingredient breakdown with crafting cost

---

## Step-by-Step Walkthroughs (Scripts)

A walkthrough is a scripted sequence of steps. Each step:
- Focuses the camera on a specific part of the machine
- Highlights the relevant blocks (you can see the rest dimmed)
- Shows a caption explaining what to place

Use the **timeline** at the bottom to jump to any step. You can also use the playback controls to let it play through automatically.

---

## Multi-Machine Scenes

Scenes show multiple machines arranged together as a complete factory section. They work exactly like single-machine walkthroughs — timeline, captions, camera movements — but the 3D view shows all machines at once. Individual machines can be highlighted independently per step.

---

## Standalone Guides

Guides are text pages with optional item cards. They can have multiple pages — use the **◄ Prev** and **Next ►** buttons at the bottom to navigate. Guides can contain links to other guides, to machine scripts, or to scenes.

---

## Variants

Some machines have optional block choices (different coil tiers, hatch tiers, fusion glass vs. standard casing, etc.). Expand the right panel and look for variant rows or the **Variants** button if your modpack author has set them up. Toggling a variant re-renders the machine immediately and your choice is remembered between sessions.

---

## Settings

Open the Settings tab in the browser to configure:

- **Display Mode** — Whether the hold-to-open indicator appears as a tooltip, on the hotbar, or in a Jade/WTHIT overlay
- **Activation Time** — How long you need to hold \[P\] before the viewer opens
- **Camera Sensitivity** — Orbit speed multiplier
- **Scroll Zoom Speed** — Zoom speed multiplier
- **Script Lock Camera** — Whether the walkthrough drives your camera or leaves it free
- **Auto-Play Scripts** — Start walkthroughs automatically when you open a machine
- **Show Baseplate** — Toggle the decorative floor beneath machines
- **Baseplate Block** — Which block is used for the floor
- **Streaming Mode** — Performance vs. quality trade-off for rendering large machines (see below)
- **🎨 Open Theme Editor** — Customise every UI colour

### Streaming Mode

Phantasia renders large machines in the background to avoid frame hitches. Three presets control when streaming kicks in:

| Mode | Best for |
|------|----------|
| **Performance** | Weak hardware or strict FPS targets. Machines stream in progressively — you may see blocks pop in. |
| **Balanced** (default) | Most players. A good compromise between visual smoothness and performance. |
| **Quality** | High-end systems. The full structure is baked before it appears, so there's no pop-in but a larger one-time GPU spike when opening. |
