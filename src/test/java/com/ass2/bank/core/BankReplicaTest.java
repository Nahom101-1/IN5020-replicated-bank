package com.ass2.bank.core;

import com.ass2.common.BankReplica;
import com.ass2.common.MessageDelivery;
import com.ass2.delivery.MessageDeliveryImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.rmi.Remote;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Bank replicas together with the real Message Delivery server. */
@Timeout(20)
class BankReplicaTest {

    private static final double DELTA = 1e-9;
    private static final Map<String, Double> RATES = Map.of("USD", 1.0, "NOK", 0.1);
    private static final String GROUP = "group07";

    private final MessageDeliveryImpl deliveryImpl = new MessageDeliveryImpl();
    private final List<Remote> exported = new ArrayList<>();

    @AfterEach
    void cleanUp() throws Exception {
        for (Remote object : exported) {
            UnicastRemoteObject.unexportObject(object, true);
        }
        deliveryImpl.close();
    }

    @Test
    void broadcastIsAppliedOnAllReplicas() throws Exception {
        Account account1 = new Account("bank1", RATES);
        Account account2 = new Account("bank2", RATES);
        BankReplicaImpl replica1 = new BankReplicaImpl(account1);
        deliveryImpl.join(GROUP, "bank1", replica1);
        deliveryImpl.join(GROUP, "bank2", new BankReplicaImpl(account2));

        account1.issue("deposit NOK 100");
        new OutstandingSender(account1, deliveryImpl, "bank1").sendNow();

        assertEquals(100.0, account1.amountIn("NOK"), DELTA);
        assertEquals(100.0, account2.amountIn("NOK"), DELTA);
        assertTrue(account1.outstanding().isEmpty());
    }

    @Test
    void lateReplicaGetsStateOfExistingReplica() throws Exception {
        Account account1 = new Account("bank1", RATES);
        deliveryImpl.join(GROUP, "bank1", new BankReplicaImpl(account1));
        account1.issue("deposit NOK 100");
        new OutstandingSender(account1, deliveryImpl, "bank1").sendNow();

        Account account2 = new Account("bank2", RATES);
        deliveryImpl.join(GROUP, "bank2", new BankReplicaImpl(account2));

        assertEquals(100.0, account2.amountIn("NOK"), DELTA);
        assertEquals(1, account2.orderCounter());
    }

    @Test
    void replicasLearnAboutNewMembers() throws Exception {
        BankReplicaImpl replica1 = new BankReplicaImpl(new Account("bank1", RATES));
        BankReplicaImpl replica2 = new BankReplicaImpl(new Account("bank2", RATES));
        deliveryImpl.join(GROUP, "bank1", replica1);
        deliveryImpl.join(GROUP, "bank2", replica2);

        replica1.waitForMembers(2);

        assertEquals(List.of("bank1", "bank2"), replica1.members());
    }

    @Test
    void worksOverRmi() throws Exception {
        MessageDelivery delivery = (MessageDelivery) export(deliveryImpl);
        Account account1 = new Account("bank1", RATES);
        Account account2 = new Account("bank2", RATES);
        delivery.join(GROUP, "bank1", (BankReplica) export(new BankReplicaImpl(account1)));
        delivery.join(GROUP, "bank2", (BankReplica) export(new BankReplicaImpl(account2)));

        account1.issue("deposit NOK 100");
        account2.issue("addInterest 10");
        new OutstandingSender(account1, delivery, "bank1").sendNow();
        new OutstandingSender(account2, delivery, "bank2").sendNow();

        Account account3 = new Account("bank3", RATES);
        delivery.join(GROUP, "bank3", (BankReplica) export(new BankReplicaImpl(account3)));

        assertEquals(110.0, account1.amountIn("NOK"), DELTA);
        assertEquals(110.0, account2.amountIn("NOK"), DELTA);
        assertEquals(110.0, account3.amountIn("NOK"), DELTA);
        assertEquals(account1.executed(), account3.executed());
    }

    /** Exports the object on a random port and returns its RMI stub. */
    private Remote export(Remote object) throws Exception {
        exported.add(object);
        return UnicastRemoteObject.exportObject(object, 0);
    }
}
