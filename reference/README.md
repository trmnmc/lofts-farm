# Reference builds

These are browser prototypes, not shipping code. They exist because the spec
(`docs/superpowers/specs/2026-08-19-lofts-farming-economy-design.md`) treats the
browser build as **the authority on behaviour and on every number**.

Open either file directly in a browser. No build step, no dependencies, no network.

## `lofts-farm.html` — the reference implementation

The farming economy as it should behave. Every constant the Minecraft port needs
lives in the `CROPS` table and the tuning functions near the top of the `<script>`
block: seed costs, grow times, raw values, seed durability, mill intervals and
tiers, fertiliser pricing, order generation and premiums, level thresholds, plot
and tool costs.

**Take the numbers from here, not from the live `config.yml`.** The v1 config's
values were rebalanced during this design pass; the two disagree on purpose.

Worth reading before porting:

- `signature()` / `sync()` / `paint()` — the repaint discipline described in spec
  §5.1. The DOM is rebuilt only when something structural changes; everything that
  moves continuously is animated by touching text and widths. The earlier version
  rebuilt everything four times a second and clicks landing mid-repaint were
  destroyed. The same trap exists with Minecraft inventory GUIs redrawn on a tick.
- `runMill()` — per-line grinding with reserves, so a machine cannot eat stock an
  accepted order needs.
- `fertPlan()` — a dry run that computes exactly what a bulk action will cost
  before the player commits to it.

## `lofts-playtest.html` — how the design was arrived at

A three-round A/B/C playtest of the old design against the new one. Kept as the
evidence trail behind the decisions in spec §2 and §12, not as something to build.

The finding that drove everything: with slow crops and a fast foreground loop,
dead air measured **0% across 180 seconds**, against 29–43% in the old build. That
is why grow times are not compressed in the port.
