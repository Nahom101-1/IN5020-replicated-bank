package com.ass2.bank.core;

import java.util.Map;
import java.util.TreeMap;

/** Balance per currency. Not thread-safe. */
final class Balances {

    private final Map<String, Double> amounts = new TreeMap<>();

    /** Adds the amount; negative withdraws. */
    void deposit(String currency, double amount) {
        checkCurrency(currency);
        checkNumber(amount);
        double current = amountIn(currency);
        amounts.put(currency, current + amount);
    }

    /** Adds interest to one currency. */
    void addInterest(String currency, double percent) {
        checkCurrency(currency);
        checkNumber(percent);
        if (amounts.containsKey(currency)) {
            double current = amounts.get(currency);
            amounts.put(currency, current * growthFactor(percent));
        }
    }

    /** Adds interest to all currencies. */
    void addInterestToAll(double percent) {
        checkNumber(percent);
        for (String currency : amounts.keySet()) {
            addInterest(currency, percent);
        }
    }

    /** Returns the amount, or 0 if none. */
    double amountIn(String currency) {
        if (amounts.containsKey(currency)) {
            return amounts.get(currency);
        }
        return 0.0;
    }

    /** Returns a sorted copy. */
    Map<String, Double> snapshot() {
        return new TreeMap<>(amounts);
    }

    private static double growthFactor(double percent) {
        return 1 + percent / 100.0;
    }

    private static void checkCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("Currency is missing");
        }
    }

    private static void checkNumber(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException("Not a valid number: " + value);
        }
    }
}