package com.ass2.common;

import java.io.Serializable;
import java.util.Objects;

/**
 * An immutable replicated operation
 * @param command  the operation as text
 * @param uniqueId sender name + counter, used to apply each transaction exactly once
 */
public record Transaction(String command, String uniqueId) implements Serializable {

    /** Rejects null fields so bad messages fail where they are created. */
    public Transaction {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(uniqueId, "uniqueId");
    }
}