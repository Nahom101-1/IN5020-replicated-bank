package com.ass2.bank.sync;

import com.ass2.common.Transaction;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/**
 * Naive getSyncedBalance: answer only when outstanding_collection is empty.
 *
 * The idea is that an empty outstanding_collection means every transaction this server issued
 * has been applied. The problem is that the condition looks at the whole collection, not only at
 * the transactions issued before the request. outstanding_collection is broadcast every 10 s, and
 * in batch mode a new command arrives every 0.5-1.5 s, so by the time one round is delivered the
 * collection already holds new transactions. It may never be empty, and the request is never
 * answered. If it does get answered, the balance can include deposits issued after the request.
 *
 * The request does not block a thread. It is put in a waiting queue, and the condition is checked
 * when the request is made and after every delivery, the only time outstanding_collection shrinks.
 * Blocking the command line instead would hide the problem: no new commands could be added, so the
 * collection would always empty within one round.
 */
public final class NaiveSyncedBalance implements SyncedBalance {

    static final String MODE = "naive";

    private final SyncHost host;
    private final Consumer<String> out;

    /** Requests waiting for outstanding_collection to become empty, oldest first. */
    private final Queue<PendingRequest> waiting = new ConcurrentLinkedQueue<>();

    NaiveSyncedBalance(SyncHost host, Consumer<String> out) {
        this.host = host;
        this.out = out;
    }

    @Override
    public void request(String currency) {
        waiting.add(PendingRequest.now(currency));
        // The collection may already be empty, in which case the answer is printed right away.
        answerIfOutstandingEmpty();
    }

    @Override
    public void onDelivered(Transaction marker) {
        // The naive version never sends markers. Markers from replicas running the ordered
        // version need nothing from us.
    }

    @Override
    public void afterDelivery() {
        answerIfOutstandingEmpty();
    }

    @Override
    public void close() {
        PendingRequest request;
        while ((request = waiting.poll()) != null) {
            request.reportUnanswered(out, MODE);
        }
    }

    @Override
    public String mode() {
        return MODE;
    }

    /**
     * Answers every waiting request if outstanding_collection is empty. Called from both the
     * command line and the delivery thread; poll() hands each request to exactly one of them.
     */
    private void answerIfOutstandingEmpty() {
        if (!host.outstandingIsEmpty()) {
            return;
        }
        PendingRequest request;
        while ((request = waiting.poll()) != null) {
            request.answer(host, out, MODE);
        }
    }
}
