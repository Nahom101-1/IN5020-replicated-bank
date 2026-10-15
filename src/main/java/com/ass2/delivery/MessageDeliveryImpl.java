package com.ass2.delivery;

import com.ass2.bank.util.Log;
import com.ass2.common.BankReplica;
import com.ass2.common.MessageDelivery;
import com.ass2.common.ReplicaState;
import com.ass2.common.Transaction;

import java.rmi.RemoteException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

public class MessageDeliveryImpl implements MessageDelivery, AutoCloseable {
	private static final long STATE_TRANSFER_TIMEOUT_SECONDS = 5;

	private final MemberRegistry members = new MemberRegistry();
	private final ExecutorService remoteCalls = Executors.newCachedThreadPool(daemonThreads("delivery-rpc"));
	private final ConcurrentMap<String, GroupQueue> groupQueues = new ConcurrentHashMap<>();
	private final Broadcaster broadcaster = new Broadcaster(remoteCalls);
	/** Numbers the broadcast messages in the log, in the order they are delivered. */
	private final AtomicLong messageCounter = new AtomicLong();

	@Override
	public void join(String group, String name, BankReplica replica) throws RemoteException {
		if (group == null || group.isBlank() || name == null || name.isBlank() || replica == null) {
			throw new RemoteException("Group, replica name, and replica reference are required");
		}

		inGroupOrder(group, () -> {
			if (members.find(name) != null) {
				throw new RemoteException("Replica name is already registered: " + name);
			}

			ReplicaState state = getStateFromExistingMember(group);
			if (state != null) {
				callWithTimeout(() -> {
					replica.installState(state);
					return null;
				}, "install state on " + name);
				Log.info("STATE installed on " + name + " (order_counter=" + state.orderCounter() + ")");
			}

			members.add(group, name, replica);
			Log.info("JOIN " + name + " to " + group + ", members=" + members.names(group));
			notifyMembership(group);
			return null;
		});
	}

	@Override
	public void leave(String name) throws RemoteException {
		MemberRegistry.Member member = members.find(name);
		if (member == null) {
			return;
		}
		inGroupOrder(member.group(), () -> {
			MemberRegistry.Member removed = members.remove(name);
			if (removed != null) {
				Log.info("LEAVE " + name + ", members=" + members.names(member.group()));
				notifyMembership(member.group());
			}
			return null;
		});
	}

	@Override
	public void broadcast(String sender, List<Transaction> transactions) throws RemoteException {
		if (transactions == null) {
			throw new RemoteException("Transaction list cannot be null");
		}
		List<Transaction> message = List.copyOf(transactions);
		MemberRegistry.Member senderMember = members.find(sender);
		if (senderMember == null) {
			throw new RemoteException("Unknown replica: " + sender);
		}

		inGroupOrder(senderMember.group(), () -> {
			if (members.find(sender) == null) {
				throw new RemoteException("Replica has left the group: " + sender);
			}
			List<MemberRegistry.Member> targets = members.members(senderMember.group());
			String label = "#" + messageCounter.incrementAndGet();
			Log.info("BROADCAST " + label + " from " + sender + ": " + message.size()
					+ " transaction(s) to " + members.names(senderMember.group()));
			Set<MemberRegistry.Member> failedMembers = broadcaster.deliver(targets, message, label);
			boolean membershipChanged = false;
			for (MemberRegistry.Member failed : failedMembers) {
				if (members.remove(failed)) {
					membershipChanged = true;
					Log.info("FAIL " + failed.name() + " (no ACK for " + label + " after 5 s), members="
							+ members.names(senderMember.group()));
				}
			}
			if (membershipChanged) {
				notifyMembership(senderMember.group());
			}
			return null;
		});
	}

	@Override
	public List<String> getMembers(String group) {
		return members.names(group);
	}

	private ReplicaState getStateFromExistingMember(String group) throws RemoteException {
		List<MemberRegistry.Member> existingMembers = members.members(group);
		for (MemberRegistry.Member member : existingMembers) {
			try {
				ReplicaState state = callWithTimeout(member.replica()::getState,
						"get state from " + member.name());
				if (state != null) {
					Log.info("STATE taken from " + member.name());
					return state;
				}
				throw new RemoteException("Replica returned an empty state snapshot");
			} catch (RemoteException e) {
				if (members.remove(member)) {
					Log.info("FAIL " + member.name() + " (could not send its state), members=" + members.names(group));
					notifyMembership(group);
				}
			}
		}
		if (!existingMembers.isEmpty()) {
			throw new RemoteException("No existing replica could provide state for group " + group);
		}
		return null;
	}

	private void notifyMembership(String group) {
		List<String> names = members.names(group);
		for (MemberRegistry.Member member : members.members(group)) {
			remoteCalls.execute(() -> {
				try {
					member.replica().onMembershipChange(names);
				} catch (RemoteException | RuntimeException e) {
					if (members.remove(member)) {
						Log.info("FAIL " + member.name() + " (membership notification failed), members="
								+ members.names(group));
						GroupQueue queue = groupQueues.get(group);
						if (queue != null) {
							queue.execute(() -> notifyMembership(group));
						}
					}
				}
			});
		}
	}

	private <T> T callWithTimeout(Callable<T> call, String operation) throws RemoteException {
		Future<T> future = remoteCalls.submit(call);
		try {
			return future.get(STATE_TRANSFER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			future.cancel(true);
			Thread.currentThread().interrupt();
			throw new RemoteException("Interrupted while attempting to " + operation, e);
		} catch (ExecutionException | TimeoutException e) {
			future.cancel(true);
			throw new RemoteException("Failed to " + operation, e);
		}
	}

	private <T> T inGroupOrder(String group, Callable<T> operation) throws RemoteException {
		Future<T> future = groupQueues.computeIfAbsent(group, GroupQueue::new).submit(operation);
		try {
			return future.get();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RemoteException("Interrupted while waiting for group delivery", e);
		} catch (ExecutionException e) {
			Throwable cause = e.getCause();
			if (cause instanceof RemoteException remoteException) {
				throw remoteException;
			}
			throw new RemoteException("Group delivery failed", cause);
		}
	}

	@Override
	public void close() {
		groupQueues.values().forEach(GroupQueue::close);
		remoteCalls.shutdownNow();
	}

	private static ThreadFactory daemonThreads(String prefix) {
		return runnable -> {
			Thread thread = new Thread(runnable, prefix);
			thread.setDaemon(true);
			return thread;
		};
	}

	private static final class GroupQueue implements AutoCloseable {
		private final ExecutorService executor;

		private GroupQueue(String group) {
			executor = Executors.newSingleThreadExecutor(daemonThreads("delivery-group-" + group));
		}

		private <T> Future<T> submit(Callable<T> operation) {
			return executor.submit(operation);
		}

		private void execute(Runnable operation) {
			executor.execute(operation);
		}

		@Override
		public void close() {
			executor.shutdownNow();
		}
	}
}
