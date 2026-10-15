package com.ass2.bank.core;

import com.ass2.bank.util.Log;
import com.ass2.common.MessageDelivery;
import com.ass2.common.Transaction;

import java.rmi.RemoteException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Sends outstanding_collection to the delivery server every 10 seconds. */
public class OutstandingSender {

    private static final long INTERVAL_SECONDS = 10;

    private final Account account;
    private final MessageDelivery delivery;
    private final String replicaName;
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "outstanding-sender");
        thread.setDaemon(true);
        return thread;
    });

    public OutstandingSender(Account account, MessageDelivery delivery, String replicaName) {
        this.account = account;
        this.delivery = delivery;
        this.replicaName = replicaName;
    }

    public void start() {
        timer.scheduleWithFixedDelay(this::sendNow, INTERVAL_SECONDS, INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    public void stop() {
        timer.shutdownNow();
    }

    /**
     * Broadcasts the current outstanding transactions, if there are any.
     * They stay in outstanding_collection until they are delivered back to us.
     */
    void sendNow() {
        List<Transaction> outstanding = account.outstanding();
        if (outstanding.isEmpty()) {
            return;
        }
        try {
            delivery.broadcast(replicaName, outstanding);
        } catch (RemoteException e) {
            // Keep the timer running; the same transactions are sent again next round.
            Log.info("Broadcast failed: " + e.getMessage());
        }
    }
}
