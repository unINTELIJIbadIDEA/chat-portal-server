package com.project;

import com.project.server.ApiServer;
import com.project.server.Server;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ServerLauncher {
    public static void main(String[] args) {
        ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(false);
            return thread;
        });

        Server tcpServer = new Server();
        ApiServer apiServer = new ApiServer();

        executor.submit(() -> {
            Thread.currentThread().setName("TCP-Server-Thread");
            try {
                tcpServer.runServer();
            } catch (Exception e) {
                System.err.println("TCP Server crashed: " + e.getMessage());
            }
        });

        executor.submit(() -> {
            Thread.currentThread().setName("API-Server-Thread");
            try {
                apiServer.runServer();
            } catch (Exception e) {
                System.err.println("API Server crashed: " + e.getMessage());
            }
        });

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Zamykanie serwerów...");

            try {
                tcpServer.stopServer();
                apiServer.stopServer();
            } catch (Exception e) {
                System.err.println("Błąd przy zamykaniu serwerów: " + e.getMessage());
                e.printStackTrace();
            }

            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    System.out.println("Wymuszone zatrzymanie wątków...");
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }

            System.out.println("Serwery zamknięte.");
        }));
    }
}
