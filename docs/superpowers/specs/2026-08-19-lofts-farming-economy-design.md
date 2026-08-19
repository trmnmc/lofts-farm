# Lofts — Farming & Economy Redesign

**Status:** design approved, ready for implementation planning
**Date:** 2026-08-19
**Deliverable:** a standalone flat-world test plugin that ports the browser reference build into Paper 1.21.x. Not yet integrated with the existing Lofts plugin.

**Reference implementation:** the browser build is the authority on behaviour **and on every number**. Where this document and the browser build disagree, the browser build wins and this document is wrong. Do not re-derive constants from the live `config.yml`; take them from the browser source.

## 0. This is Farm Economy v2, not a replacement

The existing farming economy stays live and playable. v2 is a **parallel economy** that runs alongside it and is the only one that receives further development.

That decision has hard consequences the implementation must respect:

- **v1 is frozen, not migrated.** No player data moves. Nobody's v1 balance, pantry, contracts, or progression is touched, converted, or read.
- **Separate balances, permanently.** v2 money is its own currency in its own table. A player's v1 and v2 balances never interact — no exchange rate, no arbitrage between two economies with different faucets.
- **Separate stock.** v2 crops, products, seeds and orders are v2-only. v1's pantry is invisible to v2 and vice versa.
- **No shared config keys.** v2 reads its own config file. It must not inherit or override anything under the existing `farm:`, `bakery:`, `market:`, `refiner:` or `apiary:` trees.
- **Both can be enabled at once** on the same server without either being aware of the other.

Because the economies are isolated, the balance defects catalogued in §10 do not need fixing before v2 ships — they belong to v1, which is now frozen. They are listed because they must not be *reproduced* in v2.

### 0.1 Scope boundary

**How v1 and v2 coexist for players on the live network is out of scope for this document.** Whether v2 lives in a separate world, behind a per-player toggle, on its own server, or is offered only to new players is a live-network deployment decision, not a farming-economy design decision.

This spec covers the new farm economy and nothing else. It is written so that any of those deployment models remains possible: v2 is self-contained, reads only its own config and tables, and makes no assumption about where it is mounted or who can reach it.

---

## 1. Why this exists

The farming and economy half of Lofts is boring. That was the owner's assessment, and a three-round A/B/C playtest confirmed the mechanism behind it.

The shipped build has no decisions in it. A player buys a plant from a GUI, waits, and the Auto Merchant sells the harvest for them every 30 seconds whether or not they are logged in. The Contract Manager auto-delivers every 10 seconds, unattended. Hired farmers work offline, bounded only by prepaid wages. The player has been engineered out of their own game loop.

Measured, from the playtest:

| Measure | Shipped build | Redesign |
| --- | --- | --- |
| Time with no action available | 29% of turns | **0%** of 180s |
| Actions taken | 13 | 63 (21/min) |
| Median time per decision | 3.2s | 0.6s |
| Sowings that were just the priciest affordable crop | **100%** | 16% |
| Profit | **−$680** | +$738 |

Two findings are load-bearing:

**Slow crops are not the problem.** Round 3 kept grow times long and still produced zero dead air, because a fast foreground activity sat in front of them. Grow times must **not** be compressed — doing so multiplies income for no gain in engagement.

**The current ladder is degenerate and losing.** 100% of shipped-build sowings were simply the most expensive crop affordable, and the round still lost money — because expensive crops do not mature inside a play session. Players pay to climb rungs that make them poorer.

---

## 2. Design principles

These are the non-negotiables. Every decision below follows from them.

**P1 — Three speeds.** Crops are slow and are the supply constraint. Machines are fast and can consume more than the farm can supply. Fertiliser is the toll between the two halves, and it is the monetised item.

**P2 — The gap is the product.** Machine idle time is not a defect to be tuned away. It is the pressure fertiliser relieves. It must be *deliberately sized as a config constant*, and it must be *visible at the moment it bites*, or it produces frustration without producing demand.

**P3 — Crops grow offline. Machines do not.** Crops finish while the player is logged off; that is the reason to come back. The mill only turns while its owner is online; that is the reason to stay. This asymmetry is the retention loop and must be preserved exactly.

**P4 — The order board decides what is worth growing.** Without orders, players optimise into a monoculture of whichever crop pays best per second — observed as Wheat ×11 in the playtest. Orders name a specific crop, so demand drives variety. Contracts are the spine, not a side feature.

**P5 — Nothing ever reduces a player's balance.** A missed order costs the payout, never a fee. Prices float but never below what a crop is worth growing. Fertiliser is priced off crop value, so applying it is always net-positive. Tension comes from racing for a larger gain, never from loss.

**P6 — Anything with a clock on it is ambient; only things you browse get a menu.** The browser build works because four order clocks, six crop timers and a draining hopper are visible at once. Modal GUIs would make the juggle impossible, not merely worse.

---

## 3. Content

### 3.1 Crops

| id | Name | Block material | Seed cost | Grow (s) | Raw value | Plantings/seed | Level |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `wheat` | Wheat | `WHEAT` | $3 | 30 | $20 | 8 | 1 |
| `carrot` | Carrot | `CARROTS` | $5 | 45 | $30 | 7 | 1 |
| `pumpkin` | Pumpkin | `PUMPKIN_STEM` | $45 | 90 | $190 | 5 | 3 |
| `cane` | Sugar Cane | `SUGAR_CANE` | $130 | 130 | $240 | 4 | 5 |
| `wart` | Nether Wart | `NETHER_WART` | $320 | 180 | $520 | 3 | 8 |
| `pitcher` | Pitcher | `PITCHER_CROP` | $1120 | 260 | $1600 | 2 | 12 |

All values come from the browser reference build. Seed costs and raw values happen to match the live v1 config; grow times are new to v2.

**Accepted known imbalance.** On raw profit-per-second these come out: wheat $0.57, carrot $0.56, pumpkin $1.61, sugar cane $0.85, nether wart $1.11, pitcher $1.85. Pumpkin therefore beats both sugar cane and nether wart, so those two rungs are not economically optimal on their own.

This is accepted rather than fixed, on the grounds that **nobody in v2 farms for profit-per-second — they farm what the board is asking for** (P4). A dominated crop still gets planted because an order names it and pays a premium for it. The risk being accepted is that a player who ignores the board entirely will find two crops pointless. Revisit if playtesting shows players routinely ignoring orders.

### 3.2 Products

The mill consumes **2 raw → 1 product**. Product spot price = raw spot × **2.7** (a 35% margin on the pair).

| Crop | Product | Ticker (raw / product) |
| --- | --- | --- |
| Wheat | Flour | ADM / K |
| Carrot | Carrot Mash | KR / CPB |
| Pumpkin | Pumpkin Purée | SFM / HRL |
| Sugar Cane | Sugar | KO / PEP |
| Nether Wart | Nether Spice | MRNA / MKC |
| Pitcher | Pitcher Extract | SMG / IFF |

Product names and tickers already exist in the live config's `market.tickers` block. Reuse them.

### 3.3 Starting state

$250 cash, 4 plots, 3 wheat seeds, no mill, level 1.

---

## 4. Systems

### 4.1 Growth engine

**Vanilla crop growth must be cancelled entirely.** Vanilla advances crops on random block ticks gated by light level, hydration and chunk loading. It cannot hit a 30-second target and it does not run predictably. Cancel `BlockGrowEvent` for all tracked plants.

Each planted crop stores an absolute `ready_at` epoch-millis timestamp. Ripeness is always derived by comparing `ready_at` against `System.currentTimeMillis()` — never by counting ticks.

Scheduling rules:

- Schedule **exactly one** task per plant, firing at `ready_at`, which sets the block to its final state.
- Intermediate visual stages are interpolated **only for chunks that currently have a player within view distance**. When no player is nearby, skip visual updates entirely; the block is corrected on chunk load or when a player approaches.
- **Never poll all plots.** At 50+ lofts a global sweep is the entire performance budget. Growth is event-scheduled per plant.

**Offline growth requires no catch-up simulation.** Because `ready_at` is wall-clock, a crop finishes whether or not the chunk is loaded or the owner is online. On chunk load, compute the correct stage from the timestamp and set the block once.

#### Known translation problem: stage counts differ per crop

Growth stage is the diegetic progress bar — a player reads ripeness by looking at the field. But the six crops do not share a stage model:

| Crop | Visual model | Stages |
| --- | --- | --- |
| Wheat, Carrots | `Ageable` | 0–7 |
| Pitcher Crop | `Ageable` | 0–4 |
| Nether Wart | `Ageable` | 0–3 |
| Pumpkin Stem | `Ageable` 0–7, then spawns a separate fruit block | 8 + fruit |
| Sugar Cane | grows by *height*, not age | 1–3 blocks |

Implement a per-crop `stageCount` and map `elapsed / grow_time` onto it.

Sugar cane and pumpkin get explicit handling:

- **Sugar cane** — place successive blocks upward as it advances; height *is* the progress bar.
- **Pumpkin** — suppress natural fruit spawning entirely, advance the stem through its 0–7 ages, and place the fruit block at `ready_at`.

If either exceeds the test build's time budget, fall back to a two-state representation (planted / ripe) for that crop **and record it as a known shortfall in the build notes** — do not fake intermediate stages, and do not silently ship a crop whose ripeness cannot be read from the field. Wheat, carrots, nether wart and pitcher must all have working stage interpolation; they are the proof that the diegetic timer works.

### 4.2 Seeds and durability

Seeds are **real inventory items**, bought from the merchant. Planting is no longer a purchase transaction fired at placement time.

Each seed carries N plantings (see §3.1). Store the remaining count in `PersistentDataContainer`, and drive the item's **damage value** off it so the player sees a familiar vanilla wearing-down bar rather than needing to read lore. Also show the remaining count in lore as a number.

Consequences the implementation must handle:

- **The point of sale moves** from the farmland to the merchant. The economy audit trail must follow it, or seed spend vanishes from the dashboard.
- **Seeds become tradeable** between players, and usable as order rewards or drops.
- **Sink sizing:** plantings-per-seed behaves as `1/(1-r)` in recovery-rate terms. 8 plantings ≈ a 87.5% recovery rate, i.e. a very small sink; 2 plantings ≈ 50%. This is intentional — cheap for beginners, expensive for the wealthy. Wheat works out to ~$0.38 a planting; pitcher to $560.
- **Misplant protection is required on the top two tiers.** A $1,120 pitcher seed placed by a misclick is a real loss. `wart` and `pitcher` require a **sneak-click** to plant; a normal right-click does nothing and sends an action-bar hint. No confirmation GUI — that would put a menu back into the fast loop.

### 4.3 Planting

Vanilla interaction, unchanged from a player's perspective: hold a seed, right-click farmland. Intercept `PlayerInteractEvent`, decrement seed durability, place the tracked crop, and register its `ready_at`.

This replaces GUI planting. Measured at 1.4 clicks per plant against 2.2 for the menu — and it removes a menu from the fast half of the loop, which is where friction hurts most.

### 4.3a Harvested goods live in virtual farm storage

Harvesting does **not** put items in the player's inventory. Goods go to a per-player virtual store (`stock_raw`, `stock_prod`), viewable in the market GUI alongside spot prices.

This matches the live server's existing behaviour — `bakery.defaultHarvestMode: COLLECT` already collects harvests rather than dropping them — so it will feel unchanged to current players. It also removes inventory management from the fast loop, avoids full-inventory failure states mid-scramble, and means the mill can draw input without the player shuttling items into it.

Consequences:

- **Order delivery is a GUI click, not a physical hand-off.** The board's Deliver button debits `stock_raw` or `stock_prod` directly. There is no delivery chest and no carrying goods across the farm.
- **Selling is likewise a GUI click** at the market stall, debiting virtual stock.
- Raw goods are therefore never tradeable between players in this build. Seeds are the only tradeable item. Player-to-player trade in crops is out of scope here and would need a design pass of its own.

### 4.4 Fertiliser

An item. Right-click a growing crop to **halve its remaining time**.

Cost: `max(4, round(rawValue × 0.26 × (remaining / total)))` — priced off what the crop is worth and scaled by how much time is left, so it is always net-positive to use (P5).

**Fertiliser sells against order deadlines, not machine starvation.** This was the playtest's clearest surprise: 23 purchases, first at 59 seconds in, while the mill went entirely unused. The pitch is not "your machine is idle" — it is *"the order is due and the crop isn't ready."* Surface the prompt accordingly: offer it where a player is looking at a deadline they are about to miss.

### 4.5 The mill

A **physical block**. Lit and emitting smoke while grinding; dark and silent when starved, so idleness is legible from across the farm.

- Consumes 2 raw → 1 product on a timer. Cycle: `max(1400, 5200 − (tier−1) × 640)` ms.
- Runs **one recipe at a time**. Choosing what it eats is the decision.
- Mill tier gates which crops it can process: crop tier ≤ mill tier.
- **Must be purchased.** First mill $2,000; upgrades `1600 × 2^tier` → $3,200, $6,400, $12,800, $25,600, $51,200. The doubling matches `tiers.upgradeCostMultiplier: 2.0` already used in the live config.
- Until a mill is owned, the board posts **raw orders only**.
- **Progress advances only while the owner is online** (P3). This is the single explicit place where the slow and fast loops are treated differently in code.

At $2,000 against early income of roughly $160/min, the first mill is about 13 minutes of play — a real milestone, not a formality.

### 4.6 The order board

**The spine of the game.** A physical board; a sign on it carries a live one-liner (order count, soonest deadline). Clicking opens the full list.

**An order is a list of lines.** A single-good order is simply a one-line order — there is no separate code path for the two. Each line is `{crop, kind, qty}` where `kind` is `raw` or `milled`.

- Up to **4 open orders** at any time. A new one is posted every 12–26s while under 4.
- Every line names **one specific crop** (P4).
- **Raw lines** want crops from the field: qty `max(3, 7 − tier)`, unit price `spot × 1.15`.
- **Milled lines** want finished goods: qty `max(2, 4 − floor(tier/2))`, unit price `productPrice × 1.22`. Only offered if the owner's mill tier covers that crop; 45% of eligible lines.

#### Combined orders

> **Provisional.** The unlock level, generation chance and premium below are the browser build's current values and have not yet been validated by play. Build them as config constants and expect them to move. See §12.

At **level 7** combined orders unlock: multi-line orders demanding two or more different goods against a single deadline, with a **38%** chance of being generated once eligible.

- Payout multiplier: **1.4×** the summed line value, against **1.16×** for a single-line order. The premium is the reward for the juggle.
- Deadline: `slowest_line_grow_time × 1.5 + 85s`, against `× 1.25 + 45s` for single-line orders. Combined orders must clear the slowest crop they contain, not the average.
- The order card shows **a per-line tally** (`held / required`) with a check when a line is satisfied, so a player can see at a glance which good is holding the delivery up.
- All-or-nothing: an order pays only when *every* line is satisfied. Partial delivery is not possible.

Combined orders are the escalation that turns the loop into the "scrambling and juggling" the owner named as the target experience. Level 7 is chosen so they arrive after the mill and after pumpkin, when a player has enough crops in play for a multi-good order to be answerable.

#### Common to all orders

- Fuses must always clear at least one full grow cycle of the slowest good demanded — otherwise accepting is a trap that only takes money.
- Player takes an order, then **delivers manually** once holding enough. Manual delivery is an action and it is the one that pays.
- Auto-delivers as a safety net if the player is holding enough when the deadline arrives.
- **Missing an order costs nothing** (P5). No fee, no penalty. The order expires and the payout is forfeited.
- Each order shows what the goods would fetch on the open market, so taking it is a real comparison rather than a blind accept.

### 4.7 Market

Prices reprice every **3 seconds**.

- Random walk: `price × (1 + uniform(−vol, +vol))`, clamped to `[0.6×, 1.85×]` of the crop's base raw value.
- Volatility by crop: wheat .05, carrot .06, pumpkin .09, cane .11, wart .15, pitcher .20.
- 22% chance per tick of a shock event on one random crop: ±18–35%, announced in chat.
- Selling moves the price: raw `× (1 − 0.012 × n)`, product `× (1 − 0.018 × n)`, floored at 0.6× base.

The 0.6× floor is what guarantees P5 — a sale is always profitable, just sometimes less so.

### 4.7a Currency

v2 money is **its own currency in its own table** (`players.cash`), permanently — see §0. It does not touch Vault, EssentialsX economy, or v1's balances, and there is no exchange between v1 and v2 money.

Keep every balance read and write behind a single `Economy` interface anyway. Not for a future Vault swap — v2 is deliberately not going there — but so the currency has exactly one authority, which is what makes the audit trail trustworthy and stops a second code path quietly minting money.

### 4.8 Merchant, levels, plots

- **Merchant:** a Citizens NPC. Opens a GUI listing seeds with price, grow time, plantings-per-seed and current spot value. Stocks only crops the player's level has unlocked.
- **Levels:** XP per harvest = `max(1, round(rawValue / 8))`. Level threshold = `round(55 × level^1.75)`. Level gates which seeds the merchant stocks.
- **Plots:** start 4, maximum 12. Cost `400 × 1.75^(n − 4)`.

Progression is therefore **skill-gated for knowledge and infrastructure-gated for capability**: level decides what you may buy, mill tier decides what you may process.

---

## 5. Interface mapping

Diegetic objects with GUI interiors, and exactly one persistent overlay.

| Concern | Presentation |
| --- | --- |
| Crop ripeness | The crop block's own growth stage |
| Mill running / starved | Block lit + smoke, or dark + silent |
| Order count, next deadline | Sign on the physical board |
| Cash, soonest deadline | **Action bar** — the only persistent overlay |
| Order detail, take, deliver | GUI, opened from the board |
| Mill recipe, hopper contents | GUI, opened from the mill block |
| Seed shop | GUI, opened from the merchant NPC |
| Spot prices and selling | GUI, opened from the market stall |
| Transaction history | Chat |

No scoreboard. One action-bar line is the entire HUD.

### 5.1 GUI repaint rule — carried over from the browser build

The browser build had a real defect worth not repeating: it rebuilt every panel four times a second, so any click landing mid-repaint was destroyed along with the button it hit. Buttons felt unreliable because they were.

**Redraw inventory GUI slots on state change, never on a tick schedule.** Where a value must animate (a countdown), update only that slot's display name — never rebuild the inventory. The same failure mode applies to Minecraft GUIs and will read to players as lag.

---

## 6. Data model

SQLite, alongside the existing `lofts.db` pattern.

```
plots      (id, owner_uuid, world, x, y, z, crop_id, planted_at, ready_at)
seeds      -- carried as items; durability lives in item PDC, not the DB
mills      (id, owner_uuid, world, x, y, z, tier, recipe_id, progress_ms, last_online_tick)
stock_raw  (owner_uuid, crop_id, qty)
stock_prod (owner_uuid, crop_id, qty)
orders     (id, owner_uuid, is_combo, pay, due_at, state)
order_lines(order_id, idx, crop_id, kind, qty, unit_price)
players    (owner_uuid, cash, xp, level, plots_owned)
prices     (crop_id, price, updated_at)          -- current snapshot
price_hist (crop_id, price, at)                  -- ring buffer, ~30 points, for trend display
```

All timestamps are absolute epoch millis. `mills.last_online_tick` exists solely to enforce P3.

---

## 7. Performance requirements

- Growth is **event-scheduled per plant**. No global polling of plots under any circumstance.
- Visual stage interpolation runs only for chunks with a player in view distance.
- Price repricing is a single server-wide task every 3s, not per-player.
- Order generation is per-player but cheap; cap the board at 4.
- GUI redraws are state-driven, never scheduled (§5.1).

---

## 8. Test world

Superflat. One farm area at spawn containing: 12 farmland blocks (4 active, 8 purchasable), a mill site, an order board, a merchant NPC, a market stall. Built as a schematic and pasted with FAWE.

No world generation work, no claim system, no permissions beyond op/non-op.

---

## 9. Out of scope for this build

Explicitly not included, to keep the test throwaway:

- Any integration with the existing Lofts plugin — no lofts, no rooms, no housing tiers, no extensions, no rocketry.
- Tebex, credits, PlayerPoints, prestige/rebirth.
- Land claims, permissions, friend/trust systems.
- NPC farmhands, refiners, beekeepers.
- The "crop you can't grow alone" cooperative mechanic (see §11).
- Custom textures or resource packs. Vanilla materials only.

---

## 10. Issues in the existing build to resolve before or during integration

Carried from `plugins/Lofts/config.yml`. None of these block the test build; all of them block integration.

- **Auto Merchant** sells a slot's entire pantry every 30s regardless of whether the owner is online. It is the primary suspect for runaway wealth and it removes the fast loop from the player. Must be capped or removed.
- **Contract Manager** auto-delivers any order whose basket is in storage, every 10s, unattended. Directly contradicts P4 — the delivery action is the one that pays.
- **Offline farmers** are bounded only by prepaid wages (`offlineFarmerCatchupHours: 0` = unbounded). Owner's decision: farmhands should *work only on crops already sown* — they harvest what is ready, they never plant. That makes the return state correct: full storage and an empty field, so there is stock to trade and a field to refill.
- **Order multipliers compound up to 7×** (type × rush × streak × prestige × daily × golden). `maxMarketMultiplier` and `autoSellMaxPayoutPerTick` both ship uncapped.
- **`tiers.maxTier: 1`** — the entire configured tier 1–10 ladder is inert; only tier-1 schematics exist.
- **Dominated crops.** At live config values, profit per plot-day is: wheat $17, carrot $25, pumpkin $73, sugar cane $37, nether wart $50, pitcher $96. **Sugar cane and nether wart are strictly worse than pumpkin** — cheaper and faster beats both. Those rungs cost money to reach and make the player poorer. Any new ladder must be checked for domination before shipping.
- **SECURITY:** a live Tebex `secret` and `public-token` sit in plaintext in `config.yml`, alongside a MariaDB credit-ledger connection string. The review dashboard binds to `0.0.0.0`. Rotate the Tebex secret and move both out of the committed config before this folder is handed to anyone.

---

## 11. Deferred design work

**"A crop you can't grow alone."** The owner's stated intent for the top-tier crop. It cannot be prototyped in a single-player browser build and is not in this test scope, but it is the most interesting unbuilt idea in the design and wants its own pass. Candidate shapes: it requires an input only another player's farm produces; or it is a shared plot several players contribute to; or it needs a co-op order that no single farm can fill alone.

**Supply gap sizing.** Observed at 34% mill starvation in the playtest, with fertiliser purchased 23 times. That is the starting number, not the answer. It must become a tunable config constant (P2) rather than an emergent accident of crop timings.

**Free-player route past the bottleneck.** Owner's answer: purchasable with in-game currency, expensively. Needs concrete pricing. This is not a fairness question — a player-driven market needs a population, and non-payers *are* the population generating supply.

**Session shape.** Owner's target is "brief check-ins, several times a day," with the best ten minutes being *"scrambling and juggling to fulfil contracts and crops and machines."* Those are compatible only if the check-in *is* the scramble: log in to a finished field, ten frantic minutes, log off. Grow times and order fuses should be tuned against that shape.

---

## 12. Provisional — decide by playing, not by arguing

These are implemented as specified but are **not settled**. Each is a config constant or a swappable component, and each has a question the browser build can answer before the Minecraft build commits.

| Item | Current value | What play needs to tell us |
| --- | --- | --- |
| Combined orders — unlock level | 7 | Do they arrive when you have enough crops in play to answer them, or too late to matter? |
| Combined orders — generation chance | 38% | Does the board feel like it's mostly combos, mostly singles, or right? |
| Combined orders — premium | 1.4× vs 1.16× | All-or-nothing across several goods is a real gamble. Is 1.4× enough to make taking one the obvious play? |
| **Virtual storage vs real items** | Virtual | Does never touching your inventory feel smooth, or does it feel like the crops aren't really yours? This one is expensive to change later — it decides delivery, selling, trade, and the mill's input path. |
| **Action-bar-only HUD** | Cash + next deadline | Is one line enough during a scramble, or do you need level, mill state and order count visible too? |
| Offline farmhands | Cut from the test | Does the return state work without them — finished field, empty plots, stock waiting — or does the loop need someone harvesting while you're away? |
| Supply gap size | 34% mill idle, observed | Is that pressure or a wall? It should become an explicit config constant either way (P2). |

Locked and not up for re-litigation without new evidence: three speeds (P1–P3), orders as the spine (P4), never subtracting from a balance (P5), diegetic clocks (P6), seed durability, planting from the hand, mill purchase at $2,000 with doubling upgrades, and all crop/price/market constants taken from the browser build.

## 13. Acceptance criteria

The test build is done when:

1. A crop planted from a seed item ripens in exactly its configured time, and does so while the chunk is unloaded and the player offline.
2. Crop ripeness is readable from the field without opening anything.
3. A seed visibly wears down and is consumed after exactly N plantings.
4. The mill grinds only while its owner is online, and visibly idles when starved.
5. The board holds up to 4 live orders, each naming a specific crop, each with a fuse longer than one grow cycle.
6. Taking and manually delivering an order pays out; missing one costs nothing at all.
7. Prices move every 3 seconds and never fall below 0.6× base.
8. Fertiliser halves remaining grow time and is always net-positive.
9. No global plot polling appears anywhere in the codebase.
10. No GUI is redrawn on a tick schedule.
11. A player can go from $250 and 3 wheat seeds to owning a mill without instruction.
12. Wheat, carrots, nether wart and pitcher all show working intermediate growth stages.
13. All balance access goes through a single `Economy` interface; nothing calls Vault.
14. Combined orders appear at level 7, pay 1.4×, show a per-line tally, and pay only when every line is satisfied.
15. v2 reads no v1 config keys and writes no v1 tables. Enabling v2 changes nothing about v1.
16. Every constant in the build traces to the browser reference source, not to `config.yml`.
