<!--
  This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
  Copyright (c) THM Addons contributors. Credit the devs, keep the link.
  By using this code you agree to the license terms and to keep your repo public.
-->

# Minecraft 26.2 port progress

## 2026-11-06: Legacy airplace comparison toggle

Paving's default-off `legacy-air-place` uses the 1.21.11 target-center click with face `UP`
when Packet Build has no supporting neighbor. It appears while air placement is allowed.
Supported placements, confirmation, duplicate guards, swaps and budgeting retain their behavior.

Validation: the asynchronous full build passed with 697 tests and no failures, errors or skips,
including both click geometries and the isolated OpenGL startup test. The resulting JAR was
installed in the Meteor 26.2 instance with a backup. The user will compare server behavior;
no placement-speed result is claimed from these automated tests.

## 2026-11-06: Packet-budget opt-out and fast placement capacity

HighwayBuilder now applies its General packet budget only while enabled. Fast Packet Build uses
packets actually left after mining in the same tick, without the Experimental master gate or
excluding double-mine ticks. Budget state clears before early returns and initializes before
Packet Borer and double mining. Their START, STOP, ABORT and swing costs reduce that allowance;
completed block counts no longer stand in for packets sent.

Validation: the asynchronous full build passed with 696 tests and no failures, errors or skips.
The isolated OpenGL startup test also exercises the loaded module's enabled/disabled budget,
leftover placement capacity with Experimental off, and early mining packet charges. Its test
source set compiles against the main addon output and remains absent from the release JAR.
An allocation regression verifies 18 available placements after two mines under a 23-packet
budget with swings filtered and no silent swaps. This is not a live server BPS benchmark.

Placement geometry, pending-target protection and server-update confirmation are unchanged.
Reviewed public NoCheatPlus master without finding a client-version-specific ordinary-block
placement delay; the deployed server configuration was not inspected. Details:
[packet airplace comparison](docs/packet-airplace-comparison.md).

## 2026-11-06: Recovery ownership and periodic auto alignment

Highway Monitor captures monitor-owned builder pause intent before disconnect cleanup and can
prepare reconnect for that paused builder. It cancels recovery goals and keys on leaving the main
server. Normal grounded drift now selects `Misalignment`, restoring its server-state and maximum
distance checks. Airborne height offsets alone do not start normal recovery. Corrected the
inverted Netherrack blacklist warning.

Renamed `align-first` to `auto-align` in General and Basic. Module configs, Highway Profiles and
named presets migrate the old preference, including disabled values; named preset migration uses
the existing backup and atomic-write flow. Forward building checks lateral drift every 20 client
ticks. Walking correction keeps the working direction and current statistics, skips restock and
airborne movement, and retains the existing 32-block clear-path limit.

Validation: the asynchronous full build passed with 694 tests and no failures, errors or skips.
Coverage includes recovery cause selection, disconnect resume intent, direction-preserving line
selection, cross-track distances and saved-setting/preset migration. The isolated OpenGL startup
test passed. World recovery and server placement speed are outside this startup check's scope.

Reviewed packet airplace against the local `1.21.11` branch without changing placement behavior:
[comparison](docs/packet-airplace-comparison.md).

## 2026-11-06: End backup-lobby detection

Server detection now reads `mc.gameMode.getPlayerMode()` instead of the self tab-list entry.
The reported restart log committed `UNKNOWN -> MAIN_SERVER` from tab-list Survival immediately
after transfer, allowing Highway Monitor to resume recovery in the backup lobby. Vanilla login,
respawn and gamemode-change handlers update the local mode independently of tab-list metadata.
Detection logs now include the dimension. Adventure End remains transfer/waiting, trusted cracked
authentication keeps its existing route, and Survival End remains main-server gameplay.

Validation: the asynchronous build passed with 688 tests and no failures, errors or skips,
including a regression check against vanilla mode-update handlers and the detector's mode source.
The isolated OpenGL client startup test passed. This validates the source contract, classification
rules and startup; it does not simulate a live 6b6t restart.

## 2026-11-06: Asynchronous tests and client startup smoke test

`tools/scripts/test-async.sh` detaches the Gradle build and writes a per-run log and exit code.
JUnit runs test classes concurrently with parallelism four. `MinecraftStartupTest` launches a
separate client through Loom's generated launch configuration, waits one second at the main menu,
then requests a normal shutdown. Its test mod and run directories stay outside the release JAR
and player instance. Startup crashes, missing readiness and a 180-second timeout fail JUnit.

Validation: the complete background build passed with 687 tests, zero failures, errors or skips.
JUnit timestamps confirm the startup test and three Java test classes began concurrently. The
client completed THM registration, rendered the menu using OpenGL 4.6 / Mesa 26.2.6-arch3.1,
waited 1.05 seconds and exited with code 0. Logs are under
`build/client-startup/run-13974059799752282622/`; the async result is under
`build/async-tests/run-DNRp8T/`. Confirmed the startup mod is absent from the addon JAR.
CI now supplies Xvfb and Mesa. No world/server or Vulkan runtime coverage is claimed by this test.

## 2026-11-05: Inventory and mining utilities

Ported the reviewed Bephax 0.5.5 / Minecraft 1.21.11 utility ideas to the current Mojang-named Java 25 / Minecraft 26.2 APIs. Shared swap ownership and checked inventory staging integrate with Speedmine, CrystalAura, Surround, KillAura, Nuker, XP throwing and pearl actions. Rotation state clears on disconnect and server corrections. Loadouts preserve legacy saves while new saves record components and minimum quantities. Added opt-in catch-up pacing, Shulker Overview, AutoArmor mending rotation, AutoTotem pop refill and Speedmine tool-latency/queue-through settings. Atomic AutoTotem swaps retain Meteor's normal health/delay decisions. Inventory movement settling is omitted for 6b. HighwayBuilder/Scaffold placement remains at the rolled-back implementation.

Validation: `build`, `checkBuildRules` and `checkInventoryRules`, including mixin target bytecode, ownership/restore ordering, pacing, tool-history windows, component codec round trips and queue-through geometry. No client launched. This records automated coverage; no new graphics-backend or server-performance result is claimed.

Settings and source reference: [Bephax utility adaptations](docs/bephax-utilities.md).
