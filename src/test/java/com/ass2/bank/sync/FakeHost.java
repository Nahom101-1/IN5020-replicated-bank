package com.ass2.bank.sync;

import com.ass2.common.Transaction;

import java.util.ArrayList;
import java.util.List;

/**
 * A single replica without RMI, for testing. It only knows NOK deposits, and it delivers
 * transactions the way the bank core will: markers go to the SyncedBalance, everything else is
 * applied and removed from outstanding_collection.
 */
final class FakeHost implements SyncHost {

    private final String name;
    private final List<Transaction> outstanding = new ArrayList<>();
    private int outstandingCounter = 0;
    private double nok = 0.0;

    SyncedBalance sync;

    FakeHost(String name) {
        this.name = name;
    }

    /** Issues "deposit NOK <amount>" locally, like the deposit command does. */
    Transaction deposit(double amount) {
        Transaction tx = new Transaction("deposit NOK " + amount, nextUniqueId());
        addOutstanding(tx);
        return tx;
    }

    /** What the 10 s timer broadcasts: a copy of outstanding_collection. */
    synchronized List<Transaction> broadcastSnapshot() {
        return new ArrayList<>(outstanding);
    }

    /** What deliver() does with a totally ordered list. */
    void deliver(List<Transaction> txs) {
        for (Transaction tx : txs) {
            if (SyncMarker.isMarker(tx)) {
                sync.onDelivered(tx);
                continue;
            }
            String[] words = tx.command().split(" ");
            synchronized (this) {
                nok += Double.parseDouble(words[2]);
            }
            removeOutstanding(tx);
        }
        sync.afterDelivery();
    }

    @Override
    public String instanceName() {
        return name;
    }

    synchronized String nextUniqueId() {
        return name + " " + outstandingCounter++;
    }

    @Override
    public synchronized void addOutstanding(Transaction tx) {
        outstanding.add(tx);
    }

    @Override
    public synchronized void removeOutstanding(Transaction tx) {
        outstanding.remove(tx);
    }

    @Override
    public synchronized boolean outstandingIsEmpty() {
        return outstanding.isEmpty();
    }

    @Override
    public synchronized double balanceIn(String currency) {
        if (!currency.equals("NOK")) {
            throw new IllegalArgumentException("Unknown currency " + currency);
        }
        return nok;
    }
}
