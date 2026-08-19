# LoftsFarm slice — known issues and deferred findings

Everything here was found by review, judged, and deliberately left alone. None of it
blocks the in-server acceptance run in `ACCEPTANCE.md`. This file exists so that a
future maintainer knows which oddities are *decided* rather than undiscovered.

The build history that produced this list lived in a scratch ledger which has since
been deleted; the commits themselves are the record.

## Must fix before this goes anywhere real

Not defects in the slice — consequences of it being a throwaway on a flat test world.

- **`/farm give` is not op-gated.** Any player can mint themselves seeds and fertiliser.
- **`/farm setup` overwrites existing blocks** without confirmation. Op-gated, so contained.
- **No autosave and no save-on-quit.** There are deliberately zero repeating tasks in the
  codebase, which also means a server crash loses the session and leaves untracked crop
  blocks that resume vanilla growth. A `PlayerQuitEvent` hook is the cheap fix.
- **Persistence is a flat text file, not SQLite** (spec §6). No JDBC driver was available.
  `StateCodec` is the seam — swapping it is a one-class change.
- **`StateCodec.lastSkipped()` is static mutable state.** Safe today because loading is
  single-threaded and nothing in the plugin is async. Must become non-static before any
  async save or load.

## Deferred minors

- `Check.throwsUp` catches `RuntimeException`, not `Throwable`, so an `Error` aborts the
  test run instead of being recorded as a failure.
- `Wallet.credit` and `Storage.add` have no overflow guards. A `long`/`int` wrap could
  produce a negative balance or count; practically unreachable at these magnitudes.
- `Fertiliser.MINIMUM = 4` breaks the "always net-positive" guarantee for any future crop
  with `rawValue <= 4`. Neither slice crop is affected, and nothing enforces the tie.
- `FarmState.plants()` returns the live map rather than a defensive copy, unlike
  `Storage.snapshot()`. A caller could corrupt state through it.
- An unknown crop id in a save file is skipped but *not* added to `lastSkipped()`, which is
  inconsistent with the report-skipped-lines contract.
- `ItemMeta.setDisplayName`/`setLore` are deprecated on 1.21.10 (not removed). Blast radius
  is `Items.java` only; the Adventure `Component` swap is contained to that file.
- Creative mode still consumes a seed planting on sow, while fertilising correctly exempts
  the item. Asymmetric, documented, and tested in `ACCEPTANCE.md` step 21.
- A carrot seed is a real `Material.CARROT` and right-click-air is unhandled, so a player
  can eat their own seed. Carrots are a poor choice of seed material.
- A ripe crop owned by another player reports "Not ready — 0s left"; a failed fertilise is
  silent. Both are misleading rather than wrong.
- The event handlers run at `MONITOR` and do mutate the event (`setDropItems`,
  `setCancelled`), against `MONITOR`'s documented contract. Harmless because `MONITOR` is
  terminal, but another plugin's `MONITOR` listener gets registration-order-dependent
  visibility. `MONITOR` was still the only correct tier — a re-check at `NORMAL` cannot see
  a `HIGHEST` plugin's cancellation, which happens afterwards.
- `onGrow(BlockGrowEvent)` calls `farms.at()` without a material gate, unlike `onPhysics`.
  Left deliberately: `BlockGrowEvent` fires only on vanilla's throttled random-tick
  attempts, orders of magnitude below `BlockPhysicsEvent`, and cancelling it for tracked
  plants is a hard requirement so the lookup is load-bearing.
- `OrderService.live` and `FarmService.states` are never cleared on player quit. Growth is
  bounded by unique-player count.
- Clicking Deliver on a just-expired order card can silently deliver the *replacement*
  order. Always net-positive, so the never-subtract guarantee holds, but it is confusing.
- The order board's countdown lore is static while the GUI is open — correct under the
  redraw-on-state-change-only rule, but the deadline goes stale on screen.
- Orphaned records are never garbage-collected. `/farm setup` clears the block above each
  farmland without calling `discardAt`, and `onPhysics` does not catch it. Self-heals when
  someone replants that block. Op-only, so `farms.txt` grows slowly.

## Parked with rulings

Real, examined, and left as-is.

- **The growth-chain callback removes its task-map entry before painting.** A re-entrant
  `schedule()` inside that window would be clobbered and leak an untracked task.
  Unreachable today because `paintStage` uses `setBlockData(data, false)`, which suppresses
  physics and therefore cannot re-enter `discardAt`. Revisit if `paintStage` ever triggers
  physics.
- **`discardAt` with a null record** removes the owners mapping without unindexing, leaving
  a dangling chunk-index key that `plantsInChunk` skips. Only reachable from an already
  inconsistent state; not a correctness failure.
- **`FarmService.onChunkLoad`'s javadoc overstates its behaviour.** An unripe crop in a
  freshly loaded chunk with no player within 64 blocks is not repainted immediately — it is
  corrected at its next stage boundary. The behaviour is right; the wording is loose.
- **Test gaps in the stage-boundary suite.** Boundary minimality is asserted only at stage
  4, `CARROT` boundaries are untested, and neither the scheduler chain nor a
  post-fertiliser clock is exercised by the suite. All were verified by simulation and code
  reading during review. The scheduler is Bukkit-layer and untestable in this environment
  by design; `ACCEPTANCE.md` steps 4 and 14 cover it in-server.

## Build notes worth not rediscovering

- `build.sh` compiles against **all 138 jars** under `lofts-dev/libraries`, not a curated
  subset. Paper's API jar alone is insufficient: `javac` must resolve the full overload set
  of every Bukkit type touched, which pulls in Adventure, bungeecord-chat and Guava.
  Naming dependency groups one at a time only relocated the next failure — this was
  learned twice before being fixed properly.
- `javac` runs with **`-encoding UTF-8`**. Windows defaults to the system codepage, and the
  sources contain a mix of `§` escapes and literal `§` bytes. Without the flag the
  literal bytes compile to mojibake silently.
- Very large heredocs fail with a shell parse error in this Git Bash environment. Split
  file writes into smaller appends.
