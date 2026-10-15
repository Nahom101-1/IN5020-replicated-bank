package com.ass2.delivery;

import com.ass2.bank.util.Log;
import com.ass2.common.Transaction;

import java.rmi.RemoteException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class Broadcaster {
	static final long RETRY_AFTER_MILLIS = 2_000;
	static final long REMOVE_AFTER_MILLIS = 5_000;

	private final ExecutorService calls;

	public Broadcaster(ExecutorService calls) {
		this.calls = calls;
	}

	/**
	 * Delivers one ordered message to every member, retrying only missing ACKs.
	 *
	 * @param label names the message in the log, e.g. "#4"
	 * @return the members that did not ACK within 5 s
	 */
	public Set<MemberRegistry.Member> deliver(Collection<MemberRegistry.Member> targets,
												java.util.List<Transaction> transactions,
												String label)
			throws RemoteException {
		Map<String, MemberRegistry.Member> pending = new LinkedHashMap<>();
		targets.forEach(member -> pending.put(member.name(), member));
		if (pending.isEmpty()) {
			return Set.of();
		}

		CompletionService<Acknowledgement> completions = new ExecutorCompletionService<>(calls);
		submit(pending.values(), transactions, completions);
		long started = System.nanoTime();
		long retryAt = started + TimeUnit.MILLISECONDS.toNanos(RETRY_AFTER_MILLIS);
		long removeAt = started + TimeUnit.MILLISECONDS.toNanos(REMOVE_AFTER_MILLIS);
		boolean retried = false;

		while (!pending.isEmpty()) {
			long now = System.nanoTime();
			if (!retried && now >= retryAt) {
				Log.info("RESEND " + label + " to " + pending.keySet() + " (no ACK after 2 s)");
				submit(pending.values(), transactions, completions);
				retried = true;
			}
			if (now >= removeAt) {
				break;
			}

			long nextDeadline = retried ? removeAt : retryAt;
			Future<Acknowledgement> completed;
			try {
				completed = completions.poll(nextDeadline - now, TimeUnit.NANOSECONDS);
				if (completed == null) {
					continue;
				}
				Acknowledgement acknowledgement = completed.get();
				if (acknowledgement.received()) {
					pending.remove(acknowledgement.name());
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new RemoteException("Interrupted while waiting for replica acknowledgements", e);
			} catch (ExecutionException e) {
				// Calls convert remote failures into negative acknowledgements.
			}
		}

		return Set.copyOf(pending.values());
	}

	private void submit(Collection<MemberRegistry.Member> members,
						java.util.List<Transaction> transactions,
						CompletionService<Acknowledgement> completions) {
		for (MemberRegistry.Member member : members) {
			completions.submit(() -> {
				try {
					member.replica().deliver(transactions);
					return new Acknowledgement(member.name(), true);
				} catch (RemoteException | RuntimeException e) {
					return new Acknowledgement(member.name(), false);
				}
			});
		}
	}

	private record Acknowledgement(String name, boolean received) {
	}
}
