package com.ass2.bank.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BalancesTest {

    private static final double DELTA = 1e-9;
    private static final String NOK = "NOK";
    private static final String EUR = "EUR";

    private Balances balances;

    @BeforeEach
    void setUp() {
        balances = new Balances();
    }

    @Test
    void newAccountIsZero() {
        assertEquals(0.0, balances.amountIn(NOK), DELTA);
    }

    @Test
    void depositAddsToCurrency() {
        balances.deposit(NOK, 100);
        balances.deposit(NOK, 50);

        assertEquals(150.0, balances.amountIn(NOK), DELTA);
    }

    @Test
    void negativeDepositCanGoBelowZero() {
        balances.deposit(NOK, -30);

        assertEquals(-30.0, balances.amountIn(NOK), DELTA);
    }

    @Test
    void currenciesAreSeparate() {
        balances.deposit(NOK, 100);
        balances.deposit(EUR, 20);

        assertAll(
                () -> assertEquals(100.0, balances.amountIn(NOK), DELTA),
                () -> assertEquals(20.0, balances.amountIn(EUR), DELTA)
        );
    }

    @Test
    void interestOnOneCurrency() {
        balances.deposit(NOK, 100);
        balances.deposit(EUR, 20);

        balances.addInterest(NOK, 10);

        assertAll(
                () -> assertEquals(110.0, balances.amountIn(NOK), DELTA),
                () -> assertEquals(20.0, balances.amountIn(EUR), DELTA)
        );
    }

    @Test
    void interestOnAllCurrencies() {
        balances.deposit(NOK, 100);
        balances.deposit(EUR, 20);

        balances.addInterestToAll(50);

        assertAll(
                () -> assertEquals(150.0, balances.amountIn(NOK), DELTA),
                () -> assertEquals(30.0, balances.amountIn(EUR), DELTA)
        );
    }

    @Test
    void interestOnMissingCurrencyDoesNothing() {
        balances.addInterest(NOK, 10);

        assertEquals(0.0, balances.amountIn(NOK), DELTA);
    }

    @Test
    void orderOfOperationsMatters() {
        Balances first = new Balances();
        first.deposit(NOK, 100);
        first.addInterest(NOK, 10);

        Balances second = new Balances();
        second.addInterest(NOK, 10);
        second.deposit(NOK, 100);

        // Same operations, different order, different result:
        // this is why all replicas must apply transactions in the same order.
        assertAll(
                () -> assertEquals(110.0, first.amountIn(NOK), DELTA),
                () -> assertEquals(100.0, second.amountIn(NOK), DELTA)
        );
    }

    @Test
    void snapshotIsSortedCopy() {
        balances.deposit(NOK, 100);
        balances.deposit(EUR, 20);

        Map<String, Double> snapshot = balances.snapshot();
        snapshot.put(NOK, 999.0);

        assertAll(
                () -> assertEquals(List.of(EUR, NOK), List.copyOf(snapshot.keySet())),
                () -> assertEquals(100.0, balances.amountIn(NOK), DELTA)
        );
    }

    @Test
    void rejectsMissingCurrency() {
        assertThrows(IllegalArgumentException.class, () -> balances.deposit(null, 10));
    }

    @Test
    void rejectsInvalidNumber() {
        assertThrows(IllegalArgumentException.class, () -> balances.deposit(NOK, Double.NaN));
    }
}
