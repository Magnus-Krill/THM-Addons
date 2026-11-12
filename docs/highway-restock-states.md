<!--
  This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
  Copyright (c) THM Addons contributors. Credit the devs, keep the link.
  By using this code you agree to the license terms and to keep your repo public.
-->

# HighwayBuilder restock state transitions

Every allowed transition between restock states. A transition that is not listed here is a bug.
Check new transitions against this table before adding them, and add a test in
`HighwayBuilderTHMTest` when the decision can be expressed as a static function.

## How returns work

`setState(next)` stores the current state as `lastState`. `setState(next, owner)` stores `owner` instead.
Center and ThrowOutTrash have no successor of their own: they finish with `setState(lastState)`,
so they return to their **owner**, and the owner then sees Center or ThrowOutTrash as `lastState`.
The owner reads its setup progress from that value (`nextBlockadeSetupState`):

| `lastState` seen by the owner | Next step |
|---|---|
| anything else | Center |
| Center | ThrowOutTrash |
| ThrowOutTrash | the blockade state |
| the blockade state | setup finished |

`lastState` is a single slot, not a stack. Whoever enters Center or ThrowOutTrash must pass the
real owner, never Center or ThrowOutTrash itself, or the two states return to each other forever.

## Transitions

| From | Condition | To (`lastState`) |
|---|---|---|
| `RestockTask.activate` | task started | Restock |
| KitBot failure fallback | e-chest bank as fallback | Restock (KitbotOrder) |
| KitbotOrder | blockade restored | Restock (KitbotOrder) |
| obsidian pickaxe preflight | pickaxes needed first | Restock (Restock) |
| Restock.start | no active task, pending task exists | Restock.start again |
| Restock.start | no active and no pending task | Forward (sequence finished) |
| Restock.start | stale e-chest or food task | `completeRestockTaskAndContinue` |
| Restock.start | `lastState == PlaceShulkerBlockade` | blockade marked ready, continues |
| Restock.start | blockade not ready | `nextBlockadeSetupState(lastState, PlaceShulkerBlockade)` |
| Restock.start | source position missing | blockade not ready, Center (Restock) |
| Restock | inventory has loose e-chests for obsidian | MineEnderChests |
| Restock | KitBot first | KitbotOrder |
| Restock | task done | `completeRestockTaskAndContinue` |
| Restock | invalid source block after retry | MineShulkerBlockade (Restock) |
| MineEnderChests.start | sequence active, blockade not ready | `nextBlockadeSetupState(lastState, PlaceShulkerBlockade)` |
| MineEnderChests.start | no sequence | `nextBlockadeSetupState(lastState, PlaceEChestBlockade)` |
| MineEnderChests | nothing to mine / fallback | Restock (Restock) or `completeRestockTaskAndContinue` |
| MineEnderChests | source position missing | blockade not ready, Center (MineEnderChests) |
| PlaceShulkerBlockade | placed | Restock (PlaceShulkerBlockade) |
| PlaceEChestBlockade | placed | MineEnderChests (PlaceEChestBlockade) |
| Center | centered or teleported | `lastState` (Center) |
| Center | timeout | Center (same `lastState`) |
| ThrowOutTrash | done or stalled without cursor stack | `lastState` (ThrowOutTrash) |
| ThrowOutTrash | stalled with cursor stack | builder stops with an error |
| `completeRestockTaskAndContinue` | pending task | Restock (Restock) |
| `completeRestockTaskAndContinue` | blockade must be removed | WaitForRestockBlockadeTeardown (Restock) |
| `completeRestockTaskAndContinue` | otherwise | Forward |
| WaitForRestockBlockadeTeardown | pending task | Restock (Restock) |
| WaitForRestockBlockadeTeardown | teardown may begin | MineShulkerBlockade (Restock) |
| MineShulkerBlockade | mined | MineShulkerBlockade (MineShulkerBlockade), then stop timer |
| MineShulkerBlockade stop timer | invalid-source recovery pending | Restock (Restock) |
| MineShulkerBlockade stop timer | otherwise | Forward (sequence finished) |
| MineShulkerBlockade | pending task, blockade untouched | Restock (Restock) |
| MineShulkerBlockade | pending task, blockade touched | blockade not ready, Center (`blockadeRebuildOwner(...)` = Restock) |
| MineEChestBlockade | mined | Center (Forward) |
| Restock watchdog recovery | KitbotOrder | KitbotOrder (KitbotOrder) |
| Restock watchdog recovery | otherwise | blockade not ready, Center (`restockRecoveryOwner(...)`) |
| Entering Forward | sequence still active | sequence finished and cleared |

`restockRecoveryOwner` never returns Center or ThrowOutTrash. During blockade teardown with an
active task it returns Restock, so the task is not dropped, whether recovery starts in the
teardown itself or in Center or ThrowOutTrash entered from it.

## Rule for rebuilding the blockade

Only Restock and MineEnderChests continue the setup after Center (`nextBlockadeSetupState`).
A rebuild must therefore hand Center one of them as owner (`blockadeRebuildOwner`). Teardown once
passed itself: Center returned to MineShulkerBlockade, which tore the blockade down again and
dropped the task that was just activated.
