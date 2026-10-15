package com.ass2.common;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * State sent to a replica that joins an existing group.
 * Outstanding transactions are not included: they belong to the replica that issued them.
 */
public record ReplicaState(
        Map<String, Double> balances,
        List<ExecutedTransaction> executed,
        Set<String> appliedIds,
        int orderCounter) implements Serializable {

    public ReplicaState {
        balances = Map.copyOf(balances);
        executed = List.copyOf(executed);
        appliedIds = Set.copyOf(appliedIds);
        if (orderCounter < 0) {
            throw new IllegalArgumentException("orderCounter cannot be negative");
        }
    }

    /** An applied transaction and the time it was applied. */
    public record ExecutedTransaction(Transaction transaction, LocalDateTime executedAt)
            implements Serializable {
    }
}
