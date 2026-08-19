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
