package com.ass2.delivery;

import com.ass2.common.Config;
import com.ass2.common.MessageDelivery;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.ExportException;
import java.rmi.server.UnicastRemoteObject;

public class DeliveryServerMain {
    public static void main(String[] args) {
        if (args.length > 1) {
            System.err.println("Usage: DeliveryServerMain [registry-port]");
            return;
        }

        int port;
        try {
            port = args.length == 0 ? Config.DEFAULT_DELIVERY_PORT : Integer.parseInt(args[0]);
            if (port < 1 || port > 65_535) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            System.err.println("Registry port must be an integer between 1 and 65535.");
            return;
        }

        Registry registry;
        boolean ownsRegistry;
        try {
            registry = LocateRegistry.createRegistry(port);
            ownsRegistry = true;
        } catch (ExportException e) {
            try {
                registry = LocateRegistry.getRegistry(port);
                ownsRegistry = false;
            } catch (RemoteException registryError) {
                System.err.println("Could not connect to the RMI registry: " + registryError.getMessage());
                return;
            }
        } catch (Exception e) {
            System.err.println("Could not start the RMI registry: " + e.getMessage());
            return;
        }

        MessageDeliveryImpl implementation = new MessageDeliveryImpl();
        try {
            MessageDelivery stub = (MessageDelivery) UnicastRemoteObject.exportObject(implementation, 0);
            registry.rebind(Config.DELIVERY_BINDING, stub);
            Registry activeRegistry = registry;
            boolean unexportRegistry = ownsRegistry;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    activeRegistry.unbind(Config.DELIVERY_BINDING);
                } catch (Exception ignored) {
                }
                implementation.close();
                try {
                    UnicastRemoteObject.unexportObject(implementation, true);
                    if (unexportRegistry) {
                        UnicastRemoteObject.unexportObject(activeRegistry, true);
                    }
                } catch (Exception ignored) {
                }
            }));
            System.out.println("Message Delivery server listening on registry port " + port);
        } catch (Exception e) {
            implementation.close();
            System.err.println("Could not bind Message Delivery service: " + e.getMessage());
        }
    }
}