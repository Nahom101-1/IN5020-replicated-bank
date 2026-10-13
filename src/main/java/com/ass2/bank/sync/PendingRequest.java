package com.ass2.bank.sync;

import java.util.Locale;
import java.util.function.Consumer;

/** A getSyncedBalance request that has not been answered yet. */
record PendingRequest(String currency, long issuedAtNanos) {

    /** Creates a request issued now. */
    static PendingRequest now(String currency) {
        return new PendingRequest(currency, System.nanoTime());
    }

    /** Prints the current balance and how long the request waited. */
    void answer(SyncHost host, Consumer<String> out, String mode) {
        try {
            double balance = host.balanceIn(currency);
            out.accept(String.format(Locale.ROOT, "getSyncedBalance %s: %.2f (%s, waited %.1f s)",
                    currency, balance, mode, secondsWaited()));
        } catch (IllegalArgumentException e) {
            out.accept("getSyncedBalance " + currency + ": " + e.getMessage());
        }
    }

    /** Prints that the request was never answered. */
    void reportUnanswered(Consumer<String> out, String mode) {
        out.accept(String.format(Locale.ROOT, "getSyncedBalance %s: never answered (%s, waited %.1f s)",
                currency, mode, secondsWaited()));
    }

    private double secondsWaited() {
        return (System.nanoTime() - issuedAtNanos) / 1e9;
    }
}
