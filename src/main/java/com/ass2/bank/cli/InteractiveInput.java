package com.ass2.bank.cli;
import java.util.Scanner;
import com.ass2.bank.util.Log;

public class InteractiveInput {

    public void run(){
        CommandProcessor processor = new CommandProcessor();
        Scanner scanner = new Scanner(System.in);

        while(true){
            System.out.print("Enter command: ");
            if(!scanner.hasNextLine()){
                break;
            }

            String line = scanner.nextLine();
            String result = processor.process(line);
            Log.info(result);

            if(line.trim().equals("exit")){
                break;
            }
        }
    }

    // For testing purposes. Remove after integration.
    public static void main(String[] args) {
        InteractiveInput input = new InteractiveInput();
        input.run();
    }
}
