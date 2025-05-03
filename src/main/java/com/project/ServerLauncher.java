package com.project;

import com.project.server.ApiServer;
import com.project.server.Server;
import com.project.utils.Config;
import com.project.utils.SshTunnel;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServerLauncher {
    public static void main(String[] args) {
        ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(false);
            return thread;
        });

        var tcpServer = new Server();
        var apiServer = new ApiServer();

        var tcpTunnel = new SshTunnel(Config.getREMOTE_SERVER_PORT(),Config.getLOCAL_SERVER_PORT());
        var apiTunnel = new SshTunnel(Config.getREMOTE_API_PORT(),Config.getLOCAL_API_PORT());

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

        apiTunnel.openTunnel();
        tcpTunnel.openTunnel();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Zamykanie serwerów...");

            try {
                tcpServer.stopServer();
                apiServer.stopServer();
            } catch (Exception e) {
                System.out.println("Błąd przy zamykaniu serwerów: " + e.getMessage());
            }

            executor.shutdown();
            System.out.println("Serwery zamknięte.");

            apiTunnel.closeTunnel();
            tcpTunnel.closeTunnel();
        }));
    }
}
