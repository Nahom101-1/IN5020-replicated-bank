package com.ass2.bank;

public class BankServerMain {

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


    public static void main(String[] args) {
        System.out.println("Bank server: TODO");

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
        String mode = (inputFile != null) ? "Batch mode" : "Interactive mode" ;

        /*java com.ass2.bank.BankServerMain localhost:1099 group01 3 TradingRate.txt */
        System.out.println("Server Address: " + serverAddress +
        "\nAccount Name: " + accountName + 
        "\nNumber of replicas: " + numberOfReplicas + 
        "\nCurrencyFile: " + currencyFile +
        "\nMode: " + mode);

    }
}