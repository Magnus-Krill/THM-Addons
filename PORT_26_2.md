<!--
  This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
  Copyright (c) THM Addons contributors. Credit the devs, keep the link.
  By using this code you agree to the license terms and to keep your repo public.
-->

# Minecraft 26.2 port progress

## 2026-11-05: Inventory and mining utilities

Ported the reviewed Bephax 0.5.5 / Minecraft 1.21.11 utility ideas to the current Mojang-named Java 25 / Minecraft 26.2 APIs. Shared swap ownership and checked inventory staging integrate with Speedmine, CrystalAura, Surround, KillAura, Nuker, XP throwing and pearl actions. Rotation state clears on disconnect and server corrections. Loadouts preserve legacy saves while new saves record components and minimum quantities. Added opt-in catch-up pacing, Shulker Overview, AutoArmor mending rotation, AutoTotem pop refill and Speedmine tool-latency/queue-through settings. Atomic AutoTotem swaps retain Meteor's normal health/delay decisions. Inventory movement settling is omitted for 6b. HighwayBuilder/Scaffold placement remains at the rolled-back implementation.

Validation: `build`, `checkBuildRules` and `checkInventoryRules`, including mixin target bytecode, ownership/restore ordering, pacing, tool-history windows, component codec round trips and queue-through geometry. No client launched. This records automated coverage; no new graphics-backend or server-performance result is claimed.

Settings and source reference: [Bephax utility adaptations](docs/bephax-utilities.md).
