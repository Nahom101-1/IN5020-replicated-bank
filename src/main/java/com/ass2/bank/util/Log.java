package com.ass2.bank.util;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/*
* Provides a timestamped logging for bank server output and ensures a consistent date and time format for log messages.
*/
public class Log {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public static void info(String message){
        LocalDateTime now = LocalDateTime.now();
        System.out.println("[" + now.format(FORMATTER) + "] " + message);
    }
}
