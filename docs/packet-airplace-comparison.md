<!--
  This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
  Copyright (c) THM Addons contributors. Credit the devs, keep the link.
  By using this code you agree to the license terms and to keep your repo public.
-->

# Packet airplace: 1.21.11 and 26.2

Compared the local `1.21.11` branch with the current `26.2` implementation. The table describes
the placement protocol; the budget fixes below restore available placement capacity.

| Behavior | 1.21.11 | 26.2 |
| --- | --- | --- |
| No usable neighbor | Clicks the target block center with face `UP`. | By default, clicks a horizontal face toward the player's eyes. `legacy-air-place` restores the 1.21.11 center/`UP` click. |
| Existing neighbor | Clicks the neighbor's opposing face. | Same behavior. |
| `Always` versus `Smart` | Both use a neighbor when available; the shared helper still searches for one. | Also equivalent when a neighbor is available. |
| Rotation selection | HighwayBuilder passes the mining rotation flag. | HighwayBuilder passes the placement rotation flag. |
| Successful dispatch | The helper returns true after scheduling/sending, without checking packet cancellation. | HighwayBuilder verifies `PacketEvent.Sent` before recording the pending placement and placement credit. |
| Deferred callbacks | No equivalent final HighwayBuilder validation. | Rechecks module state, target availability, held block, pending target and rotated face before sending. |
| Swap restoration | Restores the selected slot after the send path. | Restores it in `finally`, including rejected sends. |
| Prediction sequence | `PlayerInteractBlockC2SPacket`, sequence `0`. | Mojang-named `ServerboundUseItemOnPacket`, still sequence `0`. |
| Pending placements | `PacketPlaceTracker` waits for block updates and probes unanswered targets with an inert hand. | Same policy, with extracted state logic for tests and an extra check for unloaded target chunks. |

Both versions already suppress repeated sends for a pending air-place target. The existing wait
for a block update is not a new 26.2 delay.

Relevant code: `HighwayBuilderTHM.placeWithoutPrediction`, `HighwayBuilderTHM.packetAirPlaceHit`,
`PlacementUtils.placeBlockPacket`, `PlacementUtils.packetPlacementHit`, and `PacketPlaceTracker`.

## Legacy airplace comparison toggle

HighwayBuilder → Paving → `legacy-air-place` defaults off and appears while Packet Build is on
and `air-place-mode` allows air placement. Enable it to send the 1.21.11 target-center click with
face `UP` for unsupported targets. Disable it to compare the current horizontal-face click.
Supported placements, inventory swaps, packet budgeting and pending confirmation are unchanged.
The toggle isolates click geometry; it does not promise faster server acceptance.

## Packet-budget fixes

The 26.2 General packet budget was applied even when disabled. Reclaiming unused mining
capacity also depended on the Experimental master switch, although the budget is in General.
Fast Packet Build now uses the actual capacity remaining after mining in every placement state.
Its budget resets each tick and initializes before double mining; START, STOP, ABORT and swing
costs from that early work reduce the same allowance.

For example, a 23-packet budget with movement reserved, swings filtered and no silent swaps
leaves 22 packets. Reserving 11 two-packet mines previously left zero placement slots. If only
two mines run, the corrected fast path can use the remaining 18 placement slots that tick.
This is tested allocation arithmetic, not a measured server placement rate. Disabling the budget
removes this internal limit; normal-rate mode retains its configured rate and delay.

Hit geometry, server-update confirmation and pending-target duplicate protection are unchanged
by these budget fixes. No claim of measured 1.21.11 speed parity is made.

Reviewed NoCheatPlus master at
[`d758184`](https://github.com/6b6t/NoCheatPlus/commit/d7581841c327ad6f88dc4fc88518e2f39de044b5).
Its recent placement-related change covers offhand crystals. The current
[FastPlace check](https://github.com/6b6t/NoCheatPlus/blob/d7581841c327ad6f88dc4fc88518e2f39de044b5/NCPCore/src/main/java/fr/neatmonster/nocheatplus/checks/blockplace/FastPlace.java)
has no client-version-specific 26.2 delay. The deployed server configuration was not inspected.
