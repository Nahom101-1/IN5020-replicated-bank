package com.ass2.bank.core;

import com.ass2.bank.sync.SyncHost;
import com.ass2.bank.sync.SyncMarker;
import com.ass2.bank.sync.SyncedBalance;
import com.ass2.common.Transaction;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** One replica of the bank account. Thread-safe. */
public class Account implements SyncHost {

    /** A transaction that has been applied, with the time it was applied. */
    public record Executed(Transaction transaction, LocalDateTime executedAt) {}

    private final String replicaName;
    private final Map<String, Double> ratesToUsd; // 1 unit of the currency = this many USD
    private final Balances balances = new Balances();

    private final List<Transaction> outstanding = new ArrayList<>();
    private final List<Executed> executed = new ArrayList<>();
    private final Set<String> appliedIds = new HashSet<>();

    private int outstandingCounter = 0;
    private int orderCounter = 0;

    /** Handles getSyncedBalance markers; null until {@link #useSyncedBalance} is called. */
    private SyncedBalance syncedBalance;

    /** An account without currency rates, so {@link #balanceIn} cannot convert. */
    public Account(String replicaName) {
        this(replicaName, Map.of());
    }

    /** @param ratesToUsd the rates from the currency file, e.g. NOK -> 0.1. USD is always 1.0. */
    public Account(String replicaName, Map<String, Double> ratesToUsd) {
        this.replicaName = replicaName;
        this.ratesToUsd = Map.copyOf(ratesToUsd);
    }

    /** Sets the getSyncedBalance implementation that delivered markers are handed to. */
    public synchronized void useSyncedBalance(SyncedBalance syncedBalance) {
        this.syncedBalance = syncedBalance;
    }

    /** Validates a deposit/addInterest command and queues it for broadcast. */
    public synchronized Transaction issue(String command) {
        Operation.parse(command); // throws if invalid, so nothing bad is broadcast

        Transaction transaction = new Transaction(command.trim(), nextUniqueId());
        addOutstanding(transaction);
        return transaction;
    }

    /** Applies transactions in the order the delivery server decided. */
    public synchronized void deliver(List<Transaction> orderedTransactions) {
        for (Transaction transaction : orderedTransactions) {
            // A getSyncedBalance marker does not change the account: it is not applied, not
            // counted in order_counter and not added to the history, on any replica. Only the
            // replica that issued it prints the balance, which the SyncedBalance takes care of
            // (including ignoring a marker that is delivered twice).
            if (SyncMarker.isMarker(transaction)) {
                if (syncedBalance != null) {
                    syncedBalance.onDelivered(transaction);
                }
                continue;
            }
            if (appliedIds.contains(transaction.uniqueId())) {
                continue; // already applied: a resend or a rebroadcast
            }
            try {
                Operation.parse(transaction.command()).applyTo(balances);
            } catch (IllegalArgumentException e) {
                System.out.println("Skipped invalid transaction " + transaction.uniqueId());
            }
            appliedIds.add(transaction.uniqueId());
            orderCounter++;
            executed.add(new Executed(transaction, LocalDateTime.now()));
            removeFromOutstanding(transaction.uniqueId());
        }
        // The naive getSyncedBalance checks here whether outstanding_collection is now empty.
        if (syncedBalance != null) {
            syncedBalance.afterDelivery();
        }
    }

    /** Copy of the transactions not yet applied, for the 10s broadcast. */
    public synchronized List<Transaction> outstanding() {
        return new ArrayList<>(outstanding);
    }

    /** Copy of the applied transactions since the last cleanHistory. */
    public synchronized List<Executed> executed() {
        return new ArrayList<>(executed);
    }

    /** True if the transaction has been applied. */
    public synchronized boolean isApplied(String uniqueId) {
        return appliedIds.contains(uniqueId);
    }

    /** Empties the history. Counters and applied ids are kept. */
    public synchronized void cleanHistory() {
        executed.clear();
    }

    /** Amount held in one currency, without conversion. */
    public synchronized double amountIn(String currency) {
        return balances.amountIn(currency);
    }

    /** Number of transactions applied so far. */
    public synchronized int orderCounter() {
        return orderCounter;
    }

    // ---- SyncHost: what getSyncedBalance needs ----

    @Override
    public String instanceName() {
        return replicaName;
    }

    /** Returns "<replica name> <outstanding_counter>" and increases outstanding_counter. */
    @Override
    public synchronized String nextUniqueId() {
        return replicaName + " " + outstandingCounter++;
    }

    @Override
    public synchronized void addOutstanding(Transaction transaction) {
        outstanding.add(transaction);
    }

    @Override
    public synchronized void removeOutstanding(Transaction transaction) {
        removeFromOutstanding(transaction.uniqueId());
    }

    @Override
    public synchronized boolean outstandingIsEmpty() {
        return outstanding.isEmpty();
    }

    /**
     * The whole account converted to one currency: every amount is converted to USD and the sum
     * is converted to the requested currency. Used by getQuickBalance and getSyncedBalance.
     */
    @Override
    public synchronized double balanceIn(String currency) {
        double totalUsd = 0.0;
        for (Map.Entry<String, Double> entry : balances.snapshot().entrySet()) {
            totalUsd += entry.getValue() * rateToUsd(entry.getKey());
        }
        return totalUsd / rateToUsd(currency);
    }

    private double rateToUsd(String currency) {
        Double rate = ratesToUsd.get(currency);
        if (rate == null && currency.equals("USD")) {
            return 1.0; // the currency file lists rates to USD, so USD itself is not in it
        }
        if (rate == null) {
            throw new IllegalArgumentException("Unknown currency " + currency);
        }
        return rate;
    }

    private void removeFromOutstanding(String uniqueId) {
        for (int i = 0; i < outstanding.size(); i++) {
            if (outstanding.get(i).uniqueId().equals(uniqueId)) {
                outstanding.remove(i);
                return;
            }
        }
    }
}
