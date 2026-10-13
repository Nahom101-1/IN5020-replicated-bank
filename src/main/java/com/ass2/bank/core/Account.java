package com.ass2.bank.core;

import com.ass2.common.Transaction;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** One replica of the bank account. Thread-safe. */
public class Account {

    /** A transaction that has been applied, with the time it was applied. */
    public record Executed(Transaction transaction, LocalDateTime executedAt) {}

    private final String replicaName;
    private final Balances balances = new Balances();

    private final List<Transaction> outstanding = new ArrayList<>();
    private final List<Executed> executed = new ArrayList<>();
    private final Set<String> appliedIds = new HashSet<>();

    private int outstandingCounter = 0;
    private int orderCounter = 0;

    public Account(String replicaName) {
        this.replicaName = replicaName;
    }

    /** Validates a deposit/addInterest command and queues it for broadcast. */
    public synchronized Transaction issue(String command) {
        Operation.parse(command); // throws if invalid, so nothing bad is broadcast

        String uniqueId = replicaName + " " + outstandingCounter;
        Transaction transaction = new Transaction(command.trim(), uniqueId);

        outstanding.add(transaction);
        outstandingCounter++;
        return transaction;
    }

    /** Applies transactions in the order the delivery server decided. */
    public synchronized void deliver(List<Transaction> orderedTransactions) {
        for (Transaction transaction : orderedTransactions) {
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

    private void removeFromOutstanding(String uniqueId) {
        for (int i = 0; i < outstanding.size(); i++) {
            if (outstanding.get(i).uniqueId().equals(uniqueId)) {
                outstanding.remove(i);
                return;
            }
        }
    }
}