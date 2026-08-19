# LoftsFarm slice — acceptance run

Drop `build/LoftsFarm.jar` into a Paper 1.21.10 test server's `plugins/` folder.
Use a superflat world. Do NOT use the live lofts-dev server — v2 is isolated by
design and this is a throwaway.

0. Before touching the server, run the domain test suite: `bash loftsfarm/run-tests.sh`
   PASS: 145 assertions, 0 failures.

1. `/farm setup` — lays 12 farmland blocks beside you.
   PASS: 12 farmland blocks appear in a line to your right.
2. `/farm give wheat` — one seed, 8 plantings, showing a damage bar.
3. Right-click farmland with it. A wheat crop appears at stage 0.
   PASS: a wheat crop block appears immediately at stage 0 (no visible grain).
4. Watch it. Over 30 seconds it should visibly advance through stages.
   PASS: ripeness is readable from the field without opening anything.
5. Sow the same seed 7 more times. On the 8th it should say the seed wore out
   and the item should disappear.
   PASS: exactly 8 plantings, no more, no fewer.
6. `/farm give fert`, then right-click a growing crop.
   PASS: remaining time halves, cost is small, balance drops by that cost only.
7. Right-click a ripe crop.
   PASS: message says harvested to farm storage; nothing enters your inventory.
8. `/farm board` — take a look at the order.
   PASS: shows what it wants, what it pays, how much you hold, seconds left.
9. Click Deliver while short.
   PASS: refuses, consumes nothing, takes no money.
10. Harvest enough, then Deliver.
    PASS: paid, storage debited by exactly the quantity.
11. Let an order expire without delivering.
    PASS: balance is completely unchanged. No fee of any kind.
12. `/farm sell` — sells all farm storage.
    PASS: balance increases by the total value of all crops in storage; storage is emptied.
13. Plant a crop, then `/stop` the server. Wait past its grow time. Restart.
    PASS: the crop is ripe on rejoin. Offline growth works because ripeness is
    derived from a timestamp, not from ticks.
14. Plant a crop, walk far away so the chunk unloads, wait, come back.
    PASS: correct stage on return.
15. Drag an item into an empty order-board slot, then close the board.
    PASS: the item is not lost — it is returned or the drag is refused.
16. Break the farmland underneath a growing crop.
    PASS: the crop is untracked, not left as a phantom record. Verify by
    restarting and confirming it does not reappear.
17. `/farm give wheat` with a completely full inventory.
    PASS: the item drops at your feet and the message says so, rather than
    claiming success.
18. Plant two different crops, harvest them both, then `/farm sell`.
    PASS: output shows the quantity for each ("Sold 5 × Wheat for $100"), in a
    stable order.
19. `/farm give wheat`, then plant a seed until it wears out.
    PASS: wheat gives exactly 8 plantings, the durability bar visibly moves,
    and on the 8th planting the seed disappears and the message confirms
    it wore out.
20. `/farm give carrot`, then plant a seed until it wears out.
    PASS: carrot gives exactly 7 plantings.
21. Switch to creative mode, get a seed via `/farm give wheat`, and plant it.
    Then get a fertiliser via `/farm give fert` and apply it to a growing crop.
    PASS: sowing *does* consume a planting even in creative (item count drops from
    8 to 7), confirming the asymmetry documented below. Fertilising *does*
    still spend balance (balance drops by the usual cost) -- only the
    fertiliser *item* is exempted from being consumed in creative, same
    asymmetry as the seed/planting count above.
22. Get a carrot seed via `/farm give carrot`, then right-click the air (aim at
    the sky, not a block).
    PASS: the seed is consumed as food (carrot hunger bar appears, item count
    drops). This is why carrot is a poor choice of seed material.

## Known deviations from the spec

- Persistence is a flat text file, not SQLite (§6). No JDBC driver is available
  and no downloads were permitted. `StateCodec` is the seam; swapping it is a
  one-class change.
- `StateCodec` exposes skipped/corrupt lines via a static `lastSkipped()` field.
  This is safe today because loading is single-threaded, but it would need to
  become non-static before any async save/load.
- `/farm give` is not op-gated: any player can mint themselves seeds and
  fertiliser. Acceptable for an internal throwaway slice, must be gated
  before any live deployment.
- `/farm setup` overwrites existing blocks without confirmation (op-gated, so
  contained).
- Creative mode still consumes a seed planting, unlike fertilising which
  correctly exempts it.
- A carrot seed is a real `Material.CARROT` and right-click-air is unhandled,
  so a player can eat their own seed.
- No mill, market GUI, merchant NPC, levels, plot purchase, combined orders or
  milled goods. Those are out of scope for the slice by decision.
- Only wheat and carrot. Both use `Ageable` blocks, so stage interpolation is
  proven; sugar cane and pumpkin remain the unproven cases flagged in the spec.

## Build notes

- `build.sh` compiles against **all 138 jars** under `lofts-dev/libraries`,
  not a curated subset. Paper's API jar alone is insufficient: `javac` must
  resolve the full overload set of every Bukkit type touched, which pulls in
  Adventure, bungeecord-chat and Guava. Naming dependency groups one at a time
  only relocated the next failure.
- `javac` is invoked with **`-encoding UTF-8`**. Windows defaults to the system
  codepage, and source files here contain a mix of `§` escapes and literal
  `§` bytes — without the flag, the literal bytes compile to mojibake silently.
