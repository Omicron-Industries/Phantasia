package net.phoenixvine.phantasia.compat.arsnouveaucompat;

import net.phoenixvine.phantasia.common.data.script.PhantasiaScriptData;

/**
 * Default scene scripts for static Ars Nouveau setups (no recipe registry).
 * Each method returns the JSON-compiled script for one machine type.
 */
public final class ArsNouveauStaticScripts {

    // ── Spell Turrets ──────────────────────────────────────────────────────────

    public static PhantasiaScriptData basicSpellTurret() {
        return PhantasiaScriptData.fromJson(
                """
                        {
                          "machine": "ars_nouveau:basic_spell_turret",
                          "startCamera": { "yaw": -135.0, "pitch": -30.0, "zoom": 3.0 },
                          "steps": [
                            {
                              "tick": 0,
                              "caption": "The Basic Spell Turret fires spells on a redstone signal, acting like a magical dispenser. It accepts spells that use Touch or Projectile forms.",
                              "show": "pos",
                              "positions": [[2,1,2]],
                              "items": [
                                { "item": "ars_nouveau:basic_spell_turret", "label": "Basic Spell Turret", "type": "catalyst" }
                              ],
                              "camera": { "yaw": -135.0, "pitch": -30.0, "zoom": 3.0, "lerpType": "SNAP", "lerpTicks": 0 }
                            },
                            {
                              "tick": 60,
                              "caption": "Place Source Jars nearby — the turret draws Source from them each time it fires a spell.",
                              "show": "pos",
                              "positions": [[2,1,2],[0,1,2],[4,1,2],[2,1,0],[2,1,4]],
                              "items": [
                                { "item": "ars_nouveau:source_jar", "label": "Source Jar", "type": "input", "description": "Must be within 5 blocks of the turret." }
                              ],
                              "camera": { "yaw": -150.0, "pitch": -40.0, "zoom": 4.5, "lerpType": "EASE", "lerpTicks": 15 }
                            },
                            {
                              "tick": 120,
                              "caption": "For spells that use Item Pickup or Place Block, add a chest adjacent to the turret. Items will be drawn from or deposited into it.",
                              "show": "all",
                              "items": [
                                { "item": "minecraft:chest", "label": "Chest (optional)", "type": "input", "description": "Required only for spells that move or place blocks." }
                              ],
                              "camera": { "yaw": -165.0, "pitch": -42.0, "zoom": 4.8, "lerpType": "EASE", "lerpTicks": 12 }
                            },
                            {
                              "tick": 180,
                              "caption": "Inscribe a spell onto Spell Parchment at the Scribes Table, then right-click the turret to load it. Send a redstone pulse to fire.",
                              "show": "all",
                              "items": [
                                { "item": "ars_nouveau:spell_parchment", "label": "Inscribed Spell Parchment", "type": "input", "description": "Inscribe at the Scribes Table. Right-click the turret to load." }
                              ],
                              "camera": { "yaw": -135.0, "pitch": -35.0, "zoom": 3.5, "lerpType": "EASE", "lerpTicks": 15 }
                            }
                          ],
                          "globalMistakes": [
                            "The spell must use a Touch or Projectile form — Beam and other forms are not accepted.",
                            "Source Jars must be within 5 blocks of the turret and contain enough Source to cast the spell."
                          ],
                          "mistakes": [],
                          "optionalGroups": []
                        }
                        """);
    }

    public static PhantasiaScriptData enchantedSpellTurret() {
        return PhantasiaScriptData.fromJson(
                """
                        {
                          "machine": "ars_nouveau:enchanted_spell_turret",
                          "startCamera": { "yaw": -135.0, "pitch": -30.0, "zoom": 3.0 },
                          "steps": [
                            {
                              "tick": 0,
                              "caption": "The Enchanted Spell Turret works like the Basic Spell Turret, but casts spells at half the Source cost. Ideal for high-frequency automation.",
                              "show": "pos",
                              "positions": [[2,1,2]],
                              "items": [
                                { "item": "ars_nouveau:enchanted_spell_turret", "label": "Enchanted Spell Turret", "type": "catalyst", "description": "Costs 50% less Source per cast than the Basic Spell Turret." }
                              ],
                              "camera": { "yaw": -135.0, "pitch": -30.0, "zoom": 3.0, "lerpType": "SNAP", "lerpTicks": 0 }
                            },
                            {
                              "tick": 60,
                              "caption": "Surround it with Source Jars at the cardinal positions to keep it supplied.",
                              "show": "pos",
                              "positions": [[2,1,2],[0,1,2],[4,1,2],[2,1,0],[2,1,4]],
                              "items": [
                                { "item": "ars_nouveau:source_jar", "label": "Source Jar", "type": "input" }
                              ],
                              "camera": { "yaw": -150.0, "pitch": -40.0, "zoom": 4.5, "lerpType": "EASE", "lerpTicks": 15 }
                            },
                            {
                              "tick": 120,
                              "caption": "Add a chest adjacent to the turret for item-based spells (Item Pickup, Place Block).",
                              "show": "all",
                              "items": [
                                { "item": "minecraft:chest", "label": "Chest (optional)", "type": "input" }
                              ],
                              "camera": { "yaw": -165.0, "pitch": -42.0, "zoom": 4.8, "lerpType": "EASE", "lerpTicks": 12 }
                            },
                            {
                              "tick": 180,
                              "caption": "Load a spell via inscribed Spell Parchment and trigger it with a redstone signal. The 50% source discount makes this the preferred turret for expensive spells.",
                              "show": "all",
                              "items": [
                                { "item": "ars_nouveau:spell_parchment", "label": "Inscribed Spell Parchment", "type": "input", "description": "Inscribe at the Scribes Table. Right-click the turret to load." }
                              ],
                              "camera": { "yaw": -135.0, "pitch": -35.0, "zoom": 3.5, "lerpType": "EASE", "lerpTicks": 15 }
                            }
                          ],
                          "globalMistakes": [
                            "Spell Parchment must be inscribed at the Scribes Table before loading into the turret.",
                            "Spells must use Touch or Projectile form."
                          ],
                          "mistakes": [],
                          "optionalGroups": []
                        }
                        """);
    }

    public static PhantasiaScriptData timerSpellTurret() {
        return PhantasiaScriptData.fromJson(
                """
                        {
                          "machine": "ars_nouveau:timer_spell_turret",
                          "startCamera": { "yaw": -135.0, "pitch": -30.0, "zoom": 3.0 },
                          "steps": [
                            {
                              "tick": 0,
                              "caption": "The Timer Spell Turret fires automatically on a configurable interval — no redstone required. Default is 1 second; right-click to increase, punch to decrease.",
                              "show": "pos",
                              "positions": [[2,1,2]],
                              "items": [
                                { "item": "ars_nouveau:timer_spell_turret", "label": "Timer Spell Turret", "type": "catalyst", "description": "Right-click to increase interval. Punch to decrease. Sneak for 10-second steps." }
                              ],
                              "camera": { "yaw": -135.0, "pitch": -30.0, "zoom": 3.0, "lerpType": "SNAP", "lerpTicks": 0 }
                            },
                            {
                              "tick": 60,
                              "caption": "Place Source Jars nearby to keep it fuelled. The timer turret also casts Projectile, Touch, and Redstone forms for free.",
                              "show": "pos",
                              "positions": [[2,1,2],[0,1,2],[4,1,2],[2,1,0],[2,1,4]],
                              "items": [
                                { "item": "ars_nouveau:source_jar", "label": "Source Jar", "type": "input" }
                              ],
                              "camera": { "yaw": -150.0, "pitch": -40.0, "zoom": 4.5, "lerpType": "EASE", "lerpTicks": 15 }
                            },
                            {
                              "tick": 120,
                              "caption": "A chest adjacent to the turret provides items for Place Block or Item Pickup spells. Sneaking while adjusting sets the interval in 10-second steps.",
                              "show": "all",
                              "items": [
                                { "item": "minecraft:chest", "label": "Chest (optional)", "type": "input" }
                              ],
                              "camera": { "yaw": -165.0, "pitch": -42.0, "zoom": 4.8, "lerpType": "EASE", "lerpTicks": 12 }
                            },
                            {
                              "tick": 180,
                              "caption": "Use the Dominion Wand to lock the timer and prevent accidental changes. A redstone signal disables firing entirely.",
                              "show": "all",
                              "items": [
                                { "item": "ars_nouveau:spell_parchment", "label": "Inscribed Spell Parchment", "type": "input", "description": "Inscribe at the Scribes Table. Right-click the turret to load." },
                                { "item": "ars_nouveau:dominion_wand", "label": "Dominion Wand", "type": "catalyst", "description": "Use to lock the turret timer." }
                              ],
                              "camera": { "yaw": -135.0, "pitch": -35.0, "zoom": 3.5, "lerpType": "EASE", "lerpTicks": 15 }
                            }
                          ],
                          "globalMistakes": [
                            "A redstone signal will disable the timer — make sure no signal is active if you want it to fire.",
                            "Setting the timer to 0 seconds also disables it."
                          ],
                          "mistakes": [],
                          "optionalGroups": []
                        }
                        """);
    }

    public static PhantasiaScriptData rotatingSpellTurret() {
        return PhantasiaScriptData.fromJson(
                """
                        {
                          "machine": "ars_nouveau:rotating_spell_turret",
                          "startCamera": { "yaw": -135.0, "pitch": -30.0, "zoom": 3.0 },
                          "steps": [
                            {
                              "tick": 0,
                              "caption": "The Rotating Spell Turret slowly rotates, casting its loaded spell in a full arc. Great for area-coverage automation like mob farms or crop harvesting.",
                              "show": "pos",
                              "positions": [[2,1,2]],
                              "items": [
                                { "item": "ars_nouveau:rotating_spell_turret", "label": "Rotating Spell Turret", "type": "catalyst", "description": "Rotates 360° and fires continuously — ideal for area coverage." }
                              ],
                              "camera": { "yaw": -135.0, "pitch": -30.0, "zoom": 3.0, "lerpType": "SNAP", "lerpTicks": 0 }
                            },
                            {
                              "tick": 60,
                              "caption": "Supply it with Source Jars at the surrounding cardinal positions — rotating fire costs Source for each shot.",
                              "show": "pos",
                              "positions": [[2,1,2],[0,1,2],[4,1,2],[2,1,0],[2,1,4]],
                              "items": [
                                { "item": "ars_nouveau:source_jar", "label": "Source Jar", "type": "input", "description": "Keep these full — rotating turrets consume Source continuously." }
                              ],
                              "camera": { "yaw": -150.0, "pitch": -40.0, "zoom": 4.5, "lerpType": "EASE", "lerpTicks": 15 }
                            },
                            {
                              "tick": 130,
                              "caption": "Add an adjacent chest to feed item-dependent spells. Load an inscribed Spell Parchment into the turret to set the spell.",
                              "show": "all",
                              "items": [
                                { "item": "ars_nouveau:spell_parchment", "label": "Inscribed Spell Parchment", "type": "input", "description": "Inscribe at the Scribes Table. Right-click the turret to load." },
                                { "item": "minecraft:chest", "label": "Chest (optional)", "type": "input" }
                              ],
                              "camera": { "yaw": -135.0, "pitch": -35.0, "zoom": 4.2, "lerpType": "EASE", "lerpTicks": 15 }
                            }
                          ],
                          "globalMistakes": [
                            "Rotating turrets fire continuously, consuming a lot of Source — ensure jars are kept topped up.",
                            "Spells must use Touch or Projectile form."
                          ],
                          "mistakes": [],
                          "optionalGroups": []
                        }
                        """);
    }

    // ── Wixie Cauldron ─────────────────────────────────────────────────────────

    public static PhantasiaScriptData wixieCauldron() {
        return PhantasiaScriptData.fromJson(
                """
                        {
                          "machine": "ars_nouveau:wixie_cauldron",
                          "startCamera": { "yaw": -135.0, "pitch": -28.0, "zoom": 3.0 },
                          "steps": [
                            {
                              "tick": 0,
                              "caption": "The Wixie Cauldron auto-crafts items at the expense of Source. To summon a Wixie, obtain a Wixie Token by casting Dispel on a half-health Witch, then use a Wixie Charm on a Cauldron.",
                              "show": "pos",
                              "positions": [[2,1,2]],
                              "items": [
                                { "item": "ars_nouveau:wixie_charm", "label": "Wixie Charm", "type": "catalyst", "description": "Use on a Cauldron to summon a Wixie. Obtain a Wixie Token first by casting Dispel on a half-health Witch." }
                              ],
                              "camera": { "yaw": -135.0, "pitch": -28.0, "zoom": 3.0, "lerpType": "SNAP", "lerpTicks": 0 }
                            },
                            {
                              "tick": 70,
                              "caption": "Place Arcane Pedestals adjacent to the cauldron. The Wixie will cycle through each pedestal round-robin, crafting the item selected for each one.",
                              "show": "pos",
                              "positions": [[2,1,2],[1,1,2],[3,1,2],[2,1,1],[2,1,3]],
                              "items": [
                                { "item": "ars_nouveau:arcane_pedestal", "label": "Arcane Pedestal", "type": "input", "description": "Must be placed directly adjacent (1 block away) from the cauldron." }
                              ],
                              "camera": { "yaw": -145.0, "pitch": -38.0, "zoom": 4.0, "lerpType": "EASE", "lerpTicks": 12 }
                            },
                            {
                              "tick": 130,
                              "caption": "Add Source Jars within 5 blocks — each craft consumes a small amount of Source. The Wixie will pull materials from nearby chests automatically.",
                              "show": "pos",
                              "positions": [[2,1,2],[1,1,2],[3,1,2],[2,1,1],[2,1,3],[0,1,2],[4,1,2],[2,1,0],[2,1,4]],
                              "items": [
                                { "item": "ars_nouveau:source_jar", "label": "Source Jar", "type": "input" }
                              ],
                              "camera": { "yaw": -155.0, "pitch": -42.0, "zoom": 4.8, "lerpType": "EASE", "lerpTicks": 12 }
                            },
                            {
                              "tick": 200,
                              "caption": "For potion autocrafting, add a Potion Jar to output into. Right-click the cauldron with an Awkward Potion to begin. Supply Nether Wart and reagents from nearby chests.",
                              "show": "all",
                              "items": [
                                { "item": "ars_nouveau:potion_jar", "label": "Potion Jar", "type": "output", "description": "Place adjacent to the cauldron for it to deposit finished potions." }
                              ],
                              "camera": { "yaw": -145.0, "pitch": -40.0, "zoom": 5.0, "lerpType": "EASE", "lerpTicks": 15 }
                            }
                          ],
                          "globalMistakes": [
                            "A redstone signal on the cauldron will stop crafting.",
                            "The Wixie selects recipes based on what items are available nearby — you don't need to specify exact materials.",
                            "Use the Dominion Wand on an inventory and then the cauldron to restrict which inventories the Wixie uses."
                          ],
                          "mistakes": [],
                          "optionalGroups": []
                        }
                        """);
    }

    // ── Drygmy ─────────────────────────────────────────────────────────────────

    public static PhantasiaScriptData drygmy() {
        return PhantasiaScriptData.fromJson(
                """
                        {
                          "machine": "ars_nouveau:drygmy",
                          "startCamera": { "yaw": -135.0, "pitch": -28.0, "zoom": 3.0 },
                          "steps": [
                            {
                              "tick": 0,
                              "caption": "Drygmys produce items from nearby mobs without harming them. To summon one, use a Drygmy Charm on a block of Mossy Cobblestone.",
                              "show": "pos",
                              "positions": [[2,0,2],[2,1,2]],
                              "items": [
                                { "item": "ars_nouveau:drygmy_charm", "label": "Drygmy Charm", "type": "catalyst", "description": "Use on Mossy Cobblestone to summon a Drygmy. The block converts into a Drygmy Henge." }
                              ],
                              "camera": { "yaw": -135.0, "pitch": -28.0, "zoom": 3.0, "lerpType": "SNAP", "lerpTicks": 0 }
                            },
                            {
                              "tick": 70,
                              "caption": "Place Mob Jars containing living mobs at the cardinal positions. The Drygmy produces loot as if those mobs were slain — without killing them.",
                              "show": "pos",
                              "positions": [[2,0,2],[2,1,2],[0,1,2],[4,1,2],[2,1,0],[2,1,4]],
                              "items": [
                                { "item": "ars_nouveau:mob_jar", "label": "Mob Jar", "type": "input", "description": "Fill with mobs using Capture. Place within range of the Drygmy Henge." }
                              ],
                              "camera": { "yaw": -150.0, "pitch": -40.0, "zoom": 4.5, "lerpType": "EASE", "lerpTicks": 15 }
                            },
                            {
                              "tick": 140,
                              "caption": "Mob diversity matters — each unique mob type in the area adds a production bonus. Mix different mobs in the jars for maximum output.",
                              "show": "all",
                              "camera": { "yaw": -145.0, "pitch": -38.0, "zoom": 4.8, "lerpType": "EASE", "lerpTicks": 12 }
                            }
                          ],
                          "globalMistakes": [
                            "The Mossy Cobblestone transforms into a Drygmy Henge after the charm is used — this is normal.",
                            "Additional Drygmys can be summoned on the same Henge by using more charms on it.",
                            "Cast Dispel on the Drygmy or kill it to recover your charm."
                          ],
                          "mistakes": [],
                          "optionalGroups": []
                        }
                        """);
    }

    // ── Whirlisprig ────────────────────────────────────────────────────────────

    public static PhantasiaScriptData whirlisprig() {
        return PhantasiaScriptData.fromJson(
                """
                        {
                          "machine": "ars_nouveau:whirlisprig",
                          "startCamera": { "yaw": -135.0, "pitch": -28.0, "zoom": 3.0 },
                          "steps": [
                            {
                              "tick": 0,
                              "caption": "Whirlisprigs produce natural materials — wood, crops, seeds, and flowers — from the blocks in their home. To summon one, use a Whirlisprig Charm on any flower.",
                              "show": "pos",
                              "positions": [[2,1,2]],
                              "items": [
                                { "item": "ars_nouveau:whirlisprig_charm", "label": "Whirlisprig Charm", "type": "catalyst", "description": "Use on any flower in the world to summon a Whirlisprig at that location." }
                              ],
                              "camera": { "yaw": -135.0, "pitch": -28.0, "zoom": 3.0, "lerpType": "SNAP", "lerpTicks": 0 }
                            },
                            {
                              "tick": 70,
                              "caption": "The Whirlisprig's home is a 10-block radius around the flower. Fill this area with diverse natural blocks — logs, flowers, grass, saplings — to increase happiness and production.",
                              "show": "pos",
                              "positions": [[2,1,2],[0,1,2],[4,1,2],[2,1,0]],
                              "camera": { "yaw": -150.0, "pitch": -40.0, "zoom": 4.2, "lerpType": "EASE", "lerpTicks": 12 }
                            },
                            {
                              "tick": 130,
                              "caption": "Place a Chest directly adjacent to the Whirlisprig Flower — it will only produce items if a chest is present to deposit into. Add a Source Jar nearby for its Source requirement.",
                              "show": "all",
                              "items": [
                                { "item": "minecraft:chest", "label": "Chest", "type": "output", "description": "Must be placed directly adjacent to the flower — the Whirlisprig won't produce without one." },
                                { "item": "ars_nouveau:source_jar", "label": "Source Jar", "type": "input" }
                              ],
                              "camera": { "yaw": -145.0, "pitch": -38.0, "zoom": 4.5, "lerpType": "EASE", "lerpTicks": 12 }
                            }
                          ],
                          "globalMistakes": [
                            "A chest must be placed adjacent to the flower or the Whirlisprig will not produce anything.",
                            "Source must be available nearby — place Source Jars within 5 blocks.",
                            "Right-click the Whirlisprig with an empty hand to check its happiness level and what blocks it wants."
                          ],
                          "mistakes": [],
                          "optionalGroups": []
                        }
                        """);
    }

    // ── Starbuncle ─────────────────────────────────────────────────────────────

    public static PhantasiaScriptData starbuncle() {
        return PhantasiaScriptData.fromJson(
                """
                        {
                          "machine": "ars_nouveau:starbuncle",
                          "startCamera": { "yaw": -135.0, "pitch": -28.0, "zoom": 3.5 },
                          "steps": [
                            {
                              "tick": 0,
                              "caption": "Starbuncles move items between inventories. To summon one, obtain a Starbuncle Token by offering a Gold Nugget to a wild Starbuncle, then craft a Starbuncle Charm.",
                              "show": "pos",
                              "positions": [[2,1,2]],
                              "items": [
                                { "item": "ars_nouveau:starbuncle_charm", "label": "Starbuncle Charm", "type": "catalyst", "description": "Use on the ground to summon a Starbuncle. Obtain a Starbuncle Token first by offering Gold Nuggets to a wild one." }
                              ],
                              "camera": { "yaw": -135.0, "pitch": -28.0, "zoom": 3.5, "lerpType": "SNAP", "lerpTicks": 0 }
                            },
                            {
                              "tick": 60,
                              "caption": "Use the Charm on the ground to place the Starbuncle. It picks up nearby items and automatically harvests fully-grown Source Berry Bushes.",
                              "show": "pos",
                              "positions": [[2,1,2],[2,1,4]],
                              "camera": { "yaw": -140.0, "pitch": -35.0, "zoom": 4.0, "lerpType": "EASE", "lerpTicks": 10 }
                            },
                            {
                              "tick": 120,
                              "caption": "Place chests in the area. Use the Dominion Wand on a source chest and then the Starbuncle to have it take items from that chest. Use the wand on the Starbuncle and then a destination chest to have it deposit there.",
                              "show": "all",
                              "items": [
                                { "item": "ars_nouveau:dominion_wand", "label": "Dominion Wand", "type": "catalyst", "description": "Right-click a source inventory, then the Starbuncle to set pickup. Right-click the Starbuncle, then a destination to set dropoff." }
                              ],
                              "camera": { "yaw": -150.0, "pitch": -40.0, "zoom": 5.0, "lerpType": "EASE", "lerpTicks": 15 }
                            }
                          ],
                          "globalMistakes": [
                            "Starbuncles can move items between as many inventories as you like — chain multiple routes together.",
                            "Use an Allow or Deny Scroll to restrict which items a Starbuncle picks up.",
                            "Cast Dispel on a Starbuncle or kill it to recover its charm."
                          ],
                          "mistakes": [],
                          "optionalGroups": []
                        }
                        """);
    }

    // ── Bookwyrm ───────────────────────────────────────────────────────────────

    public static PhantasiaScriptData bookwyrm() {
        return PhantasiaScriptData.fromJson(
                """
                        {
                          "machine": "ars_nouveau:bookwyrm",
                          "startCamera": { "yaw": -135.0, "pitch": -30.0, "zoom": 4.0 },
                          "steps": [
                            {
                              "tick": 0,
                              "caption": "The Bookwyrm automates item transfer between a Storage Lectern and nearby inventories. Craft a Bookwyrm Charm and right-click a Storage Lectern to bind one.",
                              "show": "pos",
                              "positions": [[2,1,2]],
                              "items": [
                                { "item": "ars_nouveau:bookwyrm_charm", "label": "Bookwyrm Charm", "type": "catalyst", "description": "Right-click a Storage Lectern to bind the Bookwyrm to it. It will live there and manage transfers." }
                              ],
                              "camera": { "yaw": -135.0, "pitch": -30.0, "zoom": 4.0, "lerpType": "SNAP", "lerpTicks": 0 }
                            },
                            {
                              "tick": 60,
                              "caption": "Place chests near the Storage Lectern. The Bookwyrm will shuttle items back and forth between the lectern and the connected inventories.",
                              "show": "all",
                              "items": [
                                { "item": "ars_nouveau:archwood_chest", "label": "Archwood Chest (or any inventory)", "type": "input", "description": "Any inventory within range can be linked to the Storage Lectern." }
                              ],
                              "camera": { "yaw": -150.0, "pitch": -38.0, "zoom": 5.5, "lerpType": "EASE", "lerpTicks": 15 }
                            },
                            {
                              "tick": 120,
                              "caption": "Use the Dominion Wand to configure which inventories are linked. Right-click the lectern to open its interface and manage Bookwyrm filter settings.",
                              "show": "all",
                              "items": [
                                { "item": "ars_nouveau:dominion_wand", "label": "Dominion Wand", "type": "catalyst", "description": "Use to link inventories to the Storage Lectern. Right-click the lectern to open filter settings." }
                              ],
                              "camera": { "yaw": -120.0, "pitch": -35.0, "zoom": 4.5, "lerpType": "EASE", "lerpTicks": 12 }
                            }
                          ],
                          "globalMistakes": [
                            "The Bookwyrm needs a Storage Lectern — a regular lectern won't work.",
                            "Inventories must be within range of the lectern to be linked.",
                            "Use Allow or Deny Scrolls at the lectern to restrict which items the Bookwyrm transfers."
                          ],
                          "mistakes": [],
                          "optionalGroups": []
                        }
                        """);
    }

    // ── Ritual Brazier ─────────────────────────────────────────────────────────

    public static PhantasiaScriptData ritualBrazier() {
        return PhantasiaScriptData.fromJson(
                """
                        {
                          "machine": "ars_nouveau:ritual_brazier",
                          "startCamera": { "yaw": -135.0, "pitch": -30.0, "zoom": 3.5 },
                          "steps": [
                            {
                              "tick": 0,
                              "caption": "The Ritual Brazier performs powerful rituals using Source and ritual items. Here we demonstrate the Sunrise Ritual — which advances the time to morning.",
                              "show": "pos",
                              "positions": [[2,1,2]],
                              "items": [
                                { "item": "ars_nouveau:ritual_brazier", "label": "Ritual Brazier", "type": "catalyst" },
                                { "item": "ars_nouveau:ritual_sunrise", "label": "Ritual of Sunrise", "type": "catalyst", "description": "Advances time to day. Drop on the Brazier before right-clicking with a Spellbook." }
                              ],
                              "camera": { "yaw": -135.0, "pitch": -30.0, "zoom": 3.5, "lerpType": "SNAP", "lerpTicks": 0 }
                            },
                            {
                              "tick": 60,
                              "caption": "Add Brazier Relays at the cardinal positions to extend the ritual's effective radius and reduce its Source cost.",
                              "show": "pos",
                              "positions": [[2,1,2],[0,1,2],[4,1,2],[2,1,0],[2,1,4]],
                              "items": [
                                { "item": "ars_nouveau:brazier_relay", "label": "Brazier Relay", "type": "input", "description": "Place at the same Y level as the Brazier. Extends ritual radius and boosts power." }
                              ],
                              "camera": { "yaw": -145.0, "pitch": -40.0, "zoom": 4.5, "lerpType": "EASE", "lerpTicks": 12 }
                            },
                            {
                              "tick": 130,
                              "caption": "Place Source Jars at the corners to keep the ritual fuelled. Rituals consume large amounts of Source — ensure jars are full before starting.",
                              "show": "all",
                              "items": [
                                { "item": "ars_nouveau:source_jar", "label": "Source Jar", "type": "input", "description": "Keep these full — rituals fail if Source runs out mid-cast." }
                              ],
                              "camera": { "yaw": -155.0, "pitch": -45.0, "zoom": 5.0, "lerpType": "EASE", "lerpTicks": 12 }
                            },
                            {
                              "tick": 190,
                              "caption": "Drop the Ritual Tablet on the Brazier, then right-click with your Spellbook to ignite it. The Brazier glows as the ritual runs — each ritual has unique item and Source requirements.",
                              "show": "all",
                              "items": [
                                { "item": "ars_nouveau:novice_spellbook", "label": "Spellbook", "type": "catalyst", "description": "Right-click the Brazier with your Spellbook after placing the ritual items to begin." }
                              ],
                              "camera": { "yaw": -135.0, "pitch": -35.0, "zoom": 4.0, "lerpType": "EASE", "lerpTicks": 15 }
                            }
                          ],
                          "globalMistakes": [
                            "If Source runs out mid-ritual, the ritual will fail — ensure jars are full before starting.",
                            "Brazier Relays must be placed on the same Y level as the Brazier within a few blocks.",
                            "Drop the Ritual Tablet on the Brazier before right-clicking — holding it in your hand does nothing."
                          ],
                          "mistakes": [],
                          "optionalGroups": []
                        }
                        """);
    }

    // ── Sourcelinks ────────────────────────────────────────────────────────────

    public static PhantasiaScriptData agronomicSourcelink() {
        return PhantasiaScriptData.fromJson(
                """
                        {
                          "machine": "ars_nouveau:agronomic_sourcelink",
                          "startCamera": { "yaw": -135.0, "pitch": -30.0, "zoom": 3.5 },
                          "steps": [
                            {
                              "tick": 0,
                              "caption": "The Agronomic Sourcelink generates Source from crop and tree growth within 15 blocks. Magical plants like Mageblooms and Source Berry Bushes give bonus Source.",
                              "show": "pos",
                              "positions": [[2,1,2]],
                              "items": [
                                { "item": "ars_nouveau:agronomic_sourcelink", "label": "Agronomic Sourcelink", "type": "catalyst" },
                                { "item": "ars_nouveau:sourceberry_bush", "label": "Source Berry Bush", "type": "input", "description": "Magical plants like Mageblooms and Source Berry Bushes generate bonus Source compared to vanilla crops." }
                              ],
                              "camera": { "yaw": -135.0, "pitch": -30.0, "zoom": 3.5, "lerpType": "SNAP", "lerpTicks": 0 }
                            },
                            {
                              "tick": 70,
                              "caption": "Place Source Jars within 5 blocks — the Sourcelink outputs Source into the nearest jar. More jars mean it can keep generating even when one is full.",
                              "show": "all",
                              "items": [
                                { "item": "ars_nouveau:source_jar", "label": "Source Jar", "type": "output", "description": "Must be within 5 blocks. Multiple jars prevent Source being wasted when one fills up." }
                              ],
                              "camera": { "yaw": -150.0, "pitch": -42.0, "zoom": 4.8, "lerpType": "EASE", "lerpTicks": 15 }
                            }
                          ],
                          "globalMistakes": [
                            "Bonemealing crops does NOT generate Source — only natural growth triggers it.",
                            "Source Jars must be within 5 blocks of the Sourcelink to receive Source."
                          ],
                          "mistakes": [],
                          "optionalGroups": []
                        }
                        """);
    }

    public static PhantasiaScriptData volcanicSourcelink() {
        return PhantasiaScriptData.fromJson(
                """
                        {
                          "machine": "ars_nouveau:volcanic_sourcelink",
                          "startCamera": { "yaw": -135.0, "pitch": -30.0, "zoom": 3.5 },
                          "steps": [
                            {
                              "tick": 0,
                              "caption": "The Volcanic Sourcelink burns fuel items to generate Source, also producing heat as a byproduct. Archwood Logs give bonus Source; Blazing Archwood gives the most.",
                              "show": "pos",
                              "positions": [[2,1,2]],
                              "items": [
                                { "item": "ars_nouveau:volcanic_sourcelink", "label": "Volcanic Sourcelink", "type": "catalyst" }
                              ],
                              "camera": { "yaw": -135.0, "pitch": -30.0, "zoom": 3.5, "lerpType": "SNAP", "lerpTicks": 0 }
                            },
                            {
                              "tick": 70,
                              "caption": "It will pull fuel from adjacent Arcane Pedestals automatically. Place Pedestals beside it stacked with Blazing Archwood for a high-output setup.",
                              "show": "pos",
                              "positions": [[2,1,2],[1,1,2],[3,1,2],[2,1,1],[2,1,3]],
                              "items": [
                                { "item": "ars_nouveau:arcane_pedestal", "label": "Arcane Pedestal", "type": "input", "description": "Place adjacent and load with fuel (Blazing Archwood for best Source output)." }
                              ],
                              "camera": { "yaw": -145.0, "pitch": -38.0, "zoom": 4.2, "lerpType": "EASE", "lerpTicks": 12 }
                            },
                            {
                              "tick": 130,
                              "caption": "Add Source Jars nearby for the output. The heat produced can spawn Lava Lilies and convert nearby stone to lava — plan placement carefully.",
                              "show": "all",
                              "items": [
                                { "item": "ars_nouveau:source_jar", "label": "Source Jar", "type": "output" }
                              ],
                              "camera": { "yaw": -155.0, "pitch": -42.0, "zoom": 5.0, "lerpType": "EASE", "lerpTicks": 12 }
                            }
                          ],
                          "globalMistakes": [
                            "Avoid placing flammable blocks near a Volcanic Sourcelink — the heat it generates can be destructive.",
                            "Source Jars must be within 5 blocks to receive output."
                          ],
                          "mistakes": [],
                          "optionalGroups": []
                        }
                        """);
    }

    public static PhantasiaScriptData vitalicSourcelink() {
        return PhantasiaScriptData.fromJson(
                """
                        {
                          "machine": "ars_nouveau:vitalic_sourcelink",
                          "startCamera": { "yaw": -135.0, "pitch": -30.0, "zoom": 3.5 },
                          "steps": [
                            {
                              "tick": 0,
                              "caption": "The Vitalic Sourcelink generates Source from nearby mob deaths and animal breeding. It also passively generates Source from baby animals and accelerates their growth.",
                              "show": "pos",
                              "positions": [[2,1,2]],
                              "items": [
                                { "item": "ars_nouveau:vitalic_sourcelink", "label": "Vitalic Sourcelink", "type": "catalyst" }
                              ],
                              "camera": { "yaw": -135.0, "pitch": -30.0, "zoom": 3.5, "lerpType": "SNAP", "lerpTicks": 0 }
                            },
                            {
                              "tick": 70,
                              "caption": "Place it at the centre of a mob farm or animal pen. Source Jars nearby will fill as mobs die or breed in the area.",
                              "show": "all",
                              "items": [
                                { "item": "ars_nouveau:source_jar", "label": "Source Jar", "type": "output" }
                              ],
                              "camera": { "yaw": -150.0, "pitch": -42.0, "zoom": 4.8, "lerpType": "EASE", "lerpTicks": 15 }
                            }
                          ],
                          "globalMistakes": [
                            "Source Jars must be within 5 blocks to receive the generated Source."
                          ],
                          "mistakes": [],
                          "optionalGroups": []
                        }
                        """);
    }

    public static PhantasiaScriptData mycelialSourcelink() {
        return PhantasiaScriptData.fromJson(
                """
                        {
                          "machine": "ars_nouveau:mycelial_sourcelink",
                          "startCamera": { "yaw": -135.0, "pitch": -30.0, "zoom": 3.5 },
                          "steps": [
                            {
                              "tick": 0,
                              "caption": "The Mycelial Sourcelink generates Source from nearby food items. Source Berry food gives significantly more Source than regular food.",
                              "show": "pos",
                              "positions": [[2,1,2]],
                              "items": [
                                { "item": "ars_nouveau:mycelial_sourcelink", "label": "Mycelial Sourcelink", "type": "catalyst" }
                              ],
                              "camera": { "yaw": -135.0, "pitch": -30.0, "zoom": 3.5, "lerpType": "SNAP", "lerpTicks": 0 }
                            },
                            {
                              "tick": 70,
                              "caption": "It will also convert the 3×3 below it into Mycelium and grow mushrooms around it if space is empty. It pulls food items from adjacent Arcane Pedestals.",
                              "show": "pos",
                              "positions": [[2,1,2],[1,1,2],[3,1,2],[2,1,1],[2,1,3]],
                              "items": [
                                { "item": "ars_nouveau:arcane_pedestal", "label": "Arcane Pedestal", "type": "input", "description": "Place adjacent and load with food. Source Berry items give the most Source." }
                              ],
                              "camera": { "yaw": -145.0, "pitch": -38.0, "zoom": 4.2, "lerpType": "EASE", "lerpTicks": 12 }
                            },
                            {
                              "tick": 130,
                              "caption": "Add Source Jars within 5 blocks to capture the output. Pair with Starbuncles harvesting Source Berry Bushes for a self-sustaining Source loop.",
                              "show": "all",
                              "items": [
                                { "item": "ars_nouveau:source_jar", "label": "Source Jar", "type": "output" }
                              ],
                              "camera": { "yaw": -155.0, "pitch": -42.0, "zoom": 5.0, "lerpType": "EASE", "lerpTicks": 12 }
                            }
                          ],
                          "globalMistakes": [
                            "Source Jars must be within 5 blocks to receive the generated Source."
                          ],
                          "mistakes": [],
                          "optionalGroups": []
                        }
                        """);
    }

    public static PhantasiaScriptData alchemicalSourcelink() {
        return PhantasiaScriptData.fromJson(
                """
                        {
                          "machine": "ars_nouveau:alchemical_sourcelink",
                          "startCamera": { "yaw": -135.0, "pitch": -30.0, "zoom": 3.5 },
                          "steps": [
                            {
                              "tick": 0,
                              "caption": "The Alchemical Sourcelink generates Source by consuming potions from adjacent Potion Jars. More complex potions — longer duration, higher level, multiple effects — produce more Source.",
                              "show": "pos",
                              "positions": [[2,1,2]],
                              "items": [
                                { "item": "ars_nouveau:alchemical_sourcelink", "label": "Alchemical Sourcelink", "type": "catalyst" }
                              ],
                              "camera": { "yaw": -135.0, "pitch": -30.0, "zoom": 3.5, "lerpType": "SNAP", "lerpTicks": 0 }
                            },
                            {
                              "tick": 70,
                              "caption": "Place Potion Jars directly adjacent to the Sourcelink. Use a Wixie to autocraft potions into those jars for fully automated Source generation.",
                              "show": "pos",
                              "positions": [[2,1,2],[1,1,2],[3,1,2],[2,1,1],[2,1,3]],
                              "items": [
                                { "item": "ars_nouveau:potion_jar", "label": "Potion Jar", "type": "input", "description": "Must be placed directly adjacent (not diagonal). Fill with potions — more complex potions yield more Source." }
                              ],
                              "camera": { "yaw": -145.0, "pitch": -38.0, "zoom": 4.2, "lerpType": "EASE", "lerpTicks": 12 }
                            },
                            {
                              "tick": 130,
                              "caption": "Add Source Jars within 5 blocks for the output. This is one of the highest-yield Sourcelinks when paired with complex potions from a Potion Melder.",
                              "show": "all",
                              "items": [
                                { "item": "ars_nouveau:source_jar", "label": "Source Jar", "type": "output" }
                              ],
                              "camera": { "yaw": -155.0, "pitch": -42.0, "zoom": 5.0, "lerpType": "EASE", "lerpTicks": 12 }
                            }
                          ],
                          "globalMistakes": [
                            "Potion Jars must be directly adjacent (not diagonally) to the Alchemical Sourcelink.",
                            "Source Jars must be within 5 blocks to receive the generated Source."
                          ],
                          "mistakes": [],
                          "optionalGroups": []
                        }
                        """);
    }

    private ArsNouveauStaticScripts() {}
}
