package com.ass2.bank.cli;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;
import java.util.concurrent.ThreadLocalRandom;


public class BatchInput {
    
    private static final long MIN_DELAY_MS = 500; 
    private static final long MAX_DELAY_MS = 1500; 
    
    private void randomDelay() throws InterruptedException{
        long delay = ThreadLocalRandom.current().nextLong(MIN_DELAY_MS, MAX_DELAY_MS);
        Thread.sleep(delay);
    }

    public void run(String filename) throws FileNotFoundException{
        CommandProcessor processor = new CommandProcessor();

        try(Scanner scanner = new Scanner(new File(filename))){
            while(scanner.hasNextLine()){
                String line = scanner.nextLine();
                String result = processor.process(line);
                System.out.println(result);
                
                if(line.trim().equals("exit") || !scanner.hasNextLine()){
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
            input.run("IN5020-replicated-bank/input/test-commands.txt");
        } catch (FileNotFoundException e) {
            System.out.println("File not found.");
        }
    }
}
