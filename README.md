# Lofts — Farming & Economy v2

A redesign of the Lofts farming economy, and a standalone Paper 1.21.x test plugin
that implements it on a flat world. Not yet integrated with the live Lofts plugin.

## Start here

| | |
|---|---|
| **The spec** | [`docs/superpowers/specs/2026-08-19-lofts-farming-economy-design.md`](docs/superpowers/specs/2026-08-19-lofts-farming-economy-design.md) |
| **The build plan** | [`docs/superpowers/plans/2026-08-19-loftsfarm-vertical-slice.md`](docs/superpowers/plans/2026-08-19-loftsfarm-vertical-slice.md) |
| **The reference build** | [`reference/lofts-farm.html`](reference/lofts-farm.html) — open it in a browser and play it |
| **Plugin source** | [`loftsfarm/`](loftsfarm/) |

Play the reference build before reading the spec. The spec is a description of
that thing; ten minutes with it will save an hour of reading.

## What the design is

Three speeds, and the tension between them is the game.

**Crops are slow.** They are the supply constraint and they grow while the player
is logged off. That is the reason to come back.

**Machines are fast.** The mill can always consume more than the field produces.
It only runs while the player is present. That is the reason to stay.

**Fertiliser bridges the two.** It halves a crop's remaining time for a fee, and it
is the monetised item. The gap between what machines can eat and what the farm can
grow is not a balance bug to tune away — it is the product.

Two rules that are not up for re-litigation without new evidence:

- **The order board decides what is worth growing.** Without it, players optimise
  into whichever crop pays best per second and the other five become decoration.
- **Nothing ever subtracts from a balance.** A missed order costs the payout, never
  a penalty. Prices float but never below what a crop is worth growing. Every
  action is net-positive; what varies is how positive.

## Where the numbers live

In the reference build, not in the live `config.yml`. The two disagree on purpose —
v1's crop values were rebalanced during this pass, and two of its six crops were
strictly dominated (paying less per plot-day than a cheaper, faster crop the player
already had). Spec §3 lists the v2 tables; `reference/lofts-farm.html` is the
authority if the two ever drift.

## Building the test plugin

```bash
cd loftsfarm && ./build.sh
```

`build.sh` expects the Paper API jar and server libraries at paths currently
hardcoded to a local `lofts-dev` checkout — adjust `LIBS` at the top of the script
for your machine. See [`loftsfarm/ACCEPTANCE.md`](loftsfarm/ACCEPTANCE.md) for what
"done" means and [`loftsfarm/KNOWN-ISSUES.md`](loftsfarm/KNOWN-ISSUES.md) for what
is already known to be wrong.

## Before this touches the live server

Spec §10 lists the issues carried in from the existing build. The one that is not a
design matter: **the live `config.yml` contains a Tebex `secret` and `public-token`
in plaintext alongside a MariaDB connection string, and the review dashboard binds
to `0.0.0.0`.** Rotate the Tebex secret and move both out of committed config before
that folder is shared with anyone.
