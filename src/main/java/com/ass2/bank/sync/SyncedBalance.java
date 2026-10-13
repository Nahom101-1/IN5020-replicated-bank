package com.ass2.bank.sync;

import com.ass2.common.Transaction;

import java.util.function.Consumer;

/**
 * The getSyncedBalance command. There are two implementations, chosen with -Dsync=naive|ordered:
 *
 * naive   - waits until outstanding_collection is empty, then prints the balance. This can wait
 *           forever while new deposit/addInterest commands keep arriving, and when it does
 *           answer, the balance may include transactions issued after the request.
 * ordered - (default) puts a marker in outstanding_collection. When the marker comes back through
 *           the total order, exactly the transactions ordered before it have been applied, so the
 *           issuing replica prints the balance at that point. It answers within one broadcast round.
 *
 * How the bank server uses it:
 *  - The command line calls {@link #request} for "getSyncedBalance <Currency>". It returns at
 *    once and the answer is printed later, so the command line keeps accepting commands.
 *  - The deliver() handler checks {@link SyncMarker#isMarker} for every delivered transaction.
 *    For a marker it only calls {@link #onDelivered}: it does not apply it, does not increase
 *    order_counter and does not add it to executed_list.
 *  - After applying each delivered list, deliver() calls {@link #afterDelivery}.
 *  - On exit the bank server calls {@link #close}.
 */
public interface SyncedBalance {

    /** The system property that selects the implementation, e.g. java -Dsync=naive -jar bank.jar ... */
    String MODE_PROPERTY = "sync";

    /** Starts a getSyncedBalance request. Never blocks; the answer is printed when it is ready. */
    void request(String currency);

    /** Called by deliver() for every delivered marker, in the delivered order, instead of applying it. */
    void onDelivered(Transaction marker);

    /** Called by deliver() after a delivered list of transactions has been applied. */
    void afterDelivery();

    /** Prints the requests that never got an answer. Called when the bank server exits. */
    void close();

    /** Returns "naive" or "ordered". */
    String mode();

    /**
     * Creates the implementation for the mode, "naive" or "ordered".
     * Usage: SyncedBalance.create(System.getProperty(MODE_PROPERTY, "ordered"), host, Log::info)
     *
     * @param out where the answers are printed, one line each
     */
    static SyncedBalance create(String mode, SyncHost host, Consumer<String> out) {
        return switch (mode) {
            case NaiveSyncedBalance.MODE -> new NaiveSyncedBalance(host, out);
            case OrderedSyncedBalance.MODE -> new OrderedSyncedBalance(host, out);
            default -> throw new IllegalArgumentException(
                    "Unknown getSyncedBalance mode \"" + mode + "\", use naive or ordered");
        };
    }
}
