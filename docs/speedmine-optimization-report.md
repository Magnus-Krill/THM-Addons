<!--
  This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
  Copyright (c) THM Addons contributors. Credit the devs, keep the link.
  By using this code you agree to the license terms and to keep your repo public.
-->

# Speedmine optimization report

Date: 2026-10-28. Branch: `26.2`.

Speedmine now advances mining work independently of rendering, checks actual packet dispatch before
predicting a break, preserves delayed secondary mining, and recovers unanswered predictions through
normal mining. Existing settings and enum values remain available.

## Reference and scope

The reference is the supplied local `clientsSkidiing/SpeedMine.java`, identified as Mio's reconstructed
Minecraft 1.21.11 implementation. Its header explicitly marks many constants as guesses. None of those
timing values, native assumptions, module dependencies, or packet bursts were copied into THM.

The comparison also used this project's Minecraft 26.2 sources for `ServerPlayerGameMode`,
`ClientLevel`, `Player`, and `BlockStatePredictionHandler`, plus the resolved Meteor sources for
rotation, inventory, and packet events.

| Area | Useful observation from Mio | THM implementation |
| --- | --- | --- |
| Progress | Accumulate work as conditions change. | Integrate damage once per client tick; rendering reads a snapshot. |
| Secondary mining | Treat the secondary as delayed server mining. | Keep the first accepted STOP and wait for confirmation instead of sending a redundant final STOP. |
| Swaps | Give each mining operation a clear tool and restoration boundary. | Use the existing InventoryManager with a scoped silent-slot operation and validation holds. |
| Desync recovery | Abandon a stale instant cycle and restart normal mining. | Roll back owned predictions, send safe probes where possible, and retry START after a bounded delay. |
| Instant rebreak | Keep a completed target armed for replacements. | Preserve Off/Strict/Strong/Bypass and both triggers, with one attempt per client tick. |

## Mining and packet timing

- Progress uses a monotonic clock. Clock corrections cannot create mining credit or extend waits.
- START contributes its first tick only after the packet reaches Meteor's `PacketEvent.Sent` hook.
  A cancelled START keeps an unstarted context that can retry without earning mining work.
- Damage accumulates from each interval's tool, effects, movement penalty, and TPS. New haste,
  fatigue, or ground conditions no longer multiply the entire elapsed mining history.
- Pauses update the clock without adding damage, so resuming cannot catch up through the pause.
- A primary that reaches the configured threshold on START can finish immediately, without waiting
  for another client tick. A genuinely instant block predicts on the START sequence and omits STOP.
- When rotation is enabled, a rotation packet precedes the mining action. The cache follows actual
  outgoing movement rotations, including manual turns, and suppresses unchanged mining rotations.
- Mining actions use the existing `RangeUtils.nearestExposedFace` helper instead of always DOWN.
- Early STOP and final completion share `stopSent` state. The delayed secondary does not send another
  STOP after the primary changes the server's active target, and it waits for server air rather than
  manufacturing a secondary air prediction.
- Failed mining packets do not remove contexts, create completion records, or predict local air.
  TPS credit for rebreaks is consumed only after successful dispatch.

## Pending breaks, queues, and recovery

- Pending entries retain their latest server state and whether Speedmine owns an air prediction.
- Explicit air updates clear matching validation and arm the correct last-started target. Finishing
  an older secondary no longer replaces the primary's rebreak position.
- Explicit solid updates restore owned predicted air and end ownership of that prediction.
- Completing another target no longer drops unrelated instant/rebreak confirmation records.
- Repeated attempts at one position preserve its earliest deadline, so Strong cannot postpone
  recovery indefinitely by replacing its pending entry every tick.
- Confirmation waits include actual estimated server TPS, tool speed, and player latency, bounded
  between 2 and 30 seconds. Recovery delay also accounts for latency and a server tick.
- On timeout, Speedmine restores its last known state, clears stale rebreak priming, and queues a
  normal START. It reuses `PacketPlaceTracker.sendProbes`, including adjacent-target pairing and
  inert-hand selection, rather than adding another probe implementation.
- Loaded predictions also roll back when leaving range or disabling the module. Unloaded chunks
  are discarded without world reads or probe traffic. World changes clear contexts and queues.
- A changed block type or tool identity/components refreshes the affected context. A primary gets
  a fresh START; a secondary retains its existing delayed STOP instead of restarting both targets.
- Queue-disabled mode no longer adds a backlog solely because TPS credit is unavailable. Invalid,
  unbreakable, out-of-range, and unloaded queued positions are pruned.
- Strong still skips confirmation and TPS-start waits for its completed target. Other normal
  validations retain priority. Pre, Post, and packet callbacks share the one-attempt-per-tick limit.

## Silent swaps and InventoryManager

- SameTick uses the context's selected tool instead of rescanning the hotbar during every operation.
  EndOfTick, Keep, ToolHold, visible swaps, and normal validation holds remain available.
- Both Speedmine and the existing InventoryManager tool wrapper use `withSilentSlot`. Restoration
  runs in `finally`, preserves an existing server hold, respects a changed client selection, and
  avoids overwriting a slot that another action acquired.
- Higher-priority inventory work can defer restoration. InventoryManager stores one deferred slot,
  restores it once free if it still matches the owned slot, and clears it on disconnect. Continued
  mining cancels that deferred restoration for the retained tool.
- Server-slot tracking changes on `PacketEvent.Sent`, not on a send attempt. Forced swaps validate
  hotbar indices and no longer record rejected packets as successful selections.
- Server-forced slot changes are applied on the client thread only for the original connection.
- Tool holds send a slot packet only when the tracked server slot differs. Previous-slot snapshots
  are captured before switching, and obsolete swap snapshots now expire even without an explicit sync.
- Tool selection is shared with InventoryManager and considers Efficiency, correct-tool requirements,
  and a stable preference for the already held tool on equal scores. Empty/slow stacks skip the
  enchantment lookup. Slot packets no longer allocate block-prediction sequences.

## Performance changes

These are verified control-flow changes, not measured FPS or accepted breaks per second:

- `MineContext.progress()` no longer reads the world, scans enchantments, or changes timing. At
  normal client speed, damage evaluation runs once per active context per tick instead of on every
  rendered frame as well as mining checks.
- The primary can act one client tick earlier when its first mining tick already meets the threshold.
- Each genuinely instant break omits one STOP. Each delayed secondary omits one final STOP.
- An unchanged held tool needs no repeated forced-slot packets. SameTick avoids redundant tool scans.
- Unchanged mining rotations are reused, and redundant Pre/Post rebreak attempts are suppressed.
- Confirmation state is pruned by acknowledgement, deadline, reach, unloading, and world changes;
  historical positions are not retained indefinitely. Empty pending lists skip recovery allocation.

The vanilla delayed-break slot supports one secondary position. Extra START/STOP/ABORT bursts cannot
turn it into arbitrary parallel mining; THM preserves the two-context model instead.

## Compatibility and separate Scaffold request

No Speedmine setting names, defaults, or enum values were removed. Auto Mine, manual requests,
HighwayBuilder's public `requestBreak`/`isMining` integration, bedrock's vanilla mining path, optional
Air Miner, multitasking, and render helpers remain in place. No dependency was added.

The additional Scaffold request is implemented as `modules/THMScaffold.java`. It contains its settings,
placement logic, acknowledgement tracking, and optional packet budget in one source file. Its limiter
has only an enable toggle and a numeric limit. It can run with the external PaketLimiter disabled.
The old ScaffoldMixin and its JSON entry are removed; Meteor Scaffold is unmodified. The existing
legacy `scaffold-THM` module remains available. THMScaffold has its own saved configuration; it does
not automatically transfer the separate Meteor Scaffold profile.

The existing PaketLimiter safety fix continues to allow hotbar slot changes, including restorations.
The shared dropdown descriptions now also cover Meteor's block-list mode choices.

## Validation

Commands use Java 25:

```sh
JAVA_HOME=/usr/lib/jvm/java-25-openjdk ./gradlew test \
  --tests xyz.thm.addon.modules.SpeedmineTest \
  --tests xyz.thm.addon.modules.THMScaffoldTest \
  --tests xyz.thm.addon.utils.InventoryManagerTest \
  --tests xyz.thm.addon.MixinTargetsTest --console=plain
JAVA_HOME=/usr/lib/jvm/java-25-openjdk ./gradlew build --console=plain
git diff --check
```

- Full suite: **624 tests, zero failures, errors, or skips**.
- Speedmine: **96 cases**, including 13 added cases for render-independent progress, changing speed,
  pauses, TPS/clock jitter, primary versus secondary thresholds, bounded retry deadlines, and latency.
- InventoryManager: **2 added cases** covering current-selection restoration and ownership checks.
- THMScaffold: **6 cases** covering whole-transaction budgets, acknowledgements, reset, and migration.
- Mixin target checks and dropdown-description checks pass. An initial full run exposed the missing
  block-list tooltips; the shared description provider was fixed before the successful run.
- Build artifact: `build/libs/THM-Addons-0.3.0.jar`.

## Remaining limits

- Dispatch is not server acceptance. Explicit server block updates remain the confirmation boundary;
  local prediction and sequence acknowledgements do not prove a successful break.
- Server plugins, protection, anti-cheat policy, network delay, and TPS determine accepted throughput.
  No matched server-throughput or hardware-CPU benchmark was collected for this change, so no
  numerical BPS/FPS improvement is claimed. Gradle checks do not simulate a complete live client.
- Safe probes require an inert hand. When none is available, recovery uses the restored last-known
  state and a normal START; it never treats an unanswered probe as an air confirmation.
- A last-known-state rollback can briefly show a block that the server has already removed until
  its authoritative update arrives. This is preferable to retaining an unverified local air block.
- Strong is bounded to one attempt per client tick. It remains more permissive than Strict and
  Bypass before confirmation; that existing choice cannot guarantee acceptance on every server.
