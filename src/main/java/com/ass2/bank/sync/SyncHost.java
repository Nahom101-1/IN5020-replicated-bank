package com.ass2.bank.sync;

import com.ass2.common.Transaction;

/**
 * What the getSyncedBalance implementations need from the bank server. The bank core implements it.
 *
 * All methods may be called from the command line thread and from the RMI delivery thread, so they
 * must be thread-safe. The strategies never hold a lock of their own while calling these methods,
 * so the core can use any locking it likes without risking a deadlock. The core may also call
 * {@link SyncedBalance#onDelivered} and {@link SyncedBalance#afterDelivery} while holding its own
 * lock, as long as that lock is reentrant (a synchronized block or ReentrantLock is fine).
 */
public interface SyncHost {

    /** The name of this bank server instance, the first part of every uniqueId it creates. */
    String instanceName();

    /** Appends the transaction to outstanding_collection, so the next broadcast sends it. */
    void addOutstanding(Transaction tx);

    /** Removes the transaction from outstanding_collection. Does nothing if it is not there. */
    void removeOutstanding(Transaction tx);

    /** Returns true if outstanding_collection is empty. */
    boolean outstandingIsEmpty();

    /**
     * Returns the balance of the whole account converted to the currency, the same value
     * getQuickBalance prints. Throws IllegalArgumentException for an unknown currency.
     */
    double balanceIn(String currency);
}
