package com.ass2.common;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Snapshot transferred when a replica joins an existing group. */
public record ReplicaState(
        Map<String, Double> balances,
        List<Transaction> outstanding,
        List<ExecutedTransaction> executed,
        Set<String> appliedIds,
        int orderCounter,
        int outstandingCounter) implements Serializable {

    public ReplicaState {
        balances = Map.copyOf(balances);
        outstanding = List.copyOf(outstanding);
        executed = List.copyOf(executed);
        appliedIds = Set.copyOf(appliedIds);
        if (orderCounter < 0 || outstandingCounter < 0) {
            throw new IllegalArgumentException("Snapshot counters cannot be negative");
        }
    }

    public record ExecutedTransaction(Transaction transaction, long executedAtEpochMillis)
            implements Serializable {
    }
}