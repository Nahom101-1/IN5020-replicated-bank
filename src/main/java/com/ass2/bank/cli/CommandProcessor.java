package com.ass2.bank.cli;

import com.ass2.bank.core.Account;
import com.ass2.bank.sync.SyncMarker;
import com.ass2.bank.sync.SyncedBalance;
import com.ass2.bank.util.Log;
import com.ass2.common.Transaction;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/*
* Parses and validates bank commands received from interactive or batch input, and runs each one
* against this replica's account. Dispatches each command to its corresponding handler.
*/
public class CommandProcessor {

    /* Currency used by getQuickBalance and getSyncedBalance when none is given. */
    private static final String DEFAULT_CURRENCY = "USD";

    /* Format of the execution timestamps printed by getHistory. */
    private static final DateTimeFormatter HISTORY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Account account;
    private final SyncedBalance syncedBalance;
    private final Supplier<List<String>> members;

    /*
    * @param account       this replica of the account
    * @param syncedBalance the getSyncedBalance implementation (naive or ordered)
    * @param members       returns the names of the replicas currently in the group
    */
    public CommandProcessor(Account account, SyncedBalance syncedBalance, Supplier<List<String>> members) {
        this.account = account;
        this.syncedBalance = syncedBalance;
        this.members = members;
    }

    /*
    * Parses an input line and dispatches it to the appropriate command handler.
    *
    * @param line the command entered by the user
    * @return the result of processing the command, or an error message. It may have several
    *         lines (getHistory); the caller logs each line with its own timestamp.
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

    /*
    * Returns an error message if the currency is not in the currency file, otherwise null.
    * Unknown currencies are rejected before anything is broadcast, so they never reach the
    * other replicas.
    */
    private String checkCurrency(String currency){
        return account.hasRate(currency) ? null : "Unknown currency " + currency + ".";
    }

    /*
    * Creates a deposit transaction and adds it to outstanding_collection.
    * It is applied when it comes back from the delivery server in the total order.
    */
    private String handleDeposit(String[] parts){
        if(parts.length != 3){
            return "Invalid deposit arguments.";
        }

        String currency = parts[1];
        String error = checkCurrency(currency);
        if(error != null){
            return error;
        }
        try {
            parseNumber(parts[2]);
        } catch (NumberFormatException e) {
            return "Invalid deposit amount.";
        }
        return issue("deposit " + currency + " " + parts[2]);
    }

    /*
    * Creates an addInterest transaction with an optional currency (no currency means all
    * currencies) and adds it to outstanding_collection.
    */
    private String handleAddInterest(String[] parts){
        if(parts.length != 2 && parts.length != 3){
            return "Invalid interest arguments";
        }

        int percentageIndex = (parts.length == 3) ? 2 : 1;
        if(parts.length == 3){
            String error = checkCurrency(parts[1]);
            if(error != null){
                return error;
            }
        }
        try {
            parseNumber(parts[percentageIndex]);
        } catch (NumberFormatException e) {
            return "Invalid interest amount";
        }
        String command = (parts.length == 3)
                ? "addInterest " + parts[1] + " " + parts[2]
                : "addInterest " + parts[1];
        return issue(command);
    }

    /*
    * Adds a deposit or addInterest command to outstanding_collection.
    */
    private String issue(String command){
        try {
            Transaction transaction = account.issue(command);
            return "Queued " + transaction.command() + " as transaction " + transaction.uniqueId();
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
    }

    /*
    * Prints the balance of the whole account converted to a currency (USD if none is given),
    * right away, without waiting for outstanding transactions.
    */
    private String handleQuickBalance(String[] parts){
        if(parts.length != 1 && parts.length != 2){
            return "Invalid balance arguments";
        }
        String currency = (parts.length == 2) ? parts[1] : DEFAULT_CURRENCY;
        String error = checkCurrency(currency);
        if(error != null){
            return error;
        }
        return String.format(Locale.ROOT, "getQuickBalance %s: %.2f", currency, account.balanceIn(currency));
    }

    /*
    * Prints the executed transactions since the last cleanHistory, numbered from
    * order_counter - executed_list.size, followed by the outstanding transactions.
    * getSyncedBalance markers are not transactions on the account, so they are not listed.
    */
    private String handleGetHistory(String[] parts){
        if(parts.length != 1){
            return "Invalid history argument";
        }

        List<Account.Executed> executed;
        List<Transaction> outstanding;
        int orderCounter;
        // Read all three at once, so a delivery in between cannot make the numbering wrong.
        synchronized (account) {
            executed = account.executed();
            outstanding = account.outstanding();
            orderCounter = account.orderCounter();
        }

        List<String> lines = new ArrayList<>();
        lines.add("executed_list");
        int number = orderCounter - executed.size();
        for (Account.Executed entry : executed) {
            lines.add(number + ". " + entry.executedAt().format(HISTORY_TIME) + " " + entry.transaction().command());
            number++;
        }
        lines.add("outstanding_collection");
        for (Transaction transaction : outstanding) {
            if (!SyncMarker.isMarker(transaction)) {
                lines.add(transaction.command());
            }
        }
        return String.join("\n", lines);
    }

    /*
    * Shows whether a deposit or addInterest transaction has been applied. Accepts the full id
    * "<instance name> <counter>", or only the counter for a transaction issued by this server.
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
            return "Transaction " + uniqueId + " has been applied.";
        }
        if(account.isOutstanding(uniqueId)){
            return "Transaction " + uniqueId + " has not been applied yet (outstanding).";
        }
        return "Transaction " + uniqueId + " is unknown.";
    }

    /*
    * Empties executed_list. order_counter and outstanding_counter are kept.
    */
    private String handleCleanHistory(String[] parts){
        if(parts.length != 1){
            return "Invalid cleanHistory arguments.";
        }
        account.cleanHistory();
        return "History cleaned.";
    }

    /*
    * Prints the names of the replicas currently in the group.
    */
    private String handleMemberInfo(String[] parts){
        if(parts.length != 1){
            return "Invalid memberInfo arguments.";
        }
        List<String> names = members.get();
        return "memberInfo: " + names.size() + " members " + names;
    }

    /*
    * Starts a getSyncedBalance request (USD if no currency is given). It returns at once; the
    * answer is printed later by the SyncedBalance implementation, when it is ready.
    * The request line is logged here, before the request is made, because the naive version
    * answers right away when nothing is outstanding and its answer must come after it.
    */
    private String handleGetSyncedBalance(String[] parts){
        if(parts.length != 1 && parts.length != 2){
            return "Invalid synced balance arguments";
        }

        String currency = (parts.length == 2) ? parts[1] : DEFAULT_CURRENCY;
        String error = checkCurrency(currency);
        if(error != null){
            return error;
        }
        Log.info("getSyncedBalance " + currency + " requested (" + syncedBalance.mode() + ")");
        syncedBalance.request(currency);
        return "";
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
    * Validates an exit command. The input loop stops after it and the bank server shuts down.
    */
    private String handleExit(String[] parts){
        if(parts.length != 1){
            return "Invalid exit arguments";
        }

        return "Exit requested.";
    }

}
