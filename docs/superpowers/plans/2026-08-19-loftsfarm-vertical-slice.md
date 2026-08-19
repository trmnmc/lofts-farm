# LoftsFarm v2 Vertical Slice Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A loadable Paper 1.21.10 plugin that proves the risky half of the Farm Economy v2 translation — wall-clock crop growth with visible stages, planting from the hand with wearing seeds, virtual storage, fertiliser, and one deliverable order.

**Architecture:** Two layers with a hard boundary. A **domain layer** with zero Bukkit imports holds every rule and number (growth maths, seed wear, wallet, storage, order fulfilment, fertiliser pricing, persistence model) and is unit-tested by a dependency-free harness on plain `java`. A thin **adapter layer** wires that domain to Bukkit events, items, schedulers and GUIs, and is verified in-server against an acceptance script. The boundary exists because no mocking framework is available — anything untestable must be trivial.

**Tech Stack:** Java 21, Paper API 1.21.10-R0.1-SNAPSHOT, `javac` + `jar` directly (no Maven/Gradle), JSON flat-file persistence, dependency-free test harness.

## Global Constraints

Copied from `docs/superpowers/specs/2026-08-19-lofts-farming-economy-design.md`. Every task's requirements implicitly include these.

- **Paper 1.21.10, Java 21.** API jar: `C:/Users/truman/OneDrive/Documents/lofts-dev/libraries/io/papermc/paper/paper-api/1.21.10-R0.1-SNAPSHOT/paper-api-1.21.10-R0.1-SNAPSHOT.jar`
- **No Maven or Gradle.** Compile with `javac`, package with `"C:/Program Files/Java/jdk-21/bin/jar.exe"`. No network downloads.
- **Cancel vanilla crop growth.** `BlockGrowEvent` is cancelled for every tracked plant. Vanilla growth must never advance a tracked crop.
- **One scheduled task per plant. Never poll all plots.** A global sweep over plots is forbidden anywhere in the codebase.
- **Wall-clock timestamps only.** Ripeness is derived by comparing `ready_at` epoch millis against `System.currentTimeMillis()`, never by counting ticks. This is what makes offline growth work with no catch-up simulation.
- **Crops grow offline. The mill does not.** (No mill in this slice — do not add one.)
- **GUI redraws happen on state change only, never on a tick schedule.** Where a value must animate, update that slot's display name; never rebuild the inventory.
- **Nothing ever reduces a player's balance.** A missed order costs the payout, never a fee. There is no code path that debits for failure.
- **All numbers come from the browser reference build**, not from the live `config.yml`. Slice values: wheat — seed $3, grow 30s, raw value $20, 8 plantings. carrot — seed $5, grow 45s, raw value $30, 7 plantings. Starting cash $250.
- **v2 touches no v1 data.** No reads or writes to `plugins/Lofts/lofts.db`, no v1 config keys, no Vault.
- **Package root:** `place.games.loftsfarm`
- **Project root:** `C:/Users/truman/OneDrive/Documents/Minecraft-Server/loftsfarm`
- **RULED DEVIATION — persistence.** Spec §6 specifies SQLite. This slice uses a flat text file behind the `StateCodec` seam, because no JDBC driver is present and no downloads are permitted. **The plan governs over spec §6 here** — this is a decided trade-off, not an oversight, and it is not a spec-compliance defect. Reviewers should not flag it. Swapping to SQLite later replaces `StateCodec` only.
- **Reviewers:** this plan's code blocks are the requirement. Where a task's code omits a system named in the spec but listed under §9 "Out of scope" or in the slice scope (mill, market GUI, merchant, levels, plots, combined orders, milled goods, the four upper crops, sugar cane and pumpkin stage handling), its absence is intended.

---

### Task 1: Build harness and a plugin that loads

**Files:**
- Create: `loftsfarm/build.sh`
- Create: `loftsfarm/run-tests.sh`
- Create: `loftsfarm/src/main/resources/plugin.yml`
- Create: `loftsfarm/src/main/java/place/games/loftsfarm/LoftsFarmPlugin.java`
- Create: `loftsfarm/src/test/java/place/games/loftsfarm/test/Check.java`
- Create: `loftsfarm/src/test/java/place/games/loftsfarm/test/TestMain.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `Check.eq(String label, Object actual, Object expected)`, `Check.isTrue(String label, boolean cond)`, `Check.throwsUp(String label, Runnable r)`, `Check.report()` returning `int` exit code. `TestMain.main(String[])` runs every suite. Later tasks add a line to `TestMain` calling their own suite's `run()`.

- [ ] **Step 1: Write the test harness**

Create `loftsfarm/src/test/java/place/games/loftsfarm/test/Check.java`:

```java
package place.games.loftsfarm.test;

import java.util.ArrayList;
import java.util.List;

/** Dependency-free assertions. No JUnit is available in this environment. */
public final class Check {
    private static final List<String> FAILURES = new ArrayList<>();
    private static int passed = 0;

    private Check() {}

    public static void eq(String label, Object actual, Object expected) {
        if (expected == null ? actual == null : expected.equals(actual)) {
            passed++;
        } else {
            FAILURES.add(label + ": expected <" + expected + "> but was <" + actual + ">");
        }
    }

    public static void isTrue(String label, boolean cond) {
        if (cond) passed++; else FAILURES.add(label + ": expected true");
    }

    public static void isFalse(String label, boolean cond) {
        if (!cond) passed++; else FAILURES.add(label + ": expected false");
    }

    public static void throwsUp(String label, Runnable r) {
        try {
            r.run();
            FAILURES.add(label + ": expected an exception, none thrown");
        } catch (RuntimeException expected) {
            passed++;
        }
    }

    public static int report() {
        System.out.println();
        System.out.println(passed + " passed, " + FAILURES.size() + " failed");
        for (String f : FAILURES) System.out.println("  FAIL  " + f);
        return FAILURES.isEmpty() ? 0 : 1;
    }
}
```

- [ ] **Step 2: Write the test entry point**

Create `loftsfarm/src/test/java/place/games/loftsfarm/test/TestMain.java`:

```java
package place.games.loftsfarm.test;

public final class TestMain {
    public static void main(String[] args) {
        System.out.println("LoftsFarm domain tests");
        // Later tasks append their suites here.
        System.exit(Check.report());
    }
}
```

- [ ] **Step 3: Write the test runner script**

Create `loftsfarm/run-tests.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

OUT=build/test-classes
rm -rf "$OUT"; mkdir -p "$OUT"

# Domain layer has no Bukkit dependency, so tests need no classpath beyond itself.
find src/main/java src/test/java -name '*.java' \
  ! -path '*/bukkit/*' \
  -print0 | xargs -0 javac -d "$OUT" -Xlint:all

java -cp "$OUT" place.games.loftsfarm.test.TestMain
```

Note the `! -path '*/bukkit/*'` exclusion: everything under `place/games/loftsfarm/bukkit/` imports Paper and is therefore excluded from the test compile. That exclusion is what enforces the domain/adapter boundary — if a domain class imports Bukkit, this script stops compiling.

- [ ] **Step 4: Run the tests to verify the harness works**

Run: `bash loftsfarm/run-tests.sh`
Expected: PASS — prints `0 passed, 0 failed` and exits 0.

- [ ] **Step 5: Write plugin.yml**

Create `loftsfarm/src/main/resources/plugin.yml`:

```yaml
name: LoftsFarm
version: 0.1.0-slice
main: place.games.loftsfarm.bukkit.LoftsFarmPlugin
api-version: '1.21'
description: Farm Economy v2 vertical slice. Isolated from Lofts v1.
authors: [games.place]
commands:
  farm:
    description: LoftsFarm slice commands
    usage: /farm <help|give|balance|sell|board|setup>
```

- [ ] **Step 6: Write the plugin main class**

Create `loftsfarm/src/main/java/place/games/loftsfarm/bukkit/LoftsFarmPlugin.java`:

```java
package place.games.loftsfarm.bukkit;

import org.bukkit.plugin.java.JavaPlugin;

public final class LoftsFarmPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        getLogger().info("LoftsFarm v2 slice enabled. v1 economy untouched.");
    }

    @Override
    public void onDisable() {
        getLogger().info("LoftsFarm v2 slice disabled.");
    }
}
```

- [ ] **Step 7: Write the build script**

Create `loftsfarm/build.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

API="C:/Users/truman/OneDrive/Documents/lofts-dev/libraries/io/papermc/paper/paper-api/1.21.10-R0.1-SNAPSHOT/paper-api-1.21.10-R0.1-SNAPSHOT.jar"
JAR="C:/Program Files/Java/jdk-21/bin/jar.exe"
OUT=build/classes
JARFILE=build/LoftsFarm.jar

[ -f "$API" ] || { echo "Paper API jar not found at $API"; exit 1; }

rm -rf "$OUT"; mkdir -p "$OUT" build
find src/main/java -name '*.java' -print0 | xargs -0 javac -cp "$API" -d "$OUT" -Xlint:all
cp -r src/main/resources/* "$OUT"/
"$JAR" --create --file "$JARFILE" -C "$OUT" .

echo "Built $JARFILE"
```

- [ ] **Step 8: Build and verify the jar is well-formed**

Run:
```bash
bash loftsfarm/build.sh && unzip -l loftsfarm/build/LoftsFarm.jar | grep -E 'plugin.yml|LoftsFarmPlugin.class'
```
Expected: both `plugin.yml` and `place/games/loftsfarm/bukkit/LoftsFarmPlugin.class` are listed.

- [ ] **Step 9: Commit**

```bash
git add loftsfarm/build.sh loftsfarm/run-tests.sh loftsfarm/src
git commit -m "feat(loftsfarm): build harness, test harness, loadable plugin skeleton"
```

---

### Task 2: Crop catalogue and the growth clock

**Files:**
- Create: `loftsfarm/src/main/java/place/games/loftsfarm/domain/Crop.java`
- Create: `loftsfarm/src/main/java/place/games/loftsfarm/domain/GrowthClock.java`
- Create: `loftsfarm/src/test/java/place/games/loftsfarm/test/GrowthClockTest.java`
- Modify: `loftsfarm/src/test/java/place/games/loftsfarm/test/TestMain.java`

**Interfaces:**
- Consumes: `Check` from Task 1.
- Produces:
  - `enum Crop` with constants `WHEAT`, `CARROT` and accessors `id():String`, `displayName():String`, `seedCost():long`, `growSeconds():int`, `rawValue():long`, `plantings():int`, `stageCount():int`, plus `static Crop byId(String)` returning `null` when unknown.
  - `record GrowthClock(long plantedAt, long readyAt)` with `static GrowthClock start(Crop c, long nowMs)`, `boolean ripe(long nowMs)`, `long remainingMs(long nowMs)`, `int stage(Crop crop, long nowMs)`, `GrowthClock halved(long nowMs)`. Note `stage` takes the crop — a bare clock cannot know its own `stageCount`.

- [ ] **Step 1: Write the failing test**

Create `loftsfarm/src/test/java/place/games/loftsfarm/test/GrowthClockTest.java`:

```java
package place.games.loftsfarm.test;

import place.games.loftsfarm.domain.Crop;
import place.games.loftsfarm.domain.GrowthClock;

public final class GrowthClockTest {
    public static void run() {
        long t0 = 1_000_000L;

        Check.eq("wheat grow seconds", Crop.WHEAT.growSeconds(), 30);
        Check.eq("wheat seed cost", Crop.WHEAT.seedCost(), 3L);
        Check.eq("wheat raw value", Crop.WHEAT.rawValue(), 20L);
        Check.eq("wheat plantings", Crop.WHEAT.plantings(), 8);
        Check.eq("carrot grow seconds", Crop.CARROT.growSeconds(), 45);
        Check.eq("carrot plantings", Crop.CARROT.plantings(), 7);
        Check.eq("lookup by id", Crop.byId("carrot"), Crop.CARROT);
        Check.eq("unknown id is null", Crop.byId("nope"), null);

        GrowthClock g = GrowthClock.start(Crop.WHEAT, t0);
        Check.eq("ready 30s later", g.readyAt(), t0 + 30_000L);

        Check.isFalse("not ripe at plant time", g.ripe(t0));
        Check.isFalse("not ripe one ms early", g.ripe(t0 + 29_999L));
        Check.isTrue("ripe exactly on time", g.ripe(t0 + 30_000L));
        Check.isTrue("ripe later", g.ripe(t0 + 60_000L));

        Check.eq("stage 0 at plant", g.stage(Crop.WHEAT, t0), 0);
        Check.eq("stage 3 at half way", g.stage(Crop.WHEAT, t0 + 15_000L), 3);
        Check.eq("stage maxes at ripe", g.stage(Crop.WHEAT, t0 + 30_000L), Crop.WHEAT.stageCount() - 1);
        Check.eq("stage clamped past ripe", g.stage(Crop.WHEAT, t0 + 90_000L), Crop.WHEAT.stageCount() - 1);

        Check.eq("remaining at plant", g.remainingMs(t0), 30_000L);
        Check.eq("remaining never negative", g.remainingMs(t0 + 99_000L), 0L);

        // Fertiliser halves what is LEFT, not the total.
        GrowthClock h = g.halved(t0 + 10_000L);
        Check.eq("halved leaves 10s of the remaining 20s", h.remainingMs(t0 + 10_000L), 10_000L);
        Check.eq("halving does not move plantedAt", h.plantedAt(), g.plantedAt());

        GrowthClock ripe = GrowthClock.start(Crop.WHEAT, t0).halved(t0 + 30_000L);
        Check.eq("halving a ripe crop is a no-op", ripe.remainingMs(t0 + 30_000L), 0L);
    }
}
```

- [ ] **Step 2: Register the suite**

Modify `loftsfarm/src/test/java/place/games/loftsfarm/test/TestMain.java` — replace the comment line with:

```java
        GrowthClockTest.run();
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `bash loftsfarm/run-tests.sh`
Expected: FAIL — compile error, `package place.games.loftsfarm.domain does not exist`.

- [ ] **Step 4: Write the crop catalogue**

Create `loftsfarm/src/main/java/place/games/loftsfarm/domain/Crop.java`:

```java
package place.games.loftsfarm.domain;

/**
 * Crop definitions. Every number here comes from the browser reference build,
 * NOT from the live v1 config.yml.
 *
 * stageCount is the number of distinct visual stages the block can show,
 * including stage 0 (just sown) and the final ripe stage.
 */
public enum Crop {
    WHEAT("wheat", "Wheat", 3L, 30, 20L, 8, 8),
    CARROT("carrot", "Carrot", 5L, 45, 30L, 7, 8);

    private final String id;
    private final String displayName;
    private final long seedCost;
    private final int growSeconds;
    private final long rawValue;
    private final int plantings;
    private final int stageCount;

    Crop(String id, String displayName, long seedCost, int growSeconds,
         long rawValue, int plantings, int stageCount) {
        this.id = id;
        this.displayName = displayName;
        this.seedCost = seedCost;
        this.growSeconds = growSeconds;
        this.rawValue = rawValue;
        this.plantings = plantings;
        this.stageCount = stageCount;
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public long seedCost() { return seedCost; }
    public int growSeconds() { return growSeconds; }
    public long rawValue() { return rawValue; }
    public int plantings() { return plantings; }
    public int stageCount() { return stageCount; }

    public static Crop byId(String id) {
        if (id == null) return null;
        for (Crop c : values()) if (c.id.equals(id)) return c;
        return null;
    }
}
```

- [ ] **Step 5: Write the growth clock**

Create `loftsfarm/src/main/java/place/games/loftsfarm/domain/GrowthClock.java`:

```java
package place.games.loftsfarm.domain;

/**
 * Wall-clock crop growth. Ripeness is always derived by comparing readyAt
 * against a supplied now-in-millis — never by counting ticks. That is what
 * makes growth correct while the chunk is unloaded and the owner offline.
 */
public record GrowthClock(long plantedAt, long readyAt) {

    public static GrowthClock start(Crop crop, long nowMs) {
        return new GrowthClock(nowMs, nowMs + crop.growSeconds() * 1000L);
    }

    public boolean ripe(long nowMs) {
        return nowMs >= readyAt;
    }

    public long remainingMs(long nowMs) {
        return Math.max(0L, readyAt - nowMs);
    }

    /** 0-based visual stage, clamped to the crop's final stage. */
    public int stage(Crop crop, long nowMs) {
        int last = crop.stageCount() - 1;
        long total = readyAt - plantedAt;
        if (total <= 0L) return last;
        long elapsed = Math.max(0L, nowMs - plantedAt);
        long s = elapsed * last / total;
        return (int) Math.min(last, s);
    }

    /** Fertiliser: halve what remains. Never extends, never rewinds plantedAt. */
    public GrowthClock halved(long nowMs) {
        long remaining = remainingMs(nowMs);
        if (remaining <= 0L) return this;
        return new GrowthClock(plantedAt, nowMs + remaining / 2L);
    }
}
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `bash loftsfarm/run-tests.sh`
Expected: PASS — `22 passed, 0 failed`, exit 0.

- [ ] **Step 7: Commit**

```bash
git add loftsfarm/src
git commit -m "feat(loftsfarm): crop catalogue and wall-clock growth with visual stages"
```

---

### Task 3: Wallet that cannot go backwards, and virtual storage

**Files:**
- Create: `loftsfarm/src/main/java/place/games/loftsfarm/domain/Wallet.java`
- Create: `loftsfarm/src/main/java/place/games/loftsfarm/domain/Storage.java`
- Create: `loftsfarm/src/test/java/place/games/loftsfarm/test/WalletTest.java`
- Modify: `loftsfarm/src/test/java/place/games/loftsfarm/test/TestMain.java`

**Interfaces:**
- Consumes: `Crop`, `Check`.
- Produces:
  - `Wallet` with `long balance()`, `void credit(long amount)`, `boolean spend(long amount)` returning false and changing nothing when unaffordable, and no method that can produce a negative balance.
  - `Storage` with `int count(Crop c)`, `void add(Crop c, int n)`, `boolean take(Crop c, int n)` returning false and changing nothing when short, `Map<Crop,Integer> snapshot()`.

- [ ] **Step 1: Write the failing test**

Create `loftsfarm/src/test/java/place/games/loftsfarm/test/WalletTest.java`:

```java
package place.games.loftsfarm.test;

import place.games.loftsfarm.domain.Crop;
import place.games.loftsfarm.domain.Storage;
import place.games.loftsfarm.domain.Wallet;

public final class WalletTest {
    public static void run() {
        Wallet w = new Wallet(250L);
        Check.eq("starts at 250", w.balance(), 250L);

        w.credit(50L);
        Check.eq("credit adds", w.balance(), 300L);

        Check.isTrue("affordable spend succeeds", w.spend(100L));
        Check.eq("balance after spend", w.balance(), 200L);

        Check.isFalse("unaffordable spend refused", w.spend(999L));
        Check.eq("refused spend changed nothing", w.balance(), 200L);

        Check.isTrue("spending the exact balance is allowed", w.spend(200L));
        Check.eq("can reach zero", w.balance(), 0L);
        Check.isFalse("cannot spend from zero", w.spend(1L));
        Check.eq("still zero", w.balance(), 0L);

        // P5: nothing may ever reduce a balance below zero, by any route.
        Check.throwsUp("negative credit is rejected", () -> w.credit(-5L));
        Check.throwsUp("negative spend is rejected", () -> w.spend(-5L));
        Check.eq("balance survived the rejections", w.balance(), 0L);

        Storage s = new Storage();
        Check.eq("empty storage counts zero", s.count(Crop.WHEAT), 0);

        s.add(Crop.WHEAT, 3);
        s.add(Crop.WHEAT, 2);
        Check.eq("adds accumulate", s.count(Crop.WHEAT), 5);
        Check.eq("other crops unaffected", s.count(Crop.CARROT), 0);

        Check.isFalse("taking more than held is refused", s.take(Crop.WHEAT, 6));
        Check.eq("refused take changed nothing", s.count(Crop.WHEAT), 5);

        Check.isTrue("taking exactly what is held works", s.take(Crop.WHEAT, 5));
        Check.eq("storage empties", s.count(Crop.WHEAT), 0);

        Check.throwsUp("negative add rejected", () -> s.add(Crop.WHEAT, -1));
        Check.throwsUp("negative take rejected", () -> s.take(Crop.WHEAT, -1));
    }
}
```

- [ ] **Step 2: Register the suite**

Add to `TestMain.main`, after the existing suite call:

```java
        WalletTest.run();
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `bash loftsfarm/run-tests.sh`
Expected: FAIL — `cannot find symbol: class Wallet`.

- [ ] **Step 4: Write the wallet**

Create `loftsfarm/src/main/java/place/games/loftsfarm/domain/Wallet.java`:

```java
package place.games.loftsfarm.domain;

/**
 * v2 currency. Isolated from v1 and from Vault by design.
 *
 * Spec principle P5: nothing ever reduces a player's balance below what they
 * chose to spend. There is deliberately no debit-on-failure method here — a
 * missed order costs the payout, never a fee. If you find yourself wanting a
 * penalty method, the design is wrong, not this class.
 */
public final class Wallet {
    private long balance;

    public Wallet(long starting) {
        if (starting < 0L) throw new IllegalArgumentException("starting balance cannot be negative");
        this.balance = starting;
    }

    public long balance() { return balance; }

    public void credit(long amount) {
        if (amount < 0L) throw new IllegalArgumentException("credit cannot be negative");
        balance += amount;
    }

    /** @return true if the spend happened; false leaves the balance untouched. */
    public boolean spend(long amount) {
        if (amount < 0L) throw new IllegalArgumentException("spend cannot be negative");
        if (amount > balance) return false;
        balance -= amount;
        return true;
    }
}
```

- [ ] **Step 5: Write virtual storage**

Create `loftsfarm/src/main/java/place/games/loftsfarm/domain/Storage.java`:

```java
package place.games.loftsfarm.domain;

import java.util.EnumMap;
import java.util.Map;

/**
 * Virtual farm storage. Harvests go here, not into the player's inventory —
 * matching v1's COLLECT harvest mode and keeping inventory management out of
 * the fast loop.
 */
public final class Storage {
    private final Map<Crop, Integer> held = new EnumMap<>(Crop.class);

    public int count(Crop crop) {
        return held.getOrDefault(crop, 0);
    }

    public void add(Crop crop, int n) {
        if (n < 0) throw new IllegalArgumentException("add cannot be negative");
        held.merge(crop, n, Integer::sum);
    }

    /** @return true if taken in full; false leaves storage untouched. */
    public boolean take(Crop crop, int n) {
        if (n < 0) throw new IllegalArgumentException("take cannot be negative");
        int have = count(crop);
        if (have < n) return false;
        held.put(crop, have - n);
        return true;
    }

    public Map<Crop, Integer> snapshot() {
        return new EnumMap<>(held);
    }
}
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `bash loftsfarm/run-tests.sh`
Expected: PASS — all assertions green, exit 0.

- [ ] **Step 7: Commit**

```bash
git add loftsfarm/src
git commit -m "feat(loftsfarm): wallet with no-negative invariant and virtual storage"
```

---

### Task 4: Seed durability

**Files:**
- Create: `loftsfarm/src/main/java/place/games/loftsfarm/domain/SeedStack.java`
- Create: `loftsfarm/src/test/java/place/games/loftsfarm/test/SeedStackTest.java`
- Modify: `loftsfarm/src/test/java/place/games/loftsfarm/test/TestMain.java`

**Interfaces:**
- Consumes: `Crop`, `Check`.
- Produces: `SeedStack` with `static SeedStack fresh(Crop)`, `Crop crop()`, `int remaining()`, `boolean spent()`, `SeedStack usedOnce()` returning a new stack with one fewer planting, and `int damageFor(int maxDurability)` mapping remaining plantings onto a vanilla damage value.

- [ ] **Step 1: Write the failing test**

Create `loftsfarm/src/test/java/place/games/loftsfarm/test/SeedStackTest.java`:

```java
package place.games.loftsfarm.test;

import place.games.loftsfarm.domain.Crop;
import place.games.loftsfarm.domain.SeedStack;

public final class SeedStackTest {
    public static void run() {
        SeedStack s = SeedStack.fresh(Crop.WHEAT);
        Check.eq("fresh wheat seed has 8 plantings", s.remaining(), 8);
        Check.isFalse("fresh seed is not spent", s.spent());
        Check.eq("remembers its crop", s.crop(), Crop.WHEAT);

        SeedStack after = s.usedOnce();
        Check.eq("one use consumed", after.remaining(), 7);
        Check.eq("original is unchanged (immutable)", s.remaining(), 8);

        SeedStack walk = SeedStack.fresh(Crop.CARROT);
        Check.eq("carrot starts at 7", walk.remaining(), 7);
        for (int i = 0; i < 7; i++) walk = walk.usedOnce();
        Check.eq("all uses consumed", walk.remaining(), 0);
        Check.isTrue("now spent", walk.spent());

        final SeedStack exhausted = walk;
        Check.throwsUp("using a spent seed is a bug, not a silent no-op",
                () -> exhausted.usedOnce());

        // Damage bar: full seed shows no damage, nearly-spent shows nearly full damage.
        SeedStack w = SeedStack.fresh(Crop.WHEAT);
        Check.eq("full seed has zero damage", w.damageFor(64), 0);
        Check.eq("half-used seed is half damaged", w.usedOnce().usedOnce()
                .usedOnce().usedOnce().damageFor(64), 32);

        SeedStack one = SeedStack.fresh(Crop.WHEAT);
        for (int i = 0; i < 7; i++) one = one.usedOnce();
        Check.eq("one planting left shows 7/8 damage", one.damageFor(64), 56);
        Check.isFalse("one planting left is not spent", one.spent());
    }
}
```

- [ ] **Step 2: Register the suite**

Add to `TestMain.main`:

```java
        SeedStackTest.run();
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `bash loftsfarm/run-tests.sh`
Expected: FAIL — `cannot find symbol: class SeedStack`.

- [ ] **Step 4: Write the seed stack**

Create `loftsfarm/src/main/java/place/games/loftsfarm/domain/SeedStack.java`:

```java
package place.games.loftsfarm.domain;

/**
 * A seed item's remaining plantings. Immutable — planting produces a new stack.
 *
 * Durability rather than a recovery chance, deliberately: it yields the same
 * expected sink as a probability roll with no bad-luck spirals on an expensive
 * seed, and it is legible on the item.
 */
public record SeedStack(Crop crop, int remaining) {

    public SeedStack {
        if (crop == null) throw new IllegalArgumentException("crop required");
        if (remaining < 0) throw new IllegalArgumentException("remaining cannot be negative");
        if (remaining > crop.plantings()) {
            throw new IllegalArgumentException("remaining exceeds this crop's plantings");
        }
    }

    public static SeedStack fresh(Crop crop) {
        return new SeedStack(crop, crop.plantings());
    }

    public boolean spent() { return remaining == 0; }

    public SeedStack usedOnce() {
        if (spent()) throw new IllegalStateException("seed is spent");
        return new SeedStack(crop, remaining - 1);
    }

    /**
     * Maps remaining plantings onto a vanilla damage value so the player sees a
     * familiar wearing-down bar. A full seed reports 0 damage.
     */
    public int damageFor(int maxDurability) {
        int used = crop.plantings() - remaining;
        return (int) ((long) used * maxDurability / crop.plantings());
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `bash loftsfarm/run-tests.sh`
Expected: PASS, exit 0.

- [ ] **Step 6: Commit**

```bash
git add loftsfarm/src
git commit -m "feat(loftsfarm): immutable seed durability with vanilla damage mapping"
```

---

### Task 5: Fertiliser pricing and the order model

**Files:**
- Create: `loftsfarm/src/main/java/place/games/loftsfarm/domain/Fertiliser.java`
- Create: `loftsfarm/src/main/java/place/games/loftsfarm/domain/OrderLine.java`
- Create: `loftsfarm/src/main/java/place/games/loftsfarm/domain/Order.java`
- Create: `loftsfarm/src/test/java/place/games/loftsfarm/test/OrderTest.java`
- Modify: `loftsfarm/src/test/java/place/games/loftsfarm/test/TestMain.java`

**Interfaces:**
- Consumes: `Crop`, `GrowthClock`, `Storage`, `Check`.
- Produces:
  - `Fertiliser.costFor(Crop crop, long remainingMs)` returning `long`.
  - `record OrderLine(Crop crop, int qty, long unitPrice)` with `long lineValue()`.
  - `Order` with `static Order single(String id, Crop crop, int qty, long unitPrice, long dueAt)`, `String id()`, `List<OrderLine> lines()`, `long pay()`, `long dueAt()`, `boolean expired(long nowMs)`, `boolean satisfiedBy(Storage s)`, `int heldFor(Storage s, int lineIdx)`, `boolean deliver(Storage s)`.

- [ ] **Step 1: Write the failing test**

Create `loftsfarm/src/test/java/place/games/loftsfarm/test/OrderTest.java`:

```java
package place.games.loftsfarm.test;

import place.games.loftsfarm.domain.Crop;
import place.games.loftsfarm.domain.Fertiliser;
import place.games.loftsfarm.domain.Order;
import place.games.loftsfarm.domain.Storage;

public final class OrderTest {
    public static void run() {
        // Fertiliser is priced off crop value and how much time is left, so it is
        // always net-positive to use (P5). Wheat raw value 20, factor 0.26.
        Check.eq("full wheat cycle costs 5", Fertiliser.costFor(Crop.WHEAT, 30_000L), 5L);
        Check.eq("half a wheat cycle costs 4 (floored at minimum)",
                Fertiliser.costFor(Crop.WHEAT, 15_000L), 4L);
        Check.eq("a ripe crop costs the minimum", Fertiliser.costFor(Crop.WHEAT, 0L), 4L);
        Check.eq("full carrot cycle costs 8", Fertiliser.costFor(Crop.CARROT, 45_000L), 8L);
        Check.isTrue("fertilising is always cheaper than the crop is worth",
                Fertiliser.costFor(Crop.CARROT, 45_000L) < Crop.CARROT.rawValue());

        Order o = Order.single("k1", Crop.WHEAT, 3, 23L, 100_000L);
        Check.eq("one line", o.lines().size(), 1);
        Check.eq("pay is qty times unit", o.pay(), 69L);
        Check.isFalse("not expired before due", o.expired(99_999L));
        Check.isTrue("expired on due", o.expired(100_000L));

        Storage s = new Storage();
        Check.isFalse("empty storage does not satisfy", o.satisfiedBy(s));
        Check.eq("holds none", o.heldFor(s, 0), 0);

        s.add(Crop.WHEAT, 2);
        Check.isFalse("partial stock does not satisfy", o.satisfiedBy(s));
        Check.eq("holds two of three", o.heldFor(s, 0), 2);
        Check.isFalse("cannot deliver while short", o.deliver(s));
        Check.eq("failed delivery consumed nothing", s.count(Crop.WHEAT), 2);

        s.add(Crop.WHEAT, 5);
        Check.isTrue("enough stock satisfies", o.satisfiedBy(s));
        Check.eq("tally caps at required", o.heldFor(s, 0), 3);
        Check.isTrue("delivery succeeds", o.deliver(s));
        Check.eq("delivery consumed exactly the quantity", s.count(Crop.WHEAT), 4);
    }
}
```

- [ ] **Step 2: Register the suite**

Add to `TestMain.main`:

```java
        OrderTest.run();
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `bash loftsfarm/run-tests.sh`
Expected: FAIL — `cannot find symbol: class Fertiliser`.

- [ ] **Step 4: Write fertiliser pricing**

Create `loftsfarm/src/main/java/place/games/loftsfarm/domain/Fertiliser.java`:

```java
package place.games.loftsfarm.domain;

/**
 * Fertiliser is the toll between the slow half of the game and the fast half.
 *
 * Priced off what the crop is worth — never off what the seed cost — and scaled
 * by how much time is left, so applying it is always net-positive (P5).
 */
public final class Fertiliser {
    private static final double VALUE_FACTOR = 0.26d;
    private static final long MINIMUM = 4L;

    private Fertiliser() {}

    public static long costFor(Crop crop, long remainingMs) {
        long total = crop.growSeconds() * 1000L;
        double share = total <= 0L ? 0d : Math.min(1d, (double) Math.max(0L, remainingMs) / total);
        long scaled = Math.round(crop.rawValue() * VALUE_FACTOR * share);
        return Math.max(MINIMUM, scaled);
    }
}
```

- [ ] **Step 5: Write the order line**

Create `loftsfarm/src/main/java/place/games/loftsfarm/domain/OrderLine.java`:

```java
package place.games.loftsfarm.domain;

/** One good demanded by an order. A single-good order is a one-line order. */
public record OrderLine(Crop crop, int qty, long unitPrice) {

    public OrderLine {
        if (crop == null) throw new IllegalArgumentException("crop required");
        if (qty <= 0) throw new IllegalArgumentException("qty must be positive");
        if (unitPrice < 0L) throw new IllegalArgumentException("unitPrice cannot be negative");
    }

    public long lineValue() {
        return qty * unitPrice;
    }
}
```

- [ ] **Step 6: Write the order**

Create `loftsfarm/src/main/java/place/games/loftsfarm/domain/Order.java`:

```java
package place.games.loftsfarm.domain;

import java.util.List;

/**
 * An order is a list of lines. A single-good order is simply a one-line order,
 * so there is one code path rather than two.
 *
 * All-or-nothing: an order pays only when every line is satisfied. Missing one
 * costs the payout and nothing else — there is no penalty path here (P5).
 */
public final class Order {
    private final String id;
    private final List<OrderLine> lines;
    private final long dueAt;

    public Order(String id, List<OrderLine> lines, long dueAt) {
        if (id == null || id.isEmpty()) throw new IllegalArgumentException("id required");
        if (lines == null || lines.isEmpty()) throw new IllegalArgumentException("at least one line");
        this.id = id;
        this.lines = List.copyOf(lines);
        this.dueAt = dueAt;
    }

    public static Order single(String id, Crop crop, int qty, long unitPrice, long dueAt) {
        return new Order(id, List.of(new OrderLine(crop, qty, unitPrice)), dueAt);
    }

    public String id() { return id; }
    public List<OrderLine> lines() { return lines; }
    public long dueAt() { return dueAt; }

    public long pay() {
        long total = 0L;
        for (OrderLine l : lines) total += l.lineValue();
        return total;
    }

    public boolean expired(long nowMs) {
        return nowMs >= dueAt;
    }

    /** How much of one line the player holds, capped at what the line requires. */
    public int heldFor(Storage storage, int lineIdx) {
        OrderLine l = lines.get(lineIdx);
        return Math.min(storage.count(l.crop()), l.qty());
    }

    public boolean satisfiedBy(Storage storage) {
        for (OrderLine l : lines) {
            if (storage.count(l.crop()) < l.qty()) return false;
        }
        return true;
    }

    /**
     * Consumes the goods if every line can be met. Consumes nothing otherwise,
     * so a failed delivery can never partially raid storage.
     */
    public boolean deliver(Storage storage) {
        if (!satisfiedBy(storage)) return false;
        for (OrderLine l : lines) storage.take(l.crop(), l.qty());
        return true;
    }
}
```

- [ ] **Step 7: Run the tests to verify they pass**

Run: `bash loftsfarm/run-tests.sh`
Expected: PASS, exit 0.

- [ ] **Step 8: Commit**

```bash
git add loftsfarm/src
git commit -m "feat(loftsfarm): fertiliser pricing and all-or-nothing order model"
```

---

### Task 6: Persistence that survives restart

**Files:**
- Create: `loftsfarm/src/main/java/place/games/loftsfarm/domain/PlantRecord.java`
- Create: `loftsfarm/src/main/java/place/games/loftsfarm/domain/FarmState.java`
- Create: `loftsfarm/src/main/java/place/games/loftsfarm/domain/StateCodec.java`
- Create: `loftsfarm/src/test/java/place/games/loftsfarm/test/StateCodecTest.java`
- Modify: `loftsfarm/src/test/java/place/games/loftsfarm/test/TestMain.java`

**Interfaces:**
- Consumes: `Crop`, `GrowthClock`, `Wallet`, `Storage`, `Check`.
- Produces:
  - `record PlantRecord(String world, int x, int y, int z, Crop crop, long plantedAt, long readyAt)` with `String key()` returning `world:x:y:z` and `GrowthClock clock()`.
  - `FarmState` with `Wallet wallet()`, `Storage storage()`, `Map<String,PlantRecord> plants()`, `void put(PlantRecord)`, `PlantRecord remove(String key)`.
  - `StateCodec.write(FarmState)` returning a `String`, and `StateCodec.read(String)` returning a `FarmState`. Round-trip safe. Hand-rolled line format — no JSON library is available.

**Note on the spec:** §6 specifies SQLite. This slice uses a flat text file behind the same `StateCodec` seam, because no JDBC driver is present and this is a throwaway. Swapping to SQLite later replaces `StateCodec` only. Record this deviation in the build notes.

- [ ] **Step 1: Write the failing test**

Create `loftsfarm/src/test/java/place/games/loftsfarm/test/StateCodecTest.java`:

```java
package place.games.loftsfarm.test;

import place.games.loftsfarm.domain.Crop;
import place.games.loftsfarm.domain.FarmState;
import place.games.loftsfarm.domain.PlantRecord;
import place.games.loftsfarm.domain.StateCodec;

public final class StateCodecTest {
    public static void run() {
        FarmState st = new FarmState(250L);
        st.storage().add(Crop.WHEAT, 4);
        st.storage().add(Crop.CARROT, 1);
        st.wallet().credit(75L);
        st.put(new PlantRecord("flat", 10, 64, -3, Crop.WHEAT, 1_000L, 31_000L));
        st.put(new PlantRecord("flat", 11, 64, -3, Crop.CARROT, 2_000L, 47_000L));

        Check.eq("key format", st.plants().get("flat:10:64:-3").key(), "flat:10:64:-3");

        String text = StateCodec.write(st);
        FarmState back = StateCodec.read(text);

        Check.eq("balance round-trips", back.wallet().balance(), 325L);
        Check.eq("wheat stock round-trips", back.storage().count(Crop.WHEAT), 4);
        Check.eq("carrot stock round-trips", back.storage().count(Crop.CARROT), 1);
        Check.eq("plant count round-trips", back.plants().size(), 2);

        PlantRecord p = back.plants().get("flat:10:64:-3");
        Check.eq("plant crop round-trips", p.crop(), Crop.WHEAT);
        Check.eq("plantedAt round-trips", p.plantedAt(), 1_000L);
        Check.eq("readyAt round-trips", p.readyAt(), 31_000L);
        Check.eq("negative coordinate round-trips", p.z(), -3);

        // Ripeness must survive a restart because it is derived from a timestamp.
        Check.isTrue("crop is ripe against a later clock", p.clock().ripe(31_000L));
        Check.isFalse("and not ripe against an earlier one", p.clock().ripe(30_999L));

        Check.eq("removing returns the record", st.remove("flat:11:64:-3").crop(), Crop.CARROT);
        Check.eq("removal shrinks the map", st.plants().size(), 1);
        Check.eq("removing an absent key is null", st.remove("nope"), null);

        FarmState empty = StateCodec.read(StateCodec.write(new FarmState(0L)));
        Check.eq("empty state round-trips", empty.plants().size(), 0);
        Check.eq("zero balance round-trips", empty.wallet().balance(), 0L);
    }
}
```

- [ ] **Step 2: Register the suite**

Add to `TestMain.main`:

```java
        StateCodecTest.run();
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `bash loftsfarm/run-tests.sh`
Expected: FAIL — `cannot find symbol: class FarmState`.

- [ ] **Step 4: Write the plant record**

Create `loftsfarm/src/main/java/place/games/loftsfarm/domain/PlantRecord.java`:

```java
package place.games.loftsfarm.domain;

/** One tracked plant at one block position. */
public record PlantRecord(String world, int x, int y, int z,
                          Crop crop, long plantedAt, long readyAt) {

    public String key() {
        return world + ":" + x + ":" + y + ":" + z;
    }

    public GrowthClock clock() {
        return new GrowthClock(plantedAt, readyAt);
    }

    public PlantRecord withClock(GrowthClock c) {
        return new PlantRecord(world, x, y, z, crop, c.plantedAt(), c.readyAt());
    }
}
```

- [ ] **Step 5: Write the farm state**

Create `loftsfarm/src/main/java/place/games/loftsfarm/domain/FarmState.java`:

```java
package place.games.loftsfarm.domain;

import java.util.HashMap;
import java.util.Map;

/** Everything one player's farm knows. One instance per player. */
public final class FarmState {
    private final Wallet wallet;
    private final Storage storage = new Storage();
    private final Map<String, PlantRecord> plants = new HashMap<>();

    public FarmState(long startingCash) {
        this.wallet = new Wallet(startingCash);
    }

    public Wallet wallet() { return wallet; }
    public Storage storage() { return storage; }
    public Map<String, PlantRecord> plants() { return plants; }

    public void put(PlantRecord r) { plants.put(r.key(), r); }
    public PlantRecord remove(String key) { return plants.remove(key); }
}
```

- [ ] **Step 6: Write the codec**

Create `loftsfarm/src/main/java/place/games/loftsfarm/domain/StateCodec.java`:

```java
package place.games.loftsfarm.domain;

/**
 * Hand-rolled line format. No JSON library is available in this environment and
 * no downloads are permitted, so the format is deliberately trivial:
 *
 *   cash <balance>
 *   stock <cropId> <count>
 *   plant <world> <x> <y> <z> <cropId> <plantedAt> <readyAt>
 *
 * Spec §6 calls for SQLite. This is the throwaway-slice stand-in behind the same
 * seam; swapping to SQLite replaces this class only.
 */
public final class StateCodec {

    private StateCodec() {}

    public static String write(FarmState state) {
        StringBuilder sb = new StringBuilder();
        sb.append("cash ").append(state.wallet().balance()).append('\n');
        state.storage().snapshot().forEach((crop, n) -> {
            if (n > 0) sb.append("stock ").append(crop.id()).append(' ').append(n).append('\n');
        });
        for (PlantRecord p : state.plants().values()) {
            sb.append("plant ").append(p.world()).append(' ')
              .append(p.x()).append(' ').append(p.y()).append(' ').append(p.z()).append(' ')
              .append(p.crop().id()).append(' ')
              .append(p.plantedAt()).append(' ').append(p.readyAt()).append('\n');
        }
        return sb.toString();
    }

    public static FarmState read(String text) {
        long cash = 0L;
        // Balance must be known before the state is constructed.
        for (String line : text.split("\n")) {
            if (line.startsWith("cash ")) {
                cash = Long.parseLong(line.substring(5).trim());
                break;
            }
        }
        FarmState state = new FarmState(cash);

        for (String raw : text.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            String[] p = line.split(" ");
            switch (p[0]) {
                case "stock" -> {
                    Crop c = Crop.byId(p[1]);
                    if (c != null) state.storage().add(c, Integer.parseInt(p[2]));
                }
                case "plant" -> {
                    Crop c = Crop.byId(p[5]);
                    if (c != null) {
                        state.put(new PlantRecord(p[1],
                                Integer.parseInt(p[2]), Integer.parseInt(p[3]), Integer.parseInt(p[4]),
                                c, Long.parseLong(p[6]), Long.parseLong(p[7])));
                    }
                }
                default -> { /* cash handled above; unknown lines ignored */ }
            }
        }
        return state;
    }
}
```

- [ ] **Step 7: Run the tests to verify they pass**

Run: `bash loftsfarm/run-tests.sh`
Expected: PASS, exit 0.

- [ ] **Step 8: Commit**

```bash
git add loftsfarm/src
git commit -m "feat(loftsfarm): farm state with restart-safe timestamp persistence"
```

---

### Task 7: Seed and fertiliser items

**Files:**
- Create: `loftsfarm/src/main/java/place/games/loftsfarm/bukkit/Items.java`
- Modify: `loftsfarm/src/main/java/place/games/loftsfarm/bukkit/LoftsFarmPlugin.java`

**Interfaces:**
- Consumes: `Crop`, `SeedStack` from the domain layer.
- Produces:
  - `Items.seed(Plugin plugin, SeedStack stack)` returning an `ItemStack` carrying crop id and remaining plantings in PDC, with a damage bar and lore.
  - `Items.readSeed(Plugin plugin, ItemStack item)` returning `SeedStack` or `null` when the item is not one of ours.
  - `Items.fertiliser(Plugin plugin)` returning the fertiliser `ItemStack`.
  - `Items.isFertiliser(Plugin plugin, ItemStack item)` returning `boolean`.

- [ ] **Step 1: Write the items helper**

Create `loftsfarm/src/main/java/place/games/loftsfarm/bukkit/Items.java`:

```java
package place.games.loftsfarm.bukkit;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import place.games.loftsfarm.domain.Crop;
import place.games.loftsfarm.domain.SeedStack;

import java.util.List;

/** Builds and reads the plugin's custom items. */
public final class Items {

    private Items() {}

    private static NamespacedKey cropKey(Plugin p) { return new NamespacedKey(p, "crop"); }
    private static NamespacedKey usesKey(Plugin p) { return new NamespacedKey(p, "uses"); }
    private static NamespacedKey fertKey(Plugin p) { return new NamespacedKey(p, "fertiliser"); }

    /** Vanilla material used to represent each crop's seed. */
    private static Material seedMaterial(Crop crop) {
        return switch (crop) {
            case WHEAT -> Material.WHEAT_SEEDS;
            case CARROT -> Material.CARROT;
        };
    }

    public static ItemStack seed(Plugin plugin, SeedStack stack) {
        ItemStack item = new ItemStack(seedMaterial(stack.crop()));
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("\u00A7e" + stack.crop().displayName() + " Seed");
        meta.setLore(List.of(
                "\u00A77Plantings left: \u00A7f" + stack.remaining() + "\u00A78/" + stack.crop().plantings(),
                "\u00A78Grows in " + stack.crop().growSeconds() + "s",
                "\u00A78Right-click farmland to sow"
        ));

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(cropKey(plugin), PersistentDataType.STRING, stack.crop().id());
        pdc.set(usesKey(plugin), PersistentDataType.INTEGER, stack.remaining());

        // Drive the vanilla damage bar off remaining plantings so wear is visible.
        if (meta instanceof Damageable dmg) {
            int max = item.getType().getMaxDurability();
            if (max > 0) dmg.setDamage(stack.damageFor(max));
        }

        item.setItemMeta(meta);
        return item;
    }

    public static SeedStack readSeed(Plugin plugin, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String id = pdc.get(cropKey(plugin), PersistentDataType.STRING);
        Integer uses = pdc.get(usesKey(plugin), PersistentDataType.INTEGER);
        if (id == null || uses == null) return null;
        Crop crop = Crop.byId(id);
        if (crop == null) return null;
        return new SeedStack(crop, Math.min(uses, crop.plantings()));
    }

    public static ItemStack fertiliser(Plugin plugin) {
        ItemStack item = new ItemStack(Material.BONE_MEAL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("\u00A7aFertiliser");
        meta.setLore(List.of(
                "\u00A77Right-click a growing crop",
                "\u00A77Halves the time it has left",
                "\u00A78Cost scales with time remaining"
        ));
        meta.getPersistentDataContainer().set(fertKey(plugin), PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isFertiliser(Plugin plugin, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(fertKey(plugin), PersistentDataType.BYTE);
    }
}
```

- [ ] **Step 2: Build to verify it compiles against the Paper API**

Run: `bash loftsfarm/build.sh`
Expected: builds with no errors, `Built build/LoftsFarm.jar`.

- [ ] **Step 3: Verify the domain boundary still holds**

Run: `bash loftsfarm/run-tests.sh`
Expected: PASS. The `bukkit` package is excluded from the test compile, so if `Items.java` had leaked into the domain the test compile would fail here.

- [ ] **Step 4: Commit**

```bash
git add loftsfarm/src
git commit -m "feat(loftsfarm): seed items with PDC durability and a damage bar, plus fertiliser"
```

---

### Task 8: Planting, growth scheduling, and cancelling vanilla growth

**Files:**
- Create: `loftsfarm/src/main/java/place/games/loftsfarm/bukkit/FarmService.java`
- Create: `loftsfarm/src/main/java/place/games/loftsfarm/bukkit/GrowthScheduler.java`
- Create: `loftsfarm/src/main/java/place/games/loftsfarm/bukkit/FarmListener.java`
- Modify: `loftsfarm/src/main/java/place/games/loftsfarm/bukkit/LoftsFarmPlugin.java`

**Interfaces:**
- Consumes: `Crop`, `GrowthClock`, `PlantRecord`, `FarmState`, `StateCodec`, `SeedStack`, `Items`.
- Produces:
  - `FarmService` with `FarmState state(UUID)`, `void save()`, `void load()`, `PlantRecord plantAt(Player, Block, SeedStack)`, `boolean harvestAt(Player, Block)`, `boolean discardAt(Block)`, `boolean fertiliseAt(Player, Block)`, `Optional<PlantRecord> at(Block)`, `UUID ownerOf(Block)`.
  - `GrowthScheduler` with `void schedule(PlantRecord)`, `void cancel(String key)`, `void cancelAll()`, `void paintStage(PlantRecord)`.

- [ ] **Step 1: Write the growth scheduler**

Create `loftsfarm/src/main/java/place/games/loftsfarm/bukkit/GrowthScheduler.java`:

```java
package place.games.loftsfarm.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import place.games.loftsfarm.domain.PlantRecord;

import java.util.HashMap;
import java.util.Map;

/**
 * One scheduled task per plant, firing at its readyAt. Never a sweep over all
 * plots — that is a hard constraint from the spec, because a global poll is the
 * whole performance budget at 50+ farms.
 *
 * Intermediate visual stages are painted only when a player is close enough to
 * see them. When nobody is nearby the block is corrected on the next paint.
 */
public final class GrowthScheduler {
    private static final double PAINT_RADIUS_SQ = 64.0 * 64.0;

    private final Plugin plugin;
    private final Map<String, BukkitTask> tasks = new HashMap<>();

    public GrowthScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    public void schedule(PlantRecord record) {
        cancel(record.key());
        long delayMs = record.clock().remainingMs(System.currentTimeMillis());
        long ticks = Math.max(1L, delayMs / 50L);
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            tasks.remove(record.key());
            paintStage(record);
        }, ticks);
        tasks.put(record.key(), task);
    }

    public void cancel(String key) {
        BukkitTask t = tasks.remove(key);
        if (t != null) t.cancel();
    }

    public void cancelAll() {
        tasks.values().forEach(BukkitTask::cancel);
        tasks.clear();
    }

    /**
     * Sets the block's age to match elapsed real time.
     *
     * Intermediate stages are skipped when nobody is close enough to see them —
     * the spec only requires interpolation for chunks with a player in view
     * distance. A ripe crop is always painted regardless of who is watching,
     * because its final state must be correct whenever it is next observed.
     */
    public void paintStage(PlantRecord record) {
        World world = Bukkit.getWorld(record.world());
        if (world == null) return;
        if (!world.isChunkLoaded(record.x() >> 4, record.z() >> 4)) return;

        boolean ripe = record.clock().ripe(System.currentTimeMillis());
        if (!ripe && !anyPlayerNear(record)) return;

        Block block = world.getBlockAt(record.x(), record.y(), record.z());
        BlockData data = block.getBlockData();
        if (!(data instanceof Ageable ageable)) return;

        int stage = record.clock().stage(record.crop(), System.currentTimeMillis());
        int target = Math.min(ageable.getMaximumAge(), stage);
        if (ageable.getAge() != target) {
            ageable.setAge(target);
            block.setBlockData(ageable, false);
        }
    }

    private boolean anyPlayerNear(PlantRecord record) {
        World world = Bukkit.getWorld(record.world());
        if (world == null) return false;
        Location at = new Location(world, record.x(), record.y(), record.z());
        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(at) < PAINT_RADIUS_SQ) return true;
        }
        return false;
    }
}
```

- [ ] **Step 2: Write the farm service**

Create `loftsfarm/src/main/java/place/games/loftsfarm/bukkit/FarmService.java`:

```java
package place.games.loftsfarm.bukkit;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import place.games.loftsfarm.domain.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Owns per-player farm state, persistence, and the plant/harvest/fertilise verbs. */
public final class FarmService {
    private static final long STARTING_CASH = 250L;

    private final Plugin plugin;
    private final GrowthScheduler scheduler;
    private final Map<UUID, FarmState> states = new HashMap<>();
    private final Map<String, UUID> owners = new HashMap<>();

    public FarmService(Plugin plugin, GrowthScheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
    }

    public FarmState state(UUID id) {
        return states.computeIfAbsent(id, k -> new FarmState(STARTING_CASH));
    }

    public UUID ownerOf(Block block) {
        return owners.get(keyOf(block));
    }

    private static String keyOf(Block b) {
        return b.getWorld().getName() + ":" + b.getX() + ":" + b.getY() + ":" + b.getZ();
    }

    public Optional<PlantRecord> at(Block block) {
        UUID owner = ownerOf(block);
        if (owner == null) return Optional.empty();
        return Optional.ofNullable(state(owner).plants().get(keyOf(block)));
    }

    public PlantRecord plantAt(Player player, Block block, SeedStack stack) {
        long now = System.currentTimeMillis();
        GrowthClock clock = GrowthClock.start(stack.crop(), now);
        PlantRecord record = new PlantRecord(block.getWorld().getName(),
                block.getX(), block.getY(), block.getZ(),
                stack.crop(), clock.plantedAt(), clock.readyAt());

        state(player.getUniqueId()).put(record);
        owners.put(record.key(), player.getUniqueId());
        scheduler.schedule(record);
        scheduler.paintStage(record);
        return record;
    }

    public boolean harvestAt(Player player, Block block) {
        UUID owner = ownerOf(block);
        if (owner == null || !owner.equals(player.getUniqueId())) return false;
        FarmState st = state(owner);
        PlantRecord rec = st.plants().get(keyOf(block));
        if (rec == null || !rec.clock().ripe(System.currentTimeMillis())) return false;

        st.remove(rec.key());
        owners.remove(rec.key());
        scheduler.cancel(rec.key());
        st.storage().add(rec.crop(), 1);
        return true;
    }

    /**
     * Untracks a plant without paying out. Used when a crop is destroyed before
     * it ripens, so no record or scheduled task is left pointing at air.
     */
    public boolean discardAt(Block block) {
        UUID owner = ownerOf(block);
        if (owner == null) return false;
        String key = keyOf(block);
        state(owner).remove(key);
        owners.remove(key);
        scheduler.cancel(key);
        return true;
    }

    public boolean fertiliseAt(Player player, Block block) {
        UUID owner = ownerOf(block);
        if (owner == null || !owner.equals(player.getUniqueId())) return false;
        FarmState st = state(owner);
        PlantRecord rec = st.plants().get(keyOf(block));
        if (rec == null) return false;

        long now = System.currentTimeMillis();
        long remaining = rec.clock().remainingMs(now);
        if (remaining <= 0L) return false;

        long cost = Fertiliser.costFor(rec.crop(), remaining);
        if (!st.wallet().spend(cost)) return false;

        PlantRecord updated = rec.withClock(rec.clock().halved(now));
        st.put(updated);
        scheduler.schedule(updated);
        scheduler.paintStage(updated);
        player.sendMessage("\u00A78[\u00A7c!\u00A78] \u00A77Fertilised for \u00A7f$" + cost
                + "\u00A77. Halved the wait.");
        return true;
    }

    // ---- persistence ----

    private Path file() {
        return plugin.getDataFolder().toPath().resolve("farms.txt");
    }

    public void save() {
        StringBuilder sb = new StringBuilder();
        states.forEach((id, st) -> {
            sb.append("player ").append(id).append('\n');
            sb.append(StateCodec.write(st));
            sb.append("end\n");
        });
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), sb.toString());
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save farms: " + e.getMessage());
        }
    }

    public void load() {
        Path f = file();
        if (!Files.exists(f)) return;
        try {
            String all = Files.readString(f);
            UUID current = null;
            StringBuilder chunk = new StringBuilder();
            for (String line : all.split("\n")) {
                if (line.startsWith("player ")) {
                    current = UUID.fromString(line.substring(7).trim());
                    chunk.setLength(0);
                } else if (line.trim().equals("end") && current != null) {
                    FarmState st = StateCodec.read(chunk.toString());
                    states.put(current, st);
                    for (PlantRecord r : st.plants().values()) {
                        owners.put(r.key(), current);
                        scheduler.schedule(r);
                        scheduler.paintStage(r);
                    }
                    current = null;
                } else {
                    chunk.append(line).append('\n');
                }
            }
            plugin.getLogger().info("Loaded " + states.size() + " farm(s).");
        } catch (Exception e) {
            plugin.getLogger().warning("Could not load farms: " + e.getMessage());
        }
    }
}
```

- [ ] **Step 3: Write the listener**

Create `loftsfarm/src/main/java/place/games/loftsfarm/bukkit/FarmListener.java`:

```java
package place.games.loftsfarm.bukkit;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import place.games.loftsfarm.domain.Crop;
import place.games.loftsfarm.domain.SeedStack;

public final class FarmListener implements Listener {

    private final Plugin plugin;
    private final FarmService farms;

    public FarmListener(Plugin plugin, FarmService farms) {
        this.plugin = plugin;
        this.farms = farms;
    }

    /** Vanilla growth must never advance a tracked plant. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGrow(BlockGrowEvent event) {
        if (farms.at(event.getBlock()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        if (block == null) return;

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            ItemStack held = player.getInventory().getItemInMainHand();

            // Fertilise a growing crop.
            if (Items.isFertiliser(plugin, held) && farms.at(block).isPresent()) {
                event.setCancelled(true);
                if (farms.fertiliseAt(player, block)) {
                    if (player.getGameMode() != GameMode.CREATIVE) held.setAmount(held.getAmount() - 1);
                }
                return;
            }

            // Harvest a ripe crop.
            if (farms.at(block).isPresent()) {
                event.setCancelled(true);
                if (farms.harvestAt(player, block)) {
                    block.setType(Material.AIR);
                    player.sendMessage("\u00A78[\u00A7c!\u00A78] \u00A77Harvested to farm storage.");
                } else {
                    farms.at(block).ifPresent(r -> player.sendMessage(
                            "\u00A78[\u00A7c!\u00A78] \u00A77Not ready \u2014 \u00A7f"
                            + (r.clock().remainingMs(System.currentTimeMillis()) / 1000) + "s\u00A77 left."));
                }
                return;
            }

            // Sow from the hand.
            SeedStack stack = Items.readSeed(plugin, held);
            if (stack != null && block.getType() == Material.FARMLAND) {
                event.setCancelled(true);
                Block above = block.getRelative(0, 1, 0);
                if (!above.getType().isAir()) {
                    player.sendMessage("\u00A78[\u00A7c!\u00A78] \u00A77Something is already there.");
                    return;
                }
                above.setType(cropMaterial(stack.crop()));
                if (above.getBlockData() instanceof Ageable a) {
                    a.setAge(0);
                    above.setBlockData(a, false);
                }
                farms.plantAt(player, above, stack);

                SeedStack after = stack.usedOnce();
                if (after.spent()) {
                    held.setAmount(held.getAmount() - 1);
                    player.sendMessage("\u00A78[\u00A7c!\u00A78] \u00A77That seed wore out.");
                } else {
                    held.setItemMeta(Items.seed(plugin, after).getItemMeta());
                }
            }
        }
    }

    /**
     * Breaking a tracked crop must always untrack it, ripe or not — otherwise the
     * block is gone while the record and its scheduled task live on, pointing at
     * air. Ripe crops still pay out; unripe ones are simply discarded.
     */
    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (farms.at(block).isEmpty()) return;

        event.setDropItems(false);
        if (!farms.harvestAt(event.getPlayer(), block)) {
            // Not ripe (or not the owner's) — drop the record so nothing is orphaned.
            farms.discardAt(block);
            event.getPlayer().sendMessage(
                    "§8[§c!§8] §7That crop wasn't ready. Lost it.");
        }
    }

    private static Material cropMaterial(Crop crop) {
        return switch (crop) {
            case WHEAT -> Material.WHEAT;
            case CARROT -> Material.CARROTS;
        };
    }
}
```

- [ ] **Step 4: Wire it into the plugin**

Replace the body of `loftsfarm/src/main/java/place/games/loftsfarm/bukkit/LoftsFarmPlugin.java`:

```java
package place.games.loftsfarm.bukkit;

import org.bukkit.plugin.java.JavaPlugin;

public final class LoftsFarmPlugin extends JavaPlugin {

    private FarmService farms;
    private GrowthScheduler scheduler;

    @Override
    public void onEnable() {
        this.scheduler = new GrowthScheduler(this);
        this.farms = new FarmService(this, scheduler);
        farms.load();
        getServer().getPluginManager().registerEvents(new FarmListener(this, farms), this);
        getLogger().info("LoftsFarm v2 slice enabled. v1 economy untouched.");
    }

    @Override
    public void onDisable() {
        if (scheduler != null) scheduler.cancelAll();
        if (farms != null) farms.save();
        getLogger().info("LoftsFarm v2 slice disabled.");
    }

    public FarmService farms() { return farms; }
    public GrowthScheduler scheduler() { return scheduler; }
}
```

- [ ] **Step 5: Build to verify it compiles**

Run: `bash loftsfarm/build.sh`
Expected: `Built build/LoftsFarm.jar`, no errors.

- [ ] **Step 6: Verify no global plot polling exists**

Run:
```bash
grep -rnE 'runTaskTimer|scheduleSyncRepeating' loftsfarm/src/main/java || echo "no repeating tasks — constraint satisfied"
```
Expected: prints the "no repeating tasks" line. Any repeating task here would violate the spec's hard constraint.

- [ ] **Step 7: Commit**

```bash
git add loftsfarm/src
git commit -m "feat(loftsfarm): plant from hand, per-plant growth scheduling, vanilla growth cancelled"
```

---

### Task 9: Commands — give, balance, sell, and the test farm

**Files:**
- Create: `loftsfarm/src/main/java/place/games/loftsfarm/bukkit/FarmCommand.java`
- Modify: `loftsfarm/src/main/java/place/games/loftsfarm/bukkit/LoftsFarmPlugin.java`

**Interfaces:**
- Consumes: `FarmService`, `Items`, `Crop`, `SeedStack`.
- Produces: `FarmCommand implements CommandExecutor` handling `help`, `give <wheat|carrot|fert>`, `balance`, `sell`, `setup`.

- [ ] **Step 1: Write the command handler**

Create `loftsfarm/src/main/java/place/games/loftsfarm/bukkit/FarmCommand.java`:

```java
package place.games.loftsfarm.bukkit;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import place.games.loftsfarm.domain.Crop;
import place.games.loftsfarm.domain.FarmState;
import place.games.loftsfarm.domain.SeedStack;

public final class FarmCommand implements CommandExecutor {
    private static final String P = "\u00A78[\u00A7c!\u00A78] \u00A77";

    private final Plugin plugin;
    private final FarmService farms;

    public FarmCommand(Plugin plugin, FarmService farms) {
        this.plugin = plugin;
        this.farms = farms;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        String sub = args.length == 0 ? "help" : args[0].toLowerCase();
        FarmState st = farms.state(player.getUniqueId());

        switch (sub) {
            case "give" -> {
                String what = args.length > 1 ? args[1].toLowerCase() : "wheat";
                if (what.startsWith("fert")) {
                    var fert = Items.fertiliser(plugin);
                    fert.setAmount(16);
                    player.getInventory().addItem(fert);
                    player.sendMessage(P + "16 fertiliser.");
                } else {
                    Crop crop = Crop.byId(what);
                    if (crop == null) {
                        player.sendMessage(P + "Unknown crop. Try wheat, carrot, or fert.");
                        return true;
                    }
                    player.getInventory().addItem(Items.seed(plugin, SeedStack.fresh(crop)));
                    player.sendMessage(P + "One " + crop.displayName()
                            + " seed \u2014 " + crop.plantings() + " plantings in it.");
                }
            }
            case "balance" -> player.sendMessage(P + "Balance: \u00A7f$" + st.wallet().balance());
            case "sell" -> {
                long total = 0L;
                for (Crop c : Crop.values()) {
                    int n = st.storage().count(c);
                    if (n > 0 && st.storage().take(c, n)) {
                        long gross = n * c.rawValue();
                        st.wallet().credit(gross);
                        total += gross;
                        player.sendMessage(P + "Sold " + n + " \u00D7 " + c.displayName()
                                + " for \u00A7f$" + gross);
                    }
                }
                if (total == 0L) player.sendMessage(P + "Nothing in farm storage.");
                else player.sendMessage(P + "Total \u00A7f$" + total
                        + "\u00A77. Balance \u00A7f$" + st.wallet().balance());
            }
            case "setup" -> {
                if (!player.isOp()) {
                    player.sendMessage(P + "Operators only.");
                    return true;
                }
                var base = player.getLocation().getBlock();
                int made = 0;
                for (int dx = 0; dx < 4; dx++) {
                    for (int dz = 0; dz < 3; dz++) {
                        var b = base.getRelative(dx + 1, -1, dz);
                        b.setType(Material.FARMLAND);
                        b.getRelative(0, 1, 0).setType(Material.AIR);
                        made++;
                    }
                }
                player.sendMessage(P + "Laid " + made + " farmland blocks. Sow with a seed in hand.");
            }
            default -> {
                player.sendMessage(P + "\u00A7eLoftsFarm slice");
                player.sendMessage(P + "/farm setup \u00A78- lay a test plot (op)");
                player.sendMessage(P + "/farm give <wheat|carrot|fert>");
                player.sendMessage(P + "/farm balance");
                player.sendMessage(P + "/farm sell \u00A78- sell everything in farm storage");
                player.sendMessage(P + "\u00A78Right-click farmland to sow, a ripe crop to harvest.");
            }
        }
        return true;
    }
}
```

- [ ] **Step 2: Register the command**

In `LoftsFarmPlugin.onEnable`, after registering events, add:

```java
        var cmd = getCommand("farm");
        if (cmd != null) cmd.setExecutor(new FarmCommand(this, farms));
```

- [ ] **Step 3: Build**

Run: `bash loftsfarm/build.sh`
Expected: `Built build/LoftsFarm.jar`.

- [ ] **Step 4: Run the domain tests again**

Run: `bash loftsfarm/run-tests.sh`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add loftsfarm/src
git commit -m "feat(loftsfarm): slice commands for seeds, balance, selling and test plot setup"
```

---

### Task 10: The order board GUI

**Files:**
- Create: `loftsfarm/src/main/java/place/games/loftsfarm/bukkit/OrderService.java`
- Create: `loftsfarm/src/main/java/place/games/loftsfarm/bukkit/BoardGui.java`
- Modify: `loftsfarm/src/main/java/place/games/loftsfarm/bukkit/FarmCommand.java`
- Modify: `loftsfarm/src/main/java/place/games/loftsfarm/bukkit/LoftsFarmPlugin.java`

**Interfaces:**
- Consumes: `Order`, `OrderLine`, `Crop`, `Storage`, `FarmService`.
- Produces:
  - `OrderService` with `Order currentFor(UUID)`, `void refreshIfExpired(UUID)`, `boolean deliver(UUID, Player)`.
  - `BoardGui` with `void open(Player)`; it implements `Listener` and handles `InventoryClickEvent` and `InventoryDragEvent`, identifying its own inventory by a private `InventoryHolder` rather than by title string.

- [ ] **Step 1: Write the order service**

Create `loftsfarm/src/main/java/place/games/loftsfarm/bukkit/OrderService.java`:

```java
package place.games.loftsfarm.bukkit;

import org.bukkit.entity.Player;

import place.games.loftsfarm.domain.Crop;
import place.games.loftsfarm.domain.FarmState;
import place.games.loftsfarm.domain.Order;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * One live order per player for the slice.
 *
 * A missed order costs the payout and nothing else. There is deliberately no
 * penalty path in this class (P5) — if a future version wants one, the design
 * has changed and this comment should be deleted on purpose.
 */
public final class OrderService {
    private final FarmService farms;
    private final Random random = new Random();
    private final Map<UUID, Order> live = new HashMap<>();
    private int seq = 0;

    public OrderService(FarmService farms) {
        this.farms = farms;
    }

    public Order currentFor(UUID id) {
        refreshIfExpired(id);
        return live.get(id);
    }

    public void refreshIfExpired(UUID id) {
        Order existing = live.get(id);
        long now = System.currentTimeMillis();
        if (existing != null && !existing.expired(now)) return;
        live.put(id, generate(now));
    }

    private Order generate(long now) {
        Crop crop = Crop.values()[random.nextInt(Crop.values().length)];
        int qty = 3;
        long unit = Math.round(crop.rawValue() * 1.15d);
        // Fuse always clears at least one full grow cycle, or accepting is a trap.
        long due = now + (long) (crop.growSeconds() * 1000L * 1.25d) + 45_000L;
        return Order.single("k" + (++seq), crop, qty, unit, due);
    }

    public boolean deliver(UUID id, Player player) {
        Order order = currentFor(id);
        if (order == null) return false;
        FarmState st = farms.state(id);
        if (!order.deliver(st.storage())) return false;

        st.wallet().credit(order.pay());
        live.remove(id);
        refreshIfExpired(id);
        player.sendMessage("\u00A78[\u00A7c!\u00A78] \u00A77Delivered for \u00A7f$"
                + order.pay() + "\u00A77. Balance \u00A7f$" + st.wallet().balance());
        return true;
    }
}
```

- [ ] **Step 2: Write the board GUI**

Create `loftsfarm/src/main/java/place/games/loftsfarm/bukkit/BoardGui.java`:

```java
package place.games.loftsfarm.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import place.games.loftsfarm.domain.Order;
import place.games.loftsfarm.domain.OrderLine;

import java.util.ArrayList;
import java.util.List;

/**
 * The order board.
 *
 * Redrawn on state change only, never on a schedule. The browser build's worst
 * defect was rebuilding its panels four times a second, which destroyed any
 * click that landed mid-repaint. The same failure mode applies to inventory
 * GUIs and reads to players as lag.
 */
public final class BoardGui implements Listener {
    private static final String TITLE = "\u00A78Order Board";
    private static final int SLOT_ORDER = 11;
    private static final int SLOT_DELIVER = 15;

    private final FarmService farms;
    private final OrderService orders;

    public BoardGui(FarmService farms, OrderService orders) {
        this.farms = farms;
        this.orders = orders;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE);
        render(player, inv);
        player.openInventory(inv);
    }

    private void render(Player player, Inventory inv) {
        inv.clear();
        Order order = orders.currentFor(player.getUniqueId());
        if (order == null) return;

        var storage = farms.state(player.getUniqueId()).storage();
        long secondsLeft = Math.max(0L, (order.dueAt() - System.currentTimeMillis()) / 1000L);

        OrderLine line = order.lines().get(0);
        int have = order.heldFor(storage, 0);

        List<String> lore = new ArrayList<>();
        lore.add("\u00A77Wants \u00A7f" + line.qty() + " \u00D7 " + line.crop().displayName());
        lore.add("\u00A77Pays \u00A7a$" + order.pay());
        lore.add("\u00A77You hold " + (have >= line.qty() ? "\u00A7a" : "\u00A7c")
                 + have + "\u00A77 of \u00A7f" + line.qty());
        lore.add("\u00A78" + secondsLeft + "s left");
        lore.add("");
        lore.add("\u00A78Miss it and you simply are not paid.");
        lore.add("\u00A78Nothing is ever taken from you.");

        inv.setItem(SLOT_ORDER, icon(Material.PAPER, "\u00A7eOrder \u00A78" + order.id(), lore));

        boolean ready = order.satisfiedBy(storage);
        inv.setItem(SLOT_DELIVER, icon(
                ready ? Material.LIME_DYE : Material.GRAY_DYE,
                ready ? "\u00A7aDeliver now" : "\u00A77Not enough yet",
                List.of(ready ? "\u00A78Click to hand it over"
                              : "\u00A78Harvest more, then come back")));
    }

    private static ItemStack icon(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!TITLE.equals(event.getView().getTitle())) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getRawSlot() != SLOT_DELIVER) return;

        if (orders.deliver(player.getUniqueId(), player)) {
            // State changed, so redraw once. Not on a timer.
            render(player, event.getInventory());
        } else {
            player.sendMessage("\u00A78[\u00A7c!\u00A78] \u00A77Not enough in farm storage yet.");
        }
    }
}
```

- [ ] **Step 3: Add the board command**

In `FarmCommand.onCommand`, add a case before `default`:

```java
            case "board" -> board.open(player);
```

And add the field and constructor parameter:

```java
    private final BoardGui board;
```

Change the constructor to:

```java
    public FarmCommand(Plugin plugin, FarmService farms, BoardGui board) {
        this.plugin = plugin;
        this.farms = farms;
        this.board = board;
    }
```

Add to the help output, after the `/farm sell` line:

```java
                player.sendMessage(P + "/farm board \u00A78- the order board");
```

- [ ] **Step 4: Wire the board into the plugin**

Replace the wiring block in `LoftsFarmPlugin.onEnable`:

```java
        OrderService orders = new OrderService(farms);
        BoardGui board = new BoardGui(farms, orders);
        getServer().getPluginManager().registerEvents(new FarmListener(this, farms), this);
        getServer().getPluginManager().registerEvents(board, this);
        var cmd = getCommand("farm");
        if (cmd != null) cmd.setExecutor(new FarmCommand(this, farms, board));
```

- [ ] **Step 5: Build**

Run: `bash loftsfarm/build.sh`
Expected: `Built build/LoftsFarm.jar`.

- [ ] **Step 6: Verify no scheduled GUI repaint exists**

Run:
```bash
grep -rn 'render(' loftsfarm/src/main/java/place/games/loftsfarm/bukkit/BoardGui.java
```
Expected: `render` is called only from `open` and from the click handler — never from a scheduler.

- [ ] **Step 7: Commit**

```bash
git add loftsfarm/src
git commit -m "feat(loftsfarm): order board GUI with state-change-only redraw and no-penalty delivery"
```

---

### Task 11: In-server acceptance run

**Files:**
- Create: `loftsfarm/ACCEPTANCE.md`
- Create: `loftsfarm/testserver/README.md`

**Interfaces:**
- Consumes: the built `build/LoftsFarm.jar`.
- Produces: a documented manual test pass. Nothing else depends on this task.

- [ ] **Step 1: Write the acceptance script**

Create `loftsfarm/ACCEPTANCE.md`:

```markdown
# LoftsFarm slice — acceptance run

Drop `build/LoftsFarm.jar` into a Paper 1.21.10 test server's `plugins/` folder.
Use a superflat world. Do NOT use the live lofts-dev server — v2 is isolated by
design and this is a throwaway.

1. `/farm setup` — lays 12 farmland blocks beside you.
2. `/farm give wheat` — one seed, 8 plantings, showing a damage bar.
3. Right-click farmland with it. A wheat crop appears at stage 0.
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
13. Plant a crop, then `/stop` the server. Wait past its grow time. Restart.
    PASS: the crop is ripe on rejoin. Offline growth works because ripeness is
    derived from a timestamp, not from ticks.
14. Plant a crop, walk far away so the chunk unloads, wait, come back.
    PASS: correct stage on return.

## Known deviations from the spec

- Persistence is a flat text file, not SQLite (§6). No JDBC driver is available
  and no downloads were permitted. `StateCodec` is the seam; swapping it is a
  one-class change.
- No mill, market GUI, merchant NPC, levels, plot purchase, combined orders or
  milled goods. Those are out of scope for the slice by decision.
- Only wheat and carrot. Both use `Ageable` blocks, so stage interpolation is
  proven; sugar cane and pumpkin remain the unproven cases flagged in the spec.
```

- [ ] **Step 2: Write the test server note**

Create `loftsfarm/testserver/README.md`:

```markdown
# Test server

The slice needs its own Paper 1.21.10 server with a superflat world. Do not
install it into `lofts-dev` — that carries Lofts v1 and a live Tebex secret,
and v2 is designed to be isolated from both.

Minimum setup:

1. A folder with `paper-1.21.10.jar`.
2. `eula.txt` containing `eula=true`.
3. `server.properties` with `level-type=flat` and `online-mode=false` for local testing.
4. `plugins/LoftsFarm.jar` from `../build/`.

Then follow `../ACCEPTANCE.md`.
```

- [ ] **Step 3: Run the full test suite one final time**

Run: `bash loftsfarm/run-tests.sh`
Expected: PASS, exit 0.

- [ ] **Step 4: Build the final jar**

Run: `bash loftsfarm/build.sh`
Expected: `Built build/LoftsFarm.jar`.

- [ ] **Step 5: Commit**

```bash
git add loftsfarm/ACCEPTANCE.md loftsfarm/testserver/README.md
git commit -m "docs(loftsfarm): acceptance run and test server setup"
```

---

## Self-review notes

**Spec coverage.** Slice-scoped requirements map to tasks: wall-clock growth and stages → 2, 8. Seed durability with damage bar → 4, 7. Plant from hand → 8. Virtual storage → 3, 8. Fertiliser halving and pricing → 5, 8. One order, manual delivery, no penalty → 5, 10. Internal currency with no-negative invariant → 3. Cancel `BlockGrowEvent` → 8. One task per plant, no polling → 8 (verified by grep in Step 6). GUI redraw on state change only → 10 (verified in Step 6). Offline growth → 6, 11 (step 13).

**Deliberately deferred, and recorded in `ACCEPTANCE.md`:** SQLite (flat file instead — no JDBC driver, no downloads), mill and milled orders, market and merchant GUIs, levels, plot purchase, combined orders, sugar cane and pumpkin stage handling, the four upper crops.

**Type consistency.** `GrowthClock.stage` takes `(Crop, long)` in both its definition (Task 2 Step 5) and every call site (Task 2 Step 6, Task 8 `GrowthScheduler.paintStage`). `Storage.take` returns `boolean` and is consumed as such in `Order.deliver` and `FarmCommand.sell`. `SeedStack` is immutable throughout — `usedOnce()` returns a new instance and every call site assigns the result.
