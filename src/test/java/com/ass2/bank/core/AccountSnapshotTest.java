package com.ass2.bank.core;

import com.ass2.common.ReplicaState;
import com.ass2.common.Transaction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountSnapshotTest {

    private static final double DELTA = 1e-9;
    private static final Map<String, Double> RATES = Map.of("USD", 1.0, "NOK", 0.1);

    /** An account that has applied two transactions. */
    private Account accountWithHistory() {
        Account account = new Account("bank1", RATES);
        account.issue("deposit NOK 100");
        account.issue("addInterest 10");
        account.deliver(account.outstanding());
        return account;
    }

    @Test
    void restoredAccountHasSameBalanceAndHistory() {
        Account existing = accountWithHistory();
        Account joined = new Account("bank2", RATES);

        joined.restore(existing.snapshot());

        assertEquals(existing.balanceIn("NOK"), joined.balanceIn("NOK"), DELTA);
        assertEquals(existing.orderCounter(), joined.orderCounter());
        assertEquals(existing.executed(), joined.executed());
        assertTrue(joined.isApplied("bank1 0"));
        assertTrue(joined.isApplied("bank1 1"));
    }

    @Test
    void restoreKeepsOwnOutstandingAndCounter() {
        Account existing = accountWithHistory();
        Account joined = new Account("bank2", RATES);
        joined.issue("deposit NOK 5");

        joined.restore(existing.snapshot());

        assertEquals(1, joined.outstanding().size());
        assertEquals("bank2 1", joined.issue("deposit NOK 5").uniqueId());
    }

    @Test
    void alreadyAppliedTransactionIsNotAppliedAgainAfterRestore() {
        Account existing = accountWithHistory();
        Account joined = new Account("bank2", RATES);
        joined.restore(existing.snapshot());

        joined.deliver(List.of(new Transaction("deposit NOK 100", "bank1 0")));

        assertEquals(existing.balanceIn("NOK"), joined.balanceIn("NOK"), DELTA);
        assertEquals(2, joined.orderCounter());
    }

    @Test
    void snapshotIsNotChangedByLaterDeliveries() {
        Account account = accountWithHistory();
        ReplicaState snapshot = account.snapshot();

        account.issue("deposit NOK 50");
        account.deliver(account.outstanding());

        assertEquals(2, snapshot.orderCounter());
        assertEquals(2, snapshot.executed().size());
    }
}
