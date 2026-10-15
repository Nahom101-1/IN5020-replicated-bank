package com.ass2.bank.core;

import com.ass2.common.BankReplica;
import com.ass2.common.ReplicaState;
import com.ass2.common.Transaction;

import java.util.List;

/** The part of the bank server that the Message Delivery server calls. */
public class BankReplicaImpl implements BankReplica {

    private final Account account;
    private List<String> members = List.of();

    public BankReplicaImpl(Account account) {
        this.account = account;
    }

    /** Applies a message from the delivery server. Returning normally is the ACK. */
    @Override
    public void deliver(List<Transaction> transactions) {
        account.deliver(transactions);
    }

    /** Stores the new member list and wakes up {@link #waitForMembers}. */
    @Override
    public synchronized void onMembershipChange(List<String> newMembers) {
        members = List.copyOf(newMembers);
        notifyAll();
    }

    /** Gives our state to a replica that is joining. */
    @Override
    public ReplicaState getState() {
        return account.snapshot();
    }

    /** Takes the state of an existing replica when we join. */
    @Override
    public void installState(ReplicaState state) {
        account.restore(state);
    }

    /** The names of the replicas currently in the group. */
    public synchronized List<String> members() {
        return members;
    }

    /** Blocks until at least {@code count} replicas are in the group. */
    public synchronized void waitForMembers(int count) throws InterruptedException {
        while (members.size() < count) {
            wait();
        }
    }
}
