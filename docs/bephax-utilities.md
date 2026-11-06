<!--
  This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
  Copyright (c) THM Addons contributors. Credit the devs, keep the link.
  By using this code you agree to the license terms and to keep your repo public.
-->

# Bephax utility adaptations for Minecraft 26.2

These changes adapt ideas reviewed in Bephax 0.5.5 for Minecraft 1.21.11 to THM Addons' 26.2 APIs. Reference: [Bephax by dekrom and contributors](https://github.com/dekrom/Bephax/). The local reference was `bephax-1.21.11.jar_Decompiler.com` in Downloads. THM retains its mining prediction, TPS handling and server-confirmed break logic.

## Inventory utilities

`SwapManager` gives a module ownership of a silent hotbar hold. A higher-priority temporary action borrows the slot and restores its previous owner in `finally`. Manual/server slot changes, player changes and disconnects invalidate ownership. Slot tracking records packets that were actually sent.

`DeepSwapManager` stages inventory items in unused, unreserved hotbar slots. Sessions are bounded to three. Restoration verifies both the staged item and the displaced source stack, and waits when a container, cursor item or packet budget prevents a safe click. A cancelled staging click does not authorize item use. HighwayBuilder's managed slots remain reserved. Inventory swaps do not pause movement or require standing still.

Callers use `InventoryManager.withItem(owner, inventorySlot, silent, priority, action)`. Inventory click sequences check the remaining Paket Limiter budget before starting. This does not bypass packet cancellation.

## Settings

| Location | Setting | Default | Behavior |
| --- | --- | --- | --- |
| Speedmine | `queue-through` | Off | Clicking an already queued block queues the next visible block behind it, once per press. |
| Speedmine | `queue-limit` | 32 | Bounds the mining queue. |
| Speedmine | `tool-latency-check` | Off | Uses recent server-held Efficiency levels over a ping-based window. |
| AutoTotem, THM Swaps | `atomic-swap` | On | Replaces cursor moves with a single offhand SWAP click. |
| AutoTotem, THM Swaps | `instant-pop-refill` | Off | Refills after a pop without waiting for the configured tick delay, while retaining the held-item filter. |
| AutoArmor, THM Mending | `mending-rotation` | Off | Exchanges repaired equipment for damaged mending pieces of the same kind. |
| AutoArmor, THM Mending | `mending-threshold` | 100% | Durability at which the next mending piece can be equipped. |
| AutoArmor, THM Mending | `mending-swap-delay` | 10 ticks | Spaces equipment swaps. |
| THM Highway | `shulker-overview` | Off | Overlays the dominant contents on shulker icons in inventories and the hotbar. |
| THM Highway | `catch-up-pacing` | Off | Limits catch-up bursts to three ticks per 60 ms and carries deferred ticks forward. |

AutoArmor preserves binding-curse, avoided-enchantment and equipped-elytra protections. Catch-up pacing affects the client tick loop, so leave it off when deliberate fast Timer behavior is needed. Shulker icons use Minecraft's GUI extraction and THM render helpers for both graphics backends; their cache is capped at 256 entries.

## Loadout compatibility

New saves retain item components, including enchantments, custom names and shulker contents, and the minimum stack quantity. Tool wear does not change identity. Existing item-ID strings retain their original item-only matching behavior. Loadout writes are atomic; a malformed or unreadable file is preserved rather than replaced with an empty save.

Restoring a layout keeps extra supplies rather than discarding them. Regear only pulls from an open container into player inventory slots that the container exposes. Armor/offhand layout sorting occurs in the player inventory. Each verification pass reads the current inventory again after the configured sync delay.

## Verification

- `./gradlew build` checks compilation, unit behavior and registered mixin target bytecode.
- `./gradlew checkInventoryRules` bootstraps vanilla registries without a client and checks components, quantities, legacy matching, shulker aggregation and queue-through rays.
- `./gradlew checkBuildRules` retains the existing block-state checks.

No new packet-placement retry or scheduling behavior is included in this port.
