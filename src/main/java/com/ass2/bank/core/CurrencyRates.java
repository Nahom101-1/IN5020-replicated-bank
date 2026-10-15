package com.ass2.bank.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Reads the currency file. Each line is "<Currency> <rate to USD>", e.g. "NOK 0.10". */
public final class CurrencyRates {

    private CurrencyRates() {
    }

    /** Returns the rates from the file. USD is always included with rate 1.0. */
    public static Map<String, Double> load(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file);
        Map<String, Double> rates = new HashMap<>();
        rates.put("USD", 1.0);

        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            String[] words = line.trim().split("\\s+");
            if (words.length != 2) {
                throw new IllegalArgumentException("Invalid line in currency file: \"" + line + "\"");
            }
            rates.put(words[0], parseRate(words[1], line));
        }
        return rates;
    }

    private static double parseRate(String word, String line) {
        try {
            double rate = Double.parseDouble(word);
            if (Double.isNaN(rate) || Double.isInfinite(rate) || rate <= 0) {
                throw new IllegalArgumentException("Rate must be positive: \"" + line + "\"");
            }
            return rate;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid rate: \"" + line + "\"");
        }
    }
}
