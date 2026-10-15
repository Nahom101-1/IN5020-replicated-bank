package com.ass2.bank.cli;

import com.ass2.bank.core.Account;
import com.ass2.bank.core.BankReplicaImpl;
import com.ass2.bank.sync.SyncMarker;
import com.ass2.bank.sync.SyncedBalance;
import com.ass2.bank.util.Log;
import com.ass2.common.Transaction;

import java.util.List;
import java.util.Locale;

/**
* Parses bank commands from interactive or batch input and runs them on the account.
*/
public class CommandProcessor {

    /** Used by getQuickBalance and getSyncedBalance when no currency is given. */
    private static final String DEFAULT_CURRENCY = "USD";

    private final Account account;
    private final SyncedBalance syncedBalance;
    private final BankReplicaImpl replica;

    public CommandProcessor(Account account, SyncedBalance syncedBalance, BankReplicaImpl replica) {
        this.account = account;
        this.syncedBalance = syncedBalance;
        this.replica = replica;
    }

    /**
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

    /**
    * Parses a string into a finite double value.
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

    /**
    * Queues "deposit <Currency> <amount>" for broadcast.
    */
    private String handleDeposit(String[] parts){
        if(parts.length != 3){
            return "Invalid deposit arguments.";
        }

        String currency = parts[1];
        if(!account.knowsCurrency(currency)){
            return "Unknown currency " + currency;
        }
        try {
            parseNumber(parts[2]);
        } catch (NumberFormatException e) {
            return "Invalid deposit amount.";
        }
        return issue(String.join(" ", parts));
    }

    /**
    * Queues "addInterest [Currency] <percent>" for broadcast. Without a currency it applies to all.
    */
    private String handleAddInterest(String[] parts){
        if(parts.length != 2 && parts.length != 3){
            return "Invalid interest arguments";
        }

        if(parts.length == 3 && !account.knowsCurrency(parts[1])){
            return "Unknown currency " + parts[1];
        }
        int percentageIndex = (parts.length == 3) ? 2 : 1;
        try {
            parseNumber(parts[percentageIndex]);
        } catch (NumberFormatException e) {
            return "Invalid interest amount";
        }
        return issue(String.join(" ", parts));
    }

    /** Adds the command to outstanding_collection and returns its id. */
    private String issue(String command){
        Transaction transaction = account.issue(command);
        return "Queued \"" + command + "\" as " + transaction.uniqueId();
    }

    /**
    * Prints the balance right away, without waiting for outstanding transactions.
    */
    private String handleQuickBalance(String[] parts){
        if(parts.length > 2){
            return "Invalid balance arguments";
        }
        String currency = (parts.length == 2) ? parts[1] : DEFAULT_CURRENCY;
        if(!account.knowsCurrency(currency)){
            return "Unknown currency " + currency;
        }
        return String.format(Locale.ROOT, "getQuickBalance %s: %.2f", currency, account.balanceIn(currency));
    }

    /**
    * Prints the executed transactions, numbered from order_counter - executed_list.size,
    * and then the outstanding ones.
    */
    private String handleGetHistory(String[] parts){
        if(parts.length != 1){
            return "Invalid history argument";
        }

        List<Account.Executed> executed = account.executed();
        int number = account.orderCounter() - executed.size();

        StringBuilder history = new StringBuilder("executed_list");
        for(Account.Executed entry : executed){
            history.append("\n").append(number).append(". ")
                    .append(Log.format(entry.executedAt())).append(" ")
                    .append(entry.transaction().command());
            number++;
        }

        history.append("\noutstanding_collection");
        for(Transaction transaction : account.outstanding()){
            if(!SyncMarker.isMarker(transaction)){
                history.append("\n").append(transaction.command());
            }
        }
        return history.toString();
    }

    /**
    * Shows if a transaction has been applied. "checkTxStatus 3" means our own transaction 3,
    * "checkTxStatus <name> 3" means transaction 3 of another replica.
    */
    private String handleCheckTxStatus(String[] parts){
        if(parts.length != 2 && parts.length != 3){
            return "Invalid transaction ID.";
        }

        String serverName = (parts.length == 3) ? parts[1] : account.instanceName();
        int counterIndex = (parts.length == 3) ? 2 : 1;
        int counter;

        try {
            counter = Integer.parseInt(parts[counterIndex]);
        } catch (NumberFormatException e) {
            return "Invalid transaction ID.";
        }
        if(counter < 0){
            return "Invalid transaction ID.";
        }

        String uniqueId = serverName + " " + counter;
        if(account.isApplied(uniqueId)){
            return "Transaction " + uniqueId + ": applied";
        }
        if(isOutstanding(uniqueId)){
            return "Transaction " + uniqueId + ": outstanding, not applied yet";
        }
        return "Transaction " + uniqueId + ": not found";
    }

    private boolean isOutstanding(String uniqueId){
        for(Transaction transaction : account.outstanding()){
            if(transaction.uniqueId().equals(uniqueId)){
                return true;
            }
        }
        return false;
    }

    /**
    * Empties the history. The counters are not reset.
    */
    private String handleCleanHistory(String[] parts){
        if(parts.length != 1){
            return "Invalid cleanHistory arguments.";
        }
        account.cleanHistory();
        return "History cleaned.";
    }

    /**
    * Prints the names of the replicas in the group.
    */
    private String handleMemberInfo(String[] parts){
        if(parts.length != 1){
            return "Invalid memberInfo arguments.";
        }
        return "Members: " + String.join(", ", replica.members());
    }

    /**
    * Starts a synced balance request. The balance is printed later, when it is ready.
    */
    private String handleGetSyncedBalance(String[] parts){
        if(parts.length > 2){
            return "Invalid synced balance arguments";
        }

        String currency = (parts.length == 2) ? parts[1] : DEFAULT_CURRENCY;
        if(!account.knowsCurrency(currency)){
            return "Unknown currency " + currency;
        }
        syncedBalance.request(currency);
        return "getSyncedBalance " + currency + ": waiting for outstanding transactions";
    }

    /**
    * Waits for the given number of seconds.
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

    /**
    * Validates an exit command.
    */
    private String handleExit(String[] parts){
        if(parts.length != 1){
            return "Invalid exit arguments";
        }

        return "Exit requested.";
    }

}
