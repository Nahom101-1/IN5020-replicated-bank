package com.ass2.bank.cli;

/*
* Parses and validates vank commands received from itneractive or batch input. Dispatches each command to its corresponding handler.
*/
public class CommandProcessor {

    /* Currency used by getQuickBalance and getSyncedBalance when none is given. */
    private static final String DEFAULT_CURRENCY = "USD";

    /*
    * Parses an input line and dispatches it to the appropriate command handler.
    *
    * @param line the command entered by the user
    * @return the result of processing the command, or an error message.
    */
    public String process(String line){
        line = line.trim();
        if(line.isEmpty()){
            return "Invalid command.";
        }

        String[] parts = line.split("\\s+");
        String commandName = parts[0];

        return switch (commandName){
            case "deposit" -> handleDeposit(parts);
            case "addInterest" -> handleAddInterest(parts);
            case "getQuickBalance" -> handleQuickBalance(parts);
            case "getHistory" -> handleGetHistory(parts);
            case "checkTxStatus" -> handleCheckTxStatus(parts);
            case "cleanHistory" -> handleCleanHistory(parts);
            case "memberInfo" -> handleMemberInfo(parts);
            case "getSyncedBalance" -> handleGetSyncedBalance(parts);
            case "sleep" -> handleSleep(parts);
            case "exit" -> handleExit(parts);
            default -> "Unknown command.";
        };

    }

    /*
    * Parses a string intoa finite double value.
    *
    * @param value the numeric string to parse
    * @return the parsed double value
    * @throws NumberFormatException if the value is invalid or non-finite.
    */
    private double parseNumber(String value){
        double parsed = Double.parseDouble(value);
        if(!Double.isFinite(parsed)){
            throw new NumberFormatException("Invalid number.");
        }
        return parsed;
    }
    

    /*
    * Validates the currency and deposit amount.
    */
    private String handleDeposit(String[] parts){
        if(parts.length != 3){
            return "Invalid deposit arguments.";
        }

        String currency = parts[1];
        try {
            double amount = parseNumber(parts[2]);
            return currency + ": " + amount;        
        } catch (NumberFormatException e) {
            return "Invalid deposit amount.";
        }
    
    }

    /*
    * Validates an interest percentage with an optional currency.
    */
    private String handleAddInterest(String[] parts){
        if(parts.length != 2 && parts.length != 3){
            return "Invalid interest arguments";
        }

        String currency = (parts.length == 3) ? parts[1] : "ALL";
        int percentageIndex = (parts.length == 3) ? 2 : 1;
        try {
            double percentage = parseNumber(parts[percentageIndex]);
            return currency + ": " + percentage + "%";
        } catch (NumberFormatException e) {
            return "Invalid interest amount";
        }
        
    }

    /*
    * Validates a quick balance request for a currency.
    * Without a currency the balance is given in USD, the base currency of the rate file.
    */
    private String handleQuickBalance(String[] parts){
        if(parts.length != 1 && parts.length != 2){
            return "Invalid balance arguments";
        }
        String currency = (parts.length == 2) ? parts[1] : DEFAULT_CURRENCY;
        return "Quick balance requested: " + currency;
    }

    /*
    * Validates a transaction history request.
    */
    private String handleGetHistory(String[] parts){
        if(parts.length != 1){
            return "Invalid history argument";
        }

        return "History requested";
    }

    /*
    * Validates a transaction identifier.
    */
    private String handleCheckTxStatus(String[] parts){
        if(parts.length != 2 && parts.length != 3){
            return "Invalid transaction ID.";
        }

        String serverName = (parts.length == 3) ? parts[1] : null;
        int counterIndex = (parts.length == 3) ? 2 : 1;
        int counter;
        
        try {
            counter = Integer.parseInt(parts[counterIndex]);
        } catch (NumberFormatException e) {
            return "Invalid transaction ID.";
        }

        String uniqueId = (serverName != null) ? serverName + " " + counter : String.valueOf(counter);
        return (counter >=0) ? "Checking transaction: " + uniqueId : "Invalid transaction ID.";
    }

    /*
    * Validates a history cleanup request.
    */
    private String handleCleanHistory(String[] parts){
        if(parts.length != 1){
            return "Invalid cleanHistory arguments.";
        }
        return "History cleanup requested.";
    }

    /*
    * Validates a group membership information request.
    */
    private String handleMemberInfo(String[] parts){
        if(parts.length != 1){
            return "Invalid memberInfo arguments."; 
        }
        return "Member information requested.";
    }

    /*
    * Validates a synchronized balance request for a currency.
    * Without a currency the balance is given in USD, the base currency of the rate file.
    * WIP
    */
    private String handleGetSyncedBalance(String[] parts){
        if(parts.length != 1 && parts.length != 2){
            return "Invalid synced balance arguments";
        }

        String currency = (parts.length == 2) ? parts[1] : DEFAULT_CURRENCY;

        return "Synced balance requested: " + currency;
    }

    /*
    * Validates a non-negative sleep duration.
    */
    private String handleSleep(String[] parts){
        if(parts.length != 2){
           return "Invalid sleep arguments.";
        }

        try {
           double seconds = parseNumber(parts[1]);

           if(seconds < 0 || seconds > Long.MAX_VALUE / 1000.0){
            return "Invalid sleep duration.";
           }

           long milliseconds = (long) (seconds * 1000);
           Thread.sleep(milliseconds);

           return "Slept for " + seconds + " seconds.";
        } 
        
        catch (NumberFormatException e) {
            return "Invalid sleep duration.";
        }

        catch (InterruptedException e){
            Thread.currentThread().interrupt();
            return "Sleep interrupted";
        }

    }

    /*
    * Validates an exit command.
    */
    private String handleExit(String[] parts){
        if(parts.length != 1){
            return "Invalid exit arguments";
        }

        return "Exit requested.";
    }
    
}
