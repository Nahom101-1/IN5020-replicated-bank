package com.ass2.common;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface MessageDelivery extends Remote {
	void join(String group, String name, BankReplica replica) throws RemoteException;

	void leave(String name) throws RemoteException;

	void broadcast(String sender, List<Transaction> transactions) throws RemoteException;

	List<String> getMembers(String group) throws RemoteException;
}
