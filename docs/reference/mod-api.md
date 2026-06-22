# Phantasia Mod API Reference

This document covers everything a mod developer needs to integrate with Phantasia:
registering machines, responding to viewer events, embedding 3D previews, and querying
content from code.

All classes live in `net.phoenixvine.phantasia.api` or `net.phoenixvine.phantasia.common.multiblock`.
Everything described here is client-side only unless noted.

---

## Table of Contents

1. [Adding Your Mod's Machines](#1-adding-your-mods-machines)
2. [Block Inspector Compat](#2-block-inspector-compat)
3. [PhantasiaAPI — Querying and Opening Screens](#3-phantasiaapi--querying-and-opening-screens)
4. [Embeddable 3D Preview Widget](#4-embeddable-3d-preview-widget)
5. [Forge Events](#5-forge-events)
6. [Registering Content Programmatically](#6-registering-content-programmatically)

---

## 1. Adding Your Mod's Machines

Phantasia discovers machines through **providers**. Implement two interfaces and
register your provider once during mod init.

### `IPhantasiaMultiblockProvider`

Implement this and call `PhantasiaMultiblockRegistry.register(provider)` during your
mod's constructor or `FMLCommonSetupEvent`.

```java
public class MyMultiblockProvider implements IPhantasiaMultiblockProvider {

    @Override
    public boolean isAvailable() {
        // Return false if your mod is absent (guard for optional dependencies)
        return ModList.get().isLoaded("mymod");
    }

    @Override
    public Optional<IPhantasiaMultiblockDefinition> resolve(String machineId) {
        // Return your definition if the id belongs to your mod, empty otherwise
        MyMachine machine = MyMachineRegistry.get(machineId);
        return machine != null
                ? Optional.of(new MyMultiblockDefinition(machine))
                : Optional.empty();
    }

    @Override
    public List<IPhantasiaMultiblockDefinition> getAllDefinitions() {
        return MyMachineRegistry.all().stream()
                .map(MyMultiblockDefinition::new)
                .collect(Collectors.toList());
    }

    // Optional — override resolveFromItem() to support right-click on controller items
    // Optional — override isControllerBlock() / isPartBlock() for the crosshair overlay
}
```

Registration (in your mod's main class constructor):

```java
if (ModList.get().isLoaded("phantasia")) {
    PhantasiaMultiblockRegistry.register(new MyMultiblockProvider());
}
```

---

### `IPhantasiaMultiblockDefinition`

One instance per machine type. Phantasia calls these methods to render the structure,
apply animations, and load scripts.

| Method | Purpose |
|--------|---------|
| `getId()` | `ResourceLocation` — must match the id used in script JSON |
| `getMatchingShapes()` | Shapes used for the primary render view |
| `getAllShapes()` | All shape variants (used for automatic shape detection) |
| `getDisplayName()` | Human-readable name shown in the UI |
| `getIcon()` | `ItemStack` icon shown in the selection screen |
| `onShapeLoaded(level, origin, blockMap, localToWorld)` | Called on the render thread after all blocks are placed. Fire your `onStructureFormed` logic here. |
| `setMachineWorking(level, working)` | Called when the script transitions between working/idle steps. Update your machine's animation state here. |

`onShapeLoaded` is the most important override — it's where your multiblock's controller
BE is set up so GT-style machines show their working animation correctly.

---

## 2. Block Inspector Compat

The block inspector overlay (`Right-click` → inspect) shows extra lines provided
by registered inspectors. Register once — anywhere after mod init fires.

```java
PhantasiaBlockInspectCompat.register((block, lines, setRole) -> {
    if (block instanceof MyEnergyHatch hatch) {
        lines.add(Component.literal("Tier: " + hatch.getTier()).withStyle(ChatFormatting.YELLOW));
        setRole.accept(Component.literal("Energy Hatch"));
    }
});
```

`setRole` replaces the default "Standard Component" role label at the top of the
inspector panel. `lines` appends to the spec/utility column on the right.

---

## 3. PhantasiaAPI — Querying and Opening Screens

`PhantasiaAPI` is the main static entry point. Guard calls with `@OnlyIn(Dist.CLIENT)`
or a dist-checked proxy.

### Presence checks

```java
PhantasiaAPI.isAvailable("gtceu:electric_blast_furnace") // machine is registered
PhantasiaAPI.hasScript("gtceu:electric_blast_furnace")   // a build script exists
PhantasiaAPI.hasScene("mymod:ore_processing_line")       // a multi-machine scene exists
PhantasiaAPI.hasGuide("mymod:getting_started")           // a guide article exists
```

Use these to show or hide "View in Phantasia ▶" buttons in your own UI — if Phantasia
isn't installed or has no content for that machine the buttons simply won't appear.

### Opening screens

```java
// Single-machine build guide
PhantasiaAPI.openForMachine("gtceu:electric_blast_furnace", this);

// Multi-machine scene
PhantasiaAPI.openScene("mymod:ore_processing_line", this);

// Guide article
PhantasiaAPI.openGuide("mymod:getting_started", this);

// From an already-resolved definition
PhantasiaAPI.openForDefinition(definition, this);
```

`this` is the `Screen` Phantasia returns to when the player closes the viewer.

### Enumerating content

```java
List<String> machineIds = PhantasiaAPI.getAllMachineIds(); // all registered machines
List<String> sceneIds   = PhantasiaAPI.getAllSceneIds();   // all loaded scenes
List<String> guideIds   = PhantasiaAPI.getAllGuideIds();   // all registered guides

List<IPhantasiaMultiblockDefinition> defs = PhantasiaAPI.getAllDefinitions();
```

These are useful for populating quest editor pickers or search screens in your mod's UI.

---

## 4. Embeddable 3D Preview Widget

`PhantasiaMachinePreview` renders a live rotating 3D preview of any registered machine
inside a rectangle of your screen. It owns its own private dummy world so it never
conflicts with the main Phantasia viewer.

### Lifecycle

```java
// 1. Create once — in your screen's constructor or init():
PhantasiaMachinePreview preview = PhantasiaAPI.createPreview("gtceu:electric_blast_furnace");
// Returns null if the machine id is not registered.

// 2. Tick the camera — in your screen's tick():
preview.tick();

// 3. Render — in your screen's render():
preview.render(guiGraphics, x, y, width, height, partialTick);

// 4. Make it clickable — in your screen's mouseClicked():
if (preview.mouseClicked(mx, my, x, y, width, height, this)) return true;

// 5. Release GL resources — in your screen's onClose():
preview.close();
```

### Customisation

```java
preview.setAutoSpin(30f);           // degrees per second; default 20, set 0 to stop
preview.getCamera().setPosition(-135f, -30f, 60f); // custom yaw/pitch/zoom
```

### Loading state

The widget loads the machine pattern asynchronously. During loading it shows a
"Loading…" placeholder. You can check `preview.isReady()` to drive your own
placeholder or progress indicator, but rendering before ready is safe.

```java
if (!preview.isReady()) {
    // draw your own placeholder
} else {
    preview.renderRaw(g, x, y, w, h, pt); // raw render without the "click to view" hint
}
```

### Render variants

| Method | Draws |
|--------|-------|
| `render(g, x, y, w, h, pt)` | 3D view + border + "Click to view in Phantasia" hint |
| `renderRaw(g, x, y, w, h, pt)` | 3D view + border only — add your own label |

---

## 5. Forge Events

Phantasia fires events on `MinecraftForge.EVENT_BUS` (client-side). Subscribe from
a class annotated with `@Mod.EventBusSubscriber(value = Dist.CLIENT)`.

### `PhantasiaEvents.ViewerOpen`

Fired when the player opens the single-machine viewer.

```java
@SubscribeEvent
public static void onOpen(PhantasiaEvents.ViewerOpen event) {
    String machineId = event.getMachineId(); // e.g. "gtceu:electric_blast_furnace"
    IPhantasiaMultiblockDefinition def = event.getDefinition();
    Screen screen = event.getScreen();
}
```

### `PhantasiaEvents.ViewerClose`

Fired when the player closes the single-machine viewer.
`getSecondsViewed()` is wall-clock seconds the screen was open — use this to require
that the player actually read the guide before marking a quest objective complete.

```java
@SubscribeEvent
public static void onClose(PhantasiaEvents.ViewerClose event) {
    if (event.getSecondsViewed() >= 3f
            && "gtceu:electric_blast_furnace".equals(event.getMachineId())) {
        MyQuestMod.completeObjective(Minecraft.getInstance().player, "view_ebf");
    }
}
```

### `PhantasiaEvents.SceneViewerOpen` / `SceneViewerClose`

Same pattern as above but for the multi-machine scene viewer.
Use `getSceneId()` instead of `getMachineId()`.

```java
@SubscribeEvent
public static void onSceneClose(PhantasiaEvents.SceneViewerClose event) {
    if (event.getSecondsViewed() >= 5f) {
        MyQuestMod.completeObjective(player, "view_" + event.getSceneId());
    }
}
```

### Event reference

| Event class | Fired when | Key methods |
|-------------|------------|-------------|
| `PhantasiaEvents.ViewerOpen` | Single-machine screen opened | `getMachineId()`, `getDefinition()` |
| `PhantasiaEvents.ViewerClose` | Single-machine screen closed | `getMachineId()`, `getSecondsViewed()` |
| `PhantasiaEvents.SceneViewerOpen` | Multi-machine scene opened | `getSceneId()` |
| `PhantasiaEvents.SceneViewerClose` | Multi-machine scene closed | `getSceneId()`, `getSecondsViewed()` |

---

## 6. Registering Content Programmatically

Besides data-pack JSON, content can be registered from code. This is useful for
built-in guides or scenes that ship inside your mod jar rather than a resource pack.

### Scenes

```java
PhantasiaSceneData scene = new PhantasiaSceneData();
scene.id   = "mymod:ore_line";
scene.name = "Ore Processing Line";
// ... configure placements, steps, mistakes ...
PhantasiaScenes.register(scene);
```

### Guides

```java
PhantasiaGuideData guide = PhantasiaGuideData.builder()
        .id("mymod:getting_started")
        .title("Getting Started with MyMod")
        .addPage(/* ... */)
        .build();
PhantasiaGuideRegistry.register(guide);
```

Guides registered this way appear alongside JSON-loaded guides in the selection screen.
For most use-cases, shipping a JSON file under `data/<modid>/phantasia/guides/` is
simpler — see [the guide reference](guides.md) for the format.

---

## Summary: What to call when

| I want to… | Call |
|------------|------|
| Register my mod's machines | `PhantasiaMultiblockRegistry.register(provider)` at mod init |
| Add extra lines to the block inspector | `PhantasiaBlockInspectCompat.register(inspector)` |
| Check if content exists for a machine | `PhantasiaAPI.hasScript / hasScene / hasGuide` |
| Open the guide for a machine | `PhantasiaAPI.openForMachine(id, parent)` |
| Embed a 3D preview in my screen | `PhantasiaAPI.createPreview(id)` → `preview.render(...)` |
| Know when a player views a machine | Subscribe to `PhantasiaEvents.ViewerClose` |
| List all machines / scenes / guides | `PhantasiaAPI.getAllMachineIds()` etc. |
