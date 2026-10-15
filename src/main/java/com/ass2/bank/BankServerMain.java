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
import java.net.InetAddress;
import java.nio.file.Path;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;
import java.util.Map;


/*
* Entry point for the bank server application.
*
* Usage: java [-Dsync=naive|ordered] [-Dname=<instance name>] -jar bank.jar
*        <server ip:port> <account name> <number of replicas> <currency file> [batch file]
*
* Startup: reads the currency file, connects to the Message Delivery server, joins the group
* <account name>, waits until <number of replicas> bank servers have joined, starts the 10 s
* broadcast of outstanding_collection and then runs the commands interactively or from the file.
*/
public class BankServerMain {

    /* How often to check the member list while waiting for all replicas to join. */
    private static final long MEMBER_POLL_MILLIS = 200;

    /*
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

    /*
    * The name of this bank server instance, the first part of its transaction ids. It must be
    * unique in the group. Taken from -Dname if given, otherwise "<host name>-<process id>".
    */
    private static String instanceName(){
        String name = System.getProperty("name");
        if(name == null || name.isBlank()){
            String host;
            try {
                host = InetAddress.getLocalHost().getHostName();
            } catch (Exception e) {
                host = "bank";
            }
            name = host + "-" + ProcessHandle.current().pid();
        }
        return name.trim().replaceAll("\\s+", "-"); // ids are "<name> <counter>", so no spaces
    }


    /*
    * Validates the startup arguments, connects to the delivery server and runs the commands.
    * @param args command-line arguments containing the server address, account name, replica count, currency file and optional input file.
    */
    public static void main(String[] args) {

        if(args.length < 4 || args.length > 5){
            System.out.println("Usage: <server ip:port> <account name> <number of replicas> <currency file> [batch file]");
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
        String mode = (inputFile != null) ? "Batch mode" : "Interactive mode";
        String name = instanceName();
        String syncMode = System.getProperty(SyncedBalance.MODE_PROPERTY, "ordered");

        Log.info("Instance name: " + name);
        Log.info("Server Address: " + serverAddress);
        Log.info("Account Name: " + accountName);
        Log.info("Number of replicas: " + numberOfReplicas);
        Log.info("CurrencyFile: " + currencyFile);
        Log.info("Mode: " + mode + ", getSyncedBalance: " + syncMode);

        try {
            run(serverAddress, accountName, numberOfReplicas, currencyFile, inputFile, name, syncMode);
        } catch (Exception e) {
            Log.info("Bank server stopped: " + e);
            System.exit(1);
        }
        // RMI keeps non-daemon threads running, so the process has to be ended explicitly.
        System.exit(0);
    }

    private static void run(String serverAddress, String accountName, int numberOfReplicas, String currencyFile,
                            String inputFile, String name, String syncMode) throws Exception {
        // The replica: balance 0.0, empty lists and counters at 0.
        Map<String, Double> rates = CurrencyRates.load(Path.of(currencyFile));
        Account account = new Account(name, rates);
        SyncedBalance syncedBalance = SyncedBalance.create(syncMode, account, Log::info);
        account.useSyncedBalance(syncedBalance);

        // Connect to the Message Delivery server: "<ip>:<port>", port 1099 if left out.
        int colon = serverAddress.lastIndexOf(':');
        String host = (colon < 0) ? serverAddress : serverAddress.substring(0, colon);
        int port = (colon < 0) ? Config.DEFAULT_DELIVERY_PORT : Integer.parseInt(serverAddress.substring(colon + 1));
        MessageDelivery delivery = (MessageDelivery) LocateRegistry.getRegistry(host, port).lookup(Config.DELIVERY_BINDING);

        // Export our replica so the delivery server can call deliver(), and join the group.
        // If the group already has members, the delivery server copies the state of one of
        // them into this replica before adding it.
        BankReplicaImpl replica = new BankReplicaImpl(account);
        BankReplica stub = (BankReplica) UnicastRemoteObject.exportObject(replica, 0);
        delivery.join(accountName, name, stub);
        Log.info("Joined group " + accountName + ", waiting for " + numberOfReplicas + " replicas");

        // Wait for all initial replicas. The delivery server's member list is checked directly:
        // membership callbacks are sent from several threads and can arrive out of order.
        List<String> members = delivery.getMembers(accountName);
        while (members.size() < numberOfReplicas) {
            Thread.sleep(MEMBER_POLL_MILLIS);
            members = delivery.getMembers(accountName);
        }
        Log.info("Group " + accountName + " is ready: " + members);

        OutstandingSender sender = new OutstandingSender(account, delivery, name);
        sender.start();

        CommandProcessor processor = new CommandProcessor(account, syncedBalance, () -> {
            try {
                return delivery.getMembers(accountName);
            } catch (RemoteException e) {
                return replica.members(); // last list the delivery server sent us
            }
        });

        try {
            if(inputFile == null){
                new InteractiveInput(processor).run();
            } else {
                new BatchInput(processor).run(inputFile);
            }
        } catch (FileNotFoundException e) {
            Log.info("File not found: " + inputFile);
        } finally {
            sender.flush();
            syncedBalance.close();
            try {
                delivery.leave(name);
            } catch (RemoteException e) {
                Log.info("Could not leave the group: " + e.getMessage());
            }
            UnicastRemoteObject.unexportObject(replica, true);
            Log.info("Bank server " + name + " exited");
        }
    }
}
