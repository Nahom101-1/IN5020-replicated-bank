package com.ass2.bank;

import com.ass2.bank.cli.InteractiveInput;

import java.io.FileNotFoundException;

import com.ass2.bank.cli.BatchInput;


/*
* Entry point for the bank server application.
* Validates startup arguments and initializes the selected input mode.
*/
public class BankServerMain {

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
    * Validates the startup arguments and starts the appropriate input mode
    * @param args command-line arguments containing the servcer address, account name, replica count, currency file and optional input file.
    */
    public static void main(String[] args) {

        if(args.length < 4 || args.length > 5){
            System.out.println("Invalid number of arguments");
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
        
        System.out.println("Server Address: " + serverAddress +
        "\nAccount Name: " + accountName + 
        "\nNumber of replicas: " + numberOfReplicas + 
        "\nCurrencyFile: " + currencyFile +
        "\nMode: " + mode);
        
        if(inputFile == null){
            InteractiveInput input = new InteractiveInput();
            input.run();
        } else {
            BatchInput input = new BatchInput();
            try {
                input.run(inputFile);
            } catch (FileNotFoundException e) {
                System.out.println("File not found.");
            }
        }

    }
}
