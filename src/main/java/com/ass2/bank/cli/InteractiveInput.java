package com.ass2.bank.cli;
import java.util.Scanner;
import com.ass2.bank.util.Log;

/*
* Handles interactive command-line input for bank server.
* Reads commands, processes thyem, and logs the results. 
*/
public class InteractiveInput {

    private final CommandProcessor processor;

    public InteractiveInput(CommandProcessor processor) {
        this.processor = processor;
    }

    /*
    * Continously reads the commands untill exit.
    */
    public void run(){
        Scanner scanner = new Scanner(System.in);

        while(true){
            System.out.print("Enter command: ");
            if(!scanner.hasNextLine()){
                break;
            }

            String line = scanner.nextLine();
            Log.info("> " + line.trim());
            String result = processor.process(line);
            Log.info(result);

            if(line.trim().equals("exit")){
                break;
            }
        }
    }
}
