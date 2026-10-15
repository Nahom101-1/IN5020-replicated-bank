package com.ass2.bank.cli;


import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;
import java.util.concurrent.ThreadLocalRandom;
import com.ass2.bank.util.Log;


/*
* Handles batch processing of bank commands from an input file.
* Commands are processed sequentially with a rnadom delay between them.
*/
public class BatchInput {
    
    private static final long MIN_DELAY_MS = 500; 
    private static final long MAX_DELAY_MS = 1501; 

    /*
    * Pauses execution for a random duration between 500 and 1500 milliseconds. 
    */
    private void randomDelay() throws InterruptedException{
        long delay = ThreadLocalRandom.current().nextLong(MIN_DELAY_MS, MAX_DELAY_MS);
        Thread.sleep(delay);
    }

    /*
    * Reads and processes commands from a file untill end of file or a valid exit command.
    * Logs each command result and waits between consecutive commands.
    * @param filename path to the batch input file.
    * @throws FileNotFoundException if the specified file cannot be opened.
    */
    public void run(String filename) throws FileNotFoundException{
        CommandProcessor processor = new CommandProcessor();

        try(Scanner scanner = new Scanner(new File(filename))){
            while(scanner.hasNextLine()){
                String line = scanner.nextLine();
                String result = processor.process(line);
                Log.info(result);
                
                if(line.trim().equals("exit")){
                    break;
                }

                try {
                   randomDelay();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }
    
    // For testing purposes. Remove after integration.
    public static void main(String[] args){
        BatchInput input = new BatchInput();
        try {
            input.run("IN5020-replicated-bank/input/Rep3.txt");
        } catch (FileNotFoundException e) {
            System.out.println("File not found.");
        }
    }
}
