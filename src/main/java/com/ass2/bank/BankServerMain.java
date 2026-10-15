package com.ass2.bank;

import com.ass2.bank.cli.BatchInput;
import com.ass2.bank.cli.CommandProcessor;
import com.ass2.bank.cli.InteractiveInput;
import com.ass2.bank.core.Account;
import com.ass2.bank.core.BankReplicaImpl;
import com.ass2.bank.core.CurrencyRates;
import com.ass2.bank.core.OutstandingSender;
import com.ass2.bank.sync.SyncedBalance;
import com.ass2.bank.util.Log;
import com.ass2.common.BankReplica;
import com.ass2.common.Config;
import com.ass2.common.MessageDelivery;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;
import java.util.Map;

/**
 * Entry point for the bank server.
 * Usage: <server address> <account name> <number of replicas> <currency file> [file name]
 */
public class BankServerMain {

    /**
     * Parses and validates the number of replicas.
     * @param value the replica count provided as a string.
     * @return the parsed positive integer, or null if invalid.
     */
    private static Integer parseReplicaCount(String value){
        try {
            int count = Integer.parseInt(value);

            if (count <= 0){
                System.out.println("Number of replicas must be positive.");
                return null;
            }
            return count;

        } catch(NumberFormatException e){
            System.out.println("Invalid number of replicas.");
            return null;
        }
    }

    /**
     * Starts the bank server: joins the group, waits for the other replicas,
     * then reads commands until exit.
     * @param args command-line arguments containing the server address, account name, replica count, currency file and optional input file.
     */
    public static void main(String[] args) {

        if(args.length < 4 || args.length > 5){
            System.out.println("Invalid number of arguments");
            System.out.println("Usage: <server address> <account name> <number of replicas> <currency file> [file name]");
            return;
        }

        String serverAddress = args[0];
        String accountName = args[1];
        Integer numberOfReplicas = parseReplicaCount(args[2]);
        if(numberOfReplicas == null){
            return;
        }
        String currencyFile = args[3];
        String inputFile = (args.length == 5) ? args[4] : null;

        Map<String, Double> rates;
        try {
            rates = CurrencyRates.load(Path.of(currencyFile));
        } catch (IOException | IllegalArgumentException e) {
            System.out.println("Could not read currency file: " + e.getMessage());
            return;
        }

        // Unique name in the group, the first part of every transaction id. -Dname=<name> sets
        // it (the run scripts use rep1-a, rep1-b, ...); ids are "<name> <counter>", so no spaces.
        String replicaName = System.getProperty("name", accountName + "-" + ProcessHandle.current().pid())
                .trim().replaceAll("\\s+", "-");
        Account account = new Account(replicaName, rates);
        SyncedBalance syncedBalance = SyncedBalance.create(
                System.getProperty(SyncedBalance.MODE_PROPERTY, "ordered"), account, Log::info);
        account.useSyncedBalance(syncedBalance);
        BankReplicaImpl replica = new BankReplicaImpl(account);

        MessageDelivery delivery;
        try {
            delivery = connect(serverAddress);
            BankReplica stub = (BankReplica) UnicastRemoteObject.exportObject(replica, 0);
            delivery.join(accountName, replicaName, stub);
        } catch (RemoteException | NotBoundException | IllegalArgumentException e) {
            System.out.println("Could not join the group at " + serverAddress + ": " + e.getMessage());
            System.exit(1);
            return;
        }
        Log.info("Joined group " + accountName + " as " + replicaName
                + " (getSyncedBalance: " + syncedBalance.mode() + ")");

        // The first replicas wait for each other. A late replica already sees enough members.
        // The member list is read from the delivery server, not from the membership callbacks:
        // those are sent from several threads and can arrive out of order, and an old, shorter
        // list arriving last would make this wait forever.
        List<String> members;
        try {
            Log.info("Waiting for " + numberOfReplicas + " replicas...");
            members = delivery.getMembers(accountName);
            while (members.size() < numberOfReplicas) {
                Thread.sleep(200);
                members = delivery.getMembers(accountName);
            }
        } catch (RemoteException | InterruptedException e) {
            Log.info("Stopped while waiting for the other replicas: " + e.getMessage());
            System.exit(1);
            return;
        }
        Log.info("Members: " + String.join(", ", members));

        OutstandingSender sender = new OutstandingSender(account, delivery, replicaName);
        sender.start();

        CommandProcessor processor = new CommandProcessor(account, syncedBalance, replica);
        runCommands(inputFile, processor);

        // Send what is still outstanding before leaving, so our last transactions reach every
        // replica and our last getSyncedBalance is answered.
        try {
            sender.flush();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        syncedBalance.close();
        try {
            delivery.leave(replicaName);
        } catch (RemoteException e) {
            System.out.println("Could not leave the group: " + e.getMessage());
        }
        System.exit(0);
    }

    /** Finds the Message Delivery server. The address is "host:port" or just "host". */
    private static MessageDelivery connect(String serverAddress) throws RemoteException, NotBoundException {
        String host = serverAddress;
        int port = Config.DEFAULT_DELIVERY_PORT;

        int colon = serverAddress.lastIndexOf(':');
        if (colon >= 0) {
            host = serverAddress.substring(0, colon);
            try {
                port = Integer.parseInt(serverAddress.substring(colon + 1));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid port in " + serverAddress);
            }
        }

        Registry registry = LocateRegistry.getRegistry(host, port);
        return (MessageDelivery) registry.lookup(Config.DELIVERY_BINDING);
    }

    /** Reads commands from the file, or from the terminal if there is no file. */
    private static void runCommands(String inputFile, CommandProcessor processor) {
        if(inputFile == null){
            InteractiveInput input = new InteractiveInput(processor);
            input.run();
        } else {
            BatchInput input = new BatchInput(processor);
            try {
                input.run(inputFile);
            } catch (FileNotFoundException e) {
                System.out.println("File not found: " + inputFile);
            }
        }
    }
}
