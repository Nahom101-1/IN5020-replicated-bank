package com.ass2.bank.sync;

import com.ass2.common.Transaction;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Correct getSyncedBalance: send a marker through the same total order as the deposits.
 *
 * The request appends a "getSyncedBalance <Currency>" marker to outstanding_collection, behind the
 * transactions this server issued before it. All replicas receive the marker in the same position
 * of the total order. When it arrives, every transaction ordered before it has been applied and
 * none after it, so the issuing replica prints exactly the balance at the time of the request.
 *
 * Differences from deposit and addInterest:
 *  - only the replica that issued the marker prints the balance, the others ignore it;
 *  - it is removed from outstanding_collection but not added to executed_list, and it does not
 *    increase order_counter, so all replicas keep the same history and counters.
 *
 * It does not depend on outstanding_collection ever being empty, so it is answered within one
 * broadcast round no matter how many new commands arrive.
 */
public final class OrderedSyncedBalance implements SyncedBalance {

    static final String MODE = "ordered";

    private final SyncHost host;
    private final Consumer<String> out;

    /** This server's markers that have not been delivered yet, by uniqueId. */
    private final Map<String, PendingRequest> waiting = new ConcurrentHashMap<>();

    OrderedSyncedBalance(SyncHost host, Consumer<String> out) {
        this.host = host;
        this.out = out;
    }

    @Override
    public void request(String currency) {
        Transaction marker = SyncMarker.create(currency, host.nextUniqueId());
        // Register before adding to outstanding_collection, so the marker cannot be delivered
        // before we know about it.
        waiting.put(marker.uniqueId(), PendingRequest.now(currency));
        host.addOutstanding(marker);
    }

    @Override
    public void onDelivered(Transaction marker) {
        if (!SyncMarker.isIssuedBy(marker, host.instanceName())) {
            return; // another replica's request; only the issuer executes it
        }
        // remove() returns null the second time the same marker is delivered (the delivery server
        // resends after 2 s without an ACK), so each request is answered exactly once.
        PendingRequest request = waiting.remove(marker.uniqueId());
        if (request == null) {
            return;
        }
        host.removeOutstanding(marker);
        request.answer(host, out, MODE);
    }

    @Override
    public void afterDelivery() {
        // Nothing to do: markers are answered when they are delivered.
    }

    @Override
    public void close() {
        for (PendingRequest request : waiting.values()) {
            request.reportUnanswered(out, MODE);
        }
        waiting.clear();
    }

    @Override
    public String mode() {
        return MODE;
    }
}
