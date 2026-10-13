package com.ass2.bank.sync;

import com.ass2.common.Transaction;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyncedBalanceTest {

    private final List<String> output = new ArrayList<>();
    private final FakeHost host = new FakeHost("bank1");

    private SyncedBalance use(String mode) {
        host.sync = SyncedBalance.create(mode, host, output::add);
        return host.sync;
    }

    /** One broadcast round: send outstanding_collection and deliver it back. */
    private void broadcastRound() {
        host.deliver(host.broadcastSnapshot());
    }

    private void assertAnswered(String balance) {
        assertEquals(1, output.size(), "answers: " + output);
        assertTrue(output.get(0).startsWith("getSyncedBalance NOK: " + balance + " "), output.get(0));
    }

    // ---- naive ----

    @Test
    void naiveAnswersAtOnceWhenNothingIsOutstanding() {
        use("naive").request("NOK");
        assertAnswered("0.00");
    }

    @Test
    void naiveAnswersAfterOwnTransactionsAreApplied() {
        SyncedBalance sync = use("naive");
        host.deposit(100);
        sync.request("NOK");
        assertTrue(output.isEmpty());

        broadcastRound();

        assertAnswered("100.00");
    }

    @Test
    void naiveNeverAnswersWhileNewTransactionsKeepArriving() {
        SyncedBalance sync = use("naive");
        host.deposit(100);
        sync.request("NOK");

        // Each round, a new deposit is issued after the snapshot is sent but before it is
        // delivered, so outstanding_collection is never empty after a delivery.
        for (int round = 0; round < 20; round++) {
            List<Transaction> sent = host.broadcastSnapshot();
            host.deposit(10);
            host.deliver(sent);
        }
        assertTrue(output.isEmpty(), "answers: " + output);

        sync.close();
        assertEquals(1, output.size());
        assertTrue(output.get(0).contains("never answered"), output.get(0));
    }

    @Test
    void naiveAnswerIncludesDepositsIssuedAfterTheRequest() {
        SyncedBalance sync = use("naive");
        host.deposit(100);
        sync.request("NOK");
        host.deposit(50); // issued after the request

        broadcastRound();

        assertAnswered("150.00"); // not 100
    }

    // ---- ordered ----

    @Test
    void orderedAnswersWithBalanceAtTheTimeOfTheRequest() {
        SyncedBalance sync = use("ordered");
        host.deposit(100);
        sync.request("NOK");
        host.deposit(50); // issued after the request

        broadcastRound();

        assertAnswered("100.00");
        assertTrue(host.outstandingIsEmpty());
    }

    @Test
    void orderedAnswersWithinOneRoundWhileNewTransactionsKeepArriving() {
        SyncedBalance sync = use("ordered");
        host.deposit(100);
        sync.request("NOK");

        List<Transaction> sent = host.broadcastSnapshot();
        host.deposit(10);
        host.deliver(sent);

        assertAnswered("100.00");
    }

    @Test
    void orderedWaitsForTheMarkerToBeDelivered() {
        SyncedBalance sync = use("ordered");
        sync.request("NOK");
        // Nothing has been broadcast yet, so there is no answer even though the account is idle.
        assertTrue(output.isEmpty());

        broadcastRound();

        assertAnswered("0.00");
    }

    @Test
    void orderedIgnoresMarkersFromOtherReplicas() {
        use("ordered");
        host.deliver(List.of(SyncMarker.create("NOK", "bank2 0")));
        assertTrue(output.isEmpty());
    }

    @Test
    void orderedAnswersOnceWhenTheMarkerIsDeliveredTwice() {
        SyncedBalance sync = use("ordered");
        sync.request("NOK");
        List<Transaction> sent = host.broadcastSnapshot();

        host.deliver(sent);
        host.deliver(sent); // resent by the delivery server

        assertAnswered("0.00");
    }

    @Test
    void orderedReportsUnknownCurrencyWhenAnswering() {
        SyncedBalance sync = use("ordered");
        sync.request("XYZ");
        broadcastRound();

        assertEquals(List.of("getSyncedBalance XYZ: Unknown currency XYZ"), output);
    }

    @Test
    void orderedReportsUndeliveredMarkersOnClose() {
        SyncedBalance sync = use("ordered");
        sync.request("NOK");
        sync.close();

        assertEquals(1, output.size());
        assertTrue(output.get(0).contains("never answered"), output.get(0));
    }

    // ---- factory and markers ----

    @Test
    void createRejectsUnknownMode() {
        assertThrows(IllegalArgumentException.class, () -> use("fast"));
    }

    @Test
    void createReturnsRequestedMode() {
        assertEquals("naive", use("naive").mode());
        assertEquals("ordered", use("ordered").mode());
    }

    @Test
    void markerHelpers() {
        Transaction marker = SyncMarker.create("EUR", "bank1 7");

        assertTrue(SyncMarker.isMarker(marker));
        assertFalse(SyncMarker.isMarker(new Transaction("deposit NOK 5", "bank1 8")));
        assertEquals("EUR", SyncMarker.currencyOf(marker));
        assertTrue(SyncMarker.isIssuedBy(marker, "bank1"));
        assertFalse(SyncMarker.isIssuedBy(SyncMarker.create("EUR", "bank10 7"), "bank1"));
    }
}
