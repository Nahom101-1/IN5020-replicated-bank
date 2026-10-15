package com.ass2.bank.core;

import com.ass2.bank.sync.SyncedBalance;
import com.ass2.common.Transaction;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Account together with the real getSyncedBalance implementations. */
class AccountSyncTest {

    private static final double DELTA = 1e-9;
    private static final Map<String, Double> RATES = Map.of("USD", 1.0, "NOK", 0.1, "EUR", 1.2);

    private final List<String> output = new ArrayList<>();

    /** Creates the getSyncedBalance implementation and connects it to the account. */
    private SyncedBalance attach(Account account, String mode) {
        SyncedBalance sync = SyncedBalance.create(mode, account, output::add);
        account.useSyncedBalance(sync);
        return sync;
    }

    @Test
    void markerIsNotCountedOrAddedToHistory() {
        Account account = new Account("bank1", RATES);
        SyncedBalance sync = attach(account, "ordered");
        account.issue("deposit NOK 100");
        sync.request("NOK");

        account.deliver(account.outstanding());

        assertEquals(1, account.orderCounter());
        assertEquals(1, account.executed().size());
        assertTrue(account.outstandingIsEmpty());
        assertEquals(List.of("getSyncedBalance NOK: 100.00"), stripWaitTime(output));
    }

    @Test
    void allReplicasKeepTheSameCountersWhenAMarkerIsDelivered() {
        Account issuer = new Account("bank1", RATES);
        Account other = new Account("bank2", RATES);
        SyncedBalance sync = attach(issuer, "ordered");
        attach(other, "ordered");

        issuer.issue("deposit NOK 100");
        sync.request("NOK");
        issuer.issue("deposit NOK 50");
        List<Transaction> ordered = issuer.outstanding();

        issuer.deliver(ordered);
        other.deliver(ordered);

        assertEquals(issuer.orderCounter(), other.orderCounter());
        assertEquals(issuer.executed().size(), other.executed().size());
        assertEquals(issuer.balanceIn("NOK"), other.balanceIn("NOK"), DELTA);
        // Only the issuer answers, with the balance at the time of the request.
        assertEquals(List.of("getSyncedBalance NOK: 100.00"), stripWaitTime(output));
    }

    @Test
    void markerDoesNotUseTheDepositCounter() {
        // checkTxStatus 0 in input/Rep1.txt refers to the first deposit, even though a
        // getSyncedBalance comes before it.
        Account account = new Account("bank1", RATES);
        SyncedBalance sync = attach(account, "ordered");

        sync.request("USD");
        Transaction first = account.issue("deposit USD 90");
        Transaction second = account.issue("deposit NOK 120");

        assertEquals("bank1 0", first.uniqueId());
        assertEquals("bank1 1", second.uniqueId());
    }

    @Test
    void naiveAnswersAfterDelivery() {
        Account account = new Account("bank1", RATES);
        SyncedBalance sync = attach(account, "naive");

        account.issue("deposit NOK 100");
        sync.request("NOK");
        assertTrue(output.isEmpty());

        account.deliver(account.outstanding());

        assertEquals(List.of("getSyncedBalance NOK: 100.00"), stripWaitTime(output));
    }

    @Test
    void balanceIsConvertedThroughUsd() {
        Account account = new Account("bank1", RATES);
        account.deliver(List.of(
                new Transaction("deposit NOK 100", "bank2 0"),   // 10 USD
                new Transaction("deposit EUR 10", "bank2 1")));  // 12 USD

        assertEquals(22.0, account.balanceIn("USD"), DELTA);
        assertEquals(220.0, account.balanceIn("NOK"), DELTA);
    }

    @Test
    void usdWorksWithoutBeingInTheCurrencyFile() {
        // Same rates as input/TradingRate.txt, which has no USD line.
        Account account = new Account("bank1", Map.of("EUR", 1.12, "NOK", 0.10));
        account.deliver(List.of(
                new Transaction("deposit USD 90", "bank2 0"),
                new Transaction("deposit NOK 120", "bank2 1")));   // 12 USD

        assertEquals(102.0, account.balanceIn("USD"), DELTA);
        assertEquals(102.0 / 1.12, account.balanceIn("EUR"), DELTA);
    }

    @Test
    void unknownCurrencyIsRejected() {
        Account account = new Account("bank1", RATES);
        assertThrows(IllegalArgumentException.class, () -> account.balanceIn("XYZ"));
    }

    /** Removes the " (mode, waited x s)" part, which depends on timing. */
    private static List<String> stripWaitTime(List<String> lines) {
        return lines.stream().map(line -> line.replaceAll(" \\(.*\\)$", "")).toList();
    }
}
