package com.ass2.bank.util;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Log {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public static void info(String message){
        LocalDateTime now = LocalDateTime.now();
        System.out.println("[" + now.format(FORMATTER) + "] " + message);
    }

    public static void main(String[] args) {
        Log.info("NOK: 100.0");
        Log.info("History requested");
        Log.info("Exit requested.");
    }
}


