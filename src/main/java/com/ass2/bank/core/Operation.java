package com.ass2.bank.core;

/** A parsed deposit or addInterest command. */
final class Operation {

    /** The kinds of operations that change the balance. */
    enum Type { DEPOSIT, ADD_INTEREST }

    private final Type type;
    private final String currency; // null means "all currencies" (addInterest only)
    private final double value;    // amount for deposit, percent for addInterest

    private Operation(Type type, String currency, double value) {
        this.type = type;
        this.currency = currency;
        this.value = value;
    }

    /** Parses e.g. "deposit NOK 100", "addInterest NOK 5" or "addInterest 5". */
    static Operation parse(String command) {
        if (command == null) {
            throw invalid(command);
        }
        String[] words = command.trim().split("\\s+");

        if (words[0].equals("deposit") && words.length == 3) {
            return new Operation(Type.DEPOSIT, words[1], toNumber(words[2], command));
        }
        if (words[0].equals("addInterest") && words.length == 3) {
            return new Operation(Type.ADD_INTEREST, words[1], toNumber(words[2], command));
        }
        if (words[0].equals("addInterest") && words.length == 2) {
            return new Operation(Type.ADD_INTEREST, null, toNumber(words[1], command));
        }
        throw invalid(command);
    }

    /** Applies this operation to the balances. */
    void applyTo(Balances balances) {
        if (type == Type.DEPOSIT) {
            balances.deposit(currency, value);
        } else if (currency == null) {
            balances.addInterestToAll(value);
        } else {
            balances.addInterest(currency, value);
        }
    }

    private static double toNumber(String word, String command) {
        try {
            double number = Double.parseDouble(word);
            if (Double.isNaN(number) || Double.isInfinite(number)) {
                throw invalid(command);
            }
            return number;
        } catch (NumberFormatException e) {
            throw invalid(command);
        }
    }

    private static IllegalArgumentException invalid(String command) {
        return new IllegalArgumentException("Invalid command: \"" + command + "\"");
    }
}