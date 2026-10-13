package com.ass2.bank.sync;

import com.ass2.common.Transaction;

/**
 * The getSyncedBalance marker used by the ordered implementation.
 *
 * A marker is a normal {@link Transaction} with the command "getSyncedBalance <Currency>". It is
 * broadcast with the other outstanding transactions, but it never changes the account. The bank
 * core must check {@link #isMarker} before parsing a delivered transaction as a deposit or
 * addInterest, and hand markers to {@link SyncedBalance#onDelivered} instead.
 */
public final class SyncMarker {

    /** The command name that identifies a marker. */
    public static final String COMMAND = "getSyncedBalance";

    private SyncMarker() {
    }

    /** Creates a marker asking for the balance in the given currency. */
    public static Transaction create(String currency, String uniqueId) {
        return new Transaction(COMMAND + " " + currency, uniqueId);
    }

    /** Returns true if the transaction is a getSyncedBalance marker. */
    public static boolean isMarker(Transaction tx) {
        String[] words = tx.command().trim().split("\\s+");
        return words[0].equals(COMMAND);
    }

    /** Returns the currency the marker asks for. */
    public static String currencyOf(Transaction marker) {
        String[] words = marker.command().trim().split("\\s+");
        if (!words[0].equals(COMMAND) || words.length != 2) {
            throw new IllegalArgumentException("Not a getSyncedBalance marker: \"" + marker.command() + "\"");
        }
        return words[1];
    }

    /**
     * Returns true if the transaction was created by the named bank server.
     * The uniqueId has the form "<instance name> <counter>", so the name is everything before the
     * last space.
     */
    public static boolean isIssuedBy(Transaction tx, String instanceName) {
        String id = tx.uniqueId();
        int lastSpace = id.lastIndexOf(' ');
        return lastSpace > 0 && id.substring(0, lastSpace).equals(instanceName);
    }
}
