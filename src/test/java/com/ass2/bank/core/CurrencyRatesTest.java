package com.ass2.bank.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CurrencyRatesTest {

    private static final double DELTA = 1e-9;

    @Test
    void readsFinalTradingRateFile() throws IOException {
        Map<String, Double> rates = CurrencyRates.load(Path.of("input/TradingRate.txt"));

        assertEquals(1.12, rates.get("EUR"), DELTA);
        assertEquals(0.10, rates.get("NOK"), DELTA);
        assertEquals(0.15, rates.get("DKK"), DELTA);
        assertEquals(1.0, rates.get("USD"), DELTA);
    }

    @Test
    void balanceInUsdWorksWithFinalRates() throws IOException {
        Account account = new Account("bank1", CurrencyRates.load(Path.of("input/TradingRate.txt")));
        account.issue("deposit NOK 100");
        account.issue("deposit USD 5");
        account.deliver(account.outstanding());

        assertEquals(15.0, account.balanceIn("USD"), DELTA);
    }

    @Test
    void skipsBlankLines(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("rates.txt");
        Files.writeString(file, "EUR 1.12\n\nNOK 0.10\n");

        assertEquals(3, CurrencyRates.load(file).size());
    }

    @Test
    void rejectsBadLines(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("rates.txt");
        Files.writeString(file, "EUR abc\n");

        assertThrows(IllegalArgumentException.class, () -> CurrencyRates.load(file));
    }
}
