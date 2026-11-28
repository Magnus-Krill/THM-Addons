<!--
  This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
  Copyright (c) THM Addons contributors. Credit the devs, keep the link.
  By using this code you agree to the license terms and to keep your repo public.
-->

# Better Baritone Build

Enable `better-baritone-build` before starting a Baritone build. Open its module settings and choose
**Build and storage manager** for the custom screen.

## Index storage

1. Leave **Link storage** enabled.
2. Open each supply chest, barrel, or placed shulker box once and wait for its inventory to load.
3. Check the **Storage** tab for its position and contents.
4. Start your usual Baritone build command.

When Baritone pauses for missing materials, the module visits the nearest indexed supplier,
opens it, and takes matching stacks. It can visit several suppliers in one trip. Once stocked,
Baritone continues the same schematic from its current progress.

Loose stacks are taken first. When a material is only packed inside a shulker item, the module takes the
shulker and places it nearby, preferring the underside of a block so it opens downward, as under a sky logo.
It never places above the build height limit. After taking the material, a shulker with items left stays
placed and indexed, and later trips take the rest from it. Once empty, it is broken and left to despawn; an
empty shulker picked up within five minutes is thrown out again. Shulkers already in your inventory are
unpacked the same way.

The index persists separately for each server/world and dimension. Double chests share one entry.
Reopening storage refreshes its contents; changes made while it is closed become visible on the next visit.
Items packed inside shulker items count toward their container. Ender chests are not indexed. The index holds up to 2,048 containers per dimension.

The module takes whole stacks, up to the configured target for each material. It never drops items
to make room. If it gets some useful materials before filling the inventory, it resumes building with those.
If no supplier works, it leaves the builder paused and reports why. After fixing the problem,
press **Resume build**. Disabling the module during a supply trip also leaves the build paused.

## Build rules

Use **Block rules** to select a schematic block, a replacement block, and a rule type:

- **Replace:** Build the selected replacement. For example, `cobblestone → stone_bricks` builds stone bricks
  wherever the schematic asks for cobblestone. Compatible block properties, such as log axis, carry over.
- **Accept:** Keep the selected block if it already exists at that position. Empty positions still receive
  the schematic block. For example, accepting `stone_bricks → cobblestone` preserves existing cobblestone.

Replacements apply once, rather than chaining through other replacement rules. Rules are saved in the module's
Meteor configuration and do not change Baritone's global settings. Enable the module before starting a new build
to attach the rules. Changes to an attached build take effect immediately; changing rules during retrieval
pauses that trip so you can resume with the new materials.

The **Settings** tab also provides:

- **Grass as dirt:** Treat dirt and grass as equivalent; use dirt for new placements.
- **Strip logs:** Use raw logs/wood/stems where stripped blocks are requested, then strip them in place.
  Keep an axe in the hotbar. Already available stripped blocks can be placed directly.
- **Ignore existing:** Preserve all existing non-air blocks and fill empty positions.
- **Ignore direction:** Accept existing blocks with another axis, facing, or rotation.
- **Skip blocks:** Leave positions requesting selected schematic blocks untouched.
- **Keep in air:** Preserve selected existing blocks where the schematic requests air.

## Manual validation

Run `./gradlew checkBuildRules` for standalone checks against real vanilla block states, including
replacement precedence, acceptance rules, grass/dirt handling, log axes, and raw-to-stripped targets.
This initializes registries without starting Minecraft's client or loading a world.

Automated checks do not establish multiplayer behavior. In a disposable build, verify:

1. Index a single chest, both halves of a double chest, a barrel, and a placed shulker.
2. Run a build with two missing materials stored in different containers; confirm retrieval and continued placement.
3. Empty or remove a previously indexed supplier; confirm fallback to another supplier or a useful paused status.
4. Store a material only inside a shulker in a chest, and another in an inventory shulker; confirm both are unpacked, partly used shulkers stay placed and are reused on the next trip, and emptied ones are broken and tossed. Repeat under a sky logo at the height limit.
5. Fill the inventory, obstruct a chest, and test a protected chest; confirm no drops or repeated item-click loops.
6. Cancel Baritone, disable the module, disconnect, and change dimensions during retrieval; confirm no continued looting.
7. Reconnect and restart the client; confirm storage remains isolated by server and dimension.
8. Test replacement and acceptance rules separately, then dirt/grass equivalence and each skip setting.
9. Build stripped logs in different axes with a hotbar axe, then test with no axe and a protected log.
