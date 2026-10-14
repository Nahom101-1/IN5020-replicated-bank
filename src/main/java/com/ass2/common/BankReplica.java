package com.ass2.common;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface BankReplica extends Remote {
	void deliver(List<Transaction> transactions) throws RemoteException;

	void onMembershipChange(List<String> members) throws RemoteException;

	ReplicaState getState() throws RemoteException;

	void installState(ReplicaState state) throws RemoteException;
}
