package com.ass2.bank.cli;

public class CommandProcessor {
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

    private double parseNumber(String value){
        double parsed = Double.parseDouble(value);
        if(!Double.isFinite(parsed)){
            throw new NumberFormatException("Invalid number.");
        }
        return parsed;
    }
    

    private String handleDeposit(String[] parts){
        if(parts.length != 3){
            return "Invalid deposit arguments";
        }

        String currency = parts[1];
        try {
            double amount = parseNumber(parts[2]);
            return currency + ": " + amount;        
        } catch (NumberFormatException e) {
            return "Invalid deposit amount.";
        }
    
    }

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

    private String handleQuickBalance(String[] parts){
        if(parts.length != 2){
            return "Invalid balance arguments";
        }
        String currency = parts[1];
        return "Quick balance requested: " + currency;
    }

    private String handleGetHistory(String[] parts){
        if(parts.length != 1){
            return "Invalid history argument";
        }

        return "History requested";
    }

    private String handleCheckTxStatus(String[] parts){
        if(parts.length != 3){
            return "Invalid transaction ID.";
        }

        String serverName = parts[1];
        int counter;
        try {
            counter = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            return "Invalid transaction ID.";
        }

        String uniqueId = serverName + " " + counter;
        return (counter >=0) ? "Checking transaction: " + uniqueId : "Invalid transaction ID.";
    }

    private String handleCleanHistory(String[] parts){
        if(parts.length != 1){
            return "Invalid cleanHistory arguments.";
        }
        return "History cleanup requested.";
    }

    private String handleMemberInfo(String[] parts){
        if(parts.length != 1){
            return "Invalid memberInfo arguments."; 
        }
        return "Member information requested.";
    }

    private String handleGetSyncedBalance(String[] parts){
        if(parts.length != 2){
            return "Invalid synced balance arguments";
        }

        String currency = parts[1];

        return "Synced balance requested: " + currency;
    }

    private String handleSleep(String[] parts){
        if(parts.length != 2){
           return "Invalid sleep arguments.";
        }
        int sleep;

        try {
            sleep = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return "Invalid sleep duration.";
        }

        return (sleep >= 0) ? "Sleep requested: " + sleep + " seconds." : "Sleep duration cannot be negative.";
    }

    private String handleExit(String[] parts){
        if(parts.length != 1){
            return "Invalid exit arguments";
        }

        return "Exit requested.";
    }
    
}
