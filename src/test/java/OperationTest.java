package com.ass2.bank.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class OperationTest {

    private static final double DELTA = 1e-9;

    @Test
    void depositIsApplied() {
        Balances balances = new Balances();
        Operation.parse("deposit NOK 100").applyTo(balances);
        assertEquals(100.0, balances.amountIn("NOK"), DELTA);
    }

    @Test
    void negativeDepositAndExtraSpacesAreAccepted() {
        Balances balances = new Balances();
        Operation.parse("  deposit   NOK  -50 ").applyTo(balances);
        assertEquals(-50.0, balances.amountIn("NOK"), DELTA);
    }

    @Test
    void interestWithCurrencyChangesOnlyThatCurrency() {
        Balances balances = new Balances();
        balances.deposit("NOK", 100);
        balances.deposit("EUR", 100);
        Operation.parse("addInterest NOK 10").applyTo(balances);
        assertEquals(110.0, balances.amountIn("NOK"), DELTA);
        assertEquals(100.0, balances.amountIn("EUR"), DELTA);
    }

    @Test
    void interestWithoutCurrencyChangesAll() {
        Balances balances = new Balances();
        balances.deposit("NOK", 100);
        balances.deposit("EUR", 100);
        Operation.parse("addInterest 10").applyTo(balances);
        assertEquals(110.0, balances.amountIn("NOK"), DELTA);
        assertEquals(110.0, balances.amountIn("EUR"), DELTA);
    }

    @Test
    void assignmentExampleGives110() {
        // From the PDF: deposit NOK 100, addInterest 10 -> getSyncedBalance NOK = 110
        Balances balances = new Balances();
        Operation.parse("deposit NOK 100").applyTo(balances);
        Operation.parse("addInterest 10").applyTo(balances);
        assertEquals(110.0, balances.amountIn("NOK"), DELTA);
    }

    @Test
    void rejectsInvalidCommands() {
        String[] badCommands = {
                "deposit NOK",          // missing amount
                "deposit NOK abc",      // amount is not a number
                "deposit NOK NaN",      // NaN is not allowed
                "deposit NOK 5 extra",  // too many words
                "addInterest",          // missing percent
                "getHistory",           // not a replicated operation
                "",                     // empty
                null
        };
        for (String command : badCommands) {
            try {
                Operation.parse(command);
                fail("Expected IllegalArgumentException for: " + command);
            } catch (IllegalArgumentException expected) {
                // ok
            }
        }
    }
}